-- Recent searches (owner 2026-11-01, like Instagram): the people I opened from a search, newest first, each removable
-- and all at once. Kept on handle_lookups (search_handles writes a row per person a search showed me; it already goes
-- with either account): picked_at is when I opened them from a search. Removing sets it back to null.
-- Only a row a search made is marked, so this never widens can_view (anon or banned people stay out of reach).
alter table public.handle_lookups add column if not exists picked_at timestamptz;
create index if not exists handle_lookups_picked on public.handle_lookups (viewer, picked_at desc) where picked_at is not null;

create or replace function public.search_recent_pick(p_user uuid) returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  update public.handle_lookups l set picked_at = now() where l.viewer = auth.uid() and l.other = p_user;
end $$;

-- Same columns as search_handles; the same people it would show (not anon, not banned, not blocked either way).
create or replace function public.search_recents() returns table(id uuid, handle text, name text, photo_path text, country text, look jsonb)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  return query
    select p.id, p.handle, p.name, p.photo_path, p.country, public.look_json(p) from public.handle_lookups l
      join public.profiles p on p.id = l.other
     where l.viewer = auth.uid() and l.picked_at is not null
       and not p.anon and (p.banned_until is null or p.banned_until < now()) and not public.is_blocked(auth.uid(), p.id)
     order by l.picked_at desc limit 20;
end $$;

-- One person, or everyone (null).
create or replace function public.search_recent_forget(p_user uuid default null) returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  update public.handle_lookups l set picked_at = null
   where l.viewer = auth.uid() and l.picked_at is not null and (p_user is null or l.other = p_user);
end $$;

revoke execute on function public.search_recent_pick(uuid), public.search_recents(), public.search_recent_forget(uuid) from public, anon;
grant execute on function public.search_recent_pick(uuid), public.search_recents(), public.search_recent_forget(uuid) to authenticated;
