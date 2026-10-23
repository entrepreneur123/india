'use client'

import { useEffect, useId, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Lock, Minus, Plus } from 'lucide-react'
import type { NewVoiceRoom, RoomKind, Target } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Field, Segmented, TextArea, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { isRoomLang, RoomLangSelect } from '@/components/social/room-lang'
import { useSocialT } from '@/components/social/social-text'
import { CategoryPicker } from './topic-picker'

const AGES = Array.from({ length: 83 }, (_, i) => 18 + i) // 18..100
const MAX_TITLE = 40
const MAX_NOTE = 200
const MIN_SIZE = 2
const MAX_SIZE = 200
/** Finer steps for small rooms: 1 up to 10, 5 up to 50, then 10. */
const stepUp = (n: number) => Math.min(MAX_SIZE, n < 10 ? n + 1 : n < 50 ? n + 5 : n + 10)
const stepDown = (n: number) => Math.max(MIN_SIZE, n <= 10 ? n - 1 : n <= 50 ? n - 5 : n - 10)

/** A new room: its name, category, who may come in and an optional pinned announcement. */
export function CreateRoomDialog({
  open,
  initialCategory,
  onClose,
  onCreated,
}: {
  open: boolean
  initialCategory?: RoomKind
  onClose: () => void
  onCreated: (id: string, code: string, isPrivate: boolean) => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang: appLang } = useI18n()
  const { st } = useSocialT()
  const { mutate } = useSWRConfig()
  const { freeForRoom } = useApp()
  const id = useId()
  const [title, setTitle] = useState('')
  // A room is marked with one of the languages rooms can have; the app's own when it is one of them.
  const [lang, setLang] = useState<string>(isRoomLang(appLang) ? appLang : '')
  const [category, setCategory] = useState<RoomKind>('talk')
  const [max, setMax] = useState(50)
  const [gender, setGender] = useState<Target>('any')
  const [minAge, setMinAge] = useState(18)
  const [maxAge, setMaxAge] = useState(100)
  const [isPrivate, setPrivate] = useState(false)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const valid = title.trim().length >= 2 && title.trim().length <= MAX_TITLE && minAge <= maxAge

  useEffect(() => {
    if (!open) return
    setTitle('')
    setCategory(initialCategory ?? 'talk')
    setMax(50)
    setGender('any')
    setMinAge(18)
    setMaxAge(100)
    setPrivate(false)
    setNote('')
    setLang(isRoomLang(appLang) ? appLang : '')
  }, [open, initialCategory, appLang])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!valid || busy) return
    // Out of a random chat first (one live session at a time); staying in it keeps the form as it is.
    if (!(await freeForRoom())) return
    setBusy(true)
    const room: NewVoiceRoom = {
      title: title.trim(), category, maxMembers: max, genderRule: gender, minAge, maxAge, isPrivate, announcement: note.trim(), lang,
    }
    try {
      const made = await backend.createVoiceRoom(room)
      void mutate('voiceRooms')
      onCreated(made.id, made.code, isPrivate)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('create.title')} description={t('create.desc')}>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-6 p-6 pt-4">
        <Field
          label={t('create.name')}
          htmlFor={`${id}-t`}
          trailing={<span className="text-xs tabular-nums text-muted-foreground">{title.length}/{MAX_TITLE}</span>}
        >
          <TextInput
            id={`${id}-t`}
            value={title}
            maxLength={MAX_TITLE}
            placeholder={t('create.namePlaceholder')}
            onChange={(e) => setTitle(e.target.value)}
            data-autofocus
          />
        </Field>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold" id={`${id}-cat`}>
            {t('create.category')}
          </span>
          <CategoryPicker labelledBy={`${id}-cat`} value={category} onChange={setCategory} />
        </div>

        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-semibold">{st('rooms.lang')}</span>
          <RoomLangSelect value={lang} onChange={setLang} label={st('rooms.lang')} anyLabel={lang ? undefined : '—'} className="w-48" />
        </div>

        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-semibold" id={`${id}-max`}>
            {t('create.max')}
          </span>
          <div className="flex items-center gap-2" role="group" aria-labelledby={`${id}-max`}>
            <Button size="icon-sm" variant="secondary" onClick={() => setMax(stepDown)} aria-label={t('create.fewer')} disabled={max <= MIN_SIZE}>
              <Minus className="size-4" aria-hidden />
            </Button>
            <span className="w-10 text-center text-lg font-semibold tabular-nums" aria-live="polite">
              {max}
            </span>
            <Button size="icon-sm" variant="secondary" onClick={() => setMax(stepUp)} aria-label={t('create.more')} disabled={max >= MAX_SIZE}>
              <Plus className="size-4" aria-hidden />
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold">{t('create.who')}</span>
          <Segmented
            className="w-full"
            label={t('create.who')}
            value={gender}
            onChange={setGender}
            options={[
              { value: 'any', label: t('lobby.everyone') },
              { value: 'female', label: t('lobby.women') },
              { value: 'male', label: t('lobby.men') },
            ]}
          />
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold">{t('create.ages')}</span>
          <div className="flex items-center gap-3">
            <AgeSelect label={t('create.youngest')} value={minAge} onChange={(v) => { setMinAge(v); if (v > maxAge) setMaxAge(v) }} />
            <span className="text-muted-foreground">–</span>
            <AgeSelect label={t('create.oldest')} value={maxAge} onChange={(v) => { setMaxAge(v); if (v < minAge) setMinAge(v) }} />
          </div>
        </div>

        <Field
          label={t('create.announcement')}
          htmlFor={`${id}-n`}
          hint={t('create.announcementHint')}
          trailing={<span className="text-xs tabular-nums text-muted-foreground">{note.length}/{MAX_NOTE}</span>}
        >
          <TextArea
            id={`${id}-n`}
            value={note}
            maxLength={MAX_NOTE}
            rows={2}
            placeholder={t('create.announcementPlaceholder')}
            onChange={(e) => setNote(e.target.value)}
            className="min-h-20"
          />
        </Field>

        <label className="flex cursor-pointer items-start gap-3 rounded-2xl border border-border p-4">
          <input type="checkbox" checked={isPrivate} onChange={(e) => setPrivate(e.target.checked)} className="mt-0.5 size-5 shrink-0 accent-primary" />
          <span className="text-sm leading-relaxed">
            <span className="flex items-center gap-1.5 font-semibold">
              <Lock className="size-3.5" aria-hidden /> {t('create.private')}
            </span>
            <span className="text-muted-foreground">{t('create.privateBody')}</span>
          </span>
        </label>

        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!valid}>
          {t('create.submit')}
        </Button>
      </form>
    </Modal>
  )
}

function AgeSelect({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(Number(e.target.value))}
      className="h-11 flex-1 rounded-xl border border-input bg-card px-3 text-[15px] focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
    >
      {AGES.map((a) => (
        <option key={a} value={a}>
          {a === 100 ? '100+' : a}
        </option>
      ))}
    </select>
  )
}

export function JoinCodeDialog({ open, onClose, onJoined }: { open: boolean; onClose: () => void; onJoined: (id: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { mutate } = useSWRConfig()
  const id = useId()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) setCode('')
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (code.length !== 6 || busy) return
    setBusy(true)
    try {
      const roomId = await backend.joinVoiceCode(code)
      void mutate('voiceRooms')
      onJoined(roomId)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('join.title')} description={t('join.desc')}>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 p-6 pt-4">
        <label htmlFor={`${id}-c`} className="sr-only">
          {t('join.label')}
        </label>
        <input
          id={`${id}-c`}
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6))}
          placeholder="ABC123"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          data-autofocus
          dir="ltr"
          className="h-16 w-full rounded-xl border border-input bg-card text-center font-mono text-3xl font-bold tracking-[0.4em] focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
        />
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={code.length !== 6}>
          {t('join.submit')}
        </Button>
      </form>
    </Modal>
  )
}
