-- 1 (the owner, 2026-10-27): the rankings (profile likes, friends) count me too — I see where I stand among my
-- country. Recommended, online and liked still leave me out. Same function as 067 but for that one line.

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
     and (p.id <> me or p_kind in ('likes', 'friends')) and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
     and not (p_kind = 'online' and p.hide_online)
   order by case when p_kind = 'recommend' then random() end, s.cnt desc nulls last, s.at desc nulls last
   limit case when p_kind = 'online' then 100 else 50 end;
end $$;
