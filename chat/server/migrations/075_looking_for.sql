-- 52 (the owner, 2026-10-27): "Looking for…" in Explore — a short line ("anyone up for BGMI tonight?") that people of
-- my country see for 24 hours and answer with a message, friends or not. At most 3 open lines each and 10 a day.
-- Taking a line down keeps it (hidden_at) for reports. Answering opens a message thread with the poster
-- (matches.lfg): such a thread stays open for both even when neither takes messages from everyone (chat_open).

create table if not exists public.lfg_posts (
  id bigserial primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  body text not null check (char_length(body) between 2 and 120),
  country text,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '24 hours',
  hidden_at timestamptz
);
create index if not exists lfg_posts_country on public.lfg_posts (country, id desc) where hidden_at is null;
create index if not exists lfg_posts_user on public.lfg_posts (user_id, created_at desc);
alter table public.lfg_posts enable row level security;
revoke all on public.lfg_posts from public, anon, authenticated;

alter table public.matches add column if not exists lfg boolean not null default false;

create or replace function public.lfg_create(p_body text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); b text := btrim(coalesce(p_body, '')); n bigint;
begin
  if char_length(b) not between 2 and 120 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if exists (select 1 from public.profiles p where p.id = me and p.banned_until > now()) then raise exception 'banned' using errcode = 'P0001'; end if;
  perform public.require_verified(me);
  perform pg_advisory_xact_lock(hashtext('lfg:' || me::text));
  if (select count(*) from public.lfg_posts x where x.user_id = me and x.hidden_at is null and x.expires_at > now()) >= 3
     or (select count(*) from public.lfg_posts x where x.user_id = me and x.created_at > now() - interval '1 day') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.lfg_posts (user_id, body, country) values (me, b, (select p.country from public.profiles p where p.id = me))
  returning id into n;
  return n;
end $$;

-- Open lines of a country (null: mine), newest first, 50 at a time (p_before: older than this id).
create or replace function public.lfg_list(p_country text default null, p_before bigint default null)
returns table (id bigint, body text, created_at timestamptz, expires_at timestamptz, mine boolean,
  author_id uuid, name text, gender text, age int, photo_path text, country text)
language sql stable security definer set search_path = '' as $$
  select x.id, x.body, x.created_at, x.expires_at, x.user_id = auth.uid(),
         p.id, p.name, p.gender, extract(year from age(p.birth_date))::int, p.photo_path, p.country
    from public.lfg_posts x join public.profiles_seen p on p.id = x.user_id
   where x.hidden_at is null and x.expires_at > now()
     and x.country is not distinct from coalesce(upper(nullif(p_country, '')), (select m.country from public.profiles m where m.id = auth.uid()))
     and (p_before is null or x.id < p_before)
     and (p.banned_until is null or p.banned_until < now())
     and not public.is_blocked(auth.uid(), x.user_id)
   order by x.id desc
   limit 50
$$;

create or replace function public.lfg_hide(p_id bigint) returns void
language sql security definer set search_path = '' as $$
  update public.lfg_posts set hidden_at = now() where id = p_id and user_id = auth.uid() and hidden_at is null;
$$;

-- Answering a line: my message thread with its poster (an earlier one between us if there is one).
create or replace function public.lfg_reply(p_id bigint) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); them uuid; mid uuid;
begin
  select x.user_id into them from public.lfg_posts x where x.id = p_id and x.hidden_at is null and x.expires_at > now();
  if them is null then raise exception 'not_found' using errcode = 'P0001'; end if;
  if them = me or public.is_blocked(me, them) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  select x.id into mid from public.matches x
   where ((x.user_a = me and x.user_b = them) or (x.user_b = me and x.user_a = them)) and x.kind = 'dm'
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by, lfg) values (me, them, 'dm', now(), me, true) returning id into mid;
  else
    update public.matches set lfg = true where id = mid;
  end if;
  return mid;
end $$;

create or replace function public.chat_open(mid uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches m where m.id = mid and auth.uid() in (m.user_a, m.user_b)
                 and not public.is_blocked(m.user_a, m.user_b)
                 and (m.ended_at is null or public.are_friends(m.user_a, m.user_b) or (m.kind = 'dm' and m.lfg)
                      or (m.kind = 'dm' and exists (select 1 from public.profiles x where x.id in (m.user_a, m.user_b)
                                                      and x.dm_from = 'everyone' and not x.anon))))
$$;

revoke execute on function public.lfg_create(text), public.lfg_list(text, bigint), public.lfg_hide(bigint), public.lfg_reply(bigint)
  from public, anon;
grant execute on function public.lfg_create(text), public.lfg_list(text, bigint), public.lfg_hide(bigint), public.lfg_reply(bigint)
  to authenticated;
