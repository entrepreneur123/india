-- find_match2 without a profile lookup per waiting person (the 024 interest preference made the search
-- 12-14 ms at 2,000 waiting; this brings it back under 10 ms). Banned people can't search (find_match2 refuses
-- them) and a ban now takes them out of the line at once, so the line holds no banned people to skip.
create or replace function public.queue_drop_banned() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  update public.queue set seen_at = '-infinity' where user_id = new.id and seen_at > '-infinity';
  return null;
end $$;
create or replace trigger profiles_banned_queue after update of banned_until on public.profiles
  for each row when (new.banned_until > now() and new.banned_until is distinct from old.banned_until)
  execute function public.queue_drop_banned();
revoke execute on function public.queue_drop_banned() from public, anon, authenticated;

create or replace function public.find_match2(p_target text, p_lang text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  my_tags text[];
  banned timestamptz;
  my_lang text := case when lower(p_lang) in ('en', 'hi', 'bn', 'pt-br', 'vi') then lower(p_lang) end;
  my_since timestamptz;
  m public.matches;
  mid uuid;
  last_partner uuid;
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target is null or p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  select p.gender, p.banned_until, nullif(p.interests, '{}') into my_gender, banned, my_tags from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(hashtext('match:' || me::text));
  -- My row in the line, locked first. Someone pairing with me holds it (FOR UPDATE below) until their match is
  -- in: this waits for them, so their match is seen next. From here until this call ends nobody can take me.
  perform 1 from public.queue q where q.user_id = me for update;

  -- Someone already paired with me while I was waiting.
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

  insert into public.queue as q (user_id, gender, target, lang, interests) values (me, my_gender, p_target, my_lang, my_tags)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang, interests = excluded.interests,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end,
    seen_at = now()
  returning q.joined_at into my_since;

  -- Belt and braces: a match made for me before my row existed (first search) is taken, not a second one.
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
     and not exists (select 1 from public.blocks b where b.blocker = me and b.blocked = q.user_id)
     and not exists (select 1 from public.blocks b where b.blocker = q.user_id and b.blocked = me)
     and q.user_id is distinct from last_partner
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by (q.lang is not distinct from my_lang) desc,
            (coalesce(q.interests && my_tags, false) or q.joined_at < now() - interval '10 seconds') desc,
            q.joined_at
   limit 1
   for update of q skip locked;

  if partner is null then return null; end if;
  -- Banned people leave the line at once (trigger below); this only covers a ban racing this call.
  if exists (select 1 from public.profiles p where p.id = partner and p.banned_until > now()) then
    update public.queue set seen_at = '-infinity' where user_id = partner;
    return null;
  end if;

  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;
