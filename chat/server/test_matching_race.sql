-- Matching race (audit bug 1, migration 020). One transaction, rolled back.
-- Two sessions can't run in one transaction, so the race is replayed in order: a test trigger makes B's match with
-- A appear exactly where it landed in the race, after A's "already paired?" check and during A's own queue upsert.
-- The 011 function then pairs A a second time (with C); 020's does not. (In 020 the window can't open at all: A
-- locks its queue row before the check, and B holds that row until its match is in. The re-check after the upsert
-- is the second guard this replay exercises.)
begin;
set local statement_timeout = '10s';
create function pg_temp.act(u uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, true) $$;
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;

update public.queue set seen_at = '-infinity' where seen_at > now() - interval '1 minute';
insert into auth.users (id, aud, role, phone) values
  ('00000000-0000-0000-0000-0000000000a1', 'authenticated', 'authenticated', '919844440001'),
  ('00000000-0000-0000-0000-0000000000b1', 'authenticated', 'authenticated', '919844440002'),
  ('00000000-0000-0000-0000-0000000000c1', 'authenticated', 'authenticated', '919844440003');
insert into public.profiles (id, name, gender, birth_date) values
  ('00000000-0000-0000-0000-0000000000a1', 'Race A', 'male', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000b1', 'Race B', 'female', '1999-01-01'),
  ('00000000-0000-0000-0000-0000000000c1', 'Race C', 'female', '1999-01-01');

-- B "commits" its match with A when A's upsert touches A's row (once).
create temp table race (armed boolean);
create function pg_temp.b_pairs() returns trigger language plpgsql as $$
begin
  if new.user_id = '00000000-0000-0000-0000-0000000000a1' and (select armed from race) then
    update race set armed = false where armed;
    insert into public.matches (user_a, user_b) values ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1');
    new.seen_at := greatest(new.seen_at, now()); -- A's upsert still writes now() over B's -infinity, as it did
  end if;
  return new;
end $$;
create trigger race_b before update on public.queue for each row execute function pg_temp.b_pairs();

-- The 011 function, unchanged, to show the failure.
create function pg_temp.find_match2_011(p_target text, p_lang text) returns uuid language plpgsql set search_path = '' as $$
declare
  me uuid := auth.uid(); my_gender text; banned timestamptz;
  my_lang text := case when lower(p_lang) in ('en', 'hi', 'bn', 'pt-br', 'vi') then lower(p_lang) end;
  my_since timestamptz; m public.matches; mid uuid; last_partner uuid; partner uuid;
begin
  select p.gender, p.banned_until into my_gender, banned from public.profiles p where p.id = me;
  perform pg_advisory_xact_lock(hashtext('match:' || me::text));
  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds' order by x.created_at desc limit 1;
  if mid is not null then return mid; end if;
  for m in select * from public.matches x where me in (x.user_a, x.user_b) and x.ended_at is null loop
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none' where id = m.id;
  end loop;
  select case when x.user_a = me then x.user_b else x.user_a end into last_partner
    from public.matches x where me in (x.user_a, x.user_b) order by x.created_at desc limit 1;
  insert into public.queue as q (user_id, gender, target, lang) values (me, my_gender, p_target, my_lang)
  on conflict (user_id) do update set gender = excluded.gender, target = excluded.target, lang = excluded.lang,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end, seen_at = now()
  returning q.joined_at into my_since;
  select q.user_id into partner from public.queue q join public.profiles p on p.id = q.user_id
   where q.user_id <> me and q.seen_at > now() - interval '20 seconds'
     and (p_target = 'any' or q.gender = p_target) and (q.target = 'any' or q.target = my_gender)
     and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, q.user_id)
     and q.user_id is distinct from last_partner
   order by q.joined_at limit 1 for update of q skip locked;
  if partner is null then return null; end if;
  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;

create function pg_temp.setup() returns void language sql as $$
  update public.matches set ended_at = now() where ended_at is null
     and '00000000-0000-0000-0000-0000000000a1' in (user_a, user_b);
  update public.matches set created_at = now() - interval '1 hour'
   where '00000000-0000-0000-0000-0000000000a1' in (user_a, user_b);
  -- A has searched once already (its row exists); C is waiting.
  insert into public.queue (user_id, gender, target, lang, joined_at, seen_at) values
    ('00000000-0000-0000-0000-0000000000a1', 'male', 'any', 'hi', now() - interval '3 seconds', now() - interval '2 seconds'),
    ('00000000-0000-0000-0000-0000000000c1', 'female', 'any', 'hi', now() - interval '5 seconds', now() - interval '1 second')
  on conflict (user_id) do update set seen_at = excluded.seen_at, joined_at = excluded.joined_at;
  update race set armed = true where not armed;
$$;
create function pg_temp.open_matches() returns int language sql as $$
  select count(*)::int from public.matches where ended_at is null and '00000000-0000-0000-0000-0000000000a1' in (user_a, user_b) $$;
insert into race values (false);

-- 1. The old function: A ends up in two matches (B's, and a new one with C).
select pg_temp.setup();
select pg_temp.act('00000000-0000-0000-0000-0000000000a1');
select pg_temp.find_match2_011('any', 'hi');
select pg_temp.check(pg_temp.open_matches() = 2, 'replay reproduces the old failure (A in two matches)');

-- 2. 020's function: A gets B's match, C stays waiting, A is out of the line.
select pg_temp.setup();
select pg_temp.act('00000000-0000-0000-0000-0000000000a1');
create temp table got as select public.find_match2('any', 'hi') as mid;
select pg_temp.check(pg_temp.open_matches() = 1, 'A in one match only');
select pg_temp.check((select user_b from public.matches where id = (select mid from got)) = '00000000-0000-0000-0000-0000000000b1',
  'A gets the match B made');
select pg_temp.check((select seen_at from public.queue where user_id = '00000000-0000-0000-0000-0000000000a1') = '-infinity',
  'A left the line');
select pg_temp.check((select seen_at from public.queue where user_id = '00000000-0000-0000-0000-0000000000c1') > now() - interval '20 seconds',
  'C still waiting');
select pg_temp.check(not exists (select 1 from public.matches where ended_at is null
  and '00000000-0000-0000-0000-0000000000c1' in (user_a, user_b)), 'C not paired');

select 'all matching race checks passed' as result;
rollback;
