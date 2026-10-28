# Yaari 상용화 가이드 (MVP · 인도/방글라데시/브라질/베트남)

기준일 2026-10-19. 각 항목의 근거 URL은 맨 아래 "출처"에 있습니다. "미확인"이라고 쓴 항목은 공식 출처로 확인하지 못한 것이니 출시 전에 직접 다시 확인하세요. 법률 자문이 아니므로 유료 출시 전에 현지 변호사 검토를 한 번 받는 것을 권합니다.

---

## 0. 한눈에 보는 출시 순서

| 단계 | 기간(대략) | 할 일 | 누가 |
|---|---|---|---|
| 1 | 1일 | Google Play 개발자 계정(US$25, 1회), Supabase Pro, Vercel Pro 결제 | 사장님 |
| 2 | 1~2일 | SMS/WhatsApp 인증 연결(Twilio), 테스트 번호 삭제, 이메일 로그인 끄기 | 사장님 (설정 방법은 아래) |
| 3 | 1주 | 남은 필수 기능: 영상 노출 자동 감지 (아동안전·이용약관·계정삭제 페이지는 완료) | 개발 |
| 4 | 2주 이상 | **비공개 테스트: 테스터 12명이 14일 연속** (2023-11-13 이후 만든 개인 계정은 필수) | 사장님 + 지인 12명 |
| 5 | 1~3일 | Play Console 서류: 콘텐츠 등급(IARC), 타깃 연령 18+, 데이터 보안, CSAE 담당자, 고충처리관 | 사장님 |
| 6 | 심사 수일 | 프로덕션 출시 → 인도 먼저, 이후 방글라데시·브라질·베트남 순차 오픈 | 사장님 |

인도 SMS 발신자 등록(DLT)과 베트남·방글라데시 발신자 등록은 **3~5주** 걸리므로 1단계와 동시에 신청하세요. 그 전에는 WhatsApp 인증으로 시작하면 됩니다(아래 3절).

---

## 1. 월 비용 (출시 초기 기준)

| 항목 | 플랜 | 비용 | 비고 |
|---|---|---|---|
| Google Play 개발자 등록 | — | US$25 1회 | 2차 출처 기준 |
| Supabase (서버·DB·로그인) | Pro | 월 $25 | 무료 플랜은 7일 활동이 적으면 **일시정지**됨 → 상용 불가 |
| Vercel (웹사이트) | Pro | 월 $20/개발자 | 무료(Hobby)는 **비상업용만 허용** |
| LiveKit (영상통화) | Build → Ship | $0 → 월 $50~ | Build: 월 5,000분·동시 100명까지 무료 |
| 인증 메시지 | WhatsApp(Twilio) | 건당 인도 $0.0014 · 방글라데시 $0.0037 · 브라질 $0.0068 · 베트남 $0.0113 (Meta 요금) + Twilio 수수료 | SMS는 인도 $0.083, 방글라데시 $0.60, 브라질 $0.06, 베트남 $0.29 |
| 영상 노출 감지 | Google Vision SafeSearch | 월 1,000건 무료, 이후 1,000건당 $1.50 | 또는 AWS Rekognition 장당 약 $0.001 |
| Google Play 수수료 | — | 구독 매출의 15% | 인도는 '사용자 선택 결제'로 4%p 인하 가능(PCI DSS 필요) |

초기 월 고정비 ≈ **$45~95** + 사용량(인증 메시지·영상 감지·LiveKit 초과분).

---

## 2. 현재 앱 상태 vs 출시 필수 요건

