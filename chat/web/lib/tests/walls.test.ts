import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { WALLS } from '../looks.ts'
import { wallClass, wallOf, WALL_DESIGNS } from '../walls.ts'

test('walls: every wall the shop sells is drawn in the CSS; unknown ones are none', () => {
  const css = readFileSync(new URL('../../app/globals.css', import.meta.url), 'utf8')
  assert.deepEqual(Object.keys(WALL_DESIGNS).sort(), [...WALLS].sort())
  for (const id of WALLS) {
    assert.equal(wallOf(id), id)
    const cls = `wall-${id.slice(5)}`
    assert.equal(wallClass(id), `wall ${cls}`)
    assert.ok(css.includes(`.${cls} {`), `${cls} in globals.css`)
  }
  for (const id of [null, undefined, 'wall_disco', 'wall_saffron', 'toString', 42]) {
    assert.equal(wallOf(id), null, String(id))
    assert.equal(wallClass(id), undefined)
  }
})
