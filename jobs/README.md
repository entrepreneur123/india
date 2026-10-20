# SSC Job Alerts (가칭): 인도 공무원 채용 알림 + 자격 판정

SSC(Staff Selection Commission) 공식 사이트 **ssc.gov.in에서만** 공고·결과·입장권·정답지를 가져오고, 지원자가 생년월일·카테고리·학력을 넣으면 **직무별로 지원 가능 여부**를 알려주는 웹(이후 앱)입니다. LottoDost와 같은 원칙으로 만들었습니다: **틀린 날짜보다 늦은 날짜가 낫다.**

- 아직 게시(배포)하지 않은 시제품입니다. 데이터 브랜치·Pages 연결은 하지 않았습니다(아래 "다음 단계").
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
| `pipeline/sarkari/classify.py` | 제목으로 종류·시험·연도 판별 (영어·힌디어) |
| `pipeline/sarkari/eligibility.py` | 나이·완화·학력·응시료 판정 |
| `pipeline/rules/ssc-*.json` | **공고 원문에서 옮긴 규칙** (문단·쪽수 `ref` 포함) |
| `pipeline/sarkari/build.py` | 전체 실행, 안전장치, 새 항목(`new.json`) 산출 |
| `web/eligibility.js` | 판정 로직 JS 포팅 (Python과 같은 테스트 케이스로 검증) |
| `web/index.html`, `web/app.js` | 최신 공고 / 자격 판정 / 일정 3개 탭 |

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
python -m pytest -q tests                       # 87 tests (실제 SSC 응답 fixture)
python -m sarkari.build --out ../../public       # 실제 수집 (약 25초, 요청 약 15회, 요청 간 1초 간격)

cd ../web && node --test eligibility.test.mjs    # 26 tests (Python과 같은 케이스 23개 포함)
# 미리보기: build 결과의 public/jobs/ 에 index.html, app.js, eligibility.js 를 복사하고 정적 서버로 열기
```

GitHub 러너(인도 밖)에서 ssc.gov.in이 열리는지는 아직 모릅니다. **Actions → jobs-probe** 를 한 번 수동 실행하면 확인됩니다. 이 컨테이너에서는 ssc.gov.in만 열렸고 ssc.nic.in·upsc.gov.in·ibps.in·rrbcdg.gov.in은 연결이 끊겼습니다.

## 대형 서비스와 비교해 아직 부족한 점 (솔직하게)

| 항목 | 현재 | 대형 앱(Adda247, Testbook 등) 수준 |
|---|---|---|
| 기관 범위 | SSC만 | SSC·UPSC·RRB·IBPS·주 공무원위원회 |
| 푸시 알림 | `new.json`만 생성, 발송 없음 | 시험별 구독 푸시 |
| 판정 대상 시험 | CGL 2026, CHSL 2026 (둘 다 접수 마감) | 진행 중인 공고 위주 |
| 언어 | 화면은 영어, 공고 제목은 영어·힌디어 원문 | 힌디어 UI 기본 |
| 앱 | 웹만 | Android 앱 |
| 사진·서명 크기 조정 | 없음 | 일부 제공 |

## 다음 단계 (추천 순서)

1. Actions에서 **jobs-probe** 실행 → 러너에서 SSC 접속 가능 여부 확인 (불가하면 인도 리전 호스팅 필요)
2. 게시 워크플로: 30분마다 실행(`lastUpdates`로 대부분 요청 1회), `data` 브랜치의 `jobs/`에 게시
3. 다음 공고(MTS 2026 등)가 나오는 즉시 규칙 파일 추가: 공고 PDF → 규칙 JSON → 공식 대조 테스트
4. 힌디어 UI (원어민 검수 필수), 푸시(FCM, 기존 앱의 `push.py` 재사용)
5. Android 앱: LottoDost의 오프라인 캐시·미러 전환 구조 재사용
