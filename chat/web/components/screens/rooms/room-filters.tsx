'use client'

import { useMemo, useState } from 'react'
import { Check, Globe, Lock, Search, SlidersHorizontal } from 'lucide-react'
import { COUNTRIES } from '@/lib/countries'
import { flagEmoji } from '@/lib/format'
import { useI18n } from '@/components/i18n'
import { useCountryNames } from '@/components/screens/auth/country-select'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { cn } from '@/lib/utils'

/** The room search's details (28): the hosts' countries (none = every country) and whether rooms with a password show. */
export interface RoomFilters {
  countries: string[]
  locked: boolean
}

/** "🇮🇳 🇧🇩 +3" (the first two flags), or empty for every country. */
const flagsOf = (isos: readonly string[]) => isos.slice(0, 2).map(flagEmoji).join(' ') + (isos.length > 2 ? ` +${isos.length - 2}` : '')

/** The row under the search: what is chosen, and the sheet to change it. */
export function RoomFiltersButton({ value, onChange }: { value: RoomFilters; onChange: (f: RoomFilters) => void }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        className="flex h-10 max-w-full items-center gap-2 self-start rounded-full bg-muted px-4 text-sm font-semibold transition-colors duration-150 hover:bg-input/60"
      >
        <SlidersHorizontal className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <span>{t('rooms.options')}</span>
        <span className="truncate font-medium text-muted-foreground">
          {value.countries.length ? flagsOf(value.countries) : t('rooms.allCountries')}
          {value.locked ? null : ` · ${t('rooms.noLocked')}`}
        </span>
      </button>
      {open ? <FiltersSheet value={value} onClose={() => setOpen(false)} onChange={onChange} /> : null}
    </>
  )
}

function FiltersSheet({ value, onClose, onChange }: { value: RoomFilters; onClose: () => void; onChange: (f: RoomFilters) => void }) {
  const { t } = useI18n()
  const nameOf = useCountryNames()
  const [chosen, setChosen] = useState(value.countries)
  const [locked, setLocked] = useState(value.locked)
  const [query, setQuery] = useState('')
  // A to Z in the reader's language, the ones already chosen first (they don't jump while ticking).
  const sorted = useMemo(
    () => [...COUNTRIES].sort((a, b) => Number(value.countries.includes(b.iso)) - Number(value.countries.includes(a.iso)) || nameOf(a).localeCompare(nameOf(b))),
    [nameOf, value.countries],
  )
  const q = query.trim().toLowerCase()
  const results = q ? sorted.filter((c) => nameOf(c).toLowerCase().includes(q) || c.name.toLowerCase().includes(q) || c.iso.toLowerCase() === q) : sorted
  const toggle = (iso: string) => setChosen((c) => (c.includes(iso) ? c.filter((x) => x !== iso) : [...c, iso]))
  return (
    <Modal open onClose={onClose} variant="sheet" title={t('rooms.options')}>
      <div className="flex min-h-0 flex-col gap-3 px-4 pt-3 pb-4">
        <button
          type="button"
          role="switch"
          aria-checked={locked}
          onClick={() => setLocked((l) => !l)}
          className="flex w-full items-center gap-3 rounded-2xl border border-border px-4 py-3 text-start"
        >
          <Lock className="size-4.5 shrink-0 text-muted-foreground" aria-hidden />
          <span className="min-w-0 flex-1 text-sm font-semibold">{t('rooms.showLocked')}</span>
          <span aria-hidden className={cn('relative h-6 w-10 shrink-0 rounded-full transition-colors duration-150', locked ? 'bg-primary' : 'bg-input')}>
            <span className={cn('absolute top-0.5 size-5 rounded-full bg-white shadow transition-[inset-inline-start] duration-150', locked ? 'inset-s-[1.125rem]' : 'inset-s-0.5')} />
          </span>
        </button>
        <span className="flex items-baseline justify-between px-1 pt-1 text-sm font-semibold">
          {t('countries.label')}
          <span className="text-xs font-medium text-muted-foreground">{chosen.length ? t('rooms.countriesChosen', { n: chosen.length }) : t('rooms.allCountries')}</span>
        </span>
        <label className="flex h-11 items-center gap-2 rounded-full bg-muted px-4">
          <Search className="size-4 text-muted-foreground" aria-hidden />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('country.searchLabel')}
            aria-label={t('country.searchLabel')}
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          />
        </label>
        <ul className="h-[min(20rem,42dvh)] overflow-y-auto scrollbar-thin" aria-label={t('countries.label')}>
          {results.length === 0 ? <li className="px-3 py-6 text-center text-sm text-muted-foreground">{t('country.none')}</li> : null}
          {results.map((c) => {
            const on = chosen.includes(c.iso)
            return (
              <li key={c.iso}>
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={on}
                  onClick={() => toggle(c.iso)}
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-start text-sm transition-colors duration-150 hover:bg-muted"
                >
                  <span className="text-lg leading-none" aria-hidden>{flagEmoji(c.iso)}</span>
                  <span className={cn('min-w-0 flex-1 truncate', on && 'font-semibold')}>{nameOf(c)}</span>
                  <span className={cn('grid size-5.5 place-items-center rounded-full border border-input', on && 'border-primary bg-primary text-primary-foreground')}>
                    {on ? <Check className="size-3.5" strokeWidth={3} aria-hidden /> : null}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
        <div className="flex gap-2.5">
          <Button variant="secondary" size="lg" className="flex-1" onClick={() => setChosen([])} disabled={!chosen.length}>
            <Globe className="size-4.5" aria-hidden /> {t('rooms.allCountries')}
          </Button>
          <Button
            variant="brand"
            size="lg"
            className="flex-1"
            onClick={() => {
              onChange({ countries: chosen, locked })
              onClose()
            }}
            data-autofocus
          >
            {t('rooms.apply')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
