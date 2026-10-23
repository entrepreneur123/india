# 다음 세션 인수인계

출시 순서·비용·국가별 규칙·사장님 할 일은 **`chat/docs/LAUNCH_GUIDE.md`** 에 모두 있습니다. 이 파일은 개발 상태만 적습니다.

## 2026-10-23 0.18.7: Android 효과음 최종 구조 (0.18.4–0.18.6 실기기 실패 정리)
- LiveKit 2.29 AudioSwitchHandler(바이트코드 확인): MODE_IN_COMMUNICATION, AUDIOFOCUS_GAIN, USAGE_VOICE_COMMUNICATION, 별도 HandlerThread에서 비동기 전환. 무대가 비면 SupabaseBackend.show()가 RoomChanged 전에 stopAudio().
- 실기기 결과: 0.18.4 SIGNALLING → 두 번째 참여 무음, 0.18.5 통화모드면 VOICE_COMMUNICATION → 내려갈 때 무음(일반 모드로 바뀌며 수화기), 0.18.6 MEDIA → 참여 때 무음(AOSP MediaFocusControl이 통화 중 USAGE_MEDIA/GAME 음소거).
- 지금: 재생 순간 모드로 고름(통화 → VOICE_COMMUNICATION, 일반 → ASSISTANCE_SONIFICATION). 무대가 차거나 비는 변화·내 퇴장은 settling=true → 통화 모드면 풀릴 때까지 50ms 간격 최대 1.5초 대기. 테스트: VoiceTest(settling 전달), RoomSoundsDeviceTest(Robolectric: 모드별 usage, 대기).
- 실기기 미확인.

## 2026-10-23 0.18.6: Android 효과음 = USAGE_MEDIA
- 0.18.5(통화 모드면 USAGE_VOICE_COMMUNICATION)는 무대에서 내려갈 때 무음: 무대가 비면 LiveKit이 끊겨 통화 모드→일반으로 바뀌는 순간 재생돼 수화기로 감. 모드에 따라 고르지 않고 항상 미디어로.
- 실기기 미확인(이 환경엔 에뮬레이터 없음). 다음 확인 순서: 방 입장 → 음성참여 → 내려가기 → 다시 참여 → 내려가기, 스피커·유선·블루투스 각각.

## 2026-10-23 0.18.5: Android 효과음 두 번째부터 안 들리던 문제
- 원인(추정, 실기기 미확인): 첫 무대 참여 뒤 LiveKit 때문에 폰이 통화 모드(MODE_IN_COMMUNICATION)로 남아, USAGE_VOICE_COMMUNICATION_SIGNALLING 소리가 수화기·무음 스트림으로 감. 또 정적 AudioTrack 재사용(stop+reloadStaticData+play)은 일부 기기에서 두 번째부터 무음.
- 수정: 매번 새 AudioTrack, 통화 모드면 USAGE_VOICE_COMMUNICATION(보이스와 같은 경로·음량), 아니면 USAGE_ASSISTANCE_SONIFICATION.
- 웹 데모(Chromium)에서는 3번 반복해도 매번 재생됨(play() 성공 확인).

## 2026-10-23 0.18.4: 효과음 = 무대(음성채팅) 오르내림 + 방 들어오고 나감, 끝낸 방 목록에서 즉시 제거
- 음: 들어옴 880→1175Hz, 나감 392→262Hz (웹 `lib/room-sounds.ts` JOIN/LEAVE, Android `RoomSounds.JOIN/LEAVE`). 내 입장·퇴장은 1.2초 제한 없이 항상.
- 목록: 서버 `voice_list3`는 원래 끝난 방을 바로 뺌(ended_at 조인). 문제는 클라이언트 캐시: 웹은 SWR `voiceRooms` 키를 끝내자마자 지우고 나간 뒤 다시 읽음, Android는 `_list`에서 바로 빼고 leaveVoice 끝난 뒤 refreshList.
- 남은 일: 다른 사람 화면의 방 목록은 최대 30초 뒤에 갱신(실시간 구독은 동접 부하 때문에 안 함).

