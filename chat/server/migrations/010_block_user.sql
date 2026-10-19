-- Block without reporting (Google Play UGC policy: apps with 1:1 chat need a way to block a user).
-- Same effect as the block part of report_user: their open chats with me end, the friendship goes
-- (unfriend_on_block trigger), and neither of us can message, match or see the other again.
create function public.block_user(p_user uuid) returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = p_user) then raise exception 'not_found' using errcode = 'P0001'; end if;
  insert into public.blocks (blocker, blocked) values (me, p_user) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_user) or (user_b = me and user_a = p_user));
end $$;

revoke execute on function public.block_user(uuid) from public, anon;
grant execute on function public.block_user(uuid) to authenticated;
