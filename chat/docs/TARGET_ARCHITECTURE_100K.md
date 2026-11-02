# Yaari 목표 아키텍처 — 동시접속 10만 (2026-11-02 설계)

이 문서는 **목표 설계도**다. 실제 10만 명 부하 시험은 하지 않았다. 따라서 "10만 명을 버틴다"는 확인된 사실이 아니라 이 구조가 노리는 목표다.

표기
- [실측]: 직접 실행하거나 실서버에서 조회한 값
- [코드]: 저장소 코드를 읽고 확인한 것
- [문서]: 공식 문서·가격표 (링크는 BUILD_VS_BUY·COST_MODEL 출처)
- [추정]: 계산값 (가정은 `capacity_model.py`)

관련 문서
- 기술별 자체구축 판단: `BUILD_VS_BUY.md`
- 비용: `COST_MODEL_100K.md`
- 전환 순서: `MIGRATION_ROADMAP.md`

## 0. 지금 구조에서 출발한다 (2026-11-02 확인)

| 층 | 지금 | 근거 |
|---|---|---|
| 웹 | Next.js 16 정적 내보내기(`output: 'export'`) → Vercel Hobby CDN. 서버 함수 0개 | [코드] `web/next.config`, LAUNCH_CHECKLIST §1 |
| DB·API | Supabase Free, 뭄바이(ap-south-1), Micro(공유 CPU) | [실측] Postgres 17.11, max_connections 60, DB 57 MB, public 테이블 68·함수 394개, 프로필 509개, pg_cron 활성 7개 |
| 비즈니스 로직 | SQL 함수(`security definer`) + RLS 전 테이블. 클라이언트는 RPC만 호출 | [코드] DIAGNOSIS A |
| 실시간 | 웹은 Supabase Realtime **Broadcast만** 사용(비공개 토픽 `user:`·`chat:`·`call:`·`voice:`·`typing-`) | [코드] 웹에 `postgres_changes` 0건 |
| 실시간 (Android) | 아직 **Postgres Changes 5곳** 사용 | [코드] `SupabaseBackend.kt:459,535,539,1174,1737` |
| 음성 (방) | LiveKit Cloud SFU + Krisp(BVC) | [코드] `lib/livekit.ts`, `lib/voice-audio.ts` |
| 음성 (1:1) | **친구끼리만** 통화(`call_open`: `kind='dm'`, 익명 아님, 친구, 차단 아님) | [코드] `070_anon_chat_friend_calls.sql:111` |
| 음성 (1:1 경로) | P2P(STUN만) 우선 → 실패하면 LiveKit으로 | [코드] `lib/p2p-call.ts` |
| 랜덤 채팅 | **글만** 됨. 2026-10-27 사장님 결정 | [코드] 070 머리말 |
| 미디어 주소 | 서버가 토큰과 함께 내려준다. 앱 업데이트 없이 서버에서 바꿀 수 있음 | [코드] `voice-token/index.ts:31` `{url, token}` |
| 토큰 발급 | SDK 없이 직접 서명 | [코드] `_shared/jwt.ts` |
| LiveKit 서버 API | 표준 RoomService Twirp | [코드] `_shared/livekit.ts` |

**문서와 코드가 어긋나는 곳** (이번에 확인)
1. CAPACITY.md(10-23)의 가정 "랜덤채팅 2만 명 중 **영상 1만 명**"은 070 이후 맞지 않는다. 지금은 모르는 사람끼리 통화할 수 없다.
2. CAPACITY.md(10-26)의 "1:1 채팅 따라잡기 10초"는 연결이 정상일 때 맞지 않다. 실제로는 10초마다 검사하지만 재동기화는 6번째마다(=60초) 한다. 비정상일 때만 10초다.
   - 근거: `supabaseBackend.ts:797` `(!healthy || ++ticks % 6 === 0)`
3. 실서버 함수별 평균 시간 [실측, `pg_stat_statements`, 10-21 이후 누적, Micro 공유 CPU라 편차 큼]
   - `find_match4`: 224 ms (최소 0.7)
   - `my_badges`: 51 ms
   - `friend_list`: 53 ms
   - `online_count`: 39 ms
   - `voice_heartbeat3`: 9.5 ms
   - Realtime 복제 폴링(WAL 읽기): 288,523회, 평균 6 ms. DB 총시간 1위
   - → CAPACITY의 "회당 4–6 ms" 가정은 heartbeat에만 맞고, 매칭·배지는 10배 이상 무겁다.

