'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { ChevronDown, Search } from 'lucide-react'
import { COUNTRIES, type Country } from '@/lib/countries'
import { flagEmoji } from '@/lib/format'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

/** Country names in the user's language (Intl), with the English name as fallback and for search. */
function useCountryNames() {
  const { locale } = useI18n()
  return useMemo(() => {
    let names: Intl.DisplayNames | null = null
    try {
      names = new Intl.DisplayNames([locale], { type: 'region' })
    } catch {
      /* very old browser: English names */
    }
    return (c: Country) => names?.of(c.iso) ?? c.name
  }, [locale])
}

export function CountrySelect({ value, onChange }: { value: Country; onChange: (c: Country) => void }) {
  const { t } = useI18n()
  const nameOf = useCountryNames()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const rootRef = useRef<HTMLDivElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const listId = useId()

  const results = useMemo(() => {
    const q = query.trim().toLowerCase().replace(/^\+/, '')
    if (!q) return COUNTRIES
    return COUNTRIES.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        nameOf(c).toLowerCase().includes(q) ||
        c.dial.startsWith(q) ||
        c.iso.toLowerCase() === q,
    )
  }, [query, nameOf])

  useEffect(() => {
    if (!open) return
    setQuery('')
    setActive(0)
    requestAnimationFrame(() => searchRef.current?.focus())
    const onDown = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  useEffect(() => {
    listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [active])

  const choose = (c: Country) => {
    onChange(c)
    setOpen(false)
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={t('country.code', { name: nameOf(value), dial: value.dial })}
        className="flex h-12 items-center gap-2 rounded-xl border border-input bg-card pr-3 pl-3.5 text-[15px] font-semibold transition-colors duration-150 hover:bg-muted focus-visible:border-primary"
      >
        <span className="text-xl leading-none" aria-hidden>
          {flagEmoji(value.iso)}
        </span>
        <span className="tabular-nums" dir="ltr">+{value.dial}</span>
        <ChevronDown className={cn('size-4 text-muted-foreground transition-transform duration-150', open && 'rotate-180')} aria-hidden />
      </button>
      <AnimatePresence>
        {open ? (
          <m.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
            className="absolute top-full left-0 z-30 mt-2 w-72 max-w-[calc(100vw-2.5rem)] overflow-hidden rounded-2xl border border-border bg-card shadow-e3"
          >
            <div className="flex items-center gap-2 border-b border-border px-3.5">
              <Search className="size-4 text-muted-foreground" aria-hidden />
              <input
                ref={searchRef}
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value)
                  setActive(0)
                }}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowDown') {
                    e.preventDefault()
                    setActive((a) => Math.min(a + 1, results.length - 1))
                  } else if (e.key === 'ArrowUp') {
                    e.preventDefault()
                    setActive((a) => Math.max(a - 1, 0))
                  } else if (e.key === 'Enter') {
                    e.preventDefault()
                    if (results[active]) choose(results[active])
                  } else if (e.key === 'Escape') {
                    e.stopPropagation()
                    setOpen(false)
                  }
                }}
                placeholder={t('country.search')}
                aria-label={t('country.searchLabel')}
                role="combobox"
                aria-controls={listId}
                aria-expanded="true"
                aria-activedescendant={results[active] ? `${listId}-${results[active].iso}` : undefined}
                className="h-11 min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
            </div>
            <ul ref={listRef} id={listId} role="listbox" className="max-h-64 overflow-y-auto p-1.5 scrollbar-thin">
              {results.length === 0 ? (
                <li className="px-3 py-6 text-center text-sm text-muted-foreground">{t('country.none')}</li>
              ) : (
                results.map((c, i) => (
                  <li
                    key={c.iso}
                    id={`${listId}-${c.iso}`}
                    data-index={i}
                    role="option"
                    aria-selected={c.iso === value.iso}
                    onMouseEnter={() => setActive(i)}
                    onClick={() => choose(c)}
                    className={cn(
                      'flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm',
                      i === active && 'bg-muted',
                      c.iso === value.iso && 'font-semibold',
                    )}
                  >
                    <span className="text-lg leading-none" aria-hidden>
                      {flagEmoji(c.iso)}
                    </span>
                    <span className="min-w-0 flex-1 truncate">{nameOf(c)}</span>
                    <span className="tabular-nums text-muted-foreground" dir="ltr">+{c.dial}</span>
                  </li>
                ))
              )}
            </ul>
          </m.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}
