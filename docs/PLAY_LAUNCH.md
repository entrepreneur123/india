# Google Play 출시 키트 (인도)

> 정책 출처는 docs/RESEARCH.md 참고. 법률 자문이 아니므로 출시 전에 직접 확인하세요.

## 1. 앱 이름(제목) — 30자 이내

| 후보 | 길이 | 위험도 | 비고 |
|---|---|---|---|
| **LottoDost – Lottery Results** | 27 | 낮음 | 권장. 정부·상표 명칭을 쓰지 않음 |
| LottoDost – Lottery Result Check | 32 | — | 30자 초과라 불가 |
| Lottery Sambad … | — | **높음** | "Lottery Sambad"는 L S Publications가 상표 출원 중(No. 3743712, 미등록·Objected). 제목에 쓰지 말 것 |
| Nagaland State Lottery … | — | **높음** | 정부 사칭(Misrepresentation) 정책 위반 소지 |

검색 키워드(nagaland state lottery, lottery sambad, dear lottery result, লটারি সংবাদ)는 **설명문 안에서 서술적으로만** 씁니다.

## 2. 짧은 설명 (80자 이내)

글자 수는 코드로 셌습니다(Python `len`, 유니코드 코드포인트 기준).
- en (77자): `Dear lottery results 1, 6 & 8 PM. Scan your ticket, check prizes. Unofficial.`
- hi (75자): `डियर लॉटरी रिज़ल्ट 1, 6, 8 बजे। टिकट स्कैन करें, इनाम जाँचें। अनौपचारिक ऐप।`
- bn (76자): `ডিয়ার লটারি রেজাল্ট 1, 6, 8 PM। টিকিট স্ক্যান করে পুরস্কার মেলান। বেসরকারি।`

## 3. 전체 설명 (영어 원문, Play "정부 정보" 정책 문구 포함)

```
LottoDost shows Dear lottery results minutes after every draw and checks your ticket for you.

• All of today's draws on one screen: 1 PM (Nagaland), 6 PM (Sikkim), 8 PM (Nagaland) and Dear Laxmi
• Scan your ticket or its QR code with the camera or a photo – read on your phone
• Checks every prize rule: full number, consolation, last 5 and last 4 digits
• Bumper and monthly draws, 30+ days of old results
• Save tickets and get an alert when the result is out
• Share the result as an image on WhatsApp
• Works offline, small download, English, हिन्दी, বাংলা, नेपाली, ਪੰਜਾਬੀ, मराठी
• Prize table, game names by day, how to read your ticket and how to claim

IMPORTANT
LottoDost is an independent app. It is NOT affiliated with, endorsed by or representing the Government of
Nagaland, the Government of Sikkim, the Government of Punjab or any lottery department or distributor.
We do not sell lottery tickets, do not take payments and never ask for money.
Results are collected from public sources and should always be verified with the official result before
claiming a prize. Buying lottery tickets is not legal in every Indian state. For adults (18+) only.

Sources of information:
• Directorate of State Lotteries, Government of Nagaland – https://www.nagalandlotteries.com/results.php
• Lottery Sambad result sheets – https://www.lotterysambad.com/
```

## 4. Data safety 답변 (현재 코드 기준)

| 질문 | 답 | 근거 |
|---|---|---|
| 사용자 데이터를 수집하나요? | **예**. ML Kit(Google Play 서비스)이 진단 정보를 수집함 | ML Kit data disclosure: device info, app info, performance metrics, API configuration |
| 수집 데이터 유형 | "App info and performance → Diagnostics", "Device or other IDs" | 같은 문서. "thin"(Play 서비스) 기능은 device identifier를 추가함 |
| FCM 푸시 (Firebase 설정 시) | "Device or other IDs" 수집, 목적 **앱 기능**, 공유 안 함 | Firebase가 설치 ID와 등록 토큰을 만듦. 보내는 메시지에는 개인정보가 없음("새 결과" 신호뿐, 모든 폰에 같은 내용) |
| AdMob 광고 (ADMOB_* 설정 시) | IP 주소(대략적 위치), 앱 상호작용, 진단 정보, 광고 ID·앱 세트 ID를 **수집·공유**, 목적 광고·분석·사기 방지 | Google AdMob Data safety 문서(SDK 25.5.0, 2026-10-19 확인) |
| 공유(제3자 전달)? | **예 (AdMob 사용 시)**. ML Kit·FCM 데이터는 공유 안 함 | |
| 전송 중 암호화? | 예 (HTTPS) | |
| 삭제 요청 방법? | 계정이 없으므로 앱 데이터 삭제 또는 삭제(언인스톨) | |
| 카메라 이미지 | 수집 안 함 (기기 내 처리, 저장·업로드 없음) | ScanScreen: ML Kit 온디바이스 |

