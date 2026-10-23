-- 사장님이 한 번만 실행: Supabase 대시보드 > SQL Editor 에 이 파일 전체를 붙여넣고 Run.
-- (Claude의 DB 도구는 DELETE·DROP이 들어간 SQL을 실행하지 못해서 이 부분만 남겼습니다. 020~022는 적용 완료.)
-- 하는 일:
--   1. 랜덤채팅 대기줄: 5분 지난 줄을 5분마다 지웁니다 (지금은 하루 동안 남아 검색을 느리게 함).
--   2. 매일 밤 정리(purge_old_data): 앱 오류·이벤트 기록 90일 후 삭제 (개인정보처리방침과 같은 기간),
--      예전 텍스트 방 줄은 뺍니다.
--   3. 예전 텍스트 방(008) 표 4개와 그 함수들을 지웁니다 (019 이후 아무도 안 씀. 안드로이드 0.7.0·웹 영향 없음).
--   4. 음성방 하트비트를 가볍게: 쓰지 않는 색인 하나를 지웁니다 (020에서 대신할 색인을 만들어 둠).
-- 여러 번 실행해도 안전합니다.

-- 1
select cron.schedule('yaari-queue-cleanup', '*/5 * * * *',
  $$delete from public.queue q where q.seen_at < now() - interval '5 minutes'$$);

-- 2
create or replace function public.purge_old_data() returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.matches x
   where x.ended_at < now() - interval '30 days' and not public.are_friends(x.user_a, x.user_b);
  delete from public.queue q where q.seen_at < now() - interval '1 day';
  delete from public.banned_phones b where b.banned_until < now();
  delete from public.reports r where r.created_at < now() - interval '1 year';
  delete from public.voice_messages m where m.created_at < now() - interval '30 days';
  delete from public.voice_rooms r
   where coalesce(r.ended_at, r.created_at) < now() - interval '7 days' and not public.voice_alive(r);
  -- 021: 앱 오류·이벤트 90일, 하루 사용량 카운터 2일
  delete from public.client_events e where e.created_at < now() - interval '90 days';
  delete from public.client_event_quota q where q.day < current_date - 2;
  -- 023: 읽은 알림 90일, 안 읽은 알림 180일, 안 쓰는 휴대폰 토큰 30일
  delete from public.notifications n where n.read_at < now() - interval '90 days' or n.created_at < now() - interval '180 days';
  delete from public.device_tokens d where not d.active and d.updated_at < now() - interval '30 days';
end $$;
revoke execute on function public.purge_old_data() from public, anon, authenticated;

-- 3
drop table if exists public.room_messages, public.room_members, public.room_codes, public.rooms cascade;
drop function if exists public.create_room(text, text, integer, text, integer, integer, boolean), public.join_room(text),
  public.join_room_code(text), public.leave_room(text), public.close_room(text), public.room_list(), public.room_list2(),
  public.room_online(text), public.am_in_room(text), public.can_read_room(text), public.room_message_guard();

-- 4
drop index if exists public.voice_members_user;

select 'done' as result;
