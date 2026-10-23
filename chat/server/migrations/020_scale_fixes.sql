-- Scale and correctness fixes from the 2026-10-21 audit.
--  * Matching: a call holds its own queue row from the start, so a user can't end up in two matches (a
--    partner who took the row while this call was on its way is seen, and nobody can take it during the call).
--    The partner search reads only the last 20 s of the line through an index on seen_at (stale rows cost
--    nothing; owner_run_once_3.sql deletes them every 5 minutes) and checks blocks with an anti-join.
--  * Missing foreign-key indexes; voice_members_user without seen_at so heartbeats are HOT updates.
--  * voice_list2: what every viewer sees the same is one summary row, rebuilt at most every 5 s (and when a room
--    opens) by the call that finds it old; each call adds only the viewer's own part.
--  * Rooms send each change itself over Realtime Broadcast (private topic "voice:<room>", event "v", RLS on
--    realtime.messages): a member card, the room's settings/music/queue, or a vote, each with the room's
--    sequence number. Clients apply them and fetch the whole voice_state only on (re)connect or a gap.
--    `rev` is still bumped as before, for the Android app 0.7.0.
--  * Old server surface nobody calls any more is revoked; room_messages leaves the realtime publication.

-- ---------- matching ----------

create index if not exists queue_seen on public.queue (seen_at);

create or replace function public.find_match2(p_target text, p_lang text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  banned timestamptz;
  my_lang text := case when lower(p_lang) in ('en', 'hi', 'bn', 'pt-br', 'vi') then lower(p_lang) end;
  my_since timestamptz;
  m public.matches;
  mid uuid;
  last_partner uuid;
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target is null or p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  select p.gender, p.banned_until into my_gender, banned from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(hashtext('match:' || me::text));
  -- My row in the line, locked first. Someone pairing with me holds it (FOR UPDATE below) until their match is
  -- in: this waits for them, so their match is seen next. From here until this call ends nobody can take me.
  perform 1 from public.queue q where q.user_id = me for update;

  -- Someone already paired with me while I was waiting.
  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then return mid; end if;

  for m in select * from public.matches x where me in (x.user_a, x.user_b) and x.ended_at is null loop
    if now() - m.created_at < public.exit_lock() then raise exception 'exit_locked' using errcode = 'P0001'; end if;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none' where id = m.id;
  end loop;

  select case when x.user_a = me then x.user_b else x.user_a end into last_partner
    from public.matches x where me in (x.user_a, x.user_b) order by x.created_at desc limit 1;

  insert into public.queue as q (user_id, gender, target, lang) values (me, my_gender, p_target, my_lang)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end,
    seen_at = now()
  returning q.joined_at into my_since;

  -- Belt and braces: a match made for me before my row existed (first search) is taken, not a second one.
  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then
    update public.queue set seen_at = '-infinity' where user_id = me;
    return mid;
  end if;

  select q.user_id into partner
    from public.queue q join public.profiles p on p.id = q.user_id
   where q.seen_at > now() - interval '20 seconds'
     and q.user_id <> me
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and (p.banned_until is null or p.banned_until < now())
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = q.user_id)
     and not exists (select 1 from public.blocks b where b.blocker = q.user_id and b.blocked = me)
     and q.user_id is distinct from last_partner
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by (q.lang is not distinct from my_lang) desc, q.joined_at
   limit 1
   for update of q skip locked;

  if partner is null then return null; end if;

  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;

-- ---------- indexes ----------

