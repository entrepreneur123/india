# 자체 구축 vs 오픈소스 자체 운영 vs 외부 사업자 (2026-11-02)

## 범위와 표기

**구분**
- **직접 개발**: 우리가 코드를 쓴다.
- **OSS 자체 운영**: 검증된 오픈소스를 우리 서버에서 돌린다. 사장님 정의상 이것도 "자체 구축"이다.
- **외부 필수**: 법·통신망·정산망 때문에 사업자를 반드시 거친다. 대신 우리 쪽 어댑터 뒤에 두어 사업자를 교체할 수 있게 한다.

**표기**
- [문서]: 공식 문서 확인
- [코드]: 우리 저장소 또는 패키지 소스 확인
- [2차]: 법률사무소·언론 등 2차 출처
- [추정]: 계산·추론
- [미확인]

출처 URL은 각 절 끝에 있다. 조사 원문(인용·URL·미확인 목록)은 `research_2026-11-02/`. 가격은 `COST_MODEL_100K.md`.

> 법률 항목은 기술 판단이 아니다. "LAWYER" 표시는 출시 전 현지 변호사 확인이 필요하다는 뜻이다.

## 한눈에 보는 판단표

| 영역 | 판단 | 지금 | 목표 | 전환 조건 |
|---|---|---|---|---|
| 웹 호스팅·CDN | **OSS/저가 정적 호스팅** | Vercel Hobby(비상업 약관) | Cloudflare Pages 또는 오브젝트 스토리지 + CDN | 즉시 가능(정적 빌드). 상업 출시 전 |
| DB | **OSS 자체 운영** | Supabase Cloud Free | Postgres 17 + Patroni + pgBackRest | Supabase 월 청구가 자체 운영비 + 운영시간 가치를 넘을 때(COST_MODEL §4) |
| API·인증·Realtime | **OSS 자체 운영** | Supabase Cloud | Supabase 스택 자체 호스팅(PostgREST·GoTrue·Realtime) | DB와 함께 |
| 매칭·presence·호출 제한 | **직접 개발** | SQL 함수 + 폴링 | yaari-edge(Go) + Valkey | DB 호출이 병목일 때. 먼저 폴링을 푸시로(코드만) |
| SFU(방·relay 통화) | **OSS 자체 운영** | LiveKit Cloud | LiveKit OSS | relay 통화부터. 방은 Krisp 대체 검증 뒤 |
| TURN | **OSS 자체 운영** | LiveKit Cloud 내장 | LiveKit 내장 TURN(TLS 443) | SFU와 함께 |
| 잡음·에코 처리 | **외부(라이선스) 또는 OSS** | Krisp via LiveKit Cloud | Krisp SDK 직접 라이선스 또는 OSS 조합 | 실기기 A/B 통과 |
| OTP 생성·검증·세션 | **OSS 자체 운영** | Supabase Auth | GoTrue 자체 운영 | DB와 함께 |
| OTP 발송 | **외부 필수 + 우리 어댑터** | Twilio(미가입) | 국가별 다중 공급자 | 출시 전 |
| 신분증·라이브니스 | **외부 + 어댑터** | Didit(키 없음) | 다중 공급자, 결과만 보관 | 고위험 계정부터 |
| 결제 수단 | **외부 필수 + 어댑터** | Play Billing | Play·App Store·현지 PG | 웹 결제 시 |
| 결제 원장·권한·환불 상태 | **직접 개발** | `verify-purchase`·`play-rtdn` | 통합 원장 | 결제 2번째 수단 추가 시 |
| 신고·위험 탐지 | **직접 개발** | 신고·차단·사람 검토 | 위험 점수 + 단계형 인증 | 신고량 증가 시 |
| 모니터링·로그 | **OSS 자체 운영** | Sentry Free·PostHog | Prometheus/Grafana/Loki + Sentry 유지 | 서버 자체 운영 시작 시 |
| DDoS 방어 | **외부 필수** | Vercel·Supabase·LiveKit 기본 | Cloudflare 앞단(웹·API), SFU는 UDP라 공급자 망 방어 | 자체 서버 노출 시 |

## 1. 웹 호스팅·CDN

- **지금 웹은 순수 정적 파일이다** [코드].
  - `next.config` `output: 'export'`
  - Vercel 기능은 `vercel.json`의 헤더·`cleanUrls`·rewrite 1개(`/r/:code → /`)만 쓴다.
- 옮기는 방법 (같은 정적 파일 그대로)

