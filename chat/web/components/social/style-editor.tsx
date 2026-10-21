'use client'

import { useEffect, useId, useMemo, useState } from 'react'
import { Check, ImagePlus, Plus, Trash2, X } from 'lucide-react'
import { type Accent, BackendError, type ProfilePrompt, type PromptQ, type SpokenLang } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { ACCENTS, ANSWER_MAX, EMPTY_STYLE, LANGUAGES_MAX, nextPrompt, PROMPT_QS, PROMPTS_MAX, SPOKEN_LANGS, STATUS_MAX, styleInput, toggleLanguage } from '@/lib/profile-style'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { TextArea, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useFeedT } from './feed-text'
import { ACCENT_TEXT, AccentAvatar, accentStyle, ProfileCover } from './profile-bits'

/**
 * My profile's style: cover, colour, status, languages and up to 3 prompts, with a live preview on top. Saves the
 * whole style at once (set_profile_style takes all five).
 */
export function StyleEditor({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { ft } = useFeedT()
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={ft('style.title')} description={ft('style.desc')} className="md:max-w-xl">
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
  const [accent, setAccent] = useState<Accent | null>(start.accent)
  const [status, setStatus] = useState(start.status ?? '')
  const [languages, setLanguages] = useState<SpokenLang[]>(start.languages)
  const [prompts, setPrompts] = useState<ProfilePrompt[]>(start.prompts.length ? start.prompts : [])
  const [coverId, setCoverId] = useState<string | null>(start.cover?.id ?? null)
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [coverPreview, setCoverPreview] = useState<string | null>(null)
  const [busy, setBusy] = useState<'check' | 'save' | null>(null)

  useEffect(() => {
    if (!coverFile) return setCoverPreview(null)
    const url = URL.createObjectURL(coverFile)
    setCoverPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [coverFile])

  const cover = coverPreview ? { id: 'new', url: coverPreview } : coverId && start.cover?.id === coverId ? start.cover : null

  const pickCover = async (file: File | undefined) => {
    if (!file) return
    setBusy('check')
    try {
      // Checked on this device first, like every photo: a nude one is never uploaded.
      const { classifyImage } = await retryImport(() => import('@/lib/nudity'))
      if (await classifyImage(file)) {
        toast.show(ft('style.coverNude'), 'error')
        return
      }
      setCoverFile(file)
    } catch (err) {
      toast.error(err instanceof BackendError ? err : new BackendError('bad_media'))
    } finally {
      setBusy(null)
    }
  }

  const used = useMemo(() => prompts.map((p) => p.q), [prompts])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    const { input, problem } = styleInput({ coverId: coverFile ? null : coverId, accent, status, languages, prompts })
    if (problem === 'status') return toast.show(ft('style.problem.status'), 'error')
    if (problem === 'answer') return toast.show(ft('style.problem.answer'), 'error')
    if (problem === 'languages') return toast.show(ft('style.languagesMax'), 'error')
    setBusy('save')
    try {
      setMe(await backend.setProfileStyle(input, coverFile))
      toast.show(ft('style.saved'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  const section = 'flex flex-col gap-2.5'
  const heading = 'text-sm font-bold'

  return (
    <form onSubmit={(e) => void save(e)} className="flex flex-col" style={accentStyle(accent)}>
      <div className="flex flex-col gap-7 px-6 pt-4 pb-6">
        {/* Live preview */}
        <div className="flex flex-col items-center" aria-hidden>
          <ProfileCover style={{ cover, accent }} className="h-28 w-full" />
          <AccentAvatar name={me.name} src={me.photoUrl} size={72} accent={accent} ring="thick" className="-mt-11" />
          <p className="mt-2 text-base font-bold">{me.name}</p>
          {status.trim() ? (
            <p className={cn('mt-1 max-w-xs truncate rounded-full bg-[color-mix(in_srgb,var(--acc-via)_12%,var(--card))] px-3 py-1 text-xs font-semibold', ACCENT_TEXT)}>
              {status.trim()}
            </p>
          ) : null}
        </div>

        <section className={section}>
          <h3 className={heading}>{ft('style.cover')}</h3>
          <div className="flex flex-wrap items-center gap-2">
            <label
              htmlFor={`${id}-cover`}
              aria-busy={busy === 'check'}
              className={cn(
                'inline-flex h-10 cursor-pointer items-center gap-2 rounded-full border border-input bg-card px-5 text-sm font-semibold transition-colors duration-150 focus-within:ring-2 focus-within:ring-ring hover:bg-muted',
                busy === 'check' && 'pointer-events-none opacity-60',
              )}
            >
              <ImagePlus className={cn('size-4.5', busy === 'check' && 'animate-pulse')} aria-hidden />
              {busy === 'check' ? ft('compose.checking') : cover ? ft('style.coverChange') : ft('style.coverAdd')}
              <input id={`${id}-cover`} type="file" accept="image/*" className="sr-only" disabled={!!busy} onChange={(e) => void pickCover(e.target.files?.[0])} />
            </label>
            {cover ? (
              <Button
                variant="ghost"
                onClick={() => {
                  setCoverFile(null)
                  setCoverId(null)
                }}
              >
                <Trash2 className="size-4.5" aria-hidden /> {ft('style.coverRemove')}
              </Button>
            ) : null}
          </div>
          <p className="text-xs leading-relaxed text-muted-foreground">{ft('style.coverHint')}</p>
        </section>

        <section className={section}>
          <h3 className={heading} id={`${id}-acc`}>
            {ft('style.accent')}
          </h3>
          <div role="radiogroup" aria-labelledby={`${id}-acc`} className="grid grid-cols-5 gap-x-2 gap-y-3 sm:grid-cols-9">
            {[null, ...ACCENTS].map((a) => {
              const on = accent === a
              return (
                <button
                  key={a ?? 'brand'}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => setAccent(a)}
                  className="group flex flex-col items-center gap-1.5 rounded-2xl py-1 text-[11px] font-semibold text-muted-foreground"
                  style={accentStyle(a)}
                >
                  <span
                    className={cn(
                      'grid size-11 place-items-center rounded-full ring-offset-2 ring-offset-card transition-[box-shadow,transform] duration-150 group-hover:scale-105',
                      on ? 'ring-2 ring-[var(--acc-via)]' : 'ring-0',
                    )}
                    style={{ background: 'linear-gradient(135deg, var(--acc-from), var(--acc-via) 55%, var(--acc-to))' }}
                  >
                    {on ? <Check className="size-5 text-white drop-shadow" strokeWidth={3} aria-hidden /> : null}
                  </span>
                  <span className={cn('max-w-full truncate', on && 'text-foreground')}>{ft(`accent.${a ?? 'brand'}`)}</span>
                </button>
              )
            })}
          </div>
        </section>

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
                        ? cn('border-transparent bg-[color-mix(in_srgb,var(--acc-via)_14%,var(--card))]', ACCENT_TEXT)
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
                className="relative flex flex-col gap-2 rounded-3xl bg-[color-mix(in_srgb,var(--acc-via)_8%,var(--card))] p-4 ring-1 ring-[color-mix(in_srgb,var(--acc-via)_22%,transparent)]"
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
        <Button type="submit" variant="brand" size="lg" className="w-full" loading={busy === 'save'} disabled={busy === 'check'}>
          {t('common.save')}
        </Button>
      </div>
    </form>
  )
}

function Count({ n, max, className }: { n: number; max: number; className?: string }) {
  return <span className={cn('text-xs tabular-nums', n >= max ? 'text-primary-text' : 'text-muted-foreground', className)}>{n}/{max}</span>
}
