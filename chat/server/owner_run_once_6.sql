-- Run once in the Supabase SQL editor (038): a daily clean-up of old translations and translation counters.
-- Old answers and old counters go (daily).
create or replace function public.translate_tidy() returns void
language sql security definer set search_path = '' as $$
  with a as (delete from public.translations where created_at < now() - interval '30 days' returning 1)
  delete from public.translate_usage where day < (now() at time zone 'utc')::date - 2;
$$;
revoke execute on function public.translate_tidy() from public, anon, authenticated;
select cron.schedule('yaari-translate-tidy', '17 3 * * *', 'select public.translate_tidy()')
 where not exists (select 1 from cron.job where jobname = 'yaari-translate-tidy');