## 1. 큰 그림: Control Plane과 Media Plane 분리

```
                    ┌──────────── 사용자 (웹 / Android) ────────────┐
                    │  정적 앱(CDN)   WebSocket 1개   WebRTC(음성)   │
                    └───────┬───────────────┬────────────────┬──────┘
                            │ HTTPS         │ WSS            │ UDP/TCP/TLS443
   ┌────────────────────────▼───────────────▼──────┐  ┌──────▼─────────────────────────┐
   │ CONTROL PLANE (홈 리전 1곳 → 나중에 2곳)        │  │ MEDIA PLANE (리전마다 독립)     │
   │  API: PostgREST(RPC) + Auth(GoTrue)           │  │  LiveKit OSS SFU 노드 N대       │
   │  Realtime: Supabase Realtime(Elixir) 클러스터  │  │  내장 TURN(UDP 3478, TLS 443)   │
   │  Hot state: Valkey (presence·매칭 대기열·제한) │  │  Redis/Valkey(노드 간 방 위치)  │
   │  Postgres 주 1대 + 대기 1대 + 읽기 복제         │  │  리전: 뭄바이 → 두바이·싱가포르 │
   │  작업자: pg_cron, 푸시·KYC·결제 웹훅            │  │        → 미국 동부              │
   └───────────────┬───────────────────────────────┘  └──────▲─────────────────────────┘
                   │ 토큰 발급(리전·노드 선택) ─────────────────┘
   ┌───────────────▼───────────────────────────────────────────────────────────────────┐
   │ 외부 사업자 (어댑터 뒤에 둠): SMS·WhatsApp OTP │ KYC │ 결제(Play·App Store·PG) │ 푸시 │
   └───────────────────────────────────────────────────────────────────────────────────┘
```

원칙
1. **두 평면은 서로를 기다리지 않는다.**
   - Control Plane이 잠시 멈춰도 이미 연결된 통화는 계속된다(SFU는 토큰만 검증).
   - SFU 한 대가 죽어도 로그인·채팅은 멀쩡하다.
2. **Control Plane은 홈 리전 1곳에서 시작한다.** 지연을 줄이는 일은 CDN(정적)과 Media Plane(리전별 SFU)이 맡는다.
   - 실측 근거(SCALE_UP §1): API 엣지는 전 지역 1–50 ms이고, 원점 왕복은 채팅·매칭에서 체감 한계 안이다.
3. **새 서비스는 측정된 병목이 있을 때만 만든다.** 마이크로서비스를 먼저 쪼개지 않는다.

## 2. Control Plane

### 2-1. 무엇을 그대로 쓰고 무엇을 바꾸나

| 구성요소 | 목표 | 왜 |
|---|---|---|
| DB | **Postgres 17 자체 운영**: 주 1 + 동기 대기 1(Patroni) + 비동기 읽기 복제 1–2 | 마이그레이션 133개·SQL 함수 394개를 그대로 쓴다. 다시 짜는 비용이 가장 크다. |
| API | **PostgREST**(Supabase 자체 호스팅 묶음에 포함) | 클라이언트 `rpc()` 호출이 그대로 동작한다. |
| 인증 | **Supabase Auth(GoTrue) 자체 호스팅** + Send SMS Hook으로 우리 OTP 어댑터 연결 | JWT 형식·`auth.uid()`·RLS가 그대로다. |
| 인증 (OTP 생성·검증) | GoTrue가 하고, 발송만 우리 어댑터가 한다 | [문서] "The Send SMS Hook replaces Supabase's built-in SMS sending", 훅은 OTP를 받는다. |
| 실시간 | **Supabase Realtime 자체 호스팅**(Elixir/Phoenix, Apache-2.0) | 웹이 쓰는 Broadcast·Presence API 그대로. |
| 실시간 (규모) | 공식 벤치마크: WebSocket Broadcast 32,000명·22.4만 msg/s, DB 발 Broadcast 8만 명·1만 msg/s, 대형 팬아웃 25만 명(2–6노드) | [문서] |
| 실시간 (제한) | 요금제 상한 없음. `max_concurrent_users`는 우리가 정하는 설정값 | [문서] ARCHITECTURE.md |
| Hot state | **Valkey**(BSD, Redis 호환): presence·온라인 수·호출 제한·매칭 대기열 | 3초 heartbeat·2초 매칭 폴링을 DB 밖으로 뺀다. 3-1절 참고. |
| 파일 | S3 호환 오브젝트 스토리지(사진·음성메모) + CDN | 사업자 교체가 쉽다(S3 API). |
| 작업 | pg_cron(청소부·보존기간) 유지. 외부 호출은 작업 큐 → 작업자 | 지금 구조 그대로. |