## 2026-10-23 0.18.3: 음악 끄기·입장/퇴장 효과음·다른 사이트 옆에 띄우기 (마이그레이션 054 적용 완료)
- 서버: 054 `voice_stop(room)` (voice_dj 확인, music만 null, queue 유지).
- 효과음: 웹 `lib/room-sounds.ts`(WAV를 코드로 만들어 <audio>로 재생, Web Audio 안 씀: iPhone 잡음 방지), Android `data/RoomSounds.kt`(AudioTrack, USAGE_VOICE_COMMUNICATION_SIGNALLING). 설정 키: 웹 localStorage `yaari_room_sounds`, Android `yaari_settings/room_sounds`.
- 옆에 띄우기: 웹 `openBeside`(lib/links.ts, PC에서만 popup 창, noopener), Android `openInTab(beside = true)`(Partial Custom Tab, startActivityForResult, 배경 조작 허용).
- 확인 안 됨: 실기기 효과음 음량·경로, Partial Custom Tab(Chrome 외 브라우저는 전체 화면), Document Picture-in-Picture(방을 떠 있는 창으로)는 Chrome 데스크톱만 돼서 보류.

## 2026-10-23 0.18.2: 무료 통화·친구 전화 수신·같이 보기·내 프로필·읽음 숫자 (마이그레이션 050–053 적용 완료)
- 서버: 050 `my_badges`는 열 수 있는 대화만(최근 50 + 친구 DM). 051 `request_call(match, video)` 무료, `matches.call_video`. 052 `call_open`(열린 채팅 또는 친구, 차단 제외)을 request_call·respond_video·video-token(v10)에 적용. 053 친구 통화 요청 → 'call' 알림(응답·종료 시 읽음).
- 앱 전체 수신 창: 웹 `components/video/incoming-call.tsx`, Android `SocialController.checkRing`.
- 남은 일: 앱이 꺼져 있을 때 전체화면 전화 수신(full-screen intent·벨소리) 없음, Android 통화 서비스 타입(mediaPlayback vs phoneCall) Play 정책 확인 필요, 실기기·실서버 통화 미확인, 화면공유는 사장님 결정 대기(비용).

## 2026-10-23 0.18.1: 방장 위임·로고 홈·언어 변경 버그·최근 삭제·보낸 신청·읽음 배지·VIP 노출 차단 (마이그레이션 048–049 적용 완료)
- 서버: `hide_recents(uuid[])`(048, 다시 만나면 복귀), 049에서 VIP 실명이 새던 `voice_line/voice_react/voice_queue_add/voice_share_link/ludo_join/ludo_open/bot_context/my_blocks/push_payload/can_read_media`를 마스킹, `looks_like_vip`로 흉내 이름 차단. **profiles를 읽는 새 함수는 반드시 `profiles_seen`(사람별) 또는 `profiles_public`(방 전체)을 쓸 것.**
- 웹 언어 변경 버그 원인: `components/ui/toast.tsx`의 toast 객체가 언어마다 새로 만들어져 보이스룸·랜챗 effect가 다시 돌며 leave를 호출. ref로 고정.
- Android: `configChanges`에 `locale|layoutDirection` 추가(13+), 12 이하는 recreate지만 ViewModel 유지.
- 용량 점검 `docs/CAPACITY.md`(무료 플랜 실시간 200명 한도), 스토어 문구 `docs/STORE_LISTING.md`.
- 남은 일: VIP 성별은 서버 응답에 남아 있음(사장님 결정), 배지가 왕복 시간 동안 잠깐 다시 뜰 수 있음(낮음), 실서버·실기기 확인 안 함.

## 2026-10-23 0.18.0: 흰색+오렌지 브랜드·유료 채팅 배경·스티커·VIP 시크릿 (마이그레이션 046–047 적용 완료)
- 브랜드: 흰 바탕 + 오렌지 `#EA6A00`(흰 글자 3.2:1), 작은 오렌지 글자는 `#B45309`. 라이트 전용(다크 모드 삭제), 어두운 곳은 영상통화 화면뿐. 로고 = 오렌지 사각형 + 흰 "y".
- 상품(Play Console에 아직 미등록): `wall_*` 6종 ₹79, `sticker_*` 4팩 ₹49, `vip_anon` ₹299 (비소모성, verify-purchase LOOK 정규식에 포함). `handle_change`도 미등록.
- 스티커 메시지 본문 `:st:<팩>/<1-8>:`, 팩 미보유면 서버가 `sticker_not_owned`. 일반 이모지는 자유. 스티커 이모지는 Android 7(Unicode 9) 이하만 사용.
- VIP: 친구가 아니면 이름 "VIP", 나이 0, 사진·아이디·국가·소개 없음(서버 뷰 `profiles_seen`/`profiles_public`, profiles에 열을 추가하면 두 뷰에도 추가). 앱은 성별도 숨김(서버 응답에는 성별이 남아 있음). 이름 "VIP"는 예약(047).
- 서버 테스트(롤백): `server/test_walls_stickers_vip.sql`. 실서버에서 구매→적용·스티커 거부는 앱으로 직접 눌러보지 않음(데모·SQL 테스트만).

