-- Explore (the owner, 2026-10-26): find people by ID or name, a feed of friends' posts and highlights, 50 random people
-- of a chosen country (default mine, again on refresh), rankings by profile likes and by friends, and who is online.
--  * Profiles are open to everyone now (Explore lists them): can_view() lets anyone see a profile that is not incognito
--    (VIP, 046) or banned. Incognito people stay out of Explore.
--  * A profile like = following: anyone may like a profile once (not their own); I can see whom I liked.
--  * Messages: profiles.dm_from 'friends' (default) or 'everyone'. friend_thread() opens a 'dm' thread with a friend, or
--    with anyone whose setting is 'everyone'; such a thread stays open for both (chat_open) and shows in Recent.
--  * At most 50 posts per account.

alter table public.profiles add column if not exists dm_from text not null default 'friends' check (dm_from in ('friends', 'everyone'));

create table if not exists public.profile_likes (
  liker uuid not null references public.profiles(id) on delete cascade,
  liked uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (liker, liked),
  check (liker <> liked)
);
create index if not exists profile_likes_liked on public.profile_likes (liked);
alter table public.profile_likes enable row level security;
revoke all on public.profile_likes from public, anon, authenticated;

create or replace function public.can_view(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.profiles x where x.id = other and not x.anon and (x.banned_until is null or x.banned_until < now()))
      or exists (select 1 from public.handle_lookups l where l.viewer = auth.uid() and l.other = can_view.other and l.at > now() - interval '1 day')
      or exists (select 1 from public.matches m
               where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other))
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid()) or (f.requester = auth.uid() and f.addressee = other))
      or exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other and not a.kicked
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id
                  where s.user_id = other and s.seat is not null and r.ended_at is null
                    and s.seen_at > now() - interval '2 minutes')
      or exists (select 1 from public.notifications n where n.actor = other and n.user_id = auth.uid())))
$$;

-- ---- people lists ----------------------------------------------------------------------------------------------------

-- p_kind: 'recommend' (50 at random), 'online' (seen in the last minute, up to 100), 'likes' / 'friends' (top 50 by
-- profile likes / friends; n = the count), 'liked' (whom I liked, newest first, any country). p_country null = mine.
create or replace function public.explore_people(p_kind text, p_country text default null)
returns table (id uuid, handle text, name text, photo_path text, age int, gender text, country text, look jsonb, n int)
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
  c text := coalesce(upper(nullif(p_country, '')), (select x.country from public.profiles x where x.id = me));
begin
  if p_kind not in ('recommend', 'online', 'likes', 'friends', 'liked') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  return query
  with s as (
    select p.id as uid, null::int as cnt, null::timestamptz as at from public.profiles p where p_kind = 'recommend' and p.country = c
    union all
    select u.user_id, null, u.seen_at from public.user_seen u where p_kind = 'online' and u.seen_at > now() - interval '60 seconds'
    union all
    select l.liked, count(*)::int, null from public.profile_likes l where p_kind = 'likes' group by l.liked
    union all
    select f.uid, count(*)::int, null from (
        select requester as uid from public.friendships where status = 'accepted'
        union all select addressee from public.friendships where status = 'accepted') f
     where p_kind = 'friends' group by f.uid
    union all
    select l.liked, null, l.created_at from public.profile_likes l where p_kind = 'liked' and l.liker = me
  )
  select p.id, p.handle, p.name, p.photo_path, extract(year from age(p.birth_date))::int, p.gender, p.country, public.look_json(p), s.cnt
    from s join public.profiles p on p.id = s.uid
   where (p_kind = 'liked' or p.country = c)
     and p.id <> me and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
   order by case when p_kind = 'recommend' then random() end, s.cnt desc nulls last, s.at desc nulls last
   limit case when p_kind = 'online' then 100 else 50 end;
end $$;

-- Friends' posts and highlights, newest first (60).
create or replace function public.explore_feed()
returns table (kind text, item text, user_id uuid, handle text, name text, photo_path text, look jsonb, body text, photos text[], at timestamptz)
language sql stable security definer set search_path = '' as $$
  select x.kind, x.item, p.id, p.handle, p.name, p.photo_path, public.look_json(p), x.body, x.photos, x.at
    from (
      select 'post' as kind, po.id::text as item, po.user_id as uid, po.body, array_remove(array[po.image_path], null) as photos, po.created_at as at
        from public.posts po where public.are_friends(auth.uid(), po.user_id)
      union all
      select 'highlight', h.id::text, h.user_id, h.title, h.photos, h.created_at
        from public.highlights h where public.are_friends(auth.uid(), h.user_id)
    ) x join public.profiles p on p.id = x.uid
   where p.banned_until is null or p.banned_until < now()
   order by x.at desc
   limit 60
