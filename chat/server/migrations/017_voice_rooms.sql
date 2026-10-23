-- Voice rooms (X Spaces / Clubhouse-style): a host, 8 seats, an audience, chat, reactions and a shared
-- YouTube track. Audio itself goes through LiveKit (room "voice-<id>"): the voice-token function hands out
-- join tokens from the state kept here, and voice-sync applies role changes to people already connected.
-- Nothing is recorded; chat lines are kept 30 days like room chat.
--
-- Rules:
--  * One open room per owner; opening a new one ends the old. 10 new rooms a day.
--  * Members send a heartbeat every ~20 s. Someone not seen for 90 s is out of the room (their seat frees).
--  * The host has 2 minutes (a reload, a dropped network). After that, or when the host leaves, the
--    longest-present co-host becomes the host; with no co-host the room ends.
--  * Seat 0 is the host's; seats 1-8 are the stage. Everyone on a seat may publish audio (LiveKit); people in
--    the audience only listen. Everyone comes up muted. A host or co-host can mute anyone on the stage
--    (the server mutes the LiveKit track); the person can unmute again, as on Clubhouse.
--  * The host is the DJ: plays, pauses, skips, approves suggestions. Everyone else suggests (3 waiting each).
--  * Removed people can't come back to that room. Banned people can't do anything; people blocked with the
--    host can't enter, and lines from people I blocked (or who blocked me) are hidden from me.
-- Every change to the room's state bumps voice_rooms.rev; clients follow that row (realtime) and fetch
-- voice_state() again. Heartbeats don't bump it, so they cost no realtime traffic.

create table public.voice_rooms (
  id text primary key,
  owner uuid not null references public.profiles (id) on delete cascade,
  host uuid not null references public.profiles (id) on delete cascade,
  title text not null check (char_length(title) between 2 and 40),
  category text not null default 'chat'
    check (category in ('chat', 'friends', 'language', 'music', 'movies', 'gaming', 'sports', 'study', 'travel', 'night')),
  speak_mode text not null default 'approval' check (speak_mode in ('approval', 'open')),
  locked_seats smallint[] not null default '{}',
  -- {id, videoId, title, by, byId, startedAt (ms), pausedAt (ms | null)}
  music jsonb,
  -- [{id, videoId, title, by, byId, suggested}], at most 30
  queue jsonb not null default '[]' check (jsonb_typeof(queue) = 'array' and jsonb_array_length(queue) <= 30),
  rev bigint not null default 0,
  created_at timestamptz not null default now(),
  ended_at timestamptz
);
create index voice_rooms_live on public.voice_rooms (created_at desc) where ended_at is null;
create index voice_rooms_owner on public.voice_rooms (owner, created_at desc);

create table public.voice_members (
  room_id text not null references public.voice_rooms (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  role text not null default 'listener' check (role in ('host', 'cohost', 'speaker', 'listener')),
  seat smallint check (seat between 0 and 8),
  muted boolean not null default true,
  hand_at timestamptz,
  joined_at timestamptz not null default now(),
  seen_at timestamptz not null default now(),
  left_at timestamptz,
  kicked boolean not null default false,
  -- When the last "joined" line was written, so rejoining on a bad network doesn't flood the chat.
  joined_line_at timestamptz,
  primary key (room_id, user_id),
  check (seat is null or (left_at is null and not kicked)),
  check (seat is distinct from 0 or role = 'host'),
  check (role <> 'speaker' or seat is not null)
);
create unique index voice_members_seat on public.voice_members (room_id, seat) where seat is not null;
create index voice_members_user on public.voice_members (user_id, seen_at desc);

create table public.voice_messages (
  id bigserial primary key,
  room_id text not null references public.voice_rooms (id) on delete cascade,
  -- Text: who wrote it. System lines: who it is about. Reactions: who reacted.
  sender uuid not null references public.profiles (id) on delete cascade,
  kind text not null default 'text' check (kind in ('text', 'system', 'reaction')),
  body text check (char_length(body) between 1 and 500),
  -- System: {event, name, seat?, title?} (rendered by the apps in their language). Reaction: {kind}.
  payload jsonb,
  sender_name text not null,
  sender_gender text,
  sender_age int,
  sender_photo text,
  sender_country text,
  created_at timestamptz not null default now(),
  check ((kind = 'text') = (body is not null))
);
create index voice_messages_room on public.voice_messages (room_id, id desc);
create index voice_messages_sender on public.voice_messages (sender, created_at desc);

alter table public.voice_rooms enable row level security;
alter table public.voice_members enable row level security;
alter table public.voice_messages enable row level security;

-- Room rows hold nothing private (title, topic, music); clients follow their rev over realtime.
-- No direct writes: everything goes through the functions below. Members have no grant at all.
grant select on public.voice_rooms, public.voice_messages to authenticated;
create policy "voice rooms are public" on public.voice_rooms for select to authenticated using (true);
-- Members are read through voice_state() only (no policies). Lines: anyone who entered and wasn't removed.
create function public.voice_can_read(p_room text) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = auth.uid() and not m.kicked)
$$;
create policy "read voice room" on public.voice_messages for select to authenticated
  using (public.voice_can_read(room_id) and not public.blocked_with(sender));

