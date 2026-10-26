-- The owner, 2026-10-25:
-- 1. One country per person: random chat, rooms, ID search and friend requests only reach people of the same country
--    (profiles.country: the verified phone number's, or the identity document's). The "countries to meet" choice of
--    043 is gone (find_match3 ignores p_countries, queue.want dropped); bots too only match their own country. The
--    "N online" count is the count of my country. Friendships made before stay.
-- 2. Age by country: adult_age() is the age of majority where Google Play's "block minors" (random chat apps, policy of
--    2026-08-26) needs more than 18: South Korea 19 (Civil Act art. 4). Checked at sign-up, after the phone's country is
--    known (set_country is folded into check_adult: triggers run by name and profiles_adult came before profiles_country).
-- 3. IDs exactly like Instagram's: lower-case a-z, digits, "." and "_", 2-30 long, no "." at either end, no "..".
--    Typed capitals are lowered (handle_check, set_handle). All 505 existing IDs already fit (checked 2026-10-25).
-- 4. No free fonts: bio, status and prompt answers get name_plain() like names (fancy-font letters are a look you buy).
-- 5. find_match and find_match2 (wrappers no app calls; both apps use find_match3) are dropped.

-- 1. Same country --------------------------------------------------------------------------------------------------

create or replace function public.find_match3(p_target text, p_lang text, p_countries text[]) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  my_country text;
  my_tags text[];
  banned timestamptz;
  my_lang text := public.app_lang(p_lang);
  my_since timestamptz;
  m public.matches;
  mid uuid;
  last_partner uuid;
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target is null or p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  select p.gender, p.country, p.banned_until, nullif(p.interests, '{}') into my_gender, my_country, banned, my_tags
    from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  perform public.require_verified(me);
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(hashtext('match:' || me::text));
  -- A new search (not a poll of a running one) leaves every room: one live session at a time (031).
  if not exists (select 1 from public.queue q0 where q0.user_id = me and q0.seen_at > now() - interval '20 seconds') then
    perform public.voice_leave_all(me);
  end if;
  perform 1 from public.queue q where q.user_id = me for update;

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

  insert into public.queue as q (user_id, gender, target, lang, interests, country)
  values (me, my_gender, p_target, my_lang, my_tags, my_country)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang, interests = excluded.interests,
    country = excluded.country,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end,
    seen_at = now()
  returning q.joined_at into my_since;

  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then
    update public.queue set seen_at = '-infinity' where user_id = me;
    return mid;
  end if;

  select q.user_id into partner
    from public.queue q
   where q.seen_at > now() - interval '20 seconds'
     and q.user_id <> me
     and q.country = my_country
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = q.user_id)
     and not exists (select 1 from public.blocks b where b.blocker = q.user_id and b.blocked = me)
     and (q.user_id is distinct from last_partner
          or (my_since < now() - interval '10 seconds' and q.joined_at < now() - interval '10 seconds'))
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by (q.lang is not distinct from my_lang) desc,
            (coalesce(q.interests && my_tags, false) or q.joined_at < now() - interval '10 seconds') desc,
            q.joined_at
   limit 1
   for update of q skip locked;

  if partner is null then return null; end if;
  if exists (select 1 from public.profiles p where p.id = partner and p.banned_until > now()) then
    update public.queue set seen_at = '-infinity' where user_id = partner;
    return null;
  end if;

  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;

drop function if exists public.find_match2(text, text);
drop function if exists public.find_match(text);
alter table public.queue drop column if exists want;

create or replace function public.bot_match_people() returns void
language plpgsql security definer set search_path = '' as $$
declare q public.queue; b uuid; last_partner uuid;
begin
  for q in select x.* from public.queue x
            where x.seen_at > now() - interval '15 seconds' and x.joined_at < now() - interval '8 seconds'
              and not exists (select 1 from public.bots y where y.id = x.user_id)
            order by x.joined_at limit 20
  loop
    continue when not pg_try_advisory_xact_lock(hashtext('match:' || q.user_id::text));
    perform 1 from public.queue x where x.user_id = q.user_id and x.seen_at > now() - interval '15 seconds' for update skip locked;
    continue when not found;
    continue when exists (select 1 from public.matches x where x.user_a = q.user_id and x.ended_at is null)
               or exists (select 1 from public.matches x where x.user_b = q.user_id and x.ended_at is null);
    select case when x.user_a = q.user_id then x.user_b else x.user_a end into last_partner
      from public.matches x where q.user_id in (x.user_a, x.user_b) order by x.created_at desc limit 1;
    select y.id into b from public.bots y join public.profiles p on p.id = y.id
     where y.active and p.country = q.country and (q.target = 'any' or p.gender = q.target)
       and y.id is distinct from last_partner
       and not public.is_blocked(y.id, q.user_id)
       and not exists (select 1 from public.matches x where x.user_a = y.id and x.ended_at is null)
       and not exists (select 1 from public.matches x where x.user_b = y.id and x.ended_at is null)
     order by (y.lang = q.lang) desc, random() limit 1;
    continue when b is null;
    update public.queue set seen_at = '-infinity' where user_id = q.user_id;
    insert into public.matches (user_a, user_b) values (q.user_id, b);
  end loop;
