-- One-time 24 h gender pass (Play one-time product) next to monthly Premium, plus a purchase ledger.
alter table public.subscriptions alter column premium_until drop not null;
alter table public.subscriptions add column gender_until timestamptz;

-- Every Play purchase token is redeemed once (service role only; no client access).
create table public.purchases (
  token text primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  product_id text not null,
  created_at timestamptz not null default now()
);
create index purchases_user on public.purchases (user_id);
alter table public.purchases enable row level security;

create function public.can_choose_gender(uid uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.subscriptions s where s.user_id = uid and (s.premium_until > now() or s.gender_until > now()))
$$;
revoke execute on function public.can_choose_gender(uuid) from public, anon, authenticated;

create function public.my_entitlements() returns table (premium_until timestamptz, gender_until timestamptz)
language sql stable security definer set search_path = '' as $$
  select s.premium_until, s.gender_until from public.subscriptions s where s.user_id = auth.uid()
$$;
revoke execute on function public.my_entitlements() from public, anon;
grant execute on function public.my_entitlements() to authenticated;

create or replace function public.find_match(p_target text) returns uuid language plpgsql security definer set search_path = '' as $$
declare
  me uuid := auth.uid();
  my_gender text;
  banned timestamptz;
  m public.matches;
  mid uuid;
  last_partner uuid;
  partner uuid;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if p_target not in ('any', 'male', 'female') then raise exception 'bad_target' using errcode = 'P0001'; end if;
  select p.gender, p.banned_until into my_gender, banned from public.profiles p where p.id = me;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if banned > now() then raise exception 'banned' using errcode = 'P0001'; end if;
  if p_target <> 'any' and not public.can_choose_gender(me) then raise exception 'premium_required' using errcode = 'P0001'; end if;

  perform pg_advisory_xact_lock(7340001); -- one matcher at a time: no double matches, no lost wakeups

  -- Someone picked me while I was waiting.
  select x.id into mid from public.matches x
   where x.user_a = me and x.ended_at is null and x.created_at > now() - interval '60 seconds'
   order by x.created_at desc limit 1;
  if mid is not null then return mid; end if;

  -- Searching again ends any chat I am still in, but never before the exit lock (no skipping the 5 s via the API).
  for m in select * from public.matches x where me in (x.user_a, x.user_b) and x.ended_at is null loop
    if now() - m.created_at < public.exit_lock() then raise exception 'exit_locked' using errcode = 'P0001'; end if;
    update public.matches set ended_at = now(), ended_by = me, video_status = 'none' where id = m.id;
  end loop;

  select case when x.user_a = me then x.user_b else x.user_a end into last_partner
    from public.matches x where me in (x.user_a, x.user_b) order by x.created_at desc limit 1;

  select q.user_id into partner
    from public.queue q join public.profiles p on p.id = q.user_id
   where q.user_id <> me
     and q.seen_at > now() - interval '20 seconds'
     and (p_target = 'any' or q.gender = p_target)
     and (q.target = 'any' or q.target = my_gender)
     and (p.banned_until is null or p.banned_until < now())
     and not public.is_blocked(me, q.user_id)
     and q.user_id is distinct from last_partner
   order by q.joined_at
   limit 1;

  if partner is not null then
    delete from public.queue where user_id in (me, partner);
    insert into public.matches (user_a, user_b) values (partner, me) returning id into mid;
    return mid;
  end if;

  insert into public.queue (user_id, gender, target) values (me, my_gender, p_target)
  on conflict (user_id) do update set gender = excluded.gender, target = excluded.target, seen_at = now();
  return null;
end $$;

