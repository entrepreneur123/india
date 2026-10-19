'use client'

import { motion, type HTMLMotionProps } from 'motion/react'
import { forwardRef } from 'react'
import { LoaderCircle as Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

type Variant = 'brand' | 'solid' | 'secondary' | 'outline' | 'ghost' | 'glass' | 'danger'
type Size = 'sm' | 'md' | 'lg' | 'icon' | 'icon-sm' | 'icon-lg'

const variants: Record<Variant, string> = {
  brand:
    'bg-brand text-primary-foreground shadow-[0_8px_24px_-8px_rgb(255_61_104/0.6)] hover:shadow-[0_14px_36px_-10px_rgb(255_61_104/0.75)]',
  solid: 'bg-foreground text-background hover:opacity-90',
  secondary: 'bg-muted text-foreground hover:bg-input',
  outline: 'border border-input bg-card text-foreground hover:bg-muted',
  ghost: 'text-foreground hover:bg-muted',
  glass:
    'bg-ink-foreground/10 text-ink-foreground backdrop-blur-md ring-1 ring-ink-foreground/10 hover:bg-ink-foreground/16',
  danger: 'bg-destructive text-primary-foreground hover:opacity-90',
}

const sizes: Record<Size, string> = {
  sm: 'h-9 px-3.5 text-sm gap-1.5 rounded-full',
  md: 'h-11 px-5 text-sm gap-2 rounded-full',
  lg: 'h-14 px-7 text-base gap-2.5 rounded-full',
  icon: 'size-11 rounded-full',
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
    <motion.button
      ref={ref}
      type={type}
      disabled={inactive}
      whileHover={inactive ? undefined : { y: -1.5 }}
      whileTap={inactive ? undefined : { scale: 0.95 }}
      transition={{ type: 'spring', stiffness: 520, damping: 28 }}
      className={cn(
        'relative inline-flex shrink-0 select-none items-center justify-center font-semibold transition-[background-color,box-shadow,opacity,color] duration-200 disabled:cursor-not-allowed disabled:opacity-50',
        variants[variant],
        sizes[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : null}
      {loading && size.startsWith('icon') ? null : (children as React.ReactNode)}
    </motion.button>
  )
})
