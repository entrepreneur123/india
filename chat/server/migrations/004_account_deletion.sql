-- In-app account deletion (Google Play requirement) without letting banned users start over:
-- the phone number of an account deleted while banned is kept as a salted hash until the ban ends.
create table public.banned_phones (
  phone_hash text primary key,
  banned_until timestamptz not null
);
alter table public.banned_phones enable row level security;

create function public.phone_hash(phone text) returns text language sql immutable set search_path = '' as $$
  select encode(extensions.digest('yaari:' || coalesce(phone, ''), 'sha256'), 'hex')
$$;
revoke execute on function public.phone_hash(text) from public, anon, authenticated;

-- Called by the delete-account function (service role) right before the auth user is removed.
create function public.remember_ban(uid uuid) returns void language sql security definer set search_path = '' as $$
  insert into public.banned_phones (phone_hash, banned_until)
  select public.phone_hash(u.phone), p.banned_until
    from auth.users u join public.profiles p on p.id = u.id
   where u.id = uid and p.banned_until > now() and u.phone is not null
  on conflict (phone_hash) do update set banned_until = greatest(public.banned_phones.banned_until, excluded.banned_until)
$$;
revoke execute on function public.remember_ban(uuid) from public, anon, authenticated;

-- A new profile on a number that was banned keeps the ban.
create or replace function public.check_adult() returns trigger language plpgsql security definer set search_path = '' as $$
declare carried timestamptz;
begin
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
