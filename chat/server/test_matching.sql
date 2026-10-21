-- Matching (migration 011). One transaction, rolled back: nothing is left behind.
-- Concurrency (two calls pairing the same person) can't be shown in one transaction; it rests on each call
-- holding its own queue row and skipping locked ones (FOR UPDATE SKIP LOCKED).
begin;
set local statement_timeout = '8s';
-- 020 revoked the old app's functions; they are still exercised here (rolled back) to keep the logic covered.
grant execute on function public.find_match(text) to authenticated;
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create temp table t (k text primary key, v text);
grant all on t to authenticated;

-- Nobody else may be waiting during the test.
update public.queue set seen_at = '-infinity' where seen_at > now() - interval '1 minute';

insert into auth.users (id, aud, role, phone) values
  ('00000000-0000-0000-0000-0000000000f1', 'authenticated', 'authenticated', '919844444441'),
  ('00000000-0000-0000-0000-0000000000f2', 'authenticated', 'authenticated', '919844444442'),
  ('00000000-0000-0000-0000-0000000000f3', 'authenticated', 'authenticated', '919844444443'),
  ('00000000-0000-0000-0000-0000000000f4', 'authenticated', 'authenticated', '919844444444'),
  ('00000000-0000-0000-0000-0000000000f5', 'authenticated', 'authenticated', '919844444445'),
  ('00000000-0000-0000-0000-0000000000f6', 'authenticated', 'authenticated', '919844444446');
insert into public.profiles (id, name, gender, birth_date) values
  ('00000000-0000-0000-0000-0000000000f1', 'Hindi A', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000f2', 'Hindi B', 'female', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000f3', 'Portuguese C', 'female', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000f4', 'Hindi D', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000f5', 'Old app E', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000f6', 'Hindi F', 'male', '1999-01-01');

set local role authenticated;

-- 1. Same language pairs at once; the waiting side gets the same match on its next call.
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
select pg_temp.check(public.find_match2('any', 'hi') is null, 'first searcher waits');
select pg_temp.act('00000000-0000-0000-0000-0000000000f2');
insert into t select 'm1', public.find_match2('any', 'HI');
select pg_temp.check((select v from t where k = 'm1') is not null, 'same language pairs');
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
select pg_temp.check(public.find_match2('any', 'hi')::text = (select v from t where k = 'm1'), 'waiting side gets the match');
reset role; -- the queue is not readable by users
select pg_temp.check(not exists (select 1 from public.queue where seen_at > now() - interval '20 seconds'
  and user_id in ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000f2')), 'both left the line');
set local role authenticated;

-- 2. Different language: no pair while both are fresh ...
select pg_temp.act('00000000-0000-0000-0000-0000000000f3');
select pg_temp.check(public.find_match2('any', 'pt-BR') is null, 'C waits');
select pg_temp.act('00000000-0000-0000-0000-0000000000f4');
select pg_temp.check(public.find_match2('any', 'hi') is null, 'Hindi D does not take Portuguese C at once');
-- ... but after 8 s of waiting anyone goes.
reset role;
update public.queue set joined_at = now() - interval '9 seconds' where user_id = '00000000-0000-0000-0000-0000000000f3';
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000f4');
insert into t select 'm2', public.find_match2('any', 'hi');
select pg_temp.check((select v from t where k = 'm2') is not null, 'falls back to any language after 8 s');
select pg_temp.check((select user_a from public.matches where id = (select v from t where k = 'm2')::uuid)
  = '00000000-0000-0000-0000-0000000000f3', 'paired with the one waiting');

-- 3. The same language is preferred over someone who has waited longer.
reset role;
update public.matches set created_at = now() - interval '1 minute' where id in ((select v from t where k = 'm1')::uuid, (select v from t where k = 'm2')::uuid);
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000f3');
select pg_temp.check(public.find_match2('any', 'pt-br') is null, 'C waits again (ends the old chat)');
reset role;
update public.queue set joined_at = now() - interval '1 hour' where user_id = '00000000-0000-0000-0000-0000000000f2';
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000f2');
select pg_temp.check(public.find_match2('any', 'hi') is null, 'B waits (ends the old chat; C is new and Portuguese)');
reset role;
update public.queue set joined_at = now() - interval '30 seconds' where user_id = '00000000-0000-0000-0000-0000000000f3';
set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000f6');
insert into t select 'm3', public.find_match2('any', 'hi');
select pg_temp.check((select user_a from public.matches where id = (select v from t where k = 'm3')::uuid)
  = '00000000-0000-0000-0000-0000000000f2', 'Hindi B before Portuguese C who waited longer');

-- 4. Rejoining starts a new place in line: B's join time from an hour ago was replaced when B joined again.
reset role;
select pg_temp.check((select joined_at from public.queue where user_id = '00000000-0000-0000-0000-0000000000f2') = now(),
  'a stale row gets a new join time');
set local role authenticated;

-- 5. The old function (no language) still works, and cancelling takes you out.
select pg_temp.act('00000000-0000-0000-0000-0000000000f5');
insert into t select 'm4', public.find_match('any');
select pg_temp.check((select v from t where k = 'm4') is not null, 'old app pairs with Portuguese C');
select pg_temp.act('00000000-0000-0000-0000-0000000000f1');
select pg_temp.check(public.find_match2('any', 'hi') is null, 'A waits');
select public.cancel_search();
select pg_temp.act('00000000-0000-0000-0000-0000000000f2');
reset role;
update public.matches set created_at = now() - interval '1 minute' where id = (select v from t where k = 'm3')::uuid;
set local role authenticated;
select pg_temp.check(public.find_match2('any', 'hi') is null, 'B does not get A, who cancelled');

-- 6. Rules still hold.
select pg_temp.act('00000000-0000-0000-0000-0000000000f4');
do $$ begin
  perform public.find_match2('female', 'hi');
  raise exception 'gender filter without Plus succeeded';
exception when others then
  if sqlerrm not like '%premium_required%' then raise; end if;
end $$;

select 'all matching checks passed' as result;
rollback;
