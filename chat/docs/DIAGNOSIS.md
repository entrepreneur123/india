# Yaari 기술 진단 보고서 (2026-10-31, 코드 조사 — 수정 전)

범위: `chat/web`(Next.js 16 정적 내보내기), `chat/server/migrations` 001–117, `chat/server/functions`(Deno 엣지 함수 13개), Vercel·Supabase·LiveKit 설정, 이 날 실서버에서 직접 잰 값. 표기: **[확인]** = 코드·실측으로 확인, **[추정]** = 근거는 있으나 실서버에서 재현하지 않음, **[미확인]** = 추가 검증 필요. 근거는 `파일:줄`.

## A. 지금의 아키텍처와 실제 구현 상태

| 층 | 실제 구현 [확인] |
|---|---|
| 프론트 | Next.js 16 `output: 'export'`(정적) → Vercel CDN. React 19, SWR, Tailwind v4. 데모 백엔드(`lib/mockBackend.ts` 1,717줄)와 실서버(`lib/supabaseBackend.ts` 2,640줄)가 같은 `Backend` 인터페이스(`lib/backend.ts` 1,124줄). 36개 언어, RTL 4개. |
| 백엔드 | 별도 서버 없음. Supabase(ap-south-1 뭄바이, Free, Micro 공유 CPU): Postgres + PostgREST RPC + Auth(전화 OTP) + Storage + Realtime. 비즈니스 로직은 거의 전부 **SQL 함수(security definer)** 안에 있고 테이블 직접 접근은 거의 막혀 있다(`001_core.sql:153` revoke all; 66개 테이블 전부 RLS). |
| 실시간 | Supabase Realtime **Broadcast**(비공개 토픽 `user:<uid>`, `chat:<match>`, `voice:<room>`, `call:<match>`; 트리거가 `realtime.send`)와 클라이언트 간 **Presence**(`typing-<match>`, 공개). Socket.IO·자체 WebSocket 없음. 폴링은 보조(랜덤 매칭 2초, 채팅 재동기화 10/60초). |
| 음성·영상 | LiveKit Cloud(SFU, 지역 자동 라우팅, TURN/TLS 443). 방: `lib/voice-audio.ts`(듣는 사람은 `autoSubscribe:false`, 소리 켠 사람의 트랙만 구독). 1:1 친구 통화: `lib/p2p-call.ts` **P2P(WebRTC, STUN만) 우선** → 12초 안에 안 붙거나 UDP가 막히면 LiveKit로. 토큰은 엣지 함수 `voice-token`/`video-token`(5분 유효, 자리 있는 사람만 publish). 역할 변경·강퇴는 `voice-sync`가 LiveKit API로 집행. |
| 매칭 | `find_match4`(`096_country_first.sql:7-105`): 사용자별 advisory lock + 상대 행 `for update skip locked`로 중복 매칭 차단 [확인·`test_matching_race.sql`]. 자국 우선 → 8초 뒤 확장, 직전 상대 5초 회피, 양방향 차단·정지·검증 게이트. 대기열은 `queue` 테이블(PK user_id, 인덱스 `queue_seen`만). 파티(2~50명) 매칭은 `party_queue`(076). |
| 데이터 | 117개 마이그레이션. 인덱스는 N3(113)에서 FK 12개 보강. 보존: 매치 30일·방 7일·알림 90/180일 등 `purge_old_data`(단, 현재 본문은 `owner_run_once_4.sql`에만 있음 [확인]). |
| 인증·권한 | 전화 OTP만(이메일 제공자는 봇용으로 켜져 있음 [확인 LAUNCH_GUIDE:65]). 관리자 = `admins` 테이블(`021:79-108`), 시드가 **테스트 번호 919000000001**. KYC(Didit) 테이블·함수는 있으나 `require_verification=false`. |
| 배포·운영 | Vercel Hobby(Git 자동배포 아님, MCP로 재배포). CI: web은 `tsc`+`build`만(단위 테스트 107개·SQL 테스트 24개는 **CI에서 안 돎** [확인 ci.yml:84-87]). 관측: Sentry(오류만), PostHog(이벤트 6개), 알림(alerting) **없음**. 백업: Free 플랜(없음으로 추정 [추정]). |

실측(2026-10-31, `docs/SCALE_UP.md` §1·§4): 사이트·API는 전 지역 CDN 엣지 1–50 ms; LiveKit 인도 30–92 ms, 중동 66–163 ms, 이란 177–292 ms(리전 없음). 음성: 현 설정(RED+DTX) PESQ 2.4(깨끗한 망) / RED만 3.9, 손실 10%에서 3.4; P2P 통화(브라우저 기본) 손실 10%에서 1.4.

