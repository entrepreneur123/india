-- Moderation console (migration 021). One transaction, rolled back.
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
create temp table t (k text primary key, v text);
grant all on t to authenticated;
create function pg_temp.v(key text) returns text language sql as $$ select v from t where k = key $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000601', 'authenticated', 'authenticated', '919855540601'),
  ('eeeeeeee-0000-0000-0000-000000000602', 'authenticated', 'authenticated', '919855540602'),
  ('eeeeeeee-0000-0000-0000-000000000603', 'authenticated', 'authenticated', '919855540603');
insert into public.profiles (id, name, gender, birth_date, photo_path) values
  ('eeeeeeee-0000-0000-0000-000000000601', 'AAdmin', 'female', '1990-01-01', null),
  ('eeeeeeee-0000-0000-0000-000000000602', 'AReporter', 'male', '1999-01-01', null),
  ('eeeeeeee-0000-0000-0000-000000000603', 'AReported', 'male', '1998-01-01', 'eeeeeeee-0000-0000-0000-000000000603/avatar/a.jpg');
insert into public.admins (user_id) values ('eeeeeeee-0000-0000-0000-000000000601');
insert into public.reports (reporter, reported, reason, evidence) values
  ('eeeeeeee-0000-0000-0000-000000000602', 'eeeeeeee-0000-0000-0000-000000000603', 'harassment',
   '{"name": "AReported", "chat": [{"by": "reported", "body": "bad words", "media": "eeeeeeee-0000-0000-0000-000000000603/chat/x.jpg"}], "voice": [], "rooms": []}');
insert into t select 'r1', max(id)::text from public.reports where reported = 'eeeeeeee-0000-0000-0000-000000000603';
insert into public.reports (reporter, reported, reason, evidence) values
  ('eeeeeeee-0000-0000-0000-000000000602', 'eeeeeeee-0000-0000-0000-000000000603', 'auto_nudity', '{"source": "video_model"}');
insert into t select 'r2', max(id)::text from public.reports where reported = 'eeeeeeee-0000-0000-0000-000000000603';

set local role authenticated;

-- Not an admin: every console function says not_allowed; is_admin says false.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000602');
select pg_temp.check(not public.is_admin(), 'reporter is not an admin');
select pg_temp.fails($$select public.admin_reports()$$, 'not_allowed');
select pg_temp.fails($$select public.admin_report(1)$$, 'not_allowed');
select pg_temp.fails($$select public.admin_resolve(pg_temp.v('r1')::bigint, 'dismiss')$$, 'not_allowed');
select pg_temp.fails($$select public.admin_unban('eeeeeeee-0000-0000-0000-000000000603')$$, 'not_allowed');
select pg_temp.fails($$select public.admin_dashboard()$$, 'not_allowed');
select pg_temp.fails($$select * from public.admins$$, 'permission denied');
select pg_temp.fails($$select * from public.admin_actions$$, 'permission denied');
select pg_temp.check(not public.can_read_media('eeeeeeee-0000-0000-0000-000000000603/chat/x.jpg'), 'non-admin: evidence photo hidden');

-- Admin: the queue (filters), one report, the dashboard, photos.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000601');
select pg_temp.check(public.is_admin(), 'admin');
select pg_temp.check((select bool_and(not (x->>'reviewed')::boolean) from jsonb_array_elements(public.admin_reports()) x), 'default: unreviewed only');
select pg_temp.check((public.admin_reports()->0->>'id')::bigint = pg_temp.v('r2')::bigint, 'newest first');
select pg_temp.check((select bool_and(x->>'reason' = 'auto_nudity') from jsonb_array_elements(public.admin_reports('auto_nudity')) x)
  and exists (select 1 from jsonb_array_elements(public.admin_reports('auto_nudity')) x where (x->>'id')::bigint = pg_temp.v('r2')::bigint), 'auto_nudity filter');
select pg_temp.check((select bool_and(x->>'reason' <> 'auto_nudity') from jsonb_array_elements(public.admin_reports('manual')) x), 'manual filter');
select pg_temp.check(jsonb_array_length(public.admin_reports('all', pg_temp.v('r2')::bigint, 1)) = 1
  and (public.admin_reports('all', pg_temp.v('r2')::bigint, 1)->0->>'id')::bigint < pg_temp.v('r2')::bigint, 'paging by id');
