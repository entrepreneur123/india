-- 10 (the owner, 2026-10-27): a random chat is its own thing, even with a friend. Until now a friend's thread was the
-- latest match of the pair of any kind (friend_thread), so meeting a friend in random chat made that random chat
-- their thread (and hid the old one). Now:
--  * a friend's thread is always the pair's message thread (kind 'dm'), made when first needed;
--  * a random chat is readable and writable only while it lasts: once it ends its lines are gone for both people
--    (kept in the database for reports — report_user and the admin read them as before);
--  * Recent lists the person only (no last line from a random chat), and no unread count comes from random chats;
--  * calls only in the friends' thread.

create or replace function public.friend_thread(p_user uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); mid uuid; friends boolean := public.are_friends(me, p_user);
begin
  if public.is_blocked(me, p_user)
     or not (friends or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone' and not x.anon)) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select x.id into mid from public.matches x
   where ((x.user_a = me and x.user_b = p_user) or (x.user_b = me and x.user_a = p_user)) and x.kind = 'dm'
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by) values (me, p_user, 'dm', now(), me) returning id into mid;
  end if;
  return mid;
end $$;

-- Writing: a live chat, or a message thread the pair may use.
create or replace function public.chat_open(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and not public.is_blocked(m.user_a, m.user_b)
                 and (m.ended_at is null
                      or (m.kind = 'dm' and (public.are_friends(m.user_a, m.user_b) or m.lfg
                          or exists (select 1 from public.profiles x where x.id in (m.user_a, m.user_b)
                                      and x.dm_from = 'everyone' and not x.anon)))))
$$;

-- Reading: my threads, and a random chat only while it lasts.
create or replace function public.chat_readable(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and (m.kind <> 'random' or m.ended_at is null))
$$;
revoke execute on function public.chat_readable(uuid) from public, anon;
grant execute on function public.chat_readable(uuid) to authenticated;

alter policy "read chat" on public.messages using (public.chat_readable(match_id));

create or replace function public.can_read_media(object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(object_name))[1] = auth.uid()::text then true
    when (storage.foldername(object_name))[1] !~ '^[0-9a-f-]{36}$' then false
    when (storage.foldername(object_name))[2] = 'cover'
         and exists (select 1 from public.voice_rooms r where r.cover_path = object_name and r.ended_at is null) then true
    when (storage.foldername(object_name))[2] in ('avatar', 'cover') then public.can_view(((storage.foldername(object_name))[1])::uuid)
          and not exists (select 1 from public.profiles x where x.id = ((storage.foldername(object_name))[1])::uuid and x.anon
                            and not public.are_friends(auth.uid(), x.id))
    when (storage.foldername(object_name))[2] = 'posts' then exists (
      select 1 from public.posts p where p.image_path = object_name and public.post_visible(p.user_id, p.visibility))
    when (storage.foldername(object_name))[2] = 'chat' then exists (
      select 1 from public.messages m join public.matches x on x.id = m.match_id
       where m.media_path = object_name and auth.uid() in (x.user_a, x.user_b)
         and (x.kind <> 'random' or x.ended_at is null)
         and not public.is_blocked(x.user_a, x.user_b))
    when (storage.foldername(object_name))[2] = 'rooms' then exists (
      select 1 from public.voice_messages v join public.voice_members vm on vm.room_id = v.room_id and vm.user_id = auth.uid()
       where v.media_path = object_name and not vm.kicked and (vm.left_at is null or vm.seen_at > now() - interval '1 day')
         and not public.blocked_with(v.sender))
    else false
  end or public.is_admin()
$$;

-- Recent: the person; a last line only from a message thread.
create or replace function public.my_recents()
returns table (match_id uuid, partner uuid, met_at timestamptz, last_body text)
language sql stable security definer set search_path = '' as $$
  select distinct on (o.partner) o.id, o.partner, o.created_at,
         case when o.kind = 'dm' then (select coalesce(msg.body, '[media]') from public.messages msg where msg.match_id = o.id order by msg.id desc limit 1) end
    from (select x.id, x.created_at, x.kind, case when x.user_a = auth.uid() then x.user_b else x.user_a end as partner
            from public.matches x where auth.uid() in (x.user_a, x.user_b) and x.kind in ('random', 'dm') and not x.anon) o
   where not public.is_blocked(auth.uid(), o.partner)
     and (o.kind = 'random' or not public.are_friends(auth.uid(), o.partner))
     and not exists (select 1 from public.recent_hidden h where h.user_id = auth.uid() and h.partner = o.partner and h.hidden_at > o.created_at)
   order by o.partner, o.created_at desc
$$;

create or replace function public.my_badges() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  insert into public.user_seen as s (user_id) values (me)
  on conflict (user_id) do update set seen_at = now() where s.seen_at < now() - interval '15 seconds';
  return jsonb_build_object(
    'inbox', (select count(*) from (select 1 from public.notifications o
               where o.user_id = me and o.read_at is null and not public.is_blocked(me, o.actor) limit 99) z),
    'friends', (select count(*) from public.friendships f where f.addressee = me and f.status = 'pending'
                 and not public.is_blocked(f.requester, me)),
    'chats', coalesce((select jsonb_object_agg(c.id, c.unread) from (
       select x.id, (select count(*) from (select 1 from public.messages m
                       where m.match_id = x.id and m.sender <> me
                         and m.id > coalesce(case when x.user_a = me then x.read_a else x.read_b end, 0) limit 99) z) as unread
         from (select y.* from public.matches y where (y.user_a = me or y.user_b = me) and y.kind = 'dm'
                 and (public.are_friends(y.user_a, y.user_b)
                      or y.id in (select r.match_id from public.my_recents() r order by r.met_at desc limit 50))
                order by y.created_at desc limit 100) x
        where not public.is_blocked(x.user_a, x.user_b)) c where c.unread > 0), '{}'::jsonb));
end $$;

create or replace function public.call_open(p_match uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches x where x.id = p_match and auth.uid() in (x.user_a, x.user_b)
                   and x.kind = 'dm' and not public.is_blocked(x.user_a, x.user_b)
                   and x.ended_at is not null and not x.anon and public.are_friends(x.user_a, x.user_b))
$$;
