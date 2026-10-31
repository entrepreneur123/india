'use client'

import { forwardRef, useEffect, useRef, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import { Lock } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-xl bg-muted motion-reduce:animate-none', className)} aria-hidden />
}

export function PresenceDot({ className, label }: { className?: string; label?: string }) {
  return (
    <span className={cn('relative inline-flex size-2', className)}>
      <span className="relative inline-flex size-2 rounded-full bg-online" aria-hidden />
      {label ? <span className="sr-only">{label}</span> : null}
    </span>
  )
}

/** The app mark: a white lowercase "y" (two strokes) on an orange rounded square. Same path as public/icon.svg. */
const Y_MARK = 'M6.5 5 12 14.6M17.5 5 9.5 19'

export function Logo({ className, compact }: { className?: string; compact?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)} dir="ltr">
      <span className="grid size-8 place-items-center rounded-[10px] bg-brand">
        <svg viewBox="0 0 24 24" className="size-5 text-white" aria-hidden>
          <path d={Y_MARK} fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" />
        </svg>
      </span>
      {compact ? <span className="sr-only">yaari</span> : <span className="text-xl font-extrabold tracking-tight text-brand">yaari</span>}
    </span>
  )
}

export interface SegmentOption<T extends string> {
  value: T
  label: ReactNode
  locked?: boolean
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
  label,
  className,
  size = 'md',
  lockedLabel,
  scroll,
}: {
  options: SegmentOption<T>[]
  value: T
  onChange: (v: T) => void
  label: string
  className?: string
  size?: 'md' | 'lg'
  /** Accessible name of the lock icon on paid options. */
  lockedLabel?: string
  /** Labels keep their width and the row slides sideways when they don't fit (four tabs in a long language). */
  scroll?: boolean
}) {
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (scroll) root.current?.querySelector('[aria-checked="true"]')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }, [scroll, value])
  return (
    <div ref={root} role="radiogroup" aria-label={label} className={cn('inline-flex rounded-full bg-muted p-1', scroll && 'overflow-x-auto no-scrollbar', className)}>
      {options.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(o.value)}
            className={cn(
              'relative flex min-w-0 flex-auto items-center justify-center gap-1.5 rounded-full px-2 font-semibold transition-colors duration-150 sm:px-3',
              size === 'lg' ? 'h-11 text-sm' : 'h-9 text-sm',
              scroll && 'shrink-0',
              active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            <span
              className={cn(
                'absolute inset-0 rounded-full bg-card shadow-e1 transition-opacity duration-150',
                active ? 'opacity-100' : 'opacity-0',
              )}
              aria-hidden
            />
            <span className="relative z-10 flex min-w-0 items-center gap-1.5">
              <span className="truncate">{o.label}</span>
              {o.locked ? <Lock className="size-3.5 shrink-0 text-primary-text" aria-label={lockedLabel} /> : null}
            </span>
          </button>
        )
      })}
    </div>
  )
}

const fieldBase =
  'w-full rounded-xl border border-input bg-card px-4 text-[15px] text-foreground transition-[border-color,box-shadow] duration-150 placeholder:text-muted-foreground focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none disabled:opacity-60'

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function TextInput(
  { className, ...rest },
  ref,
) {
  return <input ref={ref} className={cn(fieldBase, 'h-12', className)} {...rest} />
})

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function TextArea({ className, ...rest }, ref) {
    return <textarea ref={ref} className={cn(fieldBase, 'min-h-24 resize-none py-3 leading-relaxed', className)} {...rest} />
  },
)

export function Field({
  label,
  htmlFor,
  hint,
  error,
  children,
  trailing,
}: {
  label: string
  htmlFor: string
  hint?: ReactNode
  error?: string | null
  children: ReactNode
  trailing?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <label htmlFor={htmlFor} className="text-sm font-semibold">
          {label}
        </label>
        {trailing}
      </div>
      {children}
      {error ? (
        <p className="text-xs font-medium text-destructive" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs leading-relaxed text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  )
}
