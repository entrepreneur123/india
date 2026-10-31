import test from 'node:test'
import assert from 'node:assert/strict'
import { connectOptions, deviceNotice, deviceProblem, fixedVolume, isAppleWebKit, partnerNotice, pathQuality, preferRelay, RELAY_AFTER, relayFirst, signalBars, videoCodecFor } from '../rtc.ts'

const IPAD_SAFARI_DESKTOP = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15'
const IPAD_KAKAO = 'Mozilla/5.0 (iPad; CPU OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 25.8.0'
const IPHONE_CHROME = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0 Mobile/15E148 Safari/604.1'
const MAC_CHROME = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36'
const CHROME_ANDROID = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36'
const WIN_EDGE = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36 Edg/129.0'

test('every iPhone/iPad browser and Safari send H.264; the rest VP8', () => {
  assert.equal(videoCodecFor(IPAD_SAFARI_DESKTOP, 5), 'h264') // iPadOS asks for the desktop site
  assert.equal(videoCodecFor(IPAD_SAFARI_DESKTOP, 0), 'h264') // Safari on a Mac
  assert.equal(videoCodecFor(IPAD_KAKAO), 'h264') // the in-app browser was missed before (no "Safari" in it)
  assert.equal(videoCodecFor(IPHONE_CHROME), 'h264') // Chrome on iOS is WebKit too
  assert.equal(videoCodecFor(MAC_CHROME), 'vp8')
  assert.equal(videoCodecFor(CHROME_ANDROID), 'vp8')
  assert.equal(videoCodecFor(WIN_EDGE), 'vp8')
  assert.equal(isAppleWebKit(MAC_CHROME, 0), false)
})

test('device errors map to what the person can do about them', () => {
  assert.equal(deviceProblem({ name: 'NotAllowedError' }), 'denied')
  assert.equal(deviceProblem({ name: 'NotFoundError' }), 'missing')
  assert.equal(deviceProblem({ name: 'NotReadableError' }), 'busy')
  assert.equal(deviceProblem({ name: 'AbortError', message: 'Starting videoinput failed' }), 'busy')
  assert.equal(deviceProblem(new Error('boom')), 'other')
  assert.equal(deviceProblem(null), 'other')
})

test('one message for the start of a call, the most useful first', () => {
  assert.equal(deviceNotice(null, null), null)
  assert.equal(deviceNotice('denied', 'denied'), 'blocked')
  assert.equal(deviceNotice('busy', null), 'deviceBusy')
  assert.equal(deviceNotice('denied', null), 'camDenied')
  assert.equal(deviceNotice(null, 'denied'), 'micDenied')
  assert.equal(deviceNotice('missing', null), 'noCamera')
  assert.equal(deviceNotice('missing', 'missing'), 'noMic')
  assert.equal(deviceNotice('other', null), 'blocked')
})

test('partner notice: dropped beats paused beats weak', () => {
  assert.equal(partnerNotice({ quality: 'excellent', videoPaused: false, left: false }), null)
  assert.equal(partnerNotice({ quality: 'poor', videoPaused: false, left: false }), 'weak')
  assert.equal(partnerNotice({ quality: 'poor', videoPaused: true, left: false }), 'paused')
  assert.equal(partnerNotice({ quality: 'lost', videoPaused: true, left: false }), 'lost')
  assert.equal(partnerNotice({ quality: 'good', videoPaused: false, left: true }), 'lost')
})

test('signal bars', () => {
  assert.deepEqual(['excellent', 'good', 'poor', 'lost', 'unknown'].map(signalBars), [3, 2, 1, 0, null])
})

test('fixedVolume: iPhone and iPad only (Safari on a Mac can set a volume)', () => {
  assert.equal(fixedVolume('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148'), true)
  assert.equal(fixedVolume('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15', 5), true)
  assert.equal(fixedVolume('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15', 0), false)
  assert.equal(fixedVolume('Mozilla/5.0 (Linux; Android 14) Chrome/120 Mobile Safari/537.36'), false)
})

test('a direct call\'s bars from round trip and loss', () => {
  assert.equal(pathQuality(0.05, 0), 'excellent')
  assert.equal(pathQuality(0.2, 0.01), 'good')
  assert.equal(pathQuality(0.05, 0.05), 'good')
  assert.equal(pathQuality(0.5, 0), 'poor')
  assert.equal(pathQuality(0.1, 0.1), 'poor')
})

test('relay first for a week after this browser’s media path broke; no storage: never', () => {
  const store = new Map<string, string>()
  const g = globalThis as { localStorage?: unknown }
  g.localStorage = { getItem: (k: string) => store.get(k) ?? null, setItem: (k: string, v: string) => void store.set(k, v) }
  try {
    assert.equal(relayFirst(1_000), false)
    preferRelay(1_000)
    for (const days of [0, 1, 6.99]) assert.equal(relayFirst(1_000 + days * 86_400_000), true)
    assert.equal(relayFirst(1_000 + 7 * 86_400_000), false)
    assert.deepEqual(connectOptions(), undefined) // Date.now() is far past the week stored at 1 s
    preferRelay()
    assert.deepEqual(connectOptions(), { rtcConfig: { iceTransportPolicy: 'relay' } })
    assert.equal(RELAY_AFTER, 2)
  } finally {
    delete g.localStorage
  }
  assert.equal(relayFirst(), false) // private mode or a server render: no storage, no relay
  assert.doesNotThrow(() => preferRelay())
})
