-- Removes the test bots of migration 028 for good (run before launch, in the Supabase SQL editor).
-- Their chats, rooms, room lines and friendships go with them (foreign keys cascade from auth.users).
-- To only pause them instead: update public.bot_config set enabled = false;
begin;
select cron.unschedule('yaari-bots');
update public.voice_rooms x set ended_at = now(), rev = rev + 1
 where x.ended_at is null and x.host in (select id from public.bots);
delete from auth.users u where u.id in (select id from public.bots);
delete from cron.job_run_details d where d.command like '%bot_tick%';
drop function if exists public.bot_tick(), public.bot_slow(), public.bot_rooms_talk(), public.bot_social(), public.bot_chat(),
  public.bot_match_people(), public.bot_join(text, uuid), public.bot_in_room(text), public.bot_room_say(text, uuid, text),
  public.bot_chat_say(uuid, uuid, text), public.bot_say(uuid, text, text, text), public.bot_free(uuid), public.is_bot(uuid);
drop table if exists public.bots, public.bot_lines, public.bot_titles, public.bot_config, public.bot_done;
commit;
