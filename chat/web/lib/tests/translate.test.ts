import test from 'node:test'
import assert from 'node:assert/strict'
import { LANG_IDS } from '../lang-tags.ts'
import { cacheKey, defaultTarget, guessLatin, isTarget, MAX_IN_FLIGHT, shouldAutoTranslate, targetOfTag, TARGETS, TranslateStore } from '../translate.ts'

test('every app language has a translation target; regional tags pick the right variant', () => {
  for (const lang of LANG_IDS) assert.ok(isTarget(defaultTarget(lang)), lang)
  assert.equal(defaultTarget('zh-Hans'), 'zh-Hans')
  assert.equal(defaultTarget('zh-Hant'), 'zh-Hant')
  assert.equal(defaultTarget('pt-BR'), 'pt-BR')
  assert.equal(defaultTarget('fil'), 'fil')
  assert.equal(defaultTarget('es'), 'es')
  assert.equal(defaultTarget('es', ['es-MX', 'en']), 'es-419')
  assert.equal(defaultTarget('es', ['es-ES']), 'es')
  assert.equal(defaultTarget('es', ['en-US']), 'es')
  assert.equal(targetOfTag('zh'), 'zh-Hans')
  assert.equal(targetOfTag('zh-TW'), 'zh-Hant')
  assert.equal(targetOfTag('zh-Hant-HK'), 'zh-Hant')
  assert.equal(targetOfTag('pt'), 'pt-BR')
  assert.equal(targetOfTag('pt-PT'), 'pt')
  assert.equal(targetOfTag('pt-AO'), 'pt')
  assert.equal(targetOfTag('es-419'), 'es-419')
  assert.equal(targetOfTag('es-AR'), 'es-419')
  assert.equal(targetOfTag('nb-NO'), 'no')
  assert.equal(targetOfTag('iw'), 'he')
  assert.equal(targetOfTag('in-ID'), 'id')
  assert.equal(targetOfTag('tl'), 'fil')
  assert.equal(targetOfTag('en-GB'), 'en')
  assert.equal(targetOfTag('xx'), null)
  assert.equal(TARGETS.length, 64)
})

test('lines that are only emoji, links, numbers or mentions are never translated by themselves', () => {
  for (const text of ['😂😂😂', '👍', 'https://example.com/a?b=1', '12345', '!!!', '@Asha', '  ', '', null, 'k', '❤️ 100%', 'www.yaari.app 🔥'])
    assert.equal(shouldAutoTranslate(text, 'en'), false, String(text))
})

test('another script than mine is translated', () => {
  assert.equal(shouldAutoTranslate('नमस्ते, आप कैसे हैं?', 'en'), true)
  assert.equal(shouldAutoTranslate('こんにちは！元気？', 'en'), true)
  assert.equal(shouldAutoTranslate('مرحبا كيف حالك', 'pt-BR'), true)
  assert.equal(shouldAutoTranslate('hello how are you', 'hi'), true)
  assert.equal(shouldAutoTranslate('привет как дела', 'ja'), true)
  assert.equal(shouldAutoTranslate('안녕하세요', 'zh-Hans'), true)
  assert.equal(shouldAutoTranslate('你好吗', 'ko'), true)
})

test('my own script and language is left alone', () => {
  assert.equal(shouldAutoTranslate('hey where are you from?', 'en'), false)
  assert.equal(shouldAutoTranslate('नमस्ते, आप कैसे हैं?', 'hi'), false)
  assert.equal(shouldAutoTranslate('こんにちは！元気？', 'ja'), false)
  assert.equal(shouldAutoTranslate('привет как дела', 'ru'), false)
  assert.equal(shouldAutoTranslate('مرحبا كيف حالك', 'ar'), false)
  assert.equal(shouldAutoTranslate('ok', 'es'), false)
  assert.equal(shouldAutoTranslate('lol', 'pt-BR'), false)
  // A line mostly in my script with a word or two of another stays as it is.
  assert.equal(shouldAutoTranslate('आज का match देखा क्या भाई?', 'hi'), false)
})

test('Latin-script languages are told apart by their common words', () => {
  assert.equal(guessLatin('hola, cómo estás?'), 'es')
  assert.equal(guessLatin('oi tudo bem com você?'), 'pt')
  assert.equal(guessLatin('kya haal hai bhai'), 'hinglish')
  assert.equal(guessLatin('xyzzy plugh'), null)
  assert.equal(shouldAutoTranslate('hola, cómo estás?', 'en'), true)
  assert.equal(shouldAutoTranslate('hola, cómo estás?', 'es'), false)
  assert.equal(shouldAutoTranslate('hola, cómo estás?', 'es-419'), false)
  assert.equal(shouldAutoTranslate('oi gente, tudo bem?', 'en'), true)
  assert.equal(shouldAutoTranslate('where are you from?', 'pt-BR'), true)
  assert.equal(shouldAutoTranslate('kya haal hai bhai', 'en'), true)
  assert.equal(shouldAutoTranslate('Arijit ka koi bhi gaana, bas 🔥', 'en'), true)
  // Hindi and Urdu readers read Hindi typed in Latin letters as it is.
  assert.equal(shouldAutoTranslate('kya haal hai bhai', 'hi'), false)
  assert.equal(shouldAutoTranslate('kya haal hai bhai', 'ur'), false)
  assert.equal(shouldAutoTranslate('tôi không biết', 'en'), true)
  assert.equal(shouldAutoTranslate('né?! acordei às 5 só pra isso kkkk', 'en'), true)
  assert.equal(shouldAutoTranslate('jajaja Messi para siempre 🐐', 'en'), true)
  assert.equal(shouldAutoTranslate('ça va? je suis à Paris', 'en'), true)
  assert.equal(shouldAutoTranslate('café time with José', 'en'), false) // English with accented names
  assert.equal(shouldAutoTranslate('Größe Schokolade Brötchen', 'en'), true)
  // Names and words that say nothing about the language are not guessed at.
  assert.equal(shouldAutoTranslate('Messi Ronaldo Neymar', 'en'), false)
})