end $$;

-- Rooms: a room is its host's country. Entering one of another country (code, link, invite) is 'not_allowed'.
create or replace function public.voice_refusal(r public.voice_rooms, me uuid, p_here boolean) returns text
language plpgsql stable security definer set search_path = '' as $$
declare p public.profiles; my_age int;
begin
 if r.host = me then return null; end if;
 if r.ended_at is not null then return 'room_closed'; end if;
 if public.is_blocked(me, r.host) then return 'not_allowed'; end if;
 select * into p from public.profiles x where x.id = me;
 if (select h.country from public.profiles h where h.id = r.host) is distinct from p.country then return 'not_allowed'; end if;
 my_age := extract(year from age(p.birth_date))::int;
 if r.gender_rule <> 'any' and p.gender is distinct from r.gender_rule then return 'room_gender'; end if;
 if my_age < r.min_age or my_age > r.max_age then return 'room_age'; end if;
 if not p_here and (select count(*) from public.voice_members m where m.room_id = r.id and m.user_id <> me
 and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds') >= r.max_members then
 return 'room_full';
 end if;
 return null;
end $$;

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
    select x.gender, x.country, extract(year from age(x.birth_date))::int as age from public.profiles x where x.id = me
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
      join public.profiles h on h.id = c.host and h.country = (select p.country from p)
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

create or replace function public.request_friend(p_user uuid) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('friend:' || me::text));
  perform public.require_verified(me);
  if not public.can_view(p_user)
     or (select a.country from public.profiles a where a.id = me) is distinct from (select b.country from public.profiles b where b.id = p_user) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  if public.are_friends(me, p_user) then return 'friends'; end if;
  update public.friendships set status = 'accepted' where requester = p_user and addressee = me;
  if found then return 'friends'; end if;
  if (select count(*) from public.friendships f where f.requester = me and f.status = 'pending'
        and f.created_at > now() - interval '1 day') >= 50 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.friendships (requester, addressee) values (me, p_user) on conflict do nothing;
  return 'requested';
end $$;

