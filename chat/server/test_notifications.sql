-- Notifications, unread badges and the push decision (migration 023). One transaction, rolled back.
-- pg_net only queues requests (net.http_request_queue) and sends them after commit, so nothing leaves here.
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
-- A time zone where it is now the given hour (for the quiet-hours checks).
create function pg_temp.zone_at(h int) returns text language sql as $$
  select z.name from pg_timezone_names z where z.name like 'Etc/GMT%' and extract(hour from now() at time zone z.name) = h limit 1 $$;
create function pg_temp.queued() returns bigint language sql as $$
  select count(*) from net.http_request_queue where url like '%/functions/v1/push' $$;
create temp table t (k text primary key, v text) on commit drop;
grant all on t to authenticated;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000701', 'authenticated', 'authenticated', '919855570701'),
  ('eeeeeeee-0000-0000-0000-000000000702', 'authenticated', 'authenticated', '919855570702'),
  ('eeeeeeee-0000-0000-0000-000000000703', 'authenticated', 'authenticated', '919855570703');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000701', 'NAsha', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000702', 'NRavi', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000703', 'NStranger', 'male', '2000-01-01');
-- Asha and Ravi met in random chat; Asha and the stranger too.
insert into public.matches (id, user_a, user_b, ended_at) values
  ('eeeeeeee-0000-0000-0000-00000000c701', 'eeeeeeee-0000-0000-0000-000000000701', 'eeeeeeee-0000-0000-0000-000000000702', now()),
  ('eeeeeeee-0000-0000-0000-00000000c703', 'eeeeeeee-0000-0000-0000-000000000701', 'eeeeeeee-0000-0000-0000-000000000703', null);

set local role authenticated;

-- ---- friend request and acceptance ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000702');
select public.request_friend('eeeeeeee-0000-0000-0000-000000000701');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000701');
select pg_temp.check((select count(*) = 1 and bool_and(kind = 'friend_request' and pending and actor->>'name' = 'NRavi')
                        from public.inbox()), 'request in the inbox, still pending');
select pg_temp.check((public.my_badges()->>'inbox')::int = 1 and (public.my_badges()->>'friends')::int = 1, 'badges: 1 unread, 1 request');
select pg_temp.check((select count(*) from public.notifications) = 1, 'RLS: I see my own rows');
select public.answer_friend('eeeeeeee-0000-0000-0000-000000000702', true);
select pg_temp.check((select read_at is not null and not pending from public.inbox() where kind = 'friend_request'), 'accepting reads the request');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000702');
select pg_temp.check((select count(*) from public.inbox() where kind = 'friend_accept' and actor->>'name' = 'NAsha') = 1, 'acceptance told');
select pg_temp.check((select count(*) from public.notifications where kind = 'friend_request') = 0, 'RLS: not someone else''s rows');

-- ---- friends' messages fold into one row per chat; strangers' never notify ----
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'one');
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'two');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000703');
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c703', 'hey');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000701');
select pg_temp.check((select n from public.inbox() where kind = 'message') = 2, 'two messages, one row (n = 2)');
select pg_temp.check((select count(*) from public.inbox() where kind = 'message') = 1, 'no row for the stranger');
select pg_temp.check((public.my_badges()->'chats'->>'eeeeeeee-0000-0000-0000-00000000c701')::int = 2, 'friend chat: 2 unread');
select pg_temp.check((public.my_badges()->'chats'->>'eeeeeeee-0000-0000-0000-00000000c703')::int = 1, 'random chat: 1 unread');
select public.mark_read('eeeeeeee-0000-0000-0000-00000000c701', (select max(id) from public.messages where match_id = 'eeeeeeee-0000-0000-0000-00000000c701'));
select pg_temp.check(public.my_badges()->'chats'->>'eeeeeeee-0000-0000-0000-00000000c701' is null, 'read: no badge');
select pg_temp.check((select read_at is not null from public.inbox() where kind = 'message'), 'reading the chat reads its inbox row');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000702');
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'three');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000701');
select pg_temp.check((select count(*) from public.inbox() where kind = 'message') = 2 and (select n from public.inbox() where kind = 'message' and read_at is null) = 1,
  'a new message after reading: a new row');
select public.inbox_read(null);
select pg_temp.check((public.my_badges()->>'inbox')::int = 0, 'mark all read');
reset role;
select pg_temp.check(exists (select 1 from public.user_seen where user_id = 'eeeeeeee-0000-0000-0000-000000000701'), 'my_badges stamps me as here');
set local role authenticated;

-- ---- devices ----
select pg_temp.fails($$select public.register_device('short', 'android')$$, 'bad_request');
select pg_temp.fails($$select public.register_device('tok-asha-0000000000000001', 'symbian')$$, 'bad_request');
select public.register_device('tok-asha-0000000000000001', 'android', 'hi-IN', 'Not/AZone');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000702');
-- the same phone, now signed in as Ravi: the token moves
select public.register_device('tok-asha-0000000000000001', 'android', 'en', 'Asia/Kolkata');
select public.unregister_device('tok-asha-0000000000000001');
reset role;
select pg_temp.check((select user_id = 'eeeeeeee-0000-0000-0000-000000000702' and not active and tz = 'Asia/Kolkata'
                        from public.device_tokens where token = 'tok-asha-0000000000000001'), 'token moved, then switched off');
