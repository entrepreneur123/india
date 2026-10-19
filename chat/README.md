# Yaari (가칭) — 글로벌 랜덤채팅 앱 (첫 시장: 인도)

로또 앱(`/android`)과 **완전히 분리된 독립 프로젝트**입니다. 이 폴더만 떼어 새 저장소로 옮겨도 그대로 빌드됩니다.

## 설치 파일 (`dist/`)

| 파일 | 내용 |
|---|---|
| `yaari-demo.apk` (1.4MB) | 서버 없이 폰 안에서 도는 체험판. 가상 상대 12명. OTP는 `123456`. 결제는 즉시 승인(데모) |
| `yaari-live-arm64.apk` (17MB) | **실제 서버**(Supabase 뭄바이) 연결판. 아래 "출시 전 설정"을 마쳐야 로그인할 수 있습니다 |

두 파일 모두 디버그 키로 서명한 테스트용입니다. Play Store에는 `./gradlew bundleLiveRelease`로 만든 App Bundle(.aab)을 올립니다. 업로드 키는 `chat/upload.properties`(저장소에 올리지 않음)에 `storeFile`, `storePassword`, `keyAlias`, `keyPassword`를 적어 두면 자동으로 쓰입니다. Play가 폰 CPU에 맞는 파일만 내려주므로 실제 다운로드 크기는 더 작습니다.

## 화면

하단 탭 4개: **공개방 · 랜챗 · 최근(친구/매칭) · 나**. 오른쪽 위 톱니바퀴 = 설정. 흰 바탕 + 포인트 색 하나, 글꼴 Plus Jakarta Sans(OFL). 랜챗은 오로라 배경과 국기가 도는 아자르식 화면. 버튼 눌림·진동·화면 전환·매칭 연출·메시지 팝인·입력 중 점·스켈레톤 로딩·당겨서 새로고침 등 모션 적용.

| 랜챗 대기 | 결제 | 매칭 순간 | 상대 프로필 | 영상통화 요청 받음 | 나가기 선택 |
|---|---|---|---|---|---|
| ![](docs/screenshots/04_random.png) | ![](docs/screenshots/05_paywall.png) | ![](docs/screenshots/06_free_exit_locked.png) | ![](docs/screenshots/08_partner_profile.png) | ![](docs/screenshots/09_incoming_video.png) | ![](docs/screenshots/10_exit_choice.png) |

| 친구 | 매칭 목록 | 메시지 | 공개방 | 공개방 대화 | 영상통화 |
|---|---|---|---|---|---|
| ![](docs/screenshots/11_friends.png) | ![](docs/screenshots/12_matches.png) | ![](docs/screenshots/13_dm.png) | ![](docs/screenshots/14_rooms.png) | ![](docs/screenshots/15_room.png) | ![](docs/screenshots/16_video.png) |

| 신고 | 게시물 작성 | 내 프로필 | 프로필 편집 | 설정 | 가입 (국가 선택) |
|---|---|---|---|---|---|
| ![](docs/screenshots/17_report.png) | ![](docs/screenshots/18_new_post.png) | ![](docs/screenshots/19_my_profile.png) | ![](docs/screenshots/20_edit_profile.png) | ![](docs/screenshots/21_settings.png) | ![](docs/screenshots/01_phone.png) |

## 기획 항목별 구현 상태

