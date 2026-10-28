# 인도 사람들은 왜 "Sarkari Result / SSC"를 검색하나 (니즈 분석)

## 방법

- Google Trends API는 이 환경에서 429(요청 제한)로 막혔습니다. 대신 **Google 자동완성(인도, gl=in)**을 썼습니다.
- 자동완성은 많은 사람이 실제로 입력하는 문구를 대략 인기순으로 보여줍니다.
- 검색어 16개(sarkari result, ssc, ssc cgl/chsl/gd/mts/cpo/je/stenographer, ssc result, ssc admit card, sarkari naukri, govt job, 힌디어 3개)를 썼습니다. 각각 a~z와 "2026"을 붙여 확장해 **서로 다른 제안 3,386개**를 모았습니다(2026-10-20).
- 원본: `google_suggest_IN_2026-10-20.json`, 재현: `python analyze_needs.py`
- **한계:** 아래 비율은 검색 횟수가 아니라 "서로 다른 검색 문구의 수" 기준입니다. 어떤 니즈가 있고 어떻게 표현되는지는 보여주지만, 니즈의 정확한 크기는 아닙니다. 검색 횟수를 알려면 Google Trends나 Search Console을 직접 봐야 합니다.

## 결과

| 의도 | 비율 | 대표 검색어 (실제 자동완성) |
|---|---|---|
| 공고·모집·지원 | 24.9% | "ssc chsl notification 2026", "ssc mts form date 2026", "sarkari naukri 2026 vacancy 12th pass", "govt job 2026 for female" |
| **결과·점수** | 17.5% | "ssc gd result 2026"(검색어 "ssc gd"의 2위, "ssc"의 8위 제안), "sarkari result" |
| 시험 이름만 등 기타 | 15.0% | "ssc cgl", "ssc gd" |
| 입장권 | 8.5% | "ssc admit card 2026 release date", "ssc admit card download" |
| **시험 날짜** | 7.4% | "ssc cgl exam date 2026"(검색어 "ssc cgl"의 1위 제안), "ssc stenographer exam date 2026" |
| 시험 준비 | 6.0% | mock test, previous year paper, "ssc ki taiyari kaise kare" |
| 강의계획서·시험 구성 | 4.5% | "ssc cgl syllabus" |
| 급여·직무 | 3.5% | "ssc chsl salary", "ssc mts salary" |
| 합격선 | 3.0% | "ssc gd cut off" |
| 자격·나이 | 2.7% | "ssc cgl age limit", "ssc cpo eligibility" |
| 기본 정보 | 2.5% | "ssc full form", "ssc kya hota hai" |

**함께 발견한 사실**
1. **"ssc result"를 검색하는 사람 상당수는 SSC(직원선발위원회)를 찾는 게 아닙니다.** 상위 제안이 "ssc result 2026 maharashtra board", "telangana", "ap"입니다. 그 주들에서 SSC는 10학년 졸업시험(Secondary School Certificate)을 뜻합니다.
2. **"sarkari result" / "govt job" 이용자는 SSC만 찾지 않습니다.** 철도(RRB·Group D)와 경찰이 가장 많고, 주 공무원위원회, 군(Agniveer), 우체국(GDS), 교사(TET), 학교 성적도 찾습니다.
3. **조건으로 찾습니다:** "12th pass", "10th pass", "for female", 주 이름(UP, Bihar, MP, Odisha, Punjab, Delhi …). "내가 지원할 수 있는 것"을 찾는 검색입니다.
4. **SI 시험은 "ssc cpo"로 검색합니다.** SSC의 정식 명칭은 "Sub-Inspector in Delhi Police and CAPFs"입니다.

## 니즈 → 기능

| 니즈 (근거) | 기능 | 상태 |
|---|---|---|
| "내가 지원할 수 있는 시험" (공고 25% + 12th pass·female 같은 조건) | **"나에게 맞는 시험"**: 정보를 한 번 입력하면 모든 시험의 직무별 지원 가능 여부, 진행 상태, 다음 단계 표시 | ✅ |
| "결과 나왔나, 내가 붙었나" (결과 17.5%, "ssc gd result 2026") | **수험번호로 결과 확인**: 26MB PDF를 받지 않고 몇 KB로 확인. 이름은 저장하지 않음 | ✅ (명단 형식이 맞는 것만) |
| "언제?" (시험 날짜·입장권·지원 기간, 각 시험 1~2위 제안) | **단계 추적**: 공고 → 접수 → 입장권 → 시험 → 정답지 → 결과. 공식 날짜만 쓰고, 없으면 "SSC 미발표" | ✅ |
| 급여 (3.5%) | 직무별 기본급 구간 (공고 원문) | ✅ (CGL·CHSL·SI) |
| 나이·자격 (2.7%) | 정확한 출생일 범위와 완화표 | ✅ |
| 강의계획서·기출문제 (4.5%·6%) | SSC 공식 PDF 링크 | ✅ |
| 검색으로 들어오기 (주제 선정 이유) | **시험별 정적 페이지**: 제목을 실제 검색 표현에 맞춤("SSC CGL 2026: Exam Date, Notification, Age Limit, Salary, Admit Card, Result"), 사이트맵 포함 | ✅ |
| 합격선 (3.0%) | 결과 공지 PDF 링크만. 표가 복잡해 자동 추출하면 틀릴 위험이 큼 | ⚠ 부분 |
| 철도·경찰·주 위원회 ("sarkari result" 이용자의 큰 비중) | 아직 SSC만 | ❌ **가장 큰 공백** |
| 주별 공고 (UP, Bihar …) | SSC는 중앙정부 시험이라 해당 없음. 주 위원회를 추가할 때 필요 | ❌ |
| 모의고사·문제 풀이 (6%) | 공식 기출 링크만. 직접 만든 문제는 정확성·저작권 검증 불가 | 의도적으로 제외 |

## 결론

사람들의 1차 니즈는 **"문제 풀이"가 아니라 "놓치지 않기와 확인"**입니다.
- 무엇이 나왔나 (공고·결과·입장권)
- 언제인가 (시험 날짜·지원 기간)
- 내가 해당되나 (자격·학력·성별)
- 내가 붙었나 (결과)

문제 풀이(준비) 관련 검색은 6% 정도로, 기존 대형 학습 플랫폼(Testbook, Adda247 등)의 영역입니다. 이 앱은 첫 번째 묶음에 집중하고, **정확성(공고 원문 기준)과 개인화(내 조건)**로 기존 Sarkari 사이트와 차별화합니다.
