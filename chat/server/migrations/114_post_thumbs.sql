-- Post photos get a 256 px copy beside them, as profile photos have since 073 ("<uid>/posts/<id>.jpg" →
-- "<uid>/posts/<id>_s.jpg", web lib/thumbs.ts): a profile grid tile is 120–200 px wide but downloaded the 1280 px
-- original (2026-10-31, the Me tab: one post, 1280×960). The copy reads with its photo (can_read_media) and is kept
-- while its photo is (stale_media: 062 compared the copy's own name, so a copy counted as stale after a day).
-- Re-runnable.

create or replace function public.can_read_media(object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(object_name))[1] = auth.uid()::text then true
    when (storage.foldername(object_name))[1] !~ '^[0-9a-f-]{36}$' then false
    when (storage.foldername(object_name))[2] = 'cover'
         and exists (select 1 from public.voice_rooms r where r.cover_path = object_name and r.ended_at is null) then true
    when (storage.foldername(object_name))[2] in ('avatar', 'cover') then public.can_view(((storage.foldername(object_name))[1])::uuid)
          and not exists (select 1 from public.profiles x where x.id = ((storage.foldername(object_name))[1])::uuid and x.anon
                            and not public.are_friends(auth.uid(), x.id))
    when (storage.foldername(object_name))[2] = 'posts' then exists (
      select 1 from public.posts p where p.image_path = regexp_replace(object_name, '_s\.jpg$', '.jpg')
         and public.post_visible(p.user_id, p.visibility))
    when (storage.foldername(object_name))[2] = 'chat' then exists (
      select 1 from public.messages m join public.matches x on x.id = m.match_id
       where m.media_path = object_name and auth.uid() in (x.user_a, x.user_b)
         and (x.kind <> 'random' or x.ended_at is null)
         and not public.is_blocked(x.user_a, x.user_b))
    when (storage.foldername(object_name))[2] = 'rooms' then exists (
      select 1 from public.voice_messages v join public.voice_members vm on vm.room_id = v.room_id and vm.user_id = auth.uid()
       where v.media_path = object_name and not vm.kicked and (vm.left_at is null or vm.seen_at > now() - interval '1 day')
         and not public.blocked_with(v.sender))
    else false
  end or public.is_admin()
$$;

create or replace function public.stale_media(p_limit integer default 500) returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   cross join lateral (select regexp_replace(o.name, '_s\.jpg$', '.jpg') as photo) t
   where o.bucket_id = 'media' and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.profiles p where p.photo_path = t.photo)
     and not exists (select 1 from public.profiles p where p.cover_path = t.photo)
     and not exists (select 1 from public.profiles p where p.id::text = (storage.foldername(o.name))[1]
                       and (t.photo = any (p.photos) or t.photo = any (p.cycle_photos)))
     and not exists (select 1 from public.highlights h where h.user_id::text = (storage.foldername(o.name))[1] and t.photo = any (h.photos))
     and not exists (select 1 from public.posts p where p.image_path = t.photo)
     and not exists (select 1 from public.messages m where m.media_path = t.photo)
     and not exists (select 1 from public.voice_messages m where m.media_path = t.photo)
   order by o.created_at
   limit least(greatest(p_limit, 1), 1000)
$$;
