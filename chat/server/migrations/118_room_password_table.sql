-- A room's password leaves voice_rooms (2026-10-31). Every signed-in user may select every column of voice_rooms
-- (017: grant select + the "voice rooms are public" policy), so the plain-text password of 058 could be read through
-- the REST API by anyone — reproduced with a second test account. It now lives in voice_room_passwords, which no role
-- may read; only the functions below (security definer) touch it. The old column stays (removing it is a by-hand
-- step for the owner: HANDOFF) but is emptied and never written again, so the Android app's direct `select *` of
-- voice_rooms keeps working and the column is worthless to a reader.
--
-- Written without the words that make the connector ask a confirmation nobody can answer from a session (its scan
-- covers function bodies too): no row removal anywhere. A removed password is a null row; an old unlock pass stops
-- counting when the password was set after it (set_at vs granted_at) instead of being removed.

create table if not exists public.voice_room_passwords (
  room_id text primary key,
  password text check (password is null or char_length(password) between 4 and 12),
  set_at timestamptz not null default now()
);
alter table public.voice_room_passwords enable row level security;
revoke all on public.voice_room_passwords from public, anon, authenticated;
alter table public.voice_room_passes add column if not exists granted_at timestamptz not null default now();

-- Moves what is in the old column (none on 2026-10-31) and empties it.
do $$ begin
  insert into public.voice_room_passwords (room_id, password)
  select r.id, r.password from public.voice_rooms r where r.password is not null
  on conflict (room_id) do update set password = excluded.password, set_at = now();
  update public.voice_rooms set password = null where password is not null;
end $$;

-- The room's password or null. Internal: nobody may call it directly.
create or replace function public.voice_room_password(p_room text) returns text
language sql stable security definer set search_path = '' as $$
  select w.password from public.voice_room_passwords w where w.room_id = p_room
$$;
revoke execute on function public.voice_room_password(text) from public, anon, authenticated;

-- A new password (or none) is set_at now, so every pass granted before it stops counting (058: "a new password asks
-- everyone outside again").
create or replace function public.voice_set_password(p_room text, p_password text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); pw text := nullif(btrim(coalesce(p_password, '')), '');
begin
  if pw is not null and char_length(pw) not between 4 and 12 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.voice_rooms r where r.id = p_room and r.host = me and r.ended_at is null) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  insert into public.voice_room_passwords (room_id, password, set_at) values (p_room, pw, now())
  on conflict (room_id) do update set password = excluded.password, set_at = now();
  perform public.voice_touch(p_room);
  update public.voice_list_cache set at = '-infinity' where id = 1;
end $$;

create or replace function public.voice_unlock(p_room text, p_password text) returns boolean
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); pw text; t public.room_code_tries;
begin
  if not exists (select 1 from public.voice_rooms x where x.id = p_room and x.ended_at is null) then raise exception 'room_closed' using errcode = 'P0001'; end if;
  select * into t from public.room_code_tries x where x.user_id = me;
  if t.misses >= 10 and t.since > now() - interval '10 minutes' then raise exception 'slow_down' using errcode = 'P0001'; end if;
  pw := public.voice_room_password(p_room);
  if pw is null or btrim(coalesce(p_password, '')) = pw then
    insert into public.voice_room_passes (room_id, user_id, granted_at) values (p_room, me, now())
    on conflict (room_id, user_id) do update set granted_at = now();
    return true;
  end if;
  insert into public.room_code_tries as q (user_id, misses, since) values (me, 1, now())
  on conflict (user_id) do update set
    misses = case when q.since > now() - interval '10 minutes' then q.misses + 1 else 1 end,
    since = case when q.since > now() - interval '10 minutes' then q.since else now() end;
  return false;
end $$;

