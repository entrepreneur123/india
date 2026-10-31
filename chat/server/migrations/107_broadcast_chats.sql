-- Connections that scale (docs/CAPACITY.md 1, the owner 2026-10-31): chats, match changes, group lines and the
-- notification count reach the web app over Broadcast from triggers (realtime.send), as voice rooms do since 020,
-- instead of Postgres Changes, which checks every change against every subscriber's RLS on one thread.
-- Private topics, readable by exactly who may read the rows today:
--   chat:<match id>  - the match's two people while chat_readable (messages, match changes)
--   user:<user id>   - that person only (a new match to wake the search, group lines, the notification count)
-- The tables stay published for Postgres Changes (older apps). Room chat lines stay on Postgres Changes: their read
-- rule hides lines between people who blocked each other, which one shared topic cannot do.
-- Re-runnable.

create or replace function public.rt_topic_ok(p_topic text) returns boolean
language sql stable security definer set search_path = '' as $$
  select case
    when p_topic = 'user:' || auth.uid()::text then true
    when p_topic ~ '^chat:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then public.chat_readable(substr(p_topic, 6)::uuid)
    else false
  end
$$;
revoke execute on function public.rt_topic_ok(text) from public, anon;
grant execute on function public.rt_topic_ok(text) to authenticated;

drop policy if exists "chat and user topics" on realtime.messages;
create policy "chat and user topics" on realtime.messages for select to authenticated
  using (extension = 'broadcast' and public.rt_topic_ok(realtime.topic()));

-- A message: the whole row (both people may read it all), to the match's topic.
create or replace function public.messages_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform realtime.send(to_jsonb(new), 'msg', 'chat:' || new.match_id::text, true);
  return null;
end $$;
drop trigger if exists messages_rt on public.messages;
create trigger messages_rt after insert on public.messages for each row execute function public.messages_rt();

-- A match: what the chat follows (ended, video, read marks) when it changes; a new one wakes the waiting side.
create or replace function public.matches_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    perform realtime.send('{}'::jsonb, 'wake', 'user:' || new.user_a::text, true);
  else
    perform realtime.send(jsonb_build_object(
      'id', new.id, 'user_a', new.user_a, 'user_b', new.user_b, 'ended_at', new.ended_at, 'ended_by', new.ended_by,
      'video_status', new.video_status, 'video_requested_by', new.video_requested_by, 'call_video', new.call_video,
      'read_a', new.read_a, 'read_b', new.read_b), 'match', 'chat:' || new.id::text, true);
  end if;
  return null;
end $$;
drop trigger if exists matches_rt_new on public.matches;
create trigger matches_rt_new after insert on public.matches for each row execute function public.matches_rt();
drop trigger if exists matches_rt on public.matches;
create trigger matches_rt after update on public.matches for each row
  when (old.ended_at is distinct from new.ended_at or old.ended_by is distinct from new.ended_by
        or old.video_status is distinct from new.video_status or old.video_requested_by is distinct from new.video_requested_by
        or old.call_video is distinct from new.call_video or old.read_a is distinct from new.read_a or old.read_b is distinct from new.read_b)
  execute function public.matches_rt();

-- A group line: to each member here now (all of them joined before it, so group_reads lets them read it).
create or replace function public.group_messages_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
declare u uuid;
begin
  for u in select m.user_id from public.group_members m where m.group_id = new.group_id and m.left_at is null loop
    perform realtime.send(to_jsonb(new), 'g', 'user:' || u::text, true);
  end loop;
  return null;
end $$;
drop trigger if exists group_messages_rt on public.group_messages;
create trigger group_messages_rt after insert on public.group_messages for each row execute function public.group_messages_rt();

-- Notifications: once per statement and person (marking 50 read is one signal), no content (the app reads them).
create or replace function public.notifications_rt() returns trigger
language plpgsql security definer set search_path = '' as $$
declare u uuid;
begin
  for u in select distinct x.user_id from changed x loop
    perform realtime.send('{}'::jsonb, 'n', 'user:' || u::text, true);
  end loop;
  return null;
end $$;
drop trigger if exists notifications_rt_ins on public.notifications;
create trigger notifications_rt_ins after insert on public.notifications referencing new table as changed
  for each statement execute function public.notifications_rt();
drop trigger if exists notifications_rt_upd on public.notifications;
create trigger notifications_rt_upd after update on public.notifications referencing new table as changed
  for each statement execute function public.notifications_rt();
drop trigger if exists notifications_rt_del on public.notifications;
create trigger notifications_rt_del after delete on public.notifications referencing old table as changed
  for each statement execute function public.notifications_rt();
