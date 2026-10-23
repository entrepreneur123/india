-- One kind of room. Every room is what 017 called a voice room: anyone opens one, picks one of three categories
-- (talk, music, games) and who may come in, and every room has the chat, the stage (host + 8 seats, raised
-- hands), listen-together music, Ludo (018) and photos. A room with nobody on a seat is a plain chat room: the
-- apps connect the audio (LiveKit) only while someone is on the stage, and voice_grant() refuses a token before.
-- The text rooms of 008 (table public.rooms, the built-in rooms included) leave the apps; the table stays for
-- the Android app 0.6.0 until it is updated (the MCP tool can't drop tables).
--
-- New here:
--  * Settings per room: max people present (2-200), gender, age range, private (joined only with the 6-character
--    code; 10 wrong codes in 10 minutes and code entry pauses, as in 014), and a host announcement (<= 200).
--    The host is no longer put on the stage when a room opens; "Join voice" takes seat 0.
--  * Icebreakers: anyone present posts a conversation starter (one per room every 20 s). Only the prompt's
--    number is stored (1-40); the apps show it in their own language.
--  * Song requests: anyone suggests a YouTube link (straight into the queue), members upvote once per song, and
--    the host's "next" plays the most voted (ties: the oldest). The host can remove any.
--  * A Ludo leaderboard per room: wins from finished games.
--  * Photos in room chat: <me>/rooms/<uuid>.jpg in storage, checked for nudity on the sender's device before
--    upload; 5 a minute. People in the room (or there in the last day) can read them; they go with the chat
--    after 30 days (purge_old_data) and the file after that (stale_media).

-- ---------- rooms: categories and settings ----------

alter table public.voice_rooms drop constraint voice_rooms_category_check;
update public.voice_rooms set category = case category when 'music' then 'music' when 'gaming' then 'games' else 'talk' end
 where category not in ('talk', 'music', 'games');
alter table public.voice_rooms
  alter column category set default 'talk',
  add constraint voice_rooms_category_check check (category in ('talk', 'music', 'games')),
  add column max_members int not null default 50 check (max_members between 2 and 200),
  add column gender_rule text not null default 'any' check (gender_rule in ('any', 'female', 'male')),
  add column min_age int not null default 18 check (min_age between 18 and 100),
  add column max_age int not null default 100 check (max_age between 18 and 100),
  add column is_private boolean not null default false,
  add column announcement text check (char_length(announcement) between 1 and 200),
  add constraint voice_rooms_age_range check (min_age <= max_age);

-- The code is the key to a private room: kept apart from the (public) room row, read through the functions only.
create table public.voice_room_codes (
  room_id text primary key references public.voice_rooms (id) on delete cascade,
  code text not null unique check (code ~ '^[A-HJ-NP-Z2-9]{6}$')
);
alter table public.voice_room_codes enable row level security;

-- One row per person and song; taking a vote back sets voted = false (no deletes needed).
create table public.voice_votes (
  room_id text not null references public.voice_rooms (id) on delete cascade,
  item_id text not null,
  user_id uuid not null references public.profiles (id) on delete cascade,
  voted boolean not null default true,
  at timestamptz not null default now(),
  primary key (room_id, item_id, user_id)
);
alter table public.voice_votes enable row level security;

-- ---------- messages: icebreakers and photos ----------

alter table public.voice_messages drop constraint voice_messages_kind_check;
alter table public.voice_messages
  add constraint voice_messages_kind_check check (kind in ('text', 'system', 'reaction', 'icebreaker', 'photo')),
  add column media_path text,
  add constraint voice_messages_media check ((kind = 'photo') = (media_path is not null));
create index voice_messages_media on public.voice_messages (media_path) where media_path is not null;

-- ---------- helpers (not callable by users) ----------

/** Old category names (apps before 019) → the three of now; null for anything else. */
create function public.voice_kind(p text) returns text language sql immutable set search_path = '' as $$
  select case
    when p in ('talk', 'music', 'games') then p
    when p = 'gaming' then 'games'
    when p in ('chat', 'friends', 'language', 'movies', 'sports', 'study', 'travel', 'night') then 'talk'
  end
$$;

/** A fresh 6-character code (no 0/O, 1/I). */
create function public.voice_new_code() returns text language plpgsql volatile security definer set search_path = '' as $$
declare c text; i int := 0;
begin
  loop
    c := (select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '') from generate_series(1, 6));
    exit when not exists (select 1 from public.voice_room_codes x where x.code = c);
    i := i + 1;
    if i > 20 then raise exception 'try_again' using errcode = 'P0001'; end if;
  end loop;
  return c;