| 대안 | 옮길 것 | 장단점 |
|---|---|---|
| Cloudflare Pages / Workers 정적 자산 | 헤더 → `_headers` 파일(저장소 루트 `docs/_headers`에 이미 같은 형식 사용 중), rewrite → `_redirects` `/r/* / 200` | 정적 요청 무료. 상업 이용 약관·한도는 COST_MODEL에서 확인 |
| 오브젝트 스토리지(R2/S3) + CDN | 헤더는 CDN 규칙 | 가장 싸고 종속이 적다. 설정이 수작업 |
| 자체 nginx | 전부 직접 | DDoS·전 세계 지연은 CDN 없이는 불리 |

- **종속을 줄이는 설계**: 배포 산출물(`out/`)과 헤더·리다이렉트 규칙을 저장소 한 곳에 두고, 호스트별 설정 파일은 빌드 때 생성한다. 그러면 호스트를 바꿀 때 DNS만 바꾼다.
- **CSP 주의**: `connect-src`에 `*.livekit.cloud`·Supabase 도메인이 박혀 있다(`vercel.json`). 자체 서버로 갈 때 우리 도메인을 먼저 추가한다.

## 2. DB·인증·API

| 대안 | 기존 마이그레이션·RPC 재사용 | 인증 | 운영 부담 | 판단 |
|---|---|---|---|---|
| Supabase Cloud(지금) | 100% | GoTrue | 낮음 | 출시~초기 |
| **Supabase 자체 호스팅** | **거의 100%** (같은 Postgres·PostgREST·GoTrue·Realtime) | GoTrue 그대로 | 높음 [문서]: 백업·PITR·지표·HA·보안 패치가 우리 몫. 공식 배포는 Docker Compose 단일 프로젝트, Kubernetes는 커뮤니티 | **목표** |
| 순수 Postgres + 자체 인증 서버 | SQL 함수는 재사용. `auth.uid()`·`auth.users`·RLS의 JWT 클레임은 같은 형식 JWT를 내는 인증 서버가 필요. 클라이언트 supabase-js 교체 | Ory Kratos·Keycloak 등 | 매우 높음(클라이언트 전면 수정) | 하지 않음 |
| Postgres + Valkey | 위 둘 중 하나에 **덧붙이는** 것 | — | 중간 | hot state용(3장) |

질문별 답

- **Supabase를 직접 호스팅하면 얼마나 줄어드나**
  - 1천 CCU에서는 거의 줄지 않는다. 사람 시간을 넣으면 오히려 비싸다.
  - 차이는 컴퓨트를 크게 쓸 때 생긴다(4XL 이상). 수치는 COST_MODEL §3.
- **마이그레이션·RPC를 그대로 쓰나**: 그렇다. `server/migrations/*.sql`이 표준 Postgres + Supabase 확장(pg_cron·pg_net·realtime 스키마)이고, 자체 호스팅 묶음에 같은 확장이 들어 있다 [문서].
  - 확인할 것 [미확인]
    - `supabase_realtime` 발행과 `realtime.send` 함수 버전
    - pg_net 웹훅 대상 URL(엣지 함수 주소가 바뀜)
    - Storage 버킷 정책
  - **스테이징 복원으로 `test_*.sql` 24개를 돌려 확인한다**(MIGRATION_ROADMAP 3단계).
- **Supabase Auth 대체**: GoTrue를 그대로 자체 운영한다(오픈소스).
  - 문자 발송만 **Send SMS Hook**으로 우리 어댑터에 넘긴다.
  - [문서] "The Send SMS Hook replaces Supabase's built-in SMS sending" — 훅은 사용자 전화번호와 OTP를 받고, HTTP 엔드포인트나 Postgres 함수로 만들 수 있다.
  - 다른 인증 서버로 바꾸는 것은 이득이 없다.
- **Postgres 수평 확장**: 순서가 있다(TARGET_ARCHITECTURE §2-4).
  1. 세로 확장
  2. 읽기 복제
  3. 폴링 제거
  4. 파티션
  5. Citus(AGPL) 샤딩. 마지막 수단
- **다지역 DB 시점**: 북미·동남아의 쓰기 지연이 체감 문제로 측정될 때, 또는 국가별 저장 의무가 생길 때(6장 베트남·사우디 국외이전).
- **백업·장애 복구**: TARGET_ARCHITECTURE §2-5.
  - pgBackRest(MIT) 또는 WAL-G(Apache-2.0) + Patroni(MIT)
  - 매달 복원 훈련

## 3. 실시간·매칭

