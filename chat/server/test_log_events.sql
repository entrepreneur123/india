-- Crash reports and events (migration 021). One transaction, rolled back.
begin;
set local statement_timeout = '20s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create function pg_temp.fails(q text, code text) returns void language plpgsql as $$
declare ok boolean := false;
begin
  begin
    execute q;
  exception when others then
    if sqlerrm like '%' || code || '%' then ok := true; else raise; end if;
  end;
  if not ok then raise exception 'failed: expected % from %', code, q; end if;
end $$;
-- n events named `name`
create function pg_temp.batch(n int, name text default 'x', kind text default 'event') returns jsonb language sql as $$
  select jsonb_agg(jsonb_build_object('kind', kind, 'name', name, 'message', repeat('m', 3000), 'stack', repeat('s', 9000),
                     'version', 'web-1', 'platform', 'web', 'route', '/rooms')) from generate_series(1, n) $$;
create temp table t (k text primary key, v int);
grant all on t to authenticated;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000501', 'authenticated', 'authenticated', '919855550501'),
  ('eeeeeeee-0000-0000-0000-000000000502', 'authenticated', 'authenticated', '919855550502');

-- Signed in: at most 20 a call, cut to size, 200 a day.
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000501');
select pg_temp.check(public.log_events(pg_temp.batch(25, 'call_started')) = 20, '20 per call');
reset role;
select pg_temp.check((select max(char_length(message)) = 2000 and max(char_length(stack)) = 8000 from public.client_events
                       where user_id = 'eeeeeeee-0000-0000-0000-000000000501'), 'message 2k, stack 8k');
select pg_temp.check((select bool_and(platform = 'web' and route = '/rooms' and app_version = 'web-1') from public.client_events
                       where user_id = 'eeeeeeee-0000-0000-0000-000000000501'), 'fields kept');
set local role authenticated;
select pg_temp.check(public.log_events('[{"kind": "crash", "name": "x"}, {"kind": "error", "name": "  "}, 5, {"kind": "error", "name": "TypeError", "platform": "ios"}]') = 1,
  'bad kinds, empty names and non-objects dropped');
reset role;
select pg_temp.check((select platform is null from public.client_events where user_id = 'eeeeeeee-0000-0000-0000-000000000501' and name = 'TypeError'),
  'unknown platform: null');
set local role authenticated;
insert into t select 'sum', sum(public.log_events(pg_temp.batch(20))) from generate_series(1, 12);
select pg_temp.check((select v from t where k = 'sum') = 200 - 20 - 4, 'daily cap 200 (incl. what was sent before)');
select pg_temp.check(public.log_events(pg_temp.batch(1)) = 0, 'over the cap: nothing kept');
select pg_temp.fails($$select public.log_events('{"kind": "error"}')$$, 'bad_request');
select pg_temp.check(public.log_events('[]') = 0, 'empty batch');
-- Someone else has their own allowance.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000502');
select pg_temp.check(public.log_events(pg_temp.batch(3)) = 3, 'second person: own cap');

-- Not signed in: only log_events_anon, under one shared cap.
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role": "anon"}', true);
select pg_temp.check(public.log_events_anon(pg_temp.batch(5, 'boot_failed', 'error')) = 5, 'anonymous: accepted');
select pg_temp.fails($$select public.log_events(pg_temp.batch(1))$$, 'permission denied');
reset role;
select pg_temp.check((select count(*) from public.client_events where name = 'boot_failed' and user_id is null) = 5, 'anonymous rows have no user');
update public.client_event_quota set n = 4998 where day = current_date and who = '00000000-0000-0000-0000-000000000000';
set local role anon;
select pg_temp.check(public.log_events_anon(pg_temp.batch(5, 'boot_failed', 'error')) = 2, 'shared anonymous cap 5,000 a day');
reset role;

-- Nobody reads the rows, nobody calls the inner function.
select pg_temp.check(not has_table_privilege('authenticated', 'public.client_events', 'select')
  or not exists (select 1 from pg_policies where tablename = 'client_events'), 'rows not readable');
select pg_temp.check(not has_function_privilege('authenticated', 'public.log_events_as(uuid, jsonb, integer)', 'execute')
  and not has_function_privilege('anon', 'public.log_events_as(uuid, jsonb, integer)', 'execute'), 'inner function hidden');
select pg_temp.check(not has_function_privilege('authenticated', 'public.log_events_anon(jsonb)', 'execute'), 'signed in: not the anonymous path');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000501');
select pg_temp.fails($$select count(*) from public.client_events$$, 'permission denied'); -- not even one's own
reset role;

select 'all log_events checks passed' as result;
rollback;
