# 다루는 복권과 다루지 않는 복권 (2026-10-19 조사)

기준: **인도 주정부가 운영하는 합법 복권**만, 그리고 **잘못된 번호를 내보내지 않을 수 있는 출처**가 있을 때만 다룹니다.
조사 방법: 각 공식 사이트를 이 환경과 GitHub 실행 서버(인도 밖)에서 직접 접속해 확인(`.github/workflows/probe.yml`).

## 다루는 것

| 복권 | 시간 (IST) | 출처 | 검증 |
|---|---|---|---|
| Nagaland Dear 1 PM · 8 PM | 매일 | lotterysambad.com 텍스트 PDF | 공식 관보 OCR 대조 → "검증됨" |
| Sikkim Dear 6 PM | 매일 | lotterysambad.com 텍스트 PDF | 구조 검증 |
| Nagaland Dear Laxmi 8 PM | 매일 | 공식 스캔 PDF | OCR 4회가 모두 일치할 때만 번호, 아니면 PDF 링크 |
| Dear·Punjab 범퍼·월간 | 비정기 | lotterysambad.com 텍스트 PDF + 상금표 | 구조 검증 |
| **Kerala State Lottery** | 매일 3 PM (범퍼 2 PM) | 공개 사본 3곳 (govdotin PDF, keralalotteryresult.net, keralalotteries.net) | **등수마다 두 사본이 정확히 일치해야 확정.** 1·2·3등·위로상이 확정돼야 게시. 하위 등수가 엇갈리면 "확인 중" 표시 후 2일간 재확인 |

Kerala는 공식 사이트(statelottery.kerala.gov.in)가 인도 밖 서버에 응답하지 않아 공개 사본을 씁니다.
실제 사례: 17-10-2026 9등에서 두 사본이 9865 / 9465로 달랐고, 그 등수만 "확인 중"으로 게시됐습니다.

## 아직 다루지 않는 것과 이유

| 복권 | 이유 | 가능해지는 조건 |
|---|---|---|
| Sikkim Labhlaxmi 4 PM | 공식 PDF가 스캔 이미지(텍스트 없음). OCR이 2등 번호를 잘못 읽은 사례 확인 | 360개 번호 OCR 검증 방식 개발, 또는 두 번째 출처 |
| Maharashtra 주 복권 | 공식 PDF가 스캔 이미지. 350개 번호 표에서 OCR 오류 확인 | 위와 같음 |
| Punjab 주간 (Dear 50) | 기계가 읽을 수 있는 출처 없음. 뉴스 기사 번호에 오타 확인(25252 → 25254) | 공식 사이트 접속(인도 서버 필요) |
| Goa | 공식 사이트가 인도 밖 접속 차단(403) | 인도 위치 서버 |
| West Bengal | 2026년 자체 추첨 확인 안 됨. "WB 로또" 검색은 대부분 Nagaland Dear 결과를 찾는 것 | 공식 추첨 재개 확인 |
| Meghalaya "Easy Lottery" | 온라인 복권. 온라인게이밍법(2025) 광고 금지와 충돌 소지 | 법률 검토 |
| Mizoram Singam | 결과를 올리는 사이트가 정부 사이트인지 확인 안 됨 | 공식 출처 확인 |
| Bhutan, Kuber, satta/matka | 인도 합법 주정부 복권 아님 | 다루지 않음 |

**인도 위치 서버**가 있으면 Kerala·Punjab 공식 결과로 검증하고 Goa를 추가할 수 있습니다.
- 무료 후보: Oracle Cloud 무료 등급(뭄바이·하이데라바드 지역, 카드 등록 필요).
- 유료 후보: 인도 VPS 월 약 4~6달러.
