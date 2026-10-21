# 다음 세션 인수인계 (디자인 AI 연결 후)

## 목표 (사장님 요청)
- 모바일 앱과 **웹/PC 버전**을 각각 대기업 수준 디자인으로: 마우스를 올리면 변하는 효과(hover), 기본적으로 살아 움직이는 화면.
- 연결된 디자인 AI로 디자인하고, Claude가 그 결과를 코드로 옮긴다. 사장님에게 디자인 선택을 묻지 말고 끝까지 진행.

## 연결할 커넥터 (claude.ai/customize/connectors)
| 커넥터 | 용도 |
|---|---|
| **v0** (Vercel) | 웹/PC 버전 화면 생성. React + Tailwind + 애니메이션. `createChat`, `sendChatMessage`, `getPreview` |
| **Magic Patterns** | 모바일 화면 디자인 시안 생성·수정. `get_design`, `update_design` |
| **Figma** | 시안 정리, 디자인 토큰(색·글꼴·간격) 읽기. `get_design_context`, `get_variable_defs` |

Vercel 커넥터는 이미 연결되어 있음(웹 배포용).

## 진행 순서
1. v0로 웹 버전 화면 생성. 아래 "디자인 브리프"를 그대로 사용 → 결과 코드를 `chat/web/`에 넣고 Supabase에 연결 → Vercel 배포.
2. Magic Patterns로 모바일 시안 생성 → 같은 디자인 언어로 `chat/app` Compose 화면 교체.
3. 두 버전 모두 같은 서버(Supabase)와 같은 규칙 사용. 서버 규칙은 이미 모두 Postgres에서 강제됨.

## 서버 (이미 완료, 웹에서 그대로 사용)
- Supabase 프로젝트 `dgynsckrqgnpyyvqekzi` (뭄바이). URL `https://dgynsckrqgnpyyvqekzi.supabase.co`, anon key는 `chat/gradle.properties`의 `SUPABASE_ANON_KEY`.
- 로그인: 전화 OTP. 테스트 번호 `919000000001`, `919000000002`, 코드 `123456` (2027-01-01까지).
- RPC:
  - 매칭: `find_match(p_target)`, `cancel_search`, `leave_match(p_match)`
  - 영상: `request_video`, `respond_video`, `end_video`
  - 신고: `report_user(p_reported, p_reason, p_match)`
  - 목록·프로필: `recent_list`, `card(uid)`, `my_entitlements`
  - 친구: `request_friend`, `answer_friend`, `remove_friend`, `friend_list`, `friend_thread`
  - 공개방: `room_list`, `join_room`, `leave_room`
- 테이블(RLS): `profiles`, `matches`, `messages`, `posts`, `room_messages`(realtime), `rooms`.
- Edge functions: `video-token`(LiveKit 토큰), `delete-account` — **브라우저 CORS 허용 완료**. `verify-purchase`는 Google Play 전용(웹 결제는 별도 필요: Razorpay/Stripe).
- 영상: LiveKit Cloud `wss://yaari-sofpbngs.livekit.cloud` (웹은 `livekit-client` npm 사용).
- 규칙: 18세 이상, 무료 5초·Plus 0.2초 나가기 잠금(서버 강제), 성별 선택·영상 요청은 유료, 신고 시 차단·친구 해제, 국기는 전화번호로 서버가 결정.

## 디자인 브리프 (v0 / Magic Patterns에 그대로 입력)
```
Design "yaari" — a global random video & text chat + social app (like Azar meets Instagram),
big-tech quality: refined type (Plus Jakarta Sans), generous spacing, subtle depth, one warm
brand gradient (#FF3D68 → #FF7A45), near-black immersive screens for random chat, clean white
for social screens. Rich motion everywhere: hover lift/glow on cards and buttons (web), spring
press feedback, page transitions, ambient animated backgrounds, live presence indicators.
Not generic, not AI-looking.

Sections / screens:
- Landing (web): hero with live animated preview, "Start chatting" CTA, how it works, safety.
- Random: dark lobby with my camera/avatar, gender filter (Everyone / Women / Men, paid locked),
  big Start button; matching animation; match reveal; 1:1 chat with video call button, report,
  exit button with 5-second countdown ring; incoming video request; video call screen.
- Rooms: grid of live public rooms (Global Lounge featured) with online counts; room chat.
- Recent: Friends (requests, friends, sent) / Matches segments.
- Me: Instagram-style profile with posts grid, edit profile; other people's profiles with
  Add friend / Message.
- Paywall "Yaari Plus": choose gender, video calls, 0.2s skip; monthly + 24h pass.
- Sign up: country code + phone, OTP, create profile (18+). Settings list.
Web/PC layout: left sidebar nav, center content, right panel (friends online / rooms).
```

## 상태
- Android 앱: `chat/app`, 테스트 demo 44 + live 46 통과, APK `chat/dist/`.
- 테스트 봇: `chat/server/tools/partner_bot.ts` (README 참고). QA 비밀번호는 저장소에 없음 → 필요하면 Supabase에서 QA 계정 비밀번호를 새로 설정.
- PR: entrepreneur123/india#1 (브랜치 `claude/wonderful-heisenberg-m37wwm`).
