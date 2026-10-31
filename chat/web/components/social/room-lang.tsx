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
        'relative inline-flex h-11 items-center rounded-full border border-input bg-card text-sm font-medium transition-colors duration-150 focus-within:ring-2 focus-within:ring-ring hover:bg-muted',
        className,
      )}
    >
      <Languages className="pointer-events-none absolute start-3.5 size-4.5 text-muted-foreground" aria-hidden />
      <span className="sr-only">{label}</span>
      {/* The whole pill is the select (a tap on the icon or the padding opens it too); long names end in "…". */}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-full min-w-0 flex-1 cursor-pointer appearance-none truncate rounded-full bg-transparent pe-4 ps-9.5 outline-none"
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