| 기능 | 판단 | 방법 |
|---|---|---|
| WebSocket 게이트웨이 | OSS 자체 운영 | Supabase Realtime(Elixir, **Apache-2.0** [문서]) |
| Presence | OSS 자체 운영 + 직접 개발 | Realtime Presence. 방 접속 판단은 yaari-edge |
| 방 관리 | 직접 개발(이미 있음) | SQL 함수 + LiveKit 집행(`voice-sync`) |
| 알림 | 직접 개발(이미 있음) | `notifications` 트리거 → `user:` 방송, FCM 푸시 |
| 채팅 | 직접 개발(이미 있음) | RPC 저장 → 트리거 `realtime.send` |
| 매칭 엔진 | **직접 개발** | Valkey 대기열 + Go. 규칙은 `find_match4` 그대로 |
| 세션 관리 | OSS | GoTrue JWT + 리프레시 |
| 호출 제한 | 직접 개발 | Valkey 토큰 버킷(지금 `rate_ok`의 확장) |
| 메시지 큐 | 필요할 때만 | 지금은 pg_cron + 작업 테이블로 충분. 외부 호출(푸시·KYC)이 늘면 Valkey Streams |

Realtime 공식 벤치마크 [문서, 클러스터 2–6노드, 인스턴스 사양 비공개]

| 시나리오 | 동시 사용자 | 처리량 | 지연 |
|---|---|---|---|
| WebSocket Broadcast | 32,000명 | 224,000 msg/s | 중앙값 6 ms |
| DB 발 Broadcast(우리 방식) | 80,000명 | 10,000 msg/s | 중앙값 46 ms |
| 대형 팬아웃 | 250,000명 | 800,000 msg/s 이상 | — |
| Postgres Changes + RLS(Android가 아직 쓰는 방식) | 500 클라이언트 | 3,000–4,000 msg/s에서 한계 | — |

→ **Android도 Broadcast로 옮겨야 한다.**

## 4. 음성·미디어 서버

| 후보 | 라이선스·언어 | 형태 | 확장 | RED·FEC·TURN | 운영 난도 | 판단 |
|---|---|---|---|---|---|---|
| **LiveKit OSS** | Apache-2.0, Go(Pion) | 완성형 서버 + 우리가 이미 쓰는 SDK | Redis로 다중 노드, **방 하나는 한 노드**, 리전 인식 노드 선택 | RED 권장(기본), 내장 TURN(UDP 3478·TLS 443) | 중간 | **채택** |
| Jitsi Videobridge | Apache-2.0, Java/Kotlin | Jitsi 스택 전체(Jicofo·Prosody) | 브리지 풀 + 리전 간 relay | RED 설정 가능, coturn 별도 | 중상 | 클라이언트 전면 교체라 불채택 |
| mediasoup | ISC, C++ + Node/Rust | 라이브러리 | 워커 1개 = 코어 1개, 약 500 consumer [문서], 기기 간 연결은 직접 | RED 미지원 추정, TURN·시그널링 직접 | 높음 | 불채택 |
| Janus | **GPLv3**, C | 플러그인 서버 | 클러스터 없음(수동 연결) | opus FEC, AudioBridge에 RNNoise | 높음 | 불채택 |
| Pion | MIT, Go | 라이브러리 | — | — | 매우 높음 | 자체 SFU를 만들 때의 재료 |
| coturn | BSD-3 | TURN만 | DNS SRV | — | 낮음 | LiveKit 내장 TURN으로 충분하면 불필요. P2P 친구 통화의 relay를 SFU 대신 TURN으로 하고 싶을 때만 |
| 자체 SFU 개발 | — | — | — | 전부 직접 | 매우 높음 | **하지 않음**(혼잡 제어·심캐스트·RED 검증 인력 없음) |

LiveKit OSS 핵심 사실 [문서]
- **공식 비교표** (자체 호스팅 vs Cloud)

| 항목 | 자체 호스팅 | Cloud |
|---|---|---|
| 구조 | "Single-home SFU" | "Global mesh SFU" |
| 방 최대 인원 | "Up to ~3,000" | 제한 없음 |
| 잡음 제거 | 브라우저 WebRTC 기본 + ai-coustics(자체 라이선스, 에이전트용) | Krisp 등 고급 모델 포함 |
| 분석 대시보드 | 없음 | 있음 |
| 토큰 폐기 | 없음 | 있음 |
| 99.99% 목표 | 없음 | 있음 |

