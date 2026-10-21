-- Cover photos can be uploaded ("<id>/cover/"), and are seen like avatars. A post's photo is seen only by whoever may
-- see that post (post_visible): someone who posts publicly can be looked at by anyone, but their friends-only photos
-- must not be readable (or listable) by everyone who can see their profile.
alter policy "upload own media" on storage.objects with check (
  bucket_id = 'media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (storage.foldername(name))[2] = any (array['avatar', 'chat', 'posts', 'rooms', 'cover'])
  and array_length(storage.foldername(name), 1) = 2);

create index if not exists posts_image on public.posts (image_path) where image_path is not null;

do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.can_read_media(text)'::regprocedure);
  a := 'when (storage.foldername(object_name))[2] in (''avatar'', ''posts'') then public.can_view(((storage.foldername(object_name))[1])::uuid)';
  if position(a in d) = 0 then raise exception 'can_read_media: anchor not found'; end if;
  execute replace(d, a, 'when (storage.foldername(object_name))[2] in (''avatar'', ''cover'') then public.can_view(((storage.foldername(object_name))[1])::uuid)
    when (storage.foldername(object_name))[2] = ''posts'' then exists (
      select 1 from public.posts p where p.image_path = object_name and public.post_visible(p.user_id, p.visibility))');
end $$;
