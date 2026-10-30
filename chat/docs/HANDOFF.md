# Yaari 인수인계 (2026-10-30) — 새 세션은 이 파일부터 읽고 시작

## 0. 먼저 읽을 것 (우선순위 순)
1. `/home/user/india/CLAUDE.md` — 사장님 작업 규칙 (보고는 한국어, 짧게, 할루시네이션 금지, 묻지 말고 끝까지 진행·검증·배포)
2. `/home/user/india/chat/CLAUDE.md` — **헌법** (모든 규칙보다 우선). 요점:
   - 작게 쪼개 하나씩 → 직접 눌러보고(Playwright/SQL) 검증 → 대체(덧붙이기 금지)
   - 레이어 우회·중복 API 금지
   - 마이그레이션은 `server/migrations/NNN_*.sql` 새 파일로만 추가. 이미 적용한 파일은 고치지 않고, 재실행해도 안전하게 쓴다
   - 화면을 바꿀 땐 앱 3개 이상 벤치마킹
   - 지시한 범위만, 상식대로
3. 이 파일

## 1. 사장님 / 사업
- 사장님: 한국 고3(08년생, 만 18세). 바이브코딩으로 아이패드에서 백그라운드 작업을 맡긴다. 보고는 반드시 한국어로.
- 권한: Supabase SQL·마이그레이션, Vercel 배포까지 전부 허용("모든 권한 부여, 알아서 웹에 배포까지"). 승인창 없이 여기서 바로 요청해도 된다.
- 제품: **Yaari** — 랜덤채팅 + 음성 룸 + 인스타식 프로필·피드 + 친구 DM.
- 시장: 초기 사용자 확보가 핵심. **인도와 중동이 메인**, 그다음 동남아·북아프리카·남미에 공격적으로 마케팅한다.
  - 지금 웹 화면은 사장님 디버깅용으로 한국어로 두지만, 실제 대상은 위 지역이다 → 문구·RTL(아랍어)·현지 통화·국가별 정서를 늘 염두에 둔다.
  - 국가별 매칭은 아자르 방식: 자국 우선, 가끔 다른 나라(096).
  - 출시 후 나라별로 하나씩 손볼 예정.
- 웹 먼저. 안드로이드 앱(`chat/app`, 네이티브 Compose)은 출시 직전 사장님 지시가 있을 때 웹 기능을 옮긴다. 지금 앱은 웹보다 많이 뒤처져 있다(확성기·음성메시지 등 없음).
- Play Console: 사장님이 만 18세라 한국 성년(만 19세) 조건 때문에 본인 명의로는 불가(약관 1.2). **부모님 명의 개인 계정**으로 만들고, 19세가 되면 앱을 이전하기로 함. 일단 "나중에"로 미룸(`docs/LAUNCH_GUIDE.md` 4장).

## 2. 기술 / 운영 레시피
- **웹:** `chat/web` — Next.js 16 static export, SWR, Tailwind v4, motion. 데모 백엔드(`lib/mockBackend.ts`)와 실서버(`lib/supabaseBackend.ts`)가 같은 `Backend` 인터페이스(`lib/backend.ts`)를 구현한다. 둘 다 고친다.
- **Supabase:** project `dgynsckrqgnpyyvqekzi`. 마이그레이션은 `mcp__Supabase__apply_migration`(name = 파일명), 검증은 `execute_sql`.
  - SQL 테스트 패턴: `begin; create temp table r(k text, v text) on commit drop; do $$ … set_config('request.jwt.claims', json_build_object('sub',uid,'role','authenticated')::text, true) … $$; select * from r; rollback;`
  - 주의: savepoint를 롤백하면 결과 행도 같이 지워진다.
- **배포(Vercel):** `mcp__Vercel__create_deployment`
  - teamId: `team_HKMkmzQNFo2UjOkkJWqlvD5Z`
  - requestBody: `{"deploymentId":"<직전 READY id>","withLatestCommit":true,"target":"production","name":"yaari-web"}` → 약 70초 뒤 `get_deployment`로 READY 확인
  - 사이트: https://yaari-web-indol.vercel.app — 직전 배포 id: `dpl_x5ajDuFSswkXkmsJsEfgrEVbmtpc` (commit 108f9604)
