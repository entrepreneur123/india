-- 사장님이 한 번만 실행: Supabase 대시보드 > SQL Editor 에 이 파일 전체를 붙여넣고 Run.
-- (Claude의 DB 도구는 DELETE/DROP이 들어간 SQL을 실행하지 못해서 이 부분만 남겼습니다. 나머지 013은 적용 완료.)
-- 하는 일: ① 매일 밤 오래된 데이터 자동 삭제 작업 등록 ② 테스트용 함수/표 제거 ③ 옛 테스트 계정 6개 삭제.
-- 여러 번 실행해도 안전합니다.

-- ① 보관 기간이 지난 데이터 (개인정보처리방침 "How long we keep data"와 같은 기간)
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
end $$;
revoke execute on function public.purge_old_data() from public, anon, authenticated;
select cron.schedule('yaari-purge-data', '17 3 * * *', 'select public.purge_old_data()');

-- ② 테스트 도우미 (001b)
drop function if exists public._act(uuid);
drop function if exists public._fails(text, text);
drop table if exists public._t;

-- ③ 옛 테스트 계정 (Arjun, Priya, Rahul, Sneha, R1, R2 — 전화번호 9198000000xx)
delete from auth.users where id in (
  '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b',
  '00000000-0000-0000-0000-00000000000c', '00000000-0000-0000-0000-00000000000d',
  '00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000e2');

select 'done' as result;
