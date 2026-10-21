-- Group rooms: replies and polls (what group chats people know - WhatsApp, Telegram - have), and no more games.
--
-- 1. voice_send2(room, text, reply_to): a line that answers another one. The answered line is copied into
--    payload.reply ({id, sender, name, text, kind}) so every app can show the quote without looking it up, also
--    when the original is far back in the history.
-- 2. Polls: the host or a co-host asks a question with 2-4 answers; one open poll per room (a new one closes the
--    last); everyone in the room votes once and can change their vote; results are live.
--      voice_poll_create(room, question, options[]) -> the room line (kind 'poll', payload {poll_id, question, options})
--      voice_poll_vote(poll, choice 0-based)       -> the poll with my vote
--      voice_poll_close(poll)                      -> the poll (host, co-host, or who asked)
--      voice_polls_get(ids[])                      -> the polls (for lines loaded from history)
--    A poll: {id, question, options[], counts[], total, closed, by, mine}. Every change is sent to the room as a
--    Broadcast event {t: 'p', poll: {...}} (with mine = null: each app keeps its own vote).
-- 3. Games are gone from the apps (Ludo was not good enough): rooms are 'talk' or 'music'. Apps still on an older
--    version can send 'games'; it is made 'talk'. The Ludo tables and functions stay for those apps for now.

-- 1. Replies
create or replace function public.voice_send2(p_room text, p_text text, p_reply_to bigint default null)
returns public.voice_messages language plpgsql security definer set search_path = '' as $$
declare m public.voice_members; t text := btrim(coalesce(p_text, '')); p public.profiles; q public.voice_messages;
  msg public.voice_messages;