| 기획 | 구현 | 서버에서도 강제하는가 |
|---|---|---|
| 휴대폰 인증, 번호 하나당 계정 하나 | Supabase Phone OTP, **51개국** 국가 선택 (폰 지역으로 자동 선택) | ✅ 번호당 계정 1개 |
| 국기 표시 | 프로필·채팅·공개방에 국기 | ✅ 국가는 **인증된 전화번호로 서버가 결정** (앱이 위조 불가). +1은 미국으로 표시 |
| 친구 | 랜챗·공개방에서 만난 사람에게 친구 요청 → 수락. 친구끼리 메시지 | ✅ 본 적 있는 사람에게만 요청 가능, 하루 50건 제한, 신고하면 친구 자동 해제 |
| 공개방 | 주제별 10개 방(Global Lounge, 언어교환, 음악…), 실시간 접속자 수 | ✅ 방에 들어와 있어야 글쓰기, 10초에 5개 제한, 차단한 사람 글은 서버가 숨김 |
| 프로필 편집 | 사진·이름·지역·소개 (인스타식) | ✅ 성별·생년월일은 서버에서도 변경 불가 |
| 18세 이상만 | 생년월일 검사 | ✅ DB 트리거. **성별·생년월일은 가입 후 변경 불가** |
| 프로필: 성별·나이·사진·피드 | 사진은 1280px로 압축 후 업로드, 인스타식 피드 | ✅ 매칭된 적 있는 사람만 프로필·사진을 볼 수 있음 |
| 랜덤 매칭 + 상대 프로필 표시 | 2초마다 매칭 요청, 채팅방 상단에 상대 정보 | ✅ 서버 잠금으로 이중 매칭 방지, 방금 만난 상대는 제외 |
| 채팅 + 사진·동영상 | 실시간 전송 + 10초마다 재동기화(망이 끊겨도 메시지 유실 없음) | ✅ 참여자만 읽기·쓰기, 10초에 10개 제한(스팸) |
| 성별 선택 = 유료 | 월 구독 **또는 24시간 이용권(단건 결제)** | ✅ 서버가 구매를 검증, 영수증은 1회만 사용 가능 |
| 영상통화 = 월 구독자만, 상대 수락 | LiveKit(인도 리전). 요청 → 상대 수락 → 통화. 받는 쪽은 무료 사용자도 가능 | ✅ 요청은 구독자만, 수락은 상대만 가능. 수락된 통화만 입장권 발급 |
| 나가기 잠금: 무료 5초 / 유료 0.2초 | X 버튼에 남은 초 표시 | ✅ 서버가 다시 확인. "다음 매칭"으로 잠금을 우회하는 것도 막음 |
| 나가기 후 「로비 / 다음 매칭」 | ✅ | — |
| 신고하고 나가기 | **잠금과 관계없이 즉시**. 신고 시 자동 차단 | ✅ 24시간 안에 서로 다른 3명이 신고하면 자동으로 7일 정지 |
| 최근 만난 사람 → 메시지, 신고 | ✅ | ✅ 차단된 상대에게는 메시지 전송 불가 |
| 캡처 방지 | 채팅·영상·상대 프로필 화면 | — |
| 받은 사진·동영상 가림 | 상대가 보낸 사진·영상은 **탭해야 보임**(원치 않는 노출 사진 차단). 동영상은 폰의 플레이어로 재생 | — |
| 입력 중 표시 | "typing…" 실시간 표시(3초에 한 번만 전송해 데이터 절약) | — |
| 영상 화질 자동 조절 | LiveKit 적응형 스트림 + dynacast: 망이 약하면 화질을 낮춰 끊김을 줄이고, 연결이 끊기면 "다시 연결 중" 표시 | — |
| **힌디어 / 영어** | 폰 언어를 따라갑니다. Android 13 이상은 앱별 언어 설정도 가능 | — |
| 설정 & 안전 | 커뮤니티 규칙, 고충처리 담당자 연락처(IT Rules 2021), 로그아웃 | — |
| **앱 안에서 계정 삭제** (Google Play 필수) | 프로필·게시물·채팅·사진 전부 삭제 | ✅ 정지된 사람은 탈퇴 후 같은 번호로 재가입해도 정지가 유지됨(번호는 해시로만 보관) |

## 구조

```
app/src/main    공통 화면·규칙 (Jetpack Compose). Backend/Billing 인터페이스
app/src/demo    FakeBackend (실제 서버와 같은 규칙) + 데모 결제
app/src/live    SupabaseBackend + Google Play 결제 + LiveKit 영상
server/migrations   Postgres 스키마·RLS·규칙 함수 (실제 프로젝트에 적용됨)
server/functions    video-token (LiveKit 입장권), verify-purchase (Play 영수증 검증), delete-account (계정 삭제)
server/test_rules.sql  서버 규칙 테스트 시나리오
```

## 검증

- **앱 테스트**: 총 90개 통과(demo 44개 + live 46개, 실패 0). 실서버 통합 테스트 `LiveServerTest`는 테스트 봇과 QA 계정을 같이 쓰므로 봇을 끈 상태에서만 돌립니다. 계정 삭제 실서버 테스트 1개는 QA 계정을 지우므로 평소에는 건너뜁니다.
  - 규칙, ViewModel 22개, 전체 UI 흐름(화면 17장), 힌디어 화면, 받은 사진 가림.
  - ViewModel 테스트에는 서버 쪽 잠금 확인, 영상 수락, 메시지(DM), 매칭 중 이탈한 방 정리, 로그아웃 중 남은 방 정리, 결제 복원도 포함됩니다.