alter publication supabase_realtime add table public.voice_rooms, public.voice_messages;

-- ---------- helpers (not callable by users) ----------

/** Milliseconds since 1970, the unit the apps use for the music clock. */
create function public.voice_ms() returns bigint language sql volatile set search_path = '' as $$
  select floor(extract(epoch from clock_timestamp()) * 1000)::bigint
$$;

/** Signed in and not banned; returns my id. */
create function public.voice_me() returns uuid language plpgsql stable security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  return me;
end $$;

/** Live: not ended, and the host (or a co-host who can take over) was seen in the last 2 minutes. */
create function public.voice_alive(r public.voice_rooms) returns boolean language sql stable security definer set search_path = '' as $$
  select r.ended_at is null and exists (select 1 from public.voice_members m
    where m.room_id = r.id and m.left_at is null and not m.kicked and m.seen_at > now() - interval '2 minutes'
      and (m.user_id = r.host or m.role = 'cohost'))
$$;

create function public.voice_touch(p_room text) returns void language sql security definer set search_path = '' as $$
  update public.voice_rooms set rev = rev + 1 where id = p_room
$$;

/** A system line about `p_user`: {event, name, ...extra}. */
create function public.voice_line(p_room text, p_user uuid, p_event text, p_extra jsonb default '{}') returns void
language sql security definer set search_path = '' as $$
  insert into public.voice_messages (room_id, sender, kind, payload, sender_name)
  select p_room, p.id, 'system', jsonb_build_object('event', p_event, 'name', p.name) || p_extra, p.name
    from public.profiles p where p.id = p_user
$$;

/** The host is gone: the longest-present co-host takes over, or the room ends. The room row must be locked. */
create function public.voice_hand_over(r public.voice_rooms) returns void language plpgsql security definer set search_path = '' as $$
declare nh uuid;
begin
  select m.user_id into nh from public.voice_members m
   where m.room_id = r.id and m.role = 'cohost' and m.user_id <> r.host and m.left_at is null and not m.kicked
     and m.seen_at > now() - interval '90 seconds'
   order by m.joined_at limit 1;
  if nh is null then
    update public.voice_rooms set ended_at = now(), rev = rev + 1 where id = r.id and ended_at is null;
    return;
  end if;
  update public.voice_members set role = 'listener', seat = null, muted = true, hand_at = null, left_at = coalesce(left_at, now())
   where room_id = r.id and user_id = r.host;
  update public.voice_members set role = 'host', seat = 0, hand_at = null where room_id = r.id and user_id = nh;
  update public.voice_rooms set host = nh, rev = rev + 1 where id = r.id;
  perform public.voice_line(r.id, nh, 'host');
end $$;

/** Moves people not seen for 90 s out of the room, and hands the room over when the host is gone. */
create function public.voice_settle(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms; n int;
begin
  select * into r from public.voice_rooms x where x.id = p_room and x.ended_at is null;
  if not found then return; end if;
  -- Nearly always nothing to do: answer without taking the room's lock.
  if not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id <> r.host
                   and m.left_at is null and not m.kicked and m.seen_at < now() - interval '90 seconds')
     and exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = r.host
                   and m.left_at is null and not m.kicked and m.seen_at > now() - interval '2 minutes') then
    return;
  end if;
  select * into r from public.voice_rooms x where x.id = p_room and x.ended_at is null for update;
  if not found then return; end if;
  update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when m.role = 'speaker' then 'listener' else m.role end
   where m.room_id = p_room and m.user_id <> r.host and m.left_at is null and not m.kicked
     and m.seen_at < now() - interval '90 seconds';
  get diagnostics n = row_count;
  if not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = r.host
                   and m.left_at is null and not m.kicked and m.seen_at > now() - interval '2 minutes') then
    perform public.voice_hand_over(r);
  elsif n > 0 then
    perform public.voice_touch(p_room);
  end if;
