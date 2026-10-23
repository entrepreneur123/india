'use client'

import { useId, useMemo, useState } from 'react'
import { Check, Plus, X } from 'lucide-react'
import type { ProfilePrompt, PromptQ, SpokenLang } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { ANSWER_MAX, EMPTY_STYLE, LANGUAGES_MAX, nextPrompt, PROMPT_QS, PROMPTS_MAX, SPOKEN_LANGS, STATUS_MAX, styleInput, toggleLanguage } from '@/lib/profile-style'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { TextArea, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useLooksT } from '@/components/looks/looks-text'
import { useFeedT } from './feed-text'
import { ACCENT_TEXT, accentStyle } from './profile-bits'

/**
 * My profile details: status, languages and up to 3 prompts. Saves them all at once (set_profile_style). The cover
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
  const [prompts, setPrompts] = useState<ProfilePrompt[]>(start.prompts.length ? start.prompts : [])
  const [busy, setBusy] = useState(false)

  const used = useMemo(() => prompts.map((p) => p.q), [prompts])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    const { input, problem } = styleInput({ status, languages, prompts })
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

        <section className={section}>
          <div className="flex items-center justify-between gap-2">
            <h3 className={heading}>{ft('style.prompts')}</h3>
            <Count n={prompts.length} max={PROMPTS_MAX} />
          </div>
          <p className="-mt-1 text-xs leading-relaxed text-muted-foreground">{ft('style.promptsHint')}</p>
          <ul className="flex flex-col gap-3">
            {prompts.map((p, i) => (
              <li
                key={i}
                className="relative flex flex-col gap-2 rounded-3xl bg-card p-4 ring-1 ring-border"
              >
                <div className="flex items-center gap-2">
                  <label className="sr-only" htmlFor={`${id}-q${i}`}>
                    {ft('style.promptQuestion')}
                  </label>
                  <select
                    id={`${id}-q${i}`}
                    value={p.q}
                    onChange={(e) => setPrompts((list) => list.map((x, j) => (j === i ? { ...x, q: e.target.value as PromptQ } : x)))}
                    className={cn('h-10 min-w-0 flex-1 rounded-full border border-input bg-card px-3 text-sm font-bold focus:border-primary focus:outline-none', ACCENT_TEXT)}
                  >
                    {PROMPT_QS.filter((q) => q === p.q || !used.includes(q)).map((q) => (
                      <option key={q} value={q}>
                        {ft(`prompt.${q}`)}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => setPrompts((list) => list.filter((_, j) => j !== i))}
                    className="grid size-9 shrink-0 place-items-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
                    aria-label={ft('style.promptRemove')}
                  >
                    <X className="size-4.5" aria-hidden />
                  </button>
                </div>
                <label className="sr-only" htmlFor={`${id}-a${i}`}>
                  {ft('style.promptAnswer')}
                </label>
                <TextArea
                  id={`${id}-a${i}`}
                  value={p.a}
                  maxLength={ANSWER_MAX}
                  placeholder={ft('style.promptAnswer')}
                  className="min-h-20 rounded-2xl text-base font-semibold"
                  onChange={(e) => setPrompts((list) => list.map((x, j) => (j === i ? { ...x, a: e.target.value } : x)))}
                />
                <Count n={[...p.a].length} max={ANSWER_MAX} className="self-end" />
              </li>
            ))}
          </ul>
          {nextPrompt(used) ? (
            <button
              type="button"
              onClick={() => {
                const q = nextPrompt(used)
                if (q) setPrompts((list) => [...list, { q, a: '' }])
              }}
              className="flex h-14 items-center justify-center gap-2 rounded-3xl border border-dashed border-input text-sm font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
            >
              <Plus className="size-4.5" aria-hidden /> {ft('style.promptAdd')}
            </button>
          ) : null}
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
