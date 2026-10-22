-- IDs, names without fancy fonts, looks (shop, equip, redeem, refund), the gone cover/colour, and ended chats that go
-- on only between friends (migration 040). One transaction, rolled back.
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

-- ---- the rule itself ----
select pg_temp.check(public.handle_valid(x), 'valid: ' || x) from unnest(array[
  'L._.1sA', 'lisa', 'ab', 'priya_99', 'राहुल', 'রাহুল', 'ਰਾਹੁਲ', 'ராகுல்', 'ರಾಹುಲ್', 'രാഹുൽ', 'محمد', 'علی۱۲', 'דני', 'Ελένη',
  'Иван', 'ნინო', 'Հայկ', 'สมชาย', 'မောင်မောင်', 'さくら', 'カタカナ', '小明', '지민', 'ㅋㅋㅋ', 'Nguyễn', 'Şükrü', 'Ədalət', 'Ọlá',
  'a!#$%&()*+,-./', 'x:;=?[]^_{|}~', 'राहुल_99', 'abcdefghijklmnopqrstuvwx']) x;
select pg_temp.check(not public.handle_valid(x), 'invalid: ' || coalesce(x, 'null')) from unnest(array[
  null, '', 'a', '지', 'abcdefghijklmnopqrstuvwxy', 'li sa', 'lisa😀', '😀😀😀', '𝓛𝓲𝓼𝓪', 'Ｌｉｓａ', 'ʟɪsᴀ', 'ⓛⓘⓢⓐ', 'ℒisa',
  'lisᵃ', 'ꜱhiv', 'ᏞᎥᏕᎪ', 'ᒪIᔕᗩ', 'ʇxǝʇ', 'li@sa', 'li"sa', 'li''sa', 'li<sa', 'li\sa', 'li`sa', '...', '!!!',
  'аdmin', 'pаypal', 'Ελa', 'lis' || chr(8205) || 'a', 'lis' || chr(8203) || 'a', 'Nguye' || chr(770) || chr(771) || 'n']) x;
select pg_temp.check(public.handle_reserved('Yaari_Official') and public.handle_reserved('admin.1') and public.handle_reserved('Support')
  and not public.handle_reserved('helpful') and not public.handle_reserved('teamwork'), 'reserved');
select pg_temp.check(public.name_plain(x), 'plain name: ' || x) from unnest(array[
  'Lisa', 'Lisa 💖', 'राहुल शर्मा', 'Nguyễn Văn A', 'محمد علی', '지민', '🇮🇳 Priya', 'Zoë', 'José']) x;
select pg_temp.check(not public.name_plain(x), 'fancy name: ' || x) from unnest(array[
  '𝓛𝓲𝓼𝓪', '𝐋𝐢𝐬𝐚', 'Ｌｉｓａ', 'ʟɪsᴀ', 'ⓛⓘⓢⓐ', '🄻🄸🅂🄰', 'ℒisa', 'ᏞᎥᏕᎪ', 'ᒪIᔕᗩ', 'ʇxǝʇ',
  'L' || chr(771) || chr(770) || 'isa']) x;

-- ---- people ----
insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000004001', 'authenticated', 'authenticated', '919855574001'),
  ('eeeeeeee-0000-0000-0000-000000004002', 'authenticated', 'authenticated', '919855574002'),
  ('eeeeeeee-0000-0000-0000-000000004003', 'authenticated', 'authenticated', '919855574003');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000004001', 'Lisa', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004002', 'Raj Kumar', 'male', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004003', '😀', 'male', '2000-01-01');
