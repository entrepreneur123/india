import test from 'node:test'
import assert from 'node:assert/strict'
import { detectLang, isRtl, LANG_IDS, langOfTag, pluralText } from '../lang-tags.ts'
import en from '../locales/en.ts'
import roomEn from '../../components/screens/voice/locales/en.ts'
import socialEn from '../../components/social/locales/en.ts'
import feedEn from '../../components/social/locales/feed/en.ts'
import trustEn from '../../components/trust/locales/en.ts'

test('the first browser language the app has is chosen, with regional and old codes mapped', () => {
  assert.equal(detectLang(['fr-CA', 'en-US']), 'fr')
  assert.equal(detectLang(['xx', 'de-AT']), 'de')
  assert.equal(detectLang(['pt-PT']), 'pt-BR')
  assert.equal(detectLang(['zh-TW']), 'zh-Hant')
  assert.equal(detectLang(['zh-HK']), 'zh-Hant')
  assert.equal(detectLang(['zh-Hant-CN']), 'zh-Hant')
  assert.equal(detectLang(['zh-CN']), 'zh-Hans')
  assert.equal(detectLang(['zh']), 'zh-Hans')
  assert.equal(detectLang(['iw-IL']), 'he')
  assert.equal(detectLang(['in']), 'id')
  assert.equal(detectLang(['tl-PH']), 'fil')
  assert.equal(detectLang(['es-MX']), 'es')
  assert.equal(detectLang(['sv-SE', 'ja-JP']), 'ja') // Swedish is not an app language (yet)
  assert.equal(detectLang([]), 'en')
  assert.equal(detectLang(['']), 'en')
})

test('every app language maps back to itself, and only Arabic, Urdu, Persian and Hebrew are right to left', () => {
  for (const id of LANG_IDS) assert.equal(langOfTag(id), id, id)
  assert.deepEqual(LANG_IDS.filter(isRtl).sort(), ['ar', 'fa', 'he', 'ur'])
  assert.equal(LANG_IDS.length, 36)
})

// Languages kept complete by hand; the others may lag (missing keys read in English at runtime).
const COMPLETE = ['hi', 'bn', 'pt-BR', 'vi']
const SETS: [string, Record<string, string>, string][] = [
  ['lib/locales', en, '../locales'],
  ['room', roomEn, '../../components/screens/voice/locales'],
  ['social', socialEn, '../../components/social/locales'],
  ['feed', feedEn, '../../components/social/locales/feed'],
  ['trust', trustEn, '../../components/trust/locales'],
]

test('the fully kept languages have every English key, and no language file has a key English lacks', async () => {
  for (const [name, source, dir] of SETS) {
    for (const lang of COMPLETE) {
      const dict = ((await import(`${dir}/${lang}.ts`)) as { default: Record<string, string> }).default
      const missing = Object.keys(source).filter((k) => !(k in dict))
      const unknown = Object.keys(dict).filter((k) => !(k in source))
      assert.deepEqual(missing, [], `${name}/${lang} misses keys`)
      assert.deepEqual(unknown, [], `${name}/${lang} has unknown keys`)
      // Placeholders survive translation.
      for (const k of Object.keys(source)) {
        const want = (source[k].match(/\{\w+\}/g) ?? []).sort().join()
        const got = (dict[k].match(/\{\w+\}/g) ?? []).sort().join()
        assert.equal(got, want, `${name}/${lang} ${k} placeholders`)
      }
    }
  }
})

test('plural forms: the own category of the language, else other, else English', () => {
  const en = { 'x.one': '{n} friend', 'x.other': '{n} friends' }
  const ru = { 'x.one': '{n} друг', 'x.few': '{n} друга', 'x.many': '{n} друзей', 'x.other': '{n} друга' }
  const pick = (dict: Record<string, string>, locale: string, n: number) => pluralText(dict, en, new Intl.PluralRules(locale).select(n), 'x.one', 'x.other')
  assert.equal(pick(ru, 'ru', 1), '{n} друг')
  assert.equal(pick(ru, 'ru', 3), '{n} друга')
  assert.equal(pick(ru, 'ru', 5), '{n} друзей')
  assert.equal(pick({}, 'ru', 5), '{n} friends') // no Russian file yet
  assert.equal(pick({ 'x.other': '{n}人の友達' }, 'ja', 1), '{n}人の友達') // Japanese has no "one"
  assert.equal(pick({ 'x.one': '{n} ami', 'x.other': '{n} amis' }, 'fr', 0), '{n} ami') // French: 0 is singular
})
