-- Migration 042: block list and unblock, ID search (and viewing who you found), gender/birth kept with the phone
-- number, 30-day ID changes with paid passes and their refund. One transaction, rolled back.
begin;
set local statement_timeout = '20s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create function pg_temp.fails(q text, code text) returns void language plpgsql as $$
declare ok boolean := false;
begin
  begin execute q; exception when others then if sqlerrm like '%' || code || '%' then ok := true; else raise; end if; end;
  if not ok then raise exception 'failed: expected % from %', code, q; end if;
end $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000004201', 'authenticated', 'authenticated', '919855574201'),
  ('eeeeeeee-0000-0000-0000-000000004202', 'authenticated', 'authenticated', '919855574202'),
  ('eeeeeeee-0000-0000-0000-000000004203', 'authenticated', 'authenticated', '919855574203');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000004201', 'Asha', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004202', 'Ravi', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004203', 'Kiran', 'male', '2000-01-01');
update public.profiles set handle = 'zq_ravi%x' where id = 'eeeeeeee-0000-0000-0000-000000004202';
update public.profiles set handle = 'zq_kiran' where id = 'eeeeeeee-0000-0000-0000-000000004203';

set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004201');
-- Strangers: not viewable, until found by ID.
select pg_temp.check(not public.can_view('eeeeeeee-0000-0000-0000-000000004202'), 'stranger hidden');
select pg_temp.check((select count(*) = 2 from public.search_handles('@ZQ_')), 'prefix search, @ and case ignored');
select pg_temp.check((select array_agg(handle) = array['zq_kiran', 'zq_ravi%x'] from public.search_handles('zq_')), 'sorted');
select pg_temp.check((select count(*) = 0 from public.search_handles('zq%')), '% is a letter, not a wildcard');
select pg_temp.check((select count(*) = 1 from public.search_handles('zq_ravi%')), '% matched literally');
select pg_temp.check((select count(*) = 0 from public.search_handles('z_')), '_ is a letter, not a wildcard');
select pg_temp.check((select count(*) = 0 from public.search_handles('  ')), 'empty query');
select pg_temp.check(public.can_view('eeeeeeee-0000-0000-0000-000000004202'), 'found by ID: viewable');
select pg_temp.check((select look is not null from public.search_handles('zq_kiran')), 'look comes with the row');
-- Search did not open everyone: Kiran was found, Asha's own lookups do not leak to Ravi.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004202');
select pg_temp.check(not public.can_view('eeeeeeee-0000-0000-0000-000000004203'), 'other people''s lookups do not count');
select pg_temp.check((select count(*) = 0 from public.search_handles('zq_ravi')), 'not myself');

-- Blocks: hidden from search, listed, unblocked.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004201');
select public.block_user('eeeeeeee-0000-0000-0000-000000004203');
select pg_temp.check((select count(*) = 1 from public.search_handles('zq_')), 'blocked: not in search');
select pg_temp.check(not public.can_view('eeeeeeee-0000-0000-0000-000000004203'), 'blocked: not viewable');
select pg_temp.check((select array_agg(handle) = array['zq_kiran'] from public.my_blocks()), 'block list');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004203');
select pg_temp.check((select count(*) = 0 from public.my_blocks()), 'only my own blocks');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004201');
select public.unblock_user('eeeeeeee-0000-0000-0000-000000004203');
select pg_temp.check((select count(*) = 0 from public.my_blocks()), 'unblocked');
select pg_temp.check((select count(*) = 2 from public.search_handles('zq_')), 'unblocked: back in search');

-- Locked: gender, birth date, country.
select pg_temp.fails($$ update public.profiles set gender = 'male' where id = 'eeeeeeee-0000-0000-0000-000000004201' $$, 'permission denied');
select pg_temp.fails($$ update public.profiles set birth_date = '2001-01-01' where id = 'eeeeeeee-0000-0000-0000-000000004201' $$, 'permission denied');
select pg_temp.fails($$ update public.profiles set country = 'KR' where id = 'eeeeeeee-0000-0000-0000-000000004201' $$, 'permission denied');

-- IDs: the first change from the free one is free, then 30 days, or a pass.
select public.set_handle('zq_asha');
select pg_temp.fails($$ select public.set_handle('zq_asha2') $$, 'handle_cooldown');
select public.set_handle('ZQ_Asha');
select pg_temp.check((select (shop() ->> 'handle_passes')::int = 0 and (shop() ->> 'handle_next')::timestamptz > now() + interval '29 days'), 'shop: 30 days');
select pg_temp.fails($$ select public.redeem_handle_pass('eeeeeeee-0000-0000-0000-000000004201', 'tok42') $$, 'permission denied');
reset role;
select pg_temp.check(public.redeem_handle_pass('eeeeeeee-0000-0000-0000-000000004201', 'tok42') = 'granted', 'pass granted');
select pg_temp.check(public.redeem_handle_pass('eeeeeeee-0000-0000-0000-000000004201', 'tok42') = 'already', 'pass once per token');
select pg_temp.check(public.redeem_handle_pass('eeeeeeee-0000-0000-0000-000000004202', 'tok42') = 'other_account', 'token is one account''s');
select pg_temp.check(public.redeem_handle_pass('eeeeeeee-0000-0000-0000-000000004201', 'tok43') = 'granted', 'second pass');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004201');
select public.set_handle('zq_asha2');
select pg_temp.check((select (shop() ->> 'handle_passes')::int = 1), 'a pass used');
reset role;
select public.play_revoke('tok43', 'refund');
select public.play_revoke('tok43', 'refund');
select pg_temp.check((select handle_passes = 0 from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004201'), 'refund takes the unused pass, once');
select pg_temp.check(public.redeem_handle_pass('eeeeeeee-0000-0000-0000-000000004201', 'tok43') = 'revoked', 'refunded token stays refunded');

-- Delete and sign up again with the same number: gender and birth date come back.
select public.remember_ban('eeeeeeee-0000-0000-0000-000000004202');
update auth.users set phone = null where id = 'eeeeeeee-0000-0000-0000-000000004202'; -- as if deleted: the number is free again
insert into auth.users (id, aud, role, phone) values ('eeeeeeee-0000-0000-0000-000000004204', 'authenticated', 'authenticated', '919855574202');
insert into public.profiles (id, name, gender, birth_date) values ('eeeeeeee-0000-0000-0000-000000004204', 'Ravi', 'female', '1980-05-05');
select pg_temp.check((select gender = 'male' and birth_date = '1999-01-01' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004204'), 'kept with the number');

select 'test_blocks_search: all passed' as result;
rollback;