## B. 가장 심각한 문제 TOP 10 (우선순위 = 사장님 기준: 치명 버그 → 보안 → 실시간 안정 → 성능 → 구조 → UX)

| # | 문제 | 근거 | 영향 |
|---|---|---|---|
| 1 | **방 비밀번호가 로그인한 누구에게나 읽힌다** [확인·실서버 재현] | `017:87-88` `grant select on voice_rooms … using (true)` + `058:8` 평문 `password` 컬럼. 테스트 계정 B가 REST로 A의 방 비밀번호 'Secret42'를 그대로 읽음(leak2.mjs). 초대 코드는 별도 테이블(`voice_room_codes`, 019:38)이라 안전. | 잠긴 방이 사실상 잠기지 않음. |
| 2 | **관리자 계정 = 고정 OTP 테스트 번호** [확인] | `021:85` 시드 919000000001, 고정 코드는 저장소 문서에 적힘(LAUNCH_GUIDE:64). 테스트 번호 2개·봇 비밀번호(이 세션 HANDOFF에 평문 기록) 삭제 전 출시 금지. 유출 비밀번호 보호 off. | 저장소를 본 누구나 관리자로 로그인해 정지·신고 대화 열람 가능. |
| 3 | **1:1 통화가 손실망에서 가장 약하고, 한 번 끊기면 일주일 내내 유료 경로** [확인·실측] | `p2p-call.ts`는 브라우저 기본 Opus(RED 없음, ~29 kbps) → 손실 10% PESQ 1.39. `voice-audio.ts:252`·`livekit.ts:157`: 비최종 Disconnected 한 번에 7일 relay 플래그 → 이후 모든 친구 통화가 P2P를 건너뛰어 LiveKit 분·TURN 소모. 방·통화의 RED+DTX 조합은 NetEq가 305 ms를 잘라 2.4점. | 통화 품질·비용. |
| 4 | **통화·방의 서버 측 수명 관리가 없다** [확인] | 벨 타임아웃 없음(발신자 탭이 죽으면 `requested`+미읽음 `call` 알림이 남아 수신자에게 모달이 계속 뜸, `incoming-call.tsx:57-62`); `end_video`(001:293)는 LiveKit 참가자를 안 내보냄; 방은 cron이 없어 전원 이탈 시 `ended_at`·`left_at`이 영원히 null(`voice_settle`은 다른 멤버 RPC 안에서만 실행); `create_voice_room2`(028:38)는 옛 방 멤버 행을 안 닫음. LiveKit 웹훅 핸들러 없음. | 유령 방·유령 벨, 목록·통계 오염, 분 과금(접속 유지 시). |
| 5 | **랜덤 매칭의 유령 상태** [확인] | 탭 닫기/새로고침에 `cancel_search` 없음(`app-shell.tsx:336` pagehide는 presence만) → 20초간 매칭 가능 → 상대는 죽은 채팅에서 12초 대기(`supabaseBackend.ts:716`). 열린 매치의 서버 타임아웃 없음(둘 다 죽으면 영구 open, `chat_readable` true). 뽑힌 직후 취소(`011:93`)는 매치를 안 끝냄. 숨긴 탭도 20초간 매칭됨. `match_card2` 일시 오류 한 번에 멀쩡한 매치를 `abandon`(`:546-549`). | 체감 "연결 안 됨"의 주범 후보. |
| 6 | **매칭·하트비트에 호출 제한이 없고, 파티 매칭에 구멍** [확인] | `find_match4`·`voice_heartbeat3`에 `slow_down` 없음(CAPACITY:149); `party_state(ready)`는 검증 게이트 우회(`076:30,46`); 파티 페어링이 늦게 폴링한 멤버의 upsert로 지워질 수 있음(`076:51-65` [추정, 재현 안 함]); 친구 요청은 거절→재요청이 무제한(`006:207`, `009:133`). | 봇·스팸에 열려 있음, 파티 중복 매칭. |
| 7 | **무료 요금제 상한 + DB CPU** [확인·실측] | Realtime 200 동시, Micro 공유 CPU(봇 16개로 504 다발), LiveKit Build 동시 100·월 5,000분 하드캡, Vercel Hobby 비상업 약관. 매칭 스캔은 8초 뒤 대기열 전체 O(n)을 2초마다(`096:72-95`, 인덱스 `queue_seen`만); 방은 화면 켠 멤버마다 3초 하트비트(200명 방 ≈ 67 RPC/s). | 동시 수백 명에서 막힘. |
| 8 | **운영 체계 공백** [확인] | 알림(alerting)·업타임 체크 없음; CI가 테스트를 안 돌림; `purge_old_data`·`yaari-queue-cleanup`·`yaari-translate-tidy`가 마이그레이션 밖(`owner_run_once_*.sql`)에만 있어 스키마가 저장소와 어긋남; 108–110·117은 손으로 기록; `yaari-bots` cron 상태 불명(031:84는 해제, HANDOFF는 실행 중). 백업 없음(Free). | 장애를 사용자가 먼저 안다; 재구축 불가. |
| 9 | **중간 보안 구멍 묶음** [확인] | 타이핑/프레즌스 채널 공개(`supabaseBackend.ts:773,2200`: 아무 매치·방에 가짜 "입력 중"); `log_events_anon`이 anon 키로 호출 가능(`021:67-75`, 일 5,000건 공용 한도 소진 가능); `link-meta` 제한이 인스턴스 메모리(`link-meta:80-90`); KYC 웹훅 서명 검증 없음(`kyc:59-64`, API 재조회로 완화); CSP `frame-src https:`·헤더 `unsafe-inline`(메타 해시로 보완); 신고에 대한 알림 없음(사람 검토인데 아무도 모름). | 괴롭힘·남용 대응 지연. |
| 10 | **구조 부채** [확인] | Android 호환용 중복 엔드포인트 누적: find_match3/4, friend_activity/2, profile_social/2, save_highlight/2, highlights_of/2, explore_feed/2; LiveKit 집행이 클라이언트의 fire-and-forget에 의존(`supabaseBackend.ts:1852`); `supabaseBackend.ts` 2,640줄 단일 파일; 매칭 `met`에 DM 스레드가 섞임(`096:51-53`, 083:22). | 유지보수·회귀 위험. |

