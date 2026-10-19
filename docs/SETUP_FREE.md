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
