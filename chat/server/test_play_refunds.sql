-- Play refunds and revocations (migration 025). One transaction, rolled back.
begin;
set local statement_timeout = '10s';
create function pg_temp.check(ok boolean, what text) returns void language plpgsql as $$
begin if ok is not true then raise exception 'failed: %', what; end if; end $$;

insert into auth.users (id, aud, role, phone) values
  ('eeeeeeee-0000-0000-0000-000000000901', 'authenticated', 'authenticated', '919855570901'),
  ('eeeeeeee-0000-0000-0000-000000000902', 'authenticated', 'authenticated', '919855570902');
insert into public.profiles (id, name, gender, birth_date) values
  ('eeeeeeee-0000-0000-0000-000000000901', 'PPlus', 'female', '1998-01-01'),
  ('eeeeeeee-0000-0000-0000-000000000902', 'PPass', 'male', '1999-01-01');
insert into public.purchases (token, user_id, product_id) values
  ('tok-sub-901', 'eeeeeeee-0000-0000-0000-000000000901', 'yaari_premium_monthly'),
  ('tok-pass-902a', 'eeeeeeee-0000-0000-0000-000000000902', 'gender_pass_24h'),
  ('tok-pass-902b', 'eeeeeeee-0000-0000-0000-000000000902', 'gender_pass_24h');
insert into public.subscriptions (user_id, premium_until, purchase_token, gender_until) values
  ('eeeeeeee-0000-0000-0000-000000000901', now() + interval '20 days', 'tok-sub-901', null),
  ('eeeeeeee-0000-0000-0000-000000000902', null, null, now() + interval '30 hours');

set local role service_role;
select pg_temp.check(public.play_revoke('tok-unknown', 'voided')->>'found' = 'false', 'unknown token: nothing');
select pg_temp.check(public.play_revoke('tok-sub-901', 'revoked')->>'product' = 'yaari_premium_monthly', 'subscription revoked');
reset role;
select pg_temp.check(not public.is_premium('eeeeeeee-0000-0000-0000-000000000901'), 'Plus ended');
select pg_temp.check((select revoked_at is not null and revoke_reason = 'revoked' from public.purchases where token = 'tok-sub-901'), 'purchase marked');
set local role service_role;
select public.play_revoke('tok-sub-901', 'voided'); -- repeated (RTDN + daily voided list): no change
reset role;
select pg_temp.check((select revoke_reason = 'revoked' from public.purchases where token = 'tok-sub-901'), 'first reason kept');

-- Two stacked passes (30 h left); one refunded takes 24 h back, twice does not take 48 h.
set local role service_role;
select public.play_revoke('tok-pass-902a', 'voided');
select public.play_revoke('tok-pass-902a', 'voided');
reset role;
select pg_temp.check((select gender_until between now() + interval '5 hours 59 minutes' and now() + interval '6 hours 1 minute'
                        from public.subscriptions where user_id = 'eeeeeeee-0000-0000-0000-000000000902'), '24 h taken back once');
set local role service_role;
select public.play_revoke('tok-pass-902b', 'voided');
reset role;
select pg_temp.check((select gender_until <= now() from public.subscriptions where user_id = 'eeeeeeee-0000-0000-0000-000000000902'), 'both refunded: no pass');

select pg_temp.check(not has_function_privilege('authenticated', 'public.play_revoke(text, text)', 'execute')
  and not has_function_privilege('anon', 'public.play_revoke(text, text)', 'execute'), 'service role only');
select pg_temp.check(exists (select 1 from cron.job where jobname = 'yaari-play-voided'), 'daily voided check scheduled');

select 'all refund checks passed' as result;
rollback;