| 필수 요건 | 상태 |
|---|---|
| 18세 이상만 (생년월일 입력, 서버에서 18세 미만 거부) | ✅ 완료 |
| 인앱 신고 | ✅ 완료 (언제든 즉시, 신고 시 자동 차단) |
| **신고 없이 차단** (Play UGC 정책: 1:1 채팅이 있으면 차단 필수) | ✅ 완료 (앱: 채팅 ⋮ 메뉴·프로필·최근·친구 / 웹: 신고 화면 하단 "신고하지 않고 차단") |
| 이용약관 동의 후 이용 | ✅ 이용약관 페이지 `/terms.html`, 웹 가입 화면에 동의 문구·링크 |
| 받은 사진·영상 가림 (탭해야 보임) | ✅ 완료 |
| **실시간 영상 노출 자동 감지** (랜덤 영상채팅은 사실상 필수) | ❌ 미구현 — 출시 전 최우선 |
| **아동안전(CSAE) 기준 공개 웹페이지 + 담당자** (랜덤채팅·소셜 앱은 18+여도 적용) | 🔶 페이지 완료 `/child-safety.html` → **담당자 실명·이메일은 사장님 지정** |
| 앱 안 계정 삭제 | ✅ 완료 |
| **웹에서 계정 삭제 요청 경로** | ✅ `/delete-account.html` (로그인 못 할 때 이메일 요청 → **7일 내 처리는 사장님이 직접**) |
| 개인정보처리방침 | ✅ `/privacy.html` (웹·타국 이용자 내용 추가. 담당자 실명·연락처 입력 필요). 기존 GitHub Pages 주소는 **404였음** → 앱·웹 모두 웹사이트 주소로 변경 |
| 사진 선택: Photo Picker (광범위 사진 권한 금지) | ✅ 시스템 Photo Picker 사용, 저장소 권한 없음 |
| 구독은 Google Play 결제만 | ✅ 완료 (웹은 결제 없이 앱 구독을 공유) |
| 인도 고충처리관 공개 | 🔶 화면은 있음, **실명·이메일 입력 필요** |
| 언어: 힌디어·벵골어·포르투갈어·베트남어 | ✅ 앱·웹 전체 번역, 기기 언어 자동 선택 + 설정에서 변경 (**현지인 검수 권장**: 기계 번역 수준) |
| 보안 점검 | ✅ 서버 감사 완료, 고위험 항목 수정·적용 (아래 6절) |

---

## 3. 로그인(전화 인증) 연결 — 사장님 작업

1. Twilio 가입 → **Verify 서비스** 생성 (WhatsApp 채널 사용 시 Meta 비즈니스 인증 필요).
2. Supabase 대시보드 → Authentication → Sign In / Providers → **Phone** → Provider: **Twilio Verify** → Account SID / Auth Token / Verify Service SID 입력.
3. 같은 화면의 **Test phone numbers(919000000001/2, 코드 123456) 삭제** — 그대로 두면 누구나 그 계정으로 로그인됩니다 (보안 감사 '치명' 항목).
4. Authentication → Providers → **Email 끄기** (지금은 테스트 봇용으로 켜져 있음. 켜 두면 전화번호 없이 계정을 무한히 만들 수 있음). 테스트 봇(`server/tools/partner_bot.ts`)도 종료.
5. Twilio 콘솔 → Verify → Settings → **Geo permissions**에서 출시 국가만 허용하고, 같은 곳의 **Fraud Guard**를 켭니다 (SMS 요금 폭탄 공격 방지). **CAPTCHA는 아직 켜지 마세요**: 켜면 Supabase가 인증번호 요청마다 CAPTCHA 토큰을 요구하는데, 지금 웹·앱은 토큰을 보내지 않아 **모든 로그인이 막힙니다** (웹 `lib/supabaseBackend.ts` `sendOtp`, 2026-10-27 확인). 앱에 Turnstile을 붙인 뒤에 켭니다.
6. 인도: 비용이 가장 싼 **WhatsApp 인증**으로 시작하고(Supabase는 Twilio·Twilio Verify에서만 WhatsApp 지원. Twilio에 **WhatsApp 발신자**를 먼저 등록해야 하고, 웹·앱이 `channel: 'whatsapp'`으로 요청하도록 바꿔야 함 — 지금 코드는 SMS로만 보냄), SMS가 필요하면 TRAI **DLT 등록**(사업자 PAN 필요, 약 10영업일) 후 국내 경로 사용. 해외 법인의 DLT 직접 등록 가능 여부는 미확인.
7. 베트남·방글라데시 SMS: 발신자 ID 사전등록 필수(베트남 약 5주·사업자등록증, 방글라데시 약 3주).

---

## 4. Google Play 출시 — 사장님 작업 (Play Console)