$$;

-- Search by ID or name (prefix, case-blind), every country.
create or replace function public.search_handles(p_q text)
returns table (id uuid, handle text, name text, photo_path text, country text, look jsonb)
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
  q text := lower(normalize(ltrim(btrim(coalesce(p_q, '')), '@'), NFC));
  pat text;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if q = '' or char_length(q) > 30 then return; end if;
  if (select count(*) from public.handle_lookups l where l.viewer = me and l.at > now() - interval '1 hour') >= 600 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  pat := replace(replace(replace(q, '\', '\\'), '%', '\%'), '_', '\_') || '%';
  return query
    with hit as (
      select p.id, p.handle, p.name, p.photo_path, p.country, public.look_json(p) as look from public.profiles p
       where (lower(p.handle) collate "C" like pat or lower(p.name) like pat)
         and p.id <> me and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
       order by lower(p.handle) collate "C" like pat desc, lower(p.handle) collate "C" limit 20
    ), seen as (
      insert into public.handle_lookups (viewer, other) select me, h.id from hit h
      on conflict (viewer, other) do update set at = now()
    )
    select h.id, h.handle, h.name, h.photo_path, h.country, h.look from hit h;
end $$;
create index if not exists profiles_name_lower on public.profiles (lower(name) text_pattern_ops);

-- ---- likes, messages ---------------------------------------------------------------------------------------------------

create or replace function public.like_profile(p_user uuid, p_on boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  if p_user = me or not public.can_view(p_user) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if p_on then insert into public.profile_likes (liker, liked) values (me, p_user) on conflict do nothing;
  else delete from public.profile_likes where liker = me and liked = p_user; end if;
end $$;

-- What a profile shows besides the card: its likes and friends, whether I like it, whether I may message it.
create or replace function public.profile_social(p_user uuid)
returns table (likes int, friends int, liked boolean, can_message boolean)
language sql stable security definer set search_path = '' as $$
  select (select count(*) from public.profile_likes l where l.liked = p_user)::int,
         (select count(*) from public.friendships f where f.status = 'accepted' and p_user in (f.requester, f.addressee))::int,
         exists (select 1 from public.profile_likes l where l.liker = auth.uid() and l.liked = p_user),
         p_user <> auth.uid() and not public.is_blocked(auth.uid(), p_user)
           and (public.are_friends(auth.uid(), p_user) or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone'))
   where public.can_view(p_user)
$$;

create or replace function public.set_dm_from(p_value text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if p_value not in ('friends', 'everyone') then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.profiles set dm_from = p_value where id = public.voice_me();
end $$;

create or replace function public.friend_thread(p_user uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); mid uuid; friends boolean := public.are_friends(me, p_user);
begin
  if public.is_blocked(me, p_user) or not (friends or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone')) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select x.id into mid from public.matches x
   where ((x.user_a = me and x.user_b = p_user) or (x.user_b = me and x.user_a = p_user)) and (friends or x.kind = 'dm')
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by) values (me, p_user, 'dm', now(), me) returning id into mid;
  end if;
  return mid;
end $$;

create or replace function public.chat_open(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and not public.is_blocked(m.user_a, m.user_b)
                 and (m.ended_at is null or public.are_friends(m.user_a, m.user_b)
                      or (m.kind = 'dm' and exists (select 1 from public.profiles x where x.id in (m.user_a, m.user_b) and x.dm_from = 'everyone'))))
$$;

create or replace function public.my_recents()
returns table (match_id uuid, partner uuid, met_at timestamptz, last_body text)
language sql stable security definer set search_path = '' as $$
  select distinct on (o.partner) o.id, o.partner, o.created_at,
         (select coalesce(msg.body, '[media]') from public.messages msg where msg.match_id = o.id order by msg.id desc limit 1)
    from (select x.id, x.created_at, x.kind, case when x.user_a = auth.uid() then x.user_b else x.user_a end as partner
            from public.matches x where auth.uid() in (x.user_a, x.user_b) and x.kind in ('random', 'dm')) o
   where not public.is_blocked(auth.uid(), o.partner)
     and (o.kind = 'random' or not public.are_friends(auth.uid(), o.partner))
     and not exists (select 1 from public.recent_hidden h where h.user_id = auth.uid() and h.partner = o.partner and h.hidden_at > o.created_at)
   order by o.partner, o.created_at desc
$$;

create or replace function public.posts_cap() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform pg_advisory_xact_lock(hashtext('posts:' || new.user_id::text));
  if (select count(*) from public.posts p where p.user_id = new.user_id) >= 50 then raise exception 'too_many_posts' using errcode = 'P0001'; end if;
  return new;
end $$;
create or replace trigger posts_cap before insert on public.posts for each row execute function public.posts_cap();
