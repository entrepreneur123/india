# 운영 매뉴얼 (1인 운영 기준)

## 처음 한 번

1. 이 브랜치를 기본 브랜치에 머지합니다(현재는 이 브랜치가 기본 브랜치입니다).
2. **Actions → results → Run workflow**를 실행합니다. `backfill_days`는 30, `ocr`는 끕니다. 그러면 `data` 브랜치에 최근 약 30일치 결과가 만들어집니다.
3. **Settings → Pages**에서 Source를 *Deploy from a branch*, Branch를 `data`, 폴더를 `/ (root)`로 지정하고 저장합니다.
   - 확인: `https://entrepreneur123.github.io/india/v2/today.json`이 열리면 됩니다.
   - 확인: `https://entrepreneur123.github.io/india/privacy.html`이 열리면 됩니다. 이 주소가 Play 콘솔에 입력할 개인정보처리방침 URL입니다.
4. (완료) `docs/privacy.html` 연락처 이메일: yechan750745@gmail.com
5. 결과 웹사이트는 `web/sitegen.py`가 결과가 바뀔 때마다 다시 만듭니다. 회차마다 미리 만든 HTML 페이지(영어 `/`, 벵골어 `/bn/`, 힌디어 `/hi/`), 날짜별 페이지, 보관함, 사이트맵을 만들고, 문구는 앱의 strings.xml을 그대로 씁니다.
   - 저장소 Settings → Variables에서 설정합니다(모두 선택 사항): `SITE_URL`(자체 도메인, 예 `https://example.in`), `ADSENSE_CLIENT`(`ca-pub-…`, 넣으면 광고 칸과 ads.txt가 생김), `CF_BEACON_TOKEN`(Cloudflare 무료 방문자 통계).

## 즉시 푸시 알림 켜기 (Firebase, 한 번만)

코드는 이미 들어 있고, 아래 값만 넣으면 작동합니다. 값이 없으면 앱은 지금처럼 예약 확인만 합니다.

1. https://console.firebase.google.com 에서 프로젝트를 만들고 Android 앱을 추가합니다(패키지명 `com.lottodost.app`, 이름을 바꾸면 그 패키지명).
2. 내려받은 `google-services.json`에서 네 값을 `android/gradle.properties`에 적습니다. 이 값들은 비밀번호가 아니라 프로젝트 식별자입니다.
   ```
   FIREBASE_APP_ID=<client[0].client_info.mobilesdk_app_id>
   FIREBASE_API_KEY=<client[0].api_key[0].current_key>
   FIREBASE_PROJECT_ID=<project_info.project_id>
   FIREBASE_SENDER_ID=<project_info.project_number>
   ```
3. Firebase 콘솔 → 프로젝트 설정 → 서비스 계정 → "새 비공개 키 생성"으로 JSON을 받습니다. 저장소 **Settings → Secrets → Actions**에 `FCM_SERVICE_ACCOUNT`라는 이름으로 JSON 전체를 붙여 넣습니다. 이 파일은 비밀이므로 저장소에 절대 커밋하지 마세요.
4. 다음 결과 게시부터 자동으로 푸시가 나갑니다. 첫 실행은 기존 결과를 기록만 하고 보내지 않습니다(설치자 전원에게 옛 결과 알림이 가지 않도록).

동작 방식: 결과가 GitHub Pages에 실제로 반영된 것을 확인한 뒤 "새 결과" 신호만 보냅니다(`pipeline/lottery_pipeline/push.py`). 알림 문구는 폰이 직접 사용자 언어로 만들고, 저장한 티켓도 폰 안에서 확인합니다.

## 매일 자동으로 도는 것

GitHub 예약 실행은 늦게 시작하거나 건너뛰는 경우가 있습니다(2026-10-19 실측: 07:20 예약이 07:58에 시작해 1 PM 결과가 13:28 IST에 게시됨). 그래서 추첨 약 1시간 45분 전에 실행을 예약하고, 실행 안에서 추첨 5분 뒤까지 기다렸다가 폴링합니다. 2026-10-18 실측에서는 "5분마다"로 설정했는데 15:09, 15:12, 15:37 UTC에만 실행됐습니다. 그래서 **추첨마다 한 번, 추첨 10분 전에 시작하는 실행**을 두고, 그 실행 안에서 60초마다 직접 확인해 결과가 나오면 즉시 게시한 뒤 끝냅니다. 30분 뒤에는 백업 실행이 하나 더 있습니다.