create index if not exists voice_rooms_host on public.voice_rooms (host);
create index if not exists voice_votes_user on public.voice_votes (user_id);
create index if not exists ludo_games_opened_by on public.ludo_games (opened_by);
-- Heartbeats only set seen_at: with no index on it (and room on the page) they are HOT updates.
create index if not exists voice_members_by_user on public.voice_members (user_id);
-- The old voice_members_user (user_id, seen_at) is dropped in owner_run_once_3.sql (the tools here can't drop).
alter table public.voice_members set (fillfactor = 85);

-- ---------- the rooms list ----------

-- What every viewer sees the same, for all live rooms, rebuilt at most every 5 s (and at once after a room
-- opens) by the call that finds it old. One row, so rebuilding is an UPDATE.
create table if not exists public.voice_list_cache (
  id int primary key check (id = 1),
  at timestamptz not null,
  rooms jsonb not null
);
alter table public.voice_list_cache enable row level security;
insert into public.voice_list_cache (id, at, rooms) values (1, '-infinity', '[]') on conflict do nothing;

create or replace function public.voice_list_refresh() returns void language sql security definer set search_path = '' as $$
  update public.voice_list_cache set at = now(), rooms = coalesce((select jsonb_agg(to_jsonb(x)) from (
    select r.id, r.title, r.category, r.host, r.created_at, r.max_members, r.gender_rule, r.min_age, r.max_age, r.is_private,
           r.announcement, case when r.music is not null then coalesce(r.music->>'title', '') end as now_playing,
           (select g.status from public.ludo_games g where g.room_id = r.id and g.status in ('lobby', 'playing') limit 1) as game,
           (select jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                     'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)
              from public.profiles p where p.id = r.host) as host_card,
           coalesce((select jsonb_agg(s.card order by s.seat) from (
              select h.seat, jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                       'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country) as card
                from public.voice_members h join public.profiles p on p.id = h.user_id
               where h.room_id = r.id and h.seat is not null and h.left_at is null and not h.kicked
                 and (h.seen_at > now() - interval '90 seconds' or h.user_id = r.host)
               order by h.seat limit 4) s), '[]') as speakers,
           c.members, c.listeners, c.fresh
      from public.voice_rooms r
      join lateral (
        select count(*)::int as members, (count(*) filter (where m.seat is null))::int as listeners,
               (count(*) filter (where m.seen_at > now() - interval '90 seconds'))::int as fresh
          from public.voice_members m
         where m.room_id = r.id and m.left_at is null and not m.kicked
           and (m.seen_at > now() - interval '90 seconds' or m.user_id = r.host)) c on true
     where r.ended_at is null
       and exists (select 1 from public.voice_members m where m.room_id = r.id and m.left_at is null and not m.kicked
                     and m.seen_at > now() - interval '2 minutes' and (m.user_id = r.host or m.role = 'cohost'))
     order by c.members desc, r.created_at desc
     limit 1000) x), '[]')
   where id = 1
$$;

/** 019's list from the shared summary, with my part (my room, my place, why I can't come in) added live. */
create or replace function public.voice_list2()
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, members int,
               now_playing text, game text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
               announcement text, code text, refusal text)
language plpgsql security definer set search_path = '' as $$
#variable_conflict use_column
declare me uuid := auth.uid();
begin
  if (select c.at from public.voice_list_cache c where c.id = 1) < now() - interval '5 seconds'
     and pg_try_advisory_xact_lock(hashtext('voice_list_refresh')) then
    perform public.voice_list_refresh();
  end if;
  return query
  with p as (
    select x.gender, extract(year from age(x.birth_date))::int as age from public.profiles x where x.id = me
  ), k as (
    select m.room_id, m.kicked, m.seat,
           m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds' as fresh,
           m.left_at is null and not m.kicked as present
      from public.voice_members m where m.user_id = me
  ), c as (
    select * from jsonb_to_recordset((select y.rooms from public.voice_list_cache y where y.id = 1)) as x(
      id text, title text, category text, host uuid, created_at timestamptz, max_members int, gender_rule text, min_age int,
      max_age int, is_private boolean, announcement text, now_playing text, game text, host_card jsonb, speakers jsonb,
      members int, listeners int, fresh int)
  ), v as (
    select c.*, coalesce(k.present and (k.fresh or c.host = me), false) as here, k.seat as my_seat, coalesce(k.fresh, false) as my_fresh
      from c join public.voice_rooms r on r.id = c.id and r.ended_at is null
      left join k on k.room_id = c.id
     where not coalesce(k.kicked, false)
       and (not c.is_private or c.host = me or k.room_id is not null)
       and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = c.host)
       and not exists (select 1 from public.blocks b where b.blocker = c.host and b.blocked = me)
  )
  select v.id, v.title, v.category, v.host = me, v.host_card, v.speakers,
         v.listeners - (v.here and v.my_seat is null)::int, v.members, v.now_playing, v.game,
         v.max_members, v.gender_rule, v.min_age, v.max_age, v.is_private, v.announcement,
         case when v.host = me then (select x.code from public.voice_room_codes x where x.room_id = v.id) end,
         -- voice_refusal() without its count (the summary has it)
         case
           when v.host = me then null
           when v.gender_rule <> 'any' and (select p.gender from p) is distinct from v.gender_rule then 'room_gender'
           when (select p.age from p) < v.min_age or (select p.age from p) > v.max_age then 'room_age'
           when not v.here and v.fresh - v.my_fresh::int >= v.max_members then 'room_full'
         end
    from v
   order by v.host = me desc, v.members desc, v.created_at desc
   limit 100;
