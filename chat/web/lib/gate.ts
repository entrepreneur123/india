// Errors that open a sheet instead of a message (035): 'verify_required' opens the identity check, 'manner_low'
// explains the manner score. The signed-in app listens (components/shell/app-shell.tsx); the toast hands these
// over when someone is listening, and shows them as text otherwise (on the sign-up screens nobody is).
import { BackendError } from './backend'
import { MANNERS } from './trust'

export type GateCode = 'verify_required' | 'manner_low'
const CODES = new Set<string>(MANNERS ? ['verify_required', 'manner_low'] : ['verify_required'])
const listeners = new Set<(code: GateCode) => void>()

export function onGate(listener: (code: GateCode) => void) {
  listeners.add(listener)
  return () => void listeners.delete(listener)
}

/** True when the error was handed to a sheet (so nothing else needs to show it). */
export function gate(err: unknown): boolean {
  if (!(err instanceof BackendError) || !CODES.has(err.code) || listeners.size === 0) return false
  listeners.forEach((l) => l(err.code as GateCode))
  return true
}
