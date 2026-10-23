'use client'

import { useEffect, useId, useState } from 'react'
import { Camera } from 'lucide-react'
import { useI18n } from '@/components/i18n'
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
  const { t } = useI18n()
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
        <Avatar name={name || t('photo.you')} src={preview ?? currentUrl} size={80} />
        <span className="absolute -inset-e-0.5 -bottom-0.5 grid size-8 place-items-center rounded-full bg-primary text-primary-foreground ring-4 ring-background transition-colors duration-150 group-hover:bg-primary-hover">
          <Camera className="size-4" aria-hidden />
        </span>
        <input id={id} type="file" accept="image/*" className="sr-only" onChange={(e) => onChange(e.target.files?.[0] ?? null)} />
      </label>
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="text-sm font-semibold">{preview || currentUrl ? t('photo.change') : t('photo.add')}</span>
        <span className="text-xs leading-relaxed text-muted-foreground">{t('photo.hint')}</span>
      </div>
    </div>
  )
}
