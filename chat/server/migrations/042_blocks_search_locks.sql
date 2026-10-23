-- 1. Blocks: my list and unblock (Settings). Unblocking does not bring back the friendship or old chats.
-- 2. Find people by ID: prefix search; a person found this way can be viewed and asked for friendship (can_view)
--    for a day. At most 600 people found per hour, against scraping.
-- 3. Gender and date of birth stay with the phone number: deleting the account and signing up again with the same
--    number gets them back (a salted hash of the number; used for a year after the deletion, older rows are ignored). Country already comes from the number
--    (007) or the ID document (035); none of the three can be edited (no column grant).
-- 4. ID changes: once every 30 days; sooner with a paid "ID change" (Play consumable handle_change, one per change).

-- 1 -----------------------------------------------------------------------------------------------------------------
create or replace function public.my_blocks() returns table (id uuid, handle text, name text, photo_path text, blocked_at timestamptz)
language sql stable security definer set search_path = '' as $$
  select p.id, p.handle, p.name, p.photo_path, b.created_at from public.blocks b join public.profiles p on p.id = b.blocked
   where b.blocker = auth.uid() order by b.created_at desc
$$;
create or replace function public.unblock_user(p_user uuid) returns void language sql security definer set search_path = '' as $$
  delete from public.blocks where blocker = auth.uid() and blocked = p_user
$$;
revoke execute on function public.my_blocks(), public.unblock_user(uuid) from public, anon;
grant execute on function public.my_blocks(), public.unblock_user(uuid) to authenticated;

-- 2 -----------------------------------------------------------------------------------------------------------------
create table if not exists public.handle_lookups (
  viewer uuid not null references public.profiles(id) on delete cascade,
  other uuid not null references public.profiles(id) on delete cascade,
  at timestamptz not null default now(),
  primary key (viewer, other)
);
alter table public.handle_lookups enable row level security;
revoke all on public.handle_lookups from public, anon, authenticated;
create index if not exists profiles_handle_prefix on public.profiles ((lower(handle) collate "C"));

