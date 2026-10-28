-- Explore's "For you" (059) listed people who are already my friends (the owner, 2026-10-28). Recommendations now leave
-- out my friends and anyone with a friend request between us (either way), as Instagram's suggestions leave out
-- people you follow. explore_people is 078's with that one condition.

create or replace function public.explore_people(p_kind text, p_country text default null)
returns table(id uuid, handle text, name text, photo_path text, age integer, gender text, country text, look jsonb, n integer)
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
  ), top as (
    select p.id, p.handle, p.name, p.photo_path, extract(year from age(p.birth_date))::int as age, p.gender, p.country,
           public.look_json(p) as look, s.cnt, s.at, false as mine
      from s join public.profiles p on p.id = s.uid
     where (p_kind = 'liked' or p.country = c)
       and p.id <> me and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
       and not (p_kind = 'online' and p.hide_online)
       and not (p_kind = 'recommend' and exists (select 1 from public.friendships f
                 where (f.requester = me and f.addressee = p.id) or (f.requester = p.id and f.addressee = me)))
     order by case when p_kind = 'recommend' then random() end, s.cnt desc nulls last, s.at desc nulls last
     limit case when p_kind = 'online' then 100 else 50 end
  )
  select x.id, x.handle, x.name, x.photo_path, x.age, x.gender, x.country, x.look, x.cnt from (
    select * from top
    union all
    select p.id, p.handle, p.name, p.photo_path, extract(year from age(p.birth_date))::int, p.gender, p.country, public.look_json(p),
           case when p_kind = 'likes' then (select count(*)::int from public.profile_likes l where l.liked = me)
                else (select count(*)::int from public.friendships f where f.status = 'accepted' and me in (f.requester, f.addressee)) end,
           null, true
      from public.profiles p
     where p.id = me and p_kind in ('likes', 'friends') and p.country is not distinct from c and not p.anon
  ) x
  order by case when p_kind = 'recommend' then random() end, x.cnt desc nulls last, x.at desc nulls last, x.mine;
end $$;
