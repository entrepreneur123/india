-- Notifications: the in-app inbox, unread badges and phone push (FCM), the last switched on by secrets only.
--  * notifications: one row per thing to tell someone. Written by triggers (a friend's message, a friend request,
--    an accepted request) and by invite_to_room (024). A friend's messages fold into one unread row per chat
--    (n counts them), so a busy chat is one line in the inbox and one push at most every 30 s.
--  * device_tokens: FCM tokens the apps register (Android, later). No token = no push: until the app sends tokens
--    nothing leaves the database.
--  * user_seen: when someone last had the app open (my_badges() stamps it). Online people get no push.
--  * push_dispatch: an online check, the 30 s per-chat limit and quiet hours (23:00-08:00 in the person's own time
--    zone, friend requests excepted), then pg_net calls the edge function `push` with a key that lives only in the
--    vault. `push` sends nothing (200 {skipped:true}) while the secret FCM_SERVICE_ACCOUNT is not set.
--  * Pushes carry ids and the sender's name only, never message text; the app writes the sentence in its language.
-- Deletes (old rows) are in owner_run_once_4.sql: the nightly purge removes read notifications after 90 days and
-- tokens no longer in use after 30 days.

-- ---------- who is around ----------
create table if not exists public.user_seen (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  seen_at timestamptz not null default now()
) with (fillfactor = 70); -- one row per person, updated in place (HOT)
alter table public.user_seen enable row level security; -- functions only

-- Has the app open somewhere right now: the web/app stamp (my_badges), the random-chat line or a room heartbeat.
create or replace function public.user_online(u uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.user_seen s where s.user_id = u and s.seen_at > now() - interval '60 seconds')
      or exists (select 1 from public.queue q where q.user_id = u and q.seen_at > now() - interval '60 seconds')
      or exists (select 1 from public.voice_members m where m.user_id = u and m.left_at is null and not m.kicked
                   and m.seen_at > now() - interval '60 seconds')
$$;

-- ---------- devices ----------
create table if not exists public.device_tokens (
  token text primary key check (char_length(token) between 20 and 4096),
  user_id uuid not null references public.profiles (id) on delete cascade,
  platform text not null check (platform in ('android', 'ios', 'web')),
  locale text check (char_length(locale) <= 20),
  tz text check (char_length(tz) <= 64),
  active boolean not null default true,
  updated_at timestamptz not null default now()
);
create index if not exists device_tokens_user on public.device_tokens (user_id) where active;
alter table public.device_tokens enable row level security; -- functions only

-- The app calls this at start and whenever FCM hands it a new token. A token moves to whoever signs in on that
-- phone. At most 10 phones per person stay active (the oldest stop getting pushes).
create or replace function public.register_device(p_token text, p_platform text, p_locale text default null, p_tz text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); zone text;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if char_length(coalesce(p_token, '')) not between 20 and 4096 or coalesce(p_platform, '') not in ('android', 'ios', 'web') then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  select z.name into zone from pg_catalog.pg_timezone_names z where z.name = p_tz limit 1;
  insert into public.device_tokens as d (token, user_id, platform, locale, tz)
  values (p_token, me, p_platform, left(p_locale, 20), zone)
  on conflict (token) do update set user_id = me, platform = excluded.platform, locale = excluded.locale,
    tz = coalesce(excluded.tz, d.tz), active = true, updated_at = now();
  update public.device_tokens d set active = false
   where d.user_id = me and d.active
     and d.token not in (select x.token from public.device_tokens x where x.user_id = me and x.active
                          order by x.updated_at desc limit 10);
end $$;

-- Sign-out on that phone, or the user turned notifications off.
create or replace function public.unregister_device(p_token text) returns void
language sql security definer set search_path = '' as $$
  update public.device_tokens set active = false, updated_at = now() where token = p_token and user_id = auth.uid()
$$;

