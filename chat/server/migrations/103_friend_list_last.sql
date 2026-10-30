-- Recent → Friends shows each friend's last message like a chat list (the owner, 2026-10-30: "a friend wrote to me,
-- Recent shows 1 in red, but not who wrote or what"). friend_list now also returns, for the pair's message thread
-- (kind 'dm', 083), the last line: its text, its media kind ('photo' / 'video' / 'audio'), whether I sent it, when.
-- Its match_id is that thread too: it used to be the pair's latest match of any kind, so after meeting a friend in a
-- random chat the row pointed at that chat and the thread's unread count (my_badges, by dm thread) never showed.
-- Friends with the newest line come first. The return type changes, so the function is dropped and made again.
-- Re-runnable.

drop function if exists public.friend_list();
create function public.friend_list()
returns table (state text, match_id uuid, id uuid, name text, gender text, age int, city text, bio text, photo_path text,
               country text, last_body text, last_kind text, last_mine boolean, last_at timestamptz)
language sql stable security definer set search_path = '' as $$
  select case when f.status = 'accepted' then 'friends' when f.requester = auth.uid() then 'requested' else 'incoming' end,
         t.id,
         p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path, p.country,
         l.body, l.media_kind, l.sender = auth.uid(), l.created_at
    from public.friendships f
    join public.profiles_seen p on p.id = case when f.requester = auth.uid() then f.addressee else f.requester end
    left join lateral (select x.id from public.matches x
                        where ((x.user_a = auth.uid() and x.user_b = p.id) or (x.user_b = auth.uid() and x.user_a = p.id))
                          and x.kind = 'dm'
                        order by x.created_at desc limit 1) t on true
    left join lateral (select m.body, m.media_kind, m.sender, m.created_at from public.messages m
                        where m.match_id = t.id order by m.id desc limit 1) l on true
   where auth.uid() in (f.requester, f.addressee) and not public.is_blocked(f.requester, f.addressee)
   order by l.created_at desc nulls last, f.created_at desc
   limit 500
$$;
revoke execute on function public.friend_list() from public, anon;
grant execute on function public.friend_list() to authenticated;
