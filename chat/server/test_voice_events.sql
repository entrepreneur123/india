-- Room changes over Realtime Broadcast (migration 020). One transaction, rolled back: the events are the rows
-- realtime.send() writes to realtime.messages (Realtime delivers them after commit; here nothing is committed).
begin;
set local statement_timeout = '20s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create temp table v (k text primary key, id text);
grant all on v to authenticated;
create function pg_temp.r() returns text language sql as $$ select id from v where k = 'r' $$;
-- Events of the room so far, oldest first.
create function pg_temp.ev() returns setof jsonb language sql as $$
  select m.payload from realtime.messages m where m.topic = 'voice:' || pg_temp.r() and m.event = 'v' and m.private
   order by m.inserted_at, (m.payload->>'seq')::bigint $$;
create function pg_temp.last() returns jsonb language sql as $$
  select e from pg_temp.ev() e order by (e->>'seq')::bigint desc nulls first limit 1 $$;
create function pg_temp.n() returns int language sql as $$ select count(*)::int from pg_temp.ev() $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000401', 'authenticated', 'authenticated', '919855560401'),
  ('eeeeeeee-0000-0000-0000-000000000402', 'authenticated', 'authenticated', '919855560402'),
  ('eeeeeeee-0000-0000-0000-000000000403', 'authenticated', 'authenticated', '919855560403');
insert into public.profiles (id, name, gender, birth_date, bio) values
  ('eeeeeeee-0000-0000-0000-000000000401', 'EHost', 'female', '1998-01-01', 'Host of things'),
  ('eeeeeeee-0000-0000-0000-000000000402', 'EGuest', 'male', '1999-01-01', 'Here to listen'),
  ('eeeeeeee-0000-0000-0000-000000000403', 'EOther', 'female', '1997-01-01', '');

-- Opening: the room has a counter; the host's own row is the first event.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000401');
insert into v values ('r', public.create_voice_room2('Events room', 'talk', 50, 'any', 18, 100, false, null)->>'id');
select pg_temp.check(exists (select 1 from public.voice_seq s where s.room_id = pg_temp.r()), 'counter row');
select pg_temp.check(pg_temp.n() = 1 and pg_temp.last()->>'t' = 'm' and (pg_temp.last()->>'seq')::int = 1
  and pg_temp.last()->>'role' = 'host', 'host row sent');

-- Joining sends the new member's card; the full state carries the same number.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000402');
insert into v values ('s0', (public.join_voice(pg_temp.r())->>'seq'));
select pg_temp.check(pg_temp.last()->>'t' = 'm' and pg_temp.last()->>'id' = 'eeeeeeee-0000-0000-0000-000000000402'
  and (pg_temp.last()->>'present')::boolean and pg_temp.last()->>'name' = 'EGuest' and pg_temp.last()->>'bio' = 'Here to listen',
  'join: member card');
select pg_temp.check((select id from v where k = 's0')::bigint = (pg_temp.last()->>'seq')::bigint, 'state seq = last event seq');
select pg_temp.check(public.voice_state(pg_temp.r())->'members'->1 ? 'j', 'state members carry join time');

-- A heartbeat sends nothing; it answers with the counter.
insert into v values ('n1', pg_temp.n()::text);
select pg_temp.check(public.voice_heartbeat2(pg_temp.r()) = (pg_temp.last()->>'seq')::bigint, 'heartbeat2 returns seq');
select public.voice_heartbeat(pg_temp.r());
select pg_temp.check(pg_temp.n() = (select id from v where k = 'n1')::int, 'heartbeats send nothing');

-- A raised hand: one small member event, numbered next.
select public.voice_hand(pg_temp.r(), true);
select pg_temp.check(pg_temp.n() = (select id from v where k = 'n1')::int + 1, 'hand: one event');
select pg_temp.check((pg_temp.last()->>'hand')::boolean and pg_temp.last()->>'ha' is not null, 'hand: up, with time');
insert into v values ('hand_bytes', octet_length(pg_temp.last()::text)::text);
select pg_temp.check((select id from v where k = 'hand_bytes')::int < 600, 'member event under 600 bytes');

