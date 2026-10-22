-- Chat translation (the owner: a global app, so a line in any language reads naturally in mine, slang included).
-- The translate edge function asks Claude; this side decides what may be translated and remembers answers.
--
-- translate_source(user, kind, id): the text of a chat line the user can read, or null. kind 'chat' = a random/friend
--   chat message (the user is in that chat), 'room' = a room chat line (the user entered the room and wasn't removed).
--   The text is always read here, never taken from the request, so the function can't be used to translate anything.
-- translate_take(user): 400 new translations a day per person (cached answers are free).
-- translations: one answer per (text, target language), shared by everyone who asks; kept 30 days.

create table if not exists public.translations (
  src_hash text not null,
  lang text not null,
  body text not null,
  created_at timestamptz not null default now(),
  primary key (src_hash, lang)
);
alter table public.translations enable row level security; -- no policies: only the service role reads or writes it

create table if not exists public.translate_usage (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null default (now() at time zone 'utc')::date,
  n int not null default 0,
  primary key (user_id, day)
);
alter table public.translate_usage enable row level security;

create or replace function public.translate_source(p_user uuid, p_kind text, p_id bigint) returns text
language plpgsql stable security definer set search_path = '' as $$
declare t text;
begin
  if p_kind = 'chat' then
    select m.body into t from public.messages m join public.matches x on x.id = m.match_id
     where m.id = p_id and (x.user_a = p_user or x.user_b = p_user);
  elsif p_kind = 'room' then
    select v.body into t from public.voice_messages v
     where v.id = p_id and v.kind = 'text'
       and exists (select 1 from public.voice_members m where m.room_id = v.room_id and m.user_id = p_user and not m.kicked);
  end if;
  return nullif(btrim(coalesce(t, '')), '');
end $$;

create or replace function public.translate_take(p_user uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
declare k int;
begin
  insert into public.translate_usage as u (user_id, day, n) values (p_user, (now() at time zone 'utc')::date, 1)
  on conflict (user_id, day) do update set n = u.n + 1
  returning n into k;
  return k <= 400;
end $$;

revoke execute on function public.translate_source(uuid, text, bigint), public.translate_take(uuid) from public, anon, authenticated;

-- Clearing old answers (30 days) and old counters is in owner_run_once_6.sql (run once in the SQL editor).
