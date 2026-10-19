# 다음 세션 인수인계

## 현재 상태 (2026-10-19)
- **웹/PC 버전 완성·배포**: https://yaari-web-indol.vercel.app (Vercel 프로젝트 `yaari-web`, 루트 `chat/web`).
  - 실서버(Supabase) 연결. 안드로이드 앱 사용자와 같은 매칭·공개방·친구를 씁니다.
  - 디자인: v0로 디자인 시스템과 첫 화면들을 만들었고, v0 크레딧이 떨어진 뒤 나머지 7개 화면(랜챗·공개방·최근·나·프로필·설정·스레드)은 같은 스타일로 직접 작성.
  - PC: 사이드바·본문·오른쪽 패널. 모바일 브라우저: 가운데가 떠 있는 하단 탭.
  - 실서버 E2E 검증(테스트 봇 상대): 로그인 → 매칭 → 실시간 메시지 → Plus 0.2초 나가기 → 영상 요청·수락·종료 → 최근 목록 → 공개방 메시지·봇 답장. 콘솔 오류 0.
- **안드로이드 앱 화면 교체**: Magic Patterns 시안(https://www.magicpatterns.com/c/vqpzdrys5sbewgh1kykx8n) 기준으로 Compose 화면 전부 교체.
  - 어두운 1:1 채팅, 떠 있는 랜챗 버튼, 공개방 카드 등을 반영. 동작·규칙은 그대로.
  - 테스트 demo 44 + live 48 통과, lint 오류 0. 스크린샷은 `chat/docs/screenshots/`.

## 남은 일 / 사장님 확인 필요
1. **SMS 인증 연결**(Supabase → Authentication → Phone). 지금은 테스트 번호(919000000001/2, 코드 123456)만 로그인 가능하고, 일반 번호는 "지금은 코드를 보낼 수 없음"이 표시됨.
2. **영상통화 실제 화면·소리**: 이 작업 환경은 WebRTC 미디어 연결이 막혀 있어 "연결 중"까지만 확인. 실제 폰/PC 두 대로 확인 필요(신호 서버 연결·토큰 발급은 확인됨).
3. 웹 결제는 없음(구매는 안드로이드 앱 Google Play). 같은 계정이면 웹에서도 Plus가 적용됨.
4. 고충처리 담당자 실제 이름·이메일(웹 `chat/web/lib/config.ts`, 앱 `SettingsScreen.kt` `Support`).
5. 웹 힌디어: 메뉴 등 일부만 번역. 앱은 전체 힌디어.
6. v0 크레딧 소진: v0를 다시 쓰려면 https://v0.app/chat/settings/billing 에서 충전.

## 테스트 봇
- `chat/server/tools/partner_bot.ts`가 지금도 어딘가에서 실행 중(QA Arjun, QA Priya).
- 서버는 같은 상대를 연속으로 다시 매칭하지 않음. 그래서 같은 테스트 계정으로 연속 테스트하면 봇 하나가 제외될 수 있음.
- 919000000001("seyoen")은 Plus 구독 중(2026-11-18까지).
- 919000000002에는 이번 세션에서 "QA Web" 프로필을 만들었음.

## 서버 (변경 없음)
Supabase `dgynsckrqgnpyyvqekzi`(뭄바이), LiveKit `wss://yaari-sofpbngs.livekit.cloud`. RPC·테이블·규칙은 `chat/server/migrations`.