select pg_temp.check(not has_table_privilege('authenticated', 'public.device_tokens', 'select')
  and not has_table_privilege('authenticated', 'public.user_seen', 'select'), 'tokens, presence: functions only');

-- ---- the push decision ----
-- Ravi has no active phone: nothing is queued.
update public.user_seen set seen_at = now() - interval '5 minutes' where user_id in ('eeeeeeee-0000-0000-0000-000000000701', 'eeeeeeee-0000-0000-0000-000000000702');
insert into t values ('q0', pg_temp.queued()::text);
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000701');
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'no phone');
reset role;
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0'), 'no token: no call');
select pg_temp.check((select pushed_at is null from public.notifications where user_id = 'eeeeeeee-0000-0000-0000-000000000702' and kind = 'message' and read_at is null), 'not pushed');

-- A phone in a zone where it is midday, Ravi offline: the next message is pushed once, the one after it within 30 s is not.
insert into public.device_tokens (token, user_id, platform, tz) values ('tok-ravi-midday-000000001', 'eeeeeeee-0000-0000-0000-000000000702', 'android', pg_temp.zone_at(12));
set local role authenticated;
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'pushed');
reset role;
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0') + 1, 'offline: one call to push');
select pg_temp.check((select pushed_at is not null from public.notifications where user_id = 'eeeeeeee-0000-0000-0000-000000000702' and kind = 'message' and read_at is null), 'marked pushed');
set local role authenticated;
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'folded');
reset role;
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0') + 1, 'same chat within 30 s: no second push');
update public.notifications set pushed_at = now() - interval '31 seconds' where user_id = 'eeeeeeee-0000-0000-0000-000000000702' and kind = 'message' and read_at is null;
set local role authenticated;
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'later');
reset role;
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0') + 2, 'after 30 s: pushed again');
-- The payload: ids, kind, the sender's name and the tokens; never the text.
select pg_temp.check((select p ? 'kind' and p->>'actor_name' = 'NAsha' and (p->>'n')::int = 4 and p::text not like '%later%'
                             and jsonb_array_length(p->'tokens') = 1
                        from (select public.push_payload(id) p from public.notifications
                               where user_id = 'eeeeeeee-0000-0000-0000-000000000702' and kind = 'message' and read_at is null) x),
  'payload without message text');

-- Online (app open in the last 60 s): no push.
update public.notifications set pushed_at = null where user_id = 'eeeeeeee-0000-0000-0000-000000000702';
insert into public.user_seen (user_id) values ('eeeeeeee-0000-0000-0000-000000000702') on conflict (user_id) do update set seen_at = now();
set local role authenticated;
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'online');
reset role;
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0') + 2, 'online: no push');

-- Quiet hours (02:00 there): a message waits, a friend request still goes.
update public.user_seen set seen_at = now() - interval '5 minutes' where user_id in ('eeeeeeee-0000-0000-0000-000000000701', 'eeeeeeee-0000-0000-0000-000000000702');
update public.device_tokens set tz = pg_temp.zone_at(2) where token = 'tok-ravi-midday-000000001';
set local role authenticated;
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-00000000c701', 'night');
reset role;
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0') + 2, 'quiet hours: message not pushed');
insert into public.notifications (user_id, kind, actor) values ('eeeeeeee-0000-0000-0000-000000000702', 'friend_request', 'eeeeeeee-0000-0000-0000-000000000703');
select pg_temp.check(pg_temp.queued() = (select v::bigint from t where k = 'q0') + 3, 'quiet hours: friend request pushed');

-- ---- blocking hides the sender's rows ----
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000702');
select public.block_user('eeeeeeee-0000-0000-0000-000000000703');
select pg_temp.check((select count(*) from public.inbox() where actor->>'name' = 'NStranger') = 0, 'blocked: gone from the inbox');

-- ---- privileges ----
reset role;
select pg_temp.check(not has_function_privilege('authenticated', 'public.push_payload(bigint)', 'execute')
  and not has_function_privilege('authenticated', 'public.edge_key_ok(text)', 'execute')
  and not has_function_privilege('authenticated', 'public.user_online(uuid)', 'execute')
  and has_function_privilege('service_role', 'public.push_payload(bigint)', 'execute'), 'push internals: service role only');
select pg_temp.check(not has_function_privilege('anon', 'public.inbox(bigint, integer)', 'execute')
  and not has_function_privilege('anon', 'public.my_badges()', 'execute')
  and not has_function_privilege('anon', 'public.register_device(text, text, text, text)', 'execute'), 'anon: nothing');
select pg_temp.check(not has_table_privilege('anon', 'public.notifications', 'select')
  and not has_table_privilege('authenticated', 'public.notifications', 'insert'), 'notifications: read-only, signed in');

select 'all notification checks passed' as result;
rollback;