1. **계정**: 법인이 있으면 조직 계정(D-U-N-S 번호 무료 발급). 개인 계정이면 **테스터 12명·14일 비공개 테스트** 필수 — 지금 바로 지인 12명을 모으세요.
2. **타깃 연령: 18+만 선택** → "미성년자 접근 제한" 켜기. **콘텐츠 등급(IARC) 설문** 정확히 작성(영상 채팅, 사용자 상호작용, 사용자 생성 콘텐츠 = 예).
3. **앱 카테고리**: Social. (Social·Dating·익명/랜덤 채팅은 아동안전 표준 정책 대상)
4. **아동안전 표준**: 공개 웹페이지 링크 + CSAM 대응 담당자 연락처 등록.
5. **데이터 보안 양식**: 수집 항목 = 전화번호, 생년월일, 성별, 사진·동영상, 메시지, 기기 ID(없으면 제외), 구매 기록. 공유 = 없음(처리 위탁: Supabase·LiveKit·Twilio). 전송 중 암호화 = 예. 삭제 요청 = 예(앱 + 웹 링크).
6. **계정 삭제 웹 링크**, **개인정보처리방침 URL** 등록.
7. 상품: 아래 4-00 3항의 목록 전부(구독 1, 소모성 2, 꾸미기 44, 선물용). 서비스 계정 키를 Supabase Secret `GOOGLE_SERVICE_ACCOUNT_JSON`에 입력(README 참고).
8. **서명 키**: 지금 APK는 디버그 키 서명. Play에는 `./gradlew bundleLiveRelease`로 만든 AAB를 업로드 키로 서명해 올립니다(README "upload.properties").
9. 국가: 인도 → 방글라데시 → 브라질 → 베트남 순으로 열기. 브라질은 2026-09-30부터 **Android 개발자 인증**(Play 밖 배포 포함) 시행.

### 4-00. 실결제 켜기 — 순서대로 (2026-10-22 확인: 지금 서버는 `billing_not_configured` = 결제 꺼짐)
앱의 결제 코드(Google Play Billing)와 서버 검증(`verify-purchase`)·환불 처리(`play-rtdn`)는 완성돼 있고, **아래 계정·설정만 없습니다.**
1. **Play Console 결제 프로필(판매자 계정)** 만들기: Play Console → 설정 → 결제 프로필. 은행 계좌·세금 정보 입력. 이게 없으면 유료 상품을 만들 수 없습니다.
2. **앱을 Play에 한 번 올리기**: `./gradlew bundleLiveRelease`로 만든 AAB를 업로드 키로 서명해 **내부 테스트** 트랙에 업로드(4번 8항). Play는 업로드된 앱에서만 결제가 됩니다(패키지 `com.yaari.chat`).
3. **상품 만들기**(ID가 코드와 정확히 같아야 함):
   - 구독 `yaari_premium_monthly`
   - 인앱 상품 `gender_pass_24h` (앱이 소비 → 다시 구매 가능)
   - 인앱 상품 `handle_change` — **아이디 변경권**(0.16): 30일이 안 지나도 아이디를 1번 바꿀 수 있음. 앱이 소비 → 다시 구매 가능. 제안가 ₹49
   - 인앱 상품 꾸미기 44개(아래 4-0)
   - 선물용 인앱 상품 `gift_<꾸미기 ID>`(예: `gift_frame_fire`) — **소모성**(앱이 소비 → 여러 번 선물 가능). 친구에게 꾸미기를 선물할 때 쓰며, 앱(안드로이드)에 선물 기능을 옮긴 뒤 만들면 됩니다(066).
4. **서비스 계정**: Google Cloud에서 서비스 계정 + JSON 키 생성 → Play Console → 사용자 및 권한 → 그 이메일 초대, 권한 **재무 데이터 보기 · 주문 및 구독 관리**. JSON 전체를 Supabase → Edge Functions → Secrets `GOOGLE_SERVICE_ACCOUNT_JSON`에 붙여넣기. (권한이 적용되기까지 몇 시간 이상 걸리는 경우가 있으니, 바로 안 되면 다음 날 다시 시험)
5. **라이선스 테스트**: Play Console → 설정 → 라이선스 테스트에 테스트할 Gmail 추가 → 그 계정은 실제 돈이 안 나가는 테스트 카드로 결제됩니다. 내부 테스트 링크로 앱을 받아 구매 → 앱에 바로 반영되면 성공.
6. **환불 자동 처리(RTDN)**: 아래 4-1.
7. 확인: 서버가 켜졌는지는 제가 다시 호출해 `billing_not_configured`가 사라졌는지로 확인할 수 있습니다.

