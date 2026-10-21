-- Removes the test bots of migrations 028 and 030 for good (then also delete the Edge Function bot-brain) (run before launch, in the Supabase SQL editor).
-- Their chats, rooms, room lines and friendships go with them (foreign keys cascade from auth.users).
-- To only pause them instead: update public.bot_config set enabled = false;
begin;
select cron.unschedule('yaari-bots');
update public.voice_rooms x set ended_at = now(), rev = rev + 1
 where x.ended_at is null and x.host in (select id from public.bots);
delete from auth.users u where u.id in (select id from public.bots);
delete from cron.job_run_details d where d.command like '%bot_tick%';
drop trigger if exists messages_bot on public.messages;
drop trigger if exists matches_bot on public.matches;
drop trigger if exists voice_messages_bot on public.voice_messages;
drop function if exists public.bot_on_message(), public.bot_on_match(), public.bot_on_room_line(), public.bot_job(jsonb),
  public.bot_llm_take(), public.bot_card(uuid), public.bot_context(jsonb), public.bot_typing(jsonb), public.bot_canned(jsonb, text),
  public.bot_post(jsonb, text), public.bot_chats(), public.bot_rooms(), public.bot_places(), public.is_live_bot(uuid);
drop function if exists public.bot_tick(), public.bot_slow(), public.bot_rooms_talk(), public.bot_social(), public.bot_chat(),
  public.bot_match_people(), public.bot_join(text, uuid), public.bot_in_room(text), public.bot_room_say(text, uuid, text),
  public.bot_chat_say(uuid, uuid, text), public.bot_say(uuid, text, text, text), public.bot_free(uuid), public.is_bot(uuid);
drop table if exists public.bots, public.bot_lines, public.bot_titles, public.bot_config, public.bot_done;
commit;
