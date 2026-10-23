'use client'

import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { INTERESTS, MAX_INTERESTS, MIN_INTERESTS, type Interest } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/** Pick 3-5 interests (first run, or from the profile). `onSkip` shows a "Skip for now" button instead of nothing. */
export function InterestsSheet({ open, onClose, onSkip }: { open: boolean; onClose: () => void; onSkip?: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, setMe } = useApp()
  const { st, tag } = useSocialT()
  const [picked, setPicked] = useState<Interest[]>(me.interests ?? [])
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) setPicked(me.interests ?? [])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const toggle = (i: Interest) =>
    setPicked((p) => (p.includes(i) ? p.filter((x) => x !== i) : p.length >= MAX_INTERESTS ? p : [...p, i]))
  const valid = picked.length >= MIN_INTERESTS && picked.length <= MAX_INTERESTS

  const save = async () => {
    if (!valid || busy) return
    setBusy(true)
    try {
      setMe(await backend.setInterests(picked))
      toast.show(st('interests.saved'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onSkip ?? onClose} variant="sheet" title={st('interests.title')} description={st('interests.body')}>
      <div className="flex flex-col gap-5 p-6 pt-4">
        <ul className="flex flex-wrap gap-2" aria-label={st('interests.label')}>
          {INTERESTS.map((i) => {
            const on = picked.includes(i)
            const full = !on && picked.length >= MAX_INTERESTS
            return (
              <li key={i}>
                <button
                  type="button"
                  aria-pressed={on}
                  disabled={full}
                  onClick={() => toggle(i)}
                  className={cn(
                    'flex h-10 items-center gap-1.5 rounded-full border px-4 text-sm font-semibold transition-colors duration-150',
                    on
                      ? 'border-transparent bg-primary text-primary-foreground'
                      : 'border-input bg-card text-foreground hover:bg-muted disabled:opacity-40 disabled:hover:bg-card',
                  )}
                >
                  {on ? <Check className="size-4" strokeWidth={2.6} aria-hidden /> : null}
                  {tag(i)}
                </button>
              </li>
            )
          })}
        </ul>
        <p className="text-sm text-muted-foreground tabular-nums" aria-live="polite">
          {st('interests.count', { n: picked.length })}
        </p>
        <div className="flex gap-3">
          {onSkip ? (
            <Button variant="outline" size="lg" className="flex-1" onClick={onSkip}>
              {st('interests.skip')}
            </Button>
          ) : null}
          <Button variant="brand" size="lg" className="flex-1" loading={busy} disabled={!valid} onClick={() => void save()}>
            {st('interests.save')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
