import { useEffect, useRef } from 'react'

// Back (the browser's button, Android's back gesture, iOS's swipe) steps back inside the app instead of leaving the
// site (the owner, 2026-10-27). What is open — a sheet, a chat, a room, another tab — is a stack of layers. While any
// is open, one extra history entry of ours (the guard) sits above the page's own: back lands on the page's entry, the
// top layer handles it, and the guard goes back on for whatever is still open. When the last layer closes by itself
// the guard is taken off again, so at the home screen back leaves the site as it should.
// One guard, not an entry per layer: a layer may close from its own button, from under another, or not at all (back
// in a random chat asks "next or lobby?"), and no entry of ours is ever left behind to be stepped through.

interface Nav {
  push: () => void
  back: () => void
  later: (f: () => void) => void
}

export function backStack(nav: Nav) {
  const layers: (() => void)[] = []
  let guarded = false // the guard is the current entry
  let dropping = false // our own back() is on its way
  const guard = () => {
    if (guarded || dropping) return
    nav.push()
    guarded = true
  }
  return {
    /** A layer opens on top; the returned function is for when it closes. */
    open(onBack: () => void) {
      layers.push(onBack)
      guard()
      return () => {
        layers.splice(layers.indexOf(onBack), 1)
        // Later: a layer closing as another opens (a chat replacing a sheet) keeps the guard.
        nav.later(() => {
          if (layers.length || !guarded || dropping) return
          dropping = true
          nav.back()
        })
      }
    },
    /** The history moved (popstate); `onGuard`: it landed on our guard (forward, or the end of our own back). */
    moved(onGuard: boolean) {
      guarded = onGuard
      if (dropping) {
        dropping = false
        if (layers.length) guard()
      } else if (onGuard) {
        if (layers.length) return
        dropping = true
        nav.back()
      } else if (layers.length) {
        layers[layers.length - 1]()
        guard() // taken off again once nothing is left open
      }
    },
  }
}

// An object: Next's router copies its own keys into a pushed state (and reloads on a popstate without them).
let stack: ReturnType<typeof backStack> | null = null
function windowStack() {
  if (stack) return stack
  stack = backStack({ push: () => history.pushState({ yaariBack: true }, ''), back: () => history.back(), later: (f) => setTimeout(f, 0) })
  addEventListener('popstate', (e) => stack!.moved(!!e.state?.yaariBack))
  return stack
}

/** While `active`, back runs `onBack` (when this is the top layer) instead of leaving. */
export function useBack(active: boolean, onBack: () => void) {
  const ref = useRef(onBack)
  ref.current = onBack
  useEffect(() => (active ? windowStack().open(() => ref.current()) : undefined), [active])
}
