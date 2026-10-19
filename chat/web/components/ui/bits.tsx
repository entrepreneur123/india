'use client'

import { motion } from 'motion/react'
import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import { Lock } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Skeleton({ className }: { className?: string }) {
  return (
    <div className={cn('relative overflow-hidden rounded-xl bg-muted', className)} aria-hidden>
      <div className="absolute inset-y-0 left-0 w-1/2 animate-shimmer bg-gradient-to-r from-transparent via-card/60 to-transparent" />
    </div>
  )
}

export function PresenceDot({ className, label }: { className?: string; label?: string }) {
  return (
    <span className={cn('relative inline-flex size-2', className)}>
      <span className="absolute inline-flex size-full animate-ping-soft rounded-full bg-online" aria-hidden />
      <span className="relative inline-flex size-2 rounded-full bg-online" aria-hidden />
      {label ? <span className="sr-only">{label}</span> : null}
    </span>
  )
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <span className="relative grid size-8 place-items-center rounded-[10px] bg-brand shadow-[0_6px_18px_-6px_rgb(255_61_104/0.7)]">
        <svg viewBox="0 0 24 24" className="size-4.5 text-primary-foreground" aria-hidden>
          <path
            d="M5 5.5l7 7.5 7-7.5M12 13v6"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <span className="text-xl font-extrabold tracking-tight">yaari</span>
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
}: {
  options: SegmentOption<T>[]
  value: T
  onChange: (v: T) => void
  label: string
  className?: string
  size?: 'md' | 'lg'
}) {
  const id = useId()
  return (
    <div role="radiogroup" aria-label={label} className={cn('inline-flex rounded-full bg-muted p-1', className)}>
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
              'relative flex flex-1 items-center justify-center gap-1.5 rounded-full font-semibold transition-colors',
              size === 'lg' ? 'h-11 px-5 text-sm' : 'h-9 px-4 text-sm',
              active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {active ? (
              <motion.span
                layoutId={`seg-${id}`}
                className="absolute inset-0 rounded-full bg-card shadow-[0_2px_10px_-2px_rgb(0_0_0/0.15)] ring-1 ring-border"
                transition={{ type: 'spring', stiffness: 500, damping: 36 }}
              />
            ) : null}
            <span className="relative z-10 flex items-center gap-1.5">
              {o.label}
              {o.locked ? <Lock className="size-3.5 text-accent" aria-label="Yaari Plus" /> : null}
            </span>
          </button>
        )
      })}
    </div>
  )
}

const fieldBase =
  'w-full rounded-2xl border border-input bg-card px-4 text-[15px] text-foreground transition-[border-color,box-shadow] placeholder:text-muted-foreground/70 focus:border-primary focus:shadow-[0_0_0_4px_rgb(255_61_104/0.14)] focus:outline-none disabled:opacity-60'

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
