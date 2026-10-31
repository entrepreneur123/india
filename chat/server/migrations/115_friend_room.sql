-- "Join the room my friend is in" (the owner, 2026-10-31, as a game's friends list): friend_activity2 adds the room
-- a friend in 'room' presence is in and whether it is private (a private room still needs its code, as from the
-- list). friend_activity (067) keeps its shape for the Android app until its next release; drop it then.
-- Re-runnable.

create or replace function public.friend_activity2()
returns table (id uuid, presence text, status text, status_at timestamptz, room_id text, room_private boolean)
language sql stable security definer set search_path = '' as $$
  select f.id,
         case
           when p.hide_online or not public.user_online(f.id) then 'offline'
           when exists (select 1 from public.matches x where x.user_a = f.id and x.ended_at is null and x.kind = 'random'
                          and x.created_at > now() - interval '6 hours')
             or exists (select 1 from public.matches x where x.user_b = f.id and x.ended_at is null and x.kind = 'random'
                          and x.created_at > now() - interval '6 hours') then 'chat'
           when exists (select 1 from public.queue q where q.user_id = f.id and q.seen_at > now() - interval '20 seconds') then 'matching'
           when r.id is not null then 'room'
           else 'online'
         end,
         nullif(btrim(coalesce(p.status, '')), ''), p.status_at,
         case when p.hide_online or not public.user_online(f.id) then null else r.id end,
         case when p.hide_online or not public.user_online(f.id) then null else r.is_private end
    from (select case when fr.requester = auth.uid() then fr.addressee else fr.requester end as id
            from public.friendships fr
           where auth.uid() in (fr.requester, fr.addressee) and fr.status = 'accepted' and not public.is_blocked(fr.requester, fr.addressee)
           limit 500) f
    join public.profiles p on p.id = f.id
    left join lateral (select x.id, x.is_private from public.voice_members m join public.voice_rooms x on x.id = m.room_id
                        where m.user_id = f.id and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds'
                          and x.ended_at is null
                        order by m.joined_at desc limit 1) r on true
$$;
revoke execute on function public.friend_activity2() from public, anon;
grant execute on function public.friend_activity2() to authenticated;
