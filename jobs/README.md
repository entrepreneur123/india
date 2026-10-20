# SSC Job Alerts (가칭): 인도 공무원 채용 알림 + 자격 판정

SSC(Staff Selection Commission) 공식 사이트 **ssc.gov.in에서만** 공고·결과·입장권·정답지를 가져오고, 지원자가 생년월일·카테고리·학력을 넣으면 **직무별로 지원 가능 여부**를 알려주는 웹(이후 앱)입니다. LottoDost와 같은 원칙으로 만들었습니다: **틀린 날짜보다 늦은 날짜가 낫다.**

- 게시 워크플로(`jobs-publish`)는 준비돼 있지만 `JOBS_PUBLISH` 변수를 켜기 전에는 아무것도 공개하지 않습니다(아래 "운영").
- 정부 사이트가 아님을 화면 맨 위에 고지하고, 모든 항목은 공식 PDF로 연결합니다(Google Play 정부 정보 정책 대응).

## 구조

```
ssc.gov.in 공개 JSON API (사이트의 main.js에서 확인, 2026-10-20)
  /api/general-website/portal/records?contentType=notice-boards|results|admit-card|answer-key&pageType=filter
  /api/general-website/portal/ssc-calendar      잠정 시험 일정표
  /api/general-website/portal/lastUpdates       최신 갱신 시각 (변화 없으면 요청 1회로 종료)
  /api/admin/5.1/allExams                       examId → 시험 코드(CGL, CHSL …)
        │
        ▼  jobs/pipeline/sarkari (Python 표준 라이브러리만 사용)
  수집 → 레코드 검증 → 분류(채용/정정/결과/정답지/입장권…) → 중복 제거 → IST 변환
        │
        ▼  jobs/v1/feed.json · calendar.json · new.json · rules/*.json · status.json
  jobs/web (정적 HTML + JS 모듈, 프레임워크 없음)
```

| 파일 | 역할 |
|---|---|
| `pipeline/sarkari/ssc.py` | API 클라이언트, 레코드 검증, 페이지 처리, 병합 |
| `pipeline/sarkari/upprpb.py` | UP 경찰 채용위원회(uppbpb.gov.in) 공지 수집: 위원회 자체 PDF만, 외부 대행사 포털 링크 제외, 실패해도 SSC는 게시 |
| `pipeline/sarkari/classify.py` | 제목으로 종류·시험·연도 판별 (영어·힌디어) |
| `pipeline/sarkari/eligibility.py` | 나이·완화·학력·응시료 판정 |
| `pipeline/rules/ssc-*.json` | **공고 원문에서 옮긴 규칙** (문단·쪽수 `ref` 포함) |
| `pipeline/sarkari/build.py` | 전체 실행, 안전장치, 새 항목(`new.json`) 산출 |
| `web/eligibility.js` | 판정 로직 JS 포팅 (Python과 같은 테스트 케이스로 검증) |
| `web/index.html`, `web/app.js` | 최신 / 시험 / 자격 / 도구 / 저장 5개 탭 |
| `web/i18n.js`, `web/tools.js`, `web/sw.js` | 영어·힌디어 문구, 서명·나이·캘린더 도구, 오프라인 |
| `pipeline/sarkari/telegram.py` | 새 공고를 텔레그램 채널에 게시 |
| `pipeline/sarkari/results_index.py` | 결과 명단 PDF → 수험번호 색인 (이름 제외, 순번 연속성 검증) |
| `pipeline/sarkari/cutoffs.py` | 결과 해설(write-up) PDF에서 합격선이 나오는 쪽 번호만 찾음 (숫자는 옮기지 않음) |
| `pipeline/sarkari/sitegen.py` | 시험별 검색 노출용 정적 페이지와 사이트맵 |
| `web/tracker.js` | 시험 단계 추적 (공식 날짜만, 없으면 "미발표") |
| `research/` | 니즈 분석: 자동완성 원본 데이터, 수집·분석 스크립트, 보고서 |
| `pipeline/exam_families.json` | 시험별 이름(영·힌, 검색용 이름)과 공고에서 확인한 학력 |
| `pipeline/ssc_guide.json` | 지원 방법·준비 서류·고객센터·타자 기준 (공고 문단 번호 포함) |
| `web/passages.js` | 타자 연습 지문 (직접 작성, SSC 지문 아님) |

