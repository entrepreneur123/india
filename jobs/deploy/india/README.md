# 인도 서버 설정 (수집 + 즉시 알림)

## 왜 필요한가
- **철도(RRB)·은행(IBPS)·UPSC·대부분의 주 위원회:** GitHub 서버(미국)에서는 응답하지 않습니다(jobs-probe로 확인). 인도에 있는 서버에서 수집해야 합니다.
- **즉시 알림:** GitHub만으로는 휴대폰에 바로 알림을 보낼 수 없습니다. 지금은 Chrome이 정한 시점에만 확인합니다. 서버가 있으면 Web Push로 바로 보냅니다. 아이폰(홈 화면에 추가한 경우)에서도 받을 수 있습니다.
- **GitHub 예약 실행의 지연·중지:** 서버에서는 30분마다 정확히 돕니다.

## 구조
```
인도 서버 (Ubuntu 24.04)
  sarkari.timer (30분마다) → run.sh
      코드 갱신 → 테스트 → 수집(build) → data 브랜치에 push → 텔레그램·푸시 발송
  sarkari-push (항상 실행) ← Caddy(HTTPS) ← 휴대폰의 알림 구독
  sarkari-capture.timer (매주 월) → 인도에서만 열리는 채용 사이트 페이지를 captures 브랜치에 저장
GitHub
  data 브랜치 → GitHub Pages (사이트), jobs-watchdog (6시간마다 신선도 확인)
```
data 브랜치에는 서버 한 곳만 씁니다. `JOBS_RUNNER=india`를 설정하면 GitHub의 jobs-publish는 멈춥니다.

## 1. 서버 만들기
- **조건:** Ubuntu **24.04**, **인도 리전**(뭄바이·하이데라바드 등), 메모리 **1–2 GB**, 디스크 20 GB.
- **업체:** Oracle Cloud, AWS Lightsail, Google Cloud, DigitalOcean 모두 인도 리전이 있습니다. 무료 등급이 인도 리전에서 되는지와 가격은 가입할 때 각 업체에서 직접 확인하세요. 여기서는 확인하지 못했습니다.
- **방화벽:** 즉시 알림을 쓰려면 들어오는 **80, 443 포트**를 열어 두세요. 업체 콘솔의 방화벽(보안 목록)에서 엽니다.

### Oracle Cloud(무료 등급)를 쓸 때
- 가입 시 홈 리전을 **India West (Mumbai)** 또는 **India South (Hyderabad)**로 고릅니다. 나중에 바꿀 수 없습니다.
- 서버 모양(Shape)은 Always Free 표시가 있는 **VM.Standard.A1.Flex(1 OCPU, 6 GB)** 또는 **VM.Standard.E2.1.Micro**를 씁니다. 이미지는 Ubuntu 24.04입니다.
- 80·443 포트는 두 곳에서 열어야 합니다.
  - 클라우드 방화벽: 서브넷의 Security List에서 엽니다.
  - 서버 안의 iptables: `setup.sh`가 엽니다.
- 활동이 적은 무료 서버는 회수될 수 있습니다. 계정을 Pay As You Go로 바꾸면 회수를 막을 수 있습니다(무료 한도 안에서는 요금 없음). 가입 당시 Oracle 문서를 확인하세요.

## 2. 설치 (서버에서 한 번)
```bash
# 기본 브랜치(claude/gallant-lovelace-ynoa63)에 jobs/ 코드가 있습니다(PR #3 병합 후).
curl -fsSLO https://raw.githubusercontent.com/entrepreneur123/india/claude/gallant-lovelace-ynoa63/jobs/deploy/india/setup.sh
sudo bash setup.sh entrepreneur123/india claude/gallant-lovelace-ynoa63
```
중간에 **배포 키**(ssh-ed25519로 시작하는 한 줄)가 출력되면 이렇게 등록하세요.
- 저장소 → **Settings → Deploy keys → Add deploy key**
- **Allow write access**를 체크하고 저장한 뒤, 서버에서 Enter를 누릅니다.

설치가 끝나면 30분마다 자동으로 수집합니다. 이렇게 확인할 수 있습니다.
```bash
systemctl list-timers sarkari.timer      # 다음 실행 시각
journalctl -u sarkari -n 50              # 최근 실행 기록 ("published" / "no change")
```

## 3. 설정 (선택)
`sudo nano /etc/sarkari.env`에서 설정합니다.
- 텔레그램: `JOBS_TELEGRAM_BOT_TOKEN`, `JOBS_TELEGRAM_CHAT_ID=@채널이름`
- WhatsApp 채널: `JOBS_WHATSAPP_CHANNEL=https://whatsapp.com/channel/...`
- 문의 이메일: `JOBS_CONTACT_EMAIL=...` (문의 페이지와 앱 하단 링크)
  - 앱의 홈과 알림 화면에 "WhatsApp" 참여 버튼으로 나옵니다.
  - WhatsApp 채널은 자동 게시 기능이 없어(공개된 게시 API를 찾지 못함), 수집할 때마다 올릴 글을 만들어 둡니다.
  - 그 글은 `사이트주소/jobs/v1/whatsapp.txt`(지난 24시간 공고와 공식 링크)에 있습니다. 복사해서 WhatsApp 앱에서 채널에 붙여 넣으면 됩니다.
