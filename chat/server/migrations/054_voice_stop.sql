-- The host or a co-host turns the room's music off for everyone (voice_dj checks). The requests stay queued.
create or replace function public.voice_stop(p_room text) returns void
language plpgsql security definer set search_path = '' as $$
declare r public.voice_rooms := public.voice_dj(p_room);
begin
  if r.music is null then return; end if;
  update public.voice_rooms set rev = rev + 1, music = null where id = p_room;
end $$;
revoke execute on function public.voice_stop(text) from public, anon;
grant execute on function public.voice_stop(text) to authenticated;
