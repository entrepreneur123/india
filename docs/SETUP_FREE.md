# 무료로 먼저 켜는 것들 (돈 드는 도메인·Play 등록은 마지막)

각 단계가 끝나면 값만 알려 주세요(또는 아래 위치에 넣어 주세요). 나머지는 코드가 이미 준비돼 있습니다.

| 순서 | 무엇 | 걸리는 시간 | 받는 효과 |
|---|---|---|---|
| 1 | Google Search Console | 5분 | Google 검색 등록, 검색어별 노출·클릭 수 확인 |
| 2 | Bing Webmaster Tools | 2분 | Bing 검색 등록 (Search Console에서 가져오기) |
| 3 | Cloudflare Web Analytics | 5분 | 방문자 수 측정 (수입 계산의 기초) |
| 4 | Cloudflare Pages 배포 | 10분 | 상업용 호스팅 준비 (광고 붙일 때 필요) |
| 나중 | 도메인, AdSense, Google Play | 유료/심사 | 광고 수입, 앱 출시 |

## 값을 넣는 곳

- **Variables**(공개돼도 되는 값): https://github.com/entrepreneur123/india/settings/variables/actions/new
- **Secrets**(비밀 값): https://github.com/entrepreneur123/india/settings/secrets/actions/new

| 이름 | 종류 | 어디서 |
|---|---|---|
| `GOOGLE_SITE_VERIFICATION` | Variable | 1단계 |
| `BING_SITE_VERIFICATION` | Variable | 2단계 (가져오기를 쓰면 불필요) |
| `CF_BEACON_TOKEN` | Variable | 3단계 |
| `CLOUDFLARE_API_TOKEN` | **Secret** | 4단계 |
| `CLOUDFLARE_ACCOUNT_ID` | **Secret** | 4단계 |
| `CF_PAGES_PROJECT` | Variable | 4단계 (예: `lottodost`) |
| `SITE_URL`, `ADSENSE_CLIENT` | Variable | 나중 (도메인, AdSense 이후) |
| `ADSENSE_SLOT` | Variable | 나중 (AdSense → 광고 → 광고 단위별 → 디스플레이 광고 단위의 숫자 ID). 없으면 자동 광고만 |
| `PLAY_URL` | Variable | 나중 (Play 출시 후 앱 주소. 넣으면 사이트에 앱 안내가 나타남) |

### 도메인 연결 (유료 1개: AdSense 신청과 검색 순위에 필요)
AdSense는 직접 소유한 도메인만 받습니다. `github.io`·`pages.dev` 주소로는 신청할 수 없습니다(Blogger 같은 협력 호스트만 예외). 검색 신뢰도도 도메인에 쌓이므로 **빨리 바꿀수록 손해가 적습니다**.

**추천: `lottodost.com` (Cloudflare에서 구입, 연 US$10.46, 갱신도 같은 값)**

2026-10-25에 등록 기관 조회(RDAP)로 확인한 내용:

| 이름 | 상태 | 비고 |
|---|---|---|
| lottodost.com | 등록 가능 | 앱·사이트 이름과 같음 |
| lotterydost.com | 등록 가능 | 브랜드를 바꿔야 함 |
| lottodost.in | 등록 가능 | 아래 `.in` 주의 참고 |
| lotterydost.in | **등록 불가** | `.in` 등록 기관이 "lottery"가 들어간 이름을 막음 ("restricted by Registry Policy") |

`.in`(인도 국가 도메인)은 Google이 "그 나라를 위한 사이트라는 강한 신호"라고 설명합니다. 그래도 `.com`을 추천하는 이유는 세 가지입니다.
- Cloudflare 가격표에 `.in`이 없습니다. 다른 업체에서 사서 네임서버를 옮기는 단계가 더 생깁니다.
- 2025년부터 `.in` 등록자는 신원 확인(KYC)을 거쳐야 하고, 외국인은 여권 제출과 인도와의 관련성 설명을 요구받는다는 보도가 있습니다. 규정이 아직 오락가락합니다.
- `.com`이어도 인도 대상임을 알리는 신호는 사이트가 이미 보냅니다: hreflang `en-IN`·`bn-IN`·`hi-IN`, IST 시간, ₹ 금액, 인도 내 Cloudflare 서버. 1위 경쟁 사이트 중에도 `.com`이 있습니다(lotterysambad.com).

