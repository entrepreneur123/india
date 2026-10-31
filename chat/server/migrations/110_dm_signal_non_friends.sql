-- 108's 'c' (a new DM message, to the other person's topic) only where no notification says it: a friend's message
-- already updates a 'message' notification (notify_message), whose 'n' (107) refreshes the counts at once; a second
-- signal made the web read my_badges twice per message (seen live: 'n' answered in ~0.3 s, before 'c''s 1 s wait).
-- Messages from people who aren't friends (DMs from anyone, 097) still get 'c'. Re-runnable.

create or replace function public.messages_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
declare other uuid;
begin
  perform realtime.send(to_jsonb(new), 'msg', 'chat:' || new.match_id::text, true);
  select case when x.user_a = new.sender then x.user_b else x.user_a end into other
    from public.matches x where x.id = new.match_id and x.kind = 'dm';
  if other is not null and not public.are_friends(other, new.sender) and not public.is_blocked(other, new.sender) then
    perform realtime.send(jsonb_build_object('m', new.match_id), 'c', 'user:' || other::text, true);
  end if;
  return null;
end $$;
