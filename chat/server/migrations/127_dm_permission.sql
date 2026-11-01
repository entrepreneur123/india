-- The owner, 2026-11-01: "messages from anyone" is on but a non-friend's DM doesn't go through; and where I may not
-- message someone, there is no message button.
-- The server already let it through (friend_thread, the message policy); the web's thread view kept its own copy of
-- 040's "an ended chat goes on only between friends", and a DM thread is born ended (059), so a non-friend always got the
-- friends bar. The web now asks chat_open (this function), the rule the message policy checks.
-- chat_open's DM part counted EITHER person's dm_from (075), so someone who takes messages from anyone could keep writing
-- to a person who had switched to friends only or to VIP anonymity, while the profile (profile_social2) and
-- friend_thread already said no. Now it is the other person's: friends, an LFG chat, they take messages from anyone (not
-- anonymous in VIP, 066), or they have written in this thread themselves (a reply accepts the conversation, as a message
-- request). Same signature and type: the message policy and the current web keep working.
create or replace function public.chat_open(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m
                 cross join lateral (select case when m.user_a = auth.uid() then m.user_b else m.user_a end as other) o
                 where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                   and not public.is_blocked(m.user_a, m.user_b)
                   and (m.ended_at is null
                        or (m.kind = 'dm' and (public.are_friends(m.user_a, m.user_b) or m.lfg
                            or exists (select 1 from public.profiles x where x.id = o.other and x.dm_from = 'everyone' and not x.anon)
                            or exists (select 1 from public.messages x where x.match_id = m.id and x.sender = o.other)))))
$$;
