-- 12 (the owner, 2026-10-27): anonymous random chat (070) is gone. Instead anyone may go by a nickname, any photo
-- and any @ID; gender, age and country stay true (set once at sign-up, country from the phone number, kept a year
-- across a deleted account by phone, overwritten only by an ID check or an admin). find_match4 refuses p_anon so a
-- page still open from before can't search anonymously; queue/matches.anon stay for the old rows.

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
  -- 12 (082): no anonymous random chat any more; a page loaded before it asks in vain rather than going on shown.
  if want_anon then raise exception 'bad_request' using errcode = 'P0001'; end if;
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
