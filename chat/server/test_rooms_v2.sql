-- Unified rooms (migration 019). One transaction, rolled back.
begin;
set local statement_timeout = '20s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
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
create function pg_temp.r(k text default 'r') returns text language sql as $$ select id from v where v.k = r.k $$;
create function pg_temp.state(k text default 'r') returns jsonb language sql as $$ select public.voice_state(pg_temp.r(k)) $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000301', 'authenticated', 'authenticated', '919855570301'),
  ('eeeeeeee-0000-0000-0000-000000000302', 'authenticated', 'authenticated', '919855570302'),
  ('eeeeeeee-0000-0000-0000-000000000303', 'authenticated', 'authenticated', '919855570303'),
  ('eeeeeeee-0000-0000-0000-000000000304', 'authenticated', 'authenticated', '919855570304'),
  ('eeeeeeee-0000-0000-0000-000000000305', 'authenticated', 'authenticated', '919855570305'),
  ('eeeeeeee-0000-0000-0000-000000000306', 'authenticated', 'authenticated', '919855570306');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000301', 'RHost', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000302', 'RWoman', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000303', 'RMan', 'male', '2000-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000304', 'ROlder', 'female', '1960-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000305', 'RBlocked', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000306', 'RThird', 'female', '1997-01-01');
insert into public.blocks (blocker, blocked) values ('eeeeeeee-0000-0000-0000-000000000301', 'eeeeeeee-0000-0000-0000-000000000305');
-- A photo already in storage for RWoman (uploads are the storage API's job; the row is what voice_photo checks).
insert into storage.objects (bucket_id, name, created_at) values
  ('media', 'eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg', now() - interval '2 days');

set local role authenticated;

-- ---- old signature (apps before 019): categories map, host on the stage ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000306');
insert into v values ('old', public.create_voice_room('Old app room', 'gaming', 'open'));
select pg_temp.check(pg_temp.state('old')->>'category' = 'games', 'gaming -> games');
select pg_temp.check((pg_temp.state('old')->'members'->0->>'seat')::int = 0, 'old signature: host on seat 0');
select pg_temp.check(pg_temp.state('old')->>'code' ~ '^[A-HJ-NP-Z2-9]{6}$', 'old signature: room has a code');
select pg_temp.fails($$select public.create_voice_room('Old app room', 'cooking', 'open')$$, 'bad_request');

-- ---- create with settings ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000301');
select pg_temp.fails($$select public.create_voice_room2('x', 'talk', 50, 'any', 18, 100, false, null)$$, 'room_title');
select pg_temp.fails($$select public.create_voice_room2('Fine', 'party', 50, 'any', 18, 100, false, null)$$, 'bad_request');
select pg_temp.fails($$select public.create_voice_room2('Fine', 'talk', 201, 'any', 18, 100, false, null)$$, 'room_size');
select pg_temp.fails($$select public.create_voice_room2('Fine', 'talk', 1, 'any', 18, 100, false, null)$$, 'room_size');
select pg_temp.fails($$select public.create_voice_room2('Fine', 'talk', 50, 'any', 40, 30, false, null)$$, 'bad_request');
select pg_temp.fails($$select public.create_voice_room2('Fine', 'talk', 50, 'any', 18, 100, false, repeat('a', 201))$$, 'bad_request');
insert into v values ('r', public.create_voice_room2('Women 18-30 music', 'music', 2, 'female', 18, 30, true, '  Be kind  ')->>'id');
select pg_temp.check(pg_temp.state()->>'announcement' = 'Be kind', 'announcement trimmed');
select pg_temp.check((pg_temp.state()->>'max')::int = 2 and pg_temp.state()->>'gender' = 'female' and (pg_temp.state()->>'private')::boolean, 'settings stored');
insert into v values ('code', pg_temp.state()->>'code');
select pg_temp.check(pg_temp.r('code') ~ '^[A-HJ-NP-Z2-9]{6}$', 'host sees the code');
select pg_temp.check(pg_temp.state()->'members'->0->>'seat' is null, 'host not on the stage yet');

-- ---- audio only while someone is on the stage ----
select pg_temp.check((select count(*) from public.voice_grant(pg_temp.r())) = 0, 'empty stage: no LiveKit token');
select pg_temp.check(public.voice_take_seat(pg_temp.r(), 0) = 'seated', 'host joins voice');
select pg_temp.check((select g.seat from public.voice_grant(pg_temp.r()) g) = 0, 'host on seat 0: token');
select public.voice_leave_seat(pg_temp.r());
select pg_temp.check(pg_temp.state()->'members'->0->>'seat' is null and pg_temp.state()->'members'->0->>'role' = 'host', 'host off the stage, still host');
select pg_temp.check((select count(*) from public.voice_grant(pg_temp.r())) = 0, 'stage empty again: no token');

-- ---- private: hidden, entered only with the code ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000302');
select pg_temp.check((select count(*) from public.voice_list2() l where l.id = pg_temp.r()) = 0, 'private: not listed');
select pg_temp.check((select count(*) from public.voice_list() l where l.id = pg_temp.r()) = 0, 'private: not in the old list');
select pg_temp.fails($$select public.join_voice(pg_temp.r())$$, 'not_found');
select pg_temp.fails($$select public.voice_heartbeat(pg_temp.r())$$, 'not_found');
select pg_temp.check(public.join_voice_code('ZZZZZZ') is null, 'wrong code: null');
select pg_temp.check(public.join_voice_code(lower(pg_temp.r('code'))) = pg_temp.r(), 'code (any case) lets me in');
select pg_temp.check((select l.members from public.voice_list2() l where l.id = pg_temp.r()) = 2, 'listed for me now, 2 present');
select pg_temp.check(jsonb_array_length(public.join_voice(pg_temp.r())->'members') = 2, 'back in by id');
select pg_temp.check(public.voice_state(pg_temp.r())->>'code' is null, 'code only for the host');

-- ---- rules: gender, age, size, blocked ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000303');
select pg_temp.fails($$select public.join_voice_code(pg_temp.r('code'))$$, 'room_gender');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000304');
select pg_temp.fails($$select public.join_voice_code(pg_temp.r('code'))$$, 'room_age');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000306');
select pg_temp.fails($$select public.join_voice_code(pg_temp.r('code'))$$, 'room_full');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000305');
select pg_temp.fails($$select public.join_voice_code(pg_temp.r('code'))$$, 'not_allowed');
-- Wrong codes: after 10 misses in 10 minutes code entry stops (the tries above used the right code).
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000303');
select public.join_voice_code('ZZZZZ' || g) from generate_series(1, 10) g;
select pg_temp.fails($$select public.join_voice_code('ZZZZZZ')$$, 'slow_down');

-- ---- settings: the host edits title and announcement ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000302');
select pg_temp.fails($$select public.voice_settings(pg_temp.r(), 'Mine now', null)$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000301');
select pg_temp.fails($$select public.voice_settings(pg_temp.r(), 'x', null)$$, 'room_title');
select public.voice_settings(pg_temp.r(), 'Arijit night', '');
select pg_temp.check(pg_temp.state()->>'title' = 'Arijit night' and pg_temp.state()->'announcement' = 'null', 'title changed, announcement removed');

-- ---- icebreakers ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000302');
insert into v values ('ib', (public.voice_icebreaker(pg_temp.r())).payload->>'q');
select pg_temp.check(pg_temp.r('ib')::int between 1 and 40, 'icebreaker: a prompt number');
select pg_temp.check((select kind = 'icebreaker' and body is null and sender_name = 'RWoman' from public.voice_messages
                       where room_id = pg_temp.r() order by id desc limit 1), 'icebreaker line readable, by me');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000301');
select pg_temp.fails($$select public.voice_icebreaker(pg_temp.r())$$, 'slow_down');
reset role;
update public.voice_messages set created_at = now() - interval '21 seconds' where room_id = (select id from v where k = 'r') and kind = 'icebreaker';
set local role authenticated;
select pg_temp.check(((public.voice_icebreaker(pg_temp.r())).payload->>'q')::int <> pg_temp.r('ib')::int, 'after 20 s: another, a new prompt');

-- ---- photos ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000302');
select pg_temp.fails($$select public.voice_photo(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000301/rooms/11111111-2222-3333-4444-555555555555.jpg')$$, 'bad_media');
select pg_temp.fails($$select public.voice_photo(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000302/rooms/99999999-2222-3333-4444-555555555555.jpg')$$, 'bad_media');
select pg_temp.fails($$select public.voice_photo(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000302/chat/11111111-2222-3333-4444-555555555555.jpg')$$, 'bad_media');
select public.voice_photo(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg') from generate_series(1, 5);
select pg_temp.fails($$select public.voice_photo(pg_temp.r(), 'eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg')$$, 'slow_down');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000301');
select pg_temp.check(public.can_read_media('eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg'), 'member reads the room photo');
select pg_temp.check((select count(*) from public.voice_messages where room_id = pg_temp.r() and kind = 'photo') = 5, 'photo lines readable');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000303');
select pg_temp.check(not public.can_read_media('eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg'), 'outsider can not');
select pg_temp.check(not public.can_read_media('eeeeeeee-0000-0000-0000-000000000302/rooms/22222222-2222-3333-4444-555555555555.jpg'), 'unknown photo: no');
reset role;
select pg_temp.check(not exists (select 1 from public.stale_media(1000) s where s = 'eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg'),
                     'stale_media keeps a room photo in use');
set local role authenticated;

-- ---- song requests: suggest, vote, the most voted plays ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000302');
select pg_temp.check(public.voice_queue_add(pg_temp.r(), 'BddP6PYo2gs', 'Kesariya') = 'suggested', 'member suggests');
select pg_temp.check(not (pg_temp.state()->'queue'->0->>'suggested')::boolean, 'suggestion goes straight into the queue');
select public.voice_queue_add(pg_temp.r(), 'Umqb9KENgmk', 'Tum Hi Ho');
select public.voice_queue_add(pg_temp.r(), '284Ov7ysmfA', 'Channa Mereya');
select pg_temp.fails($$select public.voice_queue_add(pg_temp.r(), 'VAdGW7QDJiU', '')$$, 'slow_down');
insert into v select 'q' || n, q->>'id' from jsonb_array_elements(pg_temp.state()->'queue') with ordinality as x(q, n);
select public.voice_vote(pg_temp.r(), pg_temp.r('q2'), true);
select public.voice_vote(pg_temp.r(), pg_temp.r('q2'), true); -- once per person
select pg_temp.check((pg_temp.state()->'queue'->1->>'votes')::int = 1 and (pg_temp.state()->'queue'->1->>'voted')::boolean, 'one vote, mine');
select pg_temp.fails($$select public.voice_vote(pg_temp.r(), 'nope', true)$$, 'not_found');
select pg_temp.fails($$select public.voice_skip(pg_temp.r())$$, 'not_allowed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000301');
select pg_temp.check(not (pg_temp.state()->'queue'->1->>'voted')::boolean, 'voted is per person');
select public.voice_vote(pg_temp.r(), pg_temp.r('q3'), true);
select public.voice_vote(pg_temp.r(), pg_temp.r('q2'), true);
select public.voice_skip(pg_temp.r());
select pg_temp.check((select r.music->>'videoId' from public.voice_rooms r where r.id = pg_temp.r()) = 'Umqb9KENgmk', 'most voted plays (2 votes)');
select public.voice_vote(pg_temp.r(), pg_temp.r('q3'), false); -- taken back: q1 and q3 tie at 0
select pg_temp.check((pg_temp.state()->'queue'->1->>'votes')::int = 0, 'vote taken back');
select public.voice_skip(pg_temp.r());
select pg_temp.check((select r.music->>'videoId' from public.voice_rooms r where r.id = pg_temp.r()) = 'BddP6PYo2gs', 'tie: the oldest plays');
select public.voice_queue_remove(pg_temp.r(), pg_temp.r('q3'));
select pg_temp.check(jsonb_array_length(pg_temp.state()->'queue') = 0, 'host removes a song');

-- ---- Ludo leaderboard ----
reset role;
insert into public.ludo_games (room_id, opened_by, status, players, names, winner, ended_at) values
  ((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000301', 'finished',
   array['eeeeeeee-0000-0000-0000-000000000301', 'eeeeeeee-0000-0000-0000-000000000302', null, null]::uuid[], array['RHost', 'RWoman', null, null], 1, now() - interval '3 hours'),
  ((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000301', 'finished',
   array['eeeeeeee-0000-0000-0000-000000000301', 'eeeeeeee-0000-0000-0000-000000000302', null, null]::uuid[], array['RHost', 'RWoman', null, null], 1, now() - interval '2 hours'),
  ((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000301', 'finished',
   array['eeeeeeee-0000-0000-0000-000000000301', 'eeeeeeee-0000-0000-0000-000000000302', null, null]::uuid[], array['RHost', 'RWoman', null, null], 0, now() - interval '1 hour'),
  ((select id from v where k = 'r'), 'eeeeeeee-0000-0000-0000-000000000301', 'cancelled',
   array['eeeeeeee-0000-0000-0000-000000000301', 'eeeeeeee-0000-0000-0000-000000000302', null, null]::uuid[], array['RHost', 'RWoman', null, null], null, now());
set local role authenticated;
select pg_temp.check(public.voice_leaders(pg_temp.r())->0->>'name' = 'RWoman' and (public.voice_leaders(pg_temp.r())->0->>'wins')::int = 2, 'leader: 2 wins');
select pg_temp.check(jsonb_array_length(public.voice_leaders(pg_temp.r())) = 2 and (public.voice_leaders(pg_temp.r())->1->>'wins')::int = 1, 'cancelled games do not count');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000303');
select pg_temp.fails($$select public.voice_leaders(pg_temp.r())$$, 'not_allowed');

-- ---- reports carry the photos ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000301');
select public.report_user('eeeeeeee-0000-0000-0000-000000000302', 'nudity');
reset role;
select pg_temp.check((select count(*) from public.reports r, jsonb_array_elements(r.evidence->'voice') e
                       where r.reporter = 'eeeeeeee-0000-0000-0000-000000000301' and r.reported = 'eeeeeeee-0000-0000-0000-000000000302'
                         and e->>'media' like '%/rooms/%') = 5, 'report evidence: room photos');
set local role authenticated;
select pg_temp.check(not public.can_read_media('eeeeeeee-0000-0000-0000-000000000302/rooms/11111111-2222-3333-4444-555555555555.jpg'), 'blocked: photo hidden');

-- ---- a public room: listed with its rules and why I can't come in ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000306');
insert into v values ('p', public.create_voice_room2('Open talk', 'talk', 50, 'male', 18, 100, false, null)->>'id');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000302');
select pg_temp.check((select l.refusal from public.voice_list2() l where l.id = pg_temp.r('p')) = 'room_gender', 'list says why not');
select pg_temp.fails($$select public.join_voice(pg_temp.r('p'))$$, 'room_gender');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000303');
select pg_temp.check((select l.refusal is null and l.code is null and l.category = 'talk' from public.voice_list2() l where l.id = pg_temp.r('p')), 'listed, open to me');
select public.join_voice(pg_temp.r('p'));

-- ---- users can't call the internals ----
select pg_temp.check(not has_function_privilege('authenticated', 'public.voice_admit(text, uuid, boolean)', 'execute'), 'admit hidden');
select pg_temp.check(not has_function_privilege('authenticated', 'public.voice_post(text, uuid, text, text, jsonb, text)', 'execute'), 'post hidden');
select pg_temp.check(not has_table_privilege('authenticated', 'public.voice_votes', 'select'), 'votes not readable');
select pg_temp.check(not has_table_privilege('authenticated', 'public.voice_room_codes', 'select'), 'codes not readable');
select 'all room checks passed' as result;
rollback;