end $$;

/** Why `me` may not come in (null = allowed). `p_here`: already present (doesn't count against the size). */
create function public.voice_refusal(r public.voice_rooms, me uuid, p_here boolean) returns text
language plpgsql stable security definer set search_path = '' as $$
declare p public.profiles; my_age int;
begin
  if r.host = me then return null; end if;
  if r.ended_at is not null then return 'room_closed'; end if;
  if public.is_blocked(me, r.host) then return 'not_allowed'; end if;
  select * into p from public.profiles x where x.id = me;
  my_age := extract(year from age(p.birth_date))::int;
  if r.gender_rule <> 'any' and p.gender is distinct from r.gender_rule then return 'room_gender'; end if;
  if my_age < r.min_age or my_age > r.max_age then return 'room_age'; end if;
  if not p_here and (select count(*) from public.voice_members m where m.room_id = r.id and m.user_id <> me
                       and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds') >= r.max_members then
    return 'room_full';
  end if;
  return null;
end $$;

/**
 * Comes in (or back): 017's voice_enter with the room's rules. A private room is entered the first time only
 * with its code (`p_code`); after that the person has a row and comes back like anyone.
 */
create function public.voice_admit(p_room text, me uuid, p_code boolean) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms; m public.voice_members; was_here boolean; why text;
begin
  perform public.voice_settle(p_room);
  select * into r from public.voice_rooms x where x.id = p_room for update;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if r.ended_at is not null then raise exception 'room_closed' using errcode = 'P0001'; end if;
  select * into m from public.voice_members x where x.room_id = p_room and x.user_id = me;
  if found and m.kicked then raise exception 'voice_removed' using errcode = 'P0001'; end if;
  -- Same answer as a wrong id: no probing for private rooms.
  if r.is_private and r.host <> me and not p_code and m.user_id is null then raise exception 'not_found' using errcode = 'P0001'; end if;
  was_here := m.user_id is not null and m.left_at is null and m.seen_at > now() - interval '90 seconds';
  why := public.voice_refusal(r, me, was_here);
  if why is not null then raise exception '%', why using errcode = 'P0001'; end if;

  -- One room at a time: leave any other room I'm listening or speaking in (a room I host carries on: it has
  -- its 2 minutes, and leaving it is the host's own choice).
  update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when x.role = 'speaker' then 'listener' else x.role end
   where x.user_id = me and x.room_id <> p_room and x.left_at is null and x.role <> 'host';

  insert into public.voice_members (room_id, user_id, role, seat)
  values (p_room, me, case when r.host = me then 'host' else 'listener' end, null)
  on conflict (room_id, user_id) do update set seen_at = now(), left_at = null;
  if not was_here then
    if r.host <> me and exists (select 1 from public.voice_members x where x.room_id = p_room and x.user_id = me
                 and (x.joined_line_at is null or x.joined_line_at < now() - interval '10 minutes')) then
      update public.voice_members set joined_line_at = now() where room_id = p_room and user_id = me;
      perform public.voice_line(p_room, me, 'joined');
    end if;
    perform public.voice_touch(p_room);
  end if;
end $$;

create or replace function public.voice_enter(p_room text, me uuid) returns void language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_admit(p_room, me, false);
end $$;

/** A person's card in a message row (the sender_* columns). */
create function public.voice_post(p_room text, me uuid, p_kind text, p_body text, p_payload jsonb, p_media text) returns public.voice_messages
language plpgsql security definer set search_path = '' as $$
declare p public.profiles; msg public.voice_messages;
begin
  select * into p from public.profiles x where x.id = me;
  insert into public.voice_messages (room_id, sender, kind, body, payload, media_path, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  values (p_room, p.id, p_kind, p_body, p_payload, p_media, p.name, p.gender, extract(year from age(p.birth_date))::int, p.photo_path, p.country)
  returning * into msg;
  return msg;
end $$;

-- ---------- state and lists ----------

/** 017's state, plus the room's settings (the code for the host only) and each song's votes. */
create or replace function public.voice_state_of(p_room text, me uuid) returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'now', public.voice_ms(), 'locked', to_jsonb(r.locked_seats), 'music', r.music,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.voted),
               'voted', exists (select 1 from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.user_id = me and v.voted))
             order by n)
        from jsonb_array_elements(r.queue) with ordinality as x(q, n)), '[]'),
    'announcement', r.announcement, 'max', r.max_members, 'gender', r.gender_rule, 'minAge', r.min_age, 'maxAge', r.max_age,
    'private', r.is_private,
    'code', case when r.host = me then (select c.code from public.voice_room_codes c where c.room_id = r.id) end,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
               'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country,
               'role', m.role, 'seat', m.seat, 'muted', m.muted, 'hand', m.hand_at is not null)
             order by m.seat nulls last, m.hand_at nulls last, m.joined_at)
        from public.voice_members m join public.profiles p on p.id = m.user_id
       where m.room_id = r.id and m.left_at is null and not m.kicked
         and (m.seen_at > now() - interval '90 seconds' or m.user_id in (r.host, me))), '[]'))
  from public.voice_rooms r where r.id = p_room
