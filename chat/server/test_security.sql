-- Security hardening (migration 009). One transaction, rolled back: nothing is left behind.
begin;
set local statement_timeout = '8s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.fails(sql text, expect text) returns void language plpgsql as $$
begin
  execute sql;
  raise exception 'expected % but it succeeded: %', expect, sql;
exception when others then
  if sqlerrm not like '%' || expect || '%' then raise exception 'expected %, got %: %', expect, sqlerrm, sql; end if;
end $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create temp table t (k text primary key, v text);
grant all on t to authenticated;

insert into auth.users (id, aud, role, phone) values
  ('00000000-0000-0000-0000-0000000000c1', 'authenticated', 'authenticated', '919833333331'),
  ('00000000-0000-0000-0000-0000000000c2', 'authenticated', 'authenticated', '919833333332'),
  ('00000000-0000-0000-0000-0000000000c3', 'authenticated', 'authenticated', '919833333333'),
  ('00000000-0000-0000-0000-0000000000c4', 'authenticated', 'authenticated', '919833333334'),
  ('00000000-0000-0000-0000-0000000000c5', 'authenticated', 'authenticated', '919833333335');
insert into public.profiles (id, name, gender, birth_date) values
  ('00000000-0000-0000-0000-0000000000c1', 'Victim', 'female', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000c2', 'R1', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000c3', 'R2', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000c4', 'R3', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000c5', 'Outsider', 'male', '1999-01-01');

-- 0. Table rights: function-only tables are closed even without RLS
select pg_temp.check(not has_table_privilege('authenticated', 'public.friendships', 'select'), 'friendships closed');
select pg_temp.check(not has_table_privilege('authenticated', 'public.room_codes', 'select'), 'room_codes closed');
select pg_temp.check(not has_table_privilege('authenticated', 'public.room_messages', 'update'), 'room_messages not updatable');
select pg_temp.check(has_table_privilege('authenticated', 'public.room_messages', 'select'), 'room_messages readable');

set local role authenticated;

-- 1. Room history only while in the room
select pg_temp.act('00000000-0000-0000-0000-0000000000c1');
select public.join_room('lounge');
insert into public.room_messages (room_id, body) values ('lounge', 'victim was here');
select pg_temp.act('00000000-0000-0000-0000-0000000000c5');
select pg_temp.check(not exists (select 1 from public.room_messages where sender = '00000000-0000-0000-0000-0000000000c1'), 'outsider cannot read the room');
select public.join_room('lounge');
select pg_temp.check(exists (select 1 from public.room_messages where sender = '00000000-0000-0000-0000-0000000000c1'), 'member reads the room');

-- 2. Mass reports by fresh accounts: recorded and blocked, but no automatic ban
select pg_temp.act('00000000-0000-0000-0000-0000000000c2'); select public.join_room('lounge');
select public.report_user('00000000-0000-0000-0000-0000000000c1', 'minor');
select pg_temp.act('00000000-0000-0000-0000-0000000000c3'); select public.join_room('lounge');
select public.report_user('00000000-0000-0000-0000-0000000000c1', 'minor');
select pg_temp.act('00000000-0000-0000-0000-0000000000c4'); select public.join_room('lounge');
select public.report_user('00000000-0000-0000-0000-0000000000c1', 'minor');
reset role;
select pg_temp.check((select banned_until from public.profiles where id = '00000000-0000-0000-0000-0000000000c1') is null, 'new accounts cannot auto-ban');
select pg_temp.check((select count(*) from public.reports where reported = '00000000-0000-0000-0000-0000000000c1') = 3, 'reports recorded');
-- ...but established accounts still can
update public.profiles set created_at = now() - interval '10 days'
 where id in ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000c4');
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000c4');
select public.report_user('00000000-0000-0000-0000-0000000000c1', 'nudity');
reset role;
select pg_temp.check((select banned_until from public.profiles where id = '00000000-0000-0000-0000-0000000000c1') > now(), 'established reporters ban');
-- a match id that isn't theirs is not stored
select pg_temp.check(not exists (select 1 from public.reports where reported = '00000000-0000-0000-0000-0000000000c1' and match_id is not null), 'no foreign match ids');
set local role authenticated;

-- 3. Someone who never shared a room or chat can't report
select pg_temp.act('00000000-0000-0000-0000-0000000000c5');
select public.leave_room('lounge');
select pg_temp.act('00000000-0000-0000-0000-0000000000c2');
select public.leave_room('lounge');
select pg_temp.fails($q$select public.report_user('00000000-0000-0000-0000-0000000000c5', 'other')$q$, 'not_found');

-- 4. Media paths must be the sender's own; media only in live chats
reset role;
insert into public.matches (id, user_a, user_b) values ('00000000-0000-0000-0000-00000000c0c0', '00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000c3');
insert into public.matches (id, user_a, user_b, ended_at, ended_by) values
  ('00000000-0000-0000-0000-00000000c0c1', '00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000c4', now(), '00000000-0000-0000-0000-0000000000c2');
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000c2');
select pg_temp.fails($q$insert into public.messages (match_id, media_path, media_kind) values
  ('00000000-0000-0000-0000-00000000c0c0', '00000000-0000-0000-0000-0000000000c1/chat/x.jpg', 'image')$q$, 'messages_media_own');
insert into public.messages (match_id, media_path, media_kind) values
  ('00000000-0000-0000-0000-00000000c0c0', '00000000-0000-0000-0000-0000000000c2/chat/mine.jpg', 'image');
select pg_temp.fails($q$insert into public.messages (match_id, media_path, media_kind) values
  ('00000000-0000-0000-0000-00000000c0c1', '00000000-0000-0000-0000-0000000000c2/chat/late.jpg', 'image')$q$, 'row-level security');
insert into public.messages (match_id, body) values ('00000000-0000-0000-0000-00000000c0c1', 'text in a thread is fine');
select pg_temp.fails($q$update public.profiles set photo_path = '00000000-0000-0000-0000-0000000000c1/avatar/a.jpg' where id = '00000000-0000-0000-0000-0000000000c2'$q$, 'profiles_photo_own');

-- 5. A closed room's code answers like a wrong one
select pg_temp.act('00000000-0000-0000-0000-0000000000c3');
insert into t select 'room', room_id from public.create_room('Closed soon', 'chat', 5, 'any', 18, 100, true);
insert into t select 'code', code from public.room_list2() where id = (select v from t where k = 'room');
select public.close_room((select v from t where k = 'room'));
select pg_temp.act('00000000-0000-0000-0000-0000000000c4');
select pg_temp.fails(format('select public.join_room_code(%L)', (select v from t where k = 'code')), 'not_found');

-- 6. Rate limit still works
select pg_temp.act('00000000-0000-0000-0000-0000000000c3');
select public.join_room('lounge');
insert into public.room_messages (room_id, body) values ('lounge', 'm1');
insert into public.room_messages (room_id, body) values ('lounge', 'm2');
insert into public.room_messages (room_id, body) values ('lounge', 'm3');
insert into public.room_messages (room_id, body) values ('lounge', 'm4');
insert into public.room_messages (room_id, body) values ('lounge', 'm5');
select pg_temp.fails($q$insert into public.room_messages (room_id, body) values ('lounge', 'sixth')$q$, 'slow_down');

select 'all security checks passed' as result;
rollback;
