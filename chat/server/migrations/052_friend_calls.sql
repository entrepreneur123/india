-- Calls in friends' threads (051): friend DM threads, and random chats that became friends, have ended_at set, so the
-- "chat still open" rule refused them. A call is possible in an open chat or between friends (never when blocked).
create or replace function public.call_open(p_match uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches x where x.id = p_match and auth.uid() in (x.user_a, x.user_b)
                   and not public.is_blocked(x.user_a, x.user_b)
                   and (x.ended_at is null or public.are_friends(x.user_a, x.user_b)))
$$;
revoke execute on function public.call_open(uuid) from public, anon;
grant execute on function public.call_open(uuid) to authenticated;

create or replace function public.request_call(p_match uuid, p_video boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.matches set video_status = 'requested', video_requested_by = auth.uid(), call_video = coalesce(p_video, true)
   where id = p_match and public.call_open(p_match) and video_status in ('none', 'declined');
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;

create or replace function public.respond_video(p_match uuid, p_accept boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.matches set video_status = case when p_accept then 'accepted' else 'declined' end
   where id = p_match and auth.uid() <> video_requested_by and public.call_open(p_match) and video_status = 'requested';
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;
