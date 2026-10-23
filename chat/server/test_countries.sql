-- Migration 043: random chat by chosen countries (both sides' choices must fit), manners no longer close random
-- chat, and the old find_match2 still works (= anywhere). One transaction, rolled back.
begin;
set local statement_timeout = '20s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000004301', 'authenticated', 'authenticated', '919855574301'),
  ('eeeeeeee-0000-0000-0000-000000004302', 'authenticated', 'authenticated', '819055574302'),
  ('eeeeeeee-0000-0000-0000-000000004303', 'authenticated', 'authenticated', '821055574303');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000004301', 'Asha', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004302', 'Yuki', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004303', 'Minho', 'male', '2000-01-01');
select pg_temp.check((select array_agg(country order by country) = array['IN','JP','KR'] from public.profiles where id::text like 'eeeeeeee-0000-0000-0000-00000000430%'), 'countries from numbers');
update public.profiles set manner = 5 where id = 'eeeeeeee-0000-0000-0000-000000004303';
create temp table r (m uuid);
grant all on r to authenticated;
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004302');
select pg_temp.check(public.find_match2('any', 'en') is null, 'Yuki waits (old app call)');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004303');
select pg_temp.check(public.find_match3('any', 'en', array['IN']) is null, 'Minho (low manners: allowed) wants India only: not Yuki');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004301');
insert into r select public.find_match3('any', 'en', array['KR']);
reset role;
select pg_temp.check((select x.user_a = 'eeeeeeee-0000-0000-0000-000000004303' from public.matches x join r on r.m = x.id), 'Asha (wants KR) meets Minho, not Yuki who waited first');
select pg_temp.check((select count(*) = 1 from public.queue q where q.user_id = 'eeeeeeee-0000-0000-0000-000000004302' and q.seen_at > now() - interval '1 minute'), 'Yuki still waiting');
select 'test_countries: all passed' as result;
rollback;