## 2026-10-23 0.17.0: 국가 선택 랜챗·메신저 교환·매너 끄기·보이스룸 연결 끊김 (마이그레이션 043–045 적용 완료)
- 서버 테스트(롤백): `server/test_countries.sql`, `test_friend_contacts.sql`, `test_voice_lost.sql`.
- 번역(`translate` 함수)은 Anthropic API 크레딧 부족으로 실패 중이었음(로그: credit balance too low). 크레딧 충전 필요. 이제 그런 경우 503 translate_unavailable로 앱이 조용히 자동번역을 멈춤.
- 남은 일: 웹의 "백그라운드 30분 퇴장"은 그대로(안드로이드만 삭제), Android 국가 선택 목록은 가입용 약 50개국뿐(웹은 전체), 실기기 테스트(Doze·화면 꺼짐 3초 하트비트) 미확인.
- next 16.4.2로 올림(16.4.0 보안 권고로 빌드가 멈췄음).

## 2026-10-23 0.16.0: 차단 목록·아이디 검색·아이디 30일/변경권·신상 잠금·문구 검수 (마이그레이션 042 적용 완료)
- 서버: `042_blocks_search_locks.sql`(실서버에는 042a–h로 나눠 적용; 빈 "042_blocks_search_locks" 기록 하나는 내용 없음). 테스트 `server/test_blocks_search.sql`(롤백) 통과. verify-purchase v12(`handle_change`).
- 남은 일: `phone_identities` 1년 지난 행 지우기(지금은 읽을 때 무시만 함) — 삭제문이 MCP 확인 대기에 걸려 보류, 사장님 승인 후 pg_cron 정리 작업에 추가.
- Play 상품 `handle_change`(소모성) 등록 필요. 실결제 순서는 LAUNCH_GUIDE 4-00.
- 번역: 35개 언어 새 문구 추가, hi/ko/ja/en 검수 완료. 다른 언어는 원어민 검수 권장. 영어 원문이 약간 바뀐 `pass_buy`, `reason_harassment`, `voice.sys.audience`는 다른 언어가 옛 영어 기준.

## 2026-10-22 0.15.0: 아이디·꾸미기·방 화면 (마이그레이션 040·041 적용 완료)
- 설계와 디자인 수치: `docs/LOOKS_SPEC.md`. 서버 테스트: `server/test_handles_looks.sql`(롤백식, 실서버에서 통과).
- 040: `profiles.handle`(대소문자 무시 유일, 2–24자, `handle_valid()` 허용 문자표 — 웹 `lib/handle.ts`·앱 `domain/Handle.kt`가 같은 표를 복사함, 바꿀 때 셋 다), 이름의 꾸밈 글꼴 문자 거부(`name_plain`), `cosmetic_items`(26개)·`user_cosmetics`·`profiles.look_*`, RPC `handle_check`·`set_handle`·`shop`·`equip_look`·`looks`·`admin_grant_cosmetic`, `redeem_cosmetic`(service_role 전용), `play_revoke`가 환불된 꾸미기를 벗기고 회수, 커버·색상 무시, 끝난 랜챗은 친구끼리만 새 메시지(`chat_open`, RLS).
- 041: 환불된 토큰은 다시 지급하지 않음. verify-purchase v11(꾸미기 = 비소비형 인앱, Play 확인 후 지급·확인 처리).
- 폰트 6개: Latin 부분집합, OFL에 따라 이름 변경(`Yaari Look *`), 라이선스 `docs/font-licenses/`. 웹 `public/fonts/looks`(루트 .gitignore의 `public/` 때문에 `git add -f`로 추가함).
- 남은 일: Play Console에 상품 26개 등록(LAUNCH_GUIDE 4-0), 실기기에서 결제·드래그 확인.

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
