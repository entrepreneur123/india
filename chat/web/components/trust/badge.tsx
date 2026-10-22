'use client'

import useSWR from 'swr'
import type { Backend, Trust } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { VerifiedMark } from './mark'

export { VerifiedMark }

// The verified mark next to names (035) and the small hook that reads anyone's badge and manners. Lists ask for many people at
// once: every useTrust() on screen within a frame goes out as one trust() call (at most 100 ids).

type Waiter = { id: string; resolve: (t: Trust | null) => void; reject: (e: unknown) => void }
const queues = new WeakMap<Backend, { waiting: Waiter[]; timer: ReturnType<typeof setTimeout> | null }>()

/** One person's trust, asked together with everyone else asked in the same moment. */
function loadTrust(backend: Backend, id: string): Promise<Trust | null> {
  let q = queues.get(backend)
  if (!q) queues.set(backend, (q = { waiting: [], timer: null }))
  const queue = q
  return new Promise((resolve, reject) => {
    queue.waiting.push({ id, resolve, reject })
    queue.timer ??= setTimeout(() => {
      const batch = queue.waiting.splice(0)
      queue.timer = null
      const ids = [...new Set(batch.map((w) => w.id))]
      for (let i = 0; i < ids.length; i += 100) {
        const part = new Set(ids.slice(i, i + 100))
        backend.trust([...part]).then(
          (list) => {
            const byId = new Map(list.map((t) => [t.id, t]))
            for (const w of batch) if (part.has(w.id)) w.resolve(byId.get(w.id) ?? null)
          },
          (err) => {
            for (const w of batch) if (part.has(w.id)) w.reject(err)
          },
        )
      }
    }, 16)
  })
}

/** Someone's badge and manners (null while loading, or when I may not see them). */
export function useTrust(userId: string | null | undefined): Trust | null {
  const backend = useBackend()
  const { data } = useSWR(userId ? ['trust', userId] : null, () => loadTrust(backend, userId!), {
    dedupingInterval: 120_000,
    revalidateOnFocus: false,
    shouldRetryOnError: false,
  })
  return data ?? null
}

/** The mark next to a name, when that person is verified (`verified`: already known, e.g. from a post). */
export function TrustBadge({ userId, verified, size = 15, className }: { userId?: string; verified?: boolean; size?: number; className?: string }) {
  const trust = useTrust(verified === undefined ? userId : null)
  if (!(verified ?? trust?.verified)) return null
  return <VerifiedMark size={size} className={className} />
}
