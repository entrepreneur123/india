-- 123: guest (anonymous) accounts behind a switch (docs/LAUNCH_CHECKLIST.md §3). Phone sign-in needs an SMS provider the
-- project does not have yet, so nobody outside the test numbers can sign in. Supabase's anonymous sign-in is free and
-- gives a real auth user (role authenticated, JWT claim is_anonymous = true), so every RPC and policy works as is.
-- The switch: app_flags.guests — off until the owner has turned "Allow anonymous sign-ins" on in the dashboard; the
-- app reads it through app_config() and the profile trigger refuses an anonymous account while it is off.
-- A guest has no phone, so check_adult keeps the country the app sends (an ISO code from dial_codes) instead of
-- wiping it, and the phone-based rules (one number one account, carried bans, kept identity) simply find nothing.

alter table public.app_flags add column if not exists guests boolean not null default false;

/** What the sign-in screen needs to know before anyone is signed in. */
create or replace function public.app_config() returns jsonb
language sql stable security definer set search_path = '' as $$
  select jsonb_build_object('guests', f.guests, 'verify', f.require_verification) from public.app_flags f where f.id = 1
$$;
revoke execute on function public.app_config() from public;
grant execute on function public.app_config() to anon, authenticated;

create or replace function public.is_guest() returns boolean
language sql stable set search_path = '' as $$
  select coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false)
$$;
revoke execute on function public.is_guest() from public, anon;
grant execute on function public.is_guest() to authenticated;

-- 055's check_adult: a phone's dial code still decides the country; without a phone the app's choice stays.
create or replace function public.check_adult() returns trigger
language plpgsql security definer set search_path = '' as $$
declare carried timestamptz; kept public.phone_identities; dialed text;
begin
  if public.is_guest() and not (select f.guests from public.app_flags f where f.id = 1) then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  select d.country into dialed from auth.users u join public.dial_codes d on u.phone like d.prefix || '%'
   where u.id = new.id order by length(d.prefix) desc limit 1;
  if dialed is not null then new.country := dialed;
  elsif new.country is not null and not exists (select 1 from public.dial_codes d where d.country = new.country) then new.country := null;
  end if;
  select k.* into kept from public.phone_identities k join auth.users u on k.phone_hash = public.phone_hash(u.phone)
   where u.id = new.id and k.saved_at > now() - interval '1 year';
  if kept.phone_hash is not null then new.gender := kept.gender; new.birth_date := kept.birth_date; end if;
  if new.birth_date > (current_date - make_interval(years => public.adult_age(new.country)))::date then
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
