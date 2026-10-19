-- Server rule tests. Runs in one transaction and rolls back: nothing is left behind.
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

insert into auth.users (id, aud, role, phone) values
  ('00000000-0000-0000-0000-00000000000a', 'authenticated', 'authenticated', '919800000001'),
  ('00000000-0000-0000-0000-00000000000b', 'authenticated', 'authenticated', '919800000002'),
  ('00000000-0000-0000-0000-00000000000c', 'authenticated', 'authenticated', '919800000003'),
  ('00000000-0000-0000-0000-00000000000d', 'authenticated', 'authenticated', '919800000004'),
  ('00000000-0000-0000-0000-0000000000e1', 'authenticated', 'authenticated', '919800000005'),
  ('00000000-0000-0000-0000-0000000000e2', 'authenticated', 'authenticated', '919800000006');

set local role authenticated;

-- 1. 18+ and immutable gender/birth date
select pg_temp.act('00000000-0000-0000-0000-00000000000a');
select pg_temp.fails($q$insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-00000000000a', 'Kid', 'male', current_date - interval '17 years')$q$, 'under_18');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-00000000000a', 'Arjun', 'male', current_date - interval '18 years');
select pg_temp.fails($q$update public.profiles set gender = 'female' where id = '00000000-0000-0000-0000-00000000000a'$q$, 'permission denied');
select pg_temp.fails($q$update public.profiles set banned_until = null where id = '00000000-0000-0000-0000-00000000000a'$q$, 'permission denied');
select pg_temp.fails($q$insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-00000000000b', 'Fake', 'male', '2000-01-01')$q$, 'row-level security');
select pg_temp.act('00000000-0000-0000-0000-00000000000b');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-00000000000b', 'Priya', 'female', '2001-05-05');
select pg_temp.act('00000000-0000-0000-0000-00000000000c');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-00000000000c', 'Rahul', 'male', '1999-01-01');
select pg_temp.act('00000000-0000-0000-0000-00000000000d');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-00000000000d', 'Sneha', 'female', '1998-01-01');
select pg_temp.act('00000000-0000-0000-0000-0000000000e1');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000e1', 'R1', 'male', '1990-01-01');
select pg_temp.act('00000000-0000-0000-0000-0000000000e2');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000e2', 'R2', 'male', '1990-01-01');

-- 2. Strangers can't see each other's profile before a match; Premium is required for a gender filter
select pg_temp.act('00000000-0000-0000-0000-00000000000a');
do $$ begin assert (select count(*) from public.profile_card('00000000-0000-0000-0000-00000000000b')) = 0, 'card visible before match'; end $$;
do $$ begin assert (select count(*) from public.profiles) = 1, 'sees other profiles rows'; end $$;
select pg_temp.fails($q$select public.find_match('female')$q$, 'premium_required');
select pg_temp.fails($q$insert into public.subscriptions values ('00000000-0000-0000-0000-00000000000a', now() + interval '30 days')$q$, 'permission denied');

-- 3. Matching: A waits, B arrives and is matched with A; A's next poll returns the same match
do $$ begin assert public.find_match('any') is null, 'A should queue'; end $$;
select pg_temp.act('00000000-0000-0000-0000-00000000000b');
create temp table t (mid uuid);
insert into t select public.find_match('any');
do $$ begin assert (select mid from t) is not null, 'B should match A'; end $$;
select pg_temp.act('00000000-0000-0000-0000-00000000000a');
do $$ begin assert public.find_match('any') = (select mid from t), 'A should get the same match'; end $$;
do $$ begin assert (select age from public.profile_card('00000000-0000-0000-0000-00000000000b')) >= 18, 'card after match'; end $$;

-- 4. Chat + RLS: participants read/write, outsiders can't
insert into public.messages (match_id, body) select mid, 'hello' from t;
select pg_temp.fails($q$insert into public.messages (match_id, body, sender) select mid, 'spoof', '00000000-0000-0000-0000-00000000000b' from t$q$, 'permission denied');
select pg_temp.act('00000000-0000-0000-0000-00000000000c');
do $$ begin assert (select count(*) from public.messages) = 0, 'outsider reads chat'; end $$;
select pg_temp.fails($q$insert into public.messages (match_id, body) select mid, 'intrude' from t$q$, 'row-level security');

-- 5. Exit lock: free user can't leave (or skip via find_match) within 5 s; after 5 s they can
select pg_temp.act('00000000-0000-0000-0000-00000000000b');
select pg_temp.fails($q$select public.leave_match((select mid from t))$q$, 'exit_locked');
select pg_temp.fails($q$select public.find_match('any')$q$, 'exit_locked');
reset role;
update public.matches set created_at = now() - interval '5 seconds' where id = (select mid from t);
set local role authenticated;
select public.leave_match((select mid from t));
do $$ begin assert (select ended_at from public.matches where id = (select mid from t)) is not null, 'not ended'; end $$;
-- partner already left: no lock for A, and leaving again is a no-op
select pg_temp.act('00000000-0000-0000-0000-00000000000a');
select public.leave_match((select mid from t));

-- 6. Premium: 0.2 s lock, gender filter works both ways, last partner is skipped
reset role;
insert into public.subscriptions values ('00000000-0000-0000-0000-00000000000a', now() + interval '30 days');
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-00000000000d');   -- Sneha (female) waits for men
do $$ begin assert public.find_match('any') is null; end $$;
select pg_temp.act('00000000-0000-0000-0000-00000000000b');   -- Priya waits too (last partner of A)
do $$ begin assert public.find_match('any') is null; end $$;
select pg_temp.act('00000000-0000-0000-0000-00000000000a');   -- Arjun, Premium, wants women: gets Sneha (B was his last partner)
truncate t; insert into t select public.find_match('female');
do $$ begin assert (select user_a from public.matches where id = (select mid from t)) = '00000000-0000-0000-0000-00000000000d', 'should match Sneha, not last partner'; end $$;
reset role;
update public.matches set created_at = now() - interval '250 milliseconds' where id = (select mid from t);
set local role authenticated;
select public.leave_match((select mid from t)); -- 0.25 s > 0.2 s Premium lock

-- 7. Video: needs Premium to ask; only the other person can accept
select pg_temp.act('00000000-0000-0000-0000-00000000000c');   -- Rahul (free) meets Priya, who is still waiting
truncate t; insert into t select public.find_match('any');
do $$ begin assert (select user_a from public.matches where id = (select mid from t)) = '00000000-0000-0000-0000-00000000000b'; end $$;
select pg_temp.fails($q$select public.request_video((select mid from t))$q$, 'premium_required');
reset role;
insert into public.subscriptions values ('00000000-0000-0000-0000-00000000000c', now() + interval '30 days');
set local role authenticated;
select public.request_video((select mid from t));
select pg_temp.fails($q$select public.respond_video((select mid from t), true)$q$, 'not_allowed'); -- can't accept own request
select pg_temp.act('00000000-0000-0000-0000-00000000000b');
select public.respond_video((select mid from t), true);
do $$ begin assert (select video_status from public.matches where id = (select mid from t)) = 'accepted'; end $$;

-- 8. Report: instant even inside the lock, blocks, ends chat, hides profile; blocked pair never rematches
select public.report_user('00000000-0000-0000-0000-00000000000c', 'harassment', (select mid from t));
do $$ begin assert (select ended_at from public.matches where id = (select mid from t)) is not null, 'report did not end chat'; end $$;
do $$ begin assert (select count(*) from public.profile_card('00000000-0000-0000-0000-00000000000c')) = 0, 'blocked card visible'; end $$;
do $$ begin assert (select count(*) from public.my_recents() where partner = '00000000-0000-0000-0000-00000000000c') = 0, 'blocked in recents'; end $$;
select pg_temp.fails($q$insert into public.messages (match_id, body) select mid, 'after block' from t$q$, 'row-level security');
do $$ begin assert public.find_match('any') is null; end $$;  -- Priya queues
select pg_temp.act('00000000-0000-0000-0000-00000000000c');
do $$ begin assert public.find_match('any') is null, 'blocked pair matched'; end $$;
select public.cancel_search();

-- 9. Auto-ban: 3 different reporters in 24 h ban the user and stop matching/sending
select pg_temp.act('00000000-0000-0000-0000-0000000000e1');
reset role; insert into public.matches (user_a, user_b, ended_at) values ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-00000000000c', now()),
  ('00000000-0000-0000-0000-0000000000e2', '00000000-0000-0000-0000-00000000000c', now()); set local role authenticated;
select public.report_user('00000000-0000-0000-0000-00000000000c', 'nudity');
select pg_temp.act('00000000-0000-0000-0000-0000000000e2');
select public.report_user('00000000-0000-0000-0000-00000000000c', 'nudity');
reset role;
do $$ begin assert (select banned_until from public.profiles where id = '00000000-0000-0000-0000-00000000000c') > now(), 'not banned after 3 reports'; end $$;
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-00000000000c');
select pg_temp.fails($q$select public.find_match('any')$q$, 'banned');

-- 10. Posts visible only to matched people; spam limit
select pg_temp.act('00000000-0000-0000-0000-00000000000d');
insert into public.posts (body) values ('Sneha post');
select pg_temp.act('00000000-0000-0000-0000-00000000000a');
do $$ begin assert (select count(*) from public.posts) = 1, 'matched user should see post'; end $$;
select pg_temp.act('00000000-0000-0000-0000-0000000000e1');
do $$ begin assert (select count(*) from public.posts) = 0, 'stranger sees post'; end $$;

select 'ALL SERVER TESTS PASSED' as result;
rollback;
