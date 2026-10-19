'use client'

import { useEffect, useId, useState } from 'react'
import { Camera } from 'lucide-react'
import { Avatar } from '@/components/ui/avatar'

export function PhotoPicker({
  file,
  onChange,
  name,
  currentUrl,
}: {
  file: File | null
  onChange: (f: File | null) => void
  name: string
  currentUrl?: string | null
}) {
  const id = useId()
  const [preview, setPreview] = useState<string | null>(null)

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  return (
    <div className="flex items-center gap-4">
      <label htmlFor={id} className="group relative cursor-pointer rounded-full focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 focus-within:ring-offset-background">
        <Avatar name={name || 'You'} src={preview ?? currentUrl} size={80} />
        <span className="absolute -right-0.5 -bottom-0.5 grid size-8 place-items-center rounded-full bg-brand text-primary-foreground ring-4 ring-background transition-transform group-hover:scale-110">
          <Camera className="size-4" aria-hidden />
        </span>
        <input
          id={id}
          type="file"
          accept="image/*"
          className="sr-only"
          onChange={(e) => onChange(e.target.files?.[0] ?? null)}
        />
      </label>
      <div className="flex flex-col gap-0.5">
        <span className="text-sm font-semibold">{preview || currentUrl ? 'Change photo' : 'Add a photo'}</span>
        <span className="text-xs leading-relaxed text-muted-foreground">A clear photo of your face helps people trust you.</span>
      </div>
    </div>
  )
}
