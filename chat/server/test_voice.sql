-- Voice rooms (migration 017). One transaction, rolled back.
begin;
set local statement_timeout = '20s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
-- Runs `q` and expects an error whose text contains `code`.
create function pg_temp.fails(q text, code text) returns void language plpgsql as $$
declare ok boolean := false;
begin
  begin
    execute q;
  exception when others then
    if sqlerrm like '%' || code || '%' then ok := true; else raise; end if;
  end;
  if not ok then raise exception 'failed: expected % from %', code, q; end if;
end $$;
create temp table v (k text primary key, id text) on commit drop;
grant all on v to authenticated;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000101', 'authenticated', 'authenticated', '919855580101'),
  ('eeeeeeee-0000-0000-0000-000000000102', 'authenticated', 'authenticated', '919855580102'),
  ('eeeeeeee-0000-0000-0000-000000000103', 'authenticated', 'authenticated', '919855580103'),
  ('eeeeeeee-0000-0000-0000-000000000104', 'authenticated', 'authenticated', '919855580104'),
  ('eeeeeeee-0000-0000-0000-000000000105', 'authenticated', 'authenticated', '919855580105'),
  ('eeeeeeee-0000-0000-0000-000000000106', 'authenticated', 'authenticated', '919855580106');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000101', 'VHost', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000102', 'VListener', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000103', 'VCohost', 'female', '2000-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000104', 'VBanned', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000105', 'VBlocked', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000106', 'VTalker', 'female', '1997-01-01');
update public.profiles set banned_until = now() + interval '1 day' where id = 'eeeeeeee-0000-0000-0000-000000000104';
insert into public.blocks (blocker, blocked) values ('eeeeeeee-0000-0000-0000-000000000101', 'eeeeeeee-0000-0000-0000-000000000105');

set local role authenticated;

-- ---- create: one open room per owner, validation ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select pg_temp.fails($$select public.create_voice_room('x', 'music', 'approval')$$, 'room_title');
select pg_temp.fails($$select public.create_voice_room('Fine title', 'cooking', 'approval')$$, 'bad_request');
insert into v values ('r1', public.create_voice_room('First room', 'music', 'approval'));
insert into v values ('r', public.create_voice_room('Late night songs', 'music', 'approval'));
select pg_temp.check((select id from v where k = 'r') like 'v-%', 'room id');
select pg_temp.check(public.voice_state((select id from v where k = 'r'))->'members'->0->>'role' = 'host', 'creator is host');
select pg_temp.check((public.voice_state((select id from v where k = 'r'))->'members'->0->>'seat')::int = 0, 'host on seat 0');
select pg_temp.fails($$select public.voice_state((select id from v where k = 'r1'))$$, 'room_closed');
select pg_temp.check((select count(*) from public.voice_list() l where l.id = (select id from v where k = 'r1')) = 0, 'old room not listed');
select pg_temp.check((select count(*) from public.voice_list() l where l.id = (select id from v where k = 'r') and l.mine) = 1, 'new room listed as mine');

-- ---- join, heartbeat, list ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.check(jsonb_array_length(public.join_voice((select id from v where k = 'r'))->'members') = 2, 'listener joined');
select pg_temp.check(public.voice_heartbeat((select id from v where k = 'r')) > 0, 'heartbeat returns rev');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000103');
select public.join_voice((select id from v where k = 'r'));
select pg_temp.check((select l.listeners from public.voice_list() l where l.id = (select id from v where k = 'r')) = 1, 'list: other listeners');
select pg_temp.check((select jsonb_array_length(l.speakers) from public.voice_list() l where l.id = (select id from v where k = 'r')) = 1, 'list: host on stage');
select pg_temp.check((select count(*) from public.voice_messages m where m.room_id = (select id from v where k = 'r')
                        and m.kind = 'system' and m.payload->>'event' = 'joined') = 2, 'joined lines readable by members');

