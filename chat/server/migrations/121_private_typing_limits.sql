-- 121: the typing/presence channel of a chat goes private (docs/DIAGNOSIS.md #9). typing-<match> was a public
-- channel: anyone with the anon key could join any match's channel, show up as "present" (the app's partner-presence
-- rule believed them) and send "typing". It is now a private topic checked like chat:<match> (107): only the two
-- people of the match may listen, be present or send the typing/profile signals. Policies only apply to private
-- channels, so pages still on the public channel keep working until they reload.
-- Also: the pre-sign-in event logger loses its anon grant (the web never calls it; its 5,000-a-day shared cap was for
-- anyone with the anon key to burn — Android's port signs in first and uses log_events), and edge functions get a
-- counter in the database for their limits (link-meta's lived in one instance's memory).

create or replace function public.rt_topic_ok(p_topic text) returns boolean
language sql stable security definer set search_path = '' as $$
  select case
    when p_topic = 'user:' || auth.uid()::text then true
    when p_topic ~ '^chat:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then public.chat_readable(substr(p_topic, 6)::uuid)
    when p_topic ~ '^typing-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then public.chat_readable(substr(p_topic, 8)::uuid)
    else false
  end
$$;

-- 107's "chat and user topics" policy already lets a member listen to broadcasts on any topic rt_topic_ok allows,
-- typing- now included. Presence, and sending, are only on the typing topic (chat: and user: are written by the server).
create or replace function public.typing_topic_ok(p_topic text) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_topic ~ '^typing-' and public.rt_topic_ok(p_topic)
$$;
revoke execute on function public.typing_topic_ok(text) from public, anon;
grant execute on function public.typing_topic_ok(text) to authenticated;

do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'typing presence') then
    create policy "typing presence" on realtime.messages for select to authenticated
      using (extension = 'presence' and public.typing_topic_ok(realtime.topic()));
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'typing send') then
    create policy "typing send" on realtime.messages for insert to authenticated
      with check (extension in ('broadcast', 'presence') and public.typing_topic_ok(realtime.topic()));
  end if;
end $$;

revoke execute on function public.log_events_anon(jsonb) from anon;

-- A limit counter for edge functions: one row per key, moved along in place (rows are never removed).
create table if not exists public.rate_counters (
  key text primary key,
  window_start timestamptz not null,
  n int not null
);
alter table public.rate_counters enable row level security;
revoke all on public.rate_counters from public, anon, authenticated;

/** True while the key has been seen fewer than p_limit times in the current window of p_window. */
create or replace function public.rate_ok(p_key text, p_limit int, p_window interval) returns boolean
language plpgsql security definer set search_path = '' as $$
declare c public.rate_counters;
begin
  insert into public.rate_counters as r (key, window_start, n) values (p_key, now(), 1)
  on conflict (key) do update set
    window_start = case when r.window_start < now() - p_window then now() else r.window_start end,
    n = case when r.window_start < now() - p_window then 1 else r.n + 1 end
  returning * into c;
  return c.n <= p_limit;
end $$;
revoke execute on function public.rate_ok(text, int, interval) from public, anon, authenticated;
grant execute on function public.rate_ok(text, int, interval) to service_role;
