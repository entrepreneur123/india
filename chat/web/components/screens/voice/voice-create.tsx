'use client'

import { useEffect, useId, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Hand, Armchair } from 'lucide-react'
import type { RoomCategory, SpeakMode } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Field, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { TopicPicker } from '@/components/screens/rooms/topic-picker'
import { cn } from '@/lib/utils'

const MAX_TITLE = 60

export function CreateVoiceDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { mutate } = useSWRConfig()
  const id = useId()
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<RoomCategory>('music')
  const [mode, setMode] = useState<SpeakMode>('approval')
  const [busy, setBusy] = useState(false)
  const valid = title.trim().length >= 2 && title.trim().length <= MAX_TITLE

  useEffect(() => {
    if (!open) return
    setTitle('')
    setCategory('music')
    setMode('approval')
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true)
    try {
      const roomId = await backend.createVoiceRoom({ title: title.trim(), category, mode })
      void mutate('voiceRooms')
      onCreated(roomId)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const modes: { value: SpeakMode; title: string; body: string; icon: typeof Hand }[] = [
    { value: 'approval', title: t('voice.modeApproval'), body: t('voice.modeApprovalBody'), icon: Hand },
    { value: 'open', title: t('voice.modeOpen'), body: t('voice.modeOpenBody'), icon: Armchair },
  ]

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={t('voice.createTitle')} description={t('voice.createDesc')}>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-6 p-6 pt-4">
        <Field
          label={t('voice.name')}
          htmlFor={`${id}-t`}
          trailing={<span className="text-xs tabular-nums text-muted-foreground">{title.length}/{MAX_TITLE}</span>}
        >
          <TextInput
            id={`${id}-t`}
            value={title}
            maxLength={MAX_TITLE}
            placeholder={t('voice.namePlaceholder')}
            onChange={(e) => setTitle(e.target.value)}
            data-autofocus
          />
        </Field>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold" id={`${id}-cat`}>
            {t('voice.topic')}
          </span>
          <TopicPicker labelledBy={`${id}-cat`} value={category} onChange={setCategory} />
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold" id={`${id}-mode`}>
            {t('voice.whoSpeaks')}
          </span>
          <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-labelledby={`${id}-mode`}>
            {modes.map((o) => {
              const on = mode === o.value
              const Icon = o.icon
              return (
                <button
                  key={o.value}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => setMode(o.value)}
                  className={cn(
                    'flex items-start gap-3 rounded-2xl border p-4 text-left transition-colors duration-150',
                    on ? 'border-primary bg-primary-soft' : 'border-input bg-card hover:bg-muted',
                  )}
                >
                  <Icon className={cn('mt-0.5 size-5 shrink-0', on ? 'text-primary-text' : 'text-muted-foreground')} aria-hidden />
                  <span className="min-w-0">
                    <span className={cn('block text-sm font-semibold', on && 'text-primary-text')}>{o.title}</span>
                    <span className="mt-0.5 block text-xs leading-relaxed text-muted-foreground">{o.body}</span>
                  </span>
                </button>
              )
            })}
          </div>
        </div>

        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!valid}>
          {t('voice.go')}
        </Button>
      </form>
    </Modal>
  )
}
