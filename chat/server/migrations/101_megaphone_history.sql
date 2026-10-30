-- Megaphone lines stay readable for a day (the owner, 2026-10-30): a tap on the bar at the top lists my country's lines
-- of the past 24 hours, newest first, so someone who agrees with one can open that person's profile and get in touch.
-- megaphone_recent (100) returned only the past 2 minutes' last 3 for an app opening late; it now returns the day's
-- last 50 and the app replays the past 2 minutes' itself. Same signature and grants; blocked and banned people left out.
-- Re-runnable.

create or replace function public.megaphone_recent() returns table(id bigint, user_id uuid, name text, handle text, photo_path text, body text, at bigint)
language sql stable security definer set search_path = '' as $$
  select m.id, m.user_id, p.name, p.handle, p.photo_path, m.body, (extract(epoch from m.created_at) * 1000)::bigint
    from public.megaphones m join public.profiles p on p.id = m.user_id
   where m.country is not distinct from (select x.country from public.profiles x where x.id = auth.uid())
     and m.created_at > now() - interval '24 hours'
     and not public.is_blocked(auth.uid(), m.user_id)
     and (p.banned_until is null or p.banned_until < now())
   order by m.created_at desc
   limit 50
$$;
