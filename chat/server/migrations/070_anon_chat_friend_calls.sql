-- The owner, 2026-10-27: random chat is text only, as an anonymous chat or a chat with my profile.
-- 1. Anonymous chat: find_match4 = find_match3 + p_anon; anonymous searchers meet only each other (queue.anon) and the
--    match remembers it (matches.anon). match_card2 = match_card + anon: a stranger's card keeps only the gender and the
--    country. An anonymous chat leaves no trace afterwards: not in Recent or its badges (my_recents), not a reason to
--    see the profile or the friends-only posts (can_view, post_visible), not a friend's thread (friend_thread).
--    find_match3 and match_card stay for installed apps as wrappers (a drop needs the owner's confirmation).
-- 2. Calls only between friends, in their thread: a live random chat (ended_at null) can no longer call.

alter table public.queue add column if not exists anon boolean not null default false;
alter table public.matches add column if not exists anon boolean not null default false;

-- 1 ------------------------------------------------------------------------------------------------------------------
create or replace function public.find_match4(p_target text, p_lang text, p_countries text[], p_anon boolean default false) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  my_country text;
  my_tags text[];
  banned timestamptz;
  my_lang text := public.app_lang(p_lang);
  my_since timestamptz;
  want_global boolean := coalesce(cardinality(p_countries), 0) = 0;
  want_anon boolean := coalesce(p_anon, false);
  m public.matches;
  mid uuid;
  met uuid[];
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target is null or p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  select p.gender, p.country, p.banned_until, nullif(p.interests, '{}') into my_gender, my_country, banned, my_tags
    from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  perform public.require_verified(me);
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(hashtext('match:' || me::text));
  -- A new search (not a poll of a running one) leaves every room: one live session at a time (031).
  if not exists (select 1 from public.queue q0 where q0.user_id = me and q0.seen_at > now() - interval '20 seconds') then
    perform public.voice_leave_all(me);
  end if;
  perform 1 from public.queue q where q.user_id = me for update;

  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then return mid; end if;

  for m in select * from public.matches x where me in (x.user_a, x.user_b) and x.ended_at is null loop
    if now() - m.created_at < public.exit_lock() then raise exception 'exit_locked' using errcode = 'P0001'; end if;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none' where id = m.id;
  end loop;

  -- The people of my last 200 chats, newest first (30, the owner: they come after everyone new; met[1] is the last one).
  select array_agg(t.p order by t.at desc) into met from (
    (select x.user_b as p, x.created_at as at from public.matches x where x.user_a = me order by x.created_at desc limit 200)
    union all
    (select x.user_a, x.created_at from public.matches x where x.user_b = me order by x.created_at desc limit 200)
  ) t;

  insert into public.queue as q (user_id, gender, target, lang, interests, country, global, anon)
  values (me, my_gender, p_target, my_lang, my_tags, my_country, want_global, want_anon)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang, interests = excluded.interests,
    country = excluded.country, global = excluded.global, anon = excluded.anon,
    joined_at = case when q.seen_at < now() - interval '20 seconds' or q.anon <> excluded.anon then now() else q.joined_at end,
    seen_at = now()
  returning q.joined_at into my_since;

  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then
    update public.queue set seen_at = '-infinity' where user_id = me;
    return mid;
  end if;

  select q.user_id into partner
    from public.queue q
   where q.seen_at > now() - interval '20 seconds'
     and q.user_id <> me
     and q.anon = want_anon
     and (q.country = my_country or (want_global and q.global))
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = q.user_id)
     and not exists (select 1 from public.blocks b where b.blocker = q.user_id and b.blocked = me)
     and (q.user_id is distinct from met[1]
          or (my_since < now() - interval '5 seconds' and q.joined_at < now() - interval '5 seconds'))
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by coalesce(q.user_id = any(met), false),
            (q.lang is not distinct from my_lang) desc,
            (coalesce(q.interests && my_tags, false) or q.joined_at < now() - interval '10 seconds') desc,
            q.joined_at
   limit 1
   for update of q skip locked;

  if partner is null then return null; end if;
  if exists (select 1 from public.profiles p where p.id = partner and p.banned_until > now()) then
    update public.queue set seen_at = '-infinity' where user_id = partner;
    return null;
  end if;

  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b, anon) values (partner, me, want_anon) returning id into mid;
  return mid;
end $$;

create or replace function public.find_match3(p_target text, p_lang text, p_countries text[]) returns uuid
language sql security definer set search_path = '' as $$ select public.find_match4(p_target, p_lang, p_countries, false) $$;

-- A bot meets an anonymous searcher anonymously too.
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
     where y.active and (q.global or p.country = q.country) and (q.target = 'any' or p.gender = q.target)
       and y.id is distinct from last_partner
       and not public.is_blocked(y.id, q.user_id)
       and not exists (select 1 from public.matches x where x.user_a = y.id and x.ended_at is null)
       and not exists (select 1 from public.matches x where x.user_b = y.id and x.ended_at is null)
     order by (y.lang = q.lang) desc, random() limit 1;
    continue when b is null;
    update public.queue set seen_at = '-infinity' where user_id = q.user_id;
    insert into public.matches (user_a, user_b, anon) values (q.user_id, b, q.anon);
  end loop;
