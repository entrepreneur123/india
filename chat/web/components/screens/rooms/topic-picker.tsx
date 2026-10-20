'use client'

import { Check } from 'lucide-react'
import type { RoomCategory } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { CATEGORIES, categoryLabel } from './room-text'

/** Room topics as a two-column grid of glyph + label (used by both the chat and the voice room forms). */
export function TopicPicker({ labelledBy, value, onChange }: { labelledBy: string; value: RoomCategory; onChange: (c: RoomCategory) => void }) {
  const { t } = useI18n()
  return (
    <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-labelledby={labelledBy}>
      {CATEGORIES.map((c) => {
        const on = value === c
        return (
          <button
            key={c}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onChange(c)}
            className={cn(
              'flex h-12 min-w-0 items-center gap-2.5 rounded-xl px-3 text-left text-sm font-semibold transition-[background-color,box-shadow] duration-150',
              on
                ? 'bg-primary-soft text-primary-text ring-2 ring-primary'
                : 'bg-card text-foreground shadow-[0_1px_3px_rgb(0_0_0/0.12)] ring-1 ring-border hover:bg-muted night:bg-muted night:shadow-none',
            )}
          >
            <TopicIcon topic={c} size={22} />
            <span className="min-w-0 flex-1 truncate">{categoryLabel(c, t)}</span>
            {on ? <Check className="size-4 shrink-0" aria-hidden /> : null}
          </button>
        )
      })}
    </div>
  )
}
