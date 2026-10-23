-- Reports keep evidence (migration 013). One transaction, rolled back.
begin;
set local statement_timeout = '8s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000001', 'authenticated', 'authenticated', '919855577771'),
  ('eeeeeeee-0000-0000-0000-000000000002', 'authenticated', 'authenticated', '919855577772');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000001', 'Reporter', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000002', 'Rude One', 'male', '1999-01-01');
insert into public.matches (id, user_a, user_b) values
  ('eeeeeeee-0000-0000-0000-0000000000e0', 'eeeeeeee-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000002');
insert into public.messages (match_id, sender, body) values
  ('eeeeeeee-0000-0000-0000-0000000000e0', 'eeeeeeee-0000-0000-0000-000000000001', 'hi'),
  ('eeeeeeee-0000-0000-0000-0000000000e0', 'eeeeeeee-0000-0000-0000-000000000002', 'something rude');

set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000001');
-- No match id given: the latest chat between the two is kept anyway.
select public.report_user('eeeeeeee-0000-0000-0000-000000000002', 'harassment', null);
reset role;

select pg_temp.check((select r.evidence->>'name' from public.reports r where r.reporter = 'eeeeeeee-0000-0000-0000-000000000001') = 'Rude One', 'name kept');
select pg_temp.check((select jsonb_array_length(r.evidence->'chat') from public.reports r where r.reporter = 'eeeeeeee-0000-0000-0000-000000000001') = 2, 'chat kept');
select pg_temp.check((select r.evidence->'chat'->1->>'by' from public.reports r where r.reporter = 'eeeeeeee-0000-0000-0000-000000000001') = 'reported', 'oldest first, sides labelled');
select pg_temp.check((select r.evidence->'chat'->1->>'body' from public.reports r where r.reporter = 'eeeeeeee-0000-0000-0000-000000000001') = 'something rude', 'body kept');
select pg_temp.check((select ended_at is not null from public.matches where id = 'eeeeeeee-0000-0000-0000-0000000000e0'), 'chat ended');
select pg_temp.check((select confdeltype from pg_constraint where conname = 'reports_reported_fkey') = 'n'
                 and (select confdeltype from pg_constraint where conname = 'reports_reporter_fkey') = 'n', 'reports survive account deletion');
select pg_temp.check(not has_function_privilege('authenticated', 'public.stale_media(integer)', 'execute')
                 and not has_function_privilege('anon', 'public.purge_key_ok(text)', 'execute'), 'clean-up functions are private');

select 'all retention checks passed' as result;
rollback;
