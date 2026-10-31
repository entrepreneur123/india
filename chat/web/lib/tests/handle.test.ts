import test from 'node:test'
import assert from 'node:assert/strict'
import { handleProblem, handleReserved, handleSpelling, handleValid, nameClean, namePlain, suggestHandle } from '../handle.ts'

// The same examples as server/test_handles_looks.sql: the app and the server must agree on every one.

test('IDs the server takes: letters of real scripts, digits and the ASCII specials', () => {
  for (const x of [
    'L._.1sA', 'lisa', 'ab', 'priya_99', 'राहुल', 'রাহুল', 'ਰਾਹੁਲ', 'ராகுல்', 'ರಾಹುಲ್', 'രാഹുൽ', 'محمد', 'علی۱۲', 'דני', 'Ελένη',
    'Иван', 'ნინო', 'Հայկ', 'สมชาย', 'မောင်မောင်', 'さくら', 'カタカナ', '小明', '지민', 'ㅋㅋㅋ', 'Nguyễn', 'Şükrü', 'Ədalət', 'Ọlá',
    'a!#$%&()*+,-./', 'x:;=?[]^_{|}~', 'राहुल_99', 'abcdefghijklmnopqrstuvwx',
  ]) {
    assert.equal(handleValid(x), true, `valid: ${x}`)
  }
})

test('IDs the server refuses: length, spaces, emoji, fancy fonts, @ " \' < \\ `, only specials, mixed alphabets, hidden characters', () => {
  for (const x of [
    '', 'a', '지', 'abcdefghijklmnopqrstuvwxy', 'li sa', 'lisa😀', '😀😀😀', '𝓛𝓲𝓼𝓪', 'Ｌｉｓａ', 'ʟɪsᴀ', 'ⓛⓘⓢⓐ', 'ℒisa',
    'lisᵃ', 'ꜱhiv', 'ᏞᎥᏕᎪ', 'ᒪIᔕᗩ', 'ʇxǝʇ', 'li@sa', 'li"sa', "li'sa", 'li<sa', 'li\\sa', 'li`sa', '...', '!!!',
    'аdmin', 'pаypal', 'Ελa', `lis${String.fromCodePoint(8205)}a`, `lis${String.fromCodePoint(8203)}a`,
    `Nguye${String.fromCodePoint(770)}${String.fromCodePoint(771)}n`,
  ]) {
    assert.equal(handleValid(x), false, `invalid: ${JSON.stringify(x)}`)
  }
})

test('reserved IDs: anything starting with yaari, admin, official, moderator, and a few exact words', () => {
  assert.equal(handleReserved('Yaari_Official'), true)
  assert.equal(handleReserved('admin.1'), true)
  assert.equal(handleReserved('Support'), true)
  assert.equal(handleReserved('helpful'), false)
  assert.equal(handleReserved('teamwork'), false)
})

test('the spelling is trimmed and composed (NFC) before it is judged, like set_handle', () => {
  assert.equal(handleSpelling(' L._.1sA '), 'L._.1sA')
  assert.equal(handleSpelling(`Jose${String.fromCodePoint(769)}`), 'José')
  assert.equal(handleProblem(`Jose${String.fromCodePoint(769)}`), null)
  assert.equal(handleProblem('li sa'), 'invalid')
  assert.equal(handleProblem('yaari.team'), 'reserved')
  // A new spelling of my own ID is never "reserved" (set_handle allows it).
  assert.equal(handleProblem('ADMIN_lisa', 'admin_lisa'), null)
})

test('names: emoji and accents are fine, fancy-font letters and stacked accents are not', () => {
  for (const x of ['Lisa', 'Lisa 💖', 'राहुल शर्मा', 'Nguyễn Văn A', 'محمد علی', '지민', '🇮🇳 Priya', 'Zoë', 'José']) {
    assert.equal(namePlain(x), true, `plain name: ${x}`)
  }
  for (const x of ['𝓛𝓲𝓼𝓪', '𝐋𝐢𝐬𝐚', 'Ｌｉｓａ', 'ʟɪsᴀ', 'ⓛⓘⓢⓐ', '🄻🄸🅂🄰', 'ℒisa', 'ᏞᎥᏕᎪ', 'ᒪIᔕᗩ', 'ʇxǝʇ', `L${String.fromCodePoint(771)}${String.fromCodePoint(770)}isa`]) {
    assert.equal(namePlain(x), false, `fancy name: ${x}`)
  }
})

test('a suggested ID from a name: its words in lower case, joined by "_", always one the server takes', () => {
  assert.equal(suggestHandle('Lisa'), 'lisa')
  assert.equal(suggestHandle('Raj Kumar'), 'raj_kumar')
  assert.equal(suggestHandle('राहुल शर्मा'), 'राहुल_शर्मा')
  assert.equal(suggestHandle('Zoë 💖'), 'zoë')
  assert.equal(suggestHandle('Priya', '482'), 'priya_482')
  assert.equal(suggestHandle('😀'), '')
  assert.equal(suggestHandle('A'), '')
  assert.equal(suggestHandle('Admin'), '') // reserved
  // Latin and Cyrillic words are not mixed: the second alphabet is left out.
  assert.equal(suggestHandle('Anna Анна'), 'anna')
  const long = suggestHandle('Maximiliano Alessandro Rodriguez', '4821')
  assert.equal(handleValid(long), true)
  assert.ok(long.endsWith('_4821') && [...long].length <= 24, long)
})

test('names: letters of any language, digits and . , _ - \' ’; no emoji, symbols, blank fillers or fancy fonts (111)', () => {
  const ok = ['Riya', 'माँ', 'অমিত', 'ਪੰਜਾਬ', 'தமிழ்', 'ष्ट्र', 'مهدی‌زاده', 'محمد', 'José', 'Nguyễn Văn An', '王小明', '田中々', 'Ana_2',
    "D'Souza", 'D’Souza', 'Abd-Allah', 'J. R.', 'Riya, K', '  Riya   Sen ', 'Ромео']
  const bad = ['Riya😀', '★Riya★', '꧁Riya꧂', 'ㅤ', 'Riya♥', 'Riya!', '#riya', 'Riya@', '𝓡𝓲𝔂𝓪', 'Ｒｉｙａ', 'ʀɪʏᴀ', 'Riya🇮🇳', '⠀', '༺Riya༻', 'R‍iㅤ']
  for (const n of ok) assert.equal(nameClean(n), true, n)
  for (const n of bad) assert.equal(nameClean(n), false, n)
})
