-- Applied right after 001 (Supabase advisor findings). Kept here so the repo matches the database.
-- Lock down the test helpers until they can be dropped.
alter table public._t enable row level security;
revoke all on public._t from anon, authenticated;
revoke execute on function public._act(uuid), public._fails(text, text) from public, anon, authenticated;
alter function public._act(uuid) set search_path = '';
alter function public._fails(text, text) set search_path = '';

-- Internal helpers are not part of the API: they could reveal other people's Premium status or blocks.
revoke execute on function public.message_guard(), public.check_adult(), public.is_premium(uuid),
  public.is_blocked(uuid, uuid), public.exit_lock() from public, anon, authenticated;

-- RLS helper that only answers about a chat the caller is in.
create function public.chat_blocked(mid uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and public.is_blocked(m.user_a, m.user_b))
$$;
revoke execute on function public.chat_blocked(uuid) from public, anon;
grant execute on function public.chat_blocked(uuid) to authenticated;
alter policy "write chat" on public.messages with check (
  sender = (select auth.uid()) and public.is_participant(match_id) and not public.chat_blocked(match_id));

-- Own Premium status for the client (reads only the caller's row).
create function public.my_premium_until() returns timestamptz language sql stable security definer set search_path = '' as $$
  select premium_until from public.subscriptions where user_id = auth.uid()
$$;
revoke execute on function public.my_premium_until() from public, anon;
grant execute on function public.my_premium_until() to authenticated;

-- The anti-spam check and foreign keys need these.
create index messages_sender_time on public.messages (sender, created_at desc);
create index reports_reporter on public.reports (reporter);
create index reports_match on public.reports (match_id);