create or replace function public.search_handles(p_q text)
returns table (id uuid, handle text, name text, photo_path text, country text, look jsonb)
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
  q text := lower(normalize(ltrim(btrim(coalesce(p_q, '')), '@'), NFC));
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if q = '' or char_length(q) > 30 then return; end if;
  if (select count(*) from public.handle_lookups l where l.viewer = me and l.at > now() - interval '1 hour') >= 600 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return query
    with hit as (
      select p.id, p.handle, p.name, p.photo_path, p.country, public.look_json(p) as look from public.profiles p
       where lower(p.handle) collate "C" like replace(replace(replace(q, '\', '\\'), '%', '\%'), '_', '\_') || '%'
         and p.country = (select x.country from public.profiles x where x.id = me)
         and p.id <> me and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
       order by lower(p.handle) collate "C" limit 20
    ), seen as (
      insert into public.handle_lookups (viewer, other) select me, h.id from hit h
      on conflict (viewer, other) do update set at = now()
    )
    select h.id, h.handle, h.name, h.photo_path, h.country, h.look from hit h order by lower(h.handle) collate "C";
end $$;

-- "N online" = people of my country seen in the last minute (app, queue or a room), counted every 10 s for all.
alter table public.app_stats add column if not exists by_country jsonb not null default '{}';
create or replace function public.online_count() returns int
language plpgsql security definer set search_path = '' as $$
declare s public.app_stats;
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  select * into s from public.app_stats where id = 1;
  if s.at < now() - interval '10 seconds' and pg_try_advisory_xact_lock(hashtext('online_count')) then
    update public.app_stats set at = now(), by_country = coalesce((
      select jsonb_object_agg(z.country, z.n) from (
        select p.country, count(*)::int as n from (
          select u.user_id from public.user_seen u where u.seen_at > now() - interval '60 seconds'
          union select q.user_id from public.queue q where q.seen_at > now() - interval '60 seconds'
          union select m.user_id from public.voice_members m
                 where m.left_at is null and not m.kicked and m.seen_at > now() - interval '60 seconds'
        ) o join public.profiles p on p.id = o.user_id where p.country is not null group by p.country) z), '{}')
     where id = 1 returning * into s;
  end if;
  return coalesce((s.by_country ->> (select x.country from public.profiles x where x.id = auth.uid()))::int, 0);
end $$;
alter table public.app_stats drop column if exists online;

-- 2. Age by country ------------------------------------------------------------------------------------------------

create or replace function public.adult_age(p_country text) returns int
language sql immutable set search_path = '' as $$ select case when p_country = 'KR' then 19 else 18 end $$;

create or replace function public.check_adult() returns trigger
language plpgsql security definer set search_path = '' as $$
declare carried timestamptz; kept public.phone_identities;
begin
  select d.country into new.country from auth.users u join public.dial_codes d on u.phone like d.prefix || '%'
   where u.id = new.id order by length(d.prefix) desc limit 1;
  select k.* into kept from public.phone_identities k join auth.users u on k.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and k.saved_at > now() - interval '1 year';
  if kept.phone_hash is not null then new.gender := kept.gender; new.birth_date := kept.birth_date; end if;
  if new.birth_date > (current_date - make_interval(years => public.adult_age(new.country)))::date then
    raise exception 'under_18' using errcode = 'P0001';
  end if;
  if new.birth_date < (current_date - interval '100 years')::date then
    raise exception 'invalid_birth_date' using errcode = 'P0001';
  end if;
  select b.banned_until into carried from public.banned_phones b join auth.users u on b.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and b.banned_until > now();
  if carried is not null then new.banned_until := carried; end if;
  return new;
end $$;
drop trigger if exists profiles_country on public.profiles;
drop function if exists public.set_country();

-- 3. Instagram IDs -------------------------------------------------------------------------------------------------

create or replace function public.handle_valid(p text) returns boolean
language sql immutable set search_path = '' as $$
  select coalesce(p ~ '^[a-z0-9._]{2,30}$' and p ~ '[a-z0-9]' and p !~ '^\.|\.$|\.\.', false)
$$;

create or replace function public.handle_make(p_name text) returns text
language plpgsql volatile set search_path = '' as $$
declare base text; h text;
begin
  base := btrim(left(regexp_replace(regexp_replace(lower(coalesce(p_name, '')), '[^a-z0-9._]', '', 'g'), '\.{2,}', '.', 'g'), 14), '._');
  if char_length(base) < 3 or public.handle_reserved(base) then base := 'user'; end if;
  for i in 1..50 loop
    h := base || '_' || lpad((floor(random() * case when i < 20 then 10000 else 100000000 end))::int::text, case when i < 20 then 4 else 8 end, '0');
    if not exists (select 1 from public.profiles p where lower(p.handle) = lower(h)) then return h; end if;
  end loop;
  return 'user_' || left(replace(gen_random_uuid()::text, '-', ''), 24);
end $$;

alter table public.profiles drop constraint if exists profiles_handle_check;
alter table public.profiles add constraint profiles_handle_check check (public.handle_valid(handle));

create or replace function public.handle_check(p_handle text) returns text
language plpgsql stable security definer set search_path = '' as $$
declare h text := lower(btrim(coalesce(p_handle, ''))); me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not public.handle_valid(h) then return 'invalid'; end if;
  if exists (select 1 from public.profiles p where p.id = me and p.handle = h) then return 'mine'; end if;
  if public.handle_reserved(h) then return 'reserved'; end if;
  if exists (select 1 from public.profiles p where lower(p.handle) = h and p.id <> me) then return 'taken'; end if;
  return 'ok';
end $$;

create or replace function public.set_handle(p_handle text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); h text := lower(btrim(coalesce(p_handle, ''))); cur public.profiles; paid boolean;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('handle:' || h));
  select * into cur from public.profiles p where p.id = me for update;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if not public.handle_valid(h) then raise exception 'handle_invalid' using errcode = 'P0001'; end if;
  if cur.handle = h then return h; end if;
  if public.handle_reserved(h) and h <> lower(cur.handle) then raise exception 'handle_reserved' using errcode = 'P0001'; end if;
  if exists (select 1 from public.profiles p where lower(p.handle) = h and p.id <> me) then
    raise exception 'handle_taken' using errcode = 'P0001';
  end if;
  paid := lower(cur.handle) <> h and cur.handle_set_at > now() - interval '30 days';
  if paid and cur.handle_passes < 1 then raise exception 'handle_cooldown' using errcode = 'P0001'; end if;
  update public.profiles set handle = h,
         handle_set_at = case when lower(cur.handle) = h then cur.handle_set_at else now() end,
         handle_passes = handle_passes - case when paid then 1 else 0 end
   where id = me;
  return h;
exception when unique_violation then raise exception 'handle_taken' using errcode = 'P0001';
end $$;

-- 4. Plain text in the profile ---------------------------------------------------------------------------------------

create or replace function public.profile_name_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.name := normalize(new.name, NFC);
  new.bio := normalize(new.bio, NFC);
  new.status := normalize(new.status, NFC);
  if not public.name_plain(new.name) or public.looks_like_vip(new.name) then raise exception 'name_invalid' using errcode = 'P0001'; end if;
  if not (public.name_plain(new.bio) and public.name_plain(new.status) and public.name_plain(normalize(new.prompts::text, NFC))) then
    raise exception 'text_invalid' using errcode = 'P0001';
  end if;
  return new;
end $$;
drop trigger if exists profiles_name_plain on public.profiles;
create trigger profiles_name_plain before insert or update of name, bio, status, prompts on public.profiles
  for each row execute function public.profile_name_guard();
