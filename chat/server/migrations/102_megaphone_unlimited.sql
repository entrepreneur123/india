-- Unlimited megaphones for chosen accounts (the owner, 2026-10-30: the admin account +91 9000000001 gets them for
-- testing): megaphone_wallet.unlimited. Sending still goes through the word filter and the 30 s gap, and still uses
-- none; megaphone_count returns -1 for "unlimited" (the app shows ∞). Set only here or from the SQL editor.
-- Re-runnable.

alter table public.megaphone_wallet add column if not exists unlimited boolean not null default false;

create or replace function public.megaphone_count() returns int
language sql stable security definer set search_path = '' as $$
  select coalesce((select case when w.unlimited then -1 else w.n end from public.megaphone_wallet w where w.user_id = auth.uid()), 0)
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
  update public.megaphone_wallet set n = n - (not unlimited)::int where user_id = me and (unlimited or n > 0);
  if not found then raise exception 'no_megaphone' using errcode = 'P0001'; end if;
  select * into p from public.profiles x where x.id = me;
  insert into public.megaphones (user_id, country, body) values (me, p.country, b) returning id into mid;
  perform realtime.send(
    jsonb_build_object('id', mid, 'user', me, 'name', p.name, 'handle', p.handle, 'photo', p.photo_path, 'body', b,
                       'at', (extract(epoch from now()) * 1000)::bigint),
    'm', public.megaphone_topic(p.country), true);
  return mid;
end $$;

insert into public.megaphone_wallet as w (user_id, n, unlimited)
select u.id, 0, true from auth.users u join public.profiles p on p.id = u.id where u.phone = '919000000001'
on conflict (user_id) do update set unlimited = true;