참고: 웹사이트에는 결제가 없습니다(안드로이드 앱 안의 디지털 상품은 Play 결제를 써야 하는 정책). 웹 결제(Razorpay·UPI 등)는 원하시면 따로 붙일 수 있으나 별도 사업자·PG 계약이 필요합니다.

### 4-0. 꾸미기 상품 44개 등록 (040·046·062·067) — Play Console → 수익 창출 → 인앱 상품
서버(마이그레이션 040·041, verify-purchase v11)는 준비 완료. **상품 ID가 아래와 정확히 같아야** 앱이 찾습니다. 종류는 "인앱 상품"(구독 아님), 한 번 사면 계속 보유(앱이 소비하지 않음). 가격은 아래가 제안가(₹)이며 Play에서 정한 가격이 앱에 그대로 표시됩니다. **나라별 가격(53):** Play Console → 상품 → 가격 설정에서 "다른 국가 가격을 환율로 자동 설정"을 켜고, 한국(₩)·미국($)·일본(¥) 등 주요국은 끝자리를 직접 다듬으세요(예: ₩1,200). 웹은 결제가 없어 `lib/currency.ts`의 대략 환율로 "≈ ₩1,300"처럼만 보여줍니다.
- 배지: `badge_crown` 99, `badge_diamond` 149, `badge_flame` 79, `badge_star` 79, `badge_heart` 79, `badge_bolt` 79
- 아이디 색: `color_gold` `color_rose` `color_ocean` `color_emerald` `color_violet` 각 49, `color_sunset` 99, `color_aurora` 99, `color_rainbow` 129
- 아이디 글꼴: `font_script` `font_retro` `font_bold` `font_pixel` `font_round` `font_future` 각 69
- 프로필 테두리: `frame_frost` 199, `frame_fire` 199, `frame_jewel` 249, `frame_sand` 199, `frame_neon` 149, `frame_sakura` 149
- 채팅 배경(067, 움직이는 배경): `wall_aurora` `wall_neon` `wall_sakura` `wall_galaxy` `wall_ocean` `wall_lava` `wall_rain` `wall_diya` 각 79
- VIP 모드 `vip_anon` 299, 사진 그라데이션 `cycle_photos` 199, ~~사진 효과 `effect_aurora` `effect_prism`~~ (2026-10-28 삭제: Play 콘솔에 등록하지 마세요)
- 말풍선: `bubble_peach` `bubble_ocean` `bubble_mint` 각 99, `bubble_galaxy` `bubble_gold` `bubble_neon` 각 149
- 테스트로 직접 주기(결제 없이): SQL Editor에서 운영자 계정으로 `select public.admin_grant_cosmetic('<사용자 uuid>', 'frame_fire');` (조치 기록에 남음). 웹은 결제가 없어 "앱에서 구매" 링크만 보이고, 산 꾸미기는 웹에서도 착용됩니다.

### 4-1. 환불·취소 자동 처리(RTDN) — Play Console이 생긴 뒤 (서버 쪽은 준비 완료, 비밀값만 넣으면 켜짐)

환불되거나 취소된 구매는 즉시 Plus/성별 패스를 끝냅니다 (edge function `play-rtdn`, 마이그레이션 025).
비밀값이 없으면 `play-rtdn`은 모든 호출을 거절(401)하고, 매일 새벽 점검도 아무것도 하지 않습니다.

1. **Google Cloud 콘솔**(Play Console과 연결된 프로젝트) → Pub/Sub → **주제(Topic) 만들기**: 예 `yaari-play-rtdn`.
   주제의 권한에 `google-play-developer-notifications@system.gserviceaccount.com` 을 **Pub/Sub 게시자(Publisher)** 로 추가.
2. 같은 주제에 **구독(Subscription) 만들기** → 전송 유형 **푸시(Push)** →
   엔드포인트 `https://dgynsckrqgnpyyvqekzi.supabase.co/functions/v1/play-rtdn` →
   **인증 사용(Enable authentication)** 체크 → 서비스 계정 하나 선택(새로 만들어도 됨) → 대상(Audience)은 위 엔드포인트 주소 그대로.
