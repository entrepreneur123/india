'use client'

import { useId, useState } from 'react'
import { Check } from 'lucide-react'
import type { Mbti, SpokenLang } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { EMPTY_STYLE, LANGUAGES_MAX, MBTI_TYPES, SPOKEN_LANGS, STATUS_MAX, styleInput, toggleLanguage } from '@/lib/profile-style'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useLooksT } from '@/components/looks/looks-text'
import { useFeedT } from './feed-text'
import { ACCENT_TEXT, accentStyle } from './profile-bits'

/**
 * My profile details: status, MBTI (071) and languages. Saves them all at once (set_profile_style). The cover
 * photo and colour that were here are gone (040): frames, bought in the Looks shop, decorate profiles now.
 */
export function StyleEditor({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { lt } = useLooksT()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={lt('details.title')} description={lt('details.desc')} className="md:max-w-xl">
      {open ? <EditorBody onClose={onClose} /> : null}
    </Modal>
  )
}

function EditorBody({ onClose }: { onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, setMe } = useApp()
  const { t } = useI18n()
  const { ft } = useFeedT()
  const id = useId()
  const start = me.style ?? EMPTY_STYLE
  const [status, setStatus] = useState(start.status ?? '')
  const [languages, setLanguages] = useState<SpokenLang[]>(start.languages)
  const [mbti, setMbti] = useState<Mbti | null>(start.mbti)
  const [busy, setBusy] = useState(false)

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    // Prompts are no longer edited here (the owner, 2026-10-28); ones written before are kept as they are.
    const { input, problem } = styleInput({ status, languages, prompts: start.prompts, mbti })
    if (problem === 'status') return toast.show(ft('style.problem.status'), 'error')
    if (problem === 'answer') return toast.show(ft('style.problem.answer'), 'error')
    if (problem === 'languages') return toast.show(ft('style.languagesMax'), 'error')
    setBusy(true)
    try {
      setMe(await backend.setProfileStyle(input))
      toast.show(ft('style.saved'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const section = 'flex flex-col gap-2.5'
  const heading = 'text-sm font-bold'

  return (
    <form onSubmit={(e) => void save(e)} className="flex flex-col" style={accentStyle(null)}>
      <div className="flex flex-col gap-7 px-6 pt-4 pb-6">
        <section className={section}>
          <div className="flex items-center justify-between gap-2">
            <label htmlFor={`${id}-status`} className={heading}>
              {ft('style.status')}
            </label>
            <Count n={[...status].length} max={STATUS_MAX} />
          </div>
          <TextInput id={`${id}-status`} value={status} maxLength={STATUS_MAX} placeholder={ft('style.statusPlaceholder')} onChange={(e) => setStatus(e.target.value)} />
        </section>

        <section className={section}>
          <h3 className={heading} id={`${id}-mbti`}>
            MBTI
          </h3>
          {/* The 4×4 table everyone knows; tapping the chosen one again clears it. */}
          <ul className="grid grid-cols-4 gap-2" aria-labelledby={`${id}-mbti`}>
            {MBTI_TYPES.map((m) => (
              <li key={m}>
                <button
                  type="button"
                  aria-pressed={mbti === m}
                  onClick={() => setMbti((now) => (now === m ? null : m))}
                  className={cn(
                    'h-10 w-full rounded-full border text-sm font-bold tracking-wide transition-colors duration-150',
                    mbti === m ? cn('border-transparent bg-primary-soft', ACCENT_TEXT) : 'border-input text-foreground hover:bg-muted',
                  )}
                >
                  {m}
                </button>
              </li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">{ft('style.mbtiHint')}</p>
        </section>

        <section className={section}>
          <div className="flex items-center justify-between gap-2">
            <h3 className={heading} id={`${id}-langs`}>
              {ft('style.languages')}
            </h3>
            <Count n={languages.length} max={LANGUAGES_MAX} />
          </div>
          <ul className="flex flex-wrap gap-2" aria-labelledby={`${id}-langs`}>
            {SPOKEN_LANGS.map((l) => {
              const on = languages.includes(l.id)
              return (
                <li key={l.id}>
                  <button
                    type="button"
                    lang={l.id}
                    aria-pressed={on}
                    onClick={() => {
                      const next = toggleLanguage(languages, l.id)
                      if (next === false) toast.show(ft('style.languagesMax'), 'info')
                      else setLanguages(next)
                    }}
                    className={cn(
                      'inline-flex h-9 items-center gap-1.5 rounded-full border px-3.5 text-sm font-semibold transition-colors duration-150',
                      on
                        ? cn('border-transparent bg-primary-soft', ACCENT_TEXT)
                        : 'border-input text-foreground hover:bg-muted',
                    )}
                  >
                    {on ? <Check className="size-3.5" strokeWidth={3} aria-hidden /> : null}
                    {l.native}
                  </button>
                </li>
              )
            })}
          </ul>
          <p className="text-xs text-muted-foreground">{ft('style.languagesHint')}</p>
        </section>

      </div>
      <div className="sticky bottom-0 border-t border-border bg-card px-6 py-4">
        <Button type="submit" variant="brand" size="lg" className="w-full" loading={busy}>
          {t('common.save')}
        </Button>
      </div>
    </form>
  )
}

function Count({ n, max, className }: { n: number; max: number; className?: string }) {
  return <span className={cn('text-xs tabular-nums', n >= max ? 'text-primary-text' : 'text-muted-foreground', className)}>{n}/{max}</span>
}
