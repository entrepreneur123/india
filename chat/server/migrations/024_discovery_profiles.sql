-- Finding rooms and people: room language and search, a live "people online" count, inviting a friend into a room,
-- interest tags (shown on cards, preferred in random chat after language) and up to 4 extra profile photos.
-- Android 0.7.0 keeps working: nothing it calls changes its arguments or results; everything here is new functions
-- (voice_list3, create_voice_room3, voice_settings2, ...) or new columns it does not read.

-- Tables are reached through functions only (023 created them; Supabase grants new tables to the API roles).
revoke all on public.user_seen, public.device_tokens from anon, authenticated;
revoke all on public.notifications from anon, authenticated;
grant select on public.notifications to authenticated;

-- ---------- room language ----------
alter table public.voice_rooms add column if not exists lang text
  check (lang in ('en', 'hi', 'bn', 'pt-br', 'vi'));

-- A room speaks its creator's app language unless they choose another one (Android 0.7.0 doesn't send one:
-- its last random-chat language is used).
create or replace function public.voice_room_lang() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.lang is null then
    new.lang := (select q.lang from public.queue q where q.user_id = new.owner);
  end if;
  return new;
end $$;
create or replace trigger voice_rooms_lang before insert on public.voice_rooms for each row execute function public.voice_room_lang();

create or replace function public.voice_lang(p text) returns text
language sql immutable set search_path = '' as $$
  select case when lower(p) in ('en', 'hi', 'bn', 'pt-br', 'vi') then lower(p) end
$$;