-- Host actions: seat (member event), title (room event), music (room event), vote (vote event).
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000401');
select public.voice_answer(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000402', true);
select pg_temp.check(exists (select 1 from pg_temp.ev() e where e->>'t' = 'm' and e->>'id' = 'eeeeeeee-0000-0000-0000-000000000402'
  and (e->>'seat')::int = 1 and e->>'role' = 'speaker'), 'seated: member event');
select public.voice_settings(pg_temp.r(), 'Renamed room', 'Hello all');
select pg_temp.check(pg_temp.last()->>'t' = 'r' and pg_temp.last()->>'title' = 'Renamed room' and pg_temp.last()->>'announcement' = 'Hello all'
  and not (pg_temp.last()->>'ended')::boolean, 'settings: room event');
select public.voice_queue_add(pg_temp.r(), 'g0eO74UmRBs', 'Kal Ho Naa Ho');
select pg_temp.check(pg_temp.last()->'music'->>'videoId' = 'g0eO74UmRBs', 'music: room event');
select public.voice_queue_add(pg_temp.r(), 'lTRiuFIWV54', 'Next');
insert into v values ('item', pg_temp.last()->'queue'->0->>'id');
select pg_temp.check((pg_temp.last()->'queue'->0->>'votes')::int = 0 and not (pg_temp.last()->'queue'->0 ? 'voted'), 'queue: votes, no per-viewer flag');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000402');
select public.voice_vote(pg_temp.r(), (select id from v where k = 'item'), true);
select pg_temp.check(pg_temp.last()->>'t' = 'v' and (pg_temp.last()->>'votes')::int = 1 and (pg_temp.last()->>'on')::boolean
  and pg_temp.last()->>'user' = 'eeeeeeee-0000-0000-0000-000000000402', 'vote: vote event');

-- The numbers are 1..n with no gap, and the state agrees.
select pg_temp.check((select array_agg((e->>'seq')::int order by (e->>'seq')::int) from pg_temp.ev() e)
  = (select array_agg(g) from generate_series(1, pg_temp.n()) g), 'seq dense from 1');
select pg_temp.check((public.voice_state(pg_temp.r())->>'seq')::int = pg_temp.n(), 'state seq = event count');

-- Leaving: the member goes (present false, no card).
select public.leave_voice(pg_temp.r());
select pg_temp.check(exists (select 1 from pg_temp.ev() e where e->>'id' = 'eeeeeeee-0000-0000-0000-000000000402'
  and not (e->>'present')::boolean and not e ? 'name'), 'left: present false');

-- Kicked: present false.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000403');
select public.join_voice(pg_temp.r());
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000401');
select public.voice_kick(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000403');
select pg_temp.check(exists (select 1 from pg_temp.ev() e where e->>'id' = 'eeeeeeee-0000-0000-0000-000000000403'
  and not (e->>'present')::boolean), 'kicked: present false');

-- Who may listen: the policy exists on realtime.messages and reads the member rule.
select pg_temp.check(exists (select 1 from pg_policies where schemaname = 'realtime' and tablename = 'messages'
  and policyname = 'voice room events' and cmd = 'SELECT'), 'listen policy');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000401');
select set_config('realtime.topic', 'voice:' || pg_temp.r(), true);
select pg_temp.check((select count(*) from realtime.messages m where m.topic = 'voice:' || pg_temp.r()) > 0, 'member reads the events');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000403');
select pg_temp.check((select count(*) from realtime.messages m where m.topic = 'voice:' || pg_temp.r()) = 0, 'removed member reads none');
reset role;

-- Ending: a room event with ended.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000401');
select public.end_voice_room(pg_temp.r());
select pg_temp.check(pg_temp.last()->>'t' = 'r' and (pg_temp.last()->>'ended')::boolean, 'ended: room event');

-- Internals are not callable.
select pg_temp.check(not has_function_privilege('authenticated', 'public.voice_emit(text, jsonb)', 'execute'), 'emit hidden');
select pg_temp.check(has_function_privilege('authenticated', 'public.voice_heartbeat2(text)', 'execute'), 'heartbeat2 callable');
select pg_temp.check(not has_function_privilege('anon', 'public.voice_heartbeat2(text)', 'execute'), 'anon: no heartbeat2');
select pg_temp.check(not has_function_privilege('authenticated', 'public.create_room(text, text, integer, text, integer, integer, boolean)', 'execute')
  and not has_function_privilege('authenticated', 'public.voice_list()', 'execute')
  and not has_function_privilege('authenticated', 'public.find_match(text)', 'execute'), 'old surface revoked');
select pg_temp.check(not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'room_messages'),
  'room_messages not published');

select 'all voice event checks passed (hand event ' || (select id from v where k = 'hand_bytes') || ' bytes)' as result;
rollback;
