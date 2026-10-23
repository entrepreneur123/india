-- Migration 044: friends exchange messenger contacts. One transaction, rolled back.
begin;
set local statement_timeout = '20s';
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
  ('eeeeeeee-0000-0000-0000-000000004401', 'authenticated', 'authenticated', '919855574401'),
  ('eeeeeeee-0000-0000-0000-000000004402', 'authenticated', 'authenticated', '819055574402'),
  ('eeeeeeee-0000-0000-0000-000000004403', 'authenticated', 'authenticated', '821055574403');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000004401', 'Asha', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004402', 'Yuki', 'female', '1999-01-01'),
  ('eeeeeeee-0000-0000-0000-000000004403', 'Minho', 'male', '2000-01-01');
insert into public.friendships (requester, addressee, status) values ('eeeeeeee-0000-0000-0000-000000004401', 'eeeeeeee-0000-0000-0000-000000004402', 'accepted');
set local role authenticated;
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004401');
select pg_temp.check(public.set_contacts('{"whatsapp":"+91 98765-43210","telegram":"@asha_t","line":" "}') = '{"whatsapp":"+919876543210","telegram":"asha_t"}', 'cleaned, empty dropped');
select pg_temp.fails($$ select public.set_contacts('{"whatsapp":"call me"}') $$, 'contact_invalid');
select pg_temp.fails($$ select public.set_contacts('{"telegram":"a b<script>"}') $$, 'contact_invalid');
select pg_temp.fails($$ select public.set_contacts('{"myspace":"x"}') $$, 'bad_request');
select pg_temp.fails($$ select public.share_contacts('eeeeeeee-0000-0000-0000-000000004403', true) $$, 'not_friends');
select pg_temp.fails($$ select * from public.contact_cards $$, 'permission denied');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004402');
select pg_temp.check((public.contacts_of('eeeeeeee-0000-0000-0000-000000004401') ->> 'contacts') is null, 'friend: nothing until shared');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004401');
select public.share_contacts('eeeeeeee-0000-0000-0000-000000004402', true);
select pg_temp.check(public.my_contacts() -> 'shared_with' = '["eeeeeeee-0000-0000-0000-000000004402"]', 'mine: shared with Yuki');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004402');
select pg_temp.check(public.contacts_of('eeeeeeee-0000-0000-0000-000000004401') -> 'contacts' ->> 'telegram' = 'asha_t', 'Yuki sees them');
select pg_temp.check((public.contacts_of('eeeeeeee-0000-0000-0000-000000004401') ->> 'mine_shared')::boolean = false, 'Yuki has not shared hers');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004403');
select pg_temp.check(public.contacts_of('eeeeeeee-0000-0000-0000-000000004401') is null, 'stranger: nothing');
select pg_temp.act('eeeeeeee-0000-0000-0000-000000004402');
select public.remove_friend('eeeeeeee-0000-0000-0000-000000004401');
reset role;
select pg_temp.check((select count(*) = 0 from public.contact_shares where owner = 'eeeeeeee-0000-0000-0000-000000004401'), 'unfriend ends sharing');
insert into public.friendships (requester, addressee, status) values ('eeeeeeee-0000-0000-0000-000000004401', 'eeeeeeee-0000-0000-0000-000000004402', 'accepted');
set local role authenticated;
select pg_temp.check((public.contacts_of('eeeeeeee-0000-0000-0000-000000004401') ->> 'contacts') is null, 'friends again: must share again');
reset role;
select 'test_friend_contacts: all passed' as result;
rollback;
