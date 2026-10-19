-- Matching that scales and prefers a shared language.
--
-- 1. No global lock. Before, every find_match call (one per searching user every 2 s) waited on one advisory
--    lock, so all searches in the country ran one at a time. Now each call locks only its own queue row and
--    skips rows that another call is holding (FOR UPDATE SKIP LOCKED), so two calls can never pair the same
--    person twice, and searches run in parallel. A per-user lock keeps one person's two tabs from racing.
-- 2. Same language first. People are paired with someone who uses the app in the same language (Hindi with
--    Hindi, Portuguese with Portuguese, ...) when one is waiting. After 8 s of waiting, on either side, anyone
--    goes, so nobody waits longer because of it.
-- 3. Fair queue position. A matched (or long-gone) queue row is marked stale instead of removed; joining again
--    starts a new place in the line, so an old join time can't jump the queue.
--
-- find_match(p_target) stays for app versions that don't send a language.

alter table public.queue add column if not exists lang text;

create or replace function public.find_match2(p_target text, p_lang text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
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
  select p.gender, p.banned_until into my_gender, banned from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(hashtext('match:' || me::text));

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

  -- My place in the line. This row stays locked until the end of this call, so nobody else can take me meanwhile.
  insert into public.queue as q (user_id, gender, target, lang) values (me, my_gender, p_target, my_lang)
  on conflict (user_id) do update set
    gender = excluded.gender, target = excluded.target, lang = excluded.lang,
    joined_at = case when q.seen_at < now() - interval '20 seconds' then now() else q.joined_at end,
    seen_at = now()
  returning q.joined_at into my_since;

  select q.user_id into partner
    from public.queue q join public.profiles p on p.id = q.user_id
   where q.user_id <> me
     and q.seen_at > now() - interval '20 seconds'
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and (p.banned_until is null or p.banned_until < now())
     and not public.is_blocked(me, q.user_id)
     and q.user_id is distinct from last_partner
     and (my_lang is null or q.lang is null or q.lang = my_lang
          or my_since < now() - interval '8 seconds' or q.joined_at < now() - interval '8 seconds')
   order by (q.lang is not distinct from my_lang) desc, q.joined_at
   limit 1
   for update of q skip locked;

  if partner is null then return null; end if;

  update public.queue set seen_at = '-infinity' where user_id in (me, partner);
  insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
  return mid;
end $$;

revoke execute on function public.find_match2(text, text) from public, anon;
grant execute on function public.find_match2(text, text) to authenticated;

create or replace function public.find_match(p_target text) returns uuid
language sql security definer set search_path = '' as $$
  select public.find_match2(p_target, null)
$$;

-- Leaving the queue marks the row stale (same effect as removing it: nobody can be paired with it).
create or replace function public.cancel_search() returns void language sql security definer set search_path = '' as $$
  update public.queue set seen_at = '-infinity' where user_id = auth.uid()
$$;