end $$;

/** Me in this room, for an action: present and not removed. Locks the room row (actions are serialised). */
create function public.voice_actor(p_room text) returns public.voice_members language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); r public.voice_rooms; m public.voice_members;
begin
  perform public.voice_settle(p_room);
  select * into r from public.voice_rooms x where x.id = p_room for update;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if r.ended_at is not null then raise exception 'room_closed' using errcode = 'P0001'; end if;
  select * into m from public.voice_members x where x.room_id = p_room and x.user_id = me;
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if m.kicked then raise exception 'voice_removed' using errcode = 'P0001'; end if;
  if m.left_at is not null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set seen_at = now() where room_id = p_room and user_id = me;
  return m;
end $$;

/** Another person present in the room (the target of a host action). */
create function public.voice_target(p_room text, p_user uuid) returns public.voice_members language plpgsql stable security definer set search_path = '' as $$
declare m public.voice_members;
begin
  select * into m from public.voice_members x where x.room_id = p_room and x.user_id = p_user
     and x.left_at is null and not x.kicked;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  return m;
end $$;

/** A free, unlocked stage seat: the preferred one if it is free, else the lowest; null when the stage is full. */
create function public.voice_free_seat(p_room text, p_preferred int default null) returns smallint language sql stable security definer set search_path = '' as $$
  select s::smallint from generate_series(1, 8) s, public.voice_rooms r
   where r.id = p_room and not (s::smallint = any (r.locked_seats))
     and not exists (select 1 from public.voice_members m where m.room_id = p_room and m.seat = s)
   order by s is distinct from p_preferred, s limit 1
$$;

/** Puts someone in the audience on a seat: muted, hand down, a speaker (co-hosts stay co-hosts). */
create function public.voice_seat(p_room text, p_user uuid, p_seat smallint) returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.voice_members set seat = p_seat, hand_at = null, muted = true,
         role = case when role = 'listener' then 'speaker' else role end
   where room_id = p_room and user_id = p_user;
  perform public.voice_line(p_room, p_user, 'seat', jsonb_build_object('seat', p_seat));
  perform public.voice_touch(p_room);
end $$;

/** Everything a client shows: the room, the people present (with their cards) and the server clock. */
create function public.voice_state_of(p_room text, me uuid) returns jsonb language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'now', public.voice_ms(), 'locked', to_jsonb(r.locked_seats), 'music', r.music, 'queue', r.queue,
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

/** Enters (or comes back to) a room I may be in. */
create function public.voice_enter(p_room text, me uuid) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms; m public.voice_members; was_here boolean;
begin
  perform public.voice_settle(p_room);
  select * into r from public.voice_rooms x where x.id = p_room for update;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if r.ended_at is not null then raise exception 'room_closed' using errcode = 'P0001'; end if;
  select * into m from public.voice_members x where x.room_id = p_room and x.user_id = me;
  if found and m.kicked then raise exception 'voice_removed' using errcode = 'P0001'; end if;
  if r.host <> me and public.is_blocked(me, r.host) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  was_here := m.user_id is not null and m.left_at is null and m.seen_at > now() - interval '90 seconds';

  -- One room at a time: leave any other room I'm listening or speaking in (a room I host carries on: it has
  -- its 2 minutes, and leaving it is the host's own choice).
  update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when x.role = 'speaker' then 'listener' else x.role end
   where x.user_id = me and x.room_id <> p_room and x.left_at is null and x.role <> 'host';

  insert into public.voice_members (room_id, user_id, role, seat)
  values (p_room, me, case when r.host = me then 'host' else 'listener' end, case when r.host = me then 0 end)
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

-- ---------- for the apps ----------

