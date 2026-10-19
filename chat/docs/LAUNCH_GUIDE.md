# Yaari 상용화 가이드 (MVP · 인도/방글라데시/브라질/베트남)

기준일 2026-10-19. 각 항목의 근거 URL은 맨 아래 "출처"에 있습니다. "미확인"이라고 쓴 항목은 공식 출처로 확인하지 못한 것이니 출시 전에 직접 다시 확인하세요. 법률 자문이 아니므로 유료 출시 전에 현지 변호사 검토를 한 번 받는 것을 권합니다.

---

## 0. 한눈에 보는 출시 순서

| 단계 | 기간(대략) | 할 일 | 누가 |
|---|---|---|---|
| 1 | 1일 | Google Play 개발자 계정(US$25, 1회), Supabase Pro, Vercel Pro 결제 | 사장님 |
| 2 | 1~2일 | SMS/WhatsApp 인증 연결(Twilio), 테스트 번호 삭제, 이메일 로그인 끄기 | 사장님 (설정 방법은 아래) |
| 3 | 1주 | 남은 필수 기능: 영상 노출 자동 감지, 아동안전(CSAE)·이용약관·계정삭제 웹페이지 | 개발 |
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
| **신고 없이 차단** (Play UGC 정책: 1:1 채팅이 있으면 차단 필수) | 🔧 서버 완료, 앱·웹 버튼 작업 중 |
| 이용약관 동의 후 이용 | 🔶 가입 시 커뮤니티 규칙 동의는 있음 → **이용약관 웹페이지 필요** |
| 받은 사진·영상 가림 (탭해야 보임) | ✅ 완료 |
| **실시간 영상 노출 자동 감지** (랜덤 영상채팅은 사실상 필수) | ❌ 미구현 — 출시 전 최우선 |
| **아동안전(CSAE) 기준 공개 웹페이지 + 담당자** (랜덤채팅·소셜 앱은 18+여도 적용) | ❌ 페이지 작성 예정, 담당자는 사장님 지정 |
| 앱 안 계정 삭제 | ✅ 완료 |
| **웹에서 계정 삭제 요청 경로** | 🔶 웹사이트에서 로그인 후 삭제 가능 → 안내 페이지 필요 |
| 개인정보처리방침 | ✅ 있음 (담당자 실명·연락처 입력 필요) |
| 사진 선택: Photo Picker (광범위 사진 권한 금지) | 🔧 앱 점검 중 |
| 구독은 Google Play 결제만 | ✅ 완료 (웹은 결제 없이 앱 구독을 공유) |
| 인도 고충처리관 공개 | 🔶 화면은 있음, **실명·이메일 입력 필요** |
| 언어: 힌디어·벵골어·포르투갈어·베트남어 | 🔧 작업 중 (출시 전 현지인 검수 권장) |
| 보안 점검 | ✅ 서버 감사 완료, 고위험 항목 수정·적용 (아래 6절) |

---

## 3. 로그인(전화 인증) 연결 — 사장님 작업

1. Twilio 가입 → **Verify 서비스** 생성 (WhatsApp 채널 사용 시 Meta 비즈니스 인증 필요).
2. Supabase 대시보드 → Authentication → Sign In / Providers → **Phone** → Provider: **Twilio Verify** → Account SID / Auth Token / Verify Service SID 입력.
3. 같은 화면의 **Test phone numbers(919000000001/2, 코드 123456) 삭제** — 그대로 두면 누구나 그 계정으로 로그인됩니다 (보안 감사 '치명' 항목).
4. Authentication → Providers → **Email 끄기** (지금은 테스트 봇용으로 켜져 있음. 켜 두면 전화번호 없이 계정을 무한히 만들 수 있음). 테스트 봇(`server/tools/partner_bot.ts`)도 종료.
5. Authentication → Attack Protection → **CAPTCHA(hCaptcha/Turnstile) 켜기**, SMS 발송 국가를 4개국으로 제한 (SMS 요금 폭탄 공격 방지).
6. 인도: 비용이 가장 싼 **WhatsApp 인증**으로 시작하고, SMS가 필요하면 TRAI **DLT 등록**(사업자 PAN 필요, 약 10영업일) 후 국내 경로 사용. 해외 법인의 DLT 직접 등록 가능 여부는 미확인.
7. 베트남·방글라데시 SMS: 발신자 ID 사전등록 필수(베트남 약 5주·사업자등록증, 방글라데시 약 3주).