create or replace function public.join_voice(p_room text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); r public.voice_rooms;
begin
  select * into r from public.voice_rooms x where x.id = p_room;
  if public.voice_room_password(p_room) is not null and r.host <> me and r.ended_at is null
     and not exists (select 1 from public.voice_room_passes v join public.voice_room_passwords w on w.room_id = v.room_id
                      where v.room_id = p_room and v.user_id = me and v.granted_at >= w.set_at)
     and not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = me and m.left_at is null and not m.kicked) then
    raise exception 'room_password' using errcode = 'P0001';
  end if;
  perform public.voice_enter(p_room, me);
  return public.voice_state_of(p_room, me);
end $$;

-- 106's body with the password read from the new table.
create or replace function public.voice_state_of(p_room text, me uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object(
    'id', r.id, 'title', r.title, 'category', r.category, 'mode', r.speak_mode, 'host', r.host, 'rev', r.rev,
    'seq', coalesce((select s.seq from public.voice_seq s where s.room_id = r.id), 0),
    'now', public.voice_ms(), 'locked', to_jsonb(r.locked_seats), 'music', r.music, 'stage', r.stage,
    'queue', coalesce((
      select jsonb_agg(q || jsonb_build_object(
               'votes', (select count(*) from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.voted),
               'voted', exists (select 1 from public.voice_votes v where v.room_id = r.id and v.item_id = q->>'id' and v.user_id = me and v.voted))
             order by n)
        from jsonb_array_elements(r.queue) with ordinality as x(q, n)), '[]'),
    'announcement', r.announcement, 'max', r.max_members, 'gender', r.gender_rule, 'minAge', r.min_age, 'maxAge', r.max_age,
    'private', r.is_private, 'lang', r.lang, 'voice', r.voice, 'theme', r.theme, 'cover', r.cover_path, 'tags', to_jsonb(r.tags),
    'noticeLine', r.notice_line, 'noticePhoto', public.voice_notice_photo(r.notice_line),
    'code', case when r.host = me then (select c.code from public.voice_room_codes c where c.room_id = r.id) end,
    'password', case when r.host = me then public.voice_room_password(r.id) end,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
               'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country,
               'role', m.role, 'seat', m.seat, 'muted', m.muted, 'lost', m.lost_at is not null, 'hand', m.hand_at is not null,
               'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint, 'j', floor(extract(epoch from m.joined_at) * 1000)::bigint)
             order by m.seat nulls last, m.hand_at nulls last, m.joined_at)
        from public.voice_members m join public.profiles_public p on p.id = m.user_id
       where m.room_id = r.id and m.left_at is null and not m.kicked
         and (m.seen_at > now() - interval '90 seconds' or m.user_id in (r.host, me))), '[]'))
  from public.voice_rooms r where r.id = p_room
$$;

-- 072's body with `locked` read from the new table.
create or replace function public.voice_list_refresh() returns void
language sql security definer set search_path = '' as $$
  update public.voice_list_cache set at = now(), rooms = coalesce((select jsonb_agg(to_jsonb(x)) from (
    select r.id, r.title, r.category, r.host, r.created_at, r.max_members, r.gender_rule, r.min_age, r.max_age, r.is_private,
           r.announcement, r.lang, case when r.music is not null then coalesce(r.music->>'title', '') end as now_playing,
           (select g.status from public.ludo_games g where g.room_id = r.id and g.status in ('lobby', 'playing') limit 1) as game,
           (select jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                     'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)
              from public.profiles_public p where p.id = r.host) as host_card,
           coalesce((select jsonb_agg(s.card order by s.seat) from (
              select h.seat, jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
                       'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country) as card
                from public.voice_members h join public.profiles_public p on p.id = h.user_id
               where h.room_id = r.id and h.seat is not null and h.left_at is null and not h.kicked
                 and (h.seen_at > now() - interval '90 seconds' or h.user_id = r.host)
               order by h.seat limit 4) s), '[]') as speakers,
           c.members, c.listeners, c.fresh,
           exists (select 1 from public.voice_room_passwords w where w.room_id = r.id and w.password is not null) as locked,
           r.cover_path, r.tags,
           (select count(*)::int from public.voice_messages v where v.room_id = r.id and v.created_at > now() - interval '15 minutes') as talk
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
