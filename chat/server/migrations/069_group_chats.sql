-- 48 Group chats among friends (the owner, 2026-10-26): outside rooms, a chat of up to 50 people that stays. Made
-- from my friends; anyone in it can add their own friends, rename it, or leave. Leaving keeps the row (left_at) and
-- every message stays, so a report can still show what was written (14, the owner). Text only. Reads and writes go through the functions below; the app hears new messages over Realtime (Postgres
-- Changes on group_messages, readable by members only).

create table if not exists public.group_chats (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 40),
  owner uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  last_at timestamptz not null default now()
);

create table if not exists public.group_members (
  group_id uuid not null references public.group_chats (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  -- The last message I've seen (unread = newer messages from others).
  read_id bigint not null default 0,
  left_at timestamptz,
  primary key (group_id, user_id)
);
create index if not exists group_members_user on public.group_members (user_id);

create table if not exists public.group_messages (
  id bigserial primary key,
  group_id uuid not null references public.group_chats (id) on delete cascade,
  sender uuid references public.profiles (id) on delete set null,
  kind text not null default 'text' check (kind in ('text', 'created', 'added', 'left', 'renamed')),
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now()
);
create index if not exists group_messages_group on public.group_messages (group_id, id desc);
create index if not exists group_messages_sender on public.group_messages (sender, created_at desc);

alter table public.group_chats enable row level security;
alter table public.group_members enable row level security;
alter table public.group_messages enable row level security;
revoke all on public.group_chats, public.group_members, public.group_messages from public, anon, authenticated;

create or replace function public.group_member(p_group uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members m where m.group_id = p_group and m.user_id = auth.uid() and m.left_at is null)
$$;

-- What a member may read: lines written since they (last) joined.
create or replace function public.group_reads(p_group uuid, p_at timestamptz) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members m
                  where m.group_id = p_group and m.user_id = auth.uid() and m.left_at is null and m.joined_at <= p_at)
$$;

-- Realtime only: members may read the group's messages (everything else goes through the functions).
grant select on public.group_messages to authenticated;
do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'group_messages' and policyname = 'group_messages_members') then
    create policy group_messages_members on public.group_messages for select to authenticated using (public.group_reads(group_id, created_at));
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'group_messages') then
    alter publication supabase_realtime add table public.group_messages;
  end if;
end $$;

-- A line from the server ('created', 'added', 'left', 'renamed'): the body carries the names.
create or replace function public.group_line(p_group uuid, p_sender uuid, p_kind text, p_body text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare n bigint;
begin
  insert into public.group_messages (group_id, sender, kind, body) values (p_group, p_sender, p_kind, left(p_body, 2000)) returning id into n;
  update public.group_chats set last_at = now() where id = p_group;
  return n;
end $$;

-- Friends of `me` among `p_users` who aren't in the group yet, as (id, name).
create or replace function public.group_addable(me uuid, p_group uuid, p_users uuid[]) returns table (id uuid, name text)
language sql stable security definer set search_path = '' as $$
  select p.id, p.name from public.profiles p
   where p.id = any (p_users) and p.id <> me and public.are_friends(me, p.id) and not public.is_blocked(me, p.id)
     and not exists (select 1 from public.group_members m where m.group_id = p_group and m.user_id = p.id and m.left_at is null)
$$;

create or replace function public.group_create(p_title text, p_members uuid[]) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, '')); g uuid; names text;
begin
  if char_length(t) not between 1 and 40 or cardinality(p_members) not between 1 and 49 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.group_chats x where x.owner = me and x.created_at > now() - interval '1 day') >= 20 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.group_chats (title, owner) values (t, me) returning id into g;
  insert into public.group_members (group_id, user_id) values (g, me);
  select string_agg(a.name, ', ') into names from public.group_addable(me, g, p_members) a;
  if names is null then raise exception 'not_friends' using errcode = 'P0001'; end if;
  insert into public.group_members (group_id, user_id) select g, a.id from public.group_addable(me, g, p_members) a;
  perform public.group_line(g, me, 'created', names);
  return g;
end $$;

