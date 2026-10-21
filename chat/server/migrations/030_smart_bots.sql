-- Three test bots that actually talk (Claude), instead of 500 that say canned lines.
--
-- Priya (Mumbai) hosts "Yaari Lounge" and chats in random chat; Rohan (Delhi, Hinglish) and Kavya (Pune) sit in
-- the lounge, visit people's public rooms (one bot per room) and chat in random chat. A bot can be in a room and in
-- one random chat at the same time.
--
-- The model is only asked when a person speaks to a bot:
--   - random chat: a bot is matched after 8 s of waiting; it greets, reads, shows "typing", answers every line,
--     says it can only text when asked for video (the request is declined), sometimes offers friendship after a few
--     lines, and leaves after 5 quiet minutes. Friend chats with a bot keep working.
--   - rooms: answers a line that names it, replies to it, asks a question or comes when only one person is there
--     (otherwise every other line); welcomes people who come in; says something when a room with people in it has
--     been quiet for 2 minutes; votes in polls.
-- Database triggers queue a job (pg_net) for the Edge Function bot-brain, which reads the context (bot_context),
-- asks the model (secret ANTHROPIC_API_KEY; model BOT_MODEL, default claude-opus-5-5) within a daily cap
-- (bot_config.llm_cap, default 1500 answers a day), and posts the answer (bot_post). Without the key the bots
-- answer from the canned lines of 028.
--
-- The other 497 bots of 028 are switched off (their rooms closed, their chats ended); they stay in the database
-- until owner_run_once_5.sql removes all bots before launch.

-- 1. Which bots, budget
alter table public.bots add column if not exists active boolean not null default false,
  add column if not exists role text not null default 'visitor' check (role in ('host', 'visitor'));
update public.bots set active = true, role = 'host' where id = '3e9b7726-b484-4cc7-90ba-9899365785d5';      -- Priya
update public.bots set active = true where id in ('e91dc2dc-59a8-4a6c-8e13-ce77efb625cb',                  -- Rohan
                                                  '8d32ff15-ac49-4dfc-abbf-d0695c94a71d');                 -- Kavya
alter table public.bot_config add column if not exists llm_day date not null default current_date,
  add column if not exists llm_used integer not null default 0,
  add column if not exists llm_cap integer not null default 1500;

-- 2. The others leave
update public.voice_rooms x set ended_at = now(), rev = rev + 1
 where x.ended_at is null and x.host in (select y.id from public.bots y where not y.active);
update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true,
       role = case when m.role = 'speaker' then 'listener' else m.role end
 where m.left_at is null and m.user_id in (select y.id from public.bots y where not y.active);
update public.matches x set ended_at = now(), ended_by = coalesce(
         (select y.id from public.bots y where y.id = x.user_b and not y.active), x.user_a), video_status = 'none'
 where x.ended_at is null
   and (x.user_a in (select y.id from public.bots y where not y.active) or x.user_b in (select y.id from public.bots y where not y.active));

create or replace function public.is_live_bot(p uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.bots y where y.id = p and y.active)
$$;

-- 3. Jobs for bot-brain
create or replace function public.bot_job(p_job jsonb) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url := 'https://dgynsckrqgnpyyvqekzi.supabase.co/functions/v1/bot-brain',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-edge-key', (select s.decrypted_secret from vault.decrypted_secrets s where s.name = 'edge_call_key')),
    body := p_job, timeout_milliseconds := 30000);
end $$;

create or replace function public.bot_on_message() returns trigger
language plpgsql security definer set search_path = '' as $$
declare x public.matches; b uuid;
begin
  if new.sender is null or public.is_bot(new.sender) then return null; end if;
  select * into x from public.matches m where m.id = new.match_id;
  b := case when public.is_live_bot(x.user_a) then x.user_a when public.is_live_bot(x.user_b) then x.user_b end;
  if b is not null then
    perform public.bot_job(jsonb_build_object('kind', 'chat', 'match', x.id, 'bot', b, 'msg', new.id));
  end if;
  return null;
end $$;
create trigger messages_bot after insert on public.messages for each row execute function public.bot_on_message();

create or replace function public.bot_on_match() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if public.is_live_bot(new.user_b) and not public.is_bot(new.user_a) then
    perform public.bot_job(jsonb_build_object('kind', 'greet', 'match', new.id, 'bot', new.user_b));
  end if;
  return null;
end $$;
create trigger matches_bot after insert on public.matches for each row execute function public.bot_on_match();