3. Supabase 대시보드 → Edge Functions → Secrets:
   - `PLAY_RTDN_AUDIENCE` = 2번의 대상(엔드포인트 주소)
   - `PLAY_RTDN_SA_EMAIL` = 2번에서 고른 서비스 계정 이메일 (선택이지만 권장)
   - (OIDC가 어렵다면 대신) `PLAY_RTDN_TOKEN` = 16자 이상 임의 문자열, 엔드포인트를 `...play-rtdn?token=<그 값>` 으로. 덜 안전하므로 OIDC 권장.
4. **Play Console → 수익 창출 설정(Monetization setup) → 실시간 개발자 알림** → 주제 이름 `projects/<프로젝트ID>/topics/yaari-play-rtdn` 입력 →
   **테스트 알림 보내기** → Supabase Edge Functions 로그에서 `play-rtdn` 200 확인.
5. 매일 점검(놓친 알림 보충, pg_cron `yaari-play-voided`, 04:41 UTC): Play의 **Voided Purchases API** 를 읽습니다.
   Secret `PLAY_SERVICE_ACCOUNT`(또는 이미 넣은 `GOOGLE_SERVICE_ACCOUNT_JSON`)의 서비스 계정에 Play Console에서 **재무 데이터 보기** 권한이 있어야 합니다.

### 4-2. 휴대폰 푸시 알림(FCM) — Firebase 계정이 생긴 뒤 (서버 쪽은 준비 완료)

친구 메시지·친구 요청/수락·방 초대가 앱 안 알림함(웹은 이미 사용 중)에 쌓이고, 휴대폰 토큰이 등록된 사람에게는 푸시도 갑니다
(마이그레이션 023, edge function `push`). 규칙: 앱을 열고 있으면(60초 안) 안 보냄, 같은 대화는 30초에 1번, 현지 시각 23~08시는
친구 요청만. 푸시 내용은 종류·ID·보낸 사람 이름뿐(메시지 내용 없음) — 문장은 앱이 각 언어로 만듭니다.
지금은 등록된 토큰이 0개라 아무것도 나가지 않고, 비밀값이 없으면 `push`는 `{skipped:true}`만 돌려줍니다.

1. **Firebase 콘솔** → 프로젝트 추가(위 Google Cloud 프로젝트를 그대로 써도 됨) → Android 앱 추가: 패키지 `com.yaari.chat` →
   `google-services.json` 다운로드. 앱은 google-services 플러그인을 쓰지 않으므로 이 파일은 저장소에 넣지 않고, 값 4개만 옮깁니다.
2. 프로젝트 설정 → 서비스 계정 → **새 비공개 키 생성** → 받은 JSON 파일 내용 전체를 Supabase Secret **`FCM_SERVICE_ACCOUNT`** 에 붙여넣기.
3. **안드로이드 앱(0.9.0부터 준비 완료)**: `google-services.json`의 값을 `chat/gradle.properties`(또는 `~/.gradle/gradle.properties`,
   CI라면 같은 이름의 환경 변수)에 적고 다시 빌드합니다. 값이 하나라도 비어 있으면 푸시 코드는 아예 돌지 않습니다(토큰 등록 없음).
   ```
   FIREBASE_APP_ID=      # client[0].client_info.mobilesdk_app_id  (예: 1:1234567890:android:abc123...)
   FIREBASE_API_KEY=     # client[0].api_key[0].current_key
   FIREBASE_PROJECT_ID=  # project_info.project_id
   FIREBASE_SENDER_ID=   # project_info.project_number
   ```
   그 뒤 앱이 하는 일: 로그인 후 토큰을 `register_device(token, 'android', 언어, 시간대)`로 등록, 로그아웃 때 `unregister_device`,
   첫 친구가 생긴 뒤 한 번만 알림 권한 요청(Android 13+), 채널 3개(messages / social / rooms), 알림 탭 → 그 대화방/방(초대)으로 이동.
4. **방 링크로 앱 열기(App Links)**: 앱은 `https://yaari-web-indol.vercel.app/r/<방 id 또는 코드>` 를 받도록 등록돼 있지만
   (`autoVerify=false`), 지금은 링크를 누르면 "어떤 앱으로 열기" 선택창이 뜹니다. 선택창 없이 바로 열리게 하려면 출시 서명 키가 생긴 뒤:
   Play Console → 설정 → **앱 무결성 → 앱 서명 키 인증서의 SHA-256** 복사 → 웹에 `web/public/.well-known/assetlinks.json` 추가
   (`[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"com.yaari.chat","sha256_cert_fingerprints":["<SHA-256>"]}}]`)
   → 배포 → 앱의 `live/AndroidManifest.xml` 에서 `autoVerify="true"` 로 바꿔 다음 버전 출시.