/** Live rooms for the Voice tab: host card, up to 4 people on the stage, the audience size and what plays. */
create function public.voice_list()
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, now_playing text)
language sql stable security definer set search_path = '' as $$
  with live as (
    select r.* from public.voice_rooms r
     where r.ended_at is null and public.voice_alive(r) and not public.is_blocked(auth.uid(), r.host)
       and not exists (select 1 from public.voice_members k where k.room_id = r.id and k.user_id = auth.uid() and k.kicked)
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
         case when l.music is not null then coalesce(l.music->>'title', '') end
    from live l
   order by l.host = auth.uid() desc,
            (select count(*) from here h where h.room_id = l.id) desc, l.created_at desc
   limit 100
$$;

create function public.create_voice_room(p_title text, p_category text, p_mode text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, '')); rid text;
begin
  if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
  if coalesce(p_category, 'chat') not in ('chat', 'friends', 'language', 'music', 'movies', 'gaming', 'sports', 'study', 'travel', 'night')
     or coalesce(p_mode, 'approval') not in ('approval', 'open') then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  if (select count(*) from public.voice_rooms x where x.owner = me and x.created_at > now() - interval '1 day') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  -- One open room per host: the one I'm hosting ends (a room I opened and handed over carries on).
  update public.voice_rooms x set ended_at = now(), rev = rev + 1 where x.host = me and x.ended_at is null;
  rid := 'v-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
  insert into public.voice_rooms (id, owner, host, title, category, speak_mode)
  values (rid, me, me, t, coalesce(p_category, 'chat'), coalesce(p_mode, 'approval'));
  update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when x.role = 'speaker' then 'listener' else x.role end
   where x.user_id = me and x.left_at is null and x.role <> 'host';
  insert into public.voice_members (room_id, user_id, role, seat) values (rid, me, 'host', 0);
  return rid;
end $$;

/** Enters as a listener (the host comes back to seat 0); returns the room's state. */
create function public.join_voice(p_room text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  perform public.voice_enter(p_room, me);
  return public.voice_state_of(p_room, me);
end $$;

create function public.voice_state(p_room text) returns jsonb language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); m public.voice_members; ended timestamptz;
begin
  perform public.voice_settle(p_room);
  select x.ended_at into ended from public.voice_rooms x where x.id = p_room;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if ended is not null then raise exception 'room_closed' using errcode = 'P0001'; end if;
  select * into m from public.voice_members x where x.room_id = p_room and x.user_id = me;
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if m.kicked then raise exception 'voice_removed' using errcode = 'P0001'; end if;
  return public.voice_state_of(p_room, me);
end $$;

/** "I'm still here", every ~20 s. Comes back in after a long gap (a phone that slept). Returns the room's rev. */
create function public.voice_heartbeat(p_room text) returns bigint language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); m public.voice_members;
begin
  update public.voice_members x set seen_at = now() where x.room_id = p_room and x.user_id = me
     and x.left_at is null and not x.kicked returning * into m;
  if not found then
    perform public.voice_enter(p_room, me);
  else
    perform public.voice_settle(p_room);
  end if;
  if (select x.ended_at from public.voice_rooms x where x.id = p_room) is not null then
    raise exception 'room_closed' using errcode = 'P0001';
  end if;
  return (select x.rev from public.voice_rooms x where x.id = p_room);
end $$;

/** Leaving. The host leaving hands the room to a co-host, or ends it. */
create function public.leave_voice(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); r public.voice_rooms;
begin
  select * into r from public.voice_rooms x where x.id = p_room and x.ended_at is null for update;
  if not found or me is null then return; end if;
  if r.host = me then
    perform public.voice_hand_over(r);
    return;
  end if;
  update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
         role = case when x.role = 'speaker' then 'listener' else x.role end
   where x.room_id = p_room and x.user_id = me and x.left_at is null and not x.kicked;
  if found then
    perform public.voice_line(p_room, me, 'left');
    perform public.voice_touch(p_room);
  end if;
end $$;