## 실제 API에서 측정해 반영한 것 (추측 아님)

1. **1쪽 캐시 문제:** `pageType=filter` 없이 요청하면 1쪽이 오래된 캐시입니다. 결과 목록 1쪽은 "총 382건, 6건씩", 2쪽은 "총 386건, 100건씩"이라 **최신 결과 4건(2026-10-14)이 빠지고 7~100번째가 통째로 건너뛰어졌습니다.** SSC 사이트 자신의 결과 페이지처럼 `pageType=filter`로 요청하고, 페이지 간 크기·총건수가 다르면 게시를 거부합니다.
2. **`limit` 무시:** 페이지 크기는 서버 마음대로라서 날짜와 `nextPage`로만 페이지를 넘깁니다.
3. **실제 파일명에 `..pdf`:** 경로 조작 검사를 단순 문자열 검사로 했다가 정상 공지 2건을 버렸습니다. 지금은 경로 형식 정규식으로만 검사합니다(파일명에 구분자가 없으므로 조작이 불가능합니다).
4. **오류도 HTTP 200:** 잘못된 요청은 `statusCode: "203"`으로 옵니다. 연결 끊김도 실제로 있었습니다. 둘 다 재시도 후 실패로 처리합니다.
5. **공고는 바뀝니다:** CGL 2026은 마감 후 접수창이 다시 열렸습니다(06-23 공지, 마감 06-22 → 06-25). 규칙 파일에 `amendments`로 기록합니다.

## 자격 판정의 정확성

- **SSC의 나이 기준:** "18–27세(2026-08-01 기준)"는 **1999-08-02 ~ 2008-08-01 출생**입니다. 기준일에 27세가 된 사람은 **탈락**입니다. "27세 이하면 가능"으로 계산하는 흔한 계산기는 하루~1년 차이로 틀립니다.
- 규칙 파일의 출생일 범위 5개(CHSL 1개, CGL 4개)는 공고에 적힌 날짜 그대로이며, 로더가 공식(`기준일 − 상한 + 1일 … 기준일 − 하한`)으로 다시 계산해 **하나라도 다르면 빌드를 멈춥니다.** 옮겨 적는 과정의 오타가 사용자에게 가지 않게 하는 장치입니다.
- 직무 표(CGL 40개)는 PDF 텍스트와 **쪽 이미지를 직접 대조**했습니다. 텍스트 추출에서 칸이 깨진 Inspector(Preventive Officer/Examiner) 행이 그 예입니다.
- 계산할 수 없는 것(전역 군인 복무기간 차감, 공무원·과부 완화, JSO의 수학 60% 조건 등)은 **"확인 필요"**로 표시하고 이유를 보여줍니다. 추측해서 "가능"이라고 하지 않습니다.
- EWS는 공고상 나이 완화가 없습니다(표에 없음). 테스트로 고정했습니다.

## 실행

```bash
cd jobs/pipeline
python -m pytest -q tests                       # 115 tests (실제 SSC 응답 fixture)
python -m sarkari.build --out ../../public       # 실제 수집 (약 25초, 요청 약 15회, 요청 간 1초 간격)

cd ../web && node --test eligibility.test.mjs tools.test.mjs   # 45 tests (Python과 같은 판정 케이스 31개 포함)
# 미리보기: jobs/web/ 의 파일을 build 결과의 public/jobs/ 에 복사하고 정적 서버로 열기
```

GitHub 러너(인도 밖)에서 ssc.gov.in이 열리는지는 jobs-probe로 확인합니다(아래 "운영"). 이 개발 환경에서는 ssc.gov.in만 열렸고 ssc.nic.in·upsc.gov.in·ibps.in·rrbcdg.gov.in은 연결이 끊겼습니다.

