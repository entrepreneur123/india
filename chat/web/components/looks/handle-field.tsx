'use client'

import { useEffect, useState } from 'react'
import { useBackend } from '@/lib/backend-context'
import { handleProblem, handleSpelling, type HandleCheck } from '@/lib/handle'
import { Field, TextInput } from '@/components/ui/bits'
import { cn } from '@/lib/utils'
import { useLooksT } from './looks-text'

/** The live check of an ID being typed: null while empty, 'checking' while the server is asked. */
export type HandleState = HandleCheck | 'checking' | null

const CHECK_DELAY_MS = 400

/**
 * Checks an ID while it is typed: this device's rules at once (lib/handle.ts), then handle_check 400 ms after the
 * last change. `mine`: my current ID (unchanged, it needs no check).
 */
export function useHandleCheck(value: string, mine: string | null | undefined): HandleState {
  const backend = useBackend()
  const h = handleSpelling(value)
  const local = h ? (h === mine ? 'mine' : handleProblem(h, mine)) : null
  const [remote, setRemote] = useState<{ h: string; state: HandleCheck } | null>(null)

  useEffect(() => {
    if (!h || local) return
    let alive = true
    const timer = setTimeout(() => {
      backend.handleCheck(h).then(
        (state) => alive && setRemote({ h, state }),
        // Offline or refused: no verdict; saving will tell.
        () => alive && setRemote({ h, state: 'ok' }),
      )
    }, CHECK_DELAY_MS)
    return () => {
      alive = false
      clearTimeout(timer)
    }
  }, [backend, h, local])

  if (!h) return null
  if (local) return local
  return remote?.h === h ? remote.state : 'checking'
}

/** Whether an ID in this state can be saved. */
export const handleUsable = (state: HandleState) => state === 'ok' || state === 'mine'

/**
 * The ID field: "@" before it, the live verdict under it, and the rules as the hint (or `note`, e.g. when it can
 * change again; `blocked`: that note is why the typed ID can't be saved).
 */
export function HandleField({
  id,
  value,
  onChange,
  state,
  note,
  blocked,
}: {
  id: string
  value: string
  onChange: (v: string) => void
  state: HandleState
  note?: string
  blocked?: boolean
}) {
  const { lt } = useLooksT()
  const h = handleSpelling(value)
  const verdict =
    state === 'checking' ? lt('id.checking')
    : state === 'ok' ? lt('id.ok', { handle: h })
    : state === 'mine' ? lt('id.mine')
    : state === 'invalid' ? lt('id.invalid')
    : state === 'reserved' ? lt('id.reserved')
    : state === 'taken' ? lt('id.taken', { handle: h })
    : null
  const bad = blocked || state === 'invalid' || state === 'reserved' || state === 'taken'
  return (
    <Field label={lt('id.label')} htmlFor={id} hint={note ?? lt('id.hint')} error={blocked ? note : null}>
      <div className="relative">
        <span className="pointer-events-none absolute inset-y-0 start-4 flex items-center text-[15px] font-semibold text-muted-foreground" aria-hidden>
          @
        </span>
        <TextInput
          id={id}
          value={value}
          maxLength={40}
          onChange={(e) => onChange(e.target.value)}
          autoComplete="username"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          dir="auto"
          aria-invalid={bad || undefined}
          aria-describedby={verdict ? `${id}-verdict` : undefined}
          className={cn('ps-9', bad && 'border-destructive focus:border-destructive focus:ring-destructive')}
        />
      </div>
      {verdict && !blocked ? (
        <p id={`${id}-verdict`} role="status" className={cn('text-xs font-medium', bad ? 'text-destructive' : state === 'ok' ? 'text-online' : 'text-muted-foreground')}>
          {verdict}
        </p>
      ) : null}
    </Field>
  )
}
