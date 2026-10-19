-- Social layer: friends, public rooms, and private threads between friends.
-- Profiles stay private by default; they become visible to people you matched with (as before),
-- your friends (and people asking to be), and people in the same public room as you right now.

-- ---------- friends ----------
create table public.friendships (
  requester uuid not null references public.profiles (id) on delete cascade,
  addressee uuid not null references public.profiles (id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  check (requester <> addressee),
  primary key (requester, addressee)
);
create index friendships_addressee on public.friendships (addressee);
alter table public.friendships enable row level security; -- only through the functions below

create function public.are_friends(x uuid, y uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.friendships f where f.status = 'accepted'
                 and ((f.requester = x and f.addressee = y) or (f.requester = y and f.addressee = x)))
$$;

-- ---------- public rooms ----------
create table public.rooms (
  id text primary key,                 -- slug; the app shows a translated title for known slugs
  title text not null,
  emoji text not null default '💬',
  sort int not null default 0
);
alter table public.rooms enable row level security;
create policy "rooms are public" on public.rooms for select to authenticated using (true);
insert into public.rooms (id, title, emoji, sort) values
  ('hindi', 'Hindi Chit-Chat', '🗣️', 1),
  ('english', 'English Lounge', '☕', 2),
  ('cricket', 'Cricket Corner', '🏏', 3),
  ('bollywood', 'Bollywood & Music', '🎬', 4),
  ('night', 'Late Night Talks', '🌙', 5),
  ('campus', 'College & Campus', '🎓', 6),
  ('gaming', 'Gaming', '🎮', 7),
  ('memes', 'Desi Memes', '😂', 8);

-- Who is in a room now: the app refreshes seen_at every 30 s while the room is open.
create table public.room_members (
  room_id text not null references public.rooms (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  seen_at timestamptz not null default now(),
  primary key (room_id, user_id)
);
create index room_members_user on public.room_members (user_id);
alter table public.room_members enable row level security; -- only through the functions below

-- RLS helpers: they only answer about the caller.
create function public.am_in_room(r text) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.room_members m where m.room_id = r and m.user_id = auth.uid() and m.seen_at > now() - interval '90 seconds')
$$;
create function public.blocked_with(other uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select public.is_blocked(auth.uid(), other)
$$;

-- Sender details are copied in, so a realtime insert carries everything the app shows.
create table public.room_messages (
  id bigint generated always as identity primary key,
  room_id text not null references public.rooms (id) on delete cascade,
  sender uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  sender_name text not null default '',
  sender_gender text not null default 'other',
  sender_age int not null default 18,
  sender_photo text,
  body text not null check (char_length(btrim(body)) between 1 and 500),
  created_at timestamptz not null default now()
);
create index room_messages_room on public.room_messages (room_id, id desc);
create index room_messages_sender_time on public.room_messages (sender, created_at desc);
alter table public.room_messages enable row level security;
create policy "read room" on public.room_messages for select to authenticated
  using (not public.blocked_with(sender));
create policy "write room" on public.room_messages for insert to authenticated
  with check (sender = (select auth.uid()) and public.am_in_room(room_id));
grant select on public.rooms, public.room_messages to authenticated;
grant insert (room_id, body) on public.room_messages to authenticated;

create function public.room_message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
declare p public.profiles;
begin
  select * into p from public.profiles where id = new.sender;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if p.banned_until > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if (select count(*) from public.room_messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  new.sender_name := p.name;
  new.sender_gender := p.gender;
  new.sender_age := extract(year from age(p.birth_date))::int;
  new.sender_photo := p.photo_path;
  new.created_at := now();
  return new;
end $$;
create trigger room_messages_guard before insert on public.room_messages for each row execute function public.room_message_guard();

-- ---------- visibility ----------
-- Matched with, a friend (or asking to be one), or in the same room right now; never across a block.
create or replace function public.can_view(other uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.matches m
               where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other))
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid())
                     or (f.requester = auth.uid() and f.addressee = other and f.status = 'accepted'))
      or exists (select 1 from public.room_members a join public.room_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')))
$$;

-- ---------- private threads between friends ----------
-- Matches gain a kind: 'random' (from the queue) or 'dm' (a thread between friends who have no chat yet).
alter table public.matches add column kind text not null default 'random' check (kind in ('random', 'dm'));

-- Recent shows only people met through random chat.
create or replace function public.my_recents()
returns table (match_id uuid, partner uuid, met_at timestamptz, last_body text)
language sql stable security definer set search_path = '' as $$
  select distinct on (o.partner) o.id, o.partner, o.created_at,
         (select coalesce(msg.body, '[media]') from public.messages msg where msg.match_id = o.id order by msg.id desc limit 1)
    from (select x.id, x.created_at, case when x.user_a = auth.uid() then x.user_b else x.user_a end as partner
            from public.matches x where auth.uid() in (x.user_a, x.user_b) and x.kind = 'random') o
   where not public.is_blocked(auth.uid(), o.partner)
   order by o.partner, o.created_at desc
$$;

-- ---------- RPCs ----------
create function public.room_list() returns table (id text, title text, emoji text, online int)
language sql stable security definer set search_path = '' as $$
  select r.id, r.title, r.emoji,
         (select count(*)::int from public.room_members m where m.room_id = r.id and m.seen_at > now() - interval '90 seconds')
    from public.rooms r order by r.sort
$$;

create function public.join_room(p_room text) returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.rooms r where r.id = p_room) then raise exception 'not_found' using errcode = 'P0001'; end if;
  insert into public.room_members (room_id, user_id) values (p_room, me)
  on conflict (room_id, user_id) do update set seen_at = now();
