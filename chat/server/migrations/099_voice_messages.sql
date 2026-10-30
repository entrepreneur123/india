-- Voice messages in 1:1 chats (the owner, 2026-10-30): a recorded clip goes up as chat media like a photo, kind
-- 'audio'. The browser records Opus in WebM (Chrome, Firefox, Android) or AAC in MP4 (Safari, iPhone); the app sends
-- the plain type (no ";codecs=") and keeps clips to 60 s. Re-runnable.

alter table public.messages drop constraint if exists messages_media_kind_check;
alter table public.messages add constraint messages_media_kind_check check (media_kind = any (array['image', 'video', 'audio']));

update storage.buckets
   set allowed_mime_types = (select array_agg(distinct t order by t) from unnest(allowed_mime_types || array['audio/webm', 'audio/mp4', 'audio/ogg']) t)
 where id = 'media';
