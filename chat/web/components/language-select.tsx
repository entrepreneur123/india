'use client'

import { Languages } from 'lucide-react'
import { LANGS, isLang } from '@/lib/i18n'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

/** Compact native <select> for the sign-up screen (Settings has the full list). */
export function LanguageSelect({ className }: { className?: string }) {
  const { lang, setLang, t } = useI18n()
  return (
    <label className={cn('relative inline-flex h-9 items-center rounded-full border border-border text-sm font-medium transition-colors duration-150 focus-within:ring-2 focus-within:ring-ring hover:bg-muted', className)}>
      <Languages className="pointer-events-none absolute start-3 size-4 text-muted-foreground" aria-hidden />
      <span className="sr-only">{t('settings.language')}</span>
      {/* The whole pill is the select (as RoomLangSelect). */}
      <select
        value={lang}
        onChange={(e) => isLang(e.target.value) && setLang(e.target.value)}
        className="h-full cursor-pointer appearance-none rounded-full bg-transparent pe-4 ps-8.5 outline-none"
        // System font here: the option names include Vietnamese letters, which would otherwise make every
        // visitor download the Vietnamese font file just for this menu.
        style={{ fontFamily: 'system-ui, sans-serif' }}
      >
        {LANGS.map((l) => (
          <option key={l.id} value={l.id} lang={l.id} >
            {l.native}
          </option>
        ))}
      </select>
    </label>
  )
}
