import test from 'node:test'
import assert from 'node:assert/strict'
import { accentVars, ACCENTS, ACCENT_COLORS, parseStyle, PROMPT_QS, SPOKEN_LANGS, styleInput, toggleLanguage } from '../profile-style.ts'

test('the lists match the server (033)', () => {
  assert.deepEqual(ACCENTS, ['rose', 'violet', 'ocean', 'mint', 'sunset', 'gold', 'berry', 'slate'])
  assert.deepEqual(SPOKEN_LANGS.map((l) => l.id), ['en', 'hi', 'bn', 'ta', 'te', 'mr', 'gu', 'kn', 'ml', 'pa', 'ur', 'or', 'as', 'pt', 'vi'])
  assert.deepEqual(PROMPT_QS, ['weekend', 'food', 'song', 'travel', 'talk', 'laugh', 'goal', 'secret'])
  for (const a of ACCENTS) assert.ok(ACCENT_COLORS[a].ink.startsWith('#'))
})

test('accentVars falls back to the brand', () => {
  assert.equal(accentVars(null)['--acc-ink'], ACCENT_COLORS.brand.ink)
  assert.equal(accentVars('mint')['--acc-via'], ACCENT_COLORS.mint.via)
  assert.equal(accentVars('neon' as never)['--acc-ink'], ACCENT_COLORS.brand.ink)
})

test('parseStyle drops what the app does not know', () => {
  const s = parseStyle({ accent: 'neon', status: '  ', languages: ['hi', 'xx', 'hi', 'en'], prompts: [{ q: 'food', a: ' Vada pav ' }, { q: 'zodiac', a: 'Leo' }, { q: 'song', a: '' }, 'x'] })
  assert.equal(s.accent, null)
  assert.equal(s.status, null)
  assert.deepEqual(s.languages, ['hi', 'en'])
  assert.deepEqual(s.prompts, [{ q: 'food', a: 'Vada pav' }])
  assert.deepEqual(parseStyle(null), { accent: null, status: null, languages: [], prompts: [], mbti: null })
  assert.equal(parseStyle({ mbti: 'INFP' }).mbti, 'INFP')
  assert.equal(parseStyle({ mbti: 'ABCD' }).mbti, null)
})

test('styleInput cleans the draft and says what is wrong', () => {
  const ok = styleInput({ status: '  hi there ', languages: ['en', 'en', 'bn'], prompts: [{ q: 'food', a: ' momos ' }, { q: 'food', a: 'again' }, { q: 'song', a: '   ' }], mbti: 'INFP' })
  assert.deepEqual(ok.input, { status: 'hi there', languages: ['en', 'bn'], prompts: [{ q: 'food', a: 'momos' }], mbti: 'INFP' })
  assert.equal(ok.problem, null)
  assert.equal(styleInput({ status: 'x'.repeat(61), languages: [], prompts: [], mbti: null }).problem, 'status')
  // 60 characters of a script with combining marks still fits (counted as code points like the server's char_length).
  assert.equal(styleInput({ status: 'अ'.repeat(60), languages: [], prompts: [], mbti: null }).problem, null)
  assert.equal(styleInput({ status: null, languages: ['en', 'hi', 'bn', 'ta', 'te', 'mr', 'gu'], prompts: [], mbti: null }).problem, 'languages')
  assert.equal(styleInput({ status: null, languages: [], prompts: [{ q: 'goal', a: 'y'.repeat(121) }], mbti: null }).problem, 'answer')
  assert.equal(styleInput({ status: null, languages: [], prompts: [{ q: 'goal', a: 'a' }, { q: 'food', a: 'b' }, { q: 'song', a: 'c' }, { q: 'talk', a: 'd' }], mbti: null }).problem, 'prompts')
})

test('toggleLanguage', () => {
  assert.deepEqual(toggleLanguage(['en'], 'hi'), ['en', 'hi'])
  assert.deepEqual(toggleLanguage(['en', 'hi'], 'en'), ['hi'])
  assert.equal(toggleLanguage(['en', 'hi', 'bn', 'ta', 'te', 'mr'], 'gu'), false)
})
