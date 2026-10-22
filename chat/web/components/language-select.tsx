'use client'

import { Languages } from 'lucide-react'
import { LANGS, isLang } from '@/lib/i18n'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

/** Compact native <select> for the sign-up screen (Settings has the full list). */
export function LanguageSelect({ className }: { className?: string }) {
  const { lang, setLang, t } = useI18n()
  return (
    <label className={cn('relative inline-flex h-9 items-center gap-1.5 rounded-full border border-border pe-3 ps-3 text-sm font-medium transition-colors duration-150 focus-within:ring-2 focus-within:ring-ring hover:bg-muted', className)}>
      <Languages className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      <span className="sr-only">{t('settings.language')}</span>
      <select
        value={lang}
        onChange={(e) => isLang(e.target.value) && setLang(e.target.value)}
        className="cursor-pointer appearance-none bg-transparent pe-1 outline-none"
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