create function public.end_voice_room(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_rooms set ended_at = now(), rev = rev + 1 where id = p_room;
end $$;

create function public.voice_send(p_room text, p_text text) returns public.voice_messages
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members; t text := btrim(coalesce(p_text, '')); p public.profiles; msg public.voice_messages;
begin
  if char_length(t) not between 1 and 500 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  m := public.voice_actor(p_room);
  -- Same pace as room chat (room_message_guard): 5 lines in 10 seconds.
  if (select count(*) from public.voice_messages x where x.sender = m.user_id and x.kind = 'text'
        and x.created_at > now() - interval '10 seconds') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  select * into p from public.profiles x where x.id = m.user_id;
  insert into public.voice_messages (room_id, sender, kind, body, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  values (p_room, p.id, 'text', t, p.name, p.gender, extract(year from age(p.birth_date))::int, p.photo_path, p.country)
  returning * into msg;
  return msg;
end $$;

/** A floating reaction. Taps beyond 8 in 5 seconds are dropped quietly. */
create function public.voice_react(p_room text, p_kind text) returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  if p_kind not in ('heart', 'laugh', 'fire', 'clap', 'wow') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.voice_members m join public.voice_rooms r on r.id = m.room_id
                  where m.room_id = p_room and m.user_id = me and m.left_at is null and not m.kicked and r.ended_at is null) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  if (select count(*) from public.voice_messages x where x.sender = me and x.kind = 'reaction'
        and x.created_at > now() - interval '5 seconds') >= 8 then
    return;
  end if;
  insert into public.voice_messages (room_id, sender, kind, payload, sender_name)
  select p_room, p.id, 'reaction', jsonb_build_object('kind', p_kind), p.name from public.profiles p where p.id = me;
end $$;

create function public.voice_hand(p_room text, p_up boolean) returns void language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.seat is not null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set hand_at = case when p_up then coalesce(hand_at, now()) end
   where room_id = p_room and user_id = m.user_id;
  perform public.voice_touch(p_room);
end $$;

/**
 * Tapping a free seat: someone on the stage moves to it; in an open room (or a host/co-host) one takes it;
 * in a hand-raise room a listener raises a hand instead. Returns 'seated', 'moved' or 'hand'.
 */
create function public.voice_take_seat(p_room text, p_seat int) returns text language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); r public.voice_rooms; free smallint;
begin
  select * into r from public.voice_rooms where id = p_room;
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

create function public.voice_leave_seat(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.seat is null or m.role = 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set seat = null, muted = true, role = case when role = 'speaker' then 'listener' else role end
   where room_id = p_room and user_id = m.user_id;
  perform public.voice_line(p_room, m.user_id, 'audience');
  perform public.voice_touch(p_room);
end $$;

/** My mic, as everyone sees it. Only people on a seat have one. */
create function public.voice_mic(p_room text, p_on boolean) returns void language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.seat is null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set muted = not p_on where room_id = p_room and user_id = m.user_id and muted = p_on;
  if found then perform public.voice_touch(p_room); end if;
end $$;

-- ---------- host and co-host tools ----------

create function public.voice_mod(p_room text) returns public.voice_members language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.role not in ('host', 'cohost') then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  return m;
end $$;

/** Brings someone from the audience up to a seat (they come up muted). */
create function public.voice_invite(p_room text, p_user uuid, p_seat int default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room); t public.voice_members := public.voice_target(p_room, p_user); free smallint;
begin
  if t.seat is not null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  free := public.voice_free_seat(p_room, p_seat);
  if free is null or (p_seat is not null and free <> p_seat) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  perform public.voice_seat(p_room, p_user, free);
end $$;

/** A raised hand: invite up (the first free seat), or put the hand down. */
create function public.voice_answer(p_room text, p_user uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room); t public.voice_members := public.voice_target(p_room, p_user); free smallint;
begin
  if t.seat is not null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if not p_accept then
    update public.voice_members set hand_at = null where room_id = p_room and user_id = p_user;
    perform public.voice_touch(p_room);
    return;
  end if;
  free := public.voice_free_seat(p_room);
  if free is null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  perform public.voice_seat(p_room, p_user, free);
end $$;

create function public.voice_to_audience(p_room text, p_user uuid) returns void language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room); t public.voice_members := public.voice_target(p_room, p_user);
begin
  if t.seat is null or t.role = 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set seat = null, muted = true, role = case when role = 'speaker' then 'listener' else role end
   where room_id = p_room and user_id = p_user;
  perform public.voice_line(p_room, p_user, 'audience');
  perform public.voice_touch(p_room);
