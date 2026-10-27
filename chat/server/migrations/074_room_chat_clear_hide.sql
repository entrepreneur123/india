-- 50 (the owner, 2026-10-27): the host or a co-host can clear the room's chat (everything written so far disappears
-- for everyone) or hide one line. Nothing is deleted: the lines stay for reports (14, the owner), they just aren't
-- read any more (the select policy). The room hears it at once: voice_emit 'h' (ids hidden) or 'x' (cleared, by whom).

alter table public.voice_messages add column if not exists hidden_at timestamptz;
alter table public.voice_rooms add column if not exists cleared_at timestamptz;

create or replace function public.voice_cleared_at(p_room text) returns timestamptz
language sql stable security definer set search_path = '' as $$
  select r.cleared_at from public.voice_rooms r where r.id = p_room
$$;

alter policy "read voice room" on public.voice_messages
  using (public.voice_can_read(room_id) and not public.blocked_with(sender) and hidden_at is null
         and created_at > coalesce(public.voice_cleared_at(room_id), '-infinity'));

create or replace function public.voice_hide_line(p_room text, p_id bigint) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_mod(p_room);
begin
  update public.voice_messages set hidden_at = now() where id = p_id and room_id = p_room and hidden_at is null;
  if found then perform public.voice_emit(p_room, jsonb_build_object('t', 'h', 'ids', jsonb_build_array(p_id))); end if;
end $$;

create or replace function public.voice_clear_chat(p_room text) returns void
language plpgsql security definer set search_path = '' as $$
declare m public.voice_members := public.voice_mod(p_room);
begin
  -- clock_timestamp: a line written earlier in this same moment is cleared too.
  update public.voice_rooms set cleared_at = clock_timestamp() where id = p_room;
  perform public.voice_emit(p_room, jsonb_build_object('t', 'x', 'by', coalesce((select p.name from public.profiles p where p.id = m.user_id), '')));
end $$;

revoke execute on function public.voice_cleared_at(text) from public, anon;
grant execute on function public.voice_cleared_at(text) to authenticated;
revoke execute on function public.voice_hide_line(text, bigint), public.voice_clear_chat(text) from public, anon;
grant execute on function public.voice_hide_line(text, bigint), public.voice_clear_chat(text) to authenticated;