insert into t select 'detail', public.admin_report(pg_temp.v('r1')::bigint)::text;
select pg_temp.check(pg_temp.v('detail')::jsonb->'evidence'->'chat'->0->>'body' = 'bad words', 'detail: chat evidence');
select pg_temp.check(pg_temp.v('detail')::jsonb->'reported'->>'name' = 'AReported' and (pg_temp.v('detail')::jsonb->>'reports_total')::int = 2, 'detail: reported and counts');
select pg_temp.check(jsonb_array_length(pg_temp.v('detail')::jsonb->'history') = 1, 'detail: other reports');
select pg_temp.fails($$select public.admin_report(-1)$$, 'not_found');
select pg_temp.check(public.can_read_media('eeeeeeee-0000-0000-0000-000000000603/chat/x.jpg')
  and public.can_read_media('eeeeeeee-0000-0000-0000-000000000603/avatar/a.jpg'), 'admin reads evidence photos');
select pg_temp.check(public.admin_dashboard() ? 'reports_24h' and (public.admin_dashboard()->>'unreviewed')::int >= 2
  and public.admin_dashboard() ? 'top_errors' and public.admin_dashboard() ? 'active_rooms', 'dashboard');

-- Actions: reviewed, bad requests, ban for 3 days, unban, permanent ban; each one written down.
select public.admin_resolve(pg_temp.v('r2')::bigint, 'reviewed', null, 'model false positive');
select pg_temp.fails($$select public.admin_resolve(pg_temp.v('r1')::bigint, 'delete')$$, 'bad_request');
select pg_temp.fails($$select public.admin_resolve(pg_temp.v('r1')::bigint, 'ban', 0)$$, 'bad_request');
select pg_temp.fails($$select public.admin_resolve(pg_temp.v('r1')::bigint, 'dismiss', null, repeat('x', 501))$$, 'bad_request');
select public.admin_resolve(pg_temp.v('r1')::bigint, 'ban', 3, 'insults');
reset role;
select pg_temp.check((select reviewed and outcome = 'reviewed' and reviewed_by = 'eeeeeeee-0000-0000-0000-000000000601'
                        from public.reports where id = pg_temp.v('r2')::bigint), 'marked reviewed');
select pg_temp.check((select outcome = 'banned' from public.reports where id = pg_temp.v('r1')::bigint), 'outcome banned');
select pg_temp.check((select banned_until between now() + interval '71 hours' and now() + interval '73 hours' from public.profiles
                        where id = 'eeeeeeee-0000-0000-0000-000000000603'), 'banned 3 days');
select pg_temp.check(exists (select 1 from public.banned_phones b where b.phone_hash = public.phone_hash('919855540603')
                        and b.banned_until > now()), 'phone remembered');
select pg_temp.check((select count(*) from public.admin_actions where admin = 'eeeeeeee-0000-0000-0000-000000000601') = 2, 'two actions audited');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000603');
select pg_temp.fails($$select public.find_match2('any', 'hi')$$, 'banned');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000601');
select public.admin_unban('eeeeeeee-0000-0000-0000-000000000603', 'appeal accepted');
select pg_temp.fails($$select public.admin_unban('eeeeeeee-0000-0000-0000-0000000006ff')$$, 'not_found');
reset role;
select pg_temp.check((select banned_until is null from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000000603'), 'unbanned');
select pg_temp.check(not exists (select 1 from public.banned_phones b where b.phone_hash = public.phone_hash('919855540603')
                        and b.banned_until > now()), 'phone no longer banned');
insert into public.reports (reporter, reported, reason) values ('eeeeeeee-0000-0000-0000-000000000602', 'eeeeeeee-0000-0000-0000-000000000603', 'minor');
insert into t select 'r3', max(id)::text from public.reports where reported = 'eeeeeeee-0000-0000-0000-000000000603';
set local role authenticated;
select public.admin_resolve(pg_temp.v('r3')::bigint, 'ban');
reset role;
select pg_temp.check((select banned_until = 'infinity' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000000603'), 'permanent ban');
select pg_temp.check((select count(*) from public.admin_actions where admin = 'eeeeeeee-0000-0000-0000-000000000601') = 4, 'every action audited');
set local role authenticated;
select pg_temp.check(jsonb_array_length(public.admin_report(pg_temp.v('r3')::bigint)->'actions') >= 3, 'detail shows past actions');

-- The internals and the anonymous role.
reset role;
select pg_temp.check(not has_function_privilege('authenticated', 'public.admin_me()', 'execute'), 'admin_me hidden');
select pg_temp.check(not has_function_privilege('anon', 'public.admin_reports(text, bigint, integer)', 'execute')
  and not has_function_privilege('anon', 'public.admin_resolve(bigint, text, integer, text)', 'execute'), 'anon: no console');
select pg_temp.check(exists (select 1 from public.admins a join auth.users u on u.id = a.user_id where u.phone = '919000000001'),
  'test account is an admin');

select 'all admin checks passed' as result;
rollback;