end $$;

create function public.voice_mute_member(p_room text, p_user uuid) returns void language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room); t public.voice_members := public.voice_target(p_room, p_user);
begin
  if t.seat is null or t.role = 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set muted = true where room_id = p_room and user_id = p_user;
  perform public.voice_touch(p_room);
end $$;

create function public.voice_lock_seat(p_room text, p_seat int, p_on boolean) returns void language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room);
begin
  if p_seat is null or p_seat not between 1 and 8 then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if p_on and exists (select 1 from public.voice_members m where m.room_id = p_room and m.seat = p_seat) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  update public.voice_rooms set rev = rev + 1, locked_seats = case
      when p_on then array(select distinct s from unnest(locked_seats || p_seat::smallint) s order by s)
      else array_remove(locked_seats, p_seat::smallint) end
   where id = p_room;
end $$;

-- Host only: co-hosts and removing people.
create function public.voice_set_cohost(p_room text, p_user uuid, p_on boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_actor(p_room); t public.voice_members;
begin
  if me.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  t := public.voice_target(p_room, p_user);
  if t.role = 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set role = case when p_on then 'cohost' when seat is null then 'listener' else 'speaker' end
   where room_id = p_room and user_id = p_user;
  if p_on and t.role <> 'cohost' then perform public.voice_line(p_room, p_user, 'cohost'); end if;
  perform public.voice_touch(p_room);
end $$;

create function public.voice_kick(p_room text, p_user uuid) returns void language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_actor(p_room); t public.voice_members;
begin
  if me.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  t := public.voice_target(p_room, p_user);
  if t.role = 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set kicked = true, seat = null, hand_at = null, muted = true, left_at = now(),
         role = case when role = 'speaker' then 'listener' else role end
   where room_id = p_room and user_id = p_user;
  perform public.voice_line(p_room, p_user, 'removed');
  perform public.voice_touch(p_room);
end $$;

-- ---------- listen together (the host is the DJ) ----------

create function public.voice_track(p_item jsonb) returns jsonb language sql volatile set search_path = '' as $$
  select jsonb_build_object('id', p_item->>'id', 'videoId', p_item->>'videoId', 'title', coalesce(p_item->>'title', ''),
           'by', p_item->>'by', 'byId', p_item->>'byId', 'startedAt', public.voice_ms(), 'pausedAt', null)
$$;

/** The host adds a track (it plays at once when nothing is playing); anyone else suggests one. */
create function public.voice_queue_add(p_room text, p_video text, p_title text default '') returns text
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
            'title', btrim(coalesce(p_title, '')), 'by', who, 'byId', m.user_id, 'suggested', m.role <> 'host');
  if m.role = 'host' and r.music is null then
    update public.voice_rooms set music = public.voice_track(item), rev = rev + 1 where id = p_room;
    perform public.voice_line(p_room, m.user_id, 'track', jsonb_build_object('title', item->>'title'));
    return 'playing';
  end if;
  if jsonb_array_length(r.queue) >= 30 then raise exception 'queue_full' using errcode = 'P0001'; end if;
  if m.role <> 'host' and (select count(*) from jsonb_array_elements(r.queue) q
                            where q->>'byId' = m.user_id::text and (q->>'suggested')::boolean) >= 3 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  update public.voice_rooms set queue = queue || jsonb_build_array(item), rev = rev + 1 where id = p_room;
  return case when m.role = 'host' then 'queued' else 'suggested' end;
end $$;

create function public.voice_dj(p_room text) returns public.voice_rooms language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); r public.voice_rooms;
begin
  if m.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  select * into r from public.voice_rooms where id = p_room;
  return r;
end $$;

create function public.voice_queue_approve(p_room text, p_item text, p_accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room);
begin
  if not exists (select 1 from jsonb_array_elements(r.queue) q where q->>'id' = p_item) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  update public.voice_rooms set rev = rev + 1, queue = coalesce((
      select jsonb_agg(case when q->>'id' = p_item then q || '{"suggested": false}' else q end order by n)
        from jsonb_array_elements(queue) with ordinality as x(q, n)
       where p_accept or q->>'id' <> p_item), '[]')
   where id = p_room;
end $$;

