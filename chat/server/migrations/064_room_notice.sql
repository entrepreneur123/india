-- Room notice (27, the owner, 2026-10-26): the host or a co-host pins the room's notice (voice_rooms.announcement,
-- <= 200, null removes it) from inside the room, and the chat says who pinned a new one (system line 'notice'; apps
-- that don't know it show nothing). voice_settings keeps the notice when p_announcement is null, so saving the title
-- can't undo a co-host's notice; installed apps that still send it work as before.

create or replace function public.voice_notice(p_room text, p_text text) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); a text := nullif(btrim(coalesce(p_text, '')), '');
begin
  if m.role not in ('host', 'cohost') then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(a) > 200 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.voice_rooms set announcement = a, rev = rev + 1 where id = p_room and announcement is distinct from a;
  if found and a is not null then perform public.voice_line(p_room, m.user_id, 'notice'); end if;
  update public.voice_list_cache set at = '-infinity' where id = 1;
end $$;
revoke execute on function public.voice_notice(text, text) from public, anon;
grant execute on function public.voice_notice(text, text) to authenticated;

create or replace function public.voice_settings(p_room text, p_title text, p_announcement text) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_actor(p_room); t text := btrim(coalesce(p_title, '')); a text := nullif(btrim(coalesce(p_announcement, '')), '');
begin
  if m.role <> 'host' then raise exception 'not_allowed' using errcode = 'P0001'; end if;
  if char_length(t) not between 2 and 40 then raise exception 'room_title' using errcode = 'P0001'; end if;
  if char_length(a) > 200 then raise exception 'bad_request' using errcode = 'P0001'; end if;
  update public.voice_rooms set title = t, announcement = case when p_announcement is null then announcement else a end, rev = rev + 1
   where id = p_room;
end $$;