- **벤치마크**: 16코어 c2-standard-16에서 오디오 방(발화자 10, 구독자 3,000), CPU 80%.
  - 주의: 시험은 3 kbps 빈 패킷이고, 명령어에는 구독자 1,000으로 적혀 있다.
  - → **상한값으로만 쓴다.** 우리 용량 계획은 노드당 구독 오디오 트랙 8,000개(16코어, CPU 70%)로 보수적으로 잡았다 [추정, COST_MODEL §2].
- **배포 요건**
  - 도메인 + CA 인증서(자체 서명 불가)
  - TURN용 별도 도메인·인증서
  - UDP 50000–60000
  - Docker 호스트 네트워킹, 10 Gbps 권장
  - Helm은 노드당 파드 1개

통화별 결론
1. **랜덤 1:1 음성** (070으로 지금은 없음. 도입 시)
   - 무조건 SFU 경유다. P2P와 TURN-only P2P는 쓰지 않는다.
   - 이유: IP 비노출, 강퇴·신고 즉시 차단, 품질 지표.
   - 데이터량은 TURN 중계와 같다.
2. **친구 1:1**: P2P 우선(구현됨) → relay는 우리 SFU.
3. **보이스룸**: SFU. Mesh 금지.
4. **TURN**: LiveKit 내장(TLS 443).
5. **리전 배치**
   - LiveKit OSS는 방을 리전 사이에 걸칠 수 없다.
   - 방은 방장 리전에 만들고, 토큰 서버가 리전을 고른다.
6. **장애 전환**: 노드 drain·재연결, LiveKit Cloud를 최종 대체 경로로 둔다.

## 5. Krisp와 오디오 처리

### 5-1. Krisp 사실관계

| 질문 | 답 | 근거 |
|---|---|---|
| LiveKit Cloud에만 종속되나 | **LiveKit 통합판은 그렇다** | [문서] "LiveKit Cloud includes access to advanced noise cancellation models (Krisp and ai-coustics)". 자체 호스팅 칸은 "Client-side WebRTC cancellation" |
| 자체 호스팅 LiveKit에서 쓸 수 있나 | **기술적으로 안 되고, 약관상으로도 안 된다** | 아래 두 줄 |
| └ 기술 | 켤 때 방 서버의 `/settings`를 방 토큰으로 호출해 허가 플래그를 확인한다. 10초마다 사용량을 보고하고, 6회 연속 실패하면 꺼진다. OSS 서버는 모르는 경로에 404를 준다 | [코드] npm 0.4.5 소스, [코드] livekit `server.go`. **실행 시험은 안 함** |
| └ 약관 | 패키지 라이선스는 "LiveKit Cloud Terms of Service"다. 약관 3.1은 "when integrated with the Services"(=cloud.livekit.io)로 허가하고, 3.2는 우회를 금지한다 | [문서] |
| 라이선스 검증 서버 | 모델은 `integrations.livekit.io/enc/v2`에서 받는다. 허가·사용량 보고는 **방 서버**(LiveKit Cloud)가 받는다 | [코드] |
| 클라이언트·서버 역할 | 음성 처리는 **전부 클라이언트(브라우저 AudioWorklet)**에서 한다. 서버는 허가와 과금만 한다 | [코드] |
| LiveKit 밖 WebRTC에서 | **Krisp 자체 SDK를 직접 라이선스하면 가능**하다. Jitsi Meet은 자체 호스팅에서 Krisp JS SDK를 공식 옵션으로 지원한다 | [문서] sdk-docs.krisp.ai "commercial license from Krisp Technologies", Jitsi config.js |
| Krisp SDK 가격 | 공개 안 됨(상담) | [문서] |
| 플랫폼 | LiveKit 통합판: 웹 = 잡음 + 배경 목소리 제거(BVC), Android = 잡음만. Krisp SDK: Web·iOS·Android·데스크톱. BVC는 다른 사람 목소리 제거, 8 kHz 이상 | [문서] |

### 5-2. 문제를 나눠서 본다

에코·잡음·키보드·무음·끊김은 서로 다른 기술이 푼다.

| 증상 | 원인 | 맞는 기술 | 오픈소스 |
|---|---|---|---|
| 내 목소리가 상대에게 되돌아 들림(에코) | 상대 스피커 → 상대 마이크 | **AEC**(에코 제거) | 브라우저·Android WebRTC 내장 AEC3. 서버 처리 불가 |
| 에어컨·차 소리 | 정상 잡음 | **NS**(잡음 억제) | 브라우저 NS, RNNoise(BSD-3), DeepFilterNet3(MIT/Apache, PESQ 3.17 vs RNNoise 2.33, 논문 수치) |
| 키보드·음악 | 비정상 잡음 | NS(딥러닝) 또는 **VAD 게이트** | 이미 구현: Silero VAD 게이트(키보드 열림 98–100% → 0–1.7%, HANDOFF) |
| TV·옆 사람 목소리 | 사람 목소리라 VAD가 통과시킴 | **BVC**(배경 목소리 제거) | **오픈소스 없음**(연구 코드만) [문서 조사, 미확인] |
| 무음 구간 | 마이크 꺼짐·권한·기기 반이중 | 진단(`/audio-check`), 앱 안내 | — |
| 음성 끊김 | 패킷 손실 | RED + FEC(구현됨), 리전 SFU | — |

