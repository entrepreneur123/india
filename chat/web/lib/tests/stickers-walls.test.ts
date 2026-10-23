import test from 'node:test'
import assert from 'node:assert/strict'
import { STICKER_PACKS, WALLS } from '../looks.ts'
import { previewText, stickerBody, stickerOf, STICKERS } from '../stickers.ts'
import { wallOf, wallVars, WALL_DESIGNS } from '../walls.ts'

test('stickers: 8 per pack, each with an emoji and a caption', () => {
  assert.deepEqual(Object.keys(STICKERS).sort(), [...STICKER_PACKS].sort())
  for (const pack of STICKER_PACKS) {
    assert.equal(STICKERS[pack].length, 8, pack)
    for (const s of STICKERS[pack]) assert.ok(s.emoji && s.caption && s.caption.length <= 16, `${pack} ${s.caption}`)
  }
})

test('a sticker message is the whole body ":st:<pack>/<1-8>:", as the server checks it', () => {
  assert.equal(stickerBody('sticker_namaste', 1), ':st:namaste/1:')
  assert.equal(stickerBody('sticker_love', 8), ':st:love/8:')
  for (const pack of STICKER_PACKS) for (let n = 1; n <= 8; n++) {
    const st = stickerOf(stickerBody(pack, n))!
    assert.equal(st.pack, pack)
    assert.equal(st.n, n)
    assert.equal(st.art, STICKERS[pack][n - 1])
  }
  // A pack this app doesn't know yet is still a sticker (drawn plain), never shown as its code.
  assert.deepEqual(stickerOf(':st:diwali/3:'), { pack: 'sticker_diwali', n: 3, art: null })
  for (const text of [':st:namaste/0:', ':st:namaste/9:', ' :st:namaste/1:', ':st:namaste/1: hi', 'hi :st:chai/2:', ':st:Chai/2:', ':st:x/1:', '', null, undefined]) {
    assert.equal(stickerOf(text), null, String(text))
  }
})

test('lists and quotes show a sticker as its emoji and a label', () => {
  assert.equal(previewText(':st:chai/1:', 'Sticker'), '☕ Sticker')
  assert.equal(previewText(':st:diwali/1:', 'Sticker'), 'Sticker')
  assert.equal(previewText('see you :)', 'Sticker'), 'see you :)')
})

test('walls: every wall the shop sells is drawn for light and night screens; unknown ones are none', () => {
  assert.deepEqual(Object.keys(WALL_DESIGNS).sort(), [...WALLS].sort())
  for (const id of WALLS) {
    const w = wallOf(id)!
    assert.ok(w.light && w.dark && w.light !== w.dark, id)
    assert.deepEqual(wallVars(id), { '--wall': w.light, '--wall-n': w.dark })
  }
  for (const id of [null, undefined, 'wall_disco', 'toString', 42]) {
    assert.equal(wallOf(id), null, String(id))
    assert.equal(wallVars(id), undefined)
  }
})
