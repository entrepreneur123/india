-- Room passwords (the owner, 2026-10-26): a room the host gives a password (4-12 characters) stays in the list with a
-- lock; entering it from the list or a link asks for the password (join_voice's p_password; a wrong one returns null and
-- counts with the room-code tries: 10 misses in 10 minutes = slow_down). People already in, the host, invites and room
-- codes don't need it. Only the host sees and changes it (voice_set_password). Hidden rooms are the private ones (019).

alter table public.voice_rooms add column if not exists password text check (password is null or char_length(password) between 4 and 12);

create or replace function public.voice_set_password(p_room text, p_password text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); pw text := nullif(btrim(coalesce(p_password, '')), '');
begin
  if char_length(pw) not between 4 and 12 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.voice_rooms set password = pw where id = p_room and host = me and ended_at is null;
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  perform public.voice_touch(p_room);
  update public.voice_list_cache set at = '-infinity' where id = 1;
end $$;

drop function if exists public.join_voice(text);
create or replace function public.join_voice(p_room text, p_password text default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); r public.voice_rooms; t public.room_code_tries;
begin
  select * into r from public.voice_rooms x where x.id = p_room;
  if r.password is not null and r.host <> me and r.ended_at is null and p_password is distinct from r.password
     and not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = me and m.left_at is null and not m.kicked) then
    select * into t from public.room_code_tries x where x.user_id = me;
    if t.misses >= 10 and t.since > now() - interval '10 minutes' then raise exception 'slow_down' using errcode = 'P0001'; end if;
    if p_password is not null then
      insert into public.room_code_tries as q (user_id, misses, since) values (me, 1, now())
      on conflict (user_id) do update set
        misses = case when q.since > now() - interval '10 minutes' then q.misses + 1 else 1 end,
        since = case when q.since > now() - interval '10 minutes' then q.since else now() end;
    end if;
    return null;
  end if;
  perform public.voice_enter(p_room, me);
  return public.voice_state_of(p_room, me);
end $$;

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
    'private', r.is_private, 'lang', r.lang,
    'code', case when r.host = me then (select c.code from public.voice_room_codes c where c.room_id = r.id) end,
    'password', case when r.host = me then r.password end,
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
           c.members, c.listeners, c.fresh, r.password is not null as locked
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

-- The list says 'room_password' for a locked room I'm not in (after the reasons that keep me out anyway).
create or replace function public.voice_list3(p_query text default null, p_lang text default null, p_category text default null)
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners int, members int,
  now_playing text, game text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
  announcement text, code text, refusal text, lang text)
language plpgsql security definer set search_path = '' as $$
#variable_conflict use_column
declare me uuid := auth.uid(); q text := nullif(btrim(coalesce(p_query, '')), ''); l text := public.voice_lang(p_lang);
  cat text := case when p_category in ('talk', 'music', 'games') then p_category end;
begin
  if char_length(q) > 40 then raise exception 'bad_request' using errcode = 'P0001'; end if;
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
      max_age int, is_private boolean, announcement text, lang text, now_playing text, game text, host_card jsonb, speakers jsonb,
      members int, listeners int, fresh int, locked boolean)
  ), v as (
    select c.*, coalesce(k.present and (k.fresh or c.host = me), false) as here, k.seat as my_seat, coalesce(k.fresh, false) as my_fresh,
           coalesce(k.present, false) as inside
      from c join public.voice_rooms r on r.id = c.id and r.ended_at is null
      left join k on k.room_id = c.id
     where not coalesce(k.kicked, false)
       and (not c.is_private or c.host = me or k.room_id is not null)
       and (q is null or strpos(lower(c.title), lower(q)) > 0)
       and (l is null or c.lang = l)
       and (cat is null or c.category = cat)
       and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = c.host)
       and not exists (select 1 from public.blocks b where b.blocker = c.host and b.blocked = me)
  )
  select v.id, v.title, v.category, v.host = me, v.host_card, v.speakers,
         v.listeners - (v.here and v.my_seat is null)::int, v.members, v.now_playing, v.game,
         v.max_members, v.gender_rule, v.min_age, v.max_age, v.is_private, v.announcement,
         case when v.host = me then (select x.code from public.voice_room_codes x where x.room_id = v.id) end,
         case
           when v.host = me then null
           when v.gender_rule <> 'any' and (select p.gender from p) is distinct from v.gender_rule then 'room_gender'
           when (select p.age from p) < v.min_age or (select p.age from p) > v.max_age then 'room_age'
           when not v.here and v.fresh - v.my_fresh::int >= v.max_members then 'room_full'
           when coalesce(v.locked, false) and not v.inside then 'room_password'
         end,
         v.lang
    from v
   order by v.host = me desc, v.members desc, v.created_at desc
   limit 100;
end $$;
