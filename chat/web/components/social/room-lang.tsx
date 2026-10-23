'use client'

import { Languages } from 'lucide-react'
import { LANGS, type Lang } from '@/lib/i18n'
import { cn } from '@/lib/utils'

/** The languages a room can be marked with (the server's voice_lang list), named in their own language. */
export const ROOM_LANGS: Lang[] = ['en', 'hi', 'bn', 'pt-BR', 'vi']
const ROOM_LANG_LIST = LANGS.filter((l) => ROOM_LANGS.includes(l.id))
export const isRoomLang = (id: string | null | undefined) => !!id && (ROOM_LANGS as string[]).includes(id)
export const langName = (id: string | null | undefined) => LANGS.find((l) => l.id === id)?.native ?? null

/** A native <select> of room languages; `anyLabel` adds an "all languages" choice (value ''). */
export function RoomLangSelect({
  value,
  onChange,
  label,
  anyLabel,
  className,
}: {
  value: string
  onChange: (v: string) => void
  label: string
  anyLabel?: string
  className?: string
}) {
  return (
    <label
      className={cn(
        'relative inline-flex h-11 items-center gap-2 rounded-full border border-input bg-card pe-4 ps-3.5 text-sm font-medium transition-colors duration-150 focus-within:ring-2 focus-within:ring-ring hover:bg-muted',
        className,
      )}
    >
      <Languages className="size-4.5 shrink-0 text-muted-foreground" aria-hidden />
      <span className="sr-only">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="min-w-0 flex-1 cursor-pointer appearance-none bg-transparent outline-none"
        style={{ fontFamily: 'system-ui, sans-serif' }}
      >
        {anyLabel !== undefined ? <option value="">{anyLabel}</option> : null}
        {ROOM_LANG_LIST.map((l) => (
          <option key={l.id} value={l.id} lang={l.id}>
            {l.native}
          </option>
        ))}
      </select>
    </label>
  )
}