5. 확인: SQL Editor에서 `select * from net._http_response order by id desc limit 5;` → `push` 응답이 `{"sent":1,...}`.

---

### 4-3. 본인인증(신분증 + 셀피) — Didit 계정이 생긴 뒤 (서버 쪽은 준비 완료)
전화번호 1개 = 계정 1개는 이미 적용돼 있고, 이 단계는 **사람 1명 = 계정 1개**와 성별·나이·국가 위조 방지입니다.
신분증 촬영 + 실물 셀피(라이브니스) + 얼굴 대조를 Didit의 호스팅 화면에서 진행합니다(220여 개국 신분증, 여권 지원).
1. https://didit.me 가입 → Console에서 **Workflow** 생성: ID Verification + Liveness + Face Match 켜기 → Workflow ID 복사.
2. Console → API & Webhooks → **API key** 복사. Webhook URL: `https://dgynsckrqgnpyyvqekzi.supabase.co/functions/v1/kyc`
3. Supabase 대시보드 → Edge Functions → Secrets: `DIDIT_API_KEY`, `DIDIT_WORKFLOW_ID` 입력. (그 전까지 앱은 "곧 제공"으로 안내)
4. 실제 몇 명이 인증해 본 뒤, 모든 기능을 인증한 사람만 쓰게 하려면 SQL Editor에서:
   `update public.app_flags set require_verification = true where id = 1;`
   (끄기: `false`. 켜면 미인증자는 둘러보기만 가능하고 매칭·방·글·댓글·친구 요청이 막힙니다.)
- 서버가 보관하는 것: 성별, 생년월일, 국적·발급국, 문서번호의 비밀 해시(같은 신분증 재사용 차단용). 이름·사진은 보관하지 않음.
- 개인정보처리방침(docs/privacy.html)에 "신분증 확인(처리 위탁: Didit)" 항목을 추가해야 합니다(DPDP 2023: 목적 명시·동의·최소 수집).
- 인도에서 Aadhaar 번호로 인증을 강제하는 것은 법적으로 제한되므로, 여권·운전면허·PAN 등 다른 신분증도 받는 현재 방식을 유지하세요.
- 테스트 계정 2개는 관리자 인증 처리됨(admin_verify).

## 5. 국가별 법적 필수 사항 (MVP 단계)

### 인도 (최우선)
- **IT Rules 2021 (2026-02 개정 시행)**: 고충처리관 이름·연락처 공개, 접수 24시간 내 확인·**7일 내 처리**, 사적 이미지(NCII) 신고 **2시간 내 삭제**, 정부·법원 명령 **3시간 내 이행**, 3개월마다 이용자에게 규칙 고지. (개정 내용은 법률사무소 요약 기준)
- **DPDP Act 2023**: 핵심 의무(고지·동의·유출 통지·아동 데이터)는 **2027-05-13 시행**. 18세 미만 = 아동 → 18+ 차단 유지가 중요.
- **SIM 바인딩 지침(DoT)**: 대형 메신저 대상, 2026년 12월 기한. 소규모 신규 앱 적용 여부 미확인 → 모니터링.
- 앱 차단 위험: 정부가 IT Act 69A로 앱을 차단한 전례 있음(주로 중국 관련). 신고·모더레이션 체계가 방어 수단.

### 방글라데시
- 사이버보안법은 2025년 조례로 대체 후 2026년 개정 보도. 개인정보보호법(2026) 시행 여부 **미확인** — 출시 전 재확인.
- Google Play 결제수단: 카드만 확인됨(bKash 미확인) → 결제 전환율이 낮을 수 있음.

