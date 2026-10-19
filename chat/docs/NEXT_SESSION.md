# 다음 세션 인수인계

출시 순서·비용·국가별 규칙·사장님 할 일은 **`chat/docs/LAUNCH_GUIDE.md`** 에 모두 있습니다. 이 파일은 개발 상태만 적습니다.

## 현재 상태 (2026-10-19)
- **웹**: https://yaari-web-indol.vercel.app (Vercel 프로젝트 `yaari-web`, 루트 `chat/web`, 빌드 명령은 반드시 `pnpm build`).
  - 실서버(Supabase) 연결. 안드로이드와 같은 매칭·방·친구.
  - 5개 언어(en/hi/bn/pt-BR/vi), 보안 헤더(`vercel.json`), 첫 로딩 218KB(데모)/278KB(실서버).
  - 정책 페이지 `/privacy.html`, `/terms.html`, `/child-safety.html`, `/delete-account.html`.
    원본은 `chat/docs/*.html`, 빌드 때 `scripts/copy-legal.mjs`가 복사.
  - 신고 화면 아래에 "신고하지 않고 차단"(RPC `block_user`).
- **안드로이드 0.4.0 (versionCode 4)** (0.3.0 내용 포함): 5개 언어 + 앱 안 언어 선택, Material 3 정리, 차단 버튼, 백그라운드 폴링 중지,
  릴리스 로그 제거·HTTPS 전용. 설정에 개인정보·이용약관·아동안전 링크(웹사이트 주소).
  - `chat/dist/`: `yaari-demo.apk`, `yaari-live-arm64.apk`, `yaari-live-armv7.apk`, `yaari-live.aab`.
  - 0.2.0과 같은 디버그 키라 덮어 설치됨(0.1.x에서 올 때는 삭제 후 설치).
- **서버**: 마이그레이션 008(사용자 방), 009(보안 강화), 010(차단), 011(매칭: 전역 잠금 제거·같은 언어 우선 8초), 012(`match_card`) 적용. 테스트 SQL은 `chat/server/test_*.sql`(롤백식).
- **랜덤채팅 연결 방식 (앱·웹 공통, 0.4.0)**:
  - 검색 실패(약한 네트워크)는 2·4·8·10초 간격으로 최대 1분 재시도.
  - 기다리는 쪽은 realtime(`matches` INSERT, `user_a=eq.<나>`)으로 즉시 다시 요청.
  - 상대 끊김 감지: `typing-<matchId>` 채널 presence(key=내 id, payload `{"user": id}`). 12초 안에 안 들어오면 / 나간 뒤 20초 안에 안 돌아오면 채팅 종료. 상대의 메시지·입력중 표시도 "들어옴"으로 침. 바꿀 때는 앱(`data/PartnerWatch.kt`)·웹(`lib/supabaseBackend.ts`)·테스트 봇을 함께 바꿔야 함.
  - 재동기화는 마지막 전체 조회 이후의 새 메시지만.

## 남은 일
1. **실시간 영상 노출 감지** — 출시 전 최우선(LAUNCH_GUIDE 2절).
2. 사장님 승인 필요(데이터 삭제 성격): 신고 기록을 탈퇴 시 보존(FK SET NULL), 보관 기간 자동 삭제(pg_cron),
   본인 파일 삭제 정책, 테스트 함수 `_t`/`_act`/`_fails` 제거.
   → 신고 보존 전까지는 정책 페이지에 "증거 보관"을 약속하지 않았음.
3. 보안 후속(코드): 입력중 표시 채널을 private 채널로, 서명 URL 12시간 → 1시간, LiveKit 토큰 15분 → 5분,
   verify-purchase 환불 처리, 방 코드 대입 시도 제한, 친구 요청 거절 후 재요청 쿨다운.
4. 사이트 도메인이 생기면 `SettingsScreen.kt`의 `Support.SITE`와 Play Console URL 변경.
5. 번역은 현지인 검수 필요. 실제 폰 시작 속도·영상통화 화면/소리는 기기에서 확인 필요(이 환경은 WebRTC 차단).

## 테스트 봇
- `chat/server/tools/partner_bot.ts`(QA Arjun, QA Priya)가 실행 중일 수 있음. 출시 전 종료 + Email 로그인 끄기.
- 서버는 같은 상대를 연속 매칭하지 않음. 919000000001("seyoen")은 Plus(2026-11-18까지), 919000000002는 "QA Web".

## 서버 정보
Supabase `dgynsckrqgnpyyvqekzi`(ap-south-1 뭄바이), LiveKit `wss://yaari-sofpbngs.livekit.cloud`.
