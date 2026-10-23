'use client'

import * as m from 'motion/react-m'
import { useRef } from 'react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/components/i18n'

const LENGTH = 6

export function OtpInput({
  value,
  onChange,
  onComplete,
  invalid,
  disabled,
}: {
  value: string
  onChange: (v: string) => void
  onComplete: (v: string) => void
  invalid?: boolean
  disabled?: boolean
}) {
  const { t } = useI18n()
  const refs = useRef<(HTMLInputElement | null)[]>([])
  const digits = Array.from({ length: LENGTH }, (_, i) => value[i] ?? '')

  const commit = (next: string, focusIndex: number) => {
    const clean = next.replace(/\D/g, '').slice(0, LENGTH)
    onChange(clean)
    refs.current[Math.min(focusIndex, LENGTH - 1)]?.focus()
    if (clean.length === LENGTH) onComplete(clean)
  }

  return (
    <m.div
      className="flex justify-between gap-2"
      dir="ltr"
      animate={invalid ? { x: [0, -6, 6, -4, 4, 0] } : { x: 0 }}
      transition={{ duration: 0.3 }}
      role="group"
      aria-label={t('otp.group')}
    >
      {digits.map((d, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el
          }}
          value={d}
          disabled={disabled}
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          maxLength={LENGTH}
          aria-label={t('otp.digit', { n: i + 1 })}
          autoFocus={i === 0}
          onFocus={(e) => e.currentTarget.select()}
          onChange={(e) => {
            const typed = e.target.value.replace(/\D/g, '')
            if (!typed) return
            if (typed.length > 1) {
              commit(value.slice(0, i) + typed, i + typed.length)
              return
            }
            const chars = digits.slice()
            chars[i] = typed
            commit(chars.join(''), i + 1)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace') {
              e.preventDefault()
              const chars = digits.slice()
              if (chars[i]) {
                chars[i] = ''
                onChange(chars.join('').slice(0, i) + chars.slice(i + 1).join(''))
              } else if (i > 0) {
                chars[i - 1] = ''
                onChange(chars.join(''))
                refs.current[i - 1]?.focus()
              }
            } else if (e.key === 'ArrowLeft' && i > 0) {
              refs.current[i - 1]?.focus()
            } else if (e.key === 'ArrowRight' && i < LENGTH - 1) {
              refs.current[i + 1]?.focus()
            }
          }}
          onPaste={(e) => {
            e.preventDefault()
            const pasted = e.clipboardData.getData('text').replace(/\D/g, '')
            if (pasted) commit(pasted, pasted.length)
          }}
          className={cn(
            'h-14 w-full min-w-0 rounded-xl border bg-card text-center text-xl font-semibold tabular-nums transition-[border-color,box-shadow] duration-150 focus:outline-none',
            invalid
              ? 'border-destructive'
              : d
                ? 'border-foreground/40 focus:border-primary focus:ring-1 focus:ring-primary'
                : 'border-input focus:border-primary focus:ring-1 focus:ring-primary',
          )}
        />
      ))}
    </m.div>
  )
}