> AdMob이 들어갔습니다(ADMOB_APP_ID, ADMOB_BANNER, ADMOB_INTERSTITIAL을 넣어야 실제 광고가 나옴). 콘솔의 "광고 포함"은 **예**로 바꾸고, 위 AdMob 행대로 Data safety를 작성하세요.

## 5. 콘솔 설정 체크리스트

- [ ] 개인정보처리방침 URL: `https://entrepreneur123.github.io/india/privacy.html` (Pages 활성화 후). 연락처 이메일을 채워 넣을 것
- [ ] 타깃 연령: **18세 이상만** 선택. 이렇게 하면 Restrict Minor Access가 적용됨
- [ ] 콘텐츠 등급(IARC) 질문지: 실제 돈 도박 "아니요", 시뮬레이션 도박 "아니요". 복권 결과 정보 제공은 사실대로 서술
- [ ] 광고 포함: **예** (AdMob). AdMob 콘솔 → 차단 관리 → 민감한 카테고리에서 도박·베팅은 기본 차단 상태 유지
- [ ] 정부 앱 여부: "아니요". 설명문에 출처와 비제휴 문구 포함(위 3번)
- [ ] 타깃 API 36 ✅, 64-bit ✅, AAB로 업로드(권장, `./gradlew bundleRelease`)
- [ ] **비공개 테스트 필수 (개인 개발자 계정, 2023-11-13 이후 생성):** 테스터 **최소 12명**이 **2주 연속** 참여한 비공개 테스트를 마쳐야 프로덕션 출시 신청 가능. 중간에 나갔다 다시 들어오면 기간이 다시 시작됨. (Play 고객센터 answer/14151465, 2026-10-19 확인) → 출시 일정에 최소 2주를 잡을 것
- [ ] 앱 서명: Play App Signing + 업로드 키 생성(keystore는 절대 저장소에 커밋하지 말 것)
- [ ] 국가: 인도. 스토어 등록정보 현지화: en-IN, hi-IN, bn-IN, (ne-NP는 인도 리스팅에 안 보일 수 있음), pa-IN, mr-IN
- [ ] 스크린샷: `android/app/build/outputs/roborazzi/` 이미지 활용 가능(최소 2장, 16:9 또는 9:16)

## 6. 수익화 시 주의 (중요)

- AdSense/AdMob에서 **도박 및 베팅 카테고리는 차단 유지**(AdSense는 기본 차단). Play 정책상 도박 광고를 띄우는 앱은 "복권 결과 확인 같은 보조 기능"을 가질 수 없음.
- **도박·온라인 복권·사타(satta) 광고나 링크는 절대 금지.** 온라인게이밍법(2025, 2026-05-01 시행) §6은 온라인 머니게임 광고를 금지하고, Play 정책도 복권 판매로 유도하는 것을 금지합니다.
- AdMob은 "online lottery ticket purchase"를 제한 카테고리로 분류합니다. 결과 앱이 이 카테고리로 분류되는지는 확인되지 않았습니다. 넣는다면 결과 화면 밖에 일반 광고만, 앱을 열 때 나오는 전면광고는 금지하세요(경쟁 앱 1위 불만).
- 대안: 광고 없는 유료 "Pro"(경쟁 앱 리뷰에서 유료 광고 제거 수요가 있음).