end $$;

/** A room just opened shows in the list at once (its host looks for it there). */
create or replace function public.voice_list_stale() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.voice_list_cache set at = '-infinity' where id = 1;
  return null;
end $$;
create or replace trigger voice_rooms_list_stale after insert on public.voice_rooms for each statement execute function public.voice_list_stale();
revoke execute on function public.voice_list_refresh(), public.voice_list_stale() from public, anon, authenticated;

-- ---------- room changes over Realtime Broadcast ----------

-- Each room's change counter: every event carries the next number, so a client that misses one knows.
create table if not exists public.voice_seq (
  room_id text primary key references public.voice_rooms (id) on delete cascade,
  seq bigint not null default 0
);
alter table public.voice_seq enable row level security;
grant select on public.voice_seq to authenticated;
do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'voice_seq') then
    create policy "room counters are public" on public.voice_seq for select to authenticated using (true);
  end if;
end $$;
insert into public.voice_seq (room_id) select r.id from public.voice_rooms r on conflict do nothing;

create or replace function public.voice_seq_new() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.voice_seq (room_id) values (new.id) on conflict do nothing;
  return null;
end $$;
create or replace trigger voice_rooms_seq after insert on public.voice_rooms for each row execute function public.voice_seq_new();

/**
 * Sends one change to the people in the room. Changes to a room are made with its row locked, so the counter is
 * free; the one exception (leaving a room to enter another) skips the number rather than wait for it (no
 * deadlock between two rooms): such an event goes out with seq null and clients re-fetch the state.
 */
create or replace function public.voice_emit(p_room text, p_event jsonb) returns void language plpgsql security definer set search_path = '' as $$
declare s bigint;
begin
  update public.voice_seq x set seq = x.seq + 1
   where x.room_id = (select y.room_id from public.voice_seq y where y.room_id = p_room for update skip locked)
  returning x.seq into s;
  perform realtime.send(p_event || jsonb_build_object('seq', s), 'v', 'voice:' || p_room, true);
end $$;

/** A member as voice_state shows them (present), or just the id (gone). */
create or replace function public.voice_member_json(m public.voice_members) returns jsonb language sql stable security definer set search_path = '' as $$
  select case when m.left_at is null and not m.kicked then
    jsonb_build_object('t', 'm', 'id', m.user_id, 'present', true, 'role', m.role, 'seat', m.seat, 'muted', m.muted,
      'hand', m.hand_at is not null, 'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint,
      'j', floor(extract(epoch from m.joined_at) * 1000)::bigint,
      'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int, 'city', p.city, 'bio', p.bio,
      'photo_path', p.photo_path, 'country', p.country)
  else jsonb_build_object('t', 'm', 'id', m.user_id, 'present', false) end
  from public.profiles p where p.id = m.user_id
$$;

create or replace function public.voice_member_event() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_emit(new.room_id, public.voice_member_json(new));
  return null;
end $$;
create or replace trigger voice_members_event_insert after insert on public.voice_members for each row execute function public.voice_member_event();
-- Heartbeats (seen_at only) send nothing.
create or replace trigger voice_members_event_update after update on public.voice_members for each row
  when (old.role is distinct from new.role or old.seat is distinct from new.seat or old.muted is distinct from new.muted
        or old.hand_at is distinct from new.hand_at or (old.left_at is null) <> (new.left_at is null) or old.kicked <> new.kicked)
  execute function public.voice_member_event();

