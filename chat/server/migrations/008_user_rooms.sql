-- User rooms: anyone can open a room with a title, a category, a member limit and who may join
-- (gender, age range). Every user room has a 6-character code; private rooms are found only by it.
-- Official rooms (owner null) are unchanged, and room_list()/join_room() keep answering older app versions.

alter table public.rooms
  add column owner uuid references public.profiles (id) on delete cascade,
  add column category text not null default 'chat'
    check (category in ('chat', 'friends', 'language', 'music', 'movies', 'gaming', 'sports', 'study', 'travel', 'night')),
  add column max_members int check (max_members between 2 and 50),
  add column gender_rule text not null default 'any' check (gender_rule in ('any', 'female', 'male')),
  add column min_age int not null default 18 check (min_age between 18 and 100),
  add column max_age int not null default 100 check (max_age between 18 and 100),
  add column is_private boolean not null default false,
  add column created_at timestamptz not null default now(),
  add column closed_at timestamptz,
  add constraint rooms_age_range check (min_age <= max_age),
  add constraint rooms_user_size check (owner is null or max_members is not null);
create index rooms_owner on public.rooms (owner) where owner is not null;

-- Private rooms are not listed to strangers.
alter policy "rooms are public" on public.rooms using (owner is null or not is_private or owner = (select auth.uid()));

-- The code is the key to a private room: kept apart, readable only through the functions below (no policies).
create table public.room_codes (
  room_id text primary key references public.rooms (id) on delete cascade,
  code text not null unique check (code ~ '^[A-HJ-NP-Z2-9]{6}$')
);
alter table public.room_codes enable row level security;

-- A user room is alive while open and someone was in it in the last 30 minutes (or it is new).
create function public.room_alive(r public.rooms) returns boolean language sql stable security definer set search_path = '' as $$
  select r.owner is null or (r.closed_at is null and (r.created_at > now() - interval '30 minutes'
    or exists (select 1 from public.room_members m where m.room_id = r.id and m.seen_at > now() - interval '30 minutes')))
$$;

create function public.room_online(p_room text) returns int language sql stable security definer set search_path = '' as $$
  select count(*)::int from public.room_members m where m.room_id = p_room and m.seen_at > now() - interval '90 seconds'
$$;

-- Why `me` may not enter (null = allowed). The owner always may.
create function public.room_refusal(r public.rooms, me uuid) returns text language plpgsql stable security definer set search_path = '' as $$
declare p public.profiles; my_age int;
begin
  if r.owner is null or r.owner = me then return null; end if;
  if not public.room_alive(r) then return 'room_closed'; end if;
  if public.is_blocked(me, r.owner) then return 'not_allowed'; end if;
  select * into p from public.profiles where id = me;
  my_age := extract(year from age(p.birth_date))::int;
  if r.gender_rule <> 'any' and p.gender <> r.gender_rule then return 'room_gender'; end if;
  if my_age < r.min_age or my_age > r.max_age then return 'room_age'; end if;
  if not exists (select 1 from public.room_members m where m.room_id = r.id and m.user_id = me and m.seen_at > now() - interval '90 seconds')
     and public.room_online(r.id) >= r.max_members then
    return 'room_full';
  end if;
  return null;
end $$;

-- Private rooms are readable by their owner and members only.
create function public.can_read_room(p_room text) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.rooms r where r.id = p_room and (not r.is_private or r.owner = auth.uid()
    or exists (select 1 from public.room_members m where m.room_id = r.id and m.user_id = auth.uid())))
$$;
alter policy "read room" on public.room_messages using (not public.blocked_with(sender) and public.can_read_room(room_id));

-- Enter (or stay listed in) a room. Private rooms are entered the first time with join_room_code.
create or replace function public.join_room(p_room text) returns void
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); r public.rooms; why text;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  select * into r from public.rooms x where x.id = p_room;
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  if r.is_private and r.owner <> me
     and not exists (select 1 from public.room_members m where m.room_id = r.id and m.user_id = me) then
    raise exception 'not_found' using errcode = 'P0001'; -- same answer as a wrong id: no probing for private rooms
  end if;
  why := public.room_refusal(r, me);
  if why is not null then raise exception '%', why using errcode = 'P0001'; end if;
  insert into public.room_members (room_id, user_id) values (p_room, me)
  on conflict (room_id, user_id) do update set seen_at = now();
end $$;

-- Find a room by its code and enter it; returns the room id.
create function public.join_room_code(p_code text) returns text language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); r public.rooms; why text;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  select x.* into r from public.rooms x join public.room_codes c on c.room_id = x.id
   where c.code = upper(btrim(coalesce(p_code, '')));
  if not found then raise exception 'not_found' using errcode = 'P0001'; end if;
  why := public.room_refusal(r, me);
  if why is not null then raise exception '%', why using errcode = 'P0001'; end if;
  insert into public.room_members (room_id, user_id) values (r.id, me)
  on conflict (room_id, user_id) do update set seen_at = now();
  return r.id;
