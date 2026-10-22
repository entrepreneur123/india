import test from 'node:test'
import assert from 'node:assert/strict'
import { CATALOGUE, COLOR_DESIGNS, FONT_DESIGNS, isItem, kindOf, NO_WEAR, parseLook, parseLooks, parseShop, wearOf, wearWith } from '../looks.ts'

test('the catalogue: 26 items as in the migration, each id starting with its kind', () => {
  const all = Object.values(CATALOGUE).flat()
  assert.equal(all.length, 26)
  for (const [kind, ids] of Object.entries(CATALOGUE)) for (const id of ids) assert.equal(kindOf(id), kind)
  for (const id of CATALOGUE.color) assert.ok(id in COLOR_DESIGNS, id)
  for (const id of CATALOGUE.font) assert.ok(id in FONT_DESIGNS, id)
  assert.equal(FONT_DESIGNS.font_pixel.scale, 0.8)
  assert.equal(isItem('badge_crown'), true)
  assert.equal(isItem('badge_unicorn'), false)
})

test('looks(): items this app can’t draw, or of the wrong kind, are left off; rows without an ID are skipped', () => {
  const look = parseLook({ id: 'u1', handle: 'lisa_4821', badge: 'badge_crown', color: 'badge_star', font: 'font_comic', frame: 'frame_fire' })
  assert.deepEqual(look, { id: 'u1', handle: 'lisa_4821', badge: 'badge_crown', color: null, font: null, frame: 'frame_fire' })
  assert.equal(parseLook({ id: 'u2', handle: '' }), null)
  assert.deepEqual(parseLooks([{ id: 'u3', handle: 'x_1' }, null, 'nope']).map((l) => l.id), ['u3'])
  assert.deepEqual(parseLooks(null), [])
})

test('shop(): known items in catalogue order, what I own, my look and when my ID can change', () => {
  const at = (ts: string) => Date.parse(ts)
  const shop = parseShop(
    {
      items: [
        { id: 'frame_fire', kind: 'frame', price_inr: 199 },
        { id: 'badge_crown', kind: 'badge', price_inr: 99 },
        { id: 'badge_new', kind: 'badge', price_inr: 10 },
        { id: 'color_gold', kind: 'font', price_inr: 49 },
      ],
      owned: ['badge_crown', 'hat_top'],
      look: { id: 'me', handle: 'me_1', badge: 'badge_crown', color: null, font: null, frame: null },
      handle_next: '2026-10-29T10:00:00Z',
    },
    at,
  )!
  assert.deepEqual(shop.items, [
    { id: 'badge_crown', kind: 'badge', priceInr: 99 },
    { id: 'frame_fire', kind: 'frame', priceInr: 199 },
  ])
  assert.deepEqual(shop.owned, ['badge_crown'])
  assert.equal(shop.look?.badge, 'badge_crown')
  assert.equal(shop.handleNext, Date.parse('2026-10-29T10:00:00Z'))
  assert.equal(parseShop({ items: [], owned: [], look: null, handle_next: null }, at)!.handleNext, null)
  assert.equal(parseShop(null, at), null)
})

test('wearing: one item per kind; the rest of the look stays', () => {
  const worn = wearOf({ badge: 'badge_crown', color: 'color_gold', font: null, frame: null })
  assert.deepEqual(wearWith(worn, 'color', 'color_rainbow'), { badge: 'badge_crown', color: 'color_rainbow', font: null, frame: null })
  assert.deepEqual(wearWith(worn, 'badge', null), { badge: null, color: 'color_gold', font: null, frame: null })
  assert.deepEqual(wearOf(null), NO_WEAR)
})