- **코드 리뷰 2회**: 1차 11건, 2차 4건 발견. 모두 수정했고, 2차 수정은 회귀 테스트가 실제로 버그를 잡는지 코드를 일부러 되돌려 확인했습니다.
- **실제 서버 통합 테스트** (`LiveServerTest`): 통과. 계정 삭제 기능도 실제 서버에서 QA 계정 하나로 확인했고, 삭제 후 해당 계정의 데이터가 남지 않은 것까지 확인했습니다.
  - 로그인 → 매칭 → 실시간 메시지 수신 → 서버의 5초 잠금 거부 → 구독 없는 영상 요청 거부 → 프로필 조회 → 상대가 나감 실시간 감지 → 최근 목록.
- **친구·공개방 SQL 테스트** (`server/test_social.sql`): 국기 위조 불가, 모르는 사람 요청 불가, 방에 없으면 글쓰기 불가, 발신자 이름 위조 불가, 도배 제한, 요청·수락·친구 메시지방, 신고 시 친구 해제·글 숨김. 모두 통과(실행 후 롤백).
- **서버 규칙 SQL 테스트**: 18세 미만 거부, 성별 변경 불가, 남의 채팅 읽기·쓰기 불가, 잠금, 구독, 차단, 자동 정지, 스팸 제한. 모두 통과.
- **서버 함수**: LiveKit 토큰 서명을 별도 JWT 라이브러리(jose)로 검증. 배포 후 미인증 요청 401, 키 미설정 503 확인.
- **Lint**: 오류 0개. R8 최적화 후에도 서버 데이터 변환 코드가 유지되는 것을 확인.
- **아직 못 한 것**: 실기기 테스트. 이 환경에는 에뮬레이터가 없습니다. 실제 영상통화는 LiveKit 키 설정 후 폰 2대로 확인해야 합니다.

## 출시 전에 사장님이 직접 해야 할 설정

| 순서 | 할 일 | 어디서 |
|---|---|---|
| 1 | **SMS 인증 연결** (이것이 없으면 live 앱 로그인 불가) | Supabase 대시보드 → Authentication → Sign In / Providers → Phone. Twilio Verify, MessageBird, Vonage, Textlocal 중 하나. 인도는 DLT 등록된 발신 번호가 필요 |
| 2 | **LiveKit Cloud 가입** 후 프로젝트 생성. Edge Function Secrets에 `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` 입력 | livekit.io → Supabase → Edge Functions → Secrets |
| 3 | **Play Console**에 앱 등록, 상품 2개 생성: 구독 `yaari_premium_monthly`, 단건(소모성) `gender_pass_24h`. 대상 연령은 18세 이상 전용 | Play Console |
| 4 | Google Cloud 서비스 계정을 만들어 Play Console에 권한 부여(재무 데이터 보기, 주문 관리). 키 JSON을 Secret `GOOGLE_SERVICE_ACCOUNT_JSON`에 입력 | Google Cloud Console |
| 5 | `chat/app/src/main/kotlin/com/yaari/chat/ui/SettingsScreen.kt`의 `Support`에 **실제 고충처리 담당자 이름과 이메일**을 입력합니다(IT Rules 2021 의무). 현재는 임시값입니다 | 코드 |
| 6 | 개인정보처리방침 페이지(`docs/privacy.html`)는 이 PR이 병합되면 `https://entrepreneur123.github.io/india/yaari-privacy.html`에 자동으로 올라갑니다. 담당자 정보를 실제 값으로 고친 뒤 Play Console에 이 주소를 입력합니다 | GitHub Pages |
| 7 | 테스트 데이터 삭제(아래 SQL) | Supabase SQL Editor |

테스트 데이터 정리용 SQL입니다. 서버 테스트용 가짜 사용자 6명과 QA 계정 2개를 지웁니다(QA 계정 1개는 계정 삭제 테스트로 이미 지워졌습니다). Supabase 연결 도구가 삭제 명령마다 별도 승인을 요구해서 실행하지 못했습니다.

```sql
delete from auth.users where id::text like '00000000-0000-0000-0000-0000000000%' or email like 'qa_@yaari.test';
drop table if exists public._t; drop function if exists public._act(uuid); drop function if exists public._fails(text, text);
```