## C. 먼저 풀 것과 이유
1. **#1 방 비밀번호**(마이그레이션 1개, 반나절): 실서버에서 재현된 유일한 데이터 노출. `voice_rooms`의 테이블 단위 select를 컬럼 목록 grant로 바꾸고(`password` 제외), 웹은 RPC만 쓰므로 영향 없음(`from('voice_rooms')` 직접 읽기 0건 [확인]). Android 직접 select 여부는 확인 후.
2. **#2 출시 전 계정 정리**(사장님 작업 + SQL): 테스트 번호 삭제, 관리자 시드를 사장님 실번호로, 봇 비밀번호 null, 이메일 제공자 off, 유출 비밀번호 보호 on, 문서에서 비밀번호 제거.
3. **#3 음성 설정**(코드 2곳 + P2P 1곳, 하루): 측정이 끝났으므로 바로 적용·재측정 가능. relay 플래그는 "최종 실패/2회 재구축"일 때만.
4. **#4·#5 수명 관리**(마이그레이션 1~2개 + pg_cron 1개, 1~2일): 벨 60초·열린 매치 5분·빈 방 2분·멤버 행 정리를 하는 "청소부" 함수를 cron으로; `end_video`가 voice-sync를 부르고, pagehide에 `cancel_search` keepalive.
5. **#6 호출 제한**(마이그레이션 1개): `find_match4` 초당 1회, 하트비트 초당 1회, 친구 재요청 24시간, 파티 검증 게이트.
6. **#8 운영**: CI에 `npm test`·SQL 테스트, `purge_old_data`·cron을 마이그레이션으로 되돌리기, Sentry 알림 규칙 + 외부 업타임 체크(무료), Supabase 사용량 경보.
7. **#7 요금제**는 코드가 아니라 결제(SCALE_UP §6): 마케팅 전 Vercel Pro·Supabase Pro+Small·LiveKit Ship.

