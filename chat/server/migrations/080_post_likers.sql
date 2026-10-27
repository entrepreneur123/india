-- 6 (the owner, 2026-10-27): who liked a post, shown with its comments in one sheet. Whoever may see the post may
-- see its likers (post_visible), newest first, 100 at most; people blocked either way, banned or hidden as anonymous
-- aren't listed (the count still has them).

create or replace function public.post_likers(p_post uuid)
returns table (id uuid, handle text, name text, photo_path text)
language sql stable security definer set search_path = '' as $$
  select u.id, u.handle, u.name, u.photo_path
    from public.posts p
    join public.post_likes l on l.post_id = p.id and l.liked
    join public.profiles u on u.id = l.user_id
   where p.id = p_post and public.post_visible(p.user_id, p.visibility)
     and not u.anon and (u.banned_until is null or u.banned_until < now()) and not public.is_blocked(auth.uid(), u.id)
   order by l.at desc
   limit 100
$$;

revoke execute on function public.post_likers(uuid) from public, anon;
grant execute on function public.post_likers(uuid) to authenticated;
