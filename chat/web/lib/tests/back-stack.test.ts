import test from 'node:test'
import assert from 'node:assert/strict'
import { backStack } from '../back-stack.ts'

/** A browser history: the page's entry, our guard entries, back/forward arriving later as popstate (like a browser). */
function browser() {
  const entries: ('page' | 'guard')[] = ['page']
  let at = 0
  let left = false // went back past the page: the site was left
  const tasks: (() => void)[] = []
  const s = backStack({
    push: () => {
      entries.splice(at + 1, Infinity, 'guard')
      at++
    },
    back: () => void tasks.push(() => go(-1)),
    later: (f) => void tasks.push(f),
  })
  const go = (d: number) => {
    if (at + d < 0) return void (left = true)
    if (at + d >= entries.length) return
    at += d
    s.moved(entries[at] === 'guard')
  }
  const run = () => {
    while (tasks.length) tasks.shift()!()
  }
  return {
    s,
    run,
    /** The user presses back (or forward). */
    press: (d = -1) => (go(d), run()),
    step: () => tasks.shift()!(),
    get depth() {
      return at
    },
    get left() {
      return left
    },
  }
}

/** A layer that closes when back reaches it (a sheet, a thread). */
function layer(b: ReturnType<typeof browser>, log: string[], name: string) {
  let close: (() => void) | null = null
  const open = () => {
    close = b.s.open(() => {
      log.push(name)
      shut()
    })
  }
  const shut = () => {
    close?.()
    close = null
  }
  return { open, shut, get isOpen() { return close !== null } }
}

test('back closes the top layer, every time, then leaves the site at home', () => {
  const b = browser()
  const log: string[] = []
  for (let round = 0; round < 3; round++) {
    const tab = layer(b, log, 'tab')
    const sheet = layer(b, log, 'sheet')
    tab.open()
    sheet.open()
    b.run()
    assert.equal(b.depth, 1) // one guard however many layers
    b.press()
    assert.deepEqual(log.splice(0), ['sheet'])
    assert.ok(tab.isOpen && !sheet.isOpen)
    assert.equal(b.depth, 1)
    b.press()
    assert.deepEqual(log.splice(0), ['tab'])
    assert.equal(b.depth, 0) // guard taken off with nothing open
    assert.equal(b.left, false)
  }
  b.press()
  assert.equal(b.left, true)
})

test('a layer closed by its own button takes the guard off; one replacing it keeps it', () => {
  const b = browser()
  const log: string[] = []
  const sheet = layer(b, log, 'sheet')
  for (let round = 0; round < 3; round++) {
    sheet.open()
    b.run()
    assert.equal(b.depth, 1)
    sheet.shut()
    b.run()
    assert.equal(b.depth, 0)
  }
  const a = layer(b, log, 'a')
  const c = layer(b, log, 'c')
  a.open()
  b.run()
  a.shut()
  c.open() // same moment: the guard stays
  b.run()
  assert.equal(b.depth, 1)
  b.press()
  assert.deepEqual(log, ['c'])
  assert.equal(b.depth, 0)
})

test('a layer that stays open on back (random chat asks first) keeps the guard', () => {
  const b = browser()
  let asked = 0
  b.s.open(() => asked++)
  b.run()
  for (let i = 1; i <= 3; i++) {
    b.press()
    assert.equal(asked, i)
    assert.equal(b.depth, 1)
    assert.equal(b.left, false)
  }
})

test('opening while our own back is on its way, and forward onto a stale guard', () => {
  const b = browser()
  const log: string[] = []
  const x = layer(b, log, 'x')
  const y = layer(b, log, 'y')
  x.open()
  b.run()
  x.shut()
  b.step() // the deferred check sends back(), not there yet
  y.open()
  b.run()
  assert.equal(b.depth, 1)
  assert.ok(y.isOpen)
  b.press()
  assert.deepEqual(log, ['y'])
  assert.equal(b.depth, 0)
  b.press(1) // forward onto the old guard: nothing is open, so it goes away again
  assert.equal(b.depth, 0)
})