**새로 만드는 서비스는 하나뿐: "yaari-edge"** (Go 권장, 3-1절)
- 매칭 엔진
- presence 집계
- 호출 제한
- 미디어 리전 선택
- 모두 홈 리전의 한 바이너리 안에 둔다(모듈형 단일 서비스).
- 쪼개는 시점: 매칭 처리량이나 WebSocket 수가 한 프로세스 한계에 다가간다는 측정이 나왔을 때.

### 2-2. 언어 선택 (yaari-edge)

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| **Go** | 고루틴으로 연결 수만 개를 가볍게 다룸, 단일 바이너리 배포. LiveKit·Pion·coturn 대체(Pion TURN)가 같은 생태계 | 웹과 타입 공유 안 됨 | **권장**. 미디어 쪽(LiveKit)과 운영 도구가 같다. |
| Elixir | Realtime이 이미 Elixir. presence·팬아웃에 강함(Discord 사례) | 인력·디버깅 난도. 우리가 소스를 고칠 일이 거의 없어야 함 | Realtime은 **그대로 쓰기만** 한다. |
| Node/Deno(TS) | 웹·엣지 함수와 언어 같음 | 단일 스레드라 CPU 작업(매칭 점수) 시 여러 프로세스 필요 | 엣지 함수(웹훅·결제)는 계속 Deno |
| Rust | 최고 효율 | 개발 속도·유지보수 인력 | 이 규모엔 과함 |

선택 기준: 유행이 아니라 **운영 인력 1–2명이 새벽 장애를 고칠 수 있느냐**다. 그래서 Go 하나와 SQL을 기본으로 한다.

### 2-3. 데이터 저장 구조

| 데이터 | 저장소 | 보존 | 비고 |
|---|---|---|---|
| 계정·인증 | `auth.users`(GoTrue) | 탈퇴 시 삭제 | 전화번호는 E.164 |
| 프로필·친구·차단·설정 | Postgres | 영구 | 지금 그대로 |
| 랜덤 매치·메시지 | Postgres | 30일(기존 정책) | 월 단위 **파티션**으로 바꿔 `purge`를 DROP PARTITION으로 |
| DM·방 메시지 | Postgres | 기존 정책 | 같은 파티션 방식 |
| 신고·조치 기록 | Postgres(별도 스키마) | 법정 보존 기간 | 탈퇴해도 남김(LAUNCH_GUIDE §6 보류 항목) |
| 결제 장부 | Postgres(별도 스키마, 추가만 하는 원장) | 세법 보존 기간 | 5장 |
| presence·온라인 수·대기열·호출 제한 | **Valkey** | 휘발 | 잃어도 재접속으로 복구 |
| 사진·음성메모 | 오브젝트 스토리지 | 기존 정책 | 서명 URL |
| KYC | **결과와 해시만**(성별·생년월일·국적·문서번호 비밀 해시). 원본·얼굴은 보관 안 함 | — | [코드] 035, LAUNCH_GUIDE 4-3 |
| 로그·지표 | Loki/Prometheus 또는 저렴한 SaaS | 30–90일 | 브라질 Marco Civil 접속기록 6개월(LAUNCH_GUIDE §5)은 별도 테이블 |

### 2-4. Postgres 확장 순서

1. **세로 확장**: 전용 16–32 vCPU 한 대가 현재 SQL 함수 구조로 수천 RPC/s를 처리한다.
   - 근거 [추정]: heartbeat 같은 가벼운 함수 1–10 ms 기준.
2. **읽기 분리**: 목록·프로필·피드 같은 읽기 RPC를 읽기 복제본으로.
   - PostgREST는 하나의 DB에 붙으므로 읽기 전용 PostgREST를 하나 더 띄운다.
