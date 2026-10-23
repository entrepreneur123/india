-- Unread counts only for chats I can open: a number nobody can clear stayed on the Recent tab. Counted now: the
-- random chats in my Recent list (latest per person, not cleared, latest 50 — as recent_list shows them) and DM
-- threads with people who are still my friends. Older chats with the same person, cleared ones and ex-friends' threads
-- no longer count.
do $$
declare d text := pg_get_functiondef('public.my_badges'::regproc); n text;
begin
  n := replace(d,
    'from (select y.* from public.matches y where (y.user_a = me or y.user_b = me)
                 and (y.created_at > now() - interval ''30 days'' or y.kind = ''dm'')
                order by y.created_at desc limit 100) x',
    'from (select y.* from public.matches y where (y.user_a = me or y.user_b = me)
                 and ((y.kind = ''dm'' and public.are_friends(y.user_a, y.user_b))
                      or y.id in (select r.match_id from public.my_recents() r order by r.met_at desc limit 50))
                order by y.created_at desc limit 100) x');
  if n = d then raise exception 'my_badges: text not found'; end if;
  execute n;
end $$;
