-- Tapping a face opens the profile everywhere (the owner, 2026-10-26). can_view() also lets me see:
--   * someone who commented on a post I can read (mine, a public one, a friend's),
--   * the actor of a notification to me (a like, a comment, an invite...),
--   * someone I asked for friendship that is still pending ("Sent" in Recent).
create or replace function public.can_view(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.handle_lookups l where l.viewer = auth.uid() and l.other = can_view.other and l.at > now() - interval '1 day')
      or exists (select 1 from public.matches m
               where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other))
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid()) or (f.requester = auth.uid() and f.addressee = other))
      or exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other and not a.kicked
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.posts pp where pp.user_id = other and pp.visibility = 'public')
      or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id
                  where s.user_id = other and s.seat is not null and r.ended_at is null
                    and s.seen_at > now() - interval '2 minutes')
      or exists (select 1 from public.notifications n where n.actor = other and n.user_id = auth.uid())
      or exists (select 1 from public.post_comments c join public.posts pp on pp.id = c.post_id
                  where c.user_id = other and (pp.user_id = auth.uid() or pp.visibility = 'public' or public.are_friends(auth.uid(), pp.user_id)))))
$$;
