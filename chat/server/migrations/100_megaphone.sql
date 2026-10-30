-- Megaphone (the owner, 2026-10-30), after MapleStory's: a bought item that puts one line at the top of everyone's
-- screen on my country's server (countries keep to themselves, 096) for a few seconds, with my name and photo.
-- - megaphone_wallet: how many I have (bought in the Android app as the consumable product 'megaphone', one each;
--   redeem_megaphone like redeem_handle_pass, idempotent per purchase token). No direct access to either table.
-- - megaphone_send: 1-80 characters through the chat word filter (chat_body_check), one per 30 s per person, uses one.
--   The line goes out on the private broadcast topic mega:<country> (listened to by that country's people only)
--   and is kept for megaphone_recent (an app opening late sees the last ones of the past 2 minutes).
-- Re-runnable.

create table if not exists public.megaphone_wallet (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  n int not null default 0 check (n >= 0)
);
alter table public.megaphone_wallet enable row level security;

create table if not exists public.megaphones (
  id bigint generated always as identity primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  country text,
  body text not null check (char_length(body) between 1 and 80),
  created_at timestamptz not null default now()
);
alter table public.megaphones enable row level security;
create index if not exists megaphones_country_time on public.megaphones (country, created_at desc);
create index if not exists megaphones_user_time on public.megaphones (user_id, created_at desc);

create or replace function public.megaphone_topic(p_country text) returns text
language sql immutable set search_path = '' as $$ select 'mega:' || coalesce(p_country, 'xx') $$;

create or replace function public.megaphone_count() returns int
language sql stable security definer set search_path = '' as $$
  select coalesce((select w.n from public.megaphone_wallet w where w.user_id = auth.uid()), 0)
$$;

create or replace function public.megaphone_send(p_body text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  me uuid := public.voice_me();
  b text := btrim(coalesce(p_body, ''));
  p public.profiles;
  mid bigint;
begin
  if b = '' or char_length(b) > 80 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform public.chat_body_check(me, b);
  perform pg_advisory_xact_lock(hashtext('mega:' || me::text));
  if exists (select 1 from public.megaphones m where m.user_id = me and m.created_at > now() - interval '30 seconds') then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  update public.megaphone_wallet set n = n - 1 where user_id = me and n > 0;
  if not found then raise exception 'no_megaphone' using errcode = 'P0001'; end if;
  select * into p from public.profiles x where x.id = me;
  insert into public.megaphones (user_id, country, body) values (me, p.country, b) returning id into mid;
  perform realtime.send(
    jsonb_build_object('id', mid, 'user', me, 'name', p.name, 'handle', p.handle, 'photo', p.photo_path, 'body', b,
                       'at', (extract(epoch from now()) * 1000)::bigint),
    'm', public.megaphone_topic(p.country), true);
  return mid;
end $$;

create or replace function public.megaphone_recent() returns table(id bigint, user_id uuid, name text, handle text, photo_path text, body text, at bigint)
language sql stable security definer set search_path = '' as $$
  select m.id, m.user_id, p.name, p.handle, p.photo_path, m.body, (extract(epoch from m.created_at) * 1000)::bigint
    from public.megaphones m join public.profiles p on p.id = m.user_id
   where m.country is not distinct from (select x.country from public.profiles x where x.id = auth.uid())
     and m.created_at > now() - interval '2 minutes'
     and not public.is_blocked(auth.uid(), m.user_id)
   order by m.created_at desc
   limit 3
$$;

create or replace function public.redeem_megaphone(p_user uuid, p_token text) returns text
language plpgsql security definer set search_path = '' as $$
declare pu public.purchases;
begin
  if p_user is null or p_token is null then raise exception 'bad_request' using errcode = 'P0001'; end if;
  insert into public.purchases (token, user_id, product_id) values (p_token, p_user, 'megaphone') on conflict (token) do nothing;
  if found then
    insert into public.megaphone_wallet as w (user_id, n) values (p_user, 1)
    on conflict (user_id) do update set n = w.n + 1;
    return 'granted';
  end if;
  select * into pu from public.purchases x where x.token = p_token;
  if pu.user_id <> p_user then return 'other_account'; end if;
  if pu.revoked_at is not null then return 'revoked'; end if;
  return 'already';
end $$;

revoke execute on function public.megaphone_count(), public.megaphone_send(text), public.megaphone_recent() from public, anon;
grant execute on function public.megaphone_count(), public.megaphone_send(text), public.megaphone_recent() to authenticated;
revoke execute on function public.redeem_megaphone(uuid, text) from public, anon, authenticated;

-- Only my country's people listen on its topic.
do $$ begin
  if not exists (select 1 from pg_policies where schemaname = 'realtime' and policyname = 'megaphone listen') then
    create policy "megaphone listen" on realtime.messages for select to authenticated
      using (realtime.messages.extension = 'broadcast'
             and realtime.topic() = public.megaphone_topic((select x.country from public.profiles x where x.id = auth.uid())));
  end if;
end $$;