3. **쓰기 줄이기**: 3-1절의 hot state 이전. 쓰기 부하가 가장 큰 heartbeat·매칭이 빠진다.
4. **파티셔닝**: 메시지·매치를 시간 파티션으로.
5. **샤딩(마지막 수단)**: 사용자 ID 기준 Citus. AGPL이고 운영 난도가 높다.
   - 10만 CCU에서도 3–4번까지로 충분할 것으로 본다 [추정, 부하시험으로 확인 필요].
6. **다지역 DB**: 홈 리전 밖 사용자의 쓰기 지연이 문제가 될 때 검토한다.
   - 예: 북미 비중이 크고, 채팅 전송 p95가 300 ms를 넘을 때.
   - 그전에는 읽기 복제본 + 미디어 리전 분리로 충분하다. 개인정보 국외이전 규제(6장)도 함께 본다.

### 2-5. 백업·장애 복구 (자체 운영 시 반드시)

- **백업**
  - pgBackRest 또는 WAL-G로 연속 WAL 보관 + 하루 1회 전체 백업
  - 다른 사업자의 오브젝트 스토리지에 보관(암호화)
  - 목표: RPO 5분 이하
- **고가용**
  - Patroni + etcd 3노드, 동기 대기 1대
  - 주 서버 장애 시 자동 승격, 목표 RTO 1분 안팎
- **복구 훈련**: 매달 한 번 새 서버에 백업을 복원해 `test_*.sql` 24개를 돌린다. 훈련하지 않은 백업은 백업이 아니다.
- **비밀값**: 서비스 키·JWT 비밀은 서버 비밀 저장소에 두고 저장소에는 넣지 않는다.

## 3. 실시간·매칭

### 3-1. 지금 DB를 가장 많이 두드리는 것과 목표 처리 [코드 → 목표]

| 지금 | 주기 | 목표 처리 |
|---|---|---|
| 보이스룸 heartbeat `voice_heartbeat3` | 3초(화면에 방 있음) / 20초(백그라운드 청취) | Realtime **Presence** 또는 yaari-edge가 메모리(Valkey)에서 판단. DB에는 입장·퇴장·60초 요약만 |
| 랜덤 매칭 `find_match4` | 2초 폴링 | **한 번 대기열 등록 → 짝이 되면 `user:<uid>`로 Broadcast**. 대기열은 Valkey 정렬 집합(국가·언어·성별 버킷), 매칭 결과만 DB에 `matches` insert |
| 배지 `my_badges` | 30초 | 알림 트리거가 이미 `user:<uid>`로 방송 → 폴링 제거, 앱 시작·포커스 때 1회 |
| 온라인 수 `online_count` | 15초 | Valkey 카운터 또는 5초마다 계산한 캐시 1행 |
| 방 목록 | 30초 | 이미 5초 서버 요약 캐시(`voice_list_refresh`). 캐시를 CDN/엣지에서 서빙 |
| 친구 상태 | 60초 | presence 구독(친구 수만큼 토픽) 또는 그대로 60초 |
| 채팅 재동기화 | 60초(정상) | 그대로 |

모델 결과 [추정, `capacity_model.py`, 음성 10%] — 위 처리로 DB 호출이 **약 4.5배** 준다.

| CCU | 지금 코드 | 목표 구조 |
|---|---|---|
| 1만 | 약 1,870/초 | 약 420/초 |
| 10만 | 약 18,700/초 | 약 4,200/초 |

### 3-2. 매칭 엔진 (yaari-edge 모듈)

- **버킷**: (국가, 원하는 성별, 익명 여부, 언어)별 대기열.
  - 지금 `find_match4` 규칙을 그대로 옮긴다: 자국 우선 → 8초 뒤 확장, 직전 상대 5초 회피, 양방향 차단, 정지·검증 게이트.
- **원자성**
  - 지금: advisory lock + `skip locked`
  - 목표: Valkey Lua 스크립트 하나로 "두 사람을 동시에 꺼내기"
  - 실패하면 둘 다 대기열로 되돌린다.
