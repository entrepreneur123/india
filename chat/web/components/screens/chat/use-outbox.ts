'use client'

import { useCallback, useEffect, useRef, type Dispatch, type SetStateAction } from 'react'

/** A line that may not be stored yet: the name its app gave it (091/092) and how sending it is going. */
export type Echoed = { id: number; clientId?: string | null; state?: 'sending' | 'failed' }

let echoSeq = 2 ** 52
/** An id for a line not stored yet, after every stored one (ids are a database sequence, far below this). */
export const echoId = () => ++echoSeq

/** Adds lines in id order; a stored line takes its local echo's place (matched by clientId). */
export function mergeLines<L extends Echoed>(list: L[], add: L[]) {
  const ids = new Set(add.map((l) => l.id))
  const names = new Set(add.map((l) => l.clientId))
  return [...list.filter((l) => !ids.has(l.id) && !(l.state && names.has(l.clientId))), ...add].sort((a, b) => a.id - b.id)
}

/**
 * My lines not stored yet (Matrix's send queue, Zulip's web/src/echo.ts): each shows at once and they are sent one
 * after another in the order written; a failure leaves the line with Retry and Delete (Mattermost), and coming back
 * online sends the failed ones again by themselves (the server stores each name once). `scope` is the open chat: a
 * line written in a chat no longer open is still sent, just not shown here.
 */
export function useOutbox<L extends Echoed, P>(
  scope: string,
  setLines: Dispatch<SetStateAction<L[] | null>>,
  post: (scope: string, payload: P, clientId: string) => Promise<L>,
  fail: (err: unknown) => void,
  /** Frees what the payload holds (a photo's preview) once it is sent or dropped. */
  release?: (payload: P) => void,
) {
  const outbox = useRef(new Map<string, { scope: string; payload: P; sending: boolean }>())
  const queue = useRef(Promise.resolve())
  const here = useRef(scope)
  here.current = scope
  const fns = useRef({ post, fail, release })
  fns.current = { post, fail, release }

  const mark = useCallback(
    (clientId: string, state: Echoed['state']) =>
      setLines((prev) => prev?.map((l) => (l.state && l.clientId === clientId ? { ...l, state } : l)) ?? prev),
    [setLines],
  )

  const retry = useCallback(
    (clientId: string) => {
      const line = outbox.current.get(clientId)
      if (!line || line.sending) return
      line.sending = true
      mark(clientId, 'sending')
      queue.current = queue.current.then(async () => {
        try {
          const stored = await fns.current.post(line.scope, line.payload, clientId)
          outbox.current.delete(clientId)
          if (line.scope === here.current) setLines((prev) => mergeLines(prev ?? [], [stored]))
          fns.current.release?.(line.payload)
        } catch (err) {
          line.sending = false
          if (line.scope !== here.current) return discard(clientId)
          mark(clientId, 'failed')
          fns.current.fail(err)
        }
      })
    },
    [mark, setLines], // discard reads only refs and setLines
  )

  const send = useCallback(
    (payload: P, echo: (clientId: string) => L) => {
      const clientId = crypto.randomUUID()
      outbox.current.set(clientId, { scope: here.current, payload, sending: false })
      setLines((prev) => mergeLines(prev ?? [], [echo(clientId)]))
      retry(clientId)
    },
    [retry, setLines],
  )

  function discard(clientId: string) {
    const line = outbox.current.get(clientId)
    if (line) fns.current.release?.(line.payload)
    outbox.current.delete(clientId)
    setLines((prev) => prev?.filter((l) => !(l.state && l.clientId === clientId)) ?? prev)
  }

  useEffect(() => {
    const again = () => outbox.current.forEach((line, clientId) => line.scope === here.current && retry(clientId))
    window.addEventListener('online', again)
    return () => window.removeEventListener('online', again)
  }, [retry])

  return { send, retry, discard }
}
