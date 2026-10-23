-- 1. Random chat: choose the countries to meet (free; none = anywhere). Both sides' choices must fit: I see only
--    people from my countries, and only people whose countries include mine see me. find_match3(target, lang,
--    countries); find_match2 stays for installed apps (= anywhere).
-- 2. Manners are off for now: a low manner score no longer closes random chat (ratings are still kept; the apps
--    stop asking and showing them).

alter table public.queue add column if not exists country text, add column if not exists want text[];

create or replace function public.find_match3(p_target text, p_lang text, p_countries text[]) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  my_country text;
  my_tags text[];
  banned timestamptz;
  my_lang text := public.app_lang(p_lang);
  my_want text[] := nullif(p_countries, '{}');
  my_since timestamptz;
  m public.matches;
  mid uuid;
  last_partner uuid;
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target is null or p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  if cardinality(my_want) > 10 or exists (select 1 from unnest(my_want) c where c is null or c !~ '^[A-Z]{2}$') then
    raise exception 'bad_request' using errcode = 'P0001';
  end if;
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

  select case when x.user_a = me then x.user_b else x.user_a end into last_partner
    from public.matches x where me in (x.user_a, x.user_b) order by x.created_at desc limit 1;

  insert into public.queue as q (user_id, gender, target, lang, interests, country, want)
  values (me, my_gender, p_target, my_lang, my_tags, my_country, my_want)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang, interests = excluded.interests,
    country = excluded.country, want = excluded.want,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end,
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
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and (my_want is null or q.country = any(my_want))
     and (q.want is null or my_country = any(q.want))
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = q.user_id)
     and not exists (select 1 from public.blocks b where b.blocker = q.user_id and b.blocked = me)
     and (q.user_id is distinct from last_partner
          or (my_since < now() - interval '10 seconds' and q.joined_at < now() - interval '10 seconds'))
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by (q.lang is not distinct from my_lang) desc,
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
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;
revoke execute on function public.find_match3(text, text, text[]) from public, anon;
grant execute on function public.find_match3(text, text, text[]) to authenticated;

create or replace function public.find_match2(p_target text, p_lang text) returns uuid
language sql security definer set search_path = '' as $$ select public.find_match3(p_target, p_lang, null) $$;