-- ---------- the inbox ----------
create table if not exists public.notifications (
  id bigint generated always as identity primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  kind text not null check (kind in ('message', 'friend_request', 'friend_accept', 'room_invite')),
  actor uuid not null references public.profiles (id) on delete cascade,
  match_id uuid references public.matches (id) on delete cascade,
  room_id text references public.voice_rooms (id) on delete cascade,
  n int not null default 1,          -- messages folded into this row
  created_at timestamptz not null default now(),
  read_at timestamptz,
  pushed_at timestamptz
);
create index if not exists notifications_user on public.notifications (user_id, id desc);
create index if not exists notifications_actor on public.notifications (actor);
create index if not exists notifications_match on public.notifications (match_id) where match_id is not null;
create index if not exists notifications_room on public.notifications (room_id) where room_id is not null;
create index if not exists notifications_unread on public.notifications (user_id) where read_at is null;
-- One unread row per chat, and one unread request/acceptance per person.
create unique index if not exists notifications_chat on public.notifications (user_id, match_id) where kind = 'message' and read_at is null;
create unique index if not exists notifications_once on public.notifications (user_id, kind, actor)
  where read_at is null and kind in ('friend_request', 'friend_accept');
alter table public.notifications enable row level security;
create policy "my notifications" on public.notifications for select to authenticated using (user_id = (select auth.uid()));
grant select on public.notifications to authenticated; -- read through realtime (the badge); the inbox uses inbox()
alter publication supabase_realtime add table public.notifications;

-- A friend's message: one unread row per chat, its count going up. Random chats with strangers never notify.
create or replace function public.notify_message() returns trigger
language plpgsql security definer set search_path = '' as $$
declare x public.matches; them uuid;
begin
  select * into x from public.matches m where m.id = new.match_id;
  them := case when x.user_a = new.sender then x.user_b else x.user_a end;
  if them is null or not public.are_friends(new.sender, them) or public.is_blocked(new.sender, them) then return null; end if;
  insert into public.notifications as o (user_id, kind, actor, match_id)
  values (them, 'message', new.sender, new.match_id)
  on conflict (user_id, match_id) where kind = 'message' and read_at is null
  do update set n = o.n + 1, created_at = now();
  return null;
end $$;
create or replace trigger messages_notify after insert on public.messages for each row execute function public.notify_message();

-- Reading the chat (mark_read, 015) clears its inbox row.
create or replace function public.notify_chat_read() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  update public.notifications o set read_at = now()
   where o.match_id = new.id and o.kind = 'message' and o.read_at is null
     and o.user_id = case when new.read_a is distinct from old.read_a then new.user_a else new.user_b end;
  return null;
end $$;
create or replace trigger matches_read_notify after update of read_a, read_b on public.matches
  for each row when (old.read_a is distinct from new.read_a or old.read_b is distinct from new.read_b)
  execute function public.notify_chat_read();

-- A request (at most one a day from the same person), and its acceptance.
create or replace function public.notify_friend() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' and new.status = 'pending' then
    if not exists (select 1 from public.notifications o where o.user_id = new.addressee and o.actor = new.requester
                     and o.kind = 'friend_request' and o.created_at > now() - interval '1 day') then
      insert into public.notifications (user_id, kind, actor) values (new.addressee, 'friend_request', new.requester)
      on conflict (user_id, kind, actor) where read_at is null and kind in ('friend_request', 'friend_accept') do nothing;
    end if;
  elsif tg_op = 'UPDATE' and old.status = 'pending' and new.status = 'accepted' then
    update public.notifications set read_at = now()
     where user_id = new.addressee and actor = new.requester and kind = 'friend_request' and read_at is null;
    insert into public.notifications (user_id, kind, actor) values (new.requester, 'friend_accept', new.addressee)
    on conflict (user_id, kind, actor) where read_at is null and kind in ('friend_request', 'friend_accept') do nothing;
  end if;
  return null;
end $$;
create or replace trigger friendships_notify after insert or update of status on public.friendships
  for each row execute function public.notify_friend();

-- My inbox, newest first, 30 at a time. `pending`: a friend request still waiting for my answer.
create or replace function public.inbox(p_before bigint default null, p_limit integer default 30)
returns table (id bigint, kind text, n integer, created_at timestamptz, read_at timestamptz, match_id uuid, room_id text,
               room_title text, room_open boolean, pending boolean, actor jsonb)
language sql stable security definer set search_path = '' as $$
  select o.id, o.kind, o.n, o.created_at, o.read_at, o.match_id, o.room_id,
         r.title, r.id is not null and r.ended_at is null and public.voice_alive(r),
         o.kind = 'friend_request' and exists (select 1 from public.friendships f
           where f.requester = o.actor and f.addressee = o.user_id and f.status = 'pending'),
         jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender, 'age', extract(year from age(p.birth_date))::int,
           'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path, 'country', p.country)
    from public.notifications o
    join public.profiles p on p.id = o.actor
    left join public.voice_rooms r on r.id = o.room_id
   where o.user_id = auth.uid() and (p_before is null or o.id < p_before)
     and not public.is_blocked(o.user_id, o.actor)
   order by o.id desc
   limit least(greatest(coalesce(p_limit, 30), 1), 100)