$$;

/** The rooms list: every live room I may see, with its rules, why I can't come in (null = I can), my code. */
create function public.voice_list2()
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, members int,
               now_playing text, game text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
               announcement text, code text, refusal text)
language sql stable security definer set search_path = '' as $$
  with live as (
    select r.* from public.voice_rooms r
     where r.ended_at is null and public.voice_alive(r) and not public.is_blocked(auth.uid(), r.host)
       and not exists (select 1 from public.voice_members k where k.room_id = r.id and k.user_id = auth.uid() and k.kicked)
       and (not r.is_private or r.host = auth.uid()
            or exists (select 1 from public.voice_members k where k.room_id = r.id and k.user_id = auth.uid()))
  ), here as (
    select m.* from public.voice_members m join live on live.id = m.room_id
     where m.left_at is null and not m.kicked and (m.seen_at > now() - interval '90 seconds' or m.user_id = live.host)
  )
  select l.id, l.title, l.category, l.host = auth.uid(),
         (select jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                   'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)
            from public.profiles p where p.id = l.host),
         coalesce((select jsonb_agg(s.card order by s.seat) from (
            select h.seat, jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                     'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country) as card
              from here h join public.profiles p on p.id = h.user_id
             where h.room_id = l.id and h.seat is not null order by h.seat limit 4) s), '[]'),
         (select count(*)::int from here h where h.room_id = l.id and h.seat is null and h.user_id <> auth.uid()),
         (select count(*)::int from here h where h.room_id = l.id),
         case when l.music is not null then coalesce(l.music->>'title', '') end,
         (select g.status from public.ludo_games g where g.room_id = l.id and g.status in ('lobby', 'playing') limit 1),
         l.max_members, l.gender_rule, l.min_age, l.max_age, l.is_private, l.announcement,
         case when l.host = auth.uid() then (select c.code from public.voice_room_codes c where c.room_id = l.id) end,
         public.voice_refusal(x, auth.uid(), exists (select 1 from here h where h.room_id = l.id and h.user_id = auth.uid()))
    from live l join public.voice_rooms x on x.id = l.id
   order by l.host = auth.uid() desc, (select count(*) from here h where h.room_id = l.id) desc, l.created_at desc
   limit 100
