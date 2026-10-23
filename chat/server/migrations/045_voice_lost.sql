-- Rooms: a dropped connection shows at once and ends soon. New apps beat every 3 s while in a room with
-- voice_heartbeat3 (also with the screen off: Android keeps a foreground service). No beat for 6 s: "lost" (everyone
-- sees "connection lost" on that person); still lost 7 s later: out of the room. Apps that still beat every 20 s
-- (voice_heartbeat2, installed before 0.17) keep the 90 s rule. A host who is lost hands the room to a co-host after those 7 s;
-- with no co-host the room waits for them as before (2 minutes) rather than ending on a short drop.

alter table public.voice_members add column if not exists lost_at timestamptz, add column if not exists fast boolean not null default false;

create or replace trigger voice_members_event_update after update on public.voice_members for each row
  when (old.role is distinct from new.role or old.seat is distinct from new.seat or old.muted is distinct from new.muted
        or old.hand_at is distinct from new.hand_at or (old.left_at is null) <> (new.left_at is null) or old.kicked <> new.kicked
        or (old.lost_at is null) <> (new.lost_at is null))
  execute function public.voice_member_event();

create or replace function public.voice_member_json(m public.voice_members) returns jsonb
language sql stable security definer set search_path = '' as $$
  select case when m.left_at is null and not m.kicked then
    jsonb_build_object('t', 'm', 'id', m.user_id, 'present', true, 'role', m.role, 'seat', m.seat, 'muted', m.muted,
      'lost', m.lost_at is not null,
      'hand', m.hand_at is not null, 'ha', floor(extract(epoch from m.hand_at) * 1000)::bigint,
      'j', floor(extract(epoch from m.joined_at) * 1000)::bigint,
      'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int, 'city', p.city, 'bio', p.bio,
      'photo_path', p.photo_path, 'country', p.country)
  else jsonb_build_object('t', 'm', 'id', m.user_id, 'present', false) end
  from public.profiles p where p.id = m.user_id
$$;

-- The full state says it too (one field added to voice_state_of's member objects).
do $$ begin
  execute replace(pg_get_functiondef('public.voice_state_of'::regproc),
    '''muted'', m.muted, ''hand''', '''muted'', m.muted, ''lost'', m.lost_at is not null, ''hand''');
end $$;

create or replace function public.voice_beat(p_room text, p_fast boolean) returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); m public.voice_members;
begin
  update public.voice_members x set seen_at = now(), lost_at = null, fast = p_fast where x.room_id = p_room and x.user_id = me
     and x.left_at is null and not x.kicked returning * into m;
  if not found then
    if exists (select 1 from public.matches x where x.ended_at is null and x.kind = 'random' and (x.user_a = me or x.user_b = me)) then
      raise exception 'in_random_chat' using errcode = 'P0001';
    end if;
    perform public.voice_enter(p_room, me);
    update public.voice_members x set fast = p_fast where x.room_id = p_room and x.user_id = me;
  else
    perform public.voice_settle(p_room);
  end if;
  if (select x.ended_at from public.voice_rooms x where x.id = p_room) is not null then
    raise exception 'room_closed' using errcode = 'P0001';
  end if;
  return (select x.rev from public.voice_rooms x where x.id = p_room);
end $$;
revoke execute on function public.voice_beat(text, boolean) from public, anon, authenticated;

create or replace function public.voice_heartbeat(p_room text) returns bigint
language sql security definer set search_path = '' as $$ select public.voice_beat(p_room, false) $$;

-- New apps: every 3 s; answers like voice_heartbeat2 (the room's change number).
create or replace function public.voice_heartbeat3(p_room text) returns bigint
language plpgsql security definer set search_path = '' as $$
begin
  perform public.voice_beat(p_room, true);
  return coalesce((select s.seq from public.voice_seq s where s.room_id = p_room), 0);
end $$;
revoke execute on function public.voice_heartbeat3(text) from public, anon;
grant execute on function public.voice_heartbeat3(text) to authenticated;

create or replace function public.voice_settle(p_room text) returns void
language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms; n int; host_gone boolean;
begin
  select * into r from public.voice_rooms x where x.id = p_room and x.ended_at is null;
  if not found then return; end if;
  -- Nearly always nothing to do: answer without taking the room's lock.
  if not exists (select 1 from public.voice_members m where m.room_id = p_room and m.left_at is null and not m.kicked
                   and (m.lost_at is not null or m.seen_at < now() - case when m.fast then interval '6 seconds' else interval '90 seconds' end)) then
    return;
  end if;
  select * into r from public.voice_rooms x where x.id = p_room and x.ended_at is null for update;
  if not found then return; end if;
  update public.voice_members m set lost_at = now()
   where m.room_id = p_room and m.left_at is null and not m.kicked and m.fast and m.lost_at is null and m.seen_at < now() - interval '6 seconds';
  update public.voice_members m set left_at = now(), lost_at = null, seat = null, hand_at = null, muted = true,
         role = case when m.role = 'speaker' then 'listener' else m.role end
   where m.room_id = p_room and m.user_id <> r.host and m.left_at is null and not m.kicked
     and (m.lost_at < now() - interval '7 seconds' or (not m.fast and m.seen_at < now() - interval '90 seconds'));
  get diagnostics n = row_count;
  -- The host is gone: not here, silent for 2 minutes, or lost for 7 s with a co-host to take over.
  select coalesce((select m.seen_at < now() - interval '2 minutes'
                          or (coalesce(m.lost_at < now() - interval '7 seconds', false)
                              and exists (select 1 from public.voice_members c where c.room_id = p_room and c.role = 'cohost'
                                            and c.user_id <> r.host and c.left_at is null and not c.kicked and c.lost_at is null))
                     from public.voice_members m where m.room_id = p_room and m.user_id = r.host and m.left_at is null and not m.kicked), true)
    into host_gone;
  if host_gone then
    perform public.voice_hand_over(r);
  elsif n > 0 then
    perform public.voice_touch(p_room);
  end if;
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
    return;
  end if;
  update public.voice_members set role = 'listener', seat = null, muted = true, hand_at = null, lost_at = null, left_at = coalesce(left_at, now())
   where room_id = r.id and user_id = r.host;
  update public.voice_members set role = 'host', seat = 0, hand_at = null where room_id = r.id and user_id = nh;
  update public.voice_rooms set host = nh, rev = rev + 1 where id = r.id;
  perform public.voice_line(r.id, nh, 'host');
end $$;