/** The room's own part of voice_state (no members; `voted` is each client's own, the code is the host's). */
create or replace function public.voice_room_event() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_emit(new.id, jsonb_build_object(
    't', 'r', 'title', new.title, 'category', new.category, 'mode', new.speak_mode, 'host', new.host,
    'locked', to_jsonb(new.locked_seats), 'music', new.music,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = new.id and v.item_id = q->>'id' and v.voted))
             order by n)
        from jsonb_array_elements(new.queue) with ordinality as x(q, n)), '[]'),
    'announcement', new.announcement, 'max', new.max_members, 'gender', new.gender_rule, 'minAge', new.min_age,
    'maxAge', new.max_age, 'private', new.is_private, 'ended', new.ended_at is not null));
  return null;
end $$;
create or replace trigger voice_rooms_event after update on public.voice_rooms for each row
  when ((old.title, old.category, old.speak_mode, old.host, old.locked_seats, old.music, old.queue, old.announcement,
         old.max_members, old.gender_rule, old.min_age, old.max_age, old.is_private, old.ended_at)
        is distinct from
        (new.title, new.category, new.speak_mode, new.host, new.locked_seats, new.music, new.queue, new.announcement,
         new.max_members, new.gender_rule, new.min_age, new.max_age, new.is_private, new.ended_at))
  execute function public.voice_room_event();

create or replace function public.voice_vote_event() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_emit(new.room_id, jsonb_build_object('t', 'v', 'item', new.item_id, 'user', new.user_id, 'on', new.voted,
    'votes', (select count(*) from public.voice_votes v where v.room_id = new.room_id and v.item_id = new.item_id and v.voted)));
  return null;
end $$;
create or replace trigger voice_votes_event after insert or update on public.voice_votes for each row execute function public.voice_vote_event();

-- Who may listen: anyone who entered the room and wasn't removed (the same rule as reading its chat).
do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'voice room events') then
    create policy "voice room events" on realtime.messages for select to authenticated
      using (realtime.messages.extension = 'broadcast' and realtime.topic() like 'voice:%'
             and public.voice_can_read(substr(realtime.topic(), 7)));
  end if;
end $$;

/** 019's state plus the room's change number (`seq`) and, per member, when they raised a hand and came in (ms). */
create or replace function public.voice_state_of(p_room text, me uuid) returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'seq', coalesce((select s.seq from public.voice_seq s where s.room_id = r.id), 0),
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
               'role', m.role, 'seat', m.seat, 'muted', m.muted, 'hand', m.hand_at is not null,
               'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint, 'j', floor(extract(epoch from m.joined_at) * 1000)::bigint)
             order by m.seat nulls last, m.hand_at nulls last, m.joined_at)
        from public.voice_members m join public.profiles p on p.id = m.user_id
       where m.room_id = r.id and m.left_at is null and not m.kicked
         and (m.seen_at > now() - interval '90 seconds' or m.user_id in (r.host, me))), '[]'))
  from public.voice_rooms r where r.id = p_room
$$;

/** voice_heartbeat, answering with the room's change number (the web app compares it with what it applied). */
create or replace function public.voice_heartbeat2(p_room text) returns bigint language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_heartbeat(p_room);
  return coalesce((select s.seq from public.voice_seq s where s.room_id = p_room), 0);
end $$;

revoke execute on function public.voice_seq_new(), public.voice_emit(text, jsonb), public.voice_member_json(public.voice_members),
  public.voice_member_event(), public.voice_room_event(), public.voice_vote_event()
  from public, anon, authenticated;
revoke execute on function public.voice_heartbeat2(text) from public, anon;
grant execute on function public.voice_heartbeat2(text) to authenticated;

-- ---------- old surface ----------

-- Nothing calls these any more (web, Android 0.7.0, edge functions). Old tester APKs (0.6.0 and before) stop
-- working in rooms; the tables of the old rooms go in owner_run_once_3.sql.
revoke execute on function
  public.create_room(text, text, integer, text, integer, integer, boolean), public.join_room(text), public.join_room_code(text),
  public.leave_room(text), public.close_room(text), public.room_list(), public.room_list2(), public.room_online(text),
  public.create_voice_room(text, text, text), public.voice_list(), public.find_match(text), public.profile_card(uuid),
  public.recent_cards(), public.my_recents(), public.friend_cards(), public.my_premium_until(),
  public.voice_queue_approve(text, text, boolean)
  from public, anon, authenticated;

-- room_messages leaves the realtime publication (listed by name: the tools here can't run the other form).
alter publication supabase_realtime set table public.matches, public.messages, public.voice_rooms, public.voice_messages, public.ludo_games;