---

## 4. Google Play 출시 — 사장님 작업 (Play Console)

1. **계정**: 법인이 있으면 조직 계정(D-U-N-S 번호 무료 발급). 개인 계정이면 **테스터 12명·14일 비공개 테스트** 필수 — 지금 바로 지인 12명을 모으세요.
2. **타깃 연령: 18+만 선택** → "미성년자 접근 제한" 켜기. **콘텐츠 등급(IARC) 설문** 정확히 작성(영상 채팅, 사용자 상호작용, 사용자 생성 콘텐츠 = 예).
3. **앱 카테고리**: Social. (Social·Dating·익명/랜덤 채팅은 아동안전 표준 정책 대상)
4. **아동안전 표준**: 공개 웹페이지 링크 + CSAM 대응 담당자 연락처 등록.
5. **데이터 보안 양식**: 수집 항목 = 전화번호, 생년월일, 성별, 사진·동영상, 메시지, 기기 ID(없으면 제외), 구매 기록. 공유 = 없음(처리 위탁: Supabase·LiveKit·Twilio). 전송 중 암호화 = 예. 삭제 요청 = 예(앱 + 웹 링크).
6. **계정 삭제 웹 링크**, **개인정보처리방침 URL** 등록.
7. 상품 2개: 구독 `yaari_premium_monthly`, 소모성 `gender_pass_24h`. 서비스 계정 키를 Supabase Secret `GOOGLE_SERVICE_ACCOUNT_JSON`에 입력(README 참고).
8. **서명 키**: 지금 APK는 디버그 키 서명. Play에는 `./gradlew bundleLiveRelease`로 만든 AAB를 업로드 키로 서명해 올립니다(README "upload.properties").
9. 국가: 인도 → 방글라데시 → 브라질 → 베트남 순으로 열기. 브라질은 2026-09-30부터 **Android 개발자 인증**(Play 밖 배포 포함) 시행.

---

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
- 가짜 계정 3개로 아무나 7일 정지시키는 신고 악용 차단(하루 20건 제한, 가입 3일 이상 계정만 자동정지에 반영).
- 공개방 대화 기록을 방에 없는 사람이 통째로 가져가는 문제 차단.
- 남의 사진 경로를 이용해 비공개 사진을 다시 보는 문제 차단, 업로드 폴더 제한.
- 늦게 만들어진 테이블에 남아 있던 과도한 권한 회수, 동시 요청으로 도배 제한을 넘는 문제 차단.
- 신고 없이 차단 기능 추가.

**사장님 승인이 필요해 보류한 서버 작업** (데이터를 지우는 작업이라 별도 승인 필요)
- 오래된 데이터 자동 삭제(보관 기간 정책): 공개방 메시지 30일 등 — 개인정보법 대응.
- 신고 기록이 탈퇴 시 함께 지워지지 않게 변경(재가입 악용 방지).
- 사용자가 자기 사진 파일을 지울 수 있게 하기, 1인당 업로드 용량 제한.

**앱·웹 경량화와 속도** — 7절에 결과 기록 예정 (작업 중).

---

## 7. 측정 결과

(앱·웹 작업이 끝나면 APK 크기, 첫 로딩 용량, 3G 로딩 시간을 여기에 기록합니다.)

---

## 8. MVP 이후

- 인도 DPDP 2027-05 전 동의·고지 정비, 사용자 500만 명 근접 시 인도 거주 책임자 3명(SSMI).
- 베트남 월 10만 방문 전후 전화번호 인증·연락처 통지.
- 웹 결제(현지 결제 게이트웨이 — 대부분 현지 법인·KYC 필요), Galaxy Store·Indus Appstore 추가 배포.
- 사람 검수 모더레이션 도구(신고 큐 화면), 친구 접속 상태, 푸시 알림.

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
