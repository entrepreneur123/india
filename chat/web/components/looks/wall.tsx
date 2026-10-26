import { wallClass } from '@/lib/walls'
import { cn } from '@/lib/utils'

/** A chat wall (036/067) behind what follows it: its parent is `relative isolate`. Nothing for none. */
export function Wall({ id, className }: { id: unknown; className?: string }) {
  const cls = wallClass(id)
  return cls ? <div aria-hidden className={cn('pointer-events-none absolute inset-0 -z-10 overflow-hidden', cls, className)} /> : null
}