end $$;

create function public.leave_room(p_room text) returns void language sql security definer set search_path = '' as $$
  delete from public.room_members where room_id = p_room and user_id = auth.uid()
$$;

-- Reports: besides people you matched with, anyone whose profile you can see or who wrote in a room.
create or replace function public.report_user(p_reported uuid, p_reason text, p_match uuid default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_reported = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.matches x where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))
     and not public.can_view(p_reported)
     and not exists (select 1 from public.room_messages m where m.sender = p_reported) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, match_id, reason) values (me, p_reported, p_match, p_reason);
  insert into public.blocks (blocker, blocked) values (me, p_reported) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_reported) or (user_b = me and user_a = p_reported));
  -- Automatic first response (well inside the 2-hour window): 3 different reporters in 24 h = 7-day ban pending review.
  if (select count(distinct r.reporter) from public.reports r where r.reported = p_reported and r.created_at > now() - interval '24 hours') >= 3 then
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), now() + interval '7 days') where id = p_reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and p_reported in (user_a, user_b);
  end if;
end $$;

-- A report from a chat or profile also ends the friendship.
create function public.unfriend_on_block() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  delete from public.friendships where (requester = new.blocker and addressee = new.blocked) or (requester = new.blocked and addressee = new.blocker);
  return new;
end $$;
create trigger blocks_unfriend after insert on public.blocks for each row execute function public.unfriend_on_block();

-- Send a request (or accept theirs if they already asked). Only to people you may see.
create function public.request_friend(p_user uuid) returns text language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if not public.can_view(p_user) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if public.are_friends(me, p_user) then return 'friends'; end if;
  update public.friendships set status = 'accepted' where requester = p_user and addressee = me;
  if found then return 'friends'; end if;
  if (select count(*) from public.friendships f where f.requester = me and f.status = 'pending'
        and f.created_at > now() - interval '1 day') >= 50 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.friendships (requester, addressee) values (me, p_user) on conflict do nothing;
  return 'requested';
end $$;

create function public.answer_friend(p_user uuid, p_accept boolean) returns void language plpgsql security definer set search_path = '' as $$
begin
  if p_accept then
    update public.friendships set status = 'accepted' where requester = p_user and addressee = auth.uid() and status = 'pending';
  else
    delete from public.friendships where requester = p_user and addressee = auth.uid() and status = 'pending';
  end if;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
end $$;

-- Unfriend, or withdraw a request I sent.
create function public.remove_friend(p_user uuid) returns void language sql security definer set search_path = '' as $$
  delete from public.friendships
   where (requester = auth.uid() and addressee = p_user) or (requester = p_user and addressee = auth.uid() and status = 'accepted')
$$;

-- Friends and pending requests, with the chat to open for a message (null until one exists).
create function public.friend_cards()
returns table (state text, match_id uuid, id uuid, name text, gender text, age int, city text, bio text, photo_path text)
language sql stable security definer set search_path = '' as $$
  select case when f.status = 'accepted' then 'friends' when f.requester = auth.uid() then 'requested' else 'incoming' end,
         (select x.id from public.matches x
           where (x.user_a = auth.uid() and x.user_b = p.id) or (x.user_b = auth.uid() and x.user_a = p.id)
           order by x.created_at desc limit 1),
         p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.city, p.bio, p.photo_path
    from public.friendships f
    join public.profiles p on p.id = case when f.requester = auth.uid() then f.addressee else f.requester end
   where auth.uid() in (f.requester, f.addressee) and not public.is_blocked(f.requester, f.addressee)
   order by f.created_at desc
   limit 500
$$;

-- The thread to message a friend in: their latest chat, or a new private one.
create function public.friend_thread(p_user uuid) returns uuid language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); mid uuid;
begin
  if not public.are_friends(me, p_user) or public.is_blocked(me, p_user) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  select x.id into mid from public.matches x
   where (x.user_a = me and x.user_b = p_user) or (x.user_b = me and x.user_a = p_user)
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by) values (me, p_user, 'dm', now(), me) returning id into mid;
  end if;
  return mid;
end $$;

revoke execute on function public.are_friends(uuid, uuid), public.room_message_guard(), public.unfriend_on_block()
  from public, anon, authenticated;
revoke execute on function public.am_in_room(text), public.blocked_with(uuid) from public, anon;
grant execute on function public.am_in_room(text), public.blocked_with(uuid) to authenticated; -- used by the policies
revoke execute on function public.room_list(), public.join_room(text), public.leave_room(text),
  public.request_friend(uuid), public.answer_friend(uuid, boolean),
  public.remove_friend(uuid), public.friend_cards(), public.friend_thread(uuid) from public, anon;
grant execute on function public.room_list(), public.join_room(text), public.leave_room(text),
  public.request_friend(uuid), public.answer_friend(uuid, boolean),
  public.remove_friend(uuid), public.friend_cards(), public.friend_thread(uuid) to authenticated;

alter publication supabase_realtime add table public.room_messages;
