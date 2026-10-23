import test from 'node:test'
import assert from 'node:assert/strict'
import { externalLink, inAppBrowser } from '../in-app-browser.ts'

const IPAD_KAKAO = 'Mozilla/5.0 (iPad; CPU OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 25.8.0'
const IG_IOS = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 390.0.0.0'
const FB_ANDROID = 'Mozilla/5.0 (Linux; Android 14; SM-A546E Build/UP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0 Mobile Safari/537.36 [FB_IAB/FB4A;FBAV/480.0.0]'
const SAFARI = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1'
const CHROME_ANDROID = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36'

test('built-in browsers are recognised, real browsers are not', () => {
  assert.equal(inAppBrowser(IPAD_KAKAO), 'kakao')
  assert.equal(inAppBrowser(IG_IOS), 'instagram')
  assert.equal(inAppBrowser(FB_ANDROID), 'facebook')
  assert.equal(inAppBrowser(SAFARI), null)
  assert.equal(inAppBrowser(CHROME_ANDROID), null)
})

test('a way out of the built-in browser where the app has one', () => {
  const url = 'https://yaari.app/r/ABC123?x=1'
  assert.equal(externalLink('kakao', url, IPAD_KAKAO), 'kakaotalk://web/openExternal?url=https%3A%2F%2Fyaari.app%2Fr%2FABC123%3Fx%3D1')
  assert.equal(externalLink('line', 'https://yaari.app/', ''), 'https://yaari.app/?openExternalBrowser=1')
  assert.match(externalLink('facebook', url, FB_ANDROID) ?? '', /^intent:\/\/yaari\.app\/r\/ABC123\?x=1#Intent;scheme=https;package=com\.android\.chrome;/)
  assert.equal(externalLink('instagram', url, IG_IOS), null) // iPhone: the app's own menu
})
