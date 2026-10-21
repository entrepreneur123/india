-- Room search and language, people online, room invites, interests, gallery photos and the interest preference
-- in matching (migration 024). One transaction, rolled back.
begin;
set local statement_timeout = '20s';
grant select, update on public.voice_list_cache to authenticated;
create policy test_only on public.voice_list_cache for all to authenticated using (true);
create function pg_temp.stale() returns void language sql as $$ update public.voice_list_cache set at = '-infinity' where id = 1 $$;
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
create temp table v (k text primary key, id text) on commit drop;
grant all on v to authenticated;
create function pg_temp.r(k text) returns text language sql as $$ select id from v where v.k = r.k $$;

-- Nobody else may be waiting during the matching part.
update public.queue set seen_at = '-infinity' where seen_at > now() - interval '1 minute';

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000801', 'authenticated', 'authenticated', '919855570801'),
  ('eeeeeeee-0000-0000-0000-000000000802', 'authenticated', 'authenticated', '919855570802'),
  ('eeeeeeee-0000-0000-0000-000000000803', 'authenticated', 'authenticated', '919855570803'),
  ('eeeeeeee-0000-0000-0000-000000000804', 'authenticated', 'authenticated', '919855570804'),
  ('eeeeeeee-0000-0000-0000-000000000805', 'authenticated', 'authenticated', '919855570805');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000801', 'DHost', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000802', 'DFriend', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000803', 'DStranger', 'male', '2000-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000804', 'DEarly', 'male', '2000-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000805', 'DShared', 'male', '2000-01-01');
insert into public.friendships (requester, addressee, status) values
  ('eeeeeeee-0000-0000-0000-000000000801', 'eeeeeeee-0000-0000-0000-000000000802', 'accepted');
-- DHost last searched in Hindi (Android 0.7.0 sends no room language: this one is used).
insert into public.queue (user_id, gender, target, lang, seen_at) values ('eeeeeeee-0000-0000-0000-000000000801', 'female', 'any', 'hi', '-infinity');
insert into storage.objects (bucket_id, name) values
  ('media', 'eeeeeeee-0000-0000-0000-000000000801/avatar/aaaaaaaa-0000-0000-0000-000000000001.jpg'),
  ('media', 'eeeeeeee-0000-0000-0000-000000000801/avatar/aaaaaaaa-0000-0000-0000-000000000002.jpg'),
  ('media', 'eeeeeeee-0000-0000-0000-000000000802/avatar/aaaaaaaa-0000-0000-0000-000000000003.jpg');

set local role authenticated;

-- ---- room language: the creator's by default, or chosen ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000801');
insert into v select 'old', public.create_voice_room2('Cricket adda', 'talk', 50, 'any', 18, 100, false, null)->>'id';
reset role;
select pg_temp.check((select lang from public.voice_rooms where id = pg_temp.r('old')) = 'hi', 'Android signature: creator''s language');
set local role authenticated;
insert into v select 'room', public.create_voice_room3('Bollywood songs', 'music', 50, 'any', 18, 100, false, null, 'pt-BR')->>'id';
reset role;
select pg_temp.check((select lang from public.voice_rooms where id = pg_temp.r('room')) = 'pt-br', 'chosen language');
select pg_temp.check((select ended_at is not null from public.voice_rooms where id = pg_temp.r('old')), 'one room per host still');
set local role authenticated;
select public.voice_settings2(pg_temp.r('room'), 'Bollywood songs', null, 'hi');
select pg_temp.check(public.voice_state(pg_temp.r('room'))->>'lang' = 'hi', 'voice_state carries the language (027)');

-- ---- search ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000803');
select pg_temp.stale();
select pg_temp.check((select lang = 'hi' and members >= 1 from public.voice_list3() where id = pg_temp.r('room')), 'listed with its language');
select pg_temp.check(exists (select 1 from public.voice_list3('bolly', null, null) where id = pg_temp.r('room')), 'title search, any case');
select pg_temp.check(not exists (select 1 from public.voice_list3('cricket', null, null) where id = pg_temp.r('room')), 'other title: not found');
select pg_temp.check(exists (select 1 from public.voice_list3(null, 'HI', 'music') where id = pg_temp.r('room')), 'language + category');
select pg_temp.check(not exists (select 1 from public.voice_list3(null, 'bn', null) where id = pg_temp.r('room')), 'other language: hidden');
select pg_temp.check(not exists (select 1 from public.voice_list3(null, null, 'games') where id = pg_temp.r('room')), 'other category: hidden');
select pg_temp.fails($$select * from public.voice_list3(repeat('x', 41), null, null)$$, 'bad_request');
select pg_temp.check((select count(*) from public.voice_list2() where id = pg_temp.r('room')) = 1, 'voice_list2 unchanged');

