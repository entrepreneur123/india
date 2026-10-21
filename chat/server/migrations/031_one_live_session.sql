-- One live session at a time, enforced by the server (the owner: a room and a random chat must never be open
-- together; entering one leaves the other, without asking). And the test bots are switched off: testing is done
-- with two phones now.
--
-- 1. Starting a random-chat search (the first find_match2 of a search, not every poll) leaves every room I am in,
--    exactly like Leave: a listener/speaker leaves with a "left" line; a host hands the room to a co-host or it ends.
-- 2. Joining a room (voice_admit: card, code, invite) or making one (create_voice_room2) ends my random chat and my
--    search, without the exit lock (the lock is against skipping through people, not against going to a room).
--    Friend chats ('dm') are not live sessions and stay.
-- 3. A room heartbeat never brings me back into a room while I am in a random chat (a late heartbeat after
--    starting a search would otherwise end the new chat): it answers 'in_random_chat'.
-- Applied by patching the current function bodies in place (each patch checks that its anchor exists).

create or replace function public.voice_leave_all(p_me uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms;
begin
  for r in select x.* from public.voice_rooms x
            where x.ended_at is null
              and exists (select 1 from public.voice_members m where m.room_id = x.id and m.user_id = p_me
                            and m.left_at is null and not m.kicked)
            for update of x
  loop
    if r.host = p_me then
      perform public.voice_hand_over(r);
    else
      update public.voice_members x set left_at = now(), seat = null, hand_at = null, muted = true,
             role = case when x.role = 'speaker' then 'listener' else x.role end
       where x.room_id = r.id and x.user_id = p_me and x.left_at is null and not x.kicked;
      perform public.voice_line(r.id, p_me, 'left', '{}'::jsonb);
      perform public.voice_touch(r.id);
    end if;
  end loop;
end $$;

create or replace function public.random_chat_end_all(p_me uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  update public.matches x set ended_at = now(), ended_by = p_me, video_status = 'none'
   where x.ended_at is null and x.kind = 'random' and (x.user_a = p_me or x.user_b = p_me);
  update public.queue set seen_at = '-infinity' where user_id = p_me;
end $$;

revoke execute on function public.voice_leave_all(uuid), public.random_chat_end_all(uuid) from public, anon, authenticated;

do $$
declare d text; a text;
begin
  -- 1. find_match2: a new search leaves rooms.
  d := pg_get_functiondef('public.find_match2(text, text)'::regprocedure);
  a := 'perform pg_advisory_xact_lock(hashtext(''match:'' || me::text));';
  if position(a in d) = 0 then raise exception 'find_match2: anchor not found'; end if;
  execute replace(d, a, a || '
  -- A new search (not a poll of a running one) leaves every room: one live session at a time (031).
  if not exists (select 1 from public.queue q0 where q0.user_id = me and q0.seen_at > now() - interval ''20 seconds'') then
    perform public.voice_leave_all(me);
  end if;');

  -- 2. voice_admit: joining a room ends the random chat.
  d := pg_get_functiondef('public.voice_admit(text, uuid, boolean)'::regprocedure);
  a := 'if why is not null then raise exception ''%'', why using errcode = ''P0001''; end if;';
  if position(a in d) = 0 then raise exception 'voice_admit: anchor not found'; end if;
  execute replace(d, a, a || '
 perform public.random_chat_end_all(me); -- one live session at a time (031)');

  -- 2. create_voice_room2: making a room ends the random chat.
  d := pg_get_functiondef('public.create_voice_room2(text, text, integer, text, integer, integer, boolean, text)'::regprocedure);
  a := 'update public.voice_rooms x set ended_at = now(), rev = rev + 1 where x.host = me and x.ended_at is null;';
  if position(a in d) = 0 then raise exception 'create_voice_room2: anchor not found'; end if;
  execute replace(d, a, 'perform public.random_chat_end_all(me); -- one live session at a time (031)
 ' || a);

  -- 3. voice_heartbeat: no coming back into a room during a random chat.
  d := pg_get_functiondef('public.voice_heartbeat(text)'::regprocedure);
  a := 'perform public.voice_enter(p_room, me);';
  if position(a in d) = 0 then raise exception 'voice_heartbeat: anchor not found'; end if;
  execute replace(d, a, 'if exists (select 1 from public.matches x where x.ended_at is null and x.kind = ''random'' and (x.user_a = me or x.user_b = me)) then
      raise exception ''in_random_chat'' using errcode = ''P0001'';
    end if;
    ' || a);
end $$;

-- 4. Test bots off (removed for good by owner_run_once_5.sql).
select cron.unschedule('yaari-bots') where exists (select 1 from cron.job where jobname = 'yaari-bots');
update public.bot_config set enabled = false where id = 1;
update public.bots set active = false where active;
update public.voice_rooms x set ended_at = now(), rev = rev + 1
 where x.ended_at is null and x.host in (select y.id from public.bots y);
update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true,
       role = case when m.role = 'speaker' then 'listener' else m.role end
 where m.left_at is null and m.user_id in (select y.id from public.bots y);
update public.matches x set ended_at = now(), ended_by = x.user_a, video_status = 'none'
 where x.ended_at is null and (x.user_a in (select y.id from public.bots y) or x.user_b in (select y.id from public.bots y));
update public.user_seen s set seen_at = '-infinity' where s.user_id in (select y.id from public.bots y);
