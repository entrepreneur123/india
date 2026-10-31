-- profiles_seen and profiles_public (061) were security-barrier views. Nothing selects them but security-definer
-- functions (no grant to anon or authenticated), so the barrier protected nothing, while it kept the planner from
-- pushing a join into them: every list that joined one scanned all profiles once per row, masking each (inbox2's
-- generic plan, 2026-10-31: Seq Scan on profiles, 505 rows × 23 loops → 664 ms; group_list 58 ms, friend_list 31 ms,
-- recent_list 33 ms). Without it the join is an index lookup: 4.5 / 2.1 / 2.2 / 4.0 ms (measured before applying).
alter view public.profiles_seen set (security_barrier = false);
alter view public.profiles_public set (security_barrier = false);

-- Foreign keys to people without an index: deleting an account (delete-account) would scan each of these tables.
create index if not exists chat_ratings_rater on public.chat_ratings (rater);
create index if not exists contact_shares_viewer on public.contact_shares (viewer);
create index if not exists gifts_from_user on public.gifts (from_user);
create index if not exists handle_lookups_other on public.handle_lookups (other);
create index if not exists party_queue_matched on public.party_queue (matched) where matched is not null;
create index if not exists recent_hidden_partner on public.recent_hidden (partner);
create index if not exists reports_reviewed_by on public.reports (reviewed_by) where reviewed_by is not null;
create index if not exists voice_room_passes_user on public.voice_room_passes (user_id);
create index if not exists admin_actions_admin on public.admin_actions (admin);
create index if not exists client_events_user on public.client_events (user_id) where user_id is not null;
create index if not exists group_chats_owner on public.group_chats (owner);
create index if not exists notifications_post_id on public.notifications (post_id) where post_id is not null;

-- Realtime policies: auth.uid() once per query, not per row (the linter's auth_rls_initplan).
alter policy "megaphone listen" on realtime.messages
  using (extension = 'broadcast'
         and realtime.topic() = public.megaphone_topic((select x.country from public.profiles x where x.id = (select auth.uid()))));
alter policy "friend presence" on realtime.messages
  using (extension = 'broadcast' and realtime.topic() = 'friends:' || (select auth.uid())::text);

-- Trigger functions are not an API (the linter's anon_security_definer_function_executable).
revoke execute on function public.group_messages_rt(), public.matches_rt(), public.messages_rt(), public.notifications_rt()
  from public, anon, authenticated;