- **진실은 DB**: 짝이 지어지면 `matches` insert 성공이 확정이다. 실패하면 둘 다 대기열 복귀.
- **차단·정지 조회**: 대기열 진입 때 DB에서 한 번만 하고, 짝 지을 때는 메모리의 차단 목록을 쓴다(진입 시 로드).
- **규모** [추정]
  - 10만 CCU에서 검색 중 1만 명이면 짝 짓기 시도가 초당 수천 회다.
  - 메모리 연산이라 한 프로세스로 충분한 규모다. 버킷을 국가별로 나눠 락 경합도 작다.
- **전환 방법**: 이름이 다른 새 RPC/엔드포인트로 병행 운영하다가 바꾼다(헌법 5: 중복 엔드포인트는 전환 뒤 지움).

### 3-3. WebSocket 게이트웨이

- 1단계(목표 대부분): **Supabase Realtime 자체 호스팅이 곧 게이트웨이**다.
  - 연결 하나에 채널 여러 개라 동시 연결 수는 CCU와 같다. [문서] "A socket counts once while it has at least one channel"
  - 10만 연결은 공식 벤치마크(25만 팬아웃, 2–6노드) 범위 안이다. 우리 메시지 형태로는 **직접 부하시험 필요**.
- 2단계(필요할 때만): Realtime의 Postgres 연결·인증 비용이 병목이 되면 yaari-edge가 WebSocket을 직접 받는다.
  - 근거 [문서]: 노드당 입장 속도 제한 기본 10 joins/s 바닥.
  - 그 판단은 부하시험 결과로 한다.

### 3-4. 호출 제한·악용 방지

- 지금은 120·121의 `rate_ok`(DB 한 행)와 seen_at 비교를 쓴다.
- 목표: 같은 규칙을 Valkey 토큰 버킷으로. 키는 사용자·IP·기기 3중이다.
- DB 함수의 게이트는 최후 방어선으로 남긴다.

## 4. Media Plane

### 4-1. 통화 종류별 경로

| 통화 | 경로 | 이유 |
|---|---|---|
| 친구 1:1 | **P2P 우선** → 실패하면 우리 SFU | 이미 구현. 친구는 서로 IP를 보는 대가를 받아들인다(Signal의 연락처 기본값과 같은 판단, P2P_PLAN) |
| 모르는 사람 1:1 (도입 시) | **항상 서버 경유. P2P 금지** | IP 노출 = 위치·공격 위험 |
| 보이스룸 (N명) | **SFU**. Mesh 금지 | Mesh는 송신이 N−1배 |
| UDP가 막힌 망 | SFU 내장 TURN (TLS 443) | 지금도 LiveKit이 한다 |

**모르는 사람 1:1에서 TURN 대신 SFU를 쓰는 이유**
- 지금은 070 결정으로 모르는 사람 통화가 없다. 도입하면 위 규칙을 따른다.
- 비용: 1:1 기준 TURN 중계와 SFU 전달은 서버를 지나는 데이터량이 거의 같다(각자 1스트림 송신·1스트림 수신).
- 장점
  - SFU는 방과 같은 서버·같은 코드 경로라 운영할 것이 하나다.
  - 서버가 참가자를 강퇴하거나 끊을 수 있다(신고 즉시 차단).
  - 품질 지표를 서버에서 본다.
- 단점: 미디어 경로가 한 번 더 서버를 거친다. 같은 리전 SFU라면 지연 차이는 작다 [추정].

### 4-2. SFU 선택: LiveKit OSS 자체 호스팅

- 우리 클라이언트가 이미 LiveKit SDK다. 서버 API도 표준 Twirp를 쓴다([코드] `_shared/livekit.ts`).
- 그래서 바꿀 것은 **주소·키뿐**이다. 비교는 BUILD_VS_BUY §4.
- 자체 SFU를 처음부터 개발하는 것은 하지 않는다.
  - 이유: 혼잡 제어·심캐스트·재전송·RED를 직접 구현해야 하고, 그것을 검증할 인력이 없다.
- LiveKit 오픈소스를 우리 서버에 띄우는 것은 "자체 운영"이다(사장님 정의에 부합).

### 4-3. 리전 배치와 자동 선택

- **노드 구성**: 리전마다 LiveKit 노드 N대 + 같은 리전 Redis(Valkey)로 방 위치를 공유한다.
- **방 고정**: 한 방은 한 노드에 둔다.
- **리전을 잇지 않는다**: Cloud의 리전 간 메시처럼 여러 리전을 잇지 않는다.
  - 방은 방장의 리전에 만든다.
  - 다른 리전 청취자는 그 리전으로 붙는다(지연 +50–150 ms, 음성 방송형이라 허용) [추정].