create or replace function public.group_add(p_group uuid, p_members uuid[]) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); names text; n int;
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  select string_agg(a.name, ', '), count(*) into names, n from public.group_addable(me, p_group, p_members) a;
  if names is null then return; end if;
  if (select count(*) from public.group_members m where m.group_id = p_group and m.left_at is null) + n > 50 then
    raise exception 'room_full' using errcode = 'P0001';
  end if;
  -- Someone who left comes back as new: unread starts from now.
  insert into public.group_members (group_id, user_id, read_id)
  select p_group, a.id, coalesce((select max(x.id) from public.group_messages x where x.group_id = p_group), 0)
    from public.group_addable(me, p_group, p_members) a
  on conflict (group_id, user_id) do update set left_at = null, joined_at = now(), read_id = excluded.read_id;
  perform public.group_line(p_group, me, 'added', names);
end $$;

create or replace function public.group_rename(p_group uuid, p_title text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, ''));
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(t) not between 1 and 40 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.group_chats set title = t where id = p_group and title <> t;
  if found then perform public.group_line(p_group, me, 'renamed', t); end if;
end $$;

create or replace function public.group_leave(p_group uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  update public.group_members set left_at = now() where group_id = p_group and user_id = me and left_at is null;
  if found then perform public.group_line(p_group, me, 'left', coalesce((select p.name from public.profiles p where p.id = me), '')); end if;
end $$;

-- 10 messages in 10 seconds at most (like 1:1 chats); a banned person can't write.
create or replace function public.group_send(p_group uuid, p_body text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); b text := btrim(coalesce(p_body, ''));
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(b) not between 1 and 2000 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('gmsg:' || me::text));
  if exists (select 1 from public.profiles p where p.id = me and p.banned_until > now()) then raise exception 'banned' using errcode = 'P0001'; end if;
  if (select count(*) from public.group_messages m where m.sender = me and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return public.group_line(p_group, me, 'text', b);
end $$;

-- Newest first, 50 at a time (p_before: older than this id); only what was written since I (last) joined.
create or replace function public.group_history(p_group uuid, p_before bigint default null)
returns table (id bigint, sender uuid, kind text, body text, created_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  return query select m.id, m.sender, m.kind, m.body, m.created_at from public.group_messages m
    where m.group_id = p_group and (p_before is null or m.id < p_before) and public.group_reads(p_group, m.created_at)
    order by m.id desc limit 50;
end $$;

create or replace function public.group_read(p_group uuid, p_upto bigint) returns void
language sql security definer set search_path = '' as $$
  update public.group_members set read_id = greatest(read_id, coalesce(p_upto, 0)) where group_id = p_group and user_id = auth.uid();
$$;

-- My groups, latest first: title, the people in it (cards), the last line and how many I haven't read.
create or replace function public.group_list() returns table (id uuid, title text, members jsonb, last_kind text, last_body text,
  last_sender uuid, last_at timestamptz, unread int)
language sql stable security definer set search_path = '' as $$
  select g.id, g.title,
         (select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'gender', p.gender,
                   'age', extract(year from age(p.birth_date))::int, 'city', p.city, 'bio', p.bio, 'photo_path', p.photo_path,
                   'country', p.country) order by o.joined_at)
            from public.group_members o join public.profiles_seen p on p.id = o.user_id where o.group_id = g.id and o.left_at is null),
         l.kind, l.body, l.sender, g.last_at,
         (select count(*)::int from public.group_messages x where x.group_id = g.id and x.id > me.read_id and x.sender is distinct from auth.uid())
    from public.group_members me
    join public.group_chats g on g.id = me.group_id
    left join lateral (select x.kind, x.body, x.sender from public.group_messages x where x.group_id = g.id order by x.id desc limit 1) l on true
   where me.user_id = auth.uid() and me.left_at is null
   order by g.last_at desc
   limit 100
$$;

revoke execute on function public.group_member(uuid), public.group_reads(uuid, timestamptz), public.group_line(uuid, uuid, text, text), public.group_addable(uuid, uuid, uuid[])
  from public, anon;
revoke execute on function public.group_line(uuid, uuid, text, text), public.group_addable(uuid, uuid, uuid[]) from authenticated;
grant execute on function public.group_member(uuid), public.group_reads(uuid, timestamptz) to authenticated;
revoke execute on function public.group_create(text, uuid[]), public.group_add(uuid, uuid[]), public.group_rename(uuid, text),
  public.group_leave(uuid), public.group_send(uuid, text), public.group_history(uuid, bigint), public.group_read(uuid, bigint),
  public.group_list() from public, anon;
grant execute on function public.group_create(text, uuid[]), public.group_add(uuid, uuid[]), public.group_rename(uuid, text),
  public.group_leave(uuid), public.group_send(uuid, text), public.group_history(uuid, bigint), public.group_read(uuid, bigint),
  public.group_list() to authenticated;