$$;

-- The 017 list (Android 0.6.0): private rooms only for their host and the people who came in with the code.
create or replace function public.voice_list()
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, now_playing text)
language sql stable security definer set search_path = '' as $$
  select l.id, l.title, l.category, l.mine, l.host, l.speakers, l.listeners, l.now_playing from public.voice_list2() l
$$;

-- ---------- opening, entering ----------

/** The 017 signature (Android 0.6.0): a voice-first room, the host on the stage at once. Old categories map. */
create or replace function public.create_voice_room(p_title text, p_category text, p_mode text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, '')); rid text; cat text := public.voice_kind(coalesce(p_category, 'talk'));
begin
  if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
  if cat is null or coalesce(p_mode, 'approval') not in ('approval', 'open') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.voice_rooms x where x.owner = me and x.created_at > now() - interval '1 day') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  update public.voice_rooms x set ended_at = now(), rev = rev + 1 where x.host = me and x.ended_at is null;
  rid := 'v-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
  insert into public.voice_rooms (id, owner, host, title, category, speak_mode) values (rid, me, me, t, cat, coalesce(p_mode, 'approval'));
  insert into public.voice_room_codes (room_id, code) values (rid, public.voice_new_code());
  update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when x.role = 'speaker' then 'listener' else x.role end
   where x.user_id = me and x.left_at is null and x.role <> 'host';
  insert into public.voice_members (room_id, user_id, role, seat) values (rid, me, 'host', 0);
  return rid;
end $$;

/** Opens a room with its settings, me as its host (not on the stage yet). Returns {id, code}. */
create function public.create_voice_room2(
  p_title text, p_category text, p_max int, p_gender text, p_min_age int, p_max_age int, p_private boolean, p_announcement text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, '')); a text := nullif(btrim(coalesce(p_announcement, '')), '');
        rid text; c text;
begin
  if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
  if p_max is null or p_max not between 2 and 200 then raise exception 'room_size' using errcode = 'P0001'; end if;
  if coalesce(p_category, '') not in ('talk', 'music', 'games') or coalesce(p_gender, 'any') not in ('any', 'female', 'male')
     or coalesce(p_min_age, 18) not between 18 and 100 or coalesce(p_max_age, 100) not between 18 and 100
     or coalesce(p_min_age, 18) > coalesce(p_max_age, 100) or char_length(a) > 200 then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  if (select count(*) from public.voice_rooms x where x.owner = me and x.created_at > now() - interval '1 day') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  -- One open room per host: the one I'm hosting ends (a room I opened and handed over carries on).
  update public.voice_rooms x set ended_at = now(), rev = rev + 1 where x.host = me and x.ended_at is null;
  rid := 'v-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
  c := public.voice_new_code();
  insert into public.voice_rooms (id, owner, host, title, category, max_members, gender_rule, min_age, max_age, is_private, announcement)
  values (rid, me, me, t, p_category, p_max, coalesce(p_gender, 'any'), coalesce(p_min_age, 18), coalesce(p_max_age, 100),
          coalesce(p_private, false), a);
  insert into public.voice_room_codes (room_id, code) values (rid, c);
  update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when x.role = 'speaker' then 'listener' else x.role end
   where x.user_id = me and x.left_at is null and x.role <> 'host';
  insert into public.voice_members (room_id, user_id, role, seat) values (rid, me, 'host', null);
  return jsonb_build_object('id', rid, 'code', c);
end $$;