### 브라질
- **LGPD**: 유출 시 3영업일 내 통지. 랜덤 영상채팅은 고위험 처리로 볼 여지 → 개인정보 보호책임자(encarregado) 지정 검토.
- **Marco Civil Art.15**: 영리 서비스는 **접속 기록 6개월 보관** 의무 → 서버에 접속 로그 테이블 추가 필요(현재 Supabase 로그는 보관 기간이 짧음).
- **ECA Digital (2026-03-17 시행)**: 미성년자가 접근할 가능성이 있는 서비스에 신뢰할 수 있는 연령 확인 요구 → 18+ 앱도 Google Play Age Signals 연동 검토.
- 소비자법: 온라인 계약 7일 철회권.
- 결제: Pix(자동갱신 구독 가능) 등 Play 결제수단 풍부.

### 베트남
- **개인정보보호법(2026-01-01 시행)**: 검증 가능한 동의, 처리영향평가·국외이전영향평가를 처리 시작 후 60일 내 제출.
- **Decree 147/2024**: 6개월 평균 월 10만 방문 이상이면 베트남 전화번호 인증·연락처 통지 등 추가 의무. 당국 요청 시 24시간 내 삭제.
- 결제: MoMo, ZaloPay, 통신사 결제 등 Play에서 지원.

---

## 6. 이번에 점검·수정한 것 (보안·속도·경량화)

**서버 보안 감사 → 적용 완료** (`server/migrations/009`, `010`, 실서버 롤백 테스트 통과)
- 신고는 자동 정지 없이 운영자가 `/admin`에서 대화 기록을 보고 판단(060). 신고는 1인 하루 20건 제한.
- 공개방 대화 기록을 방에 없는 사람이 통째로 가져가는 문제 차단.
- 남의 사진 경로를 이용해 비공개 사진을 다시 보는 문제 차단, 업로드 폴더 제한.
- 늦게 만들어진 테이블에 남아 있던 과도한 권한 회수, 동시 요청으로 도배 제한을 넘는 문제 차단.
- 신고 없이 차단 기능 추가.

**사장님 승인이 필요해 보류한 서버 작업** (데이터를 지우는 작업이라 별도 승인 필요)
- 오래된 데이터 자동 삭제(보관 기간 정책): 공개방 메시지 30일 등 — 개인정보법 대응.
- 신고 기록이 탈퇴 시 함께 지워지지 않게 변경(재가입 악용 방지).
- 사용자가 자기 사진 파일을 지울 수 있게 하기, 1인당 업로드 용량 제한.

**앱 (Android 0.3.0)**
- 디자인: 단색 브랜드 컬러 하나(흰 글자 대비 4.65:1), 반짝임·그림자·색종이 효과 제거, Material 3 하단 탭, 터치 영역 48dp 이상.
- 백그라운드에서는 매칭·채팅·방 새로고침을 멈춤(배터리·데이터 절약), 돌아오면 즉시 재개.
- 릴리스 빌드에서 로그 제거, HTTPS만 허용, 백업에서 로그인 정보 제외, 채팅·영상·프로필 화면 캡처 차단.
- 글꼴을 라틴+베트남어만 남겨 645KB → 160KB, 저사양 폰에서 이미지 메모리 축소, 시작 속도 프로필 추가.

**웹**
- 처음 받는 자바스크립트를 쪼개서 영상통화(139KB)·데모·각 화면은 필요할 때만 받음.
- 보안 헤더(CSP, HSTS, 프레임 금지, 카메라·마이크는 이 사이트만) 적용, 인라인 스크립트는 해시로 고정.
- 탭이 숨겨지면 새로고침·방 출석 신호를 멈추고, 매칭은 60초 숨겨지면 취소.

---

## 7. 측정 결과

**앱 설치 파일** (`dist/`, 0.2.0 → 0.3.0)

| 파일 | 이전 | 지금 |
|---|---|---|
| 실서버 arm64 (대부분의 폰) | 17.5MB | 9.95MB (−43%) |
| 실서버 armv7 (오래된 32비트 폰, 신규) | 11.8MB | 8.23MB (−30%) |
| 데모 | 2.0MB | 1.83MB |
| Play 업로드용 AAB | 33.4MB | 33.2MB (Play가 폰별로 쪼개서 내려줌) |

**웹 첫 화면** (데모판 가입 화면, 휴대폰 화면, gzip, 캐시 없음)

