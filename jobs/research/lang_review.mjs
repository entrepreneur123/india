// Writes research/lang_review/<code>.md: every interface text this app wrote itself in that language,
// next to the English, for a native speaker to mark. Run from jobs/: node research/lang_review.mjs
// Notice titles and PDFs come from the recruiters as they are and are not part of the review.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { EN, LANGS } from '../web/i18n.js';

const cell = (s) => String(s).replace(/\|/g, '\\|').replace(/\n/g, ' ⏎ ');
const guide = JSON.parse(readFileSync('pipeline/ssc_guide.json', 'utf8'));
const fams = JSON.parse(readFileSync('pipeline/exam_families.json', 'utf8')).families;
mkdirSync('research/lang_review', { recursive: true });
for (const { code, name } of LANGS.filter((l) => l.code !== 'en')) {
  const L = (await import(`../web/lang/${code}.js`)).default;
  const out = [`# ${name} (${code}) 검수표 (원어민용)

이 앱이 **직접 쓴** 화면 문구 전부입니다. 공고 제목과 PDF는 기관 원문 그대로라 검수 대상이 아닙니다.
\`node research/lang_review.mjs\`로 코드에서 만들어지므로, 문구를 고치면(web/lang/${code}.js) 다시 생성하세요.

## 검수 방법
각 줄의 "OK?" 칸에 ✅ 또는 고칠 문장을 적어 주세요. 특히 봐 주실 점:
1. 뜻이 영어와 같은가 (날짜·금액·자격 조건: 틀리면 지원자가 손해를 봅니다)
2. 정부 채용 공고에서 쓰는 말과 맞는가 (SC/ST/OBC/EWS, 전역 군인, 장애인 등)
3. 수험생이 일상에서 쓰는 자연스러운 말인가 (딱딱하거나 직역투가 아닌가), 버튼 글자가 짧은가
4. {자리표시자}는 그대로 두고 위치만 옮길 수 있습니다

## 화면 문구 (web/lang/${code}.js)

| 키 | English | ${name} | OK? |
|---|---|---|---|`];
  for (const [k, en] of Object.entries(EN)) out.push(`| \`${k}\` | ${cell(en)} | ${cell(L[k] ?? '')} | |`);
  if (code === 'hi') {
    out.push(`
## 지원 방법·서류 (pipeline/ssc_guide.json, 공고 문단 번호 포함; 다른 언어는 영어로 표시)

| 문단 | English | हिन्दी | OK? |
|---|---|---|---|`);
    for (const e of [...guide.steps, ...guide.documents]) out.push(`| ${cell(e.ref)} | ${cell(e.en)} | ${cell(e.hi)} | |`);
    out.push(`
## 시험 이름 (pipeline/exam_families.json)

| 코드 | English | हिन्दी | OK? |
|---|---|---|---|`);
    for (const [c, f] of Object.entries(fams)) out.push(`| ${c} | ${cell(f.name)} | ${cell(f.hi)} | |`);
  }
  writeFileSync(`research/lang_review/${code}.md`, `${out.join('\n')}\n`);
}
console.log(`research/lang_review/: ${LANGS.length - 1} sheets`);