$$;

-- Marks these (or, with null, all of mine) read.
create or replace function public.inbox_read(p_ids bigint[] default null) returns void
language sql security definer set search_path = '' as $$
  update public.notifications o set read_at = now()
   where o.user_id = auth.uid() and o.read_at is null and (p_ids is null or o.id = any (p_ids))
$$;

-- The badges, and "I'm here" (no pushes while the app is open). The apps call it at start, when a notification
-- arrives over realtime and every 30 s while on screen.
--   inbox:   unread inbox rows (capped at 99)
--   chats:   {match id: unread messages} for my friends' chats and my random chats of the last 30 days (100 newest)
--   friends: incoming friend requests
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
         from (select y.* from public.matches y where (y.user_a = me or y.user_b = me)
                 and (y.created_at > now() - interval '30 days' or y.kind = 'dm')
                order by y.created_at desc limit 100) x
        where not public.is_blocked(x.user_a, x.user_b)) c where c.unread > 0), '{}'::jsonb));
end $$;

-- ---------- push ----------
select vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'edge_call_key')
 where not exists (select 1 from vault.secrets where name = 'edge_call_key');
create or replace function public.edge_key_ok(p_key text) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(p_key = (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'edge_call_key'), false)
$$;

-- Decides whether this notification goes to the phone now, and if so asks `push` to send it.
create or replace function public.push_dispatch() returns trigger
language plpgsql security definer set search_path = '' as $$
declare zone text; hr int;
begin
  if new.read_at is not null then return null; end if;
  select d.tz into zone from public.device_tokens d where d.user_id = new.user_id and d.active order by d.updated_at desc limit 1;
  if not found then return null; end if;                            -- no phone registered: nothing to do
  if new.pushed_at > now() - interval '30 seconds' then return null; end if; -- this chat had a push just now
  if public.user_online(new.user_id) then return null; end if;      -- they have the app open
  if new.kind <> 'friend_request' then
    hr := extract(hour from now() at time zone coalesce(zone, 'Asia/Kolkata'));
    if hr >= 23 or hr < 8 then return null; end if;                 -- quiet hours, their own time
  end if;
  update public.notifications set pushed_at = now() where id = new.id;
  perform net.http_post(
    url := 'https://dgynsckrqgnpyyvqekzi.supabase.co/functions/v1/push',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-edge-key', (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'edge_call_key')),
    body := jsonb_build_object('id', new.id), timeout_milliseconds := 10000);
  return null;
end $$;
create or replace trigger notifications_push after insert or update of n on public.notifications
  for each row execute function public.push_dispatch();

-- What `push` sends: ids, the kind and the sender's name (the app writes the sentence), and where to.
create or replace function public.push_payload(p_id bigint) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('id', o.id, 'kind', o.kind, 'n', o.n, 'actor', o.actor, 'actor_name', p.name,
           'match', o.match_id, 'room', o.room_id, 'read', o.read_at is not null,
           'tokens', coalesce((select jsonb_agg(jsonb_build_object('token', d.token, 'platform', d.platform, 'locale', d.locale))
                                 from public.device_tokens d where d.user_id = o.user_id and d.active), '[]'))
    from public.notifications o join public.profiles p on p.id = o.actor where o.id = p_id
$$;

-- FCM said the token is gone (app uninstalled).
create or replace function public.push_token_dead(p_token text) returns void
language sql security definer set search_path = '' as $$
  update public.device_tokens set active = false, updated_at = now() where token = p_token
$$;

revoke execute on function public.user_online(uuid), public.notify_message(), public.notify_chat_read(), public.notify_friend(),
  public.edge_key_ok(text), public.push_dispatch(), public.push_payload(bigint), public.push_token_dead(text)
  from public, anon, authenticated;
grant execute on function public.edge_key_ok(text), public.push_payload(bigint), public.push_token_dead(text) to service_role;
revoke execute on function public.register_device(text, text, text, text), public.unregister_device(text),
  public.inbox(bigint, integer), public.inbox_read(bigint[]), public.my_badges() from public, anon;
grant execute on function public.register_device(text, text, text, text), public.unregister_device(text),
  public.inbox(bigint, integer), public.inbox_read(bigint[]), public.my_badges() to authenticated;