create or replace function public.bot_on_room_line() returns trigger
language plpgsql security definer set search_path = '' as $$
declare b uuid; named boolean; people int;
begin
  if new.kind <> 'text' or public.is_bot(new.sender) then return null; end if;
  -- The bot answered to, else one that is named, else any bot here.
  select m.user_id, coalesce(m.user_id::text = new.payload->'reply'->>'sender', false) or new.body ~* ('\m' || p.name || '\M')
    into b, named
    from public.voice_members m join public.bots y on y.id = m.user_id and y.active join public.profiles p on p.id = m.user_id
   where m.room_id = new.room_id and m.left_at is null and not m.kicked
   order by 2 desc, random() limit 1;
  if b is null then return null; end if;
  select count(*) into people from public.voice_members m
   where m.room_id = new.room_id and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds'
     and not public.is_bot(m.user_id);
  if not named and new.body !~ '\?' and people > 1 and random() < 0.5 then return null; end if;
  perform public.bot_job(jsonb_build_object('kind', 'room', 'room', new.room_id, 'bot', b, 'msg', new.id));
  return null;
end $$;
create trigger voice_messages_bot after insert on public.voice_messages for each row execute function public.bot_on_room_line();

-- 4. What bot-brain calls (service role only)
create or replace function public.bot_llm_take() returns boolean
language plpgsql security definer set search_path = '' as $$
declare ok boolean;
begin
  update public.bot_config set llm_used = case when llm_day = current_date then llm_used + 1 else 1 end, llm_day = current_date
   where id = 1 and (llm_day <> current_date or llm_used < llm_cap)
  returning true into ok;
  return coalesce(ok, false);
end $$;