| | 이전 | 지금 |
|---|---|---|
| 첫 로딩 자바스크립트 | 301KB | 218KB (실서버판은 Supabase 포함 278KB) |
| 느린 3G + 저사양 CPU: 첫 화면 표시 | 8.1초 | 2.1초 |
| 느린 3G + 저사양 CPU: 번호 입력 가능 | 8.9초 | 7.2초 |
| Lighthouse 모바일 (성능/접근성/권장사항/SEO) | 78/100/96/100 | 96/100/100/100 |

측정하지 못한 것: 실제 폰 앱 시작 시간(이 환경에 에뮬레이터·기기가 없음), Play에서 폰별 다운로드 크기.

---

## 8. MVP 이후

- 인도 DPDP 2027-05 전 동의·고지 정비, 사용자 500만 명 근접 시 인도 거주 책임자 3명(SSMI).
- 베트남 월 10만 방문 전후 전화번호 인증·연락처 통지.
- 웹 결제(현지 결제 게이트웨이 — 대부분 현지 법인·KYC 필요), Galaxy Store·Indus Appstore 추가 배포.
- 친구 접속 상태, 웹 푸시(Web Push). (모더레이션 콘솔은 /admin, 푸시 서버는 4-2절 — 완료.)

---

## 출처

- Google Play 신규 개인 계정 테스트 요건: https://support.google.com/googleplay/android-developer/answer/14151465
- 계정 유형: https://support.google.com/googleplay/android-developer/answer/13634885
- UGC 정책: https://support.google.com/googleplay/android-developer/answer/9876937
- 성적 콘텐츠 정책: https://support.google.com/googleplay/android-developer/answer/9878810
- 아동안전 표준(CSAE): https://support.google.com/googleplay/android-developer/answer/14747720
- 타깃 연령: https://support.google.com/googleplay/android-developer/answer/9867159
- 콘텐츠 등급: https://support.google.com/googleplay/android-developer/answer/9859655
- 데이터 보안: https://support.google.com/googleplay/android-developer/answer/10787469
- 계정 삭제: https://support.google.com/googleplay/android-developer/answer/13327111
- 사진·동영상 권한: https://support.google.com/googleplay/android-developer/answer/14115180
- 결제 정책: https://support.google.com/googleplay/android-developer/answer/9858738 · 사용자 선택 결제: https://support.google.com/googleplay/android-developer/answer/13821247 · 수수료: https://support.google.com/googleplay/android-developer/answer/112622
- Android 개발자 인증: https://developer.android.com/developer-verification/guides
- 인도 IT Rules 2026 개정(요약): https://www.hoganlovells.com/en/publications/india-introduces-mandatory-labelling-for-ai-and-3hour-takedown-for-illegal-content
- 인도 DPDP Rules: https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014
- Twilio 인도 SMS 가이드(DLT): https://www.twilio.com/en-us/guidelines/in/sms · 베트남: https://www.twilio.com/en-us/guidelines/vn/sms · 방글라데시: https://www.twilio.com/en-us/guidelines/bd/sms
- Twilio SMS 요금: https://www.twilio.com/en-us/sms/pricing/in (국가 코드만 바꿔 br, vn) · https://www.twilio.com/pt-br/sms/pricing/bd
- WhatsApp 요금: https://developers.facebook.com/docs/whatsapp/pricing
- Supabase 전화 로그인: https://supabase.com/docs/guides/auth/phone-login · 요금: https://supabase.com/docs/guides/platform/billing-on-supabase · 일시정지: https://supabase.com/docs/guides/platform/free-project-pausing
- Vercel 요금: https://vercel.com/pricing · LiveKit 요금: https://livekit.com/pricing
- Google Vision 요금: https://cloud.google.com/vision/pricing · AWS Rekognition: https://aws.amazon.com/rekognition/pricing/
- 브라질 Marco Civil Art.15: https://modeloinicial.com.br/lei/27460/marco-civil-internet/art-15 · ECA Digital: https://www.demarest.com.br/eca-digital-entrada-em-vigor-em-17-de-marco-de-2026/
- 베트남 개인정보보호법: https://www.tilleke.com/insights/vietnams-new-personal-data-protection-law-a-closer-look/2/ · Decree 147: https://luatvietnam.vn/legal-updates/social-network-user-accounts-to-be-verified-with-subscriber-phone-numbers-892-100003-article.html
- Play 결제수단(국가별): https://support.google.com/googleplay/answer/2651410
