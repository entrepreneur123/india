-- User rooms (migration 008). One transaction, rolled back: nothing is left behind.
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
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;
create temp table t (k text primary key, v text);
grant all on t to authenticated;

insert into auth.users (id, aud, role, phone) values
  ('00000000-0000-0000-0000-0000000000d1', 'authenticated', 'authenticated', '919822222221'),
  ('00000000-0000-0000-0000-0000000000d2', 'authenticated', 'authenticated', '919822222222'),
  ('00000000-0000-0000-0000-0000000000d3', 'authenticated', 'authenticated', '919822222223'),
  ('00000000-0000-0000-0000-0000000000d4', 'authenticated', 'authenticated', '919822222224');

set local role authenticated;
select pg_temp.act('00000000-0000-0000-0000-0000000000d1');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000d1', 'Owner', 'female', '1998-01-01');
select pg_temp.act('00000000-0000-0000-0000-0000000000d2');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000d2', 'Woman25', 'female', (current_date - interval '25 years')::date);
select pg_temp.act('00000000-0000-0000-0000-0000000000d3');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000d3', 'Man25', 'male', (current_date - interval '25 years')::date);
select pg_temp.act('00000000-0000-0000-0000-0000000000d4');
insert into public.profiles (id, name, gender, birth_date) values ('00000000-0000-0000-0000-0000000000d4', 'Woman40', 'female', (current_date - interval '40 years')::date);

-- 1. Bad input is refused
select pg_temp.act('00000000-0000-0000-0000-0000000000d1');
select pg_temp.fails($q$select * from public.create_room('x', 'chat', 5, 'any', 18, 100, false)$q$, 'room_title');
select pg_temp.fails($q$select * from public.create_room('Hello', 'chat', 99, 'any', 18, 100, false)$q$, 'room_size');
select pg_temp.fails($q$select * from public.create_room('Hello', 'chat', 5, 'any', 40, 20, false)$q$, 'bad_request');
select pg_temp.fails($q$select * from public.create_room('Hello', 'dating', 5, 'any', 18, 100, false)$q$, 'bad_request');

-- 2. Public room: women 20-30, max 2 people. Owner is inside and sees the code.
insert into t select 'pub', room_id from public.create_room('  Women 20s chat ', 'friends', 2, 'female', 20, 30, false);
select pg_temp.check((select title from public.rooms where id = (select v from t where k = 'pub')) = 'Women 20s chat', 'title trimmed');
select pg_temp.check((select code ~ '^[A-HJ-NP-Z2-9]{6}$' and mine and online = 1 and refusal is null
                        from public.room_list2() where id = (select v from t where k = 'pub')), 'owner sees code, is in');
select pg_temp.check(not exists (select 1 from public.room_codes), 'codes unreadable from the table');

-- 3. Conditions: a man and a 40-year-old woman can't enter; a 25-year-old woman can; then it is full
select pg_temp.act('00000000-0000-0000-0000-0000000000d3');
select pg_temp.check((select refusal from public.room_list2() where id = (select v from t where k = 'pub')) = 'room_gender', 'man sees why');
select pg_temp.check((select code from public.room_list2() where id = (select v from t where k = 'pub')) is null, 'code hidden from others');
select pg_temp.fails(format('select public.join_room(%L)', (select v from t where k = 'pub')), 'room_gender');
select pg_temp.act('00000000-0000-0000-0000-0000000000d4');
select pg_temp.fails(format('select public.join_room(%L)', (select v from t where k = 'pub')), 'room_age');
select pg_temp.act('00000000-0000-0000-0000-0000000000d2');
select public.join_room((select v from t where k = 'pub'));
insert into public.room_messages (room_id, body) values ((select v from t where k = 'pub'), 'hi from a member');
select public.join_room((select v from t where k = 'pub')); -- heartbeat while inside is fine even when full
select pg_temp.check((select online from public.room_list2() where id = (select v from t where k = 'pub')) = 2, 'two online');

-- 4. Private room: hidden from the list, its messages unreadable, enterable only with the code
select pg_temp.act('00000000-0000-0000-0000-0000000000d1');
insert into t select 'priv', room_id from public.create_room('Secret club', 'chat', 10, 'any', 18, 100, true);
insert into t select 'code', code from public.room_list2() where id = (select v from t where k = 'priv');
select pg_temp.check((select closed_at is not null from public.rooms where id = (select v from t where k = 'pub')), 'one open room per owner');
insert into public.room_messages (room_id, body) values ((select v from t where k = 'priv'), 'members only');
select pg_temp.act('00000000-0000-0000-0000-0000000000d3');
select pg_temp.check(not exists (select 1 from public.room_list2() where id = (select v from t where k = 'priv')), 'private not listed');
select pg_temp.check(not exists (select 1 from public.room_messages where room_id = (select v from t where k = 'priv')), 'private messages hidden');
select pg_temp.fails(format('select public.join_room(%L)', (select v from t where k = 'priv')), 'not_found');
select pg_temp.fails($q$select public.join_room_code('ZZZZZZ')$q$, 'not_found');
select pg_temp.check(public.join_room_code(lower((select v from t where k = 'code'))) = (select v from t where k = 'priv'), 'joined by code');
select pg_temp.check(exists (select 1 from public.room_messages where room_id = (select v from t where k = 'priv')), 'member reads');
select pg_temp.check(exists (select 1 from public.room_list2() where id = (select v from t where k = 'priv')), 'member lists it');

-- 5. Closed rooms take no messages; only the owner can close
select pg_temp.fails(format('select public.close_room(%L)', (select v from t where k = 'priv')), 'not_allowed');
select pg_temp.act('00000000-0000-0000-0000-0000000000d2');
select pg_temp.fails(format($f$insert into public.room_messages (room_id, body) values (%L, 'late')$f$, (select v from t where k = 'pub')), 'room_closed');
select pg_temp.act('00000000-0000-0000-0000-0000000000d1');
select public.close_room((select v from t where k = 'priv'));
select pg_temp.act('00000000-0000-0000-0000-0000000000d3');
select pg_temp.fails(format('select public.join_room(%L)', (select v from t where k = 'priv')), 'room_closed');

-- 6. Official rooms work exactly as before (old app call shape included)
select public.join_room('lounge');
select pg_temp.check(exists (select 1 from public.room_list() where id = 'lounge'), 'old room_list');
select pg_temp.check((select official from public.room_list2() where id = 'lounge'), 'official flag');

select 'all user-room checks passed' as result;
rollback;
