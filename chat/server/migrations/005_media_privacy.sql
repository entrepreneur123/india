-- Review fix: a single match used to let someone list and download every chat photo/video the other
-- person ever sent to anyone. Now: avatars and posts are visible to matched people; chat media only to
-- the two people in the chat where it was sent.
create index messages_media_path on public.messages (media_path) where media_path is not null;

create function public.can_read_media(object_name text) returns boolean language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(object_name))[1] = auth.uid()::text then true
    when (storage.foldername(object_name))[1] !~ '^[0-9a-f-]{36}$' then false
    when (storage.foldername(object_name))[2] in ('avatar', 'posts') then public.can_view(((storage.foldername(object_name))[1])::uuid)
    when (storage.foldername(object_name))[2] = 'chat' then exists (
      select 1 from public.messages m join public.matches x on x.id = m.match_id
       where m.media_path = object_name and auth.uid() in (x.user_a, x.user_b)
         and not public.is_blocked(x.user_a, x.user_b))
    else false
  end
$$;
revoke execute on function public.can_read_media(text) from public, anon;
grant execute on function public.can_read_media(text) to authenticated;

alter policy "read visible media" on storage.objects
  using (bucket_id = 'media' and public.can_read_media(name));
