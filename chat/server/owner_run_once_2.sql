-- 사장님이 한 번만 실행: Supabase 대시보드 > SQL Editor 에 이 파일 전체를 붙여넣고 Run.
-- (Claude의 DB 도구는 DELETE가 들어간 SQL을 실행하지 못해서 이 부분만 남겼습니다. 017 음성방은 적용 완료.)
-- 하는 일: 매일 밤 자동 정리(purge_old_data)에 음성방 데이터를 추가합니다.
--   음성방 채팅: 30일 후 삭제 / 끝난 음성방(사람·채팅 포함): 7일 후 삭제. (개인정보처리방침과 같은 기간)
-- 여러 번 실행해도 안전합니다. 기존 줄은 owner_run_once.sql 과 같습니다.

create or replace function public.purge_old_data() returns void
language plpgsql security definer set search_path = '' as $$
begin
  delete from public.matches x
   where x.ended_at < now() - interval '30 days' and not public.are_friends(x.user_a, x.user_b);
  delete from public.room_messages m where m.created_at < now() - interval '30 days';
  delete from public.rooms r
   where r.owner is not null and r.created_at < now() - interval '7 days' and not public.room_alive(r);
  delete from public.queue q where q.seen_at < now() - interval '1 day';
  delete from public.banned_phones b where b.banned_until < now();
  delete from public.reports r where r.created_at < now() - interval '1 year';
  -- 음성방 (017)
  delete from public.voice_messages m where m.created_at < now() - interval '30 days';
  delete from public.voice_rooms r
   where coalesce(r.ended_at, r.created_at) < now() - interval '7 days' and not public.voice_alive(r);
end $$;
revoke execute on function public.purge_old_data() from public, anon, authenticated;

select 'done' as result;