- **선택은 토큰 서버(`voice-token`)가 한다**
  1. 클라이언트가 리전별 핑 결과를 보낸다(앱 시작 시 HTTPS 핑 3–4곳).
  2. 서버가 방의 리전·노드 부하로 `url`을 고른다.
  3. 이미 응답에 `url`이 있으므로 클라이언트 변경은 핑 측정만 추가하면 된다 [코드].
- **장애 전환**
  - 노드: 노드 다운 → LiveKit SDK 재연결 → 토큰 재발급 시 살아 있는 노드로. 방 상태는 DB에 있으므로 다시 만든다.
  - 리전: 리전 다운 → 토큰 서버가 다음 가까운 리전.
  - **전환 기간에는 LiveKit Cloud를 마지막 대체 경로로 남긴다**: 같은 SDK라 `url`만 바꾸면 된다.
- **자동 확장**
  - 기준: 노드 CPU 60–70% 또는 노드당 구독 트랙 수 한도.
  - 처음에는 사람이 노드를 추가한다(전용 서버는 즉시 생성이 안 됨).
  - 클라우드 VM 풀이 있으면 스크립트로 추가한다.

### 4-4. 리전 순서 (사용자 분포를 보고)

| 순서 | 리전 | 대상 | 조건 |
|---|---|---|---|
| 1 | 뭄바이 | 인도·남아시아 | 처음부터 |
| 2 | 두바이 또는 바레인 | 걸프 국가 | 중동 사용자 비중 10% 이상. **UAE·사우디 VoIP 규제 확인이 먼저**(6장) |
| 3 | 싱가포르 | 동남아 | 동남아 비중 10% 이상 |
| 4 | 미국 동부 | 북미 | 북미 비중 10% 이상 |

미디어 리전 추가 비용은 서버 1–2대와 대역폭이다. Control Plane은 늘리지 않는다.

### 4-5. 음성 품질

- **현재 설정 유지** [실측 근거 SCALE_UP §4]: Opus 48 kbps 모노 + RED + 인밴드 FEC, DTX 끔.
- **품질 지표 수집**
  - 클라이언트 `getStats` 요약(손실·지터·RTT·은폐)을 통화 종료 시 1행으로 저장한다.
  - 리전 라우팅을 측정으로 고치는 근거가 된다.
- **Krisp 의존성** (가장 큰 위험)
  - 2026-11-02 사장님 실기기 A/B에서 Krisp를 끄면 에코가 났다.
  - Krisp는 LiveKit Cloud 서버에 허가를 묻고 사용량을 보고한다 [코드: 패키지 0.4.5 소스].
  - 자체 서버로 옮기면 Krisp가 꺼질 가능성이 높다. 대체안은 BUILD_VS_BUY §5.
  - **결론: 방은 Krisp 대체안이 실기기 A/B를 통과하기 전까지 LiveKit Cloud에 남긴다.**
  - 1:1 친구 통화의 relay부터 자체 SFU로 옮긴다.
    - P2P 경로는 이미 Krisp 없이 돈다(P2P_PLAN).
    - relay 통화는 지금 Krisp를 쓴다(`livekit.ts:91` `cleanMicrophone`). 자체 SFU에서는 Krisp가 거절되어 컴퓨터는 음성 게이트, 휴대폰은 원본 마이크로 간다(`livekit.ts:243-248`의 대체 경로) → P2P 통화와 같은 수준.

## 5. 인증·결제·신뢰 (교체 가능한 어댑터)

### 5-1. 공통 원칙

외부 사업자는 우리 쪽 인터페이스 하나 뒤에 둔다. 우리는 **결과와 최소한의 증명값**만 보관한다.

```
interface OtpSender   { send(phoneE164, code, channel: 'sms'|'whatsapp', locale): Result }   // GoTrue Send SMS Hook이 호출
interface IdVerifier  { start(user, level): {redirectUrl, sessionId}; result(sessionId): {status, birthYear, sex, docCountry, docHash} }
interface PaymentRail { verify(receipt|orderId): Purchase; refundEvents(): Stream<Refund> }
```

### 5-2. OTP