- **Git:**
  - 브랜치: `claude/upbeat-cannon-3ddo4f`, PR: entrepreneur123/india#2 (draft, 구독 중, CI 매번 green)
  - 커밋 끝줄: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` + `Claude-Session: …`
- **검증:**
  - `cd chat/web && npx tsc --noEmit -p . && npm test` (101 pass) `&& npm run build`
  - Playwright: `node <scratchpad>/pw/serve.mjs chat/web/out 3123`(백그라운드) + `login.js` helper(데모 로그인, `noCall()` = 데모 전화 창 닫기, `nav()` = 데스크톱 사이드바 이동, `before(p)` = 라우트 가로채기). 스크래치패드는 세션마다 바뀌므로 필요하면 새로 만든다.
  - 데모에선 20초 뒤 Sofia 확성기, 가짜 Arjun 전화가 뜬다. 테스트할 때 `noCall()`로 닫는다.
- **관리자 테스트 계정:** +91 9000000001 (seyoen, id f0540a34-35d2-4100-a31f-8ed4cd64ff8f, 국가 IN) — 확성기 무제한(102).

## 3. 이번 세션에 한 것 (모두 배포됨, 마이그레이션 095–105 적용됨)
- 095 친구 1:1 통화 P2P(실패 시 LiveKit), 096 국가 우선 매칭, 097 DM 기본 '모두 허용', 098 친구·좋아요 목록(제3자 프로필에서도)
- 099 1:1 음성메시지(마이크 = 입력칸이 비었을 때 보내기 버튼 자리, `components/screens/chat/voice-note.tsx`)
- 100–102 확성기(유료, 소모품): 위쪽 상시 띠 `MegaphoneBar` + 채팅창 `MegaphoneChat`(최근 24시간, 확성기 있으면 바로 입력), 관리자 무제한(`megaphone_wallet.unlimited`, count = -1 → ∞)
- 인스타식 프로필 정리, MBTI·언어 제거, 방 미리보기 시트(입장 버튼), 방 입장 시 소리 꺼짐 기본
- 피드 4:5, 하이라이트 9:16 칸 — 사진은 **자르지 않고 원본 비율로 줄여 넣기**(object-contain, 사장님 지시)
- 넓은 화면은 폭 제한 없이 끝까지 사용(가운데 칸이 늘어남), 방 목록은 휴대폰에서도 한 줄에 2개(작게)
- 1:1 음성메시지는 "+" 메뉴에서 시작(사진·동영상과 같이)
- 103 최근 → 친구 목록이 채팅 목록처럼 마지막 메시지·시간·안 읽은 수 표시(dm 스레드 기준), 최신 대화가 위로

## 4. 이어서 할 일 (우선순위 순)
1. **방 채팅 ✓/✓✓ 화면 연결 — 완료(2026-10-30)**: `lib/voice-chat.ts`의 readByOthers·movesReads·nextReads(서버 규칙과 같음, 단위 테스트), 세션 `readBy`·`markRead`, 화면은 1.5~3초 무작위 대기 후 읽음(동시 호출 방지). 아래는 당시 설계 메모.
   - RPC `voice_read(p_room, p_upto bigint)` → `{hi, by, hi2}`. 읽음 표시가 움직이면 방 스트림에 `voice_emit` `{t:'k', hi, by, hi2, seq}` 이 온다.
   - (참고) 데모 Playwright 테스트는 약 20초 뒤 뜨는 가짜 Arjun 전화를 먼저 거절해야 한다(창이 화면을 덮어 클릭이 30초 지연됨).
   - 판정: 다른 사람이 읽은 최대 id = `by === me ? hi2 : hi`. 내 줄 id ≤ 그 값이면 ✓✓, 아니면 ✓.
   - 할 일:
     - `lib/backend.ts`: `VoiceEvent`에 `{type:'read'; hi; by; hi2}`, `Backend.voiceRead(roomId, upto)` 추가
     - `supabaseBackend.subscribeVoice`의 `VoiceChange`에 `t:'k'` 추가하고 onChange에서 'p'처럼 번호 처리 후 emit
     - mock(`lib/mockVoice.ts`)에도 구현
     - 방 화면(`components/screens/voice/voice-room.tsx` / `voice-chat.tsx`): 입장 시 `voiceRead(room, 0)`으로 현재 값을 받는다. 화면이 보이고 남의 새 줄이 생기면 1~2초 디바운스로 `voiceRead(room, 남의 최신 id)` 호출. 단, 이미 `hi`와 `hi2`가 모두 그 id 이상이면 호출하지 않는다(서버 부하).
     - 내 텍스트·사진·음성 줄에 Check/CheckCheck 아이콘(1:1 채팅의 read receipt 스타일 재사용)
   - 테스트: 데모 2탭 또는 mock 시뮬레이션, 3회 반복.
2. **방 음성메시지 화면 연결 — 완료(2026-10-30)**: 방 입력칸 "+"를 모두에게(사진·음성메시지, 방장·부방장은 투표까지), 녹음 바·말풍선은 1:1의 `VoiceRecorder`·`AudioBubble` 재사용, ✓/✓✓도 붙음. 음성메시지에는 답장 없음(voice_send2가 text·photo·poll만 인용). 아래는 당시 설계 메모.
   - RPC `voice_audio(p_room, p_path)`. 경로: `<uid>/rooms/<uuid>.(webm|m4a|ogg)`(기존 `upload(file,'rooms')` 그대로), 1분에 6개 제한.
   - 할 일:
     - `LINE_KINDS`에 'audio' 추가, `toVoiceLines`에 audio 매핑(서명 URL)
     - `VoiceLine`에 `{kind:'audio', url,…}` 추가, `Backend.sendVoiceAudio(roomId, clip)` 추가
     - `VoiceComposer`의 "+" 메뉴에 '음성메시지' 항목 추가(사장님 지시: 녹음은 "+"에서). 지금 "+" 메뉴는 방장·부방장만 있고 다른 사람은 사진 버튼만 있음 → 모두에게 "+"(사진, 음성메시지, 방장이면 투표), 녹음 바는 `VoiceRecorder` 재사용
     - 말풍선은 `AudioBubble` 재사용, mock 구현
2-1. 음성메시지 위치: **완료(2026-10-30)** — 1:1·랜덤 채팅은 입력칸 왼쪽 "+" → 사진·동영상 / 음성메시지(카카오톡식, 사장님 지시). 보내기 자리 마이크는 없앰.
   - 그룹 채팅은 사진도 없음(텍스트만, `group_messages`에 media 칼럼 없음) → 음성을 넣으려면 group media 설계부터.
3. 보고 대기 중인 사장님 확인 사항: 없음. Play 결제는 사장님이 "나중에"라고 함.
4. 출시 전 체크:
   - `app_flags.require_verification`이 지금 false → 켜기(Didit 인증 세팅 후)
   - 확성기 Play 상품 `megaphone` 만든 뒤 `verify-purchase` 함수 배포(저장소 코드에는 MEGAPHONE 반영돼 있음, 서버에는 아직 안 올림)
   - 저장된 MBTI·언어 데이터 삭제 여부는 사장님 결정 대기(데이터 삭제라 묻기 대상)

## 5. 알려진 한계 (확인된 사실만)
- 실기기 P2P 연결 성공률, 실계정 음성 업로드는 실서버에서 아직 직접 확인하지 못함(데모와 SQL로만 검증).
- 피드 게시물 사진은 작은 사본(thumbnail)이 없다(프로필·하이라이트만 있음). 큰 피드는 원본 1280px를 그대로 받는다.
- 확성기 위쪽 띠는 채팅·그룹·방 전체 화면·랜덤 채팅 중에는 숨는다.