-- ---- approval mode: a listener can't speak until invited ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.fails($$select public.voice_mic((select id from v where k = 'r'), true)$$, 'not_allowed');
select pg_temp.check((select g.seat from public.voice_grant((select id from v where k = 'r')) g) is null, 'listener grant: no seat');
select pg_temp.check(public.voice_take_seat((select id from v where k = 'r'), 1) = 'hand', 'approval room: tapping a seat raises the hand');
select pg_temp.fails($$select public.voice_invite((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000103')$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select pg_temp.check((select (x->>'hand')::boolean from jsonb_array_elements(public.voice_state((select id from v where k = 'r'))->'members') x
                       where x->>'id' = 'eeeeeeee-0000-0000-0000-000000000102'), 'host sees the hand');
select public.voice_answer((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102', true);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.check((select g.seat from public.voice_grant((select id from v where k = 'r')) g) = 1, 'approved: seat 1');
select pg_temp.check((select g.role from public.voice_grant((select id from v where k = 'r')) g) = 'speaker', 'approved: speaker');
select public.voice_mic((select id from v where k = 'r'), true);
select pg_temp.check(not (select g.muted from public.voice_grant((select id from v where k = 'r')) g), 'speaker unmuted');

-- ---- seats: locked, unique, moves ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select public.voice_lock_seat((select id from v where k = 'r'), 2, true);
select pg_temp.fails($$select public.voice_invite((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000103', 2)$$, 'not_allowed');
select public.voice_invite((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000103');
select pg_temp.check((select (x->>'seat')::int from jsonb_array_elements(public.voice_state((select id from v where k = 'r'))->'members') x
                       where x->>'id' = 'eeeeeeee-0000-0000-0000-000000000103') = 3, 'invite skips the locked seat');
select pg_temp.fails($$select public.voice_lock_seat((select id from v where k = 'r'), 3, true)$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.fails($$select public.voice_take_seat((select id from v where k = 'r'), 3)$$, 'not_allowed');
select pg_temp.fails($$select public.voice_take_seat((select id from v where k = 'r'), 2)$$, 'not_allowed');
select pg_temp.check(public.voice_take_seat((select id from v where k = 'r'), 4) = 'moved', 'move to a free seat');
select pg_temp.fails($$select public.voice_take_seat((select id from v where k = 'r'), 0)$$, 'not_allowed');

-- ---- co-hosts ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000103');
select pg_temp.fails($$select public.voice_mute_member((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102')$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select public.voice_set_cohost((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000103', true);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000103');
select public.voice_mute_member((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.fails($$select public.voice_kick((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102')$$, 'not_allowed');
select pg_temp.fails($$select public.voice_set_cohost((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102', true)$$, 'not_allowed');
select pg_temp.fails($$select public.voice_mute_member((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000101')$$, 'not_allowed');
select pg_temp.fails($$select public.voice_pause((select id from v where k = 'r'))$$, 'not_allowed');
select public.voice_to_audience((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.check((select (x->>'role') || coalesce(x->>'seat', '-') from jsonb_array_elements(public.voice_state((select id from v where k = 'r'))->'members') x
                       where x->>'id' = 'eeeeeeee-0000-0000-0000-000000000102') = 'listener-', 'moved to audience');
select pg_temp.check((select count(*) from public.voice_sync_info((select id from v where k = 'r'))) = 3, 'co-host may sync everyone');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.fails($$select public.voice_mute_member((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000103')$$, 'not_allowed');
select pg_temp.fails($$select * from public.voice_sync_info((select id from v where k = 'r'))$$, 'not_allowed');
select pg_temp.check(not (select s.publish from public.voice_sync_info((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102') s), 'listener: no publish');

-- ---- music: validation, suggestions, DJ ----
select pg_temp.fails($$select public.voice_queue_add((select id from v where k = 'r'), 'not-an-id', '')$$, 'bad_request');
select pg_temp.fails($$select public.voice_queue_add((select id from v where k = 'r'), 'BddP6PYo2gs', repeat('x', 101))$$, 'bad_request');
select pg_temp.check(public.voice_queue_add((select id from v where k = 'r'), 'BddP6PYo2gs', 'Kesariya') = 'suggested', 'listener suggests');
select public.voice_queue_add((select id from v where k = 'r'), 'Umqb9KENgmk', '');
select public.voice_queue_add((select id from v where k = 'r'), '284Ov7ysmfA', '');
select pg_temp.fails($$select public.voice_queue_add((select id from v where k = 'r'), 'VAdGW7QDJiU', '')$$, 'slow_down');
select pg_temp.fails($$select public.voice_skip((select id from v where k = 'r'))$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select pg_temp.check(public.voice_queue_add((select id from v where k = 'r'), 'g0eO74UmRBs', 'Kal Ho Naa Ho') = 'playing', 'host: plays at once');
select pg_temp.check(public.voice_queue_add((select id from v where k = 'r'), 'lTRiuFIWV54', '') = 'queued', 'host: queued next');
select public.voice_queue_approve((select id from v where k = 'r'),
  (select q->>'id' from public.voice_rooms r, jsonb_array_elements(r.queue) q where r.id = (select id from v where k = 'r') and q->>'videoId' = '284Ov7ysmfA'), true);
select public.voice_queue_approve((select id from v where k = 'r'),
  (select q->>'id' from public.voice_rooms r, jsonb_array_elements(r.queue) q where r.id = (select id from v where k = 'r') and q->>'videoId' = 'Umqb9KENgmk'), false);
select pg_temp.check((select jsonb_array_length(r.queue) from public.voice_rooms r where r.id = (select id from v where k = 'r')) = 3, 'declined suggestion gone');
insert into v values ('t1', (select r.music->>'id' from public.voice_rooms r where r.id = (select id from v where k = 'r')));
select public.voice_pause((select id from v where k = 'r'));
select pg_temp.check((select r.music->>'pausedAt' from public.voice_rooms r where r.id = (select id from v where k = 'r')) is not null, 'paused');
select public.voice_resume((select id from v where k = 'r'));
select pg_temp.check((select r.music->'pausedAt' = 'null' from public.voice_rooms r where r.id = (select id from v where k = 'r')), 'resumed');
select public.voice_skip((select id from v where k = 'r'), (select id from v where k = 't1'));
-- Since 019 suggestions go straight into the queue and the most voted plays; with no votes, the oldest: B's first.
select pg_temp.check((select r.music->>'videoId' from public.voice_rooms r where r.id = (select id from v where k = 'r')) = 'BddP6PYo2gs', 'skip plays the oldest (no votes)');
select public.voice_skip((select id from v where k = 'r'), (select id from v where k = 't1')); -- stale: nothing happens
select pg_temp.check((select r.music->>'videoId' from public.voice_rooms r where r.id = (select id from v where k = 'r')) = 'BddP6PYo2gs', 'stale skip ignored');
select pg_temp.check((select r.music->>'title' from public.voice_rooms r where r.id = (select id from v where k = 'r')) = 'Kesariya', 'title kept');

-- ---- chat: limits and reactions ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000106');
select public.join_voice((select id from v where k = 'r'));
select pg_temp.fails($$select public.voice_send((select id from v where k = 'r'), repeat('x', 501))$$, 'bad_request');
select (public.voice_send((select id from v where k = 'r'), 'line ' || g)).id from generate_series(1, 5) g;
select pg_temp.fails($$select public.voice_send((select id from v where k = 'r'), 'one too many')$$, 'slow_down');
select public.voice_react((select id from v where k = 'r'), 'fire') from generate_series(1, 10);
select pg_temp.fails($$select public.voice_react((select id from v where k = 'r'), 'poop')$$, 'bad_request');
select pg_temp.check((select count(*) from public.voice_messages m where m.sender = 'eeeeeeee-0000-0000-0000-000000000106' and m.kind = 'reaction') = 8, 'reactions capped at 8 / 5 s');
select pg_temp.check((select count(*) from public.voice_messages m where m.sender = 'eeeeeeee-0000-0000-0000-000000000106' and m.kind = 'text') = 5, 'five lines sent');

-- ---- report from a voice room keeps the reported person's lines ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000103');
select public.report_user('eeeeeeee-0000-0000-0000-000000000106', 'harassment');
reset role;
select pg_temp.check((select jsonb_array_length(r.evidence->'voice') from public.reports r
                        where r.reporter = 'eeeeeeee-0000-0000-0000-000000000103' and r.reported = 'eeeeeeee-0000-0000-0000-000000000106') = 5, 'report evidence: voice lines');
set local role authenticated;
-- Blocked now: the reporter no longer sees their lines.
select pg_temp.check((select count(*) from public.voice_messages m where m.sender = 'eeeeeeee-0000-0000-0000-000000000106') = 0, 'blocked sender hidden');

-- ---- kicked, banned, blocked ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select public.voice_kick((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.check((select s.remove from public.voice_sync_info((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000102') s), 'kicked: remove from LiveKit');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000102');
select pg_temp.fails($$select public.join_voice((select id from v where k = 'r'))$$, 'voice_removed');
select pg_temp.fails($$select public.voice_heartbeat((select id from v where k = 'r'))$$, 'voice_removed');
select pg_temp.fails($$select public.voice_send((select id from v where k = 'r'), 'hi')$$, 'voice_removed');
select pg_temp.check((select count(*) from public.voice_grant((select id from v where k = 'r'))) = 0, 'kicked: no token');
select pg_temp.check((select count(*) from public.voice_list() l where l.id = (select id from v where k = 'r')) = 0, 'kicked: not listed');
select pg_temp.check((select count(*) from public.voice_messages m where m.room_id = (select id from v where k = 'r')) = 0, 'kicked: chat hidden');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000104');
select pg_temp.fails($$select public.join_voice((select id from v where k = 'r'))$$, 'banned');
select pg_temp.fails($$select public.create_voice_room('Banned room', 'chat', 'open')$$, 'banned');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000105');
select pg_temp.fails($$select public.join_voice((select id from v where k = 'r'))$$, 'not_allowed');
select pg_temp.check((select count(*) from public.voice_list() l where l.id = (select id from v where k = 'r')) = 0, 'blocked: not listed');

-- ---- the host leaves: the co-host takes over; then the last one leaves: the room ends ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000101');
select public.leave_voice((select id from v where k = 'r'));
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000103');
select pg_temp.check(public.voice_state((select id from v where k = 'r'))->>'host' = 'eeeeeeee-0000-0000-0000-000000000103', 'co-host is the host now');
select pg_temp.check((select g.seat from public.voice_grant((select id from v where k = 'r')) g) = 0, 'new host on seat 0');
select pg_temp.check(public.voice_queue_add((select id from v where k = 'r'), 'VAdGW7QDJiU', '') = 'queued', 'new host is the DJ');
select public.leave_voice((select id from v where k = 'r'));
select pg_temp.fails($$select public.voice_state((select id from v where k = 'r'))$$, 'room_closed');
select pg_temp.check((select s.ended and s.remove from public.voice_sync_info((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000103') s), 'ended: everyone removed');

-- ---- open room, timeouts: 90 s for members, 2 minutes for the host ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000106');
insert into v values ('o', public.create_voice_room('Open mic', 'friends', 'open'));
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000105');
select public.join_voice((select id from v where k = 'o'));
select pg_temp.check(public.voice_take_seat((select id from v where k = 'o'), 5) = 'seated', 'open room: take a seat');
reset role;
update public.voice_members set seen_at = now() - interval '100 seconds'
 where room_id = (select id from v where k = 'o') and user_id = 'eeeeeeee-0000-0000-0000-000000000105';
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000106');
select public.voice_heartbeat((select id from v where k = 'o'));
reset role;
select pg_temp.check((select left_at is not null and seat is null from public.voice_members
                        where room_id = (select id from v where k = 'o') and user_id = 'eeeeeeee-0000-0000-0000-000000000105'), 'silent member is out, seat freed');
update public.voice_members set seen_at = now() - interval '3 minutes'
 where room_id = (select id from v where k = 'o') and user_id = 'eeeeeeee-0000-0000-0000-000000000106';
set local role authenticated;
select pg_temp.check((select count(*) from public.voice_list() l where l.id = (select id from v where k = 'o')) = 0, 'host gone 2 min: not listed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000105');
select pg_temp.fails($$select public.voice_heartbeat((select id from v where k = 'o'))$$, 'room_closed');

-- ---- users can't call the internals ----
select pg_temp.check(not has_function_privilege('authenticated', 'public.voice_settle(text)', 'execute'), 'settle hidden');
select pg_temp.check(not has_function_privilege('authenticated', 'public.voice_enter(text, uuid)', 'execute'), 'enter hidden');
select pg_temp.check(not has_function_privilege('anon', 'public.join_voice(text)', 'execute'), 'anon cannot join');
select pg_temp.check(not has_table_privilege('authenticated', 'public.voice_members', 'select'), 'members not readable directly');
select pg_temp.check(not has_table_privilege('authenticated', 'public.voice_messages', 'insert'), 'lines only through voice_send');
select 'all voice checks passed' as result;
rollback;
