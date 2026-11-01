-- Owner 2026-11-01: a host who leaves or loses the connection hands the room to someone still in it — a co-host first, else
-- anyone (on the stage first, then whoever came in first); only an empty room ends. Before leaving, the host may name
-- anyone present as the new host (voice_make_host was co-hosts only). When a disconnected host is replaced stays as it was
-- (voice_settle: 7 s lost with a co-host present, else 2 minutes unseen).
create or replace function public.voice_hand_over(r public.voice_rooms) returns void
language plpgsql security definer set search_path = '' as $$
declare nh uuid;
begin
  select m.user_id into nh from public.voice_members m
   where m.room_id = r.id and m.user_id <> r.host and m.left_at is null and not m.kicked
     and m.lost_at is null and m.seen_at > now() - interval '90 seconds'
   order by m.role = 'cohost' desc, m.seat is not null desc, m.joined_at limit 1;
  if nh is null then
    update public.voice_rooms set ended_at = now(), rev = rev + 1 where id = r.id and ended_at is null;
    update public.voice_members x set left_at = now(), lost_at = null, seat = null, hand_at = null, muted = true
     where x.room_id = r.id and x.left_at is null;
    return;
  end if;
  update public.voice_members set role = 'listener', seat = null, muted = true, hand_at = null, lost_at = null, left_at = coalesce(left_at, now())
   where room_id = r.id and user_id = r.host;
  update public.voice_members set role = 'host', seat = 0, hand_at = null where room_id = r.id and user_id = nh;
  update public.voice_rooms set host = nh, rev = rev + 1 where id = r.id;
  perform public.voice_line(r.id, nh, 'host');
end $$;

create or replace function public.voice_make_host(p_room text, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_actor(p_room); t public.voice_members;
begin
  if me.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  t := public.voice_target(p_room, p_user);
  if t.role = 'host' or t.lost_at is not null then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_members set seat = null where room_id = p_room and user_id = t.user_id;
  update public.voice_members set role = 'cohost', seat = t.seat, muted = case when t.seat is null then true else muted end
   where room_id = p_room and user_id = me.user_id;
  update public.voice_members set role = 'host', seat = 0, hand_at = null where room_id = p_room and user_id = t.user_id;
  update public.voice_rooms set host = t.user_id where id = p_room;
  perform public.voice_line(p_room, t.user_id, 'host');
  perform public.voice_touch(p_room);
end $$;