**우리 기기에서 난 에코의 실제 원인**: 2026-11-02 A/B(Android 10 Chrome)에서 Krisp를 켜면 깔끔했다.
- Krisp가 에코를 없앤 것은 에코가 "다른 사람 목소리"라서 BVC가 지운 것으로 보인다 [추정].
- 같은 폰은 통화 모드 AEC가 반이중으로 동작한다(HANDOFF 11-02).
- → **오픈소스로 대체하기 가장 어려운 칸이 정확히 여기다.**

### 5-3. Krisp 대체의 경제성

| 안 | 품질 | 비용 | 판단 |
|---|---|---|---|
| A. 방은 LiveKit Cloud 유지(Krisp 포함) | 지금과 같음 | Cloud 분당·GB 요금 | 대체안 검증 전까지 기본 |
| B. Krisp SDK 직접 라이선스 + 자체 LiveKit | 같은 Krisp(BVC 포함) | 연 라이선스(비공개 가격) | **견적을 받아 C와 비교**. 방 트래픽이 커지면 가장 유력 |
| C. 오픈소스 조합: 브라우저 AEC/NS + DeepFilterNet 또는 RNNoise + Silero 게이트 | 잡음·키보드는 근접 예상. **에코·옆 사람 목소리는 Krisp보다 약할 가능성 큼** | 0원(CPU만) | 실기기 A/B로 판정. 실패하면 B |
| D. 서버 쪽 잡음 제거 | 복호화·디코딩·재인코딩 필요, 지연·CPU 큼, 종단 암호화와 충돌 | 큼 | 하지 않음 |

### 5-4. 출처
- docs.livekit.io/intro/cloud.md
- docs.livekit.io/transport/media/noise-cancellation.md
- docs.livekit.io/transport/self-hosting/benchmark.md
- docs.livekit.io/transport/self-hosting/ports-firewall.md
- docs.livekit.io/transport/self-hosting/distributed.md
- livekit.com/legal/terms-of-service
- sdk-docs.krisp.ai/docs/licensing-information.md
- github.com/jitsi/jitsi-meet `config.js`
- mediasoup.org/documentation/v3/scalability/
- github.com/Rikorose/DeepFilterNet
- github.com/xiph/rnnoise

## 6. 인증·신원 (자체 vs 계약)

### 6-1. OTP (전화번호 소유 확인)

| 부분 | 판단 |
|---|---|
| 코드 생성·만료·재발급·시도 제한·세션 | **자체**(GoTrue) |
| 발송 한도·부정 탐지(번호·IP·기기·국번 위험) | **직접 개발**(yaari-edge 또는 SQL `rate_ok`) |
| 국가별 번호 검증 | **직접 개발**(libphonenumber, Apache-2.0) |
| 실제 발송 | **외부 필수**: 통신사망에 연결된 사업자 |
| 공급자 장애 대체 | **직접 개발**: 국가별 우선순위 + 실패 시 다음 공급자 |

국가별 발송 요건 [문서, Twilio 국가 가이드·Meta 문서]

| 국가 | 요건 |
|---|---|
| 인도 | 국내 경로 OTP는 **DLT 등록**(사업자·헤더·템플릿)이 필요하고, 사실상 인도 PAN/GST가 필요하다. 해외 법인의 직접 등록 가능 여부는 미확인(LAWYER). DLT 없이 **국제 경로**를 쓰면 발신자명이 무작위 숫자로 바뀐다 |
| WhatsApp | AUTHENTICATION 템플릿으로 OTP 허용. 본문은 고정이고, 복사·원탭·제로탭 버튼을 쓴다 |
| UAE·사우디·인도네시아·베트남·필리핀 | 발신자 ID 사전등록 필수, 2–5주 |
| 미국 | Twilio Verify 공용 발신번호는 A2P 10DLC 등록 면제(OTP 전용) |
| SMS 펌핑 방지 | Fraud Guard·국가 허용 목록 |

