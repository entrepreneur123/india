-- Calls are free (owner, 2026-10-23): a voice or video call in a chat (random or friends), started when the other
-- person accepts. call_video says which: false = voice only (camera off; the apps can turn it on later in the call).
-- request_video (older apps) stays and asks for a video call, now without Plus.
alter table public.matches add column if not exists call_video boolean not null default true;

create or replace function public.request_call(p_match uuid, p_video boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.matches set video_status = 'requested', video_requested_by = auth.uid(), call_video = coalesce(p_video, true)
   where id = p_match and auth.uid() in (user_a, user_b) and ended_at is null and video_status in ('none', 'declined')
     and not public.is_blocked(user_a, user_b);
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;
revoke execute on function public.request_call(uuid, boolean) from public, anon;
grant execute on function public.request_call(uuid, boolean) to authenticated;

create or replace function public.request_video(p_match uuid) returns void
language sql security definer set search_path = '' as $$ select public.request_call(p_match, true) $$;