test('Persian or Urdu among Arabic letters, Japanese among Chinese characters', () => {
  assert.equal(shouldAutoTranslate('سلام، چطوری؟', 'ar'), true)
  assert.equal(shouldAutoTranslate('آپ کیسے ہیں؟', 'ar'), true)
  assert.equal(shouldAutoTranslate('今日は楽しかったです', 'zh-Hans'), true)
  assert.equal(shouldAutoTranslate('今天很开心', 'zh-Hans'), false)
})

const deferred = () => {
  let resolve!: (v: { text: string; same: boolean }) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<{ text: string; same: boolean }>((a, b) => ((resolve = a), (reject = b)))
  return { promise, resolve, reject }
}
const tick = () => new Promise((r) => setTimeout(r, 0))

test(`the store sends at most ${MAX_IN_FLIGHT} requests at once and never asks twice for the same line`, async () => {
  const store = new TranslateStore()
  const calls: ReturnType<typeof deferred>[] = []
  const run = () => {
    const d = deferred()
    calls.push(d)
    return d.promise
  }
  for (let i = 1; i <= 10; i++) store.request(cacheKey('chat', i, 'en'), run, { manual: false })
  for (let i = 1; i <= 10; i++) store.request(cacheKey('chat', i, 'en'), run, { manual: false }) // re-render
  assert.equal(calls.length, MAX_IN_FLIGHT)
  assert.equal(store.get(cacheKey('chat', 7, 'en'))?.status, 'loading')
  calls[0].resolve({ text: 'hi', same: false })
  await tick()
  assert.equal(calls.length, MAX_IN_FLIGHT + 1)
  assert.deepEqual(store.get(cacheKey('chat', 1, 'en')), { status: 'done', text: 'hi', same: false })
  store.request(cacheKey('chat', 1, 'en'), run, { manual: true })
  assert.equal(calls.length, MAX_IN_FLIGHT + 1) // known: nothing sent
  // Each answer frees a slot for the next waiting line (resolving twice is harmless).
  for (let round = 0; round < 4; round++) {
    for (const c of calls) c.resolve({ text: 'x', same: true })
    await tick()
  }
  assert.equal(calls.length, 10)
  assert.equal(store.pending, 0)
})

test('a tapped line goes before waiting automatic ones; lines scrolled away are skipped', async () => {
  const store = new TranslateStore()
  const started: number[] = []
  const gates: ReturnType<typeof deferred>[] = []
  const run = (id: number) => () => {
    started.push(id)
    const d = deferred()
    gates.push(d)
    return d.promise
  }
  const onScreen = new Set([1, 2, 3, 4, 5, 6])
  for (let i = 1; i <= 6; i++) store.request(cacheKey('room', i, 'fr'), run(i), { manual: false, wanted: () => onScreen.has(i) })
  store.request(cacheKey('room', 9, 'fr'), run(9), { manual: true })
  onScreen.delete(5) // scrolled away while waiting
  gates[0].resolve({ text: 'a', same: false })
  await tick()
  assert.deepEqual(started, [1, 2, 3, 4, 9])
  gates[1].resolve({ text: 'b', same: false })
  await tick()
  assert.deepEqual(started, [1, 2, 3, 4, 9, 6])
  assert.equal(store.get(cacheKey('room', 5, 'fr')), undefined) // asked again if it comes back
})

test('a failure is kept (no retry storm) until the reader taps again; errors are reported', async () => {
  const seen: [string, boolean][] = []
  const store = new TranslateStore((code, manual) => seen.push([code, manual]))
  let n = 0
  const fail = () => {
    n++
    return Promise.reject(Object.assign(new Error('x'), { code: 'translate_failed' }))
  }
  const key = cacheKey('chat', 3, 'de')
  store.request(key, fail, { manual: false })
  await tick()
  await tick()
  assert.deepEqual(store.get(key), { status: 'error', code: 'translate_failed' })
  store.request(key, fail, { manual: false })
  assert.equal(n, 1)
  store.request(key, fail, { manual: true })
  await tick()
  await tick()
  assert.equal(n, 2)
  assert.deepEqual(seen, [['translate_failed', false], ['translate_failed', true]])
})

test('dropping queued automatic requests keeps the tapped ones', async () => {
  const store = new TranslateStore()
  const started: number[] = []
  const hold = () => new Promise<{ text: string; same: boolean }>(() => undefined)
  for (let i = 1; i <= 6; i++)
    store.request(cacheKey('chat', i, 'en'), () => (started.push(i), hold()), { manual: i === 6 })
  store.dropQueued()
  assert.equal(store.get(cacheKey('chat', 5, 'en')), undefined)
  assert.equal(store.get(cacheKey('chat', 6, 'en'))?.status, 'loading')
  assert.equal(store.pending, MAX_IN_FLIGHT + 1) // four on their way, the tapped one next
})
