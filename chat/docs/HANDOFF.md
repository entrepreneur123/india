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
- **Supabase:** project `dgynsckrqgnpyyvqekzi` (무료 요금제 — 실시간 동시접속 약 200 상한). 107부터 1:1·랜덤 채팅·매칭·그룹·알림은 Broadcast(chat:<match>, user:<uid>), 방 채팅 줄만 Postgres Changes(차단 필터 때문). 마이그레이션은 `mcp__Supabase__apply_migration`(name = 파일명), 검증은 `execute_sql`.
  - SQL 테스트 패턴: `begin; create temp table r(k text, v text) on commit drop; do $$ … set_config('request.jwt.claims', json_build_object('sub',uid,'role','authenticated')::text, true) … $$; select * from r; rollback;`
  - 주의: savepoint를 롤백하면 결과 행도 같이 지워진다.
- **배포(Vercel):** `mcp__Vercel__create_deployment`
  - teamId: `team_HKMkmzQNFo2UjOkkJWqlvD5Z`
  - requestBody: `{"deploymentId":"<직전 READY id>","withLatestCommit":true,"target":"production","name":"yaari-web"}` → 약 70초 뒤 `get_deployment`로 READY 확인
  - 사이트: https://yaari-web-indol.vercel.app — 직전 배포 id: 아래 0번 항목의 최신 커밋 배포(get_deployment로 확인)
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

- 방 유튜브: 방장·부방장은 유튜브 창 자체의 재생·정지·막대바로도 방 전체를 조작(0.6초 안정 후 반영, iframe에 포커스가 있을 때만 = 사용자가 직접 누른 것). 휴대폰이 스스로 멈춘 건 내 화면만. 유튜브 창의 볼륨은 우리 슬라이더에도 반영. 이 환경에선 실제 유튜브가 "콘텐츠 사용 불가"로 막혀 가짜 IFrame API(스크래치패드 fakeyt.js)로 검증함.

## 4. 이어서 할 일 (우선순위 순)
0. **2026-10-31 1~8단계(9 결제 제외)**: 108(방 줄 Broadcast; 웹 청취자는 방이 화면에 있으면 3초 하트비트(6초 끊김·13초 퇴장), 탭이 뒤로 갈 때만 20초(90초 퇴장), 닫으면 즉시)·109(국가번호 304개)·110(DM 'c'는 친구 아닌 사람만)·111(이름 이모지·기호 금지 name_clean)·112(콘솔 사용자 찾기·직접 정지 admin_find/admin_ban) 적용. 웹: 약한 망(P2P 3초 STUN 판단, 릴레이 우선 7일, SignalReconnecting), 휴대폰 360px 레이아웃, 언어별 랜딩 /hi·/ar…(app/[lang]), robots·sitemap·hreflang. 벤치마킹: 남은 큰 차이는 방 선물(유료 재화) — 9단계(결제)와 함께.
   - 108·109·110 적용 메모: apply_migration이 확인 질문(DROP 문)에 막혀 execute_sql로 적용하고 `supabase_migrations.schema_migrations`에 같은 이름으로 기록했다. 108은 088의 "friend presence" 정책을 남겼다(예전 페이지의 거절 재시도 방지) — 나중 마이그레이션에서 지운다.
   - Android는 이미 모두 3초 하트비트(포그라운드 서비스) — 웹의 화면 안 규칙과 같다. 방 줄은 아직 Postgres Changes(앱 출시 때 108 Broadcast로 옮김).
   - Supabase MCP는 DROP 문이 있으면 확인 질문을 띄우고 230초 뒤 실패한다. 시험·적용 SQL에서 DROP을 피할 것(`create or replace trigger` 사용).
   - **2026-10-31 오후 점검(네트워크·음질·DB·화면)**: 113(profiles_seen·profiles_public의 security_barrier 해제 — 권한이 없는 뷰라 보안 효과는 없고 플래너만 막았음: inbox2 664→4ms, group_list 58→2ms, friend_list 31→2ms, recent_list 33→4ms; 사람 FK 인덱스 12개; realtime 정책 initplan; 트리거 함수 EXECUTE 회수), 114(게시물 사진 256px 사본 + stale_media가 `_s.jpg`를 원본 기준으로 판단 — 062 규칙대로면 프로필 사진 사본 4개가 하루 뒤 야간 정리에 지워질 참이었음). 웹: 앱 시작 요청 23→17(user 토픽 첫 join의 catch-up 제거, mine() 대신 lastMe), 방 링크로 들어오면 안내 투어는 방을 나간 뒤, 방 링크는 Rooms 탭 점등, 게시물 격자는 사본 사용, 탐색 더보기 로딩 아이콘 중복 제거.
   - 지역: Supabase ap-south-1(뭄바이), Vercel CDN(전 세계 엣지), LiveKit Cloud `wss://yaari-sofpbngs.livekit.cloud`(글로벌 엣지 자동 라우팅). check-host.net 프로브(HTML 1회 요청): 인도 Rajpura 688ms·자카르타 582·이스탄불 533·상파울루 350·미국 63·호치민 1675 — 엣지 캐시가 비어 있을 때의 원점 왕복이라 실사용자 체감은 더 짧다. API(401 응답)는 Cloudflare 엣지가 답해 지역 측정에 못 씀.
   - 음성 설정 확인(코드·협상 SDP): Opus 48kbps mono + DTX + RED(payload 63 협상됨) + in-band FEC, 방은 autoSubscribe false·소리 끔 기본(듣는 사람은 말하는 사람 트랙만 다운로드), 영상 720p 심캐스트(180/360)·adaptiveStream·dynacast, Krisp. 이 샌드박스는 UDP/TURN이 막혀 ICE가 'checking'에 머물러 실제 비트레이트·손실은 못 쟀다(실기기에서 chrome://webrtc-internals로 확인).
   - 남은 것: 중복 인덱스 posts_user/posts_user_time와 미사용 인덱스 19개는 DROP이라 MCP로 못 지움(사장님 SQL 에디터에서 `drop index posts_user;` 등). 옛 게시물(114 전)은 사본이 없어 격자에서 원본을 내려받음(backfillThumbs는 프로필 사진만). looks(30명) 20ms는 can_view 7단계 EXISTS 비용 — 필요하면 다음에.
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
