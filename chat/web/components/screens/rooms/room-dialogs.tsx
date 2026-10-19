'use client'

import { useEffect, useId, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Lock, Minus, Plus } from 'lucide-react'
import type { NewRoom, RoomCategory, Target } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Field, Segmented, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { CATEGORIES, categoryLabel } from './room-text'

const AGES = Array.from({ length: 83 }, (_, i) => 18 + i) // 18..100

export function CreateRoomDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (id: string, code: string) => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { mutate } = useSWRConfig()
  const id = useId()
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<RoomCategory>('chat')
  const [max, setMax] = useState(10)
  const [gender, setGender] = useState<Target>('any')
  const [minAge, setMinAge] = useState(18)
  const [maxAge, setMaxAge] = useState(100)
  const [isPrivate, setPrivate] = useState(false)
  const [busy, setBusy] = useState(false)
  const valid = title.trim().length >= 2 && title.trim().length <= 40 && minAge <= maxAge

  useEffect(() => {
    if (!open) return
    setTitle('')
    setCategory('chat')
    setMax(10)
    setGender('any')
    setMinAge(18)
    setMaxAge(100)
    setPrivate(false)
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true)
    const room: NewRoom = { title: title.trim(), category, maxMembers: max, genderRule: gender, minAge, maxAge, isPrivate }
    try {
      const made = await backend.createRoom(room)
      await mutate('rooms')
      onCreated(made.id, made.code)
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
          trailing={<span className="text-xs tabular-nums text-muted-foreground">{title.length}/40</span>}
        >
          <TextInput
            id={`${id}-t`}
            value={title}
            maxLength={40}
            placeholder={t('create.namePlaceholder')}
            onChange={(e) => setTitle(e.target.value)}
            data-autofocus
          />
        </Field>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold" id={`${id}-cat`}>
            {t('create.category')}
          </span>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-labelledby={`${id}-cat`}>
            {CATEGORIES.map((c) => {
              const on = category === c.id
              return (
                <button
                  key={c.id}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => setCategory(c.id)}
                  className={cn(
                    'flex h-9 items-center gap-1.5 rounded-lg border px-3 text-sm font-medium transition-colors duration-150',
                    on ? 'border-transparent bg-primary-soft text-primary-text' : 'border-input bg-card hover:bg-muted',
                  )}
                >
                  <span aria-hidden>{c.emoji}</span> {categoryLabel(c.id, t)}
                </button>
              )
            })}
          </div>
        </div>

        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-semibold" id={`${id}-max`}>
            {t('create.max')}
          </span>
          <div className="flex items-center gap-2" role="group" aria-labelledby={`${id}-max`}>
            <Button size="icon-sm" variant="secondary" onClick={() => setMax((m) => Math.max(2, m - 1))} aria-label={t('create.fewer')} disabled={max <= 2}>
              <Minus className="size-4" aria-hidden />
            </Button>
            <span className="w-8 text-center text-lg font-semibold tabular-nums" aria-live="polite">
              {max}
            </span>
            <Button size="icon-sm" variant="secondary" onClick={() => setMax((m) => Math.min(50, m + 1))} aria-label={t('create.more')} disabled={max >= 50}>
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
      const roomId = await backend.joinRoomCode(code)
      await mutate('rooms')
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