→ **어댑터 설계**: `OtpSender` 구현체를 국가 표로 고른다.
- 인도: WhatsApp → 국제 경로 SMS → (법인 생기면) DLT 국내 SMS
- 그 밖: Verify류

GoTrue Send SMS Hook 하나로 붙이므로 클라이언트 변경이 없다.

### 6-2. PASS와 국가별 본인확인

PASS는 한국 통신 3사의 본인확인 서비스다. 한국 명의 휴대폰이 있는 사람만 쓸 수 있고, 본인확인기관(NICE 등)과 계약해야 한다 [2차]. **글로벌 대체재가 아니며 한국 출시 전에는 불필요하다.**

| 국가 | 전화 소유 | 연령·신원 | 우리가 할 수 있는 것 | 계약·법인 필요 |
|---|---|---|---|---|
| 한국 | PASS(통신사) | PASS·신분증 | — | 본인확인기관 계약, 한국 사업자 |
| 인도 | OTP(DLT) | Aadhaar 온라인 인증은 정부 승인 목적에 한정. 오프라인 검증(OVSE)은 등록 필요. DigiLocker | 셀피 연령 추정·라이브니스·여권/면허/PAN 문서 | Aadhaar·DigiLocker는 인도 법인 사실상 필요(LAWYER) |
| 미국 | OTP(Verify) | 주별 앱스토어 연령법: 텍사스 시행 중, 유타 2027-05-06, 루이지애나 2027-07-01 → 스토어 연령 신호(Play Age Signals, Apple Declared Age Range) 사용 의무 | 스토어 신호 연동, 문서 KYC | — |
| 캐나다 | OTP | 연방 연령법 없음(C-34 법안 단계) | — | — |
| UAE | OTP(발신자 등록) | UAE PASS | 문서 KYC | UAE PASS는 **UAE 무역면허** 필요 |
| 사우디 | OTP(발신자 등록) | Nafath | 문서 KYC | 민간 가입 요건 미확인(LAWYER) |
| 인도네시아 | OTP(발신자 등록) | PP Tunas: 16세 미만 제한, 고위험 플랫폼 연령 확인 | 연령 추정 | PSE 등록 |
| 베트남 | **베트남 번호로 계정 인증 의무**(Decree 147) | — | 번호 국가 검사 | 발신자·템플릿 등록 |

### 6-3. 단계형 KYC (모두에게 강제하지 않는다)

| 레벨 | 누구 | 방법 | 비용 |
|---|---|---|---|
| 0 | 게스트 | 없음(기기·IP 제한) | 0 |
| 1 | 기본 | 전화 OTP | 건당 OTP |
| 2 | 랜덤 영상·음성(도입 시), 신고 1회 이상 | 셀피 + 라이브니스 + 연령 추정 | 낮음 |
| 3 | 유료 수익 기능·방장 정산·신고 다수·법이 요구하는 국가 | 신분증 + 라이브니스 + 얼굴 대조 | 건당 KYC |

- **보관 원칙**(지금 035 방식 유지): 결과(통과/실패), 생년, 성별, 발급국, 문서번호의 비밀 해시만 남긴다. 사진·얼굴 템플릿·원본은 보관하지 않는다.
- **공급자 교체**: `IdVerifier` 인터페이스 + 공급자 ID·세션 ID만 저장. 결과 스키마는 우리 것이다.
- **모두 강제하지 않는 이유**: 가입 전환율 하락, 건당 비용, 생체정보 규제(미국 BIPA 등, LAWYER).

## 7. 결제

| 부분 | 판단 | 근거 |
|---|---|---|
| Android 앱 안 디지털 상품 | **Play Billing 필수**. 예외: 미국은 2025-10-29부터 외부 결제·링크 허용(신고·수수료 의무), 인도는 사용자 선택 결제(수수료 4%p 인하) | [문서] Play 결제 정책·13821247·15582165 |
| iOS 앱 안 | **IAP 필수**(3.1.1). 미국 스토어는 외부 결제 링크 허용(현재 수수료 0, 법원 결정 대기). 웹에서 산 것을 앱에서 쓰려면 **같은 상품을 IAP로도 팔아야 함**(3.1.3(b)) | [문서] |
| 웹 결제 | 현지 PG(Razorpay·Stripe 등) 필수. 인도는 RBI 인가 PA/PA-CB를 거치면 우리 별도 라이선스 불필요. 정기결제 ₹15,000까지 추가인증 면제 | [2차] |
| 세금 | 인도 OIDAR GST 18%(문턱 없음), UAE VAT 5%, 사우디 15%(비거주 B2C 첫 판매부터). 웹 직판 시 우리가 신고 | [2차], 스토어 판매분의 대리납부 여부 미확인(LAWYER) |
| **우리가 직접 만드는 것** | 상품 카탈로그, 구매 원장(추가만), 구독 상태, 권한(entitlement), 영수증 검증, 환불·취소 상태, 구매 이력, 부정 결제 탐지(같은 영수증 재사용·환불 남용) | 지금 `verify-purchase`(재사용 차단)·`play-rtdn`(환불 즉시 회수)이 이 모양 |
| 하지 않는 것 | 스토어 결제 우회 유도, 앱 안에서 웹 결제로 안내(미국 외), 자체 카드 결제 처리(PCI·인가) | 정책·법 |