-- ---- people online ----
reset role;
update public.app_stats set at = '-infinity' where id = 1;
set local role authenticated;
select pg_temp.check(public.online_count() >= 1, 'host in the room counts');

-- ---- invites ----
select pg_temp.fails(format('select public.invite_to_room(%L, %L)', pg_temp.r('room'), 'eeeeeeee-0000-0000-0000-000000000802'), 'not_allowed'); -- I'm not in it
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000801');
select pg_temp.fails(format('select public.invite_to_room(%L, %L)', pg_temp.r('room'), 'eeeeeeee-0000-0000-0000-000000000803'), 'not_allowed'); -- not a friend
select public.invite_to_room(pg_temp.r('room'), 'eeeeeeee-0000-0000-0000-000000000802');
select public.invite_to_room(pg_temp.r('room'), 'eeeeeeee-0000-0000-0000-000000000802'); -- again within 10 min: nothing new
select pg_temp.fails(format('select public.invite_to_room(%L, %L)', pg_temp.r('old'), 'eeeeeeee-0000-0000-0000-000000000802'), 'room_closed');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000802');
select pg_temp.check((select count(*) = 1 and bool_and(room_title = 'Bollywood songs' and room_open and actor->>'name' = 'DHost')
                        from public.inbox() where kind = 'room_invite'), 'one invite in the inbox');
-- Make the room private: the invite still lets the friend in (like the code), a stranger can't use it.
reset role;
update public.voice_rooms set is_private = true where id = pg_temp.r('room');
insert into v select 'inv', id::text from public.notifications where kind = 'room_invite' and user_id = 'eeeeeeee-0000-0000-0000-000000000802';
set local role authenticated;
select pg_temp.fails(format('select public.join_voice(%L)', pg_temp.r('room')), 'not_found');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000803');
select pg_temp.fails(format('select public.join_invite(%s)', pg_temp.r('inv')), 'not_found');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000802');
select pg_temp.check(public.join_invite(pg_temp.r('inv')::bigint) = pg_temp.r('room'), 'invite opens the room');
select pg_temp.check(public.join_voice(pg_temp.r('room')) is not null, 'and joining works');
select pg_temp.check((select read_at is not null from public.inbox() where kind = 'room_invite'), 'invite read');

-- ---- interests ----
select pg_temp.fails($$select public.set_interests(array['music', 'movies'])$$, 'bad_request');
select pg_temp.fails($$select public.set_interests(array['music', 'movies', 'knitting'])$$, 'bad_request');
select pg_temp.fails($$select public.set_interests(array['music', 'movies', 'cricket', 'food', 'travel', 'books'])$$, 'bad_request');
select public.set_interests(array['music', 'movies', 'cricket', 'music']); -- duplicates folded: 3
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000805');
select public.set_interests(array['cricket', 'food', 'travel']);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000801');
select public.set_interests(array['cricket', 'books', 'tech']);

-- ---- photos ----
select pg_temp.fails($$select public.set_photos(array['eeeeeeee-0000-0000-0000-000000000802/avatar/aaaaaaaa-0000-0000-0000-000000000003.jpg'])$$, 'bad_request'); -- someone else's
select pg_temp.fails($$select public.set_photos(array['eeeeeeee-0000-0000-0000-000000000801/avatar/missing.jpg'])$$, 'bad_request');
select pg_temp.fails($$select public.set_photos(array['eeeeeeee-0000-0000-0000-000000000801/avatar/aaaaaaaa-0000-0000-0000-000000000001.jpg',
  'eeeeeeee-0000-0000-0000-000000000801/avatar/aaaaaaaa-0000-0000-0000-000000000001.jpg'])$$, 'bad_request');
select public.set_photos(array['eeeeeeee-0000-0000-0000-000000000801/avatar/aaaaaaaa-0000-0000-0000-000000000002.jpg',
  'eeeeeeee-0000-0000-0000-000000000801/avatar/aaaaaaaa-0000-0000-0000-000000000001.jpg']);
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000802');
select pg_temp.check((select interests @> array['books', 'cricket', 'tech'] and cardinality(interests) = 3 from public.profile_extras(array['eeeeeeee-0000-0000-0000-000000000801'::uuid])),
  'friend sees interests');