- 생성·만료·검증·세션은 **GoTrue**가 한다(자체 운영).
- 발송은 어댑터가 한다.
  - 국가 → 공급자 우선순위 표(예: 인도 = WhatsApp → 국내 DLT SMS 사업자 → Twilio).
  - 실패하거나 5초 안에 전달 확인이 없으면 다음 공급자로.
- 부정 가입 방지
  - 번호·IP·기기별 시간당 발송 한도
  - 출시 국가만 허용
  - 국번 위험 목록(SMS 펌핑 방지)
  - CAPTCHA(Turnstile)

### 5-3. 신원·연령

- 단계형 인증
  - 레벨 0: 게스트
  - 레벨 1: 전화
  - 레벨 2: 셀피 연령 추정 또는 신분증
  - 레벨 3: 신분증 + 라이브니스
- 모두에게 비싼 KYC를 강제하지 않는다. 대상은 다음뿐이다.
  - 신고가 누적된 계정
  - 유료 판매자·방장 수익 기능
  - 법이 요구하는 국가
- 상세는 BUILD_VS_BUY §6.

### 5-4. 결제

- **자체 원장(추가만)**: 구매·환불·권한·구독 상태.
  - 지금 `verify-purchase`·`play-rtdn`이 이미 이 역할의 시작이다.
- 앱 안 디지털 상품: **Play Billing / App Store**(정책상 의무).
- 웹 결제: 현지 PG 어댑터(Razorpay·Stripe 등). 국가별 정책은 BUILD_VS_BUY §7.

### 5-5. 신뢰·안전

- 지금 있는 것: 신고·차단·금칙어·사람 검토·/admin.
- 추가할 것
  - 위험 점수: 신고 수, 차단당한 수, 빠른 이탈률, 기기·IP 공유 계정 수
  - 점수에 따른 단계형 인증 요구
  - 영상 도입 시 기기 안 노출 감지(이미 일부 구현, LAUNCH_CHECKLIST)

## 6. 관측·운영 (자체 운영의 실제 비용)

| 항목 | 내용 |
|---|---|
| 지표 | Prometheus + Grafana. 대상: DB, Realtime, LiveKit, 노드 CPU·대역폭 |
| 로그 | Loki 또는 SaaS |
| 경보 | DB 복제 지연, 디스크 80%, SFU CPU 70%, OTP 실패율, 매칭 대기 p95 |
| 상태 페이지 | 외부 업타임 체커 1개 |
| 배포 | 서버 설정은 코드로(Ansible/Terraform). 수작업 금지 |
| 보안 | OS 자동 패치, SSH 키만, 방화벽(SFU UDP 범위·443만), DDoS 방어 앞단(Cloudflare 등) |
| 당직 | 사람 1명이 24시간을 맡는 것은 지속 불가. **자체 운영 비중을 늘리는 시점 = 운영 담당자를 둘 수 있는 매출 시점**(COST_MODEL §5) |

## 7. 검증 계획 (10만이라고 말하기 전에)

부하시험은 스테이징에서만 한다(실서버 금지).
- 기간: 1k → 5k → 10k → 25k 단계
- 도구: k6로 WebSocket + RPC, LiveKit `lk load-test`로 SFU

| 측정 | 통과 기준 |
|---|---|
| 매칭 대기 p95 | 3초 이하(대기열에 짝이 있을 때) |
| 채팅 전달 p95 | 300 ms 이하 |
| DB CPU | 60% 이하 |
| Realtime 노드 CPU | 60% 이하 |
| SFU 노드당 구독 트랙 | 한도의 70% 이하 |
| 패킷 손실 5%에서 PESQ | 3.0 이상 |

10만 CCU 주장은 25k 단계 결과로 노드 수를 선형 외삽한 뒤, 그 외삽을 50k에서 한 번 더 확인해야 한다.

## 8. 대기업 벤치마킹 (공개 자료만)

표기
- [확인]: 회사 공식 블로그·소스·문서
- [2차]: 언론
- [미공개]: 공개 자료 없음. 추측하지 않음

