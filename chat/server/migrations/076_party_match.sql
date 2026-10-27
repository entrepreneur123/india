-- 49 (the owner, 2026-10-27): many-to-many random chat. A friends' group chat (069) is a party: the members who say
-- "I'm in" (ready, kept alive by the party sheet every few seconds) search together, and the server pairs the party
-- with another searching party — any sizes (2 with 5 is fine), at most 50 people in all. Both parties then meet in a
-- new group chat (group_chats.kind 'party'), titled "their group × our group".
-- Matching follows random chat: my country, or both searching everywhere; nobody blocked between the two parties.
-- party_state(group, ready): my heartbeat and the party's state (who's in, searching, the chat it was paired into).
-- party_search(group, everywhere): searches (or keeps searching); returns the new chat once paired, else null.

alter table public.group_chats add column if not exists kind text not null default 'friends';
alter table public.group_members add column if not exists ready_at timestamptz;

create table if not exists public.party_queue (
  group_id uuid primary key references public.group_chats (id) on delete cascade,
  members uuid[] not null,
  country text,
  global boolean not null default true,
  joined_at timestamptz not null default now(),
  seen_at timestamptz not null default now(),
  matched uuid references public.group_chats (id) on delete set null,
  matched_at timestamptz
);
alter table public.party_queue enable row level security;
revoke all on public.party_queue from public, anon, authenticated;

create or replace function public.party_state(p_group uuid, p_ready boolean) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  update public.group_members set ready_at = case when p_ready then now() end where group_id = p_group and user_id = me;
  return jsonb_build_object(
    'ready', coalesce((select jsonb_agg(m.user_id) from public.group_members m
                        where m.group_id = p_group and m.left_at is null and m.ready_at > now() - interval '30 seconds'), '[]'),
    'searching', exists (select 1 from public.party_queue q where q.group_id = p_group and q.matched is null
                           and q.seen_at > now() - interval '20 seconds'),
    'matched', (select q.matched from public.party_queue q where q.group_id = p_group and me = any (q.members)
                  and q.matched_at > now() - interval '2 minutes'));
end $$;

create or replace function public.party_search(p_group uuid, p_global boolean default true) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); mine uuid[]; my_country text; other public.party_queue; g uuid; everyone uuid[];
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if exists (select 1 from public.profiles p where p.id = me and p.banned_until > now()) then raise exception 'banned' using errcode = 'P0001'; end if;
  perform public.require_verified(me);
  perform pg_advisory_xact_lock(hashtext('party:' || p_group::text));
  update public.group_members set ready_at = now() where group_id = p_group and user_id = me;

  -- Paired a moment ago (by the other party, or another of us): that chat.
  select q.matched into g from public.party_queue q
   where q.group_id = p_group and me = any (q.members) and q.matched_at > now() - interval '2 minutes';
  if g is not null then return g; end if;

  mine := array(select m.user_id from public.group_members m join public.profiles p on p.id = m.user_id
                 where m.group_id = p_group and m.left_at is null and m.ready_at > now() - interval '30 seconds'
                   and (p.banned_until is null or p.banned_until < now()));
  if cardinality(mine) < 2 then raise exception 'party_small' using errcode = 'P0001'; end if;
  select p.country into my_country from public.profiles p where p.id = me;

  insert into public.party_queue as q (group_id, members, country, global)
  values (p_group, mine, my_country, coalesce(p_global, true))
  on conflict (group_id) do update set members = excluded.members, country = excluded.country, global = excluded.global,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end,
    seen_at = now(), matched = null, matched_at = null;

  select q.* into other from public.party_queue q
   where q.group_id <> p_group and q.matched is null and q.seen_at > now() - interval '20 seconds'
     and not (q.members && mine)
     and cardinality(q.members) + cardinality(mine) <= 50
     and (q.country is not distinct from my_country or (q.global and coalesce(p_global, true)))
     and not exists (select 1 from unnest(q.members) a cross join unnest(mine) b where public.is_blocked(a, b))
   order by q.joined_at
   limit 1
   for update skip locked;
  if other.group_id is null then return null; end if;

  everyone := mine || other.members;
  insert into public.group_chats (title, owner, kind)
  values (left(coalesce((select c.title from public.group_chats c where c.id = other.group_id), '') || ' × '
               || coalesce((select c.title from public.group_chats c where c.id = p_group), ''), 40), me, 'party')
  returning id into g;
  insert into public.group_members (group_id, user_id) select g, u from unnest(everyone) u on conflict do nothing;
  perform public.group_line(g, me, 'created', (select string_agg(p.name, ', ') from public.profiles p where p.id = any (everyone) and p.id <> me));
  update public.party_queue set matched = g, matched_at = now(), seen_at = '-infinity' where group_id in (p_group, other.group_id);
  return g;
end $$;

create or replace function public.party_cancel(p_group uuid) returns void
language sql security definer set search_path = '' as $$
  update public.party_queue set seen_at = '-infinity' where group_id = p_group and public.group_member(p_group) and matched is null;
$$;

-- A party chat doesn't count against the 20 groups a day I may make myself.
create or replace function public.group_create(p_title text, p_members uuid[]) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); t text := btrim(coalesce(p_title, '')); g uuid; names text;
begin
  if char_length(t) not between 1 and 40 or cardinality(p_members) not between 1 and 49 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  if (select count(*) from public.group_chats x where x.owner = me and x.kind = 'friends' and x.created_at > now() - interval '1 day') >= 20 then
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

revoke execute on function public.party_state(uuid, boolean), public.party_search(uuid, boolean), public.party_cancel(uuid) from public, anon;
grant execute on function public.party_state(uuid, boolean), public.party_search(uuid, boolean), public.party_cancel(uuid) to authenticated;
