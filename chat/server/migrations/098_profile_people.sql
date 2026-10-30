-- The owner, 2026-10-30: tap a profile's counts to see the people, as Instagram's followers / following, on anyone's
-- profile I may see (can_view). Three lists: friends, who liked them, whom they liked; newest first, 100 at most.
-- Like post_likers (059): people in VIP (anon), banned or blocked with me are left out.

create or replace function public.profile_people(p_user uuid, p_kind text) returns table(id uuid, handle text, name text, photo_path text)
language sql stable security definer set search_path = '' as $$
  select u.id, u.handle, u.name, u.photo_path
    from (
      select case when f.requester = p_user then f.addressee else f.requester end as other, f.created_at as at
        from public.friendships f
       where p_kind = 'friends' and f.status = 'accepted' and p_user in (f.requester, f.addressee)
      union all
      select l.liker, l.created_at from public.profile_likes l where p_kind = 'likers' and l.liked = p_user
      union all
      select l.liked, l.created_at from public.profile_likes l where p_kind = 'liked' and l.liker = p_user
    ) x
    join public.profiles u on u.id = x.other
   where public.can_view(p_user)
     and not u.anon and (u.banned_until is null or u.banned_until < now()) and not public.is_blocked(auth.uid(), u.id)
   order by x.at desc
   limit 100
$$;
revoke execute on function public.profile_people(uuid, text) from public, anon;
grant execute on function public.profile_people(uuid, text) to authenticated;