| UTC 시작 | IST | 하는 일 |
|---|---|---|
| 05:47 (백업 07:17) | 11:17 | 실행 안에서 13:05 IST까지 대기 → 1 PM 결과지가 나올 때까지 최대 90분 폴링 → 즉시 게시 |
| 10:47 (백업 12:17) | 16:17 | 18:05 IST부터 6 PM(Sikkim) 결과지 폴링 |
| 12:47 (백업 14:17) | 18:17 | 20:05 IST부터 8 PM 결과지 폴링 |
| 15:10, 15:40, 16:10 | 20:40~21:40 | 공식 관보 OCR 대조(Verified), Dear Laxmi PDF, 범퍼 카탈로그·예정 추첨 갱신 |
| 03:00 | 08:30 | 최근 2일 재확인 |

- 결과가 바뀔 때만 `data` 브랜치에 커밋되고 Pages가 다시 빌드됩니다.
- 실행 로그에 `::warning::`이 뜨면 사람이 확인해야 합니다. 모든 경고는 **번호를 게시하지 않거나 기존 번호를 유지하는 쪽**으로 동작합니다.
- Sambad 요청 빈도: 결과를 기다리는 동안 1분에 2건 정도(결과지 1건 + 목록 1건)입니다. 결과가 나오면 바로 멈춥니다.

## 범퍼·월간 추첨 당일 절차 (중요)

범퍼는 상금 구조가 매번 다르고 상금액이 이미지로만 인쇄됩니다. 그래서 상금표를 사람이 한 번 입력해야 합니다.

1. 추첨 후 Actions 로그에 `XX241026 (DEAR PUJA BUMPER) has no prize table in specials.json` 경고가 뜹니다. 이때 앱에는 결과지 링크만 표시됩니다.
2. Sambad 결과지 PDF를 열어 각 등수의 상금, 맞추는 방식(전체 번호 / 끝 5자리 / 끝 4자리), 번호 개수를 `pipeline/specials.json`에 추가합니다. 기존 두 항목을 참고하세요.
3. 커밋하면 다음 실행에서 자동으로 파싱됩니다. 개수가 한 개라도 다르면 게시되지 않으니, 실수가 숫자로 드러납니다.

## 호스팅 이전 (사용자 증가 시)

- GitHub Pages는 월 100GB 소프트 한도가 있습니다. 시뮬레이션상 **DAU 약 30만 명 전후**에서 넘습니다(`loadsim/simulate.py`). 또 GitHub 약관상 상업 서비스용 CDN이 아닙니다.
- Cloudflare 이전 절차:
  1. Cloudflare Pages 프로젝트를 만들고 커스텀 도메인을 연결합니다(`*.pages.dev`는 공용 도메인이라 차단 위험이 있습니다).
  2. 저장소 Secrets에 `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`를, Variables에 `CF_PAGES_PROJECT`를 추가합니다.
  3. `pipeline/app_config.json`의 `mirrors` 맨 앞에 새 도메인을 넣습니다. 이미 설치된 앱도 config.json을 받아 다음 실행부터 새 주소를 씁니다.

## 알려진 한계

- 시작 시점이 수십 분 늦어지는 문제는 폴링 루프로 흡수합니다. 그래도 백업 실행까지 모두 누락되면 결과가 늦어집니다. 더 확실히 하려면 Cloudflare Workers Cron으로 `workflow_dispatch`를 호출하는 방법이 있습니다.
- 공개 저장소의 예약 워크플로는 60일간 저장소 활동이 없으면 자동으로 꺼집니다. data 브랜치 커밋이 활동으로 인정되는지는 `미확인`이니, 가끔 Actions 탭을 확인하세요.
- 공식 관보는 스캔 이미지라 OCR 검증이 자주 실패합니다. 이때는 Verified 표시 없이 Sambad 번호만 게시합니다.
  - 28개 결과지 대조에서 불일치는 0건이었습니다.
- Dear Laxmi(4자리)는 공식 관보에만 있어 대부분 PDF 링크로만 표시됩니다.
- 공식 사이트는 Python 기본 User-Agent를 403으로 차단합니다(2026-10-18 확인). 파이프라인은 별도 UA를 씁니다.
