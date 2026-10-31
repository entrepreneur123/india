# Yaari 웹 출시 필수요소 체크리스트 (무료 플랜 기준) — 2026-10-31

사장님의 "무료 환경 기반 글로벌 랜덤채팅 웹사이트 완성" 지시에 따라 실제 사이트(https://yaari-web-indol.vercel.app)와 코드·DB를 직접 확인한 결과다.
[확인] = 직접 실행·조회해 본 사실, [추정] = 코드만 읽고 판단. 구조·용량·요금제 사실은 `DIAGNOSIS.md`, `SCALE_UP.md`, `CAPACITY.md`에 있고 여기엔 반복하지 않는다.

## 0. 한 줄 요약
코드·보안·운영 기반은 갖춰졌다. **지금 당장 실제 사람이 쓰지 못하는 이유는 하나 — 로그인이다.** 전화번호 인증(OTP)이 Twilio 미가입 상태라
테스트 번호 외엔 문자가 가지 않는다 [확인: 임의 번호로 OTP 요청 → `auth account AC000… does not exist` (Twilio 20003)]. 무료로 바로 열 수 있는 길은
Supabase 익명(게스트) 로그인이며, 현재 꺼져 있다 [확인: `signInAnonymously` → "Anonymous sign-ins are disabled"].

## 1. 실제 기술 구조 (확인)
- 프론트: Next.js 16 정적 내보내기(`chat/web`, `output: export`) → Vercel Hobby가 CDN으로 서빙. 서버리스 함수 없음(API 라우트 0, Vercel 함수 0). 긴 연결(WebSocket/WebRTC)은 브라우저 ↔ Supabase Realtime / LiveKit / 상대 브라우저로 직접 — 서버리스 함수에 연결을 들고 있는 구조가 아니다.
- 백엔드: Supabase(ap-south-1, Free) — Auth(전화 OTP), Postgres(마이그레이션 001~122, 모든 쓰기는 RPC `security definer` 함수), Realtime Broadcast(private topic: `user:`, `chat:`, `call:`, `typing-`, `mega:`), Storage(media, 비공개), Edge Functions 13개(video-token, voice-sync, link-meta, kyc, play, purge-media…), pg_cron 7개.
- 매칭: `find_match4` — 사용자별 advisory lock + 대기열 `for update skip locked` + "이미 짝지어졌나" 선검사 → 경쟁 상태·중복 매칭 방지는 서버가 한다(클라이언트 상태만으로 매칭되는 경로 없음). 취소·재매칭·타임아웃·이탈: 클라이언트 2초 폴·12초 상대 부재 종료 + 119 청소부(벨 60초, 양쪽 5분 부재 매치 종료). 네트워크 단절 복구: 재접속 시 match row·메시지 resync.
- 채팅: 메시지는 DB 저장(랜덤챗 30일 뒤 삭제, 친구 스레드는 유지), Broadcast로 즉시 전달, 중복은 id 기준, 읽음 표시, 신고·차단·속도 제한(10초 10건).
- 음성: 방 = LiveKit Cloud SFU(Build 무료: 동시 100, 월 5,000분) · 1:1 통화 = P2P(STUN만) → 실패 시 LiveKit로 fallback. TURN 서버는 따로 없음(아래 §7).
- 인증·권한: 전화 OTP만. RLS 전 테이블(57개) 적용, 클라이언트는 anon 키만(Vercel 환경변수 4개 모두 공개값 [확인]), service role 키는 Edge Function 환경에만. 관리자는 `admins` 테이블 + `/admin`.

## 2. 필수요소 점검표
| 영역 | 항목 | 상태 | 근거 |
|---|---|---|---|
| 인증 | 회원가입/로그인/로그아웃 | ⚠ 코드 완성, **실사용 불가**(SMS 미설정) | OTP 실패 재현 [확인] |
| 인증 | 게스트 | ✗ 없음 → **이번에 구현**(아래 §3) | Supabase 익명 로그인 꺼짐 [확인] |
| 인증 | 세션 유지/만료 | ✓ supabase-js 자동 갱신, 만료 시 로그인 화면 | 코드 [확인] |
| 인증 | 차단·신고·탈퇴·개인정보 삭제 | ✓ `block_user`·`report_user`(사람 검토, 대화 기록 첨부)·`delete-account` 함수·`/delete-account` 안내 페이지 | 코드·페이지 [확인] |
| 매칭 | 대기열·상태·중복 방지·취소·재매칭·타임아웃·이탈·복구·악용 방지 | ✓ (119·120으로 보강) | §1 |
| 채팅 | 송수신·방 생성/종료·중복 방지·복구·상대 상태·권한·신고/차단/스팸 | ✓ | §1 |
| 음성 | 시그널링·STUN·마이크 거부 처리·음소거/퇴장·재연결·방장 권한·다수 참가 | ✓ (마이크 거부: 통화 `video.micDenied` 안내, 방 `mic_denied` 오류→토스트) · TURN 없음 | 코드 [확인] |
| 페이지 | 첫 화면·소개 | ⚠ `/hi` 등 35개 언어 랜딩은 정적 HTML에 내용 있음, **영어 `/`는 JS 전엔 "Loading"만** → 이번에 보완 | curl [확인] |
| 페이지 | 로딩·빈 화면·오류·오프라인·재접속 | ✓ Splash, OfflineBanner, boot 'offline' 재시도, 화면 오류 경계 | 코드 [확인] |
| 페이지 | 모바일·반응형 | ✓ 360/390/412 감사(S2), 1280/1440/1920(N4) | 이전 세션 |
| 페이지 | 뒤로가기·새로고침·탭 종료 | ✓ useBack, 세션 복구, pagehide 비콘(presence·leave_voice·cancel_search) | 119 [확인] |
| 법적 | 개인정보처리방침·이용약관·아동안전·계정삭제 | ✓ `/privacy` `/terms` `/child-safety` `/delete-account` (200) | curl [확인] |
| 법적 | 커뮤니티 가이드라인·신고/문의·운영자 연락처·연령 정책 | ✓ 약관 §Community rules, 앱 Settings의 규칙 시트·grievance/support 메일, 18+ 게이트 | 코드·페이지 [확인] |
| 법적 | 고충처리인 실명·이메일 | ✗ `grievance@yaari.app` 자리표시(도메인 미보유) → **사장님**(YAA-7) | 페이지 [확인] |
| 배포/SEO | HTTPS·보안 헤더 | ✓ HSTS preload, CSP, X-Frame DENY, nosniff, Referrer, Permissions-Policy, COOP | 헤더 [확인] |
| 배포/SEO | 제목·설명·OG·twitter | ✓ (og:image 없음 → 이번에 추가) | HTML [확인] |
| 배포/SEO | 파비콘·아이콘·매니페스트 | ✗ `/favicon.ico`·PNG·manifest 404(SVG만) → **이번에 추가** | curl [확인] |
| 배포/SEO | robots·sitemap·색인 | ✓ robots(/admin 차단)·sitemap 36개 언어·hreflang; 채팅·프로필은 URL이 없어 색인 불가 | curl [확인] |
| 배포/SEO | 404 | ✓ 404 상태 코드 + 앱 404 화면 | curl [확인] |
| 배포/SEO | 자체 도메인 | ⏸ 가능(Vercel Pro 첫해 무료 도메인; Hobby는 비상업 약관) → 결제 뒤(SCALE_UP §6) | 문서 |
| 배포/SEO | 배포 실패·환경변수 | ✓ CI(typecheck·단위 108·빌드), Vercel 환경변수 4개 확인, 데모 모드 fallback | [확인] |
| 보안 | RLS·권한·API 제한·스팸/봇·방 접근·비밀키·관리자 분리 | ✓ (118~121) · 어드바이저: ERROR 0, WARN 2(설계상 security definer RPC 204개 · 비밀번호 유출 보호=비밀번호 미사용) | 어드바이저 [확인] |
| 보안 | 봇 방지(CAPTCHA) | ✗ 없음 — 게스트 로그인과 함께 Turnstile 권장(무료, Cloudflare 계정 = 사장님, YAA-12) | — |

## 3. 게스트(익명) 로그인 — 장단점과 결정
- 장점: 돈 0, 가입 마찰 0(Omegle 방식), SMS/DLT 없이 오늘 열 수 있다. Supabase가 IP당 시간당 30회로 익명 가입을 제한한다(기본값).
- 단점: 번호 1개=계정 1개 규칙(031/035)이 게스트엔 없다 → 차단당한 사람이 새 게스트로 돌아올 수 있다(차단은 계정 id 기준으로만). 성별·나이는 자기 신고(전화 계정도 KYC 전엔 같다). 익명 계정이 쌓인다(정리 규칙 필요).
- 결정: 게스트를 **스위치로** 넣는다(`app_flags.guests`). 사장님이 Supabase 대시보드 Authentication → Sign In / Providers → "Allow anonymous sign-ins"를 켜고 알려주면 스위치를 켠다. 전화 인증은 그대로 두어 Twilio가 생기면 "인증됨" 배지 경로가 된다.

## 7. 유료가 필요한 것과 무료 대안
| 항목 | 무료로는 | 유료 |
|---|---|---|
| 문자 인증 | 불가(Supabase 기본 SMS 없음) → 게스트/Google 로그인(Google은 사장님의 OAuth 클라이언트 생성 필요, 무료) | Twilio + 인도 DLT |
| TURN | 없음. STUN만으로 P2P가 안 되는 망(대칭 NAT·기업망)은 LiveKit(SFU, 자체 TURN 포함)로 fallback — 이미 구현 | LiveKit 유료 플랜 분량 |
| 업타임 모니터 | Sentry 모니터는 좌석 필요 → UptimeRobot 등 무료 가입(사장님) | Sentry PAYG |
| 도메인 | — | Vercel Pro $20(첫해 도메인 포함) |
| 동시 접속 | Realtime 200·LiveKit 100·DB Micro | Supabase Pro + Small, LiveKit Ship |
