-- 5 (the owner, 2026-10-27): the host can hand the room to one of its co-hosts (only a co-host). They swap places:
-- the new host takes the host's seat (0), the old host stays a co-host in the new host's former seat, or goes to the
-- audience (mic off) when the new host had none. The room hears it like an automatic hand-over (voice_line 'host').
-- voice_actor locks the room row, so two hand-overs (or a hand-over and the host leaving) can't cross.

create or replace function public.voice_make_host(p_room text, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_actor(p_room); t public.voice_members;
begin
  if me.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  t := public.voice_target(p_room, p_user);
  if t.role <> 'cohost' or t.lost_at is not null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set seat = null where room_id = p_room and user_id = t.user_id;
  update public.voice_members set role = 'cohost', seat = t.seat, muted = case when t.seat is null then true else muted end
   where room_id = p_room and user_id = me.user_id;
  update public.voice_members set role = 'host', seat = 0, hand_at = null where room_id = p_room and user_id = t.user_id;
  update public.voice_rooms set host = t.user_id where id = p_room;
  perform public.voice_line(p_room, t.user_id, 'host');
  perform public.voice_touch(p_room);
end $$;

revoke execute on function public.voice_make_host(text, uuid) from public, anon;
grant execute on function public.voice_make_host(text, uuid) to authenticated;