`PaymentRail` 어댑터: Play, App Store, Razorpay, Stripe 구현체. 원장은 "어느 레일의 어느 주문이 어떤 권한을 언제부터 언제까지 줬나" 한 가지 모양이다.

## 8. 플랫폼·국가 정책이 기술 계획을 바꾸는 곳 (사장님 결정 필요)

1. **Apple 1.2** [문서, 원문 확인]: "Apps … used primarily for … Chatroulette-style experiences, random or anonymous chat … do not belong on the App Store and may be removed without notice."
   - 랜덤 채팅이 핵심인 iOS 앱은 심사 통과가 불확실하다.
   - **iOS는 친구·보이스룸 중심 앱으로 기획하거나, 웹(PWA)으로만 제공하는 것**을 검토한다.
2. **Google Play**: 모르는 사람 무작위 연결·익명 채팅 앱은 Play Console 도구로 **미성년자를 차단해야** 하고, 아동안전 기준 페이지·담당자가 필요하다(일부 준비됨, LAUNCH_GUIDE).
3. **UAE 음성통화**: VoIP는 TDRA 허가 사업이다. 허가 목록(Teams·Zoom·Meet·BOTIM) 밖의 앱 통화는 차단을 예상해야 한다 [문서, 검색 발췌].
   - → UAE에서는 음성·영상 기능을 끄고 글만 제공하거나, 허가 사업자와 제휴한다.
   - 사우디는 2017년 부분 허용이고 조건부다. 카타르·이집트·오만은 불안정하다.
4. **인도 SIM 바인딩**(DoT, 2026-12-31 기한): 메신저 대상이다. 우리 같은 소셜 앱이 대상인지 미확인(LAWYER).
5. **베트남**: 계정을 베트남 번호로 인증해야 한다. **인도네시아**: PSE 등록 + 16세 미만 제한.

## 9. 국가별 법·정책 요약과 출시 순서

이 절은 법률 자문이 아니다. [2차] 표시는 법률사무소·언론 요약이다. 출시 국가마다 현지 변호사 확인이 필요하다(LAWYER).

### 9-1. 국가별 표

| 국가 | 개인정보 | 플랫폼 의무 | 음성통화 | 연령 | 현지 법인·대리인 |
|---|---|---|---|---|---|
| **인도** | DPDP: 동의관리자 규정 2026-11-13, 핵심 의무(고지·동의·유출 72시간 보고·아동) **2027-05-13** [문서 PIB] | IT Rules(2026-02-20 개정): 정부·법원 명령 **3시간**, 사적 이미지 **2시간** 삭제, 고충 24시간 접수. 처리 기한 7일 vs 15일은 출처가 엇갈림 [2차] | 허가 불필요(OTT는 통신법 제외) [2차]. **SIM 바인딩 지침**(메신저, 2026-12-31) 적용 여부 미확인 | 18세 미만 = 아동 → 18+ 차단 유지 | 사용자 500만 명 초과 시 **인도 거주** 책임자 3명. DLT·Aadhaar는 사실상 인도 법인 |
| **UAE** | PDPL 시행령 미발표 [2차] | — | **VoIP = TDRA 허가 사업**. 허가 목록 밖 앱 통화 차단 예상 [문서, 검색 발췌] | — | UAE PASS·통화 허가에 현지 면허 |
| **사우디** | PDPL 시행 중. 국외이전 규정(표준계약). 2026-10부터 해외 사업자도 등록 가능(대리인) [2차] | — | 2017 부분 허용, 조건부 [2차] | Nafath(민간 요건 미확인) | 등록 대리인 |
| **카타르·이집트·오만** | 이집트 PDPL: 허가·DPO, 유예 약 2026-11 종료 [2차] | — | 차단·속도 제한이 불안정 [2차] | — | 이집트 허가 |
| **미국** | CCPA 문턱(매출 $26.6M 등), 주별 법 | **TAKE IT DOWN Act**(2026-05-19 집행): 사적 이미지 48시간 삭제 [문서 FTC] | 제한 없음 | COPPA(13세), **앱스토어 연령법**: 텍사스 시행 중·유타 2027-05-06·루이지애나 2027-07-01 → 스토어 연령 신호 사용. 주별 SNS 연령법 소송 중(LAWYER) | 없음 |
| **캐나다** | PIPEDA(C-36 법안 단계) | — | 제한 없음 | C-34(16세 미만 SNS 금지) 법안 단계 | 없음 |
| **인도네시아** | PDP법 + 시행령 2027-01-16 | **PSE 등록**(해외 포함, 미등록 차단) | — | PP Tunas(2026-03-28): 16세 미만 제한, 고위험 플랫폼 연령 확인 | 현지 연락인 사실상 필요 |
| **베트남** | 새 개인정보법 + Decree 356(2026-01-01): **국외이전 영향평가 제출** | Decree 147: 월 10만 방문×6개월 시 **베트남 번호 계정 인증**·삭제 의무 | — | — | 현지 연락처 |
| **필리핀** | DPA. 민감정보 1,000명 이상이면 등록 | — | — | — | — |
| **태국** | PDPA: **현지 대리인** | 디지털 플랫폼 신고 + **현지 코디네이터** | — | — | 대리인·코디네이터(법인 불필요) |
| **말레이시아** | PDPA 개정 | 사용자 800만 이상 면허, 온라인안전법(2026-01-01) | — | — | 미확인 |