begin
  if char_length(t) not between 1 and 500 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  m := public.voice_actor(p_room);
  if (select count(*) from public.voice_messages x where x.sender = m.user_id and x.kind = 'text'
        and x.created_at > now() - interval '10 seconds') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  if p_reply_to is not null then
    select * into q from public.voice_messages x where x.id = p_reply_to and x.room_id = p_room and x.kind in ('text', 'photo', 'poll');
  end if;
  select * into p from public.profiles x where x.id = m.user_id;
  insert into public.voice_messages (room_id, sender, kind, body, payload, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  values (p_room, p.id, 'text', t,
          case when q.id is not null then jsonb_build_object('reply', jsonb_build_object(
            'id', q.id, 'sender', q.sender, 'name', q.sender_name, 'kind', q.kind,
            'text', case q.kind when 'text' then left(q.body, 120) when 'poll' then q.payload->>'question' end)) end,
          p.name, p.gender, extract(year from age(p.birth_date))::int, p.photo_path, p.country)
  returning * into msg;
  return msg;
end $$;

-- 2. Polls
alter table public.voice_messages drop constraint voice_messages_kind_check,
  add constraint voice_messages_kind_check check (kind in ('text', 'system', 'reaction', 'icebreaker', 'photo', 'poll'));

create table if not exists public.voice_polls (
  id bigint generated always as identity primary key,
  room_id text not null,
  created_by uuid not null,
  question text not null check (char_length(question) between 1 and 120),
  options text[] not null check (cardinality(options) between 2 and 4),
  created_at timestamptz not null default now(),
  closed_at timestamptz
);
create index if not exists voice_polls_room on public.voice_polls (room_id, id desc);
create table if not exists public.voice_poll_votes (
  poll_id bigint not null,
  user_id uuid not null,
  choice smallint not null,
  at timestamptz not null default now(),
  primary key (poll_id, user_id)
);
alter table public.voice_polls enable row level security;
alter table public.voice_poll_votes enable row level security;
revoke all on public.voice_polls, public.voice_poll_votes from public, anon, authenticated;

create or replace function public.voice_poll_json(p public.voice_polls, p_me uuid) returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('id', p.id, 'question', p.question, 'options', to_jsonb(p.options), 'by', p.created_by,
    'closed', p.closed_at is not null,
    'counts', (select jsonb_agg((select count(*) from public.voice_poll_votes v where v.poll_id = p.id and v.choice = i - 1) order by i)
                 from generate_subscripts(p.options, 1) i),
    'total', (select count(*) from public.voice_poll_votes v where v.poll_id = p.id),
    'mine', (select v.choice from public.voice_poll_votes v where v.poll_id = p.id and v.user_id = p_me))
$$;

create or replace function public.voice_poll_create(p_room text, p_question text, p_options text[])
returns public.voice_messages language plpgsql security definer set search_path = '' as $$
declare m public.voice_members; q text := btrim(coalesce(p_question, '')); o text[]; p public.voice_polls; old public.voice_polls;
  pr public.profiles; msg public.voice_messages;
begin
  o := array(select btrim(x) from unnest(coalesce(p_options, '{}')) with ordinality t(x, n) where btrim(x) <> '' order by n);
  if char_length(q) not between 1 and 120 or cardinality(o) not between 2 and 4
     or exists (select 1 from unnest(o) x where char_length(x) > 40)
     or (select count(distinct lower(x)) from unnest(o) x) <> cardinality(o) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  m := public.voice_actor(p_room);
  if m.role not in ('host', 'cohost') then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if (select count(*) from public.voice_polls x where x.room_id = p_room and x.created_at > now() - interval '10 minutes') >= 5 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  for old in update public.voice_polls x set closed_at = now() where x.room_id = p_room and x.closed_at is null returning * loop
    perform public.voice_emit(p_room, jsonb_build_object('t', 'p', 'poll', public.voice_poll_json(old, null)));
  end loop;
  insert into public.voice_polls (room_id, created_by, question, options) values (p_room, m.user_id, q, o) returning * into p;
  select * into pr from public.profiles x where x.id = m.user_id;
  insert into public.voice_messages (room_id, sender, kind, payload, sender_name, sender_gender, sender_age, sender_photo, sender_country)
  values (p_room, pr.id, 'poll', jsonb_build_object('poll_id', p.id, 'question', q, 'options', to_jsonb(o)),
          pr.name, pr.gender, extract(year from age(pr.birth_date))::int, pr.photo_path, pr.country)
  returning * into msg;
  return msg;
end $$;

create or replace function public.voice_poll_vote(p_poll bigint, p_choice integer) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p public.voice_polls;
begin
  select * into p from public.voice_polls x where x.id = p_poll;
  if not found or not exists (select 1 from public.voice_members m join public.voice_rooms r on r.id = m.room_id
                                where m.room_id = p.room_id and m.user_id = me and m.left_at is null and not m.kicked
                                  and r.ended_at is null) then
    raise exception 'not_found' using errcode = 'P0001';
  end if;
  if p.closed_at is not null then raise exception 'poll_closed' using errcode = 'P0001'; end if;
  if p_choice is null or p_choice < 0 or p_choice >= cardinality(p.options) then raise exception 'bad_request' using errcode = 'P0001'; end if;
  insert into public.voice_poll_votes (poll_id, user_id, choice) values (p.id, me, p_choice)
  on conflict (poll_id, user_id) do update set choice = excluded.choice, at = now();
  perform public.voice_emit(p.room_id, jsonb_build_object('t', 'p', 'poll', public.voice_poll_json(p, null)));
  return public.voice_poll_json(p, me);
end $$;

create or replace function public.voice_poll_close(p_poll bigint) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); p public.voice_polls;
begin
  select * into p from public.voice_polls x where x.id = p_poll;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if p.created_by <> me and not exists (select 1 from public.voice_members m where m.room_id = p.room_id and m.user_id = me
                                          and m.role in ('host', 'cohost') and m.left_at is null and not m.kicked) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  update public.voice_polls set closed_at = coalesce(closed_at, now()) where id = p.id returning * into p;
  perform public.voice_emit(p.room_id, jsonb_build_object('t', 'p', 'poll', public.voice_poll_json(p, null)));
  return public.voice_poll_json(p, me);
end $$;

create or replace function public.voice_polls_get(p_ids bigint[]) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(public.voice_poll_json(p, auth.uid()) order by p.id), '[]')
    from public.voice_polls p
   where p.id = any (p_ids[1:50]) and public.voice_can_read(p.room_id)
$$;

revoke execute on function public.voice_send2(text, text, bigint), public.voice_poll_json(public.voice_polls, uuid),
  public.voice_poll_create(text, text, text[]), public.voice_poll_vote(bigint, integer), public.voice_poll_close(bigint),
  public.voice_polls_get(bigint[]) from public, anon;
revoke execute on function public.voice_poll_json(public.voice_polls, uuid) from authenticated;
grant execute on function public.voice_send2(text, text, bigint), public.voice_poll_create(text, text, text[]),
  public.voice_poll_vote(bigint, integer), public.voice_poll_close(bigint), public.voice_polls_get(bigint[]) to authenticated;

-- 3. No games
create or replace function public.voice_no_games() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.category = 'games' then new.category := 'talk'; end if;
  return new;
end $$;
create trigger voice_rooms_no_games before insert or update of category on public.voice_rooms
  for each row execute function public.voice_no_games();
update public.voice_rooms set category = 'talk', rev = rev + 1 where category = 'games' and ended_at is null;
