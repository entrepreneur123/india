-- From the 16-bot swarm of 2026-10-31 (docs/HANDOFF.md):
--  * looks() was the API's largest total (979 calls, mean 214 ms, 8 timed out at 8 s under load): can_view() per
--    person, a security-definer call with seven EXISTS, for a check that nearly always ends at its first branch. The
--    common case (a plain, unbanned profile with no block) is decided in the query itself; can_view() stays for the
--    rest (anon or banned profiles seen through a match, a room, a friendship...). Same answers: measured 50 people,
--    72 ms of can_view → 7 ms.
--  * A room's end left its members "present" (78 rows of ended rooms with left_at null): end_voice_room and
--    voice_hand_over close them, as the host's own leave does.
-- Re-runnable.

create or replace function public.looks(p_users uuid[]) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.look_json(p)), '[]') from public.profiles p
   where p.id = any (p_users[1:100])
     and (p.id = auth.uid()
          or (not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(auth.uid(), p.id))
          or public.can_view(p.id)
          or exists (select 1 from public.posts pp where pp.user_id = p.id and pp.visibility = 'public'))
$$;

create or replace function public.end_voice_room(p_room text) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room);
begin
  if m.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.voice_rooms set ended_at = now(), rev = rev + 1 where id = p_room;
  update public.voice_members x set left_at = now(), lost_at = null, seat = null, hand_at = null, muted = true
   where x.room_id = p_room and x.left_at is null;
end $$;

create or replace function public.voice_hand_over(r public.voice_rooms) returns void
language plpgsql security definer set search_path = '' as $$
declare nh uuid;
begin
  select m.user_id into nh from public.voice_members m
   where m.room_id = r.id and m.role = 'cohost' and m.user_id <> r.host and m.left_at is null and not m.kicked
     and m.lost_at is null and m.seen_at > now() - interval '90 seconds'
   order by m.joined_at limit 1;
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