/**
 * Finds a live room by its code and comes in; returns its id, or null for a wrong code (raising would undo the
 * miss being counted). Guessing: 10 wrong codes in 10 minutes and code entry stops for the rest of the window
 * (room_code_tries, shared with the old rooms of 014).
 */
create function public.join_voice_code(p_code text) returns text language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t public.room_code_tries; rid text;
begin
  select * into t from public.room_code_tries x where x.user_id = me;
  if t.misses >= 10 and t.since > now() - interval '10 minutes' then raise exception 'slow_down' using errcode = 'P0001'; end if;
  select r.id into rid from public.voice_room_codes c join public.voice_rooms r on r.id = c.room_id
   where c.code = upper(btrim(coalesce(p_code, ''))) and r.ended_at is null and public.voice_alive(r);
  if rid is null then
    insert into public.room_code_tries as q (user_id, misses, since) values (me, 1, now())
    on conflict (user_id) do update set
      misses = case when q.since > now() - interval '10 minutes' then q.misses + 1 else 1 end,
      since = case when q.since > now() - interval '10 minutes' then q.since else now() end;
    return null;
  end if;
  perform public.voice_admit(rid, me, true);
  return rid;
end $$;

/** Host: the title and the announcement pinned at the top (empty removes it). */
create function public.voice_settings(p_room text, p_title text, p_announcement text) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); t text := btrim(coalesce(p_title, '')); a text := nullif(btrim(coalesce(p_announcement, '')), '');
begin
  if m.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
  if char_length(a) > 200 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.voice_rooms set title = t, announcement = a, rev = rev + 1 where id = p_room;
end $$;

-- ---------- the stage: the host joins it like anyone ----------

/** As in 017, plus: the host off the stage takes seat 0 ("Join voice"). */
create or replace function public.voice_take_seat(p_room text, p_seat int) returns text language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); r public.voice_rooms; free smallint;
begin
  select * into r from public.voice_rooms where id = p_room;
  if m.role = 'host' and m.seat is null then
    update public.voice_members set seat = 0, hand_at = null, muted = true where room_id = p_room and user_id = m.user_id;
    perform public.voice_touch(p_room);
    return 'seated';
  end if;
  if p_seat is null or p_seat not between 1 and 8 then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  free := public.voice_free_seat(p_room, p_seat);
  if m.seat is not null then
    if m.role = 'host' or free is distinct from p_seat then raise exception 'not_allowed' using errcode = 'P0001'; end if;
    update public.voice_members set seat = p_seat where room_id = p_room and user_id = m.user_id;
    perform public.voice_touch(p_room);
    return 'moved';
  end if;
  if r.speak_mode = 'open' or m.role = 'cohost' then
    if free is distinct from p_seat then raise exception 'not_allowed' using errcode = 'P0001'; end if;
    perform public.voice_seat(p_room, m.user_id, free);
    return 'seated';
  end if;
  update public.voice_members set hand_at = coalesce(hand_at, now()) where room_id = p_room and user_id = m.user_id;
  perform public.voice_touch(p_room);
  return 'hand';
end $$;