create function public.voice_queue_remove(p_room text, p_item text) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room);
begin
  update public.voice_rooms set rev = rev + 1, queue = coalesce((
      select jsonb_agg(q order by n) from jsonb_array_elements(queue) with ordinality as x(q, n) where q->>'id' <> p_item), '[]')
   where id = p_room;
end $$;

/** Plays a queued track now (it leaves the queue). */
create function public.voice_play_item(p_room text, p_item text) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room); item jsonb;
begin
  select q into item from jsonb_array_elements(r.queue) q where q->>'id' = p_item;
  if item is null then raise exception 'not_found' using errcode = 'P0001'; end if;
  update public.voice_rooms set rev = rev + 1, music = public.voice_track(item), queue = coalesce((
      select jsonb_agg(q order by n) from jsonb_array_elements(queue) with ordinality as x(q, n) where q->>'id' <> p_item), '[]')
   where id = p_room;
  perform public.voice_line(p_room, auth.uid(), 'track', jsonb_build_object('title', item->>'title'));
end $$;

create function public.voice_pause(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room);
begin
  if r.music is null or r.music->'pausedAt' <> 'null' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_rooms set rev = rev + 1, music = music || jsonb_build_object('pausedAt', public.voice_ms()) where id = p_room;
end $$;

create function public.voice_resume(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room); now_ms bigint := public.voice_ms();
begin
  if r.music is null or r.music->'pausedAt' = 'null' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_rooms set rev = rev + 1, music = music || jsonb_build_object(
      'startedAt', (music->>'startedAt')::bigint + now_ms - (music->>'pausedAt')::bigint, 'pausedAt', null)
   where id = p_room;
end $$;

/**
 * Next track: the first approved one in the queue, or none. `p_current` is the track the caller wants to skip
 * (a player that ended, a tap): when that one isn't playing any more, nothing happens, so two skips never
 * jump two tracks.
 */
create function public.voice_skip(p_room text, p_current text default null) returns void language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room); item jsonb;
begin
  if p_current is not null and r.music->>'id' is distinct from p_current then return; end if;
  select q into item from jsonb_array_elements(r.queue) with ordinality as x(q, n)
   where not coalesce((q->>'suggested')::boolean, false) order by n limit 1;
  if item is null then
    update public.voice_rooms set rev = rev + 1, music = null where id = p_room;
    return;
  end if;
  update public.voice_rooms set rev = rev + 1, music = public.voice_track(item), queue = coalesce((
      select jsonb_agg(q order by n) from jsonb_array_elements(queue) with ordinality as x(q, n) where q->>'id' <> item->>'id'), '[]')
   where id = p_room;
  perform public.voice_line(p_room, (item->>'byId')::uuid, 'track', jsonb_build_object('title', item->>'title'));
end $$;

-- ---------- for the edge functions (voice-token, voice-sync) ----------

/** My LiveKit grant: present in a live room and not banned. No row = no token. */
create function public.voice_grant(p_room text) returns table (role text, seat smallint, muted boolean)
language sql stable security definer set search_path = '' as $$
  select m.role, m.seat, m.muted from public.voice_members m
    join public.voice_rooms r on r.id = m.room_id
    join public.profiles p on p.id = m.user_id
   where m.room_id = p_room and m.user_id = auth.uid() and r.ended_at is null and m.left_at is null and not m.kicked
     and (p.banned_until is null or p.banned_until < now())
$$;

/**
 * What LiveKit should allow, for one person (asked by that person or by the room's host/co-host) or for
 * everyone in the room (host/co-host only). `publish`: may send audio; `remove`: must be disconnected.
 */
create function public.voice_sync_info(p_room text, p_user uuid default null)
returns table (user_id uuid, publish boolean, remove boolean, muted boolean, ended boolean)
language plpgsql stable security definer set search_path = '' as $$
declare me uuid := auth.uid(); r public.voice_rooms; mod boolean;
begin
  select * into r from public.voice_rooms x where x.id = p_room;
  if me is null or not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  mod := r.host = me or exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = me
           and m.role = 'cohost' and m.left_at is null and not m.kicked);
  if not mod and p_user is distinct from me then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  return query
    select m.user_id, r.ended_at is null and m.seat is not null and m.left_at is null and not m.kicked,
           r.ended_at is not null or m.kicked or m.left_at is not null, m.muted, r.ended_at is not null
      from public.voice_members m where m.room_id = p_room and (p_user is null or m.user_id = p_user);