end $$;

create function public.create_room(
  p_title text, p_category text, p_max int, p_gender text, p_min_age int, p_max_age int, p_private boolean
) returns table (room_id text, room_code text) language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); t text := btrim(coalesce(p_title, '')); c text; rid text; i int := 0;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
  if p_max is null or p_max not between 2 and 50 then raise exception 'room_size' using errcode = 'P0001'; end if;
  if coalesce(p_gender, 'any') not in ('any', 'female', 'male')
     or coalesce(p_category, 'chat') not in ('chat', 'friends', 'language', 'music', 'movies', 'gaming', 'sports', 'study', 'travel', 'night')
     or coalesce(p_min_age, 18) not between 18 and 100 or coalesce(p_max_age, 100) not between 18 and 100
     or coalesce(p_min_age, 18) > coalesce(p_max_age, 100) then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
  if (select count(*) from public.rooms x where x.owner = me and x.created_at > now() - interval '1 day') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  -- One open room per owner: an older one of theirs closes when a new one opens.
  update public.rooms x set closed_at = now() where x.owner = me and x.closed_at is null;
  loop
    c := (select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '')
            from generate_series(1, 6));
    exit when not exists (select 1 from public.room_codes x where x.code = c);
    i := i + 1;
    if i > 20 then raise exception 'try_again' using errcode = 'P0001'; end if;
  end loop;
  rid := 'u-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 16);
  insert into public.rooms (id, title, emoji, sort, owner, category, max_members, gender_rule, min_age, max_age, is_private)
  values (rid, t,
          case coalesce(p_category, 'chat') when 'friends' then '🤝' when 'language' then '🗣️' when 'music' then '🎧'
            when 'movies' then '🎬' when 'gaming' then '🎮' when 'sports' then '⚽' when 'study' then '🎓'
            when 'travel' then '✈️' when 'night' then '🌙' else '💬' end,
          1000, me, coalesce(p_category, 'chat'), p_max, coalesce(p_gender, 'any'),
          coalesce(p_min_age, 18), coalesce(p_max_age, 100), coalesce(p_private, false));
  insert into public.room_codes (room_id, code) values (rid, c);
  insert into public.room_members (room_id, user_id) values (rid, me);
  return query select rid, c;
end $$;

-- The owner closes their room: nobody can enter or write any more, and it leaves every list.
create function public.close_room(p_room text) returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.rooms set closed_at = now() where id = p_room and owner = auth.uid() and closed_at is null;
  if not found then raise exception 'not_allowed' using errcode = 'P0001'; end if;
end $$;

-- Rooms for the new apps: official ones first, then live public user rooms, plus any open room I own or am in.
-- `code` only for my own rooms; `refusal` says why I couldn't enter (null = I can).
create function public.room_list2()
returns table (id text, title text, emoji text, online int, official boolean, mine boolean, owner_name text,
               category text, max_members int, gender_rule text, min_age int, max_age int, is_private boolean,
               code text, refusal text)
language sql stable security definer set search_path = '' as $$
  select r.id, r.title, r.emoji, public.room_online(r.id), r.owner is null, coalesce(r.owner = auth.uid(), false), o.name,
         r.category, r.max_members, r.gender_rule, r.min_age, r.max_age, r.is_private,
         case when r.owner = auth.uid() then c.code end,
         public.room_refusal(r, auth.uid())
    from public.rooms r
    left join public.profiles o on o.id = r.owner
    left join public.room_codes c on c.room_id = r.id
   where r.owner is null
      or (public.room_alive(r) and not public.is_blocked(auth.uid(), r.owner)
          and (not r.is_private or r.owner = auth.uid()
               or exists (select 1 from public.room_members m where m.room_id = r.id and m.user_id = auth.uid())))
   order by r.owner is not null, r.sort, public.room_online(r.id) desc, r.created_at desc
   limit 200
$$;

-- A closed room takes no more messages, even from people still inside.
create or replace function public.room_message_guard() returns trigger language plpgsql security definer set search_path = '' as $$
declare p public.profiles;
begin
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

revoke execute on function public.room_alive(public.rooms), public.room_refusal(public.rooms, uuid) from public, anon, authenticated;
revoke execute on function public.room_online(text), public.can_read_room(text), public.join_room_code(text),
  public.create_room(text, text, int, text, int, int, boolean), public.close_room(text), public.room_list2() from public, anon;
grant execute on function public.room_online(text), public.can_read_room(text), public.join_room_code(text),
  public.create_room(text, text, int, text, int, int, boolean), public.close_room(text), public.room_list2() to authenticated;
