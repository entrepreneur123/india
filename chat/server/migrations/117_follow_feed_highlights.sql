-- The owner, 2026-10-31: a profile like is a follow, and the app now says so. Three lists on a profile besides its
-- friends: who follows them ('likers'), whom they follow ('liked') and mutual follows ('mutual', new); profile_social
-- counts the following too. The feed shows friends' and followed people's posts and highlights, each under its own
-- audience. Highlights get the audience posts have ('friends' by default, 'public'), judged by post_visible (friends
-- and people I chatted with, or anyone); the ones made before stay public, as highlights_of showed them to anyone
-- who could see the profile. The table and function names keep "like": only the words on screen changed.

-- ---- lists ---------------------------------------------------------------------------------------------------------

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
      union all
      select l.liked, greatest(l.created_at, m.created_at)
        from public.profile_likes l join public.profile_likes m on m.liker = l.liked and m.liked = l.liker
       where p_kind = 'mutual' and l.liker = p_user
    ) x
    join public.profiles u on u.id = x.other
   where public.can_view(p_user)
     and not u.anon and (u.banned_until is null or u.banned_until < now()) and not public.is_blocked(auth.uid(), u.id)
   order by x.at desc
   limit 100
$$;

-- The return type grows (following): a function can't change its return type in place and a DROP can't be applied
-- through the MCP (its confirmation question goes unanswered), so profile_social2 is the web's; profile_social (066)
-- stays for Android until the app switches, then both old ones are dropped by hand (HANDOFF).
create or replace function public.profile_social2(p_user uuid)
returns table (likes int, friends int, liked boolean, can_message boolean, following int)
language sql stable security definer set search_path = '' as $$
  select (select count(*) from public.profile_likes l where l.liked = p_user)::int,
         (select count(*) from public.friendships f where f.status = 'accepted' and p_user in (f.requester, f.addressee))::int,
         exists (select 1 from public.profile_likes l where l.liker = auth.uid() and l.liked = p_user),
         p_user <> auth.uid() and not public.is_blocked(auth.uid(), p_user)
           and (public.are_friends(auth.uid(), p_user)
                or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone' and not x.anon)),
         (select count(*) from public.profile_likes l where l.liker = p_user)::int
   where public.can_view(p_user)
$$;
revoke execute on function public.profile_social2(uuid) from public, anon;
grant execute on function public.profile_social2(uuid) to authenticated;

-- ---- highlights' audience ------------------------------------------------------------------------------------------

-- Default 'public': the highlights made before there was a choice were visible to anyone who saw the profile, and an
-- old client (Android) that doesn't send an audience keeps that. The web always sends one ('friends' preselected).
alter table public.highlights add column if not exists visibility text not null default 'public' check (visibility in ('public', 'friends'));

-- save_highlight (057) keeps its three arguments for Android; the web's save_highlight2 takes the audience.
create or replace function public.save_highlight2(p_id bigint, p_title text, p_photos text[], p_visibility text default 'friends') returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := normalize(btrim(coalesce(p_title, '')), NFC); p text[] := coalesce(p_photos, '{}'); hid bigint;
begin
  if char_length(t) not between 1 and 20 or not public.name_plain(t)
     or cardinality(p) not between 1 and 10 or cardinality(p) <> (select count(distinct x) from unnest(p) x)
     or p_visibility not in ('public', 'friends')
     or exists (select 1 from unnest(p) x where x not like me::text || '/avatar/%'
                  or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = x)) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  if p_id is null then
    perform pg_advisory_xact_lock(hashtext('highlights:' || me::text));
    if (select count(*) from public.highlights h where h.user_id = me) >= 10 then raise exception 'not_allowed' using errcode = 'P0001'; end if;
    insert into public.highlights (user_id, title, photos, visibility) values (me, t, p, p_visibility) returning id into hid;
  else
    update public.highlights set title = t, photos = p, visibility = p_visibility where id = p_id and user_id = me returning id into hid;
    if hid is null then raise exception 'not_found' using errcode = 'P0001'; end if;
  end if;
  return hid;
end $$;
revoke execute on function public.save_highlight2(bigint, text, text[], text) from public, anon;
grant execute on function public.save_highlight2(bigint, text, text[], text) to authenticated;

-- Someone's highlights, oldest first: their photos readable (can_read_media's avatar rule) and each one's audience
-- met (post_visible: mine, public, or friends and people I chatted with). highlights_of (057) keeps its shape for
-- Android but filters the same way, so a friends-only album never reaches the old app either.
create or replace function public.highlights_of(p_user uuid) returns table (id bigint, title text, photos text[])
language sql stable security definer set search_path = '' as $$
  select h.id, h.title, h.photos from public.highlights h
   where h.user_id = p_user and public.can_read_media(p_user::text || '/avatar/x') and public.post_visible(p_user, h.visibility)
   order by h.id
$$;
create or replace function public.highlights_of2(p_user uuid) returns table (id bigint, title text, photos text[], visibility text)
language sql stable security definer set search_path = '' as $$
  select h.id, h.title, h.photos, h.visibility from public.highlights h
   where h.user_id = p_user and public.can_read_media(p_user::text || '/avatar/x') and public.post_visible(p_user, h.visibility)
   order by h.id
$$;
revoke execute on function public.highlights_of2(uuid) from public, anon;
grant execute on function public.highlights_of2(uuid) to authenticated;

-- ---- the feed ------------------------------------------------------------------------------------------------------

-- Friends' and followed people's posts and highlights, newest first (60). The people come first (one pass over my
-- friendships and follows), then their posts by index; a friend's post always shows, a followed person's when its
-- audience allows (post_visible). Someone in VIP (anon) shows to friends only, as their profile does (can_view).
create or replace function public.explore_feed2()
returns table (kind text, item text, user_id uuid, handle text, name text, photo_path text, look jsonb, body text, photos text[],
  at timestamptz, post jsonb)
language sql stable security definer set search_path = '' as $$
  with who as (
    select y.id, bool_or(y.friend) as friend from (
      select case when f.requester = auth.uid() then f.addressee else f.requester end as id, true as friend
        from public.friendships f where f.status = 'accepted' and auth.uid() in (f.requester, f.addressee)
      union all
      select l.liked, false from public.profile_likes l where l.liker = auth.uid()
    ) y group by y.id
  )
  select x.kind, x.item, p.id, p.handle, p.name, p.photo_path, public.look_json(p), x.body, x.photos, x.at,
         case when x.kind = 'post' then (select public.post_json(po) from public.posts po where po.id = x.item::uuid) end
    from (
      select 'post' as kind, po.id::text as item, po.user_id as uid, w.friend, po.body, array_remove(array[po.image_path], null) as photos, po.created_at as at
        from who w join public.posts po on po.user_id = w.id
       where w.friend or public.post_visible(po.user_id, po.visibility)
      union all
      select 'highlight', h.id::text, h.user_id, w.friend, h.title, h.photos, h.created_at
        from who w join public.highlights h on h.user_id = w.id
       where w.friend or public.post_visible(h.user_id, h.visibility)
    ) x join public.profiles p on p.id = x.uid
   where (p.banned_until is null or p.banned_until < now()) and (x.friend or not p.anon)
   order by x.at desc
   limit 60
$$;