create or replace function public.create_voice_room3(p_title text, p_category text, p_max integer, p_gender text, p_min_age integer,
  p_max_age integer, p_private boolean, p_announcement text, p_lang text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare j jsonb;
begin
  j := public.create_voice_room2(p_title, p_category, p_max, p_gender, p_min_age, p_max_age, p_private, p_announcement);
  if public.voice_lang(p_lang) is not null then
    update public.voice_rooms set lang = public.voice_lang(p_lang) where id = j->>'id';
  end if;
  return j;
end $$;

create or replace function public.voice_settings2(p_room text, p_title text, p_announcement text, p_lang text)
returns void language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_settings(p_room, p_title, p_announcement);
  update public.voice_rooms set lang = coalesce(public.voice_lang(p_lang), lang) where id = p_room;
  update public.voice_list_cache set at = '-infinity' where id = 1; -- the list shows it at once
end $$;

-- The list summary (020) also carries the language, for voice_list3.
create or replace function public.voice_list_refresh() returns void
language sql security definer set search_path = '' as $$
  update public.voice_list_cache set at = now(), rooms = coalesce((select jsonb_agg(to_jsonb(x)) from (
    select r.id, r.title, r.category, r.host, r.created_at, r.max_members, r.gender_rule, r.min_age, r.max_age, r.is_private,
           r.announcement, r.lang, case when r.music is not null then coalesce(r.music->>'title', '') end as now_playing,
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

-- voice_list2 with search: title words (any case), language and category; most people first. Nulls = everything.
-- Same rows and rules as voice_list2 otherwise, plus `lang`.
create or replace function public.voice_list3(p_query text default null, p_lang text default null, p_category text default null)
returns table (id text, title text, category text, mine boolean, host jsonb, speakers jsonb, listeners integer, members integer,
               now_playing text, game text, max_members integer, gender_rule text, min_age integer, max_age integer,
               is_private boolean, announcement text, code text, refusal text, lang text)
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
      members int, listeners int, fresh int)
  ), v as (
    select c.*, coalesce(k.present and (k.fresh or c.host = me), false) as here, k.seat as my_seat, coalesce(k.fresh, false) as my_fresh
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
         end,
         v.lang
    from v
   order by v.host = me desc, v.members desc, v.created_at desc
   limit 100;
end $$;

-- ---------- people online ----------
-- "N people online" on the random-chat screen: counted at most every 10 s, whoever asks first does it.
create table if not exists public.app_stats (
  id int primary key check (id = 1),
  online int not null default 0,
  at timestamptz not null default '-infinity'
);
insert into public.app_stats (id) values (1) on conflict do nothing;
alter table public.app_stats enable row level security;
revoke all on public.app_stats from anon, authenticated;

-- People with the app open (023's stamp), searching, or in a room; the room count comes from the list summary.
create or replace function public.online_count() returns integer
language plpgsql security definer set search_path = '' as $$
declare s public.app_stats;
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  select * into s from public.app_stats where id = 1;
  if s.at < now() - interval '10 seconds' and pg_try_advisory_xact_lock(hashtext('online_count')) then
    update public.app_stats set at = now(), online =
      (select count(*) from (select u.user_id from public.user_seen u where u.seen_at > now() - interval '60 seconds'
                             union
                             select q.user_id from public.queue q where q.seen_at > now() - interval '60 seconds') z)
      + coalesce((select sum((r->>'fresh')::int) from public.voice_list_cache c, jsonb_array_elements(c.rooms) r
                   where c.id = 1 and c.at > now() - interval '1 minute'), 0)::int
     where id = 1 returning * into s;
  end if;
  return s.online;
end $$;

-- ---------- invite a friend into a room ----------
-- I'm in the room now and they're my friend: they get an inbox row (and a push, 023). One invite per friend per
-- room every 10 minutes, 30 an hour in all.
create or replace function public.invite_to_room(p_room text, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); r public.voice_rooms;
begin
  select * into r from public.voice_rooms x where x.id = p_room;
  if not found or r.ended_at is not null or not public.voice_alive(r) then raise exception 'room_closed' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.voice_members m where m.room_id = p_room and m.user_id = me and m.left_at is null
                   and not m.kicked and (m.seen_at > now() - interval '90 seconds' or m.user_id = r.host))
     or not public.are_friends(me, p_user) or public.is_blocked(me, p_user) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.notifications o where o.user_id = p_user and o.actor = me and o.kind = 'room_invite'
               and o.room_id = p_room and o.created_at > now() - interval '10 minutes') then
    return; -- already invited: nothing new to tell them
  end if;
  if (select count(*) from public.notifications o where o.actor = me and o.kind = 'room_invite'
        and o.created_at > now() - interval '1 hour') >= 30 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.notifications (user_id, kind, actor, room_id) values (p_user, 'room_invite', me, p_room);
end $$;

-- Opening an invite (24 h): lets me into the room even when it is private (like its code), marks it read and
-- returns the room id; the app then joins as usual (join_voice).
create or replace function public.join_invite(p_id bigint) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); o public.notifications;
begin
  select * into o from public.notifications x where x.id = p_id and x.user_id = me and x.kind = 'room_invite';
  if not found or public.is_blocked(me, o.actor) then raise exception 'not_found' using errcode = 'P0001'; end if;
  update public.notifications set read_at = coalesce(read_at, now()) where id = p_id;
  if o.created_at < now() - interval '24 hours' then raise exception 'room_closed' using errcode = 'P0001'; end if;
  perform public.voice_admit(o.room_id, me, true);
  return o.room_id;
end $$;

-- ---------- interests and photos ----------
alter table public.profiles add column if not exists interests text[] not null default '{}'
  check (cardinality(interests) <= 5 and interests <@ array['music', 'movies', 'cricket', 'football', 'gaming', 'travel', 'food',
    'fitness', 'books', 'tech', 'art', 'dance', 'fashion', 'photography', 'anime', 'pets', 'study', 'business', 'spirituality',
    'comedy']);
-- Up to 4 more photos, each a file in my own avatar folder (so it is readable exactly like the main photo).
alter table public.profiles add column if not exists photos text[] not null default '{}' check (cardinality(photos) <= 4);
alter table public.queue add column if not exists interests text[];

-- 3 to 5 known tags, or none to clear them.
create or replace function public.set_interests(p_tags text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text[] := array(select distinct x from unnest(coalesce(p_tags, '{}')) x);
begin
  if cardinality(t) not in (0, 3, 4, 5) then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.profiles set interests = t where id = me;
exception when check_violation then raise exception 'bad_request' using errcode = 'P0001';
end $$;

-- The gallery in its new order. Every path must be a photo I uploaded to my avatar folder; the app runs the same
-- on-device nudity check on each one as on the main photo.
create or replace function public.set_photos(p_paths text[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p text[] := coalesce(p_paths, '{}');
begin
  if cardinality(p) > 4 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
     or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                  or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  update public.profiles set photos = p where id = me;
end $$;

-- Interests and gallery of the people I may see (same rule as card()), several at once.
create or replace function public.profile_extras(p_users uuid[]) returns table (id uuid, interests text[], photos text[])
language sql stable security definer set search_path = '' as $$
  select p.id, p.interests, p.photos from public.profiles p
   where p.id = any (p_users[1:50]) and public.can_view(p.id)
$$;

-- The gallery files are in use (the nightly media clean-up keeps them).
create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = o.name)
     and not exists (select 1 from public.profiles p where p.id::text = (storage.foldername(o.name))[1] and o.name = any (p.photos))
     and not exists (select 1 from public.posts p where p.image_path = o.name)
     and not exists (select 1 from public.messages m where m.media_path = o.name)
     and not exists (select 1 from public.voice_messages m where m.media_path = o.name)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;

-- ---------- matching: shared interests after language ----------
-- 020's find_match2 with one more sort key: among people of my language, someone sharing an interest comes first,
-- unless someone else has waited over 10 s (they keep their turn). My tags ride along in my queue row.
create or replace function public.find_match2(p_target text, p_lang text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  my_tags text[];
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
  select p.gender, p.banned_until, nullif(p.interests, '{}') into my_gender, banned, my_tags from public.profiles p where p.id = me;
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

  insert into public.queue as q (user_id, gender, target, lang, interests) values (me, my_gender, p_target, my_lang, my_tags)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang, interests = excluded.interests,
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
   order by (q.lang is not distinct from my_lang) desc,
            (coalesce(q.interests && my_tags, false) or q.joined_at < now() - interval '10 seconds') desc,
            q.joined_at
   limit 1
   for update of q skip locked;

  if partner is null then return null; end if;

  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;

revoke execute on function public.voice_room_lang(), public.voice_lang(text), public.voice_list_refresh(), public.stale_media(integer)
  from public, anon, authenticated;
grant execute on function public.stale_media(integer) to service_role;
revoke execute on function public.create_voice_room3(text, text, integer, text, integer, integer, boolean, text, text),
  public.voice_settings2(text, text, text, text), public.voice_list3(text, text, text), public.online_count(),
  public.invite_to_room(text, uuid), public.join_invite(bigint), public.set_interests(text[]), public.set_photos(text[]),
  public.profile_extras(uuid[]), public.find_match2(text, text) from public, anon;
grant execute on function public.create_voice_room3(text, text, integer, text, integer, integer, boolean, text, text),
  public.voice_settings2(text, text, text, text), public.voice_list3(text, text, text), public.online_count(),
  public.invite_to_room(text, uuid), public.join_invite(bigint), public.set_interests(text[]), public.set_photos(text[]),
  public.profile_extras(uuid[]), public.find_match2(text, text) to authenticated;
