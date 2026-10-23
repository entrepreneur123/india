-- Room powers (the owner): the host and co-hosts run the room; everyone else controls only their own screen.
--
-- 1. Music: only the host or a co-host plays, pauses, resumes, skips and seeks (voice_seek, new). Others still
--    suggest (3 at a time) and vote; pausing, volume and quality on their own screen are client-only.
--    A co-host adding a song when nothing plays starts it, like the host.
-- 2. Kick: the host removes anyone; a co-host removes listeners and speakers (not the host, not another co-host).
--    Off the stage (voice_to_audience): the same rule. Inviting onto the stage stays host + co-hosts (voice_mod).
-- 3. Shared link ("stage"): the host or a co-host pins a web page next to the chat for everyone; null clears it.
--    voice_state_of carries it as 'stage'.
-- 4. Typing in rooms needs no server change: clients use a public 'vtyping-<room>' channel, like random chat.
-- Function bodies are patched in place where only a line changes (each patch checks its anchor).

-- 1. Music controls: host or co-host.
create or replace function public.voice_dj(p_room text) returns public.voice_rooms
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); r public.voice_rooms;
begin
  if m.role not in ('host', 'cohost') then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  select * into r from public.voice_rooms where id = p_room;
  return r;
end $$;

-- Jump to p_ms into the current song (paused songs stay paused at the new place).
create or replace function public.voice_seek(p_room text, p_ms bigint) returns void
language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room); now_ms bigint := public.voice_ms(); at bigint;
begin
  if r.music is null or p_ms is null or p_ms < 0 or p_ms > 86400000 then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  at := case when r.music->'pausedAt' = 'null' then now_ms else (r.music->>'pausedAt')::bigint end;
  update public.voice_rooms set rev = rev + 1, music = music || jsonb_build_object('startedAt', at - p_ms)
   where id = p_room;
end $$;
revoke execute on function public.voice_seek(text, bigint) from public, anon;
grant execute on function public.voice_seek(text, bigint) to authenticated;

do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.voice_queue_add(text, text, text)'::regprocedure);
  a := 'if m.role = ''host'' and r.music is null then';
  if position(a in d) = 0 then raise exception 'voice_queue_add: anchor 1 not found'; end if;
  d := replace(d, a, 'if m.role in (''host'', ''cohost'') and r.music is null then');
  a := 'if m.role <> ''host'' and (select count(*)';
  if position(a in d) = 0 then raise exception 'voice_queue_add: anchor 2 not found'; end if;
  d := replace(d, a, 'if m.role not in (''host'', ''cohost'') and (select count(*)');
  a := 'return case when m.role = ''host'' then ''queued'' else ''suggested'' end;';
  if position(a in d) = 0 then raise exception 'voice_queue_add: anchor 3 not found'; end if;
  execute replace(d, a, 'return case when m.role in (''host'', ''cohost'') then ''queued'' else ''suggested'' end;');
end $$;

-- 2. Kick: host anyone but themself; co-host only listeners and speakers.
create or replace function public.voice_kick(p_room text, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room); t public.voice_members;
begin
  t := public.voice_target(p_room, p_user);
  if t.role = 'host' or (me.role = 'cohost' and t.role = 'cohost') then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  update public.voice_members set kicked = true, seat = null, hand_at = null, muted = true, left_at = now(),
         role = case when role = 'speaker' then 'listener' else role end
   where room_id = p_room and user_id = p_user;
  perform public.voice_line(p_room, p_user, 'removed');
  perform public.voice_touch(p_room);
end $$;

do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.voice_to_audience(text, uuid)'::regprocedure);
  a := 'if t.seat is null or t.role = ''host'' then';
  if position(a in d) = 0 then raise exception 'voice_to_audience: anchor not found'; end if;
  execute replace(d, a, 'if t.seat is null or t.role = ''host'' or (me.role = ''cohost'' and t.role = ''cohost'') then');
end $$;

-- 3. Shared link.
alter table public.voice_rooms add column if not exists stage jsonb;

create or replace function public.voice_share_link(p_room text, p_url text, p_title text default null) returns void
language plpgsql security definer set search_path = '' as $$
declare me public.voice_members := public.voice_mod(p_room); who text;
begin
  if p_url is not null and (char_length(p_url) > 500 or p_url !~ '^https://[^\s/?#@]+\.[^\s/?#@]+([/?#][^\s]*)?$'
                            or char_length(coalesce(p_title, '')) > 200) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  select p.name into who from public.profiles p where p.id = me.user_id;
  update public.voice_rooms set rev = rev + 1, stage = case when p_url is null then null else jsonb_build_object(
      'url', p_url, 'title', nullif(btrim(coalesce(p_title, '')), ''), 'by', who, 'byId', me.user_id, 'at', public.voice_ms()) end
   where id = p_room;
end $$;
revoke execute on function public.voice_share_link(text, text, text) from public, anon;
grant execute on function public.voice_share_link(text, text, text) to authenticated;

do $$
declare d text; a text;
begin
  d := pg_get_functiondef('public.voice_state_of(text, uuid)'::regprocedure);
  a := '''music'', r.music,';
  if position(a in d) = 0 then raise exception 'voice_state_of: anchor not found'; end if;
  execute replace(d, a, a || ' ''stage'', r.stage,');
end $$;

