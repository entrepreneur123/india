'use client'

import { useEffect, useId, useState } from 'react'
import { Plus, X } from 'lucide-react'
import { checkPoll, POLL_MAX_OPTIONS, POLL_MIN_OPTIONS, POLL_OPTION_MAX, POLL_QUESTION_MAX, repeatedOptions } from '@/lib/voice-polls'
import { useI18n } from '@/components/i18n'
import { Field, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { PollIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { useRoomT } from './room-t'

/**
 * A new poll (hosts and co-hosts): the question and 2-4 answers, rows added and removed here. The same rules as the
 * server (lib/voice-polls.ts checkPoll): it can't be sent until they hold. Its own chunk: most people never open it.
 */
export function PollSheet({
  open,
  onClose,
  onCreate,
}: {
  open: boolean
  onClose: () => void
  onCreate: (question: string, options: string[]) => Promise<boolean>
}) {
  const { t, num } = useI18n()
  const { rt } = useRoomT()
  const id = useId()
  const [question, setQuestion] = useState('')
  const [options, setOptions] = useState<string[]>(['', ''])
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) {
      setQuestion('')
      setOptions(['', ''])
    }
  }, [open])

  const check = checkPoll(question, options)
  const repeated = repeatedOptions(options)
  const set = (i: number, v: string) => setOptions((list) => list.map((o, k) => (k === i ? v : o)))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (check.problem || busy) return
    setBusy(true)
    const ok = await onCreate(check.question, check.options)
    setBusy(false)
    if (ok) onClose()
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={rt('pollNew')} description={rt('pollDesc')}>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-5 p-6 pt-4">
        <Field
          label={rt('question')}
          htmlFor={`${id}-q`}
          trailing={<span className="text-xs text-muted-foreground tabular-nums">{num(question.length)}/{num(POLL_QUESTION_MAX)}</span>}
        >
          <TextInput
            id={`${id}-q`}
            value={question}
            maxLength={POLL_QUESTION_MAX}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder={rt('questionPlaceholder')}
            autoComplete="off"
            data-autofocus
          />
        </Field>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1.5 flex w-full items-center gap-2 text-sm font-semibold">
            <PollIcon size={18} /> {rt('options')}
          </legend>
          {options.map((o, i) => (
            <div key={i} className="flex items-center gap-2">
              <span
                aria-hidden
                className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-soft text-xs font-bold text-primary-text tabular-nums"
              >
                {num(i + 1)}
              </span>
              <label htmlFor={`${id}-o${i}`} className="sr-only">
                {rt('option', { n: num(i + 1) })}
              </label>
              <TextInput
                id={`${id}-o${i}`}
                value={o}
                maxLength={POLL_OPTION_MAX}
                onChange={(e) => set(i, e.target.value)}
                placeholder={rt('option', { n: num(i + 1) })}
                autoComplete="off"
                aria-invalid={repeated.has(i)}
                className={cn('h-11 min-w-0 flex-1', repeated.has(i) && 'border-destructive focus:border-destructive focus:ring-destructive')}
              />
              {options.length > POLL_MIN_OPTIONS ? (
                <Button
                  size="icon-sm"
                  variant="ghost"
                  onClick={() => setOptions((list) => list.filter((_, k) => k !== i))}
                  aria-label={rt('removeOption', { n: num(i + 1) })}
                >
                  <X className="size-4.5" aria-hidden />
                </Button>
              ) : null}
            </div>
          ))}
          {options.length < POLL_MAX_OPTIONS ? (
            <Button variant="ghost" size="sm" className="self-start text-primary-text" onClick={() => setOptions((list) => [...list, ''])}>
              <Plus className="size-4" aria-hidden /> {rt('addOption')}
            </Button>
          ) : null}
          <p className={cn('text-xs leading-relaxed', repeated.size ? 'font-medium text-destructive' : 'text-muted-foreground')} role={repeated.size ? 'alert' : undefined}>
            {repeated.size ? rt('same') : rt('optionsHint')}
          </p>
        </fieldset>

        <div className="flex gap-3">
          <Button variant="outline" className="flex-1" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="brand" className="flex-1" loading={busy} disabled={!!check.problem}>
            {rt('start')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