end $$;

-- ---------- visibility and reports ----------

-- People in the same voice room see each other's cards (photo, posts) like room members do, and whoever is on
-- a live stage is visible to everyone (the Voice list shows their faces).
create or replace function public.can_view(other uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.matches m
               where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other))
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid())
                     or (f.requester = auth.uid() and f.addressee = other and f.status = 'accepted'))
      or exists (select 1 from public.room_members a join public.room_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other and not a.kicked
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id
                  where s.user_id = other and s.seat is not null and r.ended_at is null
                    and s.seen_at > now() - interval '2 minutes')))
$$;

-- Reports: people who were in the same voice room in the last day may report each other, and what the
-- reported person wrote there comes with the report. The rest is unchanged from 016.
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

  -- What was said, kept with the report: the chat it came from (else the latest one between the two),
  -- and what the reported person wrote in my rooms and voice rooms in the last day.
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
    'voice', (select coalesce(jsonb_agg(jsonb_build_object('room', s.room_id, 'body', s.body, 'at', s.created_at) order by s.id), '[]')
               from (select m.* from public.voice_messages m join public.voice_members vm on vm.room_id = m.room_id and vm.user_id = me
                      where m.sender = p_reported and m.kind = 'text' and m.created_at > now() - interval '24 hours'
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

revoke execute on function public.voice_ms(), public.voice_me(), public.voice_alive(public.voice_rooms), public.voice_touch(text),
  public.voice_line(text, uuid, text, jsonb), public.voice_hand_over(public.voice_rooms), public.voice_settle(text),
  public.voice_actor(text), public.voice_target(text, uuid), public.voice_free_seat(text, int), public.voice_seat(text, uuid, smallint),
  public.voice_state_of(text, uuid), public.voice_enter(text, uuid), public.voice_mod(text), public.voice_track(jsonb),
  public.voice_dj(text)
  from public, anon, authenticated;
revoke execute on function public.voice_can_read(text), public.voice_list(), public.create_voice_room(text, text, text),
  public.join_voice(text), public.voice_state(text), public.voice_heartbeat(text), public.leave_voice(text),
  public.end_voice_room(text), public.voice_send(text, text), public.voice_react(text, text), public.voice_hand(text, boolean),
  public.voice_take_seat(text, int), public.voice_leave_seat(text), public.voice_mic(text, boolean),
  public.voice_invite(text, uuid, int), public.voice_answer(text, uuid, boolean), public.voice_to_audience(text, uuid),
  public.voice_mute_member(text, uuid), public.voice_lock_seat(text, int, boolean), public.voice_set_cohost(text, uuid, boolean),
  public.voice_kick(text, uuid), public.voice_queue_add(text, text, text), public.voice_queue_approve(text, text, boolean),
  public.voice_queue_remove(text, text), public.voice_play_item(text, text), public.voice_pause(text), public.voice_resume(text),
  public.voice_skip(text, text), public.voice_grant(text), public.voice_sync_info(text, uuid)
  from public, anon;
grant execute on function public.voice_can_read(text), public.voice_list(), public.create_voice_room(text, text, text),
  public.join_voice(text), public.voice_state(text), public.voice_heartbeat(text), public.leave_voice(text),
  public.end_voice_room(text), public.voice_send(text, text), public.voice_react(text, text), public.voice_hand(text, boolean),
  public.voice_take_seat(text, int), public.voice_leave_seat(text), public.voice_mic(text, boolean),
  public.voice_invite(text, uuid, int), public.voice_answer(text, uuid, boolean), public.voice_to_audience(text, uuid),
  public.voice_mute_member(text, uuid), public.voice_lock_seat(text, int, boolean), public.voice_set_cohost(text, uuid, boolean),
  public.voice_kick(text, uuid), public.voice_queue_add(text, text, text), public.voice_queue_approve(text, text, boolean),
  public.voice_queue_remove(text, text), public.voice_play_item(text, text), public.voice_pause(text), public.voice_resume(text),
  public.voice_skip(text, text), public.voice_grant(text), public.voice_sync_info(text, uuid)
  to authenticated;