end $$;

-- The partner's card for a match I am in (card()'s rules); a stranger's is only the gender and the country.
create or replace function public.match_card2(p_match uuid)
returns table(id uuid, name text, gender text, age integer, city text, bio text, photo_path text, country text, anon boolean)
language sql stable security definer set search_path = '' as $$
  select c.*, false from public.matches m
   cross join lateral public.card(case when m.user_a = auth.uid() then m.user_b else m.user_a end) c
   where m.id = p_match and auth.uid() in (m.user_a, m.user_b) and not m.anon
  union all
  select p.id, null, p.gender, null, null, null, null, p.country, true from public.matches m
    join public.profiles p on p.id = case when m.user_a = auth.uid() then m.user_b else m.user_a end
   where m.id = p_match and auth.uid() in (m.user_a, m.user_b) and m.anon
$$;

create or replace function public.match_card(p_match uuid)
returns table(id uuid, name text, gender text, age integer, city text, bio text, photo_path text, country text)
language sql stable security definer set search_path = '' as $$
  select c.id, c.name, c.gender, c.age, c.city, c.bio, c.photo_path, c.country from public.match_card2(p_match) c
$$;

create or replace function public.my_recents()
returns table (match_id uuid, partner uuid, met_at timestamptz, last_body text)
language sql stable security definer set search_path = '' as $$
  select distinct on (o.partner) o.id, o.partner, o.created_at,
         (select coalesce(msg.body, '[media]') from public.messages msg where msg.match_id = o.id order by msg.id desc limit 1)
    from (select x.id, x.created_at, x.kind, case when x.user_a = auth.uid() then x.user_b else x.user_a end as partner
            from public.matches x where auth.uid() in (x.user_a, x.user_b) and x.kind in ('random', 'dm') and not x.anon) o
   where not public.is_blocked(auth.uid(), o.partner)
     and (o.kind = 'random' or not public.are_friends(auth.uid(), o.partner))
     and not exists (select 1 from public.recent_hidden h where h.user_id = auth.uid() and h.partner = o.partner and h.hidden_at > o.created_at)
   order by o.partner, o.created_at desc
$$;

create or replace function public.can_view(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.profiles x where x.id = other and not x.anon and (x.banned_until is null or x.banned_until < now()))
      or exists (select 1 from public.handle_lookups l where l.viewer = auth.uid() and l.other = can_view.other and l.at > now() - interval '1 day')
      or exists (select 1 from public.matches m
               where ((m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other)) and not m.anon)
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid()) or (f.requester = auth.uid() and f.addressee = other))
      or exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other and not a.kicked
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id
                  where s.user_id = other and s.seat is not null and r.ended_at is null
                    and s.seen_at > now() - interval '2 minutes')
      or exists (select 1 from public.notifications n where n.actor = other and n.user_id = auth.uid())))
$$;

create or replace function public.post_visible(p_author uuid, p_visibility text) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_author = auth.uid() or (
    not public.is_blocked(auth.uid(), p_author) and (
      p_visibility = 'public' or public.are_friends(auth.uid(), p_author)
      or exists (select 1 from public.matches m where ((m.user_a = auth.uid() and m.user_b = p_author)
                                                     or (m.user_b = auth.uid() and m.user_a = p_author)) and not m.anon)))
$$;

create or replace function public.friend_thread(p_user uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); mid uuid; friends boolean := public.are_friends(me, p_user);
begin
  if public.is_blocked(me, p_user)
     or not (friends or exists (select 1 from public.profiles x where x.id = p_user and x.dm_from = 'everyone' and not x.anon)) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select x.id into mid from public.matches x
   where ((x.user_a = me and x.user_b = p_user) or (x.user_b = me and x.user_a = p_user)) and (friends or x.kind = 'dm') and not x.anon
   order by x.created_at desc limit 1;
  if mid is null then
    insert into public.matches (user_a, user_b, kind, ended_at, ended_by) values (me, p_user, 'dm', now(), me) returning id into mid;
  end if;
  return mid;
end $$;

-- 2 ------------------------------------------------------------------------------------------------------------------
create or replace function public.call_open(p_match uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.matches x where x.id = p_match and auth.uid() in (x.user_a, x.user_b)
                   and not public.is_blocked(x.user_a, x.user_b)
                   and x.ended_at is not null and not x.anon and public.are_friends(x.user_a, x.user_b))
$$;

revoke execute on function public.find_match4(text, text, text[], boolean), public.match_card2(uuid) from public, anon;
grant execute on function public.find_match4(text, text, text[], boolean), public.match_card2(uuid) to authenticated;
