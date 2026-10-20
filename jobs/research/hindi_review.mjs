// Writes research/HINDI_REVIEW.md: every Hindi text this app wrote itself, next to the English,
// for a native speaker to mark. Run from jobs/: node research/hindi_review.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { STRINGS } from '../web/i18n.js';

const cell = (s) => String(s).replace(/\|/g, '\\|').replace(/\n/g, ' ⏎ ');
const guide = JSON.parse(readFileSync('pipeline/ssc_guide.json', 'utf8'));
const fams = JSON.parse(readFileSync('pipeline/exam_families.json', 'utf8')).families;
const out = [];
out.push(`# 힌디어 검수표 (원어민용)

이 앱이 **직접 쓴** 힌디어 문장 전부입니다. SSC·UP 경찰 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
이 파일은 \`node research/hindi_review.mjs\`로 코드에서 만들어지므로, 문구를 고치면 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (특히 날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 공고에서 쓰는 말과 맞는가 (예: 수험생이 아는 말이 "रिज़ल्ट"인지 "परिणाम"인지)
3. 일상 사용자가 읽기 쉬운가 (너무 딱딱하거나 직역투가 아닌가)
4. 철자: nukta(ज़/फ़), 표준 철자(हिंदी/हिन्दी 혼용), 숫자·{자리표시자} 유지

## 자체 검토에서 이미 고친 것 (2026-10-20)
- 타자 안내에 "힌디 시험은 Mangal 글꼴 필요"라고 썼으나 2026 CHSL·CGL·Steno 공고에 없는 내용이라 삭제 (영어·힌디 모두)
- "결과"를 탭은 रिज़ल्ट, 배지는 परिणाम으로 섞어 쓰던 것을 रिज़ल्ट로 통일

## 원어민 판단이 필요한 것
- \`की-डिप्रेशन\` (key depression): SSC 힌디 공고의 표기를 확인하지 못했습니다
- \`हिंदी\` / \`हिन्दी\`가 섞여 있습니다 (언어 선택은 हिन्दी). 하나로 통일할지
- 시험 이름의 힌디어(exam_families.json)는 SSC 공식 목록·공고 제목에서 옮겼고, UP 경찰 4개는 위원회 공지 제목의 낱말로 만든 이름입니다

## 1. 화면 문구 (web/i18n.js)

| 키 | English | हिन्दी | OK? |
|---|---|---|---|`);
for (const [k, en] of Object.entries(STRINGS.en)) {
  const hi = STRINGS.hi[k];
  if (typeof en !== 'string') continue;
  out.push(`| \`${k}\` | ${cell(en)} | ${cell(hi)} | |`);
}
const fnKeys = Object.keys(STRINGS.en).filter((k) => typeof STRINGS.en[k] !== 'string');
if (fnKeys.length) out.push(`\n함수로 된 문구(${fnKeys.map((k) => `\`${k}\``).join(', ')})는 web/i18n.js에서 직접 봐 주세요.`);
out.push(`
## 2. 지원 방법·서류 (pipeline/ssc_guide.json, 공고 문단 번호 포함)

| 문단 | English | हिन्दी | OK? |
|---|---|---|---|`);
for (const e of [...guide.steps, ...guide.documents]) out.push(`| ${cell(e.ref)} | ${cell(e.en)} | ${cell(e.hi)} | |`);
out.push(`
## 3. 시험 이름 (pipeline/exam_families.json)

| 코드 | English | हिन्दी | OK? |
|---|---|---|---|`);
for (const [code, f] of Object.entries(fams)) out.push(`| ${code} | ${cell(f.name)} | ${cell(f.hi)} | |`);
writeFileSync('research/HINDI_REVIEW.md', `${out.join('\n')}\n`);
console.log(`research/HINDI_REVIEW.md: ${out.length} lines`);
