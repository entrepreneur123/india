-- PENDING: not applied to the live database yet (waiting for the owner's OK, 2026-10-28); remove this line when applied.
-- Group lines stored once however often they are sent (as 091 for 1:1 chats; Matrix transaction ids, Zulip's
-- local_id in web/src/echo.ts). The web app now shows a group line at once and sends it again after a dropped
-- connection; the name it gives the line comes back on the stored row, so its "sending…" copy is matched with it.
--  * group_line gains p_client (default null): its other callers (notes for created/added/renamed/left, parties)
--    are unchanged. Dropped and created again because a new argument is a new signature (two would be ambiguous).
--  * group_send(p_group, p_body) stays for older apps and now goes through the 3-argument one.

alter table public.group_messages add column if not exists client_id uuid;
create unique index if not exists group_messages_sender_client on public.group_messages (sender, client_id);

drop function if exists public.group_line(uuid, uuid, text, text);
create or replace function public.group_line(p_group uuid, p_sender uuid, p_kind text, p_body text, p_client uuid default null)
returns bigint language plpgsql security definer set search_path = '' as $$
declare n bigint;
begin
  insert into public.group_messages (group_id, sender, kind, body, client_id)
  values (p_group, p_sender, p_kind, left(p_body, 2000), p_client) returning id into n;
  update public.group_chats set last_at = now() where id = p_group;
  return n;
end $$;
revoke execute on function public.group_line(uuid, uuid, text, text, uuid) from public, anon, authenticated;

create or replace function public.group_send(p_group uuid, p_body text, p_client uuid) returns bigint
language plpgsql security definer set search_path = '' as $$
declare me uuid := public.voice_me(); b text := btrim(coalesce(p_body, '')); n bigint;
begin
  if not public.group_member(p_group) then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(b) not between 1 and 2000 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  perform pg_advisory_xact_lock(hashtext('gmsg:' || me::text));
  -- Sent before (its reply was lost): that line is the answer. After the lock, so two tries can't both insert.
  if p_client is not null then
    select m.id into n from public.group_messages m where m.sender = me and m.client_id = p_client;
    if n is not null then return n; end if;
  end if;
  if exists (select 1 from public.profiles p where p.id = me and p.banned_until > now()) then raise exception 'banned' using errcode = 'P0001'; end if;
  if (select count(*) from public.group_messages m where m.sender = me and m.created_at > now() - interval '10 seconds') >= 10 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  return public.group_line(p_group, me, 'text', b, p_client);
end $$;
revoke execute on function public.group_send(uuid, text, uuid) from public, anon;
grant execute on function public.group_send(uuid, text, uuid) to authenticated;

create or replace function public.group_send(p_group uuid, p_body text) returns bigint
language sql security definer set search_path = '' as $$ select public.group_send(p_group, p_body, null) $$;
