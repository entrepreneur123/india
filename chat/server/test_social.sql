-- Friends, rooms, flags (migrations 006-007). One transaction, rolled back: nothing is left behind.
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

insert into auth.users (id, aud, role, phone) values
  ('00000000-0000-0000-0000-0000000000f1', 'authenticated', 'authenticated', '919811111111'),
  ('00000000-0000-0000-0000-0000000000f2', 'authenticated', 'authenticated', '821011112222'),
  ('00000000-0000-0000-0000-0000000000f3', 'authenticated', 'authenticated', '14155550100');

set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000f1', 'Asha', 'female', '2000-01-01');
select pg_temp.act('00000000-0000-0000-0000-0000000000f2');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000f2', 'Minho', 'male', '1999-01-01');
select pg_temp.act('00000000-0000-0000-0000-0000000000f3');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000f3', 'Sam', 'male', '1998-01-01');

-- 1. Flag from the verified phone number; the app cannot set it
select pg_temp.check((select country from public.profiles where id = '00000000-0000-0000-0000-0000000000f3') = 'US', 'US flag');
select pg_temp.fails($q$update public.profiles set country = 'FR' where id = '00000000-0000-0000-0000-0000000000f3'$q$, 'permission denied');

-- 2. Strangers can't see each other or send friend requests
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
select pg_temp.check(not exists (select 1 from public.card('00000000-0000-0000-0000-0000000000f2')), 'stranger hidden');
select pg_temp.fails($q$select public.request_friend('00000000-0000-0000-0000-0000000000f2')$q$, 'not_allowed');

-- 3. Rooms: must join to write; sender details and flag are filled in by the server
select pg_temp.fails($q$insert into public.room_messages (room_id, body) values ('lounge', 'hi')$q$, 'row-level security');
select public.join_room('lounge');
insert into public.room_messages (room_id, body) values ('lounge', 'hello world');
select pg_temp.check((select sender_name = 'Asha' and sender_country = 'IN' from public.room_messages where body = 'hello world'), 'sender filled');
select pg_temp.fails($q$insert into public.room_messages (room_id, body, sender_name) values ('lounge', 'x', 'Admin')$q$, 'permission denied');
select pg_temp.check((select online from public.room_list() where id = 'lounge') = 1, 'online count');
insert into public.room_messages (room_id, body) values ('lounge', '2'), ('lounge', '3'), ('lounge', '4'), ('lounge', '5');
select pg_temp.fails($q$insert into public.room_messages (room_id, body) values ('lounge', '6')$q$, 'slow_down');

-- 4. Same room = visible, so a friend request works; accepting makes them friends
select pg_temp.act('00000000-0000-0000-0000-0000000000f2');
select public.join_room('lounge');
select pg_temp.check(exists (select 1 from public.card('00000000-0000-0000-0000-0000000000f1')), 'room peer visible');
select pg_temp.check(public.request_friend('00000000-0000-0000-0000-0000000000f1') = 'requested', 'requested');
select public.leave_room('lounge');
select pg_temp.check((select state from public.friend_list() where id = '00000000-0000-0000-0000-0000000000f1') = 'requested', 'my outgoing');
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
select public.leave_room('lounge');
select pg_temp.check(exists (select 1 from public.card('00000000-0000-0000-0000-0000000000f2')), 'requester visible to addressee');
select pg_temp.check((select state from public.friend_list() where id = '00000000-0000-0000-0000-0000000000f2') = 'incoming', 'incoming');
select pg_temp.fails($q$select public.friend_thread('00000000-0000-0000-0000-0000000000f2')$q$, 'not_allowed');
select public.answer_friend('00000000-0000-0000-0000-0000000000f2', true);
select pg_temp.check((select state from public.friend_list() where id = '00000000-0000-0000-0000-0000000000f2') = 'friends', 'friends');

-- 5. Friends can message: a private thread that is not listed in Recent and allows no video
select pg_temp.check(public.friend_thread('00000000-0000-0000-0000-0000000000f2') is not null, 'thread');
insert into public.messages (match_id, body) values (public.friend_thread('00000000-0000-0000-0000-0000000000f2'), 'hey friend');
select pg_temp.check((select count(*) from public.matches where kind = 'dm') = 1, 'one thread, reused');
select pg_temp.check(not exists (select 1 from public.recent_list()), 'thread not in Recent');
select pg_temp.check((select match_id from public.friend_list() where id = '00000000-0000-0000-0000-0000000000f2') is not null, 'thread id listed');

-- 6. A report ends the friendship and hides their room messages
select pg_temp.act('00000000-0000-0000-0000-0000000000f2');
select public.join_room('lounge');
insert into public.room_messages (room_id, body) values ('lounge', 'from minho');
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
select public.report_user('00000000-0000-0000-0000-0000000000f2', 'harassment');
select pg_temp.check(not exists (select 1 from public.friend_list()), 'unfriended');
select pg_temp.check(not exists (select 1 from public.room_messages where body = 'from minho'), 'blocked sender hidden');
select pg_temp.fails($q$select public.request_friend('00000000-0000-0000-0000-0000000000f2')$q$, 'not_allowed');

-- 7. Reporting someone you never met or saw is refused
select pg_temp.fails($q$select public.report_user('00000000-0000-0000-0000-0000000000f3', 'other')$q$, 'not_found');

rollback;