- 사이트 주소가 다르면 `SITE_URL`

## 4. GitHub를 이 서버로 전환
1. 저장소 → **Settings → Secrets and variables → Actions → Variables**에 `JOBS_RUNNER` = `india`를 추가합니다.
   - 이때부터 GitHub의 jobs-publish는 실행되지 않습니다(쓰는 곳은 한 곳만).
2. **Settings → Pages**: 브랜치를 `data`로 설정합니다(이미 했다면 그대로 둡니다).
3. **jobs-watchdog**이 6시간마다 사이트의 `status.json`을 확인합니다. 7시간 넘게 갱신이 없으면 실패하고 GitHub이 메일을 보냅니다. 규칙 파일이 아직 없는 새 SSC 공고도 경고로 알려 줍니다.

## 5. 즉시 알림 켜기 (선택, 권장)
1. 이 서버를 가리키는 **호스트 이름**을 준비합니다. 보유한 도메인의 하위 이름(예: `push.내도메인.com`)에 서버 IP로 A 레코드를 추가하면 됩니다. 무료 동적 DNS도 가능합니다.
2. `/etc/sarkari.env`에서 다음을 설정합니다.
   - `PUSH_HOST=push.내도메인.com`
   - `PUSH_CONTACT=mailto:내메일`
3. `sudo bash /opt/sarkari/code/jobs/deploy/india/enable-push.sh`를 실행합니다.
   - Caddy가 HTTPS 인증서를 자동으로 받습니다.
   - 끝에 "push server reachable over HTTPS"가 나오면 성공입니다.
4. 다음 수집 때 `site.json`에 푸시 주소가 들어갑니다. 앱의 **더보기 → 알림 켜기**는 그때부터 즉시 알림으로 동작합니다.

알림이 얼마나 빨리 가나요?
- **SSC 공지:** 서버가 30분마다 확인하므로 보통 1시간 안에 갑니다.
- **결과와 UP·하리야나 공지:** 3시간마다의 전체 확인에서 잡히므로 최대 3시간이 걸립니다.

## 6. 사이트를 인도 서버에서 직접 제공 (권장)
GitHub Pages 대신 이 서버(인도)가 사이트를 보냅니다.
- 인도 휴대폰과 가까워 응답이 빠릅니다.
- 3G/4G에서 파일이 작아지도록 압축합니다(zstd, gzip).
- 새 공고가 수집 즉시 보입니다. GitHub Pages는 다시 빌드하는 데 몇 분이 걸립니다.

설정 순서:
1. 도메인의 하위 이름(예: `jobs.내도메인.in`)에 서버 IP로 A 레코드를 추가합니다.
2. `/etc/sarkari.env`에 `SITE_HOST=jobs.내도메인.in`을 적습니다.
   - 즉시 알림과 같은 이름을 써도 됩니다(`PUSH_HOST`도 같은 값).
3. `sudo bash /opt/sarkari/code/jobs/deploy/india/enable-site.sh`를 실행합니다.
   - Caddy가 HTTPS 인증서를 자동으로 받습니다.
   - 사이트 주소, 사이트맵, 텔레그램·WhatsApp 글 속 링크가 새 주소로 바뀝니다.
   - 끝에 "site reachable"이 나오면 성공입니다.

함께 적용되는 것:
- 보안 헤더: HSTS, CSP, nosniff, 프레임 금지.
- 공개하지 않는 경로 차단: `.git`, `captures`.
- `robots.txt`와 사이트맵.
- 캐시: 데이터는 2분, 코드는 5분. 오프라인 앱 기능은 그대로입니다.

## 7. 인도에서만 열리는 채용 사이트
- 매주 월요일, 22개 사이트의 첫 페이지와 링크 목록이 `captures` 브랜치에 저장됩니다. 설치 직후 한 번은 바로 실행됩니다.
- 이 실제 페이지를 보고 사이트별 수집기를 만듭니다. SSC·UP·하리야나도 이렇게 만들었습니다.
- 어떤 사이트가 열렸는지는 `captures/index.json`에서 볼 수 있습니다.

## 보안
- 서버는 `CODE_BRANCH`의 코드를 실행합니다. 이 브랜치와 data 브랜치에 아무나 push할 수 없게 **브랜치 보호**를 켜 두세요.
- 배포 키는 이 저장소에만 쓸 수 있습니다. 서버를 없애면 GitHub에서 키도 지우세요.
- 푸시 서버는 휴대폰 브라우저의 푸시 주소, 팔로우한 시험 코드, 언어만 저장합니다. 이름·번호·이메일은 저장하지 않습니다.
- 브라우저 푸시 서비스(Google, Mozilla, Apple, Microsoft) 주소만 받고, 이 사이트에서 온 요청만 받습니다. 요청 수도 제한합니다.
