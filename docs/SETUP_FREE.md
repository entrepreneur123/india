# 무료로 먼저 켜는 것들 (돈 드는 도메인·Play 등록은 마지막)

각 단계가 끝나면 값만 알려 주세요(또는 아래 위치에 넣어 주세요). 나머지는 코드가 이미 준비돼 있습니다.

| 순서 | 무엇 | 걸리는 시간 | 받는 효과 |
|---|---|---|---|
| 1 | Google Search Console | 5분 | Google 검색 등록, 검색어별 노출·클릭 수 확인 |
| 2 | Bing Webmaster Tools | 2분 | Bing 검색 등록 (Search Console에서 가져오기) |
| 3 | Cloudflare Web Analytics | 5분 | 방문자 수 측정 (수입 계산의 기초) |
| 4 | Telegram 채널 자동 게시 | 10분 | 결과가 나오면 채널에 자동 게시 → 재방문 |
| 5 | Cloudflare Pages 배포 | 10분 | 상업용 호스팅 준비 (광고 붙일 때 필요) |
| 나중 | 도메인, AdSense, Google Play | 유료/심사 | 광고 수입, 앱 출시 |

## 값을 넣는 곳

- **Variables**(공개돼도 되는 값): https://github.com/entrepreneur123/india/settings/variables/actions/new
- **Secrets**(비밀 값): https://github.com/entrepreneur123/india/settings/secrets/actions/new

| 이름 | 종류 | 어디서 |
|---|---|---|
| `GOOGLE_SITE_VERIFICATION` | Variable | 1단계 |
| `BING_SITE_VERIFICATION` | Variable | 2단계 (가져오기를 쓰면 불필요) |
| `CF_BEACON_TOKEN` | Variable | 3단계 |
| `TELEGRAM_BOT_TOKEN` | **Secret** | 4단계 |
| `TELEGRAM_CHAT_ID` | Variable | 4단계 (예: `@채널아이디`) |
| `CLOUDFLARE_API_TOKEN` | **Secret** | 5단계 |
| `CLOUDFLARE_ACCOUNT_ID` | **Secret** | 5단계 |
| `CF_PAGES_PROJECT` | Variable | 5단계 (예: `lottodost`) |
| `SITE_URL`, `ADSENSE_CLIENT` | Variable | 나중 (도메인, AdSense 이후) |
| `ADSENSE_SLOT` | Variable | 나중 (AdSense → 광고 → 광고 단위별 → 디스플레이 광고 단위의 숫자 ID). 없으면 자동 광고만 |
| `PLAY_URL` | Variable | 나중 (Play 출시 후 앱 주소. 넣으면 사이트에 앱 안내가 나타남) |

### AdSense 승인 후 설정 (확인: AdSense 고객센터, 2026-10-19)
1. 광고 → 사이트 옆 수정 → **자동 광고 켜기**. 오버레이 형식에서 **사이드 레일**(PC 화면 양옆, 우리 사이트는 가운데 폭이 좁아 자리가 넉넉함)과 **앵커**를 켜고, **비네트(전면)는 끄기**(경쟁 앱 불만 1위가 광고 방해).
2. 브랜드 안전 → 차단 관리 → 민감한 카테고리: **도박 및 베팅(Gambling & Betting)은 기본 차단 상태 그대로 둘 것.** 온라인게이밍법 §6(온라인 머니게임 광고 금지)과 Play 정책 때문.
3. 수동 광고 단위를 하나 만들어 그 숫자 ID를 `ADSENSE_SLOT`에 넣으면, 결과 아래·등수 사이에 페이지당 최대 3개가 들어갑니다(번호 안에는 절대 넣지 않음).
