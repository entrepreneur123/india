# 운영 매뉴얼 (1인 운영 기준)

## 처음 한 번

1. 이 브랜치를 기본 브랜치에 머지합니다(현재는 이 브랜치가 기본 브랜치입니다).
2. **Actions → results → Run workflow**를 실행합니다. `backfill_days`는 30, `ocr`는 끕니다. 그러면 `data` 브랜치에 최근 약 30일치 결과가 만들어집니다.
3. **Settings → Pages**에서 Source를 *Deploy from a branch*, Branch를 `data`, 폴더를 `/ (root)`로 지정하고 저장합니다.
   - 확인: `https://entrepreneur123.github.io/india/v2/today.json`이 열리면 됩니다.
   - 확인: `https://entrepreneur123.github.io/india/privacy.html`이 열리면 됩니다. 이 주소가 Play 콘솔에 입력할 개인정보처리방침 URL입니다.
4. `docs/privacy.html`의 연락처 이메일을 채웁니다.

## 매일 자동으로 도는 것

| UTC | IST | 하는 일 |
|---|---|---|
| 07:35–08:55, 5분마다 | 13:05–14:25 | 1 PM 결과지(Sambad) 수집 |
| 12:35–13:55 | 18:05–19:25 | 6 PM(Sikkim) 수집 |
| 14:35–15:55 | 20:05–21:25 | 8 PM 수집. 15시대에는 공식 관보 OCR 검증과 범퍼 카탈로그·예정 추첨 갱신도 함께 함 |
| 03:00 | 08:30 | 전날 재확인 |

- 결과가 바뀔 때만 `data` 브랜치에 커밋되고 Pages가 다시 빌드됩니다.
- 실행 로그에 `::warning::`이 뜨면 사람이 확인해야 합니다. 모든 경고는 **번호를 게시하지 않거나 기존 번호를 유지하는 쪽**으로 동작합니다.

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

- GitHub cron은 정시 실행을 보장하지 않습니다(수 분 지연 가능). 정시성이 중요해지면 Cloudflare Workers Cron이나 Cloud Scheduler로 트리거만 옮기세요.
- 공식 관보는 스캔 이미지라 OCR 검증이 자주 실패합니다. 이때는 Verified 표시 없이 Sambad 번호만 게시합니다.
  - 28개 결과지 대조에서 불일치는 0건이었습니다.
- Dear Laxmi(4자리)는 공식 관보에만 있어 대부분 PDF 링크로만 표시됩니다.
- 공식 사이트는 Python 기본 User-Agent를 403으로 차단합니다(2026-10-18 확인). 파이프라인은 별도 UA를 씁니다.
