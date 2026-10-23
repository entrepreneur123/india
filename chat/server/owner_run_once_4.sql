-- 사장님이 한 번만 실행: Supabase 대시보드 > SQL Editor 에 이 파일 전체를 붙여넣고 Run. (owner_run_once_3.sql 다음에)
-- (Claude의 DB 도구는 DELETE가 들어간 SQL을 실행하지 못해서 이 부분만 남겼습니다. 023~025는 적용 완료.)
-- 하는 일:
--   1. 매일 밤 정리(purge_old_data)에 알림 정리를 넣습니다: 읽은 알림 90일, 안 읽은 알림 180일,
--      쓰지 않는 휴대폰 알림 토큰 30일 후 삭제 (개인정보처리방침과 같은 기간). 3번 파일의 정리 내용도 그대로 포함.
--   2. 푸시 자체 점검(2026-10-21)에 쓴 가짜 토큰과 알림 한 줄을 지웁니다.
-- 여러 번 실행해도 안전합니다.

-- 1
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
  delete from public.client_events e where e.created_at < now() - interval '90 days';
  delete from public.client_event_quota q where q.day < current_date - 2;
  delete from public.notifications n where n.read_at < now() - interval '90 days' or n.created_at < now() - interval '180 days';
  delete from public.device_tokens d where not d.active and d.updated_at < now() - interval '30 days';
end $$;
revoke execute on function public.purge_old_data() from public, anon, authenticated;

-- 2
delete from public.device_tokens where token = 'selftest-fake-fcm-token-0000000001';
delete from public.notifications where user_id = '336a1482-2066-4a58-812d-a02a5e420076' and actor = '7f8a9167-0332-469f-84b0-5e2e29f72ce6'
   and kind = 'friend_request' and created_at < '2026-10-22';