create or replace function public.bot_card(p uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('name', x.name, 'gender', x.gender, 'age', extract(year from age(x.birth_date))::int,
           'city', x.city, 'country', x.country, 'interests', to_jsonb(coalesce(x.interests, '{}')),
           'lang', (select y.lang from public.bots y where y.id = x.id))
    from public.profiles x where x.id = p
$$;

-- Everything the model needs for one job, or null when there is nothing to do (anymore).
create or replace function public.bot_context(p_job jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare k text := p_job->>'kind'; b uuid := (p_job->>'bot')::uuid; x public.matches; h uuid; last public.messages;
  r public.voice_rooms; line public.voice_messages; top bigint;
begin
  if not public.is_live_bot(b) or not (select c.enabled from public.bot_config c where c.id = 1) then return null; end if;
  if k in ('chat', 'greet', 'video') then
    select * into x from public.matches m where m.id = (p_job->>'match')::uuid and b in (m.user_a, m.user_b);
    if not found then return null; end if;
    h := case when x.user_a = b then x.user_b else x.user_a end;
    if public.is_blocked(b, h) or (x.ended_at is not null and not public.are_friends(b, h)) then return null; end if;
    select * into last from public.messages m where m.match_id = x.id order by m.id desc limit 1;
    if k = 'chat' and (last.id is distinct from (p_job->>'msg')::bigint) then return null; end if; -- a newer line has its own job
    if k = 'greet' and last.id is not null then return null; end if;
    if last.id is not null then
      update public.matches m set read_a = case when m.user_a = b then greatest(coalesce(m.read_a, 0), last.id) else m.read_a end,
                                  read_b = case when m.user_b = b then greatest(coalesce(m.read_b, 0), last.id) else m.read_b end
       where m.id = x.id;
    end if;
    return jsonb_build_object('kind', k, 'bot', public.bot_card(b), 'partner', public.bot_card(h),
      'friends', public.are_friends(b, h),
      'history', coalesce((select jsonb_agg(jsonb_build_object('me', s.sender = b,
                   'text', coalesce(s.body, case when s.media_kind = 'video' then '[video]' else '[photo]' end)) order by s.id)
                 from (select * from public.messages m where m.match_id = x.id order by m.id desc limit 20) s), '[]'));
  end if;
  if k in ('room', 'welcome', 'idle') then
    select * into r from public.voice_rooms v where v.id = p_job->>'room' and v.ended_at is null;
    if not found or not exists (select 1 from public.voice_members m where m.room_id = r.id and m.user_id = b
                                  and m.left_at is null and not m.kicked) then
      return null;
    end if;
    if k = 'room' then
      select * into line from public.voice_messages v where v.id = (p_job->>'msg')::bigint and v.room_id = r.id;
      if not found then return null; end if;
      -- Already answered by a bot, or the room moved on (5 lines later it would be odd to answer).
      if exists (select 1 from public.voice_messages v where v.room_id = r.id and v.id > line.id and v.kind = 'text'
                   and (public.is_bot(v.sender) and v.payload->'reply'->>'id' = line.id::text)) then
        return null;
      end if;
      select count(*) into top from public.voice_messages v where v.room_id = r.id and v.id > line.id and v.kind = 'text';
      if top >= 5 then return null; end if;
    end if;
    return jsonb_build_object('kind', k, 'bot', public.bot_card(b),
      'room', jsonb_build_object('title', r.title, 'category', r.category, 'lang', r.lang, 'announcement', r.announcement,
                                 'playing', r.music->>'title', 'host', (select p.name from public.profiles p where p.id = r.host)),
      'people', coalesce((select jsonb_agg(p.name order by m.joined_at) from public.voice_members m join public.profiles p on p.id = m.user_id
                   where m.room_id = r.id and m.left_at is null and not m.kicked and m.seen_at > now() - interval '90 seconds'
                     and m.user_id <> b), '[]'),
      'to', case when k = 'room' then jsonb_build_object('id', line.id, 'name', line.sender_name, 'text', line.body)
                 when k = 'welcome' then jsonb_build_object('name', (select p.name from public.profiles p where p.id = (p_job->>'user')::uuid)) end,
      'history', coalesce((select jsonb_agg(jsonb_build_object('me', s.sender = b, 'name', s.sender_name,
                   'text', case s.kind when 'text' then s.body when 'photo' then '[photo]'
                                       when 'poll' then '[poll] ' || (s.payload->>'question') end,
                   'replyTo', s.payload->'reply'->>'name') order by s.id)
                 from (select * from public.voice_messages v where v.room_id = r.id and v.kind in ('text', 'photo', 'poll')
                        order by v.id desc limit 20) s), '[]'));
  end if;
  return null;
end $$;

-- "typing..." in the person's chat while the answer is written (same Broadcast the apps send).
create or replace function public.bot_typing(p_job jsonb) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if p_job->>'kind' in ('chat', 'greet', 'video') then
    perform realtime.send(jsonb_build_object('from', p_job->>'bot'), 'typing', 'typing-' || (p_job->>'match'), false);
  end if;
end $$;

-- The canned answer when there is no model (028's lines).
create or replace function public.bot_canned(p_job jsonb, p_text text default null) returns text
language plpgsql security definer set search_path = '' as $$
declare b uuid := (p_job->>'bot')::uuid; lg text := (select y.lang from public.bots y where y.id = b); t text := coalesce(p_text, ''); k text;
begin
  k := case p_job->>'kind'
    when 'greet' then 'greet' when 'welcome' then 'welcome' when 'idle' then 'room_talk' when 'video' then 'react'
    else case
      when t ~* '(where|kahan|kaha |kidhar|onde|đâu|কোথায়)' then 'where'
      when t ~* '(name|naam|nome|tên|নাম)' then 'name'
      when t ~* '(\mage\M|how old|umar|umra|idade|tuổi|বয়স)' then 'age'
      when p_job->>'kind' = 'room' then 'room_reply'
      else 'react' end end;
  return public.bot_say(b, lg, k, p_job->>'toName');
end $$;

-- Posts the answer; false when it is no longer wanted (a newer line came while it was written).
create or replace function public.bot_post(p_job jsonb, p_text text) returns boolean
language plpgsql security definer set search_path = '' as $$
declare k text := p_job->>'kind'; b uuid := (p_job->>'bot')::uuid; t text := left(btrim(coalesce(p_text, '')), 500);
  x public.matches; last public.messages; q public.voice_messages; p public.profiles;
begin
  if t = '' or not public.is_live_bot(b) then return false; end if;
  if k in ('chat', 'greet', 'video') then
    select * into x from public.matches m where m.id = (p_job->>'match')::uuid and b in (m.user_a, m.user_b);
    if not found then return false; end if;
    select * into last from public.messages m where m.match_id = x.id order by m.id desc limit 1;
    if k = 'chat' and last.id is distinct from (p_job->>'msg')::bigint then return false; end if;
    if k = 'greet' and last.id is not null then return false; end if;
    insert into public.messages (match_id, sender, body) values (x.id, b, t);
    return true;
  end if;
  if not exists (select 1 from public.voice_members m join public.voice_rooms r on r.id = m.room_id
                  where m.room_id = p_job->>'room' and m.user_id = b and m.left_at is null and not m.kicked and r.ended_at is null) then
    return false;
  end if;
  if k = 'room' then
    select * into q from public.voice_messages v where v.id = (p_job->>'msg')::bigint and v.room_id = p_job->>'room';
    if exists (select 1 from public.voice_messages v where v.room_id = q.room_id and v.id > q.id and v.kind = 'text'
                 and public.is_bot(v.sender) and v.payload->'reply'->>'id' = q.id::text) then
      return false;
    end if;
  end if;
  select * into p from public.profiles x2 where x2.id = b;
  insert into public.voice_messages (room_id, sender, kind, body, payload, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  values (p_job->>'room', b, 'text', t,
          case when q.id is not null then jsonb_build_object('reply', jsonb_build_object(
            'id', q.id, 'sender', q.sender, 'name', q.sender_name, 'kind', q.kind, 'text', left(q.body, 120))) end,
          p.name, p.gender, extract(year from age(p.birth_date))::int, p.photo_path, p.country);
  return true;
exception when others then
  return false; -- too fast, banned by a test, room gone
end $$;

revoke execute on function public.is_live_bot(uuid), public.bot_job(jsonb), public.bot_on_message(), public.bot_on_match(),
  public.bot_on_room_line(), public.bot_llm_take(), public.bot_card(uuid), public.bot_context(jsonb), public.bot_typing(jsonb),
  public.bot_canned(jsonb, text), public.bot_post(jsonb, text) from public, anon, authenticated;
grant execute on function public.bot_llm_take(), public.bot_context(jsonb), public.bot_typing(jsonb),
  public.bot_canned(jsonb, text), public.bot_post(jsonb, text) to service_role;

-- 5. Every 10 seconds (pg_cron yaari-bots): matching, chats, rooms, presence.
create or replace function public.bot_match_people() returns void
language plpgsql security definer set search_path = '' as $$
declare q public.queue; b uuid; last_partner uuid;
begin
  for q in select x.* from public.queue x
            where x.seen_at > now() - interval '15 seconds' and x.joined_at < now() - interval '8 seconds'
              and not exists (select 1 from public.bots y where y.id = x.user_id)
            order by x.joined_at limit 20
  loop
    continue when not pg_try_advisory_xact_lock(hashtext('match:' || q.user_id::text));
    perform 1 from public.queue x where x.user_id = q.user_id and x.seen_at > now() - interval '15 seconds' for update skip locked;
    continue when not found;
    continue when exists (select 1 from public.matches x where x.user_a = q.user_id and x.ended_at is null)
               or exists (select 1 from public.matches x where x.user_b = q.user_id and x.ended_at is null);
    select case when x.user_a = q.user_id then x.user_b else x.user_a end into last_partner
      from public.matches x where q.user_id in (x.user_a, x.user_b) order by x.created_at desc limit 1;
    select y.id into b from public.bots y join public.profiles p on p.id = y.id
     where y.active and (q.target = 'any' or p.gender = q.target)
       and y.id is distinct from last_partner
       and not public.is_blocked(y.id, q.user_id)
       and not exists (select 1 from public.matches x where x.user_a = y.id and x.ended_at is null)
       and not exists (select 1 from public.matches x where x.user_b = y.id and x.ended_at is null)
     order by (y.lang = q.lang) desc, random() limit 1;
    continue when b is null;
    update public.queue set seen_at = '-infinity' where user_id = q.user_id;
    insert into public.matches (user_a, user_b) values (q.user_id, b);
  end loop;
end $$;

create or replace function public.bot_chats() returns void
language plpgsql security definer set search_path = '' as $$
declare mt public.matches; b uuid; h uuid; last_at timestamptz;
begin
  for mt in select x.* from public.matches x
             where x.ended_at is null and public.is_live_bot(x.user_b) and not public.is_bot(x.user_a)
  loop
    b := mt.user_b; h := mt.user_a;
    if mt.video_status = 'requested' and mt.video_requested_by = h then
      update public.matches set video_status = 'declined' where id = mt.id and video_status = 'requested';
      perform public.bot_job(jsonb_build_object('kind', 'video', 'match', mt.id, 'bot', b));
    end if;
    if not public.are_friends(h, b)
       and (select count(*) from public.messages m where m.match_id = mt.id and m.sender = h) >= 4
       and not exists (select 1 from public.friendships f where (f.requester = h and f.addressee = b) or (f.requester = b and f.addressee = h)) then
      insert into public.bot_done (key) values ('f' || mt.id) on conflict do nothing;
      if found and random() < 0.5 then
        insert into public.friendships (requester, addressee) values (b, h) on conflict do nothing;
      end if;
    end if;
    select coalesce(max(m.created_at), mt.created_at) into last_at from public.messages m where m.match_id = mt.id;
    if last_at < now() - interval '5 minutes' then
      update public.matches set ended_at = now(), ended_by = b, video_status = 'none' where id = mt.id and ended_at is null;
    end if;
  end loop;
end $$;

create or replace function public.bot_rooms() returns void
language plpgsql security definer set search_path = '' as $$
declare r record; vb uuid;
begin
  -- Welcome people who just came into a room with a bot in it.
  for r in select m.room_id, m.user_id, m.joined_line_at from public.voice_members m join public.voice_rooms x on x.id = m.room_id
            where m.joined_line_at > now() - interval '30 seconds' and m.joined_line_at < now() - interval '2 seconds'
              and m.left_at is null and x.ended_at is null and not public.is_bot(m.user_id)
              -- not when they already talk, or a bot already spoke to the room since they came
              and not exists (select 1 from public.voice_messages v where v.room_id = m.room_id and v.kind = 'text'
                                and v.created_at > m.joined_line_at and (v.sender = m.user_id or public.is_bot(v.sender)))
  loop
    select z.user_id into vb from public.voice_members z join public.bots y on y.id = z.user_id and y.active
     where z.room_id = r.room_id and z.left_at is null and not z.kicked order by random() limit 1;
    continue when vb is null;
    insert into public.bot_done (key) values ('w' || r.room_id || r.user_id || extract(epoch from r.joined_line_at)::bigint)
    on conflict do nothing;
    continue when not found;
    perform public.bot_job(jsonb_build_object('kind', 'welcome', 'room', r.room_id, 'bot', vb, 'user', r.user_id));
  end loop;

  -- A room with people in it that has been quiet for 2 minutes.
  for r in select x.id from public.voice_rooms x
            where x.ended_at is null
              and exists (select 1 from public.voice_members m where m.room_id = x.id and m.left_at is null and not m.kicked
                            and m.seen_at > now() - interval '90 seconds' and not public.is_bot(m.user_id))
              and exists (select 1 from public.voice_members m join public.bots y on y.id = m.user_id and y.active
                            where m.room_id = x.id and m.left_at is null and not m.kicked)
              and coalesce((select max(v2.created_at) from public.voice_messages v2 where v2.room_id = x.id and v2.kind = 'text'),
                           '-infinity') < now() - interval '2 minutes'
  loop
    insert into public.bot_done (key) values ('i' || r.id || floor(extract(epoch from now()) / 300)::bigint) on conflict do nothing;
    continue when not found;
    select z.user_id into vb from public.voice_members z join public.bots y on y.id = z.user_id and y.active
     where z.room_id = r.id and z.left_at is null and not z.kicked order by random() limit 1;
    perform public.bot_job(jsonb_build_object('kind', 'idle', 'room', r.id, 'bot', vb));
  end loop;

  -- Polls: bots in the room vote 10-40 seconds after the question.
  for r in select pl.id, pl.room_id, cardinality(pl.options) n, z.user_id from public.voice_polls pl
             join public.voice_members z on z.room_id = pl.room_id and z.left_at is null and not z.kicked
             join public.bots y on y.id = z.user_id and y.active
            where pl.closed_at is null and pl.created_at < now() - make_interval(secs => 10 + (hashtext(z.user_id::text) & 2147483647) % 30)
              and not exists (select 1 from public.voice_poll_votes pv where pv.poll_id = pl.id and pv.user_id = z.user_id)
  loop
    insert into public.voice_poll_votes (poll_id, user_id, choice) values (r.id, r.user_id, floor(random() * r.n)::int);
    perform public.voice_emit(r.room_id, jsonb_build_object('t', 'p', 'poll',
      public.voice_poll_json((select pl from public.voice_polls pl where pl.id = r.id), null)));
  end loop;
end $$;

-- Every 30 seconds: presence, the lounge, visits.
create or replace function public.bot_places() returns void
language plpgsql security definer set search_path = '' as $$
declare b record; hb uuid; lounge text; here text; pick text;
begin
  insert into public.user_seen (user_id, seen_at)
  select y.id, now() from public.bots y
   where y.active and not exists (select 1 from public.voice_members m join public.voice_rooms x on x.id = m.room_id
                                   where m.user_id = y.id and m.left_at is null and not m.kicked and x.ended_at is null)
  on conflict (user_id) do update set seen_at = excluded.seen_at;
  update public.voice_members m set seen_at = now() from public.bots y where y.id = m.user_id and y.active and m.left_at is null and not m.kicked;

  -- The lounge: always open, hosted by the host bot.
  select x.id into lounge from public.voice_rooms x join public.bots y on y.id = x.host and y.role = 'host' and y.active
   where x.ended_at is null order by x.created_at desc limit 1;
  if lounge is null then
    select y.id into hb from public.bots y where y.active and y.role = 'host' limit 1;
    if hb is not null then
      lounge := 'v-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
      update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true
       where m.user_id = hb and m.left_at is null;
      insert into public.voice_rooms (id, owner, host, title, category, max_members, gender_rule, min_age, max_age, is_private, lang, announcement)
      values (lounge, hb, hb, 'Yaari Lounge', 'talk', 50, 'any', 18, 100, false, 'en',
              'Chat with Priya, Rohan and Kavya, Yaari''s test bots. Say hi!');
      insert into public.voice_room_codes (room_id, code) values (lounge, public.voice_new_code());
      insert into public.voice_members (room_id, user_id, role, seat) values (lounge, hb, 'host', null);
    end if;
  end if;

  -- Visitors: one per people's public room that has no bot yet; otherwise in the lounge.
  for b in select y.id from public.bots y where y.active and y.role = 'visitor' order by y.id loop
    select m.room_id into here from public.voice_members m join public.voice_rooms x on x.id = m.room_id
     where m.user_id = b.id and m.left_at is null and not m.kicked and x.ended_at is null limit 1;
    pick := null;
    if here is null or here = lounge then
      select x.id into pick from public.voice_rooms x
       where x.ended_at is null and not x.is_private and not public.is_bot(x.host)
         and exists (select 1 from public.voice_members m where m.room_id = x.id and m.user_id = x.host and m.left_at is null
                       and m.seen_at > now() - interval '2 minutes')
         and not exists (select 1 from public.voice_members m join public.bots y on y.id = m.user_id and y.active
                           where m.room_id = x.id and m.left_at is null and not m.kicked)
         and not exists (select 1 from public.voice_members m where m.room_id = x.id and m.user_id = b.id and m.kicked)
         and public.voice_refusal(x, b.id, false) is null
       order by x.created_at desc limit 1;
      pick := coalesce(pick, case when here is null then lounge end);
    end if;
    continue when pick is null or pick = here;
    update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true,
           role = case when m.role = 'speaker' then 'listener' else m.role end
     where m.user_id = b.id and m.left_at is null and m.role <> 'host';
    if here is not null then perform public.voice_touch(here); end if;
    perform public.bot_join(pick, b.id);
  end loop;
end $$;

create or replace function public.bot_social() returns void
language plpgsql security definer set search_path = '' as $$
declare r record;
begin
  update public.friendships f set status = 'accepted'
   where f.status = 'pending' and f.created_at < now() - interval '5 seconds' and public.is_live_bot(f.addressee);
  for r in select n.user_id, n.room_id from public.notifications n join public.bots y on y.id = n.user_id and y.active
            where n.read_at is null and n.kind = 'room_invite' and n.room_id is not null
              and n.created_at < now() - interval '4 seconds' and y.role = 'visitor'
  loop
    update public.voice_members m set left_at = now(), seat = null, hand_at = null, muted = true,
           role = case when m.role = 'speaker' then 'listener' else m.role end
     where m.user_id = r.user_id and m.room_id <> r.room_id and m.left_at is null and m.role <> 'host';
    perform public.bot_join(r.room_id, r.user_id);
  end loop;
  update public.notifications n set read_at = now()
    from public.bots y where y.id = n.user_id and n.read_at is null and n.created_at < now() - interval '4 seconds';
end $$;

create or replace function public.bot_tick() returns void
language plpgsql security definer set search_path = '' as $$
declare t bigint;
begin
  update public.bot_config set tick = tick + 1
   where id = 1 and enabled and id = (select c.id from public.bot_config c where c.id = 1 for update skip locked)
  returning tick into t;
  if t is null then return; end if;
  perform public.bot_match_people();
  perform public.bot_chats();
  perform public.bot_social();
  perform public.bot_rooms();
  if t % 3 = 1 then perform public.bot_places(); end if;
end $$;

revoke execute on function public.bot_chats(), public.bot_rooms(), public.bot_places() from public, anon, authenticated;
