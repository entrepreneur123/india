# 다음 세션 인수인계

출시 순서·비용·국가별 규칙·사장님 할 일은 **`chat/docs/LAUNCH_GUIDE.md`** 에 모두 있습니다. 이 파일은 개발 상태만 적습니다.

## 2026-10-21 테스트 봇 500개 (마이그레이션 028, 출시 전에 반드시 제거)
- 데이터베이스 안에만 있는 봇 500개(인도 440, 방글라데시·브라질·베트남 각 20; 전화번호는 실제로 있을 수 없는 번호, 소개 "Test bot").
  pg_cron `yaari-bots`가 10초마다 `bot_tick()` 실행(평균 0.06초):
  - 랜덤채팅: 12초 넘게 기다린 사람에게 봇 매칭(사람끼리는 그 전에 먼저 매칭). 인사, 읽음 표시, 언어별 답장(어디/이름/나이 질문 인식),
    3번 넘게 말하면 절반 확률로 친구 요청, 3분 조용하거나 긴 대화 후 나감. 친구 요청은 5초 뒤 수락, 영상 요청은 거절, 방 초대는 입장.
  - 방: 봇이 여는 방 24개(대화·음악·게임, 1~3시간 뒤 닫히고 새로 열림), 방마다 3~20명이 들락날락, 사람의 공개 방에 2~5명 방문
    (가끔 손들기), 수다, 사람이 들어오면 환영 인사, 사람 말에 답장. 무대에는 안 올라가므로 LiveKit 비용 없음.
  - 뒤에서 봇끼리 랜덤채팅 약 15개(부하용), 접속 표시(`user_seen`)로 "N명 접속 중"에 포함.
- 방 만들기 제한: 하루 10개 → 10분에 5개·하루 30개, 관리자는 무제한(이 제한이 "You're sending too fast"로 보였음).
- 끄기: `update public.bot_config set enabled = false;` 완전 제거: `chat/server/owner_run_once_5.sql`(사장님 실행).
- 실제 검증(QA Web 계정, 공개 API): 매칭, 대화 4번 왕복과 맞는 답, 읽음 표시, 친구 수락 + 알림, 방 목록 24개, 방 입장 환영 인사, 접속자 515.

## 2026-10-21 변경 (감사 결과 2단계: 알림·방 찾기·관심사·환불)
- **서버 마이그레이션 023~027 적용 완료.**
  - 023: 알림함 `notifications`(친구 메시지는 대화당 1줄로 묶음, 친구 요청/수락, 방 초대), `my_badges()`(안 읽은 수 + "앱 켜져 있음"
    표시 `user_seen`), `inbox()`, `inbox_read()`, 휴대폰 토큰 `device_tokens` + `register_device`/`unregister_device`.
    푸시 판단 `push_dispatch`(토큰 없으면 아무것도 안 함, 60초 안 접속 중이면 안 보냄, 대화당 30초 1번, 현지 23~08시는 친구 요청만)
    → pg_net으로 edge function `push`(볼트 키 `edge_call_key`). `push`는 `FCM_SERVICE_ACCOUNT`가 없으면 `{skipped:true}`.
  - 024: 방 언어 `voice_rooms.lang`(안드로이드 0.7.0이 만든 방은 마지막 랜덤채팅 언어), `voice_list3(검색어, 언어, 분류)`,
    `create_voice_room3`, `voice_settings2`, `online_count()`(10초 캐시), `invite_to_room`/`join_invite`(비공개 방도 초대로 입장),
    관심사 `profiles.interests`(20개 중 3~5, `set_interests`), 추가 사진 4장 `profiles.photos`(`set_photos`, 본인 avatar 폴더만),
    `profile_extras(ids)`, `stale_media`가 추가 사진을 지우지 않음, 매칭: 언어 다음 공통 관심사(10초 넘게 기다린 사람은 순서 유지).
  - 025: Play 환불/취소 `play_revoke`(구독 → Plus 즉시 종료, 성별 패스 → 24시간 회수), 매일 04:41 UTC `play-rtdn?mode=voided`.
  - 026: 정지되면 즉시 대기줄에서 빠짐 → `find_match2`가 대기자마다 프로필을 읽지 않음 (2,000명 대기 4~5.6 ms).
  - 027: `voice_state`에 `lang`.
- **edge function** `push`, `play-rtdn`(verify_jwt 끔, 자체 인증: 볼트 키 / Pub/Sub OIDC 또는 `?token=`). 설정법은 LAUNCH_GUIDE 4-1, 4-2.
- **웹**: 알림(데스크톱 사이드바 "Notifications", 휴대폰은 랜덤 첫 화면·Recent 상단 종), Recent 탭/친구·매치 줄의 안 읽은 수(실시간),
  방 검색(이름·언어), 방 카드에 언어, 방 만들기/설정에 언어, 방에서 친구 초대 + 링크 `/r/<id>`(비공개는 `/r/<코드>`, 로그인 전에 열어도
  가입 후 그 방으로), 랜덤 화면 "N people online", 첫 실행 관심사 고르기 + 3장 소개(한 번만, 건너뛰기 가능), Me의 관심사·사진 4장,
  프로필 시트의 관심사(공통은 강조)·사진. 새 문구는 `components/social/locales/*`(첫 화면 용량 영향 없음).
  `vercel.json`: `cleanUrls`(→ `/admin` 동작) + `/r/:code` 재작성.
