-- Room code guessing limit (migration 014). One transaction, rolled back.
begin;
set local statement_timeout = '8s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000003', 'authenticated', 'authenticated', '919855577773'),
  ('eeeeeeee-0000-0000-0000-000000000004', 'authenticated', 'authenticated', '919855577774');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000003', 'Host', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000004', 'Guesser', 'male', '1999-01-01');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000003');
create temp table rc on commit drop as select * from public.create_room('Code test', 'chat', 10, 'any', 18, 100, true);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000004');
select pg_temp.check(public.join_room_code('ZZZZZ' || g) is null, 'wrong code is null') from generate_series(1, 9) g;
select pg_temp.check(public.join_room_code((select room_code from rc)) = (select room_id from rc), 'right code works after 9 misses');
select pg_temp.check(public.join_room_code('WRONG1') is null, '10th miss');
do $$ begin
  perform public.join_room_code((select room_code from rc));
  raise exception 'no limit after 10 misses';
exception when others then if sqlerrm not like '%slow_down%' then raise; end if; end $$;
select pg_temp.check(not has_table_privilege('authenticated', 'public.room_code_tries', 'select'), 'tries table hidden from users');
reset role;
update public.room_code_tries set since = now() - interval '11 minutes' where user_id = 'eeeeeeee-0000-0000-0000-000000000004';
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000004');
select pg_temp.check(public.join_room_code((select room_code from rc)) = (select room_id from rc), 'allowed again after 10 minutes');
select 'all room code checks passed' as result;
rollback;
