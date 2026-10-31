-- Room chat lines over Broadcast too (docs/CAPACITY.md 1; 107 left them on Postgres Changes). With nothing on the web
-- listening to Postgres Changes, Realtime stops polling the WAL for it: its list_changes query was by far the largest
-- load on the database (pg_stat_statements, 2026-10-31: 284,773 calls, 1,695 s; the next query 67 s).
-- A line keeps its read rule ('read voice room'): one between two people who blocked each other reaches neither.
--  * Nearly always nobody who may read the room (voice_can_read) has a block with the sender: one send to the room's
--    topic, voice:<room>, where the room's events already go.
--  * Otherwise each person in the room now without a block with the sender gets it on their own topic (user:<id>).
-- Also, on my own topic (one channel less to join than 088's friends:<id>):
--  * 'p': a friend's presence changed (088).
--  * 'c': a new message in one of my DMs, so the unread counts move at once rather than at the next 30 s poll.
-- 088's 'friend presence' policy (friends:<id>) stays for pages still open from before: nothing is sent there now, and
-- removing it would make those pages retry a refused join every few seconds. A later migration drops it.
-- The tables stay published for Postgres Changes (the Android app). Re-runnable.

create or replace function public.voice_messages_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if not exists (select 1 from public.blocks b
                   join public.voice_members m
                     on m.user_id = case when b.blocker = new.sender then b.blocked else b.blocker end
                  where (b.blocker = new.sender or b.blocked = new.sender) and m.room_id = new.room_id and not m.kicked) then
    perform realtime.send(to_jsonb(new), 'l', 'voice:' || new.room_id, true);
  else
    perform realtime.send(to_jsonb(new), 'l', 'user:' || m.user_id::text, true)
       from public.voice_members m
      where m.room_id = new.room_id and m.left_at is null and not m.kicked and not public.is_blocked(m.user_id, new.sender);
  end if;
  return null;
end $$;
revoke execute on function public.voice_messages_rt() from public, anon, authenticated;
create or replace trigger voice_messages_rt after insert on public.voice_messages for each row execute function public.voice_messages_rt();

create or replace function public.messages_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
declare other uuid;
begin
  perform realtime.send(to_jsonb(new), 'msg', 'chat:' || new.match_id::text, true);
  select case when x.user_a = new.sender then x.user_b else x.user_a end into other
    from public.matches x where x.id = new.match_id and x.kind = 'dm';
  if other is not null and not public.is_blocked(other, new.sender) then
    perform realtime.send(jsonb_build_object('m', new.match_id), 'c', 'user:' || other::text, true);
  end if;
  return null;
end $$;

-- As 088, to user:<friend> (107's policy lets only them listen there).
create or replace function public.presence_ping(p_online boolean default true) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); was timestamptz;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  select s.pinged_at into was from public.user_seen s where s.user_id = me;
  insert into public.user_seen as s (user_id, seen_at, pinged_at)
  values (me, case when p_online then now() else '-infinity'::timestamptz end, now())
  on conflict (user_id) do update set seen_at = excluded.seen_at, pinged_at = now();
  -- A burst of changes (or a loop) tells friends once; closing the app always does.
  if p_online and was > now() - interval '2 seconds' then return; end if;
  if (select p.hide_online from public.profiles p where p.id = me) then return; end if;
  perform realtime.send('{}'::jsonb, 'p', 'user:' || f.id, true)
     from (select case when fr.requester = me then fr.addressee else fr.requester end as id
             from public.friendships fr
            where me in (fr.requester, fr.addressee) and fr.status = 'accepted' and not public.is_blocked(fr.requester, fr.addressee)
            limit 500) f
    where public.user_online(f.id);
end $$;