- **사장님이 하실 일**: `owner_run_once_3.sql` 다음 `owner_run_once_4.sql` 실행(알림 90일·토큰 30일 정리, 자체 점검용 가짜 토큰 삭제).
  Firebase·Play Console 계정이 생기면 LAUNCH_GUIDE 4-1(환불), 4-2(푸시)의 비밀값만 넣으면 켜짐.
- **안드로이드 다음 버전**: FCM 붙이기(`register_device`, 채널 3개, data 메시지로 알림 만들기), 알림함·관심사·방 검색 화면.
- 테스트: `test_notifications.sql`, `test_discovery.sql`, `test_play_refunds.sql` 추가. (MCP의 SQL 도구는 WHERE 없는 UPDATE가 있으면
  사용자 확인을 기다리다 60초에 끊김 — 테스트에는 항상 WHERE를 붙일 것.)
- 테스트 계정 919000000001(seyoen)과 919000000002(QA Web)를 친구로 만들어 둠(실시간 알림 점검용).

## 2026-10-21 변경 (감사 결과 1단계: 서버 + 웹)
- **서버 마이그레이션 020~022 적용 완료.**
  - 020: 매칭 경합 수정(한 사람이 두 매칭에 들어가던 버그), 대기줄 `seen_at` 색인, 빠진 FK 색인,
    방 목록 `voice_list2` 요약 캐시(5초마다 재생성: 120방×30명 215ms → 1.5ms),
    방 변경을 Realtime Broadcast로 바로 전송(private 토픽 `voice:<방>`, 변경마다 번호 `seq`) — 웹은 전체 상태(200명 방 63KB)를
    다시 받지 않고 변경(약 0.3KB)만 적용. 안드로이드 0.7.0을 위해 `rev` 증가는 그대로.
    안 쓰는 옛 함수 실행권한 회수, `room_messages`를 realtime에서 뺌.
  - 021: 앱 오류·이벤트 기록(`client_events`, RPC `log_events`/`log_events_anon`, 하루 200건/사람, 비로그인 전체 5,000건),
    운영 콘솔용 `admins`, `admin_actions`(모든 조치 기록), `admin_*` 함수.
  - 022: 살아 있는 함수들이 옛 텍스트 방 표를 더 이상 읽지 않음(표 삭제 준비).
- **웹**: 방에서 서버가 거절하는 모든 경우(꽉 참·정지·차단·나이/성별·강퇴)에 이유를 보여주고 방에서 나감.
  배경 30분 지난 청취자는 자동으로 방에서 나감. 방 목록은 화면에 보일 때만 30초마다. 재동기화는 연결 정상이면 45초, 아니면 10초.
  오류 자동 보고(window.onerror, unhandledrejection, 화면 오류 경계) + 주요 단계 이벤트
  (signup_done, match_found, chat_message_sent, room_created, room_joined, ludo_started, call_started; plus_purchased는 웹에 구매가 없어 안드로이드용).
  개인정보처리방침에 진단 정보(90일, 메시지 내용 없음) 추가.
- **운영 콘솔 `/admin`** (앱 어디에도 링크 없음, 첫 화면 용량 영향 없음): 신고 목록(미검토/자동 노출 감지/사용자 신고/사유별),
  신고 상세(대화·방 줄·사진), 검토 완료·기각·정지(1/7/30/365일·영구, 전화번호 기억)·정지 해제, 대시보드(사유별 신고, 활성 방, 접속자, 최근 오류).
  - 운영자 추가(사장님 본인 계정, SQL Editor에서 한 번):
    `insert into public.admins (user_id) select id from auth.users where phone = '91XXXXXXXXXX' on conflict do nothing;`
    (전화번호는 국가번호 포함, + 없이). 테스트 계정 919000000001은 이미 운영자.
- **사장님이 하실 일**: `chat/server/owner_run_once_3.sql`을 SQL Editor에 붙여넣고 Run (대기줄 5분 정리, 오류 기록 90일 삭제,
  옛 텍스트 방 표 삭제, 색인 하나 삭제). 그 밖에 Firebase(푸시)·Play Console(환불 알림)은 계정이 필요해서 아직 안 함.
- 테스트: `chat/server/test_matching_race.sql`, `test_voice_events.sql`, `test_log_events.sql`, `test_admin.sql` 추가.
  기존 테스트는 회수한 옛 함수를 트랜잭션 안에서만 다시 허용해 계속 검사.
- 안드로이드(chat/app)는 이번에 손대지 않음: 방 거절 처리(`failed()`), 30분 자동 퇴장, Broadcast 적용, 오류 보고는 다음 단계.

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