## 사용자 니즈 (근거: [research/NEEDS.md](research/NEEDS.md))

Google 인도 자동완성 3,386개를 분석한 결과, 사람들이 찾는 것은 **문제 풀이(6%)가 아니라 "놓치지 않기와 확인"**이었습니다. 공고(25%), 결과(17.5%), 입장권(8.5%), 시험 날짜(7.4%)가 그렇습니다. 그래서 첫 화면을 다음 순서로 구성했습니다.

1. **나에게 맞는 시험**: 정보를 한 번 입력하면 모든 시험의 지원 가능 여부, 진행 상태, 다음 단계를 보여줍니다.
2. **최신 공고**
3. **결과 확인**: 수험번호만 넣으면 됩니다. 26MB PDF를 받지 않고 몇 KB로 확인하며, 이름은 저장하지 않습니다.
4. **시험별 정보**: 언제 무엇이 있는지, 급여, 나이 등.

검색 유입을 위해 시험마다 정적 페이지(`jobs/exam/<시험>/`)와 사이트맵도 만듭니다.

## 화면 구성 (2차 개편, 근거: [research/BENCHMARK.md](research/BENCHMARK.md))

하단 탭 5개로 바꿨습니다(앱처럼 엄지로 누르기 쉬운 위치).

| 탭 | 내용 |
|---|---|
| 홈 | 지금 접수 중인 시험(남은 일수), 30일 안의 시험 일정, 최신 공고(검색·필터) |
| 나에게 맞는 시험 | 정보를 한 번 입력하면 모든 시험의 지원 가능 여부와 다음 단계 |
| 결과 | 수험번호로 합격 명단 검색 |
| 시험 | 시험별 상세: 단계 추적, 주요 날짜, 응시료, 나이, 급여, 직무, FAQ, 지원 방법, 공식 자료 |
| 더보기 | 서명 조정, 타자·데이터 입력 속도 연습(SSC 기준), 나이 계산기, 지원 방법, 준비 서류, 고객센터, 저장, 설정(언어·테마), 앱 소개 |

그 밖에 테마 수동 전환(자동/라이트/다크), 불러오는 동안 자리 표시 화면, 저장·구독 알림 메시지를 넣었습니다.

## 기능 (경쟁 서비스 벤치마킹 반영)

sarkariresult.com의 실제 상세 페이지와 Adda247, FreeJobAlert, Naukri Alert 등 앱 설명을 참고했습니다.

| 기능 | 내용 | 근거 데이터 |
|---|---|---|
| 최신 공고 | 검색, 종류(새 채용·결과·입장권·정답지·정정·모집 인원), 시험·학력·언어 필터, 내 시험만, 지난 방문 이후 NEW 표시 | SSC API |
| 저장·공유 | 공고 저장(기기 안), WhatsApp/공유 버튼 | — |
| 시험 상세 | 주요 날짜, 응시료(정정 수수료 포함), 나이(출생일 범위), 완화표, 모집 인원, 직무, 중요 링크, 그 시험의 모든 공지 | 공고 원문(문단·쪽수) |
| 캘린더 알림 | 남은 일정만 `.ics`로 저장, 하루 전 알림 | 규칙 파일 |
| 자격 판정 | CGL·CHSL·속기사·SI 2026, 성별 제한 직무, 계산 불가 조건은 "확인 필요" | 공고 원문 |
| 공식 자료 | 강의계획서, 잠정·최종 모집 인원, 기출문제, 증명서 양식, 지침 (SSC PDF 링크만) | SSC API |
| 도구 | 서명 JPEG 10~20KB 맞춤(기기 안에서 처리), 나이 계산기, 사진 촬영 안내 | 공고 9.4~9.6항 |
| 힌디어 화면 | 버튼 하나로 전환, 기기 언어가 힌디어면 기본 | `i18n.js` |
| 오프라인·설치 | PWA: 인터넷이 끊겨도 마지막 공고 표시, 홈 화면 설치 | `sw.js` |
| 텔레그램 채널 | 새 공고 자동 게시 (토큰 있을 때만) | `new.json` |

