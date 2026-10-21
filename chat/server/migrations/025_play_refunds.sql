-- Google Play refunds and revocations end Plus (and the 24 h gender pass) right away.
--  * play-rtdn (edge function) receives Play's Real-time developer notifications through Pub/Sub push:
--    SUBSCRIPTION_REVOKED and voided purchases call play_revoke().
--  * Once a day pg_cron asks play-rtdn to read Play's Voided Purchases API as well (a missed notification is
--    caught within a day). Without the secret PLAY_SERVICE_ACCOUNT (or GOOGLE_SERVICE_ACCOUNT_JSON, used by
--    verify-purchase) that call does nothing.
alter table public.purchases add column if not exists revoked_at timestamptz,
  add column if not exists revoke_reason text check (char_length(revoke_reason) <= 40);

-- Marks the purchase revoked and ends what it gave. Safe to repeat. Returns what it found (for the logs).
create or replace function public.play_revoke(p_token text, p_reason text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare pu public.purchases; owner uuid; premium boolean;
begin
  select * into pu from public.purchases x where x.token = p_token;
  owner := coalesce(pu.user_id, (select s.user_id from public.subscriptions s where s.purchase_token = p_token limit 1));
  if owner is null then return jsonb_build_object('found', false); end if;
  update public.purchases set revoked_at = coalesce(revoked_at, now()), revoke_reason = coalesce(revoke_reason, left(p_reason, 40))
   where token = p_token;
  premium := coalesce(pu.product_id, 'yaari_premium_monthly') = 'yaari_premium_monthly';
  if premium then
    update public.subscriptions set premium_until = least(premium_until, now()), updated_at = now() where user_id = owner;
  else
    -- one pass = 24 h taken back from what is left (passes stack)
    update public.subscriptions set gender_until = greatest(now(), gender_until - interval '24 hours'), updated_at = now()
     where user_id = owner and gender_until > now() and pu.revoked_at is null;
  end if;
  return jsonb_build_object('found', true, 'user', owner, 'product', coalesce(pu.product_id, 'yaari_premium_monthly'));
end $$;
revoke execute on function public.play_revoke(text, text) from public, anon, authenticated;
grant execute on function public.play_revoke(text, text) to service_role;

select cron.schedule('yaari-play-voided', '41 4 * * *', $cron$
  select net.http_post(
    url := 'https://dgynsckrqgnpyyvqekzi.supabase.co/functions/v1/play-rtdn?mode=voided',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-edge-key', (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'edge_call_key')),
    body := '{}'::jsonb, timeout_milliseconds := 60000)
$cron$);
