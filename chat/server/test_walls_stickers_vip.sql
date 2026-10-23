-- Migration 046: no emoji in chats (stickers need their pack), walls and incognito need their purchase, and someone
-- incognito is "VIP" to all but friends (and to everyone in rooms), and can't be found by ID. Rolled back.
begin;
set local statement_timeout = '30s';
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
  ('eeeeeeee-0000-0000-0000-000000004601', 'authenticated', 'authenticated', '919855574601'),
  ('eeeeeeee-0000-0000-0000-000000004602', 'authenticated', 'authenticated', '919855574602'),
  ('eeeeeeee-0000-0000-0000-000000004603', 'authenticated', 'authenticated', '919855574603');
insert into public.profiles (id, name, gender, birth_date, city, bio, photo_path) values
  ('eeeeeeee-0000-0000-0000-000000004601', 'Veera', 'female', '1998-01-01', 'Pune', 'hi', 'eeeeeeee-0000-0000-0000-000000004601/avatar/a.jpg'),
  ('eeeeeeee-0000-0000-0000-000000004602', 'Stranger', 'male', '1999-01-01', 'Delhi', '', null),
  ('eeeeeeee-0000-0000-0000-000000004603', 'Friend', 'male', '2000-01-01', 'Goa', '', null);
update public.profiles set handle = 'zz_veera' where id = 'eeeeeeee-0000-0000-0000-000000004601';
insert into public.matches (id, user_a, user_b) values ('eeeeeeee-0000-0000-0000-0000000046aa', 'eeeeeeee-0000-0000-0000-000000004601', 'eeeeeeee-0000-0000-0000-000000004602');
insert into public.friendships (requester, addressee, status) values ('eeeeeeee-0000-0000-0000-000000004601', 'eeeeeeee-0000-0000-0000-000000004603', 'accepted');

set local role authenticated;
-- Chats: no emoji; stickers need the pack.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004601');
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-0000000046aa', 'hello there');
select pg_temp.fails($$ insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-0000000046aa', 'hi 😀') $$, 'emoji_not_allowed');
select pg_temp.fails($$ insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-0000000046aa', 'love ❤️') $$, 'emoji_not_allowed');
select pg_temp.fails($$ insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-0000000046aa', ':st:chai/2:') $$, 'sticker_not_owned');
reset role;
insert into public.user_cosmetics (user_id, item_id) values ('eeeeeeee-0000-0000-0000-000000004601', 'sticker_chai');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004601');
insert into public.messages (match_id, body) values ('eeeeeeee-0000-0000-0000-0000000046aa', ':st:chai/2:');
select pg_temp.check(not public.has_emoji('नमस्ते, kaise ho? 안녕 こんにちは'), 'scripts are not emoji');
-- Walls and incognito need their purchase.
select pg_temp.fails($$ select public.set_wall('wall_stars') $$, 'not_owned');
select pg_temp.fails($$ select public.set_anon(true) $$, 'not_owned');
reset role;
insert into public.user_cosmetics (user_id, item_id) values ('eeeeeeee-0000-0000-0000-000000004601', 'vip_anon'), ('eeeeeeee-0000-0000-0000-000000004601', 'wall_stars');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004601');
select public.set_wall('wall_stars');
select public.set_anon(true);
select pg_temp.check((select shop() ->> 'wall' = 'wall_stars' and (shop() ->> 'anon')::boolean from (select public.shop() as shop) s), 'shop says wall and anon');
select pg_temp.check((select name = 'Veera' from public.card('eeeeeeee-0000-0000-0000-000000004601')), 'I still see myself');
-- The stranger met in random chat sees "VIP" and nothing else.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004602');
select pg_temp.check((select name = 'VIP' and age = 0 and photo_path is null and city = '' and bio = '' and country is null
                        from public.card('eeeeeeee-0000-0000-0000-000000004601')), 'stranger: VIP card');
select pg_temp.check((select coalesce(j -> 0 ->> 'handle', '') = '' and j -> 0 ->> 'badge' is null from (select public.looks(array['eeeeeeee-0000-0000-0000-000000004601'::uuid]) j) x), 'stranger: no ID or looks');
select pg_temp.check((select count(*) = 0 from public.search_handles('zz_vee')), 'not found by ID');
-- A friend sees everything.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004603');
select pg_temp.check((select name = 'Veera' and photo_path is not null from public.card('eeeeeeee-0000-0000-0000-000000004601')), 'friend sees through');
reset role;
-- Rooms (broadcast to all): VIP for everyone.
select pg_temp.check((select public.voice_member_json(row('zz', 'eeeeeeee-0000-0000-0000-000000004601', 'listener', null, true, null, now(), now(), null, false, null, null, false)::public.voice_members) ->> 'name' = 'VIP'), 'room member: VIP');
select 'test_walls_stickers_vip: all passed' as result;
rollback;
