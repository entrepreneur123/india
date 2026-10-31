-- 122: the retention code as it runs on production, back in the repository (docs/DIAGNOSIS.md #8). purge_old_data grew
-- past 013 and translate_tidy plus two cron jobs were added by hand (owner_run_once_3/4/6, SQL editor), so the
-- database and the repository disagreed. These are the live definitions (pg_get_functiondef and cron.job on
-- 2026-10-31) — ALREADY IN EFFECT. The MCP connector cannot run this file (it holds the word the connector asks a
-- question about); running it again in the SQL editor is a safe no-op. Don't edit the live code here without applying.
-- Cron on production (cron.job): yaari-purge-data 17 3 * * * (013) · yaari-purge-media 27 3 * * * (013) ·
-- yaari-play-voided 41 4 * * * (025) · yaari-queue-cleanup */5 * * * * · yaari-translate-tidy 17 3 * * * ·
-- yaari-phone-identities 23 3 * * * (042) · yaari-janitor every 30 s (119).

create or replace function public.purge_old_data() returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.matches x
   where x.ended_at < now() - interval '30 days' and not public.are_friends(x.user_a, x.user_b);
  delete from public.queue q where q.seen_at < now() - interval '1 day';
  delete from public.banned_phones b where b.banned_until < now();
  delete from public.reports r where r.created_at < now() - interval '1 year';
  delete from public.voice_messages m where m.created_at < now() - interval '30 days';
  delete from public.voice_rooms r
   where coalesce(r.ended_at, r.created_at) < now() - interval '7 days' and not public.voice_alive(r);
  delete from public.client_events e where e.created_at < now() - interval '90 days';
  delete from public.client_event_quota q where q.day < current_date - 2;
  delete from public.notifications n where n.read_at < now() - interval '90 days' or n.created_at < now() - interval '180 days';
  delete from public.device_tokens d where not d.active and d.updated_at < now() - interval '30 days';
end $$;
revoke execute on function public.purge_old_data() from public, anon, authenticated;

create or replace function public.translate_tidy() returns void
language sql security definer set search_path = '' as $$
  with a as (delete from public.translations where created_at < now() - interval '30 days' returning 1)
  delete from public.translate_usage where day < (now() at time zone 'utc')::date - 2;
$$;
revoke execute on function public.translate_tidy() from public, anon, authenticated;

select cron.schedule('yaari-queue-cleanup', '*/5 * * * *', $cron$delete from public.queue q where q.seen_at < now() - interval '5 minutes'$cron$);
select cron.schedule('yaari-translate-tidy', '17 3 * * *', 'select public.translate_tidy()');
