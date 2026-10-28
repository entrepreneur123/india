'use client'

import { useState, type ImgHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/** A photo that appears whole once it has arrived (a short fade), not drawn top to bottom as it downloads. */
export function FadeImg({ className, onLoad, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  const [shown, setShown] = useState<unknown>(null)
  return (
    <img
      {...props}
      className={cn('transition-opacity duration-200', shown === props.src ? 'opacity-100' : 'opacity-0', className)}
      onLoad={(e) => {
        setShown(props.src)
        onLoad?.(e)
      }}
    />
  )
}
