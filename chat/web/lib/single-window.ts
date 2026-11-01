'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

// One window at a time, as WhatsApp Web does (the owner, 2026-11-01): the same account in two browser windows used
// to run two random chats and two room connections side by side (LiveKit then cut the older one off). The window
// using Yaari holds a Web Lock; another window that opens finds it taken and offers "Use here", which steals the lock:
// the first window's hold is aborted and it shows the same offer. Locks are per browser profile and let go when a
// window closes. Without the Locks API (very old browsers) every window simply runs.

const LOCK = 'yaari-window'
export type WindowState = 'checking' | 'here' | 'elsewhere'

export function useSingleWindow(enabled: boolean) {
  const [state, setState] = useState<WindowState>('checking')
  const release = useRef<(() => void) | null>(null)

  const claim = useCallback((steal: boolean) => {
    if (typeof navigator === 'undefined' || !navigator.locks) return setState('here')
    release.current?.()
    navigator.locks
      .request(LOCK, steal ? { steal: true } : { ifAvailable: true }, (lock) => {
        if (!lock) return setState('elsewhere')
        setState('here')
        // Held until this window lets go (sign-out, unmount) or another window steals it.
        return new Promise<void>((done) => (release.current = done))
      })
      .catch(() => setState('elsewhere')) // stolen: "Use here" in another window
  }, [])

  useEffect(() => {
    if (!enabled) return
    claim(false)
    return () => {
      release.current?.()
      release.current = null
    }
  }, [enabled, claim])

  return { state: enabled ? state : 'here', useHere: () => claim(true) } as const
}
