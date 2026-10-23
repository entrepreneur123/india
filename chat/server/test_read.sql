-- Read ticks (migration 015). One transaction, rolled back.
begin;
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'authenticated', 'authenticated', '919855577775'),
  ('eeeeeeee-0000-0000-0000-000000000006', 'authenticated', 'authenticated', '919855577776'),
  ('eeeeeeee-0000-0000-0000-000000000007', 'authenticated', 'authenticated', '919855577777');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'A', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000006', 'B', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000007', 'C', 'male', '1999-01-01');
insert into public.matches (id, user_a, user_b) values ('eeeeeeee-0000-0000-0000-0000000000e5', 'eeeeeeee-0000-0000-0000-000000000005', 'eeeeeeee-0000-0000-0000-000000000006');
create temp table ids as with i as (insert into public.messages (match_id, sender, body) values
  ('eeeeeeee-0000-0000-0000-0000000000e5', 'eeeeeeee-0000-0000-0000-000000000005', 'one'),
  ('eeeeeeee-0000-0000-0000-0000000000e5', 'eeeeeeee-0000-0000-0000-000000000005', 'two') returning id) select max(id) top from i;
grant select on ids to authenticated;
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000006');
select public.mark_read('eeeeeeee-0000-0000-0000-0000000000e5', (select top from ids) + 1000);
select pg_temp.check((select read_b from public.matches where id = 'eeeeeeee-0000-0000-0000-0000000000e5') = (select top from ids), 'capped at newest');
select public.mark_read('eeeeeeee-0000-0000-0000-0000000000e5', 1);
select pg_temp.check((select read_b from public.matches where id = 'eeeeeeee-0000-0000-0000-0000000000e5') = (select top from ids), 'never backwards');
select pg_temp.check((select read_a from public.matches where id = 'eeeeeeee-0000-0000-0000-0000000000e5') is null, 'other side untouched');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000007');
select public.mark_read('eeeeeeee-0000-0000-0000-0000000000e5', 5);
reset role;
select pg_temp.check((select read_a from public.matches where id = 'eeeeeeee-0000-0000-0000-0000000000e5') is null, 'outsider cannot mark');
select 'read receipt checks passed' r;
rollback;
