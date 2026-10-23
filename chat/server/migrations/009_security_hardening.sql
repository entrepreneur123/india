-- Pre-launch hardening from the security audit (2026-10-19). Fixes that remove data or schema objects
-- (report foreign keys, retention purges, letting users remove their own files) need the owner's approval
-- and are listed in docs/NEXT_SESSION.md instead.

-- H1. Reports: no mass-banning with throwaway accounts.
-- Reporters are limited to 20 a day; the match must be theirs; room reports need a shared room in the last
-- 24 h; and the automatic 7-day ban only counts reporters whose accounts are 3+ days old and not banned.
create or replace function public.report_user(p_reported uuid, p_reason text, p_match uuid default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_reported is null or p_reported = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.reports r where r.reporter = me and r.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  if p_match is not null and not exists (select 1 from public.matches x where x.id = p_match
       and ((x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))) then
    p_match := null;
  end if;
  if not exists (select 1 from public.matches x where (x.user_a = me and x.user_b = p_reported) or (x.user_b = me and x.user_a = p_reported))
     and not public.can_view(p_reported)
     and not exists (select 1 from public.room_messages m join public.room_members rm on rm.room_id = m.room_id and rm.user_id = me
                      where m.sender = p_reported and m.created_at > now() - interval '24 hours') then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, match_id, reason) values (me, p_reported, p_match, p_reason);
  insert into public.blocks (blocker, blocked) values (me, p_reported) on conflict do nothing;
  update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
   where ended_at is null and ((user_a = me and user_b = p_reported) or (user_b = me and user_a = p_reported));
  if (select count(distinct r.reporter) from public.reports r join public.profiles rp on rp.id = r.reporter
       where r.reported = p_reported and r.created_at > now() - interval '24 hours'
         and rp.created_at < now() - interval '3 days' and (rp.banned_until is null or rp.banned_until < now())) >= 3 then
    update public.profiles set banned_until = greatest(coalesce(banned_until, now()), now() + interval '7 days') where id = p_reported;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none'
     where ended_at is null and p_reported in (user_a, user_b);
  end if;
end $$;

-- M1. Tables created after 001 inherited full table rights for anon/authenticated from the default ACL.
-- RLS already denies the rows; this removes the rights too, so a future policy mistake can't open them.
revoke all on public.purchases, public.banned_phones, public.dial_codes, public.friendships, public.room_members,
              public.room_codes, public.rooms, public.room_messages from anon, authenticated;
grant select on public.rooms, public.room_messages to authenticated;
grant insert (room_id, body) on public.room_messages to authenticated;
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke execute on functions from anon, authenticated, public;

-- M2. Room history only for the people in the room now (private rooms: their members and the host).
-- The apps join a room before they load its history.
create or replace function public.can_read_room(p_room text) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.rooms r where r.id = p_room and (r.owner = auth.uid()
    or exists (select 1 from public.room_members m where m.room_id = r.id and m.user_id = auth.uid()
                and (r.is_private or m.seen_at > now() - interval '90 seconds'))))
$$;

-- M3. Media paths must be in the uploader's own folder (no borrowing someone else's file to read it).
alter table public.messages add constraint messages_media_own
  check (media_path is null or media_path like sender::text || '/chat/%');
alter table public.posts add constraint posts_image_own
  check (image_path is null or image_path like user_id::text || '/posts/%');
alter table public.profiles add constraint profiles_photo_own
  check (photo_path is null or photo_path like id::text || '/avatar/%');

-- M6. Uploads only into <me>/avatar|chat|posts/<file>.
alter policy "upload own media" on storage.objects with check (
  bucket_id = 'media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (storage.foldername(name))[2] in ('avatar', 'chat', 'posts')
  and array_length(storage.foldername(name), 1) = 2);

-- M7. A closed room's code answers like a wrong code.
create or replace function public.join_room_code(p_code text) returns text language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); r public.rooms; why text;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  select x.* into r from public.rooms x join public.room_codes c on c.room_id = x.id
   where c.code = upper(btrim(coalesce(p_code, ''))) and x.closed_at is null;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  why := public.room_refusal(r, me);
  if why is not null then raise exception '%', why using errcode = 'P0001'; end if;
  insert into public.room_members (room_id, user_id) values (r.id, me)
  on conflict (room_id, user_id) do update set seen_at = now();
  return r.id;
end $$;

-- L2. Rate limits counted under a per-user lock, so parallel requests can't slip past them.
create or replace function public.message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform pg_advisory_xact_lock(hashtext('msg:' || new.sender::text));
  if exists (select 1 from public.profiles p where p.id = new.sender and p.banned_until > now()) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  if (select count(*) from public.messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return new;
end $$;

create or replace function public.room_message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
declare p public.profiles;
begin
  perform pg_advisory_xact_lock(hashtext('room:' || new.sender::text));
  select * into p from public.profiles where id = new.sender;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if p.banned_until > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if exists (select 1 from public.rooms r where r.id = new.room_id and r.closed_at is not null) then
    raise exception 'room_closed' using errcode = 'P0001';
  end if;
  if (select count(*) from public.room_messages m where m.sender = new.sender and m.created_at > now() - interval '10 seconds') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  new.sender_name := p.name;
  new.sender_gender := p.gender;
  new.sender_age := extract(year from age(p.birth_date))::int;
  new.sender_photo := p.photo_path;
  new.sender_country := p.country;
  new.created_at := now();
  return new;
end $$;

create or replace function public.request_friend(p_user uuid) returns text language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('friend:' || me::text));
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

-- L7. Photos and videos only in a live random chat (as the apps already do); threads stay text.
alter policy "write chat" on public.messages with check (
  sender = (select auth.uid()) and public.is_participant(match_id) and not public.chat_blocked(match_id)
  and (media_path is null or exists (select 1 from public.matches x where x.id = match_id and x.ended_at is null)));