select pg_temp.check((select photos[1] like '%000000000002.jpg' and cardinality(photos) = 2
                        from public.profile_extras(array['eeeeeeee-0000-0000-0000-000000000801'::uuid])), 'friend sees the gallery in order');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000804');
select pg_temp.check(not exists (select 1 from public.profile_extras(array['eeeeeeee-0000-0000-0000-000000000801'::uuid])), 'stranger sees nothing');
reset role;
select pg_temp.check(not exists (select 1 from public.stale_media(1000) s where s like 'eeeeeeee-0000-0000-0000-000000000801/avatar/%'), 'gallery kept by the clean-up');
select pg_temp.check(not has_column_privilege('authenticated', 'public.profiles', 'interests', 'update')
  and not has_column_privilege('authenticated', 'public.profiles', 'photos', 'update'), 'interests, photos: functions only');

-- ---- matching: shared interests after language ----
-- DEarly (no shared interest) waits 5 s, DShared (cricket) 2 s, both Hindi: DShared is chosen for DHost.
insert into public.queue (user_id, gender, target, lang, interests, joined_at, seen_at) values
  ('eeeeeeee-0000-0000-0000-000000000804', 'male', 'any', 'hi', array['anime'], now() - interval '5 seconds', now()),
  ('eeeeeeee-0000-0000-0000-000000000805', 'male', 'any', 'hi', array['cricket', 'food', 'travel'], now() - interval '2 seconds', now());
update public.voice_rooms set ended_at = now() where host = 'eeeeeeee-0000-0000-0000-000000000801' and ended_at is null;
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000801');
insert into v select 'm1', public.find_match2('any', 'hi')::text;
reset role;
select pg_temp.check((select user_a = 'eeeeeeee-0000-0000-0000-000000000805' from public.matches where id = pg_temp.r('m1')::uuid), 'shared interest first');
select pg_temp.check((select interests @> array['books', 'cricket', 'tech'] and cardinality(interests) = 3 from public.queue where user_id = 'eeeeeeee-0000-0000-0000-000000000801'),
  'my tags ride in my queue row');
-- Someone waiting over 10 s keeps their turn over a newer shared-interest match.
update public.matches set ended_at = now() where id = pg_temp.r('m1')::uuid;
update public.queue set joined_at = now() - interval '12 seconds', seen_at = now() where user_id = 'eeeeeeee-0000-0000-0000-000000000804';
update public.queue set joined_at = now() - interval '1 seconds', seen_at = now() where user_id = 'eeeeeeee-0000-0000-0000-000000000805';
insert into auth.users (id, aud, role, phone) values ('eeeeeeee-0000-0000-0000-000000000806', 'authenticated', 'authenticated', '919855570806');
insert into public.profiles (id, name, gender, birth_date, interests) values ('eeeeeeee-0000-0000-0000-000000000806', 'DCricket', 'female', '1999-01-01', array['cricket', 'food', 'tech']);
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000000806');
insert into v select 'm2', public.find_match2('any', 'hi')::text;
reset role;
select pg_temp.check((select user_a = 'eeeeeeee-0000-0000-0000-000000000804' from public.matches where id = pg_temp.r('m2')::uuid), 'long wait keeps its turn');

-- A ban takes the person out of the line at once (026), so the search needs no profile lookup per person.
update public.queue set seen_at = now() where user_id = 'eeeeeeee-0000-0000-0000-000000000805';
update public.profiles set banned_until = now() + interval '1 day' where id = 'eeeeeeee-0000-0000-0000-000000000805';
select pg_temp.check((select seen_at = '-infinity' from public.queue where user_id = 'eeeeeeee-0000-0000-0000-000000000805'), 'banned: out of the line');

-- ---- privileges ----
select pg_temp.check(not has_function_privilege('anon', 'public.voice_list3(text, text, text)', 'execute')
  and not has_function_privilege('anon', 'public.online_count()', 'execute')
  and not has_function_privilege('anon', 'public.invite_to_room(text, uuid)', 'execute')
  and not has_function_privilege('authenticated', 'public.voice_list_refresh()', 'execute')
  and not has_table_privilege('authenticated', 'public.app_stats', 'select'), 'privileges');

select 'all discovery checks passed' as result;
rollback;