공통(앱스토어) [문서]
- **Google Play**
  - 무작위 연결·익명 채팅은 미성년자를 차단해야 한다.
  - 아동안전 기준 페이지, 신고, CSAM 대응, 담당자가 필요하다.
  - 2027-01-27부터 데이트·소셜 앱은 최소 위치 권한만 쓴다.
- **Apple**: 1.2로 랜덤·익명 채팅은 거절·삭제 대상이다. 앱 안 계정 삭제(5.1.1(v))가 필요하다.

### 9-2. LAUNCH_GUIDE 계획과 사장님 목표 시장의 차이

| | LAUNCH_GUIDE(10-19) | 지금 목표 |
|---|---|---|
| 시장 | 인도·방글라데시·브라질·베트남 | 인도·중동·북미·동남아 |
| 빠진 법 검토 | — | 중동 전부(통화 허가·개인정보 등록), 미국(TAKE IT DOWN·앱스토어 연령법·주법), 캐나다, 인도네시아·태국·필리핀·말레이시아 |
| 더 이상 필요 없을 수 있는 것 | 방글라데시·브라질(Marco Civil 6개월 접속기록, ECA Digital) | — |
| 공통 | 인도(가장 준비됨), 베트남(Decree 147) | 같음 |
| 문서상 차이 | LAUNCH_GUIDE §5 인도 IT Rules "7일 내 처리" | 출처가 엇갈림(7 vs 15일) → 재확인 필요 |

### 9-3. 출시 국가 순서 제안 (좁게 시작)

| 순서 | 국가 | 이유 | 출시 전 필수 |
|---|---|---|---|
| 1 | **인도** (Android + 웹) | 코드·번역·문서가 가장 준비됨. 서버(뭄바이)·미디어 지연 최적 | WhatsApp OTP, 고충처리관 실명, CSAE 담당자, DPDP 2027-05 준비 |
| 2 | **미국·캐나다** (Android + 웹) | 통화 규제 없음, 결제 선택지 넓음 | 사적 이미지 48시간 삭제 절차, 스토어 연령 신호(텍사스), 미국 리전 SFU는 사용자 10% 이후 |
| 3 | **필리핀·말레이시아·태국** | 영어권, 규제가 상대적으로 단순 | 발신자 등록, 태국 대리인·코디네이터 |
| 4 | **사우디·카타르** (음성 기능은 실측 후) | 수요 크지만 통화 규제 불안정 | 사우디 등록 대리인, VAT, 현지 망 통화 실측 |
| 5 | **인도네시아·베트남** | PSE 등록·번호 인증·국외이전 신고 등 절차가 무거움 | PSE, Decree 147 번호 인증, CTIA |
| 보류 | **UAE** (음성) | TDRA 허가 없이 통화 불가 | 허가 사업자 제휴 또는 글 전용 출시 |
| 보류 | **iOS 전체** | Apple 1.2 | 별도 앱 기획 |
