-- 6 (the owner, 2026-10-27): the Explore feed shows a friend's post as a full post — likes, comments, my like — so
-- it can be liked there and opened (likers and comments together, 080). explore_feed2 = explore_feed plus the post
-- (post_json, as on profiles and tag pages; null for a highlight). explore_feed stays for clients still running the
-- old build until the wrapper cleanup.

create or replace function public.explore_feed2()
returns table (kind text, item text, user_id uuid, handle text, name text, photo_path text, look jsonb, body text, photos text[],
  at timestamptz, post jsonb)
language sql stable security definer set search_path = '' as $$
  select x.kind, x.item, p.id, p.handle, p.name, p.photo_path, public.look_json(p), x.body, x.photos, x.at,
         case when x.kind = 'post' then (select public.post_json(po) from public.posts po where po.id = x.item::uuid) end
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

revoke execute on function public.explore_feed2() from public, anon;
grant execute on function public.explore_feed2() to authenticated;
