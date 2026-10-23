'use client'

import { useMemo, useState } from 'react'
import { Check, ChevronRight, Globe, Search } from 'lucide-react'
import { COUNTRIES } from '@/lib/countries'
import { flagEmoji } from '@/lib/format'
import { useI18n } from '@/components/i18n'
import { useCountryNames } from '@/components/screens/auth/country-select'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { cn } from '@/lib/utils'

// The countries to meet in random chat (043, free for everyone): none = anywhere, at most 10. Remembered in this
// browser; storage can be blocked, then the choice lasts for the visit.
export const MAX_COUNTRIES = 10
const KEY = 'yaari_meet_countries'
const KNOWN = new Set(COUNTRIES.map((c) => c.iso))

export function savedCountries(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(v) ? v.filter((x): x is string => KNOWN.has(x)).slice(0, MAX_COUNTRIES) : []
  } catch {
    return []
  }
}
export function saveCountries(isos: string[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(isos))
  } catch {
    /* blocked: lasts for this visit */
  }
}

/** "🇰🇷 🇯🇵 +1" (the first two flags), or empty for anywhere. */
export const flagsOf = (isos: readonly string[]) =>
  isos.slice(0, 2).map(flagEmoji).join(' ') + (isos.length > 2 ? ` +${isos.length - 2}` : '')

/** The lobby's row under the gender choice: "Countries · Anywhere" (or the flags); opens the picker. */
export function CountriesButton({ value, onChange }: { value: string[]; onChange: (isos: string[]) => void }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        className="mt-3 flex h-12 w-full items-center gap-2.5 rounded-full bg-muted px-4 text-sm font-semibold transition-colors duration-150 hover:bg-input/60"
      >
        <Globe className="size-4.5 text-muted-foreground" aria-hidden />
        <span>{t('countries.label')}</span>
        <span className={cn('ms-auto truncate', value.length ? 'text-base' : 'text-muted-foreground')}>
          {value.length ? flagsOf(value) : t('countries.anywhere')}
        </span>
        <ChevronRight className="size-4 shrink-0 text-muted-foreground rtl:-scale-x-100" aria-hidden />
      </button>
      {open ? <CountriesSheet value={value} onClose={() => setOpen(false)} onChange={onChange} /> : null}
    </>
  )
}

function CountriesSheet({ value, onClose, onChange }: { value: string[]; onClose: () => void; onChange: (isos: string[]) => void }) {
  const { t } = useI18n()
  const nameOf = useCountryNames()
  const [chosen, setChosen] = useState(value)
  const [query, setQuery] = useState('')
  // A to Z in the reader's language, the ones already chosen first (they don't jump while ticking).
  const sorted = useMemo(
    () => [...COUNTRIES].sort((a, b) => Number(value.includes(b.iso)) - Number(value.includes(a.iso)) || nameOf(a).localeCompare(nameOf(b))),
    [nameOf, value],
  )
  const q = query.trim().toLowerCase()
  const results = q ? sorted.filter((c) => nameOf(c).toLowerCase().includes(q) || c.name.toLowerCase().includes(q) || c.iso.toLowerCase() === q) : sorted
  const full = chosen.length >= MAX_COUNTRIES
  const toggle = (iso: string) => setChosen((c) => (c.includes(iso) ? c.filter((x) => x !== iso) : full ? c : [...c, iso]))
  const done = (isos: string[]) => {
    onChange(isos)
    onClose()
  }
  return (
    <Modal open onClose={onClose} variant="sheet" title={t('countries.title')} description={t('countries.hint', { n: MAX_COUNTRIES })}>
      <div className="flex min-h-0 flex-col gap-3 px-4 pt-3 pb-4">
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
        <ul className="h-[min(22rem,50dvh)] overflow-y-auto scrollbar-thin" aria-label={t('countries.label')}>
          {results.length === 0 ? <li className="px-3 py-6 text-center text-sm text-muted-foreground">{t('country.none')}</li> : null}
          {results.map((c) => {
            const on = chosen.includes(c.iso)
            return (
              <li key={c.iso}>
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={on}
                  disabled={!on && full}
                  onClick={() => toggle(c.iso)}
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-start text-sm transition-colors duration-150 hover:bg-muted disabled:opacity-40"
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
          <Button variant="secondary" size="lg" className="flex-1" onClick={() => done([])}>
            <Globe className="size-4.5" aria-hidden /> {t('countries.anywhere')}
          </Button>
          <Button variant="brand" size="lg" className="flex-1" onClick={() => done(chosen)} data-autofocus>
            {t('countries.done')}
            {chosen.length ? <span className="tabular-nums opacity-80">· {chosen.length}</span> : null}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
