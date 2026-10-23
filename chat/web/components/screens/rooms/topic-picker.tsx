'use client'

import type { RoomKind } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { CATEGORIES, categoryBody, categoryLabel, kindTopic } from './room-text'

/** The two categories as chips, and what the chosen one is like underneath. */
export function CategoryPicker({ labelledBy, value, onChange }: { labelledBy: string; value: RoomKind; onChange: (c: RoomKind) => void }) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col gap-2">
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
                'flex h-[4.5rem] min-w-0 flex-col items-center justify-center gap-1 rounded-2xl px-2 text-sm font-semibold transition-[background-color,box-shadow] duration-150',
                on
                  ? 'bg-primary-soft text-foreground ring-2 ring-primary'
                  : 'bg-card text-foreground ring-1 ring-border hover:bg-muted',
              )}
            >
              <TopicIcon topic={kindTopic(c)} size={26} />
              <span className="max-w-full truncate">{categoryLabel(c, t)}</span>
            </button>
          )
        })}
      </div>
      <p className="text-xs leading-relaxed text-muted-foreground" aria-live="polite">
        {categoryBody(value, t)}
      </p>
    </div>
  )
}