/** Off the stage (the host too: the room goes on as a chat room). */
create or replace function public.voice_leave_seat(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.seat is null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set seat = null, muted = true, role = case when role = 'speaker' then 'listener' else role end
   where room_id = p_room and user_id = m.user_id;
  if m.role <> 'host' then perform public.voice_line(p_room, m.user_id, 'audience'); end if;
  perform public.voice_touch(p_room);
end $$;

/** A LiveKit grant only while someone is on the stage: a room nobody speaks in costs no audio. */
create or replace function public.voice_grant(p_room text) returns table (role text, seat smallint, muted boolean)
language sql stable security definer set search_path = '' as $$
  select m.role, m.seat, m.muted from public.voice_members m
    join public.voice_rooms r on r.id = m.room_id
    join public.profiles p on p.id = m.user_id
   where m.room_id = p_room and m.user_id = auth.uid() and r.ended_at is null and m.left_at is null and not m.kicked
     and (p.banned_until is null or p.banned_until < now())
     and exists (select 1 from public.voice_members s where s.room_id = p_room and s.seat is not null and s.left_at is null and not s.kicked)
$$;

-- ---------- icebreakers and photos ----------

/** A conversation starter: a prompt (1-40) not used in this room's last 15. One per room every 20 s. */
create function public.voice_icebreaker(p_room text) returns public.voice_messages language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); q int;
begin
  if exists (select 1 from public.voice_messages x where x.room_id = p_room and x.kind = 'icebreaker'
               and x.created_at > now() - interval '20 seconds') then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  select s into q from generate_series(1, 40) s
   where s not in (select (x.payload->>'q')::int from (select y.payload from public.voice_messages y
                     where y.room_id = p_room and y.kind = 'icebreaker' order by y.id desc limit 15) x)
   order by random() limit 1;
  return public.voice_post(p_room, m.user_id, 'icebreaker', null, jsonb_build_object('q', q), null);
end $$;

/** A photo I uploaded to <me>/rooms/ (checked on my device first). 5 a minute. */
create function public.voice_photo(p_room text, p_path text) returns public.voice_messages language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if coalesce(p_path, '') !~ ('^' || m.user_id::text || '/rooms/[0-9a-f-]{36}\.jpg$')
     or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = p_path) then
    raise exception 'bad_media' using errcode = 'P0001';
  end if;
  if (select count(*) from public.voice_messages x where x.sender = m.user_id and x.kind = 'photo'
        and x.created_at > now() - interval '1 minute') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return public.voice_post(p_room, m.user_id, 'photo', null, null, p_path);
end $$;

-- ---------- song requests: suggestions go straight into the queue, people vote ----------

/** The host's track plays at once when nothing plays; everything else joins the queue (3 waiting per person). */
create or replace function public.voice_queue_add(p_room text, p_video text, p_title text default '') returns text
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members; r public.voice_rooms; item jsonb; who text;
begin
  if coalesce(p_video, '') !~ '^[A-Za-z0-9_-]{11}$' or char_length(coalesce(p_title, '')) > 100 then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  m := public.voice_actor(p_room);
  select * into r from public.voice_rooms where id = p_room;
  select p.name into who from public.profiles p where p.id = m.user_id;
  item := jsonb_build_object('id', substr(md5(random()::text || clock_timestamp()::text), 1, 10), 'videoId', p_video,
            'title', btrim(coalesce(p_title, '')), 'by', who, 'byId', m.user_id, 'suggested', false);
  if m.role = 'host' and r.music is null then
    update public.voice_rooms set music = public.voice_track(item), rev = rev + 1 where id = p_room;
    perform public.voice_line(p_room, m.user_id, 'track', jsonb_build_object('title', item->>'title'));
    return 'playing';
  end if;
  if jsonb_array_length(r.queue) >= 30 then raise exception 'queue_full' using errcode = 'P0001'; end if;
  if m.role <> 'host' and (select count(*) from jsonb_array_elements(r.queue) q where q->>'byId' = m.user_id::text) >= 3 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  update public.voice_rooms set queue = queue || jsonb_build_array(item), rev = rev + 1 where id = p_room;
  return case when m.role = 'host' then 'queued' else 'suggested' end;
end $$;

/** One vote per person and song; `p_on` false takes it back. */
create function public.voice_vote(p_room text, p_item text, p_on boolean) returns void language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if not exists (select 1 from public.voice_rooms r, jsonb_array_elements(r.queue) q where r.id = p_room and q->>'id' = p_item) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.voice_votes (room_id, item_id, user_id, voted) values (p_room, p_item, m.user_id, coalesce(p_on, true))
  on conflict (room_id, item_id, user_id) do update set voted = excluded.voted, at = now()
   where public.voice_votes.voted is distinct from excluded.voted;
  if found then perform public.voice_touch(p_room); end if;