create or replace function public.search_handles(p_q text) returns table (id uuid, handle text, name text, photo_path text, country text, look jsonb)
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid();
  q text := lower(normalize(ltrim(btrim(coalesce(p_q, '')), '@'), NFC));
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if q = '' or char_length(q) > 24 then return; end if;
  if (select count(*) from public.handle_lookups l where l.viewer = me and l.at > now() - interval '1 hour') >= 600 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return query
    with hit as (
      select p.id, p.handle, p.name, p.photo_path, p.country, public.look_json(p) as look from public.profiles p
       where lower(p.handle) collate "C" like replace(replace(replace(q, '\', '\\'), '%', '\%'), '_', '\_') || '%'
         and p.id <> me and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(me, p.id)
       order by lower(p.handle) collate "C" limit 20
    ), seen as (
      insert into public.handle_lookups (viewer, other) select me, h.id from hit h
      on conflict (viewer, other) do update set at = now()
    )
    select h.id, h.handle, h.name, h.photo_path, h.country, h.look from hit h order by lower(h.handle) collate "C";
end $$;
revoke execute on function public.search_handles(text) from public, anon;
grant execute on function public.search_handles(text) to authenticated;

create or replace function public.can_view(other uuid) returns boolean language sql stable security definer set search_path = '' as $$
  select other = auth.uid() or (
    not public.is_blocked(auth.uid(), other) and (
      exists (select 1 from public.handle_lookups l where l.viewer = auth.uid() and l.other = can_view.other and l.at > now() - interval '1 day')
      or exists (select 1 from public.matches m
               where (m.user_a = auth.uid() and m.user_b = other) or (m.user_b = auth.uid() and m.user_a = other))
      or exists (select 1 from public.friendships f
                  where (f.requester = other and f.addressee = auth.uid())
                     or (f.requester = auth.uid() and f.addressee = other and f.status = 'accepted'))
      or exists (select 1 from public.voice_members a join public.voice_members b on b.room_id = a.room_id
                  where a.user_id = auth.uid() and b.user_id = other and not a.kicked
                    and a.seen_at > now() - interval '90 seconds' and b.seen_at > now() - interval '30 minutes')
      or exists (select 1 from public.posts pp where pp.user_id = other and pp.visibility = 'public')
      or exists (select 1 from public.voice_members s join public.voice_rooms r on r.id = s.room_id
                  where s.user_id = other and s.seat is not null and r.ended_at is null
                    and s.seen_at > now() - interval '2 minutes')))
$$;

-- 3 -----------------------------------------------------------------------------------------------------------------
create table if not exists public.phone_identities (
  phone_hash text primary key, gender text not null, birth_date date not null, saved_at timestamptz not null default now()
);
alter table public.phone_identities enable row level security;
revoke all on public.phone_identities from public, anon, authenticated;

create or replace function public.remember_ban(uid uuid) returns void language sql security definer set search_path = '' as $$
  insert into public.banned_phones (phone_hash, banned_until)
  select public.phone_hash(u.phone), p.banned_until
    from auth.users u join public.profiles p on p.id = u.id
   where u.id = uid and p.banned_until > now() and u.phone is not null
  on conflict (phone_hash) do update set banned_until = greatest(public.banned_phones.banned_until, excluded.banned_until);
  insert into public.phone_identities (phone_hash, gender, birth_date)
  select public.phone_hash(u.phone), p.gender, p.birth_date
    from auth.users u join public.profiles p on p.id = u.id where u.id = uid and u.phone is not null
  on conflict (phone_hash) do update set gender = excluded.gender, birth_date = excluded.birth_date, saved_at = now();
$$;

create or replace function public.check_adult() returns trigger language plpgsql security definer set search_path = '' as $$
declare carried timestamptz; kept public.phone_identities;
begin
  select k.* into kept from public.phone_identities k join auth.users u on k.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and k.saved_at > now() - interval '1 year';
  if kept.phone_hash is not null then new.gender := kept.gender; new.birth_date := kept.birth_date; end if;
  if new.birth_date > (current_date - interval '18 years')::date then
    raise exception 'under_18' using errcode = 'P0001';
  end if;
  if new.birth_date < (current_date - interval '100 years')::date then
    raise exception 'invalid_birth_date' using errcode = 'P0001';
  end if;
  select b.banned_until into carried from public.banned_phones b join auth.users u on b.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and b.banned_until > now();
  if carried is not null then new.banned_until := carried; end if;
  return new;
end $$;

-- 4 -----------------------------------------------------------------------------------------------------------------
alter table public.profiles add column if not exists handle_passes smallint not null default 0 check (handle_passes >= 0);

create or replace function public.set_handle(p_handle text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); h text := normalize(btrim(coalesce(p_handle, '')), NFC); cur public.profiles; paid boolean;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('handle:' || lower(h)));
  select * into cur from public.profiles p where p.id = me for update;
  if not found then raise exception 'no_profile' using errcode = 'P0001'; end if;
  if not public.handle_valid(h) then raise exception 'handle_invalid' using errcode = 'P0001'; end if;
  if cur.handle = h then return h; end if;
  if public.handle_reserved(h) and lower(h) <> lower(cur.handle) then raise exception 'handle_reserved' using errcode = 'P0001'; end if;
  if exists (select 1 from public.profiles p where lower(p.handle) = lower(h) and p.id <> me) then
    raise exception 'handle_taken' using errcode = 'P0001';
  end if;
  paid := lower(cur.handle) <> lower(h) and cur.handle_set_at > now() - interval '30 days';
  if paid and cur.handle_passes < 1 then raise exception 'handle_cooldown' using errcode = 'P0001'; end if;
  update public.profiles set handle = h,
         handle_set_at = case when lower(cur.handle) = lower(h) then cur.handle_set_at else now() end,
         handle_passes = handle_passes - case when paid then 1 else 0 end
   where id = me;
  return h;
exception when unique_violation then raise exception 'handle_taken' using errcode = 'P0001';
end $$;

create or replace function public.shop() returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare me uuid := auth.uid(); p public.profiles;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  select * into p from public.profiles x where x.id = me;
  return jsonb_build_object(
    'items', (select coalesce(jsonb_agg(jsonb_build_object('id', i.id, 'kind', i.kind, 'price_inr', i.price_inr) order by i.kind, i.sort), '[]')
                from public.cosmetic_items i where i.active),
    'owned', (select coalesce(jsonb_agg(u.item_id order by u.acquired_at), '[]') from public.user_cosmetics u where u.user_id = me),
    'look', case when p.id is null then null else public.look_json(p) end,
    'handle_next', case when p.handle_set_at is null then null else p.handle_set_at + interval '30 days' end,
    'handle_passes', coalesce(p.handle_passes, 0));
end $$;

-- verify-purchase (service role) after Play confirmed the purchase: granted | already | other_account | revoked.
create or replace function public.redeem_handle_pass(p_user uuid, p_token text) returns text
language plpgsql security definer set search_path = '' as $$
declare pu public.purchases;
begin
  if p_user is null or p_token is null then raise exception 'bad_request' using errcode = 'P0001'; end if;
  insert into public.purchases (token, user_id, product_id) values (p_token, p_user, 'handle_change') on conflict (token) do nothing;
  if found then
    update public.profiles set handle_passes = handle_passes + 1 where id = p_user;
    return 'granted';
  end if;
  select * into pu from public.purchases x where x.token = p_token;
  if pu.user_id <> p_user then return 'other_account'; end if;
  if pu.revoked_at is not null then return 'revoked'; end if;
  return 'already';
end $$;
revoke all on function public.redeem_handle_pass(uuid, text) from public, anon, authenticated;
grant execute on function public.redeem_handle_pass(uuid, text) to service_role;

-- A refunded ID change takes back an unused one.
create or replace function public.play_revoke(p_token text, p_reason text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare pu public.purchases; owner uuid; premium boolean; item text;
begin
  select * into pu from public.purchases x where x.token = p_token;
  owner := coalesce(pu.user_id, (select s.user_id from public.subscriptions s where s.purchase_token = p_token limit 1));
  if owner is null then return jsonb_build_object('found', false); end if;
  update public.purchases set revoked_at = coalesce(revoked_at, now()), revoke_reason = coalesce(revoke_reason, left(p_reason, 40))
   where token = p_token;
  if pu.product_id = 'handle_change' then
    if pu.revoked_at is null then
      update public.profiles set handle_passes = greatest(handle_passes - 1, 0) where id = owner;
    end if;
    return jsonb_build_object('found', true, 'user', owner, 'product', pu.product_id);
  end if;
  item := (select i.id from public.cosmetic_items i where i.id = pu.product_id);
  if item is not null then
    delete from public.user_cosmetics u where u.user_id = owner and u.item_id = item;
    update public.profiles set
      look_badge = nullif(look_badge, item), look_color = nullif(look_color, item),
      look_font = nullif(look_font, item), look_frame = nullif(look_frame, item)
     where id = owner;
    return jsonb_build_object('found', true, 'user', owner, 'product', item);
  end if;
  premium := coalesce(pu.product_id, 'yaari_premium_monthly') = 'yaari_premium_monthly';
  if premium then
    update public.subscriptions set premium_until = least(premium_until, now()), updated_at = now() where user_id = owner;
  else
    update public.subscriptions set gender_until = greatest(now(), gender_until - interval '24 hours'), updated_at = now()
     where user_id = owner and gender_until > now() and pu.revoked_at is null;
  end if;
  return jsonb_build_object('found', true, 'user', owner, 'product', coalesce(pu.product_id, 'yaari_premium_monthly'));
end $$;

-- Kept for a year after the deletion, then removed (daily at 03:23 UTC).
select cron.schedule('yaari-phone-identities', '23 3 * * *', $$delete from public.phone_identities where saved_at < now() - interval '1 year'$$);
