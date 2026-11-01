import test from 'node:test'
import assert from 'node:assert/strict'
import { handleProblem, handleReserved, handleSpelling, handleValid, nameClean, namePlain, suggestHandle } from '../handle.ts'

// The rule of handle_valid() (055, Instagram's): checked against the server function with these same lists.
export const VALID = ['lisa', 'ab', 'priya_99', 'l._.1sa', 'a.b_c', '__x', '9lives', 'abcdefghijklmnopqrstuvwxyz1234']
export const INVALID = ['', 'a', '..', '__', '_.', '.lisa', 'lisa.', 'li..sa', 'li sa', 'li-sa', 'li@sa', 'lisa😀', 'Lisa',
  '지민', 'राहुल', 'Иван', 'zoë', 'a!b', 'abcdefghijklmnopqrstuvwxyz12345']

test('IDs the server takes: 2–30 of a–z, 0–9, "." and "_" (lower case, as stored)', () => {
  for (const x of VALID) assert.equal(handleValid(x), true, `valid: ${x}`)
})

test('IDs the server refuses: other letters, specials, length, and "." first, last or doubled', () => {
  for (const x of INVALID) assert.equal(handleValid(x), false, `invalid: ${JSON.stringify(x)}`)
})

test('reserved IDs: anything starting with yaari, admin, official, moderator, and a few exact words', () => {
  assert.equal(handleReserved('Yaari_Official'), true)
  assert.equal(handleReserved('admin.1'), true)
  assert.equal(handleReserved('Support'), true)
  assert.equal(handleReserved('helpful'), false)
  assert.equal(handleReserved('teamwork'), false)
})

test('the spelling is trimmed and lower-cased before it is judged, like set_handle', () => {
  assert.equal(handleSpelling(' L._.1sA '), 'l._.1sa')
  assert.equal(handleProblem('Lisa_99'), null)
  assert.equal(handleProblem('li sa'), 'invalid')
  assert.equal(handleProblem('지민'), 'invalid')
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

test('a suggested ID from a name: its English words in lower case, joined by "_", always one the server takes', () => {
  assert.equal(suggestHandle('Lisa'), 'lisa')
  assert.equal(suggestHandle('Raj Kumar'), 'raj_kumar')
  assert.equal(suggestHandle('राहुल शर्मा'), '') // another script: the server's free ID stays
  assert.equal(suggestHandle('조예찬', '482'), '')
  assert.equal(suggestHandle('Zoë 💖'), 'zo')
  assert.equal(suggestHandle('Priya', '482'), 'priya_482')
  assert.equal(suggestHandle('😀'), '')
  assert.equal(suggestHandle('A'), '')
  assert.equal(suggestHandle('Admin'), '') // reserved
  const long = suggestHandle('Maximiliano Alessandro Rodriguez', '4821')
  assert.equal(handleValid(long), true)
  assert.ok(long.endsWith('_4821') && long.length <= 30, long)
})

test('names: letters of any language, digits and . , _ - \' ’; no emoji, symbols, blank fillers or fancy fonts (111)', () => {
  const ok = ['Riya', 'माँ', 'অমিত', 'ਪੰਜਾਬ', 'தமிழ்', 'ष्ट्र', 'مهدی‌زاده', 'محمد', 'José', 'Nguyễn Văn An', '王小明', '田中々', 'Ana_2',
    "D'Souza", 'D’Souza', 'Abd-Allah', 'J. R.', 'Riya, K', '  Riya   Sen ', 'Ромео']
  const bad = ['Riya😀', '★Riya★', '꧁Riya꧂', 'ㅤ', 'Riya♥', 'Riya!', '#riya', 'Riya@', '𝓡𝓲𝔂𝓪', 'Ｒｉｙａ', 'ʀɪʏᴀ', 'Riya🇮🇳', '⠀', '༺Riya༻', 'R‍iㅤ']
  for (const n of ok) assert.equal(nameClean(n), true, n)
  for (const n of bad) assert.equal(nameClean(n), false, n)
})