## D. 권장 아키텍처 (지금 규모에 맞게)
- **그대로 둘 것**: Supabase 단일 리전(뭄바이) + Vercel CDN + LiveKit Cloud. 실측상 타깃 지역 지연이 충분히 낮아 멀티리전·자체 미디어 서버는 불필요. SQL 함수 중심 구조도 이 규모에선 가장 싸고 안전하다(RLS 전 테이블 적용 확인).
- **더할 것**: ① pg_cron 청소부(위 C-4), ② LiveKit **웹훅**(participant_left/room_finished → DB 반영; 지금은 없음), ③ 호출 제한 표준 함수 하나(`rate_ok(key, n, window)`)로 매칭·하트비트·요청 통일, ④ Realtime Postgres Changes 잔여분을 Broadcast로(108 계획대로), ⑤ 비밀 컬럼은 별도 테이블 또는 컬럼 grant.
- **나중에(동시 수천 명)**: Supabase Pro 지출 한도 해제로 Realtime 1만, 컴퓨트 Medium 이상, LiveKit Scale, 매칭 대기열을 Redis가 아닌 **Postgres 파티션 + 복합 인덱스(seen_at, country, anon)** 로 먼저 버티기, 읽기 전용 복제본은 그 다음.

## E. 작업 순서와 복잡도
| 순서 | 작업 | 복잡도 | 검증 |
|---|---|---|---|
| 1 | **완료** 118: 비밀번호를 `voice_room_passwords`로(함수만 읽음) | 낮음 | REST로 B가 못 읽음(leak2.mjs), verify118 12/12 |
| 2 | **완료** 음성: `dtx:false`(방·통화), P2P에 RED+48k+FEC, relay 플래그 1일 | 낮음 | Chromium 루프백에서 RED 협상·86 kbps 확인(sdp-full.mjs), 프로덕션 통화 e2e |
| 3 | **완료** 119: 청소부 cron 30초(벨 60초·양쪽 5분 부재 매치·방 settle·멤버 행) + pagehide cancel_search + match_card2 재시도. `end_video`→LiveKit 강제 퇴장은 미룸(P2P 기본, 상대 앱 60초 resync) | 중간 | 프로덕션 픽스처 3종 + 첫 실행이 유령 1·3·81 정리 |
| 4 | **완료** 120: find_match4·voice_beat 1초 제한(기존 seen_at으로, 새 테이블 없음), 친구 재요청 1시간, party 인증 게이트 + 늦은 멤버 upsert 버그 | 중간 | RPC 폭주 + SQL(행세) 테스트, 파티 픽스처 |
| 5 | **완료(일부 사장님)** CI에 웹 단위 테스트, 122로 purge/cron 기록, Sentry 알림 규칙은 이미 3개, 업타임 모니터는 만들었으나 무료 플랜 좌석 없음(사장님) | 낮음 | CI 실행 결과, Sentry 콘솔 |
| 6 | **완료** 121: typing 채널 private + 정책, log_events_anon anon 회수, rate_ok로 link-meta 제한 DB화. 남은 것: KYC 웹훅 서명(공급자 비밀 — 사장님), 신고 알림(알림 kind 제약 변경에 DROP 필요 — 사장님 SQL) | 중간 | 두 계정 e2e 5/5, link-meta 31회 호출 |
| 7 | 구조: `…2` 정리는 Android 전환 뒤 DROP(사장님), supabaseBackend 분할은 기능 안정 뒤 | 높음 | tsc·테스트 |

## F. 비용이 드는 항목 (공식 가격, SCALE_UP §3)
Vercel Pro $20/월(상업 이용 필수) · Supabase Pro $25 + 컴퓨트 Small $15 · LiveKit Ship $50 · 도메인(Pro 첫해 무료 TLD 있음) · Twilio Verify/WhatsApp 건당 · (선택) Sentry 유료 알림은 불필요, 무료로 가능.

## G. 지금 당장 vs 사용자 늘어난 뒤
- **지금(무료 상태에서 코드로)**: B의 #1·#3·#4·#5·#6·#8·#9 전부. 비용 0.
- **마케팅 직전(결제)**: #7 요금제 3개 + 인증 문자(DLT·WhatsApp) + 테스트 계정 정리.
- **동시 1,000명 넘으면**: 매칭 복합 인덱스·대기열 파티션, Realtime 상한 증설, LiveKit Scale, 읽기 복제본 검토, 지역별 Realtime 분리는 그때 측정으로.

측정 지표(지금 잴 수 있는 것만): 매칭 대기시간(`queue.joined_at`→`matches.created_at`), 매칭 성공률(검색 시작 대비 매치), 채팅 첫 줄까지 시간, 통화 연결 성공률·P2P 비율(`call` 상태 전이), 방 입장→소리까지 시간, LiveKit 분/일, Supabase CPU·Realtime 동시 연결(대시보드), 504/오류율(Sentry), 신고 처리 지연. 봇 떼(bots.mjs)로 부하, 두 계정 e2e로 회귀.