#### 순서 (약 15분)
1. https://dash.cloudflare.com 로그인 → 왼쪽 **Domain Registration → Register Domains** → `lottodost.com` 검색 → 구입. 연락처는 본인 정보를 넣으면 되고, WHOIS 공개 정보는 Cloudflare가 자동으로 가립니다.
2. **Workers & Pages → `lottodost` → Custom domains → Set up a custom domain** → `lottodost.com` 입력 → Activate. 같은 방법으로 `www.lottodost.com`도 추가합니다. 같은 계정에서 산 도메인이라 DNS가 자동으로 잡힙니다. 몇 분 뒤 https://lottodost.com 이 열리면 완료입니다.
3. GitHub Variables에 `SITE_URL` = `https://lottodost.com` (끝에 / 없이) 추가 → Actions → results → Run workflow (deploy 체크). 이 실행부터 모든 대표 주소(canonical)·sitemap·공유 링크가 새 주소로 바뀝니다. 옛 github.io 페이지는 새 주소를 대표 주소로 가리킵니다.
4. Search Console → 속성 추가 → **도메인** 유형으로 `lottodost.com` 입력 → 안내되는 TXT 레코드를 Cloudflare DNS에 추가 → 확인 → Sitemaps에 `https://lottodost.com/sitemap.xml` 제출.
5. Bing Webmaster Tools → Search Console에서 가져오기(Bing·Edge 검색. IndexNow로 새 결과를 바로 알림).
6. AdSense 신청 → 사이트에 `lottodost.com` 입력 → 받은 `ca-pub-…`를 Variable `ADSENSE_CLIENT`에 추가. ads.txt는 자동으로 생기고, 심사 코드도 모든 페이지에 들어갑니다.

### 앱 광고 (AdMob, Play 출시 전에)
1. https://admob.google.com 가입 → 앱 추가(Android, 아직 미출시 선택) → **앱 ID**(`ca-app-pub-…~…`)
2. 광고 단위 2개 만들기: **배너**(적응형) 1개, **전면 광고** 1개 → 각각의 광고 단위 ID(`ca-app-pub-…/…`)
3. 저에게 세 값을 주시면 `ADMOB_APP_ID`, `ADMOB_BANNER`, `ADMOB_INTERSTITIAL`로 빌드에 넣습니다(광고 ID는 공개돼도 되는 값).
4. AdMob → 차단 관리 → 민감한 카테고리: **도박 및 베팅은 기본 차단 그대로**.
- 앱에 이미 들어간 것: 탭 바 위 배너 1개, 결과 화면을 닫을 때만 전면 광고(첫 2분 금지, 4분 간격, 한 번 실행에 최대 3번), 유럽 등 법이 요구하는 곳에서만 동의 창.

### AdSense 승인 후 설정 (확인: AdSense 고객센터, 2026-10-19)
1. 광고 → 사이트 옆 수정 → **자동 광고 켜기**. 오버레이 형식에서 **사이드 레일**(PC 화면 양옆, 우리 사이트는 가운데 폭이 좁아 자리가 넉넉함)과 **앵커**를 켜고, **비네트(전면)는 끄기**(경쟁 앱 불만 1위가 광고 방해).
2. 브랜드 안전 → 차단 관리 → 민감한 카테고리: **도박 및 베팅(Gambling & Betting)은 기본 차단 상태 그대로 둘 것.** 온라인게이밍법 §6(온라인 머니게임 광고 금지)과 Play 정책 때문.
3. 수동 광고 단위를 하나 만들어 그 숫자 ID를 `ADSENSE_SLOT`에 넣으면, 결과 아래·등수 사이에 페이지당 최대 3개가 들어갑니다(번호 안에는 절대 넣지 않음).
