-- 120: call limits where there were none (docs/DIAGNOSIS.md #6). The app's own cadence sets the rule — the random-chat
-- poll is every 2 s and a room beat every 3 s — so anything faster than once a second is a loop or a script and is
-- answered cheaply instead of running the partner search or the room settle. A friend request to the same person is
-- one an hour (023 already sends at most one notification a day; the hour stops a declined request from reappearing
-- at once without locking out a request withdrawn by mistake for a day). Parties: being "in" needs the same
-- verification as searching, and a member who polls late no longer wipes the party's fresh pairing.
-- Bodies are the live ones (pg_get_functiondef on 2026-10-31: find_match4 = 096, voice_beat = 045, party_* = 076,
-- request_friend = 035) with the marked lines added.

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
  if want_anon then raise exception 'bad_request' using errcode = 'P0001'; end if;
  select p.gender, p.country, p.banned_until, nullif(p.interests, '{}') into my_gender, my_country, banned, my_tags
    from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  perform public.require_verified(me);
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(hashtext('match:' || me::text));
  if not exists (select 1 from public.queue q0 where q0.user_id = me and q0.seen_at > now() - interval '20 seconds') then
    perform public.voice_leave_all(me);
  end if;
  perform 1 from public.queue q where q.user_id = me for update;

  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then return mid; end if;
  -- 120: asked again within a second — nobody paired us (above), so nothing more to do yet.
  if exists (select 1 from public.queue q where q.user_id = me and q.seen_at > now() - interval '1 second') then return null; end if;

  for m in select * from public.matches x where me in (x.user_a, x.user_b) and x.ended_at is null loop
    if now() - m.created_at < public.exit_lock() then raise exception 'exit_locked' using errcode = 'P0001'; end if;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none' where id = m.id;
  end loop;

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
     and (q.country = my_country or (want_global and q.global)
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = q.user_id)
     and not exists (select 1 from public.blocks b where b.blocker = q.user_id and b.blocked = me)
     and (q.user_id is distinct from met[1]
          or (my_since < now() - interval '5 seconds' and q.joined_at < now() - interval '5 seconds'))
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by coalesce(q.user_id = any(met), false),
            (q.country is not distinct from my_country) desc,
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

create or replace function public.voice_beat(p_room text, p_fast boolean) returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); m public.voice_members;
begin
  -- 120: beating again within a second — the row is as fresh as it gets.
  if exists (select 1 from public.voice_members x where x.room_id = p_room and x.user_id = me and x.left_at is null and not x.kicked
               and x.seen_at > now() - interval '1 second') then
    return (select x.rev from public.voice_rooms x where x.id = p_room);
  end if;
  update public.voice_members x set seen_at = now(), lost_at = null, fast = p_fast where x.room_id = p_room and x.user_id = me
     and x.left_at is null and not x.kicked returning * into m;
  if not found then
    if exists (select 1 from public.matches x where x.ended_at is null and x.kind = 'random' and (x.user_a = me or x.user_b = me)) then
      raise exception 'in_random_chat' using errcode = 'P0001';
    end if;
    perform public.voice_enter(p_room, me);
    update public.voice_members x set fast = p_fast where x.room_id = p_room and x.user_id = me;
  else
    perform public.voice_settle(p_room);
  end if;
  if (select x.ended_at from public.voice_rooms x where x.id = p_room) is not null then
    raise exception 'room_closed' using errcode = 'P0001';
  end if;
  return (select x.rev from public.voice_rooms x where x.id = p_room);
end $$;

create or replace function public.request_friend(p_user uuid) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
begin
  if me is null or p_user = me then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('friend:' || me::text));
  perform public.require_verified(me);
  if not public.can_view(p_user) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if public.are_friends(me, p_user) then return 'friends'; end if;
  update public.friendships set status = 'accepted' where requester = p_user and addressee = me;
  if found then return 'friends'; end if;
  -- 120: still pending — as before (the insert below did nothing); a request they declined or I withdrew within the
  -- hour is not sent again yet (the notification of 023 is the record of the last one).
  if exists (select 1 from public.friendships f where f.requester = me and f.addressee = p_user) then return 'requested'; end if;
  if exists (select 1 from public.notifications o where o.user_id = p_user and o.actor = me and o.kind = 'friend_request'
               and o.created_at > now() - interval '1 hour') then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  if (select count(*) from public.friendships f where f.requester = me and f.status = 'pending'
        and f.created_at > now() - interval '1 day') >= 50 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.friendships (requester, addressee) values (me, p_user) on conflict do nothing;
  return 'requested';
end $$;

create or replace function public.party_state(p_group uuid, p_ready boolean) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me();
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if p_ready then perform public.require_verified(me); end if; -- 120: "I'm in" is the gate party_search has
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
declare me uuid := public.voice_me(); mine uuid[]; my_country text; other public.party_queue; g uuid; in_it boolean; everyone uuid[];
  gate boolean := coalesce((select f.require_verification from public.app_flags f where f.id = 1), false);
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if exists (select 1 from public.profiles p where p.id = me and p.banned_until > now()) then raise exception 'banned' using errcode = 'P0001'; end if;
  perform public.require_verified(me);
  perform pg_advisory_xact_lock(hashtext('party:' || p_group::text));
  update public.group_members set ready_at = now() where group_id = p_group and user_id = me;

  -- Paired a moment ago (by the other party, or another of us): that chat. 120: a member who was not in it by then
  -- (said "I'm in" late) gets nothing and leaves the pairing alone — before, their search below wiped it.
  select q.matched, me = any (q.members) into g, in_it from public.party_queue q
   where q.group_id = p_group and q.matched_at > now() - interval '2 minutes';
  if g is not null then return case when in_it then g end; end if;

  mine := array(select m.user_id from public.group_members m join public.profiles p on p.id = m.user_id
                 where m.group_id = p_group and m.left_at is null and m.ready_at > now() - interval '30 seconds'
                   and (p.banned_until is null or p.banned_until < now())
                   and (not gate or p.id_verified_at is not null)); -- 120: the gate for everyone in the party
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