**일부러 넣지 않은 것**
- **SSC용 사진 크기 조정기:** SSC는 사진 업로드를 받지 않고 지원서에서 카메라로 직접 촬영하며, 기존 사진을 찍으면 탈락시킵니다(CHSL·CGL 2026 공고 9.4~9.5항). 경쟁 사이트의 사진 조정기는 SSC에는 잘못된 안내입니다.
- **모의고사·문제 풀이 콘텐츠:** 정확성·저작권 검증이 불가능하므로 SSC 공식 기출문제와 강의계획서 링크만 둡니다.
- **UPSC·RRB·IBPS 등 다른 기관:** 이 환경에서 접속이 끊겼습니다. jobs-probe로 GitHub 러너에서 접속되는지 먼저 확인해야 합니다.

## 공고에서 확인한 사실 (2026-10-20)

- 시험 일정: CGL 1차 09-30~10-30(12-09 공지), JE 11-02~06, SI 11-20~28, CHSL 1차 11-30~12-31(01-10 공지, 모두 "예정")
- 학력: CGL 학사 · CHSL 12학년 · 속기사 12학년 · SI 학사(Fire는 10학년+3년 디플로마도 가능) · JE 공학 디플로마/학사 · 힌디 번역사 석사. MTS·GD·Selection Post·델리 경찰은 미확인이라 "공고 확인"으로 표시합니다.
- SI 2026 공고에는 장애인(PwBD) 나이 완화가 없고, 서명 크기가 본문(6cm)과 부록(4cm)에서 서로 다릅니다. 둘 다 그대로 안내합니다.
- SSC 자체 힌디어 시험명 일부(MTS·속기사)는 글자가 깨져 있어, 공지 제목의 표기를 씁니다.

## 운영

**jobs-probe 실행 (GitHub 러너에서 SSC 접속 확인)**
- 지금: 이 PR에 `jobs/pipeline`이 바뀔 때마다 자동 실행됩니다. PR의 Checks → jobs-probe → Summary에 결과가 나옵니다.
- 기본 브랜치에 병합한 뒤: 저장소 → **Actions** 탭 → 왼쪽 목록의 **jobs-probe** → 오른쪽 **Run workflow** → 브랜치 선택 → **Run workflow**. 수동 실행 버튼은 워크플로 파일이 기본 브랜치에 있어야만 보입니다.

**게시 켜기**
1. Settings → Secrets and variables → Actions → **Variables**에 `JOBS_PUBLISH` = `true`
2. (선택) 텔레그램: Secret `JOBS_TELEGRAM_BOT_TOKEN`, Variable `JOBS_TELEGRAM_CHAT_ID`
3. 30분마다 `data` 브랜치의 `jobs/`에 게시되고, 주소는 `<사이트>/jobs/` 입니다. 변화가 없으면 커밋하지 않습니다.

## 남은 부족한 점

| 항목 | 현재 | 대형 앱 수준 |
|---|---|---|
| 기관 범위 | SSC + UP 경찰 채용위원회 (공지만; 자격 판정은 SSC만) | SSC·UPSC·RRB·IBPS·주 위원회 |
| 앱 이름 | "SSC Job Alerts" 그대로 (UP 경찰이 들어가며 이름과 범위가 어긋남) | 결정 필요: 이름을 넓힐지 |
| 앱 푸시 | 텔레그램·캘린더 알림 | 시험별 앱 푸시 (Android 앱 필요) |
| 자격 판정 | 2026 공고 4개 (모두 접수 마감) | 진행 중인 공고 전체 |
| 힌디어 | 앱이 쓴 번역, **원어민 검수 전** ([검수표](research/HINDI_REVIEW.md) 준비됨) | 원어민 작성 |