end $$;

/** Next track: the most voted in the queue (ties: the oldest), or none. `p_current` as in 017. */
create or replace function public.voice_skip(p_room text, p_current text default null) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room); item jsonb;
begin
  if p_current is not null and r.music->>'id' is distinct from p_current then return; end if;
  select q into item from jsonb_array_elements(r.queue) with ordinality as x(q, n)
   order by (select count(*) from public.voice_votes v where v.room_id = p_room and v.item_id = q->>'id' and v.voted) desc, n
   limit 1;
  if item is null then
    update public.voice_rooms set rev = rev + 1, music = null where id = p_room;
    return;
  end if;
  update public.voice_rooms set rev = rev + 1, music = public.voice_track(item), queue = coalesce((
      select jsonb_agg(q order by n) from jsonb_array_elements(queue) with ordinality as x(q, n) where q->>'id' <> item->>'id'), '[]')
   where id = p_room;
  perform public.voice_line(p_room, (item->>'byId')::uuid, 'track', jsonb_build_object('title', item->>'title'));
end $$;

-- ---------- Ludo leaderboard ----------

/** Top 5 winners of this room's finished games: [{id, name, photo_path, wins}]. */
create function public.voice_leaders(p_room text) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  if not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = me and m.left_at is null and not m.kicked) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'photo_path', p.photo_path, 'wins', w.wins) order by w.wins desc, w.last)
      from (select g.players[g.winner + 1] as uid, count(*)::int as wins, max(g.ended_at) as last
              from public.ludo_games g
             where g.room_id = p_room and g.status = 'finished' and g.winner is not null and g.players[g.winner + 1] is not null
             group by 1 order by 2 desc, 3 limit 5) w
      join public.profiles p on p.id = w.uid
     where not public.blocked_with(w.uid)), '[]');
end $$;

-- ---------- media: room photos ----------

alter policy "upload own media" on storage.objects with check (
  bucket_id = 'media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (storage.foldername(name))[2] in ('avatar', 'chat', 'posts', 'rooms')
  and array_length(storage.foldername(name), 1) = 2);

-- Room photos: people in the room, or there in the last day, who haven't blocked the sender (nor been blocked).
create or replace function public.can_read_media(object_name text) returns boolean language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(object_name))[1] = auth.uid()::text then true
    when (storage.foldername(object_name))[1] !~ '^[0-9a-f-]{36}$' then false
    when (storage.foldername(object_name))[2] in ('avatar', 'posts') then public.can_view(((storage.foldername(object_name))[1])::uuid)
    when (storage.foldername(object_name))[2] = 'chat' then exists (
      select 1 from public.messages m join public.matches x on x.id = m.match_id
       where m.media_path = object_name and auth.uid() in (x.user_a, x.user_b)
         and not public.is_blocked(x.user_a, x.user_b))
    when (storage.foldername(object_name))[2] = 'rooms' then exists (
      select 1 from public.voice_messages v join public.voice_members vm on vm.room_id = v.room_id and vm.user_id = auth.uid()
       where v.media_path = object_name and not vm.kicked and (vm.left_at is null or vm.seen_at > now() - interval '1 day')
         and not public.blocked_with(v.sender))
    else false
  end
$$;

-- Files older than a day that nothing points at (013), now also room photos.
create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = o.name)
     and not exists (select 1 from public.posts p where p.image_path = o.name)
     and not exists (select 1 from public.messages m where m.media_path = o.name)
     and not exists (select 1 from public.room_messages m where m.sender_photo = o.name)
     and not exists (select 1 from public.voice_messages m where m.media_path = o.name)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;