select pg_temp.check((select handle ~ '^lisa_[0-9]{4}$' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004001'), 'free ID from the name');
select pg_temp.check((select handle ~ '^rajkumar_[0-9]{4}$' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004002'), 'free ID: plain letters of the name');
select pg_temp.check((select handle ~ '^user_[0-9]{4}$' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004003'), 'free ID when the name has no letters');
select pg_temp.check((select count(*) = 0 from public.profiles where handle is null), 'everyone has an ID');

set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004001');
-- names
select pg_temp.fails($$ update public.profiles set name = '𝓛𝓲𝓼𝓪' where id = 'eeeeeeee-0000-0000-0000-000000004001' $$, 'name_invalid');
update public.profiles set name = 'Lisa ✨' where id = 'eeeeeeee-0000-0000-0000-000000004001';
-- An ID cannot be written straight into the table, nor a look.
select pg_temp.fails($$ update public.profiles set handle = 'hack' where id = 'eeeeeeee-0000-0000-0000-000000004001' $$, 'permission denied');
select pg_temp.fails($$ update public.profiles set look_badge = 'badge_crown' where id = 'eeeeeeee-0000-0000-0000-000000004001' $$, 'permission denied');
select pg_temp.fails($$ insert into public.user_cosmetics (user_id, item_id) values ('eeeeeeee-0000-0000-0000-000000004001', 'badge_crown') $$, 'permission denied');
select pg_temp.fails($$ select public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'badge_crown', 'tok') $$, 'permission denied');
select pg_temp.fails($$ select public.admin_grant_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'badge_crown') $$, 'not_allowed');

-- IDs
select pg_temp.check(public.handle_check('L._.1sA') = 'ok', 'check ok');
select pg_temp.check(public.handle_check('𝓛𝓲𝓼𝓪') = 'invalid', 'check invalid');
select pg_temp.check(public.handle_check('yaari.team') = 'reserved', 'check reserved');
select pg_temp.check(public.set_handle(' L._.1sA ') = 'L._.1sA', 'set (trimmed)');
select pg_temp.check(public.handle_check('l._.1sa') = 'mine' or public.handle_check('l._.1sa') = 'ok', 'own ID, other case');
select pg_temp.check(public.set_handle('l._.1SA') = 'l._.1SA', 'a new spelling of the same ID right away');
select pg_temp.fails($$ select public.set_handle('lisa.new') $$, 'handle_cooldown');
select pg_temp.fails($$ select public.set_handle('li sa') $$, 'handle_invalid');
select pg_temp.fails($$ select public.set_handle('support') $$, 'handle_reserved');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004002');
select pg_temp.check(public.handle_check('L._.1sa') = 'taken', 'check taken, case-blind');
select pg_temp.fails($$ select public.set_handle('L._.1Sa') $$, 'handle_taken');
select pg_temp.check(public.set_handle('राहुल_99') = 'राहुल_99', 'Hindi ID');
-- NFC: the decomposed spelling of an accented ID is stored composed.
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004003');
select pg_temp.check(public.set_handle('Jose' || chr(769)) = 'José', 'NFC');

-- ---- looks ----
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004001');
select pg_temp.check(jsonb_array_length(public.shop()->'items') = 26 and jsonb_array_length(public.shop()->'owned') = 0, 'shop');
select pg_temp.fails($$ select public.equip_look('badge_crown', null, null, null) $$, 'not_owned');
reset role;
select pg_temp.check(public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'badge_crown', 'tok-crown') = 'granted', 'redeem');
select pg_temp.check(public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'badge_crown', 'tok-crown') = 'already', 'redeem again: same person');
select pg_temp.check(public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004002', 'badge_crown', 'tok-crown') = 'other_account', 'a token is the first account''s');
select pg_temp.check(public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'frame_fire', 'tok-fire') = 'granted', 'redeem a frame');
select pg_temp.check(public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'frame_frost', 'tok-frost') = 'granted', 'redeem another frame');
select pg_temp.check((select look_badge = 'badge_crown' and look_frame = 'frame_fire' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004001'),
  'the first of a kind is worn at once, the second is not');
select pg_temp.fails($$ select public.redeem_cosmetic('eeeeeeee-0000-0000-0000-000000004001', 'badge_nope', 'tok-x') $$, 'bad_request');
set local role authenticated;
select pg_temp.check(public.equip_look(null, null, null, 'frame_frost')->>'frame' = 'frame_frost', 'equip');
select pg_temp.check((public.equip_look(null, null, null, 'frame_frost')->>'badge') is null, 'take the badge off');
select pg_temp.fails($$ select public.equip_look('frame_frost', null, null, null) $$, 'bad_request');
select pg_temp.check(jsonb_array_length(public.shop()->'owned') = 3, 'shop: owned');
select pg_temp.check((select count(*) = 3 from public.user_cosmetics), 'I see only my own items');
select public.equip_look('badge_crown', null, null, 'frame_fire');

-- Who may see my look: someone I met; not a stranger.
reset role;
insert into public.matches (user_a, user_b, kind) values ('eeeeeeee-0000-0000-0000-000000004001', 'eeeeeeee-0000-0000-0000-000000004002', 'random');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004002');
select pg_temp.check((select (x->>'badge') = 'badge_crown' and (x->>'handle') = 'l._.1SA'
  from jsonb_array_elements(public.looks(array['eeeeeeee-0000-0000-0000-000000004001'::uuid])) x), 'looks: someone I met');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004003');
select pg_temp.check(jsonb_array_length(public.looks(array['eeeeeeee-0000-0000-0000-000000004001'::uuid])) = 0, 'looks: a stranger sees nothing');

-- Refund: the look comes off and is no longer owned.
reset role;
select pg_temp.check((public.play_revoke('tok-fire', 'refund')->>'product') = 'frame_fire', 'revoke');
select pg_temp.check((select look_frame is null and look_badge = 'badge_crown' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004001'), 'refund takes the frame off');
select pg_temp.check(not exists (select 1 from public.user_cosmetics where item_id = 'frame_fire'), 'refund: not owned');

-- ---- the free cover and colour are gone ----
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004001');
select public.set_profile_style('eeeeeeee-0000-0000-0000-000000004001/cover/x.jpg', 'rose', 'hello', array['en'], '[]');
reset role;
select pg_temp.check((select cover_path is null and accent is null and status = 'hello' from public.profiles where id = 'eeeeeeee-0000-0000-0000-000000004001'), 'cover and colour ignored');

-- ---- an ended chat goes on only between friends ----
update public.matches set ended_at = now() where user_a = 'eeeeeeee-0000-0000-0000-000000004001' and user_b = 'eeeeeeee-0000-0000-0000-000000004002';
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004001');
select pg_temp.fails($$ insert into public.messages (match_id, body) select id, 'hi again'
  from public.matches where user_a = 'eeeeeeee-0000-0000-0000-000000004001' and user_b = 'eeeeeeee-0000-0000-0000-000000004002' $$, 'row-level security');
select pg_temp.check((select count(*) = 0 from public.messages m join public.matches x on x.id = m.match_id
  where x.user_a = 'eeeeeeee-0000-0000-0000-000000004001'), 'history still readable (and nothing was written)');
reset role;
insert into public.friendships (requester, addressee, status) values ('eeeeeeee-0000-0000-0000-000000004001', 'eeeeeeee-0000-0000-0000-000000004002', 'accepted');
set local role authenticated;
insert into public.messages (match_id, body) select id, 'hi friend'
  from public.matches where user_a = 'eeeeeeee-0000-0000-0000-000000004001' and user_b = 'eeeeeeee-0000-0000-0000-000000004002';
-- A live random chat is open to both, friends or not.
reset role;
insert into public.matches (user_a, user_b, kind) values ('eeeeeeee-0000-0000-0000-000000004003', 'eeeeeeee-0000-0000-0000-000000004002', 'random');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004003');
insert into public.messages (match_id, body) select id, 'hello'
  from public.matches where user_a = 'eeeeeeee-0000-0000-0000-000000004003' and ended_at is null;

reset role;
select 'test_handles_looks: all passed' as result;
rollback;
