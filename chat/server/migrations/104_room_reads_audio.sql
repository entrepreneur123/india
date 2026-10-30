-- Room chat (the owner, 2026-10-30):
-- 1. Read marks: my line shows two ticks once anyone else in the room has read it. Per room, voice_reads keeps the
--    highest line id read (hi) and by whom (by), and the highest read by anyone else (hi2): for a viewer, the
--    furthest read by someone other than them is hi, or hi2 when they are the one who read furthest. Both only grow.
--    voice_read(room, upto) reports how far I have read (clamped to a line that exists) and returns the marks; it
--    writes only when a mark moves (the first two readers of a line), then tells the room (voice_emit 't' = 'k').
-- 2. Voice messages: a line of kind 'audio' (a recorded clip in the media bucket, rooms/ folder), as voice_photo.
-- Re-runnable.

create table if not exists public.voice_reads (
  room_id text primary key references public.voice_rooms (id) on delete cascade,
  hi bigint not null default 0,
  by uuid,
  hi2 bigint not null default 0
);
alter table public.voice_reads enable row level security;

create or replace function public.voice_read(p_room text, p_upto bigint) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); v bigint; r public.voice_reads;
begin
  if not exists (select 1 from public.voice_members m
                  where m.room_id = p_room and m.user_id = me and not m.kicked and m.left_at is null) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select max(x.id) into v from public.voice_messages x where x.room_id = p_room and x.id <= coalesce(p_upto, 0);
  if v is not null then
    insert into public.voice_reads (room_id) select p_room
     where not exists (select 1 from public.voice_reads y where y.room_id = p_room) on conflict do nothing;
    update public.voice_reads x set
      hi2 = case when x.by is distinct from me and v > x.hi then x.hi
                 when x.by is distinct from me then greatest(x.hi2, v) else x.hi2 end,
      hi = greatest(x.hi, v),
      by = case when v > x.hi then me else x.by end
     where x.room_id = p_room and (v > x.hi or (x.by is distinct from me and v > x.hi2))
    returning * into r;
    if found then
      perform public.voice_emit(p_room, jsonb_build_object('t', 'k', 'hi', r.hi, 'by', r.by, 'hi2', r.hi2));
    end if;
  end if;
  if r.room_id is null then select * into r from public.voice_reads y where y.room_id = p_room; end if;
  return jsonb_build_object('hi', coalesce(r.hi, 0), 'by', r.by, 'hi2', coalesce(r.hi2, 0));
end $$;
revoke execute on function public.voice_read(text, bigint) from public, anon;
grant execute on function public.voice_read(text, bigint) to authenticated;

-- Voice messages in rooms.
alter table public.voice_messages drop constraint if exists voice_messages_kind_check;
alter table public.voice_messages add constraint voice_messages_kind_check
  check (kind = any (array['text', 'system', 'reaction', 'icebreaker', 'photo', 'poll', 'audio']));
alter table public.voice_messages drop constraint if exists voice_messages_media;
alter table public.voice_messages add constraint voice_messages_media check ((kind in ('photo', 'audio')) = (media_path is not null));

create or replace function public.voice_audio(p_room text, p_path text) returns public.voice_messages
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if coalesce(p_path, '') !~ ('^' || m.user_id::text || '/rooms/[0-9a-f-]{36}\.(webm|m4a|ogg)$')
     or not exists (select 1 from storage.objects o where o.bucket_id = 'media' and o.name = p_path) then
    raise exception 'bad_media' using errcode = 'P0001';
  end if;
  if (select count(*) from public.voice_messages x where x.sender = m.user_id and x.kind = 'audio'
        and x.created_at > now() - interval '1 minute') >= 6 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return public.voice_post(p_room, m.user_id, 'audio', null, null, p_path);
end $$;
revoke execute on function public.voice_audio(text, text) from public, anon;
grant execute on function public.voice_audio(text, text) to authenticated;
