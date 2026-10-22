import test from 'node:test'
import assert from 'node:assert/strict'
import {
  BAD_TAGS,
  canStart,
  checkRating,
  GOOD_TAGS,
  mannerLevel,
  mannerShown,
  parseKycResult,
  parseMyTrust,
  parseTrust,
  parseTrustList,
  shouldAskRating,
  toggleTag,
  verifyState,
} from '../trust.ts'
import { parseAuthor, parseTime } from '../feed.ts'

test('manner levels: <20 needs work, 20-39 low, 40-69 good, 70-89 great, 90+ excellent', () => {
  const cases: [number, string][] = [
    [0, 'needs_work'], [19, 'needs_work'], [19.9, 'needs_work'], [20, 'low'], [39, 'low'], [40, 'good'], [50, 'good'],
    [69, 'good'], [70, 'great'], [89, 'great'], [90, 'excellent'], [100, 'excellent'], [Number.NaN, 'needs_work'],
  ]
  for (const [score, level] of cases) assert.equal(mannerLevel(score), level, `score ${score}`)
})

test('the score shows from 3 ratings ("New here" before)', () => {
  assert.equal(mannerShown(0), false)
  assert.equal(mannerShown(2), false)
  assert.equal(mannerShown(3), true)
  assert.equal(mannerShown(40), true)
})

test('rating tags: the chosen side only, no repeats, at most 3 (same rule as rate_chat)', () => {
  assert.equal(checkRating(true, []), null)
  assert.equal(checkRating(false, []), null)
  assert.equal(checkRating(true, ['kind', 'funny', 'good_listener']), null)
  assert.equal(checkRating(false, ['rude', 'creepy']), null)
  assert.equal(checkRating(true, ['rude']), 'bad_request')
  assert.equal(checkRating(false, ['kind']), 'bad_request')
  assert.equal(checkRating(true, ['kind', 'kind']), 'bad_request')
  assert.equal(checkRating(true, ['kind', 'funny', 'respectful', 'interesting']), 'bad_request')
  assert.equal(checkRating(true, ['nice']), 'bad_request')
  assert.equal(GOOD_TAGS.length, 5)
  assert.equal(BAD_TAGS.length, 5)
})

test('choosing tags stops at 3; tapping again removes', () => {
  let t = toggleTag<string>([], 'kind')
  t = toggleTag(t, 'funny')
  t = toggleTag(t, 'respectful')
  assert.deepEqual(toggleTag(t, 'interesting'), ['kind', 'funny', 'respectful'])
  assert.deepEqual(toggleTag(t, 'funny'), ['kind', 'respectful'])
})

test('kyc results: known ones pass, anything else is none', () => {
  for (const r of ['verified', 'pending', 'declined', 'unreadable', 'minor', 'duplicate', 'none']) assert.equal(parseKycResult(r), r)
  assert.equal(parseKycResult('Approved'), 'none')
  assert.equal(parseKycResult(null), 'none')
  assert.equal(parseKycResult(3), 'none')
})

test('verification screen state from what is known', () => {
  const base = { verified: false, result: null, unavailable: false, opening: false }
  assert.equal(verifyState(base), 'unverified')
  assert.equal(verifyState({ ...base, result: 'none' }), 'unverified')
  assert.equal(verifyState({ ...base, opening: true }), 'opening')
  assert.equal(verifyState({ ...base, result: 'pending' }), 'pending')
  assert.equal(verifyState({ ...base, result: 'declined' }), 'declined')
  assert.equal(verifyState({ ...base, result: 'unreadable' }), 'unreadable')
  assert.equal(verifyState({ ...base, result: 'duplicate' }), 'duplicate')
  assert.equal(verifyState({ ...base, result: 'minor' }), 'minor')
  assert.equal(verifyState({ ...base, result: 'verified' }), 'verified')
  assert.equal(verifyState({ ...base, unavailable: true }), 'unavailable')
  // Verified wins over everything; unavailable over a stale result.
  assert.equal(verifyState({ ...base, verified: true, unavailable: true }), 'verified')
  assert.equal(verifyState({ ...base, result: 'declined', unavailable: true }), 'unavailable')
  assert.deepEqual(
    (['unverified', 'opening', 'pending', 'verified', 'declined', 'unreadable', 'duplicate', 'minor', 'unavailable'] as const).filter(canStart),
    ['unverified', 'declined', 'unreadable'],
  )
})

test('trust(): numbers clamped, only good tags kept, bad rows skipped', () => {
  const t = parseTrust({ id: 'u1', verified: true, manner: 104.4, ratings: 12, tags: [{ tag: 'kind', n: 5 }, { tag: 'rude', n: 9 }, { tag: 'funny', n: 0 }, 'x'] })
  assert.deepEqual(t, { id: 'u1', verified: true, manner: 100, ratings: 12, tags: [{ tag: 'kind', n: 5 }] })
  assert.deepEqual(parseTrust({ id: 'u2' }), { id: 'u2', verified: false, manner: 50, ratings: 0, tags: [] })
  assert.equal(parseTrust({ verified: true }), null)
  assert.deepEqual(parseTrustList([{ id: 'a' }, null, 'b', { id: 'c', manner: -3 }]).map((x) => [x.id, x.manner]), [['a', 50], ['c', 0]])
  assert.deepEqual(parseTrustList(null), [])
})

test('my_trust(): required, the last check and the chats rated today', () => {
  const t = parseMyTrust(
    { id: 'me', verified: false, manner: 50, ratings: 0, tags: [], required: true, kyc: { status: 'In Review', result: 'pending', at: '2026-10-22 09:00:00.123456+00' }, rated: ['m1', 2, 'm2'] },
    parseTime,
  )!
  assert.equal(t.required, true)
  assert.deepEqual(t.kyc, { status: 'In Review', result: 'pending', at: Date.parse('2026-10-22T09:00:00.123+00:00') })
  assert.deepEqual(t.rated, ['m1', 'm2'])
  const none = parseMyTrust({ id: 'me', kyc: null }, parseTime)!
  assert.equal(none.required, false)
  assert.equal(none.kyc, null)
  assert.deepEqual(none.rated, [])
  assert.equal(parseMyTrust({ id: 'me', kyc: { status: 'Not Started', result: null, at: 'x' } }, parseTime)!.kyc?.result, null)
})

test('ask for a rating only when both wrote and it is not rated or skipped', () => {
  const base = { matchId: 'm1', bothWrote: true, rated: [] as string[], skipped: [] as string[] }
  assert.equal(shouldAskRating(base), true)
  assert.equal(shouldAskRating({ ...base, bothWrote: false }), false)
  assert.equal(shouldAskRating({ ...base, rated: ['m0', 'm1'] }), false)
  assert.equal(shouldAskRating({ ...base, skipped: ['m1'] }), false)
  assert.equal(shouldAskRating({ ...base, rated: ['m2'], skipped: ['m3'] }), true)
})

test('post and comment authors carry the verified badge', () => {
  const sign = () => null
  assert.equal(parseAuthor({ id: 'u1', name: 'A', verified: true }, sign)?.verified, true)
  assert.equal(parseAuthor({ id: 'u1', name: 'A' }, sign)?.verified, false)
})
