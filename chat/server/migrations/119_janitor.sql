-- 119: server-side lifetimes (docs/DIAGNOSIS.md #4, #5). Until now only clients ended calls, chats and rooms, so a
-- dead tab left a ring 'requested' for ever (the friend's app kept showing the call), a random chat open with nobody
-- in it, a room "live" with nobody beating (three on 2026-10-31, hosts silent for 250 minutes) and member rows open
-- in ended rooms (81). A cron job every 30 s now ends what no client will. Nothing is removed: rows stay as history
-- (013's retention job removes old ones).

-- When the call started ringing (request_call sets it; rows from before this migration are null = already rung out).
alter table public.matches add column if not exists video_requested_at timestamptz;
create index if not exists matches_ringing on public.matches (video_requested_at) where video_status = 'requested';
create index if not exists voice_members_open on public.voice_members (room_id) where left_at is null;

-- 067's request_call, now stamping the ring.
create or replace function public.request_call(p_match uuid, p_video boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from public.matches x where x.id = p_match and auth.uid() in (x.user_a, x.user_b) and x.ended_at is not null
               and not public.seen_online(case when x.user_a = auth.uid() then x.user_b else x.user_a end)) then
    raise exception 'offline' using errcode = 'P0001';
  end if;
  update public.matches set video_status = 'requested', video_requested_by = auth.uid(), video_requested_at = now(),
         call_video = coalesce(p_video, true)
   where id = p_match and public.call_open(p_match) and video_status in ('none', 'declined');
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;

-- The sweep. Each rule is the one the clients already live by:
--  * a ring lasts 60 s (WhatsApp, Telegram and FaceTime ring about a minute), then the match says 'none' as if the
--    caller had hung up — both apps hear the match row change (107);
--  * a random chat ends once both people have been away 5 minutes (the app stamps user_seen every 30 s while open
--    and -infinity as it closes; while one person is still there their app ends it 12 s after the other vanishes);
--  * a room follows voice_settle (lost 7 s → out, host silent 2 minutes → co-host takes over or the room ends),
--    which until now ran only inside a member's own request, so a room everyone had left never settled;
--  * member rows still open in an ended room are closed (116 closes them as a room ends; these are older).
create or replace function public.janitor() returns void
language plpgsql security definer set search_path = '' as $$
declare r record;
begin
  update public.matches set video_status = 'none'
   where video_status = 'requested' and coalesce(video_requested_at, '-infinity') < now() - interval '60 seconds';

  update public.matches x set ended_at = now(), video_status = 'none'
   where x.ended_at is null and x.kind = 'random' and x.created_at < now() - interval '5 minutes'
     and not exists (select 1 from public.user_seen s where s.user_id in (x.user_a, x.user_b) and s.seen_at > now() - interval '5 minutes');

  for r in
    select x.id from public.voice_rooms x
     where x.ended_at is null
       and exists (select 1 from public.voice_members m where m.room_id = x.id and m.left_at is null and not m.kicked
                     and (m.lost_at is not null
                          or m.seen_at < now() - case when m.fast then interval '6 seconds' else interval '90 seconds' end))
  loop
    begin
      perform public.voice_settle(r.id);
    exception when others then
      raise warning 'janitor: room %: %', r.id, sqlerrm;
    end;
  end loop;

  update public.voice_members m set left_at = now(), lost_at = null, seat = null, hand_at = null, muted = true
   where m.left_at is null and exists (select 1 from public.voice_rooms x where x.id = m.room_id and x.ended_at is not null);
end $$;
revoke execute on function public.janitor() from public, anon, authenticated;

select cron.schedule('yaari-janitor', '30 seconds', $cron$set statement_timeout = '10s'; select public.janitor()$cron$);