-- Reports: what the reported person wrote or posted in a room (photos as their paths). The rest is as in 017.
create or replace function public.report_user(p_reported uuid, p_reason text, p_match uuid default null)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); rid bigint; thread uuid;
begin
  if me is null or p_reported is null or p_reported = me or p_reason = 'auto_nudity' then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.reason <> 'auto_nudity' and r.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  if p_match is not null and not exists (select 1 from public.matches x where x.id = p_match
       and ((x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))) then
    p_match := null;
  end if;
  if not exists (select 1 from public.matches x where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))
     and not public.can_view(p_reported)
     and not exists (select 1 from public.room_messages m join public.room_members rm on rm.room_id = m.room_id and rm.user_id = me
                      where m.sender = p_reported and m.created_at > now() - interval '24 hours')
     and not exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                      where a.user_id = me and b.user_id = p_reported
                        and a.seen_at > now() - interval '24 hours' and b.seen_at > now() - interval '24 hours') then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, match_id, reason) values (me, p_reported, p_match, p_reason) returning id into rid;

  thread := coalesce(p_match, (select x.id from public.matches x
    where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported)
    order by x.created_at desc limit 1));
  update public.reports set evidence = jsonb_build_object(
    'name', (select p.name from public.profiles p where p.id = p_reported),
    'chat', (select coalesce(jsonb_agg(jsonb_build_object('by', case when s.sender = p_reported then 'reported' else 'reporter' end,
                     'body', s.body, 'media', s.media_path, 'at', s.created_at) order by s.id), '[]')
               from (select * from public.messages m where m.match_id = thread order by m.id desc limit 100) s),
    'rooms', (select coalesce(jsonb_agg(jsonb_build_object('room', s.room_id, 'body', s.body, 'at', s.created_at) order by s.id), '[]')
               from (select m.* from public.room_messages m join public.room_members rm on rm.room_id = m.room_id and rm.user_id = me
                      where m.sender = p_reported and m.created_at > now() - interval '24 hours' order by m.id desc limit 50) s),
    'voice', (select coalesce(jsonb_agg(jsonb_build_object('room', s.room_id, 'body', s.body, 'media', s.media_path, 'at', s.created_at) order by s.id), '[]')
               from (select m.* from public.voice_messages m join public.voice_members vm on vm.room_id = m.room_id and vm.user_id = me
                      where m.sender = p_reported and m.kind in ('text', 'photo') and m.created_at > now() - interval '24 hours'
                      order by m.id desc limit 50) s))
   where id = rid;

  insert into public.blocks (blocker, blocked) values (me, p_reported) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_reported) or (user_b = me and user_a = p_reported));
  if (select count(distinct r.reporter) from public.reports r join public.profiles rp on rp.id = r.reporter
       where r.reported = p_reported and r.reason <> 'auto_nudity' and r.created_at > now() - interval '24 hours'
         and rp.created_at < now() - interval '3 days' and (rp.banned_until is null or rp.banned_until < now())) >= 3 then
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), now() + interval '7 days') where id = p_reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and p_reported in (user_a, user_b);
  end if;
end $$;

-- ---------- privileges ----------

revoke execute on function public.voice_kind(text), public.voice_new_code(), public.voice_refusal(public.voice_rooms, uuid, boolean),
  public.voice_admit(text, uuid, boolean), public.voice_post(text, uuid, text, text, jsonb, text)
  from public, anon, authenticated;
revoke execute on function public.voice_list2(), public.create_voice_room2(text, text, int, text, int, int, boolean, text),
  public.join_voice_code(text), public.voice_settings(text, text, text), public.voice_icebreaker(text), public.voice_photo(text, text),
  public.voice_vote(text, text, boolean), public.voice_leaders(text)
  from public, anon;
grant execute on function public.voice_list2(), public.create_voice_room2(text, text, int, text, int, int, boolean, text),
  public.join_voice_code(text), public.voice_settings(text, text, text), public.voice_icebreaker(text), public.voice_photo(text, text),
  public.voice_vote(text, text, boolean), public.voice_leaders(text)
  to authenticated;
