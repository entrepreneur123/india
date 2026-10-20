-- Room codes can't be guessed: 10 wrong codes in 10 minutes and code entry stops for the rest of the window
-- (slow_down). A 6-character code has about a billion values, so this leaves guessing hopeless.
--
-- A wrong code now returns NULL instead of raising not_found, because raising would also undo the miss being
-- counted. The apps (web 0.4.1 / Android 0.4.1) turn NULL into not_found.

create table if not exists public.room_code_tries (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  misses integer not null default 0,
  since timestamptz not null default now()
);
alter table public.room_code_tries enable row level security; -- no policies: only the function below uses it

create or replace function public.join_room_code(p_code text) returns text
language plpgsql security definer set search_path = '' as $$
declare me uuid := auth.uid(); r public.rooms; why text; t public.room_code_tries;
begin
  if me is null then raise exception 'not_signed_in' using errcode = 'P0001'; end if;
  if not exists (select 1 from public.profiles p where p.id = me and (p.banned_until is null or p.banned_until < now())) then
    raise exception 'banned' using errcode = 'P0001';
  end if;
  select * into t from public.room_code_tries x where x.user_id = me;
  if t.misses >= 10 and t.since > now() - interval '10 minutes' then raise exception 'slow_down' using errcode = 'P0001'; end if;

  select x.* into r from public.rooms x join public.room_codes c on c.room_id = x.id
   where c.code = upper(btrim(coalesce(p_code, ''))) and x.closed_at is null;
  if not found then
    insert into public.room_code_tries as q (user_id, misses, since) values (me, 1, now())
    on conflict (user_id) do update set
      misses = case when q.since > now() - interval '10 minutes' then q.misses + 1 else 1 end,
      since = case when q.since > now() - interval '10 minutes' then q.since else now() end;
    return null;
  end if;
  why := public.room_refusal(r, me);
  if why is not null then raise exception '%', why using errcode = 'P0001'; end if;
  insert into public.room_members (room_id, user_id) values (r.id, me)
  on conflict (room_id, user_id) do update set seen_at = now();
  return r.id;
end $$;
