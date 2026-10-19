'use client'

import { type HTMLMotionProps } from 'motion/react'
import * as m from 'motion/react-m'
import { forwardRef } from 'react'
import { LoaderCircle as Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

type Variant = 'brand' | 'solid' | 'secondary' | 'outline' | 'ghost' | 'glass' | 'danger'
type Size = 'sm' | 'md' | 'lg' | 'icon' | 'icon-sm' | 'icon-lg'

// Flat fills; hover darkens and lifts by one elevation step (Material filled/tonal buttons).
const variants: Record<Variant, string> = {
  brand: 'bg-primary text-primary-foreground hover:bg-primary-hover hover:shadow-e1',
  solid: 'bg-foreground text-background hover:opacity-90',
  secondary: 'bg-muted text-foreground hover:bg-input/60',
  outline: 'border border-input bg-card text-foreground hover:bg-muted',
  ghost: 'text-foreground hover:bg-muted',
  glass: 'bg-ink-foreground/10 text-ink-foreground ring-1 ring-ink-foreground/15 hover:bg-ink-foreground/16',
  danger: 'bg-destructive text-white hover:opacity-90 night:text-ink',
}

const sizes: Record<Size, string> = {
  sm: 'h-9 px-4 text-sm gap-1.5 rounded-full',
  md: 'h-10 px-5 text-sm gap-2 rounded-full',
  lg: 'h-12 px-6 text-[15px] gap-2 rounded-full',
  icon: 'size-10 rounded-full',
  'icon-sm': 'size-9 rounded-full',
  'icon-lg': 'size-14 rounded-full',
}

export interface PressableProps extends HTMLMotionProps<'button'> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, PressableProps>(function Button(
  { variant = 'secondary', size = 'md', loading, disabled, className, children, type = 'button', ...rest },
  ref,
) {
  const inactive = disabled || loading
  return (
    <m.button
      ref={ref}
      type={type}
      disabled={inactive}
      whileTap={inactive ? undefined : { scale: 0.97 }}
      transition={{ duration: 0.1 }}
      className={cn(
        'relative inline-flex shrink-0 select-none items-center justify-center font-semibold transition-[background-color,box-shadow,opacity,color] duration-150 disabled:cursor-not-allowed disabled:opacity-50',
        variants[variant],
        sizes[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
      {loading && size.startsWith('icon') ? null : (children as React.ReactNode)}
    </m.button>
  )
})
