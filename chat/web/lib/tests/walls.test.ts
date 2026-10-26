import test from 'node:test'
import assert from 'node:assert/strict'
import { WALLS } from '../looks.ts'
import { wallOf, wallVars, WALL_DESIGNS } from '../walls.ts'

test('walls: every wall the shop sells is drawn; unknown ones are none', () => {
  assert.deepEqual(Object.keys(WALL_DESIGNS).sort(), [...WALLS].sort())
  for (const id of WALLS) {
    const w = wallOf(id)!
    assert.ok(w, id)
    assert.deepEqual(wallVars(id), { background: w })
  }
  for (const id of [null, undefined, 'wall_disco', 'toString', 42]) {
    assert.equal(wallOf(id), null, String(id))
    assert.equal(wallVars(id), undefined)
  }
})
