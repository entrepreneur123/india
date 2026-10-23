-- Migration 045: a lost connection shows after 6 s without a beat and leaves 7 s later (new apps, voice_heartbeat3);
-- old apps keep 90 s; a lost host hands over to a co-host, and without one the room waits. Rolled back.
begin;
set local statement_timeout = '20s';
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000004501', 'authenticated', 'authenticated', '919855574501'),
  ('eeeeeeee-0000-0000-0000-000000004502', 'authenticated', 'authenticated', '919855574502'),
  ('eeeeeeee-0000-0000-0000-000000004503', 'authenticated', 'authenticated', '919855574503'),
  ('eeeeeeee-0000-0000-0000-000000004504', 'authenticated', 'authenticated', '919855574504');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000004501', 'Host', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004502', 'Cohost', 'male', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004503', 'Listener', 'male', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004504', 'Oldapp', 'male', '1998-01-01');
insert into public.voice_rooms (id, owner, host, title) values ('zz-lost', 'eeeeeeee-0000-0000-0000-000000004501', 'eeeeeeee-0000-0000-0000-000000004501', 'Lost test');
insert into public.voice_members (room_id, user_id, role, seat, fast) values
  ('zz-lost', 'eeeeeeee-0000-0000-0000-000000004501', 'host', 0, true),
  ('zz-lost', 'eeeeeeee-0000-0000-0000-000000004502', 'cohost', null, true),
  ('zz-lost', 'eeeeeeee-0000-0000-0000-000000004503', 'listener', null, true),
  ('zz-lost', 'eeeeeeee-0000-0000-0000-000000004504', 'listener', null, false);
create temp view mem as select user_id, left_at is null here, lost_at is not null lost, role from public.voice_members where room_id = 'zz-lost';

select public.voice_settle('zz-lost');
select pg_temp.check((select bool_and(here and not lost) from mem), 'all fine');
-- A new app misses its beats: lost (and the full state says so), then out 7 s later.
update public.voice_members set seen_at = now() - interval '8 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004503';
select public.voice_settle('zz-lost');
select pg_temp.check((select here and lost from mem where user_id = 'eeeeeeee-0000-0000-0000-000000004503'), 'listener lost, still here');
select pg_temp.check((select public.voice_member_json(m) ->> 'lost' = 'true' from public.voice_members m where m.user_id = 'eeeeeeee-0000-0000-0000-000000004503'), 'json says lost');
-- Back in time: a beat clears it.
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004503');
select public.voice_heartbeat3('zz-lost');
reset role;
select pg_temp.check((select here and not lost from mem where user_id = 'eeeeeeee-0000-0000-0000-000000004503'), 'a beat clears lost');
update public.voice_members set seen_at = now() - interval '20 seconds', lost_at = now() - interval '8 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004503';
select public.voice_settle('zz-lost');
select pg_temp.check((select not here from mem where user_id = 'eeeeeeee-0000-0000-0000-000000004503'), 'lost for 7 s: out');
-- An old app (20 s beats) keeps the 90 s rule.
update public.voice_members set seen_at = now() - interval '30 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004504';
select public.voice_settle('zz-lost');
select pg_temp.check((select here and not lost from mem where user_id = 'eeeeeeee-0000-0000-0000-000000004504'), 'old app: still here at 30 s');
update public.voice_members set seen_at = now() - interval '100 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004504';
select public.voice_settle('zz-lost');
select pg_temp.check((select not here from mem where user_id = 'eeeeeeee-0000-0000-0000-000000004504'), 'old app: out after 90 s');
-- The host is lost: after 7 s the co-host takes over.
update public.voice_members set seen_at = now() - interval '8 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004501';
select public.voice_settle('zz-lost');
select pg_temp.check((select here and lost from mem where user_id = 'eeeeeeee-0000-0000-0000-000000004501'), 'host lost, still host');
select pg_temp.check((select host = 'eeeeeeee-0000-0000-0000-000000004501' from public.voice_rooms where id = 'zz-lost'), 'not handed over yet');
update public.voice_members set lost_at = now() - interval '8 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004501';
select public.voice_settle('zz-lost');
select pg_temp.check((select host = 'eeeeeeee-0000-0000-0000-000000004502' and ended_at is null from public.voice_rooms where id = 'zz-lost'), 'co-host took over');
-- A lost host without a co-host: the room waits (up to 2 minutes).
update public.voice_members set seen_at = now() - interval '20 seconds', lost_at = now() - interval '14 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000004502';
select public.voice_settle('zz-lost');
select pg_temp.check((select ended_at is null from public.voice_rooms where id = 'zz-lost'), 'no co-host: room waits');
update public.voice_members set seen_at = now() - interval '3 minutes' where user_id = 'eeeeeeee-0000-0000-0000-000000004502';
select public.voice_settle('zz-lost');
select pg_temp.check((select ended_at is not null from public.voice_rooms where id = 'zz-lost'), 'host gone 2 min, no co-host: room ends');
select 'test_voice_lost: all passed' as result;
rollback;