| 회사 | 미디어 전송 | 모르는 사람 IP | 서버·확장 | 신뢰·안전 | 출처 |
|---|---|---|---|---|---|
| **Azar/Hyperconnect** | 1:1은 P2P 연결 그림을 공개 [확인]. TURN 사용·IP 보호 여부 [미공개]. 라이브·그룹은 자체 SFU, Origin–Edge 분리(호스트=Origin, 시청자=Edge): 연결 25% 빠름, RTT 약 30%↓, 손실 최대 80%↓, 그룹 50명 [확인] | [미공개] | AWS + Cloudflare. 매칭 이벤트는 Kafka·Flink·Redis. ScyllaDB(EKS) [확인] | 매칭 모델에 "신고 확률" 출력, p90 236→48 ms [확인]. 기기 안 모더레이션으로 전송 전 차단(0.004–0.006초, 보도자료는 Slide·Hakuna 명시) [확인] | hyperconnect.github.io 2022-12-30, 2024-09-25, 2024-11-19, 2025-06-11 |
| **OmeTV** | 개인정보처리방침: 직접 연결될 수 있고 **IP가 상대 기기에 제공됨** [확인] | 노출 | [미공개] | [미공개] | ome.tv/privacy |
| **Discord** | 모든 통화가 자체 SFU(C++), **P2P 없음**. 이유 중 하나가 IP 보호·DDoS [확인] | 비노출 | 2018: 음성서버 850+대, 13리전, 동시 음성 260만+, 송출 220 Gbps. 서버가 etcd에 부하 등록 → 리전 안 최소 부하 서버 배정, DDoS 받는 서버는 회전에서 제외 [확인]. 시그널링·presence는 Elixir(500만 동시) [확인] | 2024 DAVE 종단암호화 [확인] | discord.com/blog (음성 2.5M, Elixir 5M, 메시지 저장) |
| **Jitsi** | 2명이면 P2P, 3명째부터 브리지 [확인] | 2명 땐 노출 | 시그널링 샤드 + 리전별 자동확장 브리지 풀, 브리지 간 직접 연결, 대륙 간 RTT 29%↓ [확인] | — | jitsi.org/blog/bridge-cascading-is-back |
| **Signal** | 연락처끼리만 P2P, **모르는 사람은 중계**, "항상 중계" 설정 [확인] | 비노출 | 그룹통화 SFU는 Rust. 상태 없는 프런트엔드가 방→서버 매핑(DynamoDB), 유휴 CPU 많은 서버로 가중 랜덤 배정 [확인, 소스] | 프레임 종단암호화 | signal.org/blog, github.com/signalapp/Signal-Calling-Service |
| **LiveKit** | SFU + 내장 TURN(443) [확인] | 비노출 | 자체 호스팅: Redis 공유, 방 = 1노드, 종료 시 drain. Cloud: 세션을 여러 서버에 나누고 서버끼리 자체 프로토콜로 중계, 1초 안 서버 이동 [확인] | — | docs.livekit.io, livekit.com/blog/scaling-webrtc-with-distributed-mesh |

**우리가 실제로 적용할 것** (1–2명 팀이 할 수 있는 것만)

| 적용할 것 | 근거 | 우리 상태 |
|---|---|---|
| 모르는 사람 = 서버 중계, 친구 = P2P | Signal·Discord | 구현됨(070·P2P_PLAN) |
| 2명 P2P / 3명 이상 SFU, 실패 시 서버로 고정 | Jitsi | 구현됨 |
| 서버가 부하를 보고하고 최소 부하·유휴 CPU 서버 배정 | Discord·Signal·LiveKit | 2단계 토큰 서버 |
| 공격받거나 죽은 서버를 회전에서 빼기, 종료 시 drain | Discord·LiveKit | 2단계 |
| 별도 TURN 대신 SFU 내장 TURN 443 | LiveKit | 2단계 |
| 방→서버 매핑을 작은 표(Valkey)에 | Signal·LiveKit | 2·4단계 |
| 기기 안 모더레이션(전송 전 차단) | Hyperconnect | 노출 감지 일부 구현(LAUNCH_CHECKLIST) |
| 정책별 임계값을 설정으로 | Hyperconnect WSDM 2023 | 위험 점수(5-5) |
| 매칭에 신고 확률 반영 | Azar | 4단계 이후 |

**적용하지 않을 것**
- Kafka·Flink·ScyllaDB(Hyperconnect·Discord): 우리 규모에서 Postgres로 충분하다.
- 자체 C++/Rust SFU: 하지 않는다.
- Cloud식 리전 간 메시 중계: LiveKit OSS에 없다. 방장 리전 고정으로 대신한다.
