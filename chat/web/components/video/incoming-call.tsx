'use client'

import { useEffect, useState } from 'react'
import { Phone, Video } from 'lucide-react'
import type { IncomingCall as Ringing, Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'

/** "Asha wants a voice call": their photo, the kind of call, Decline and Accept. In a chat, and app-wide for friends. */
export function CallRequestModal({ open, partner, video, busy, onAnswer }: {
  open: boolean; partner: Profile; video: boolean; busy?: boolean; onAnswer: (accept: boolean) => void
}) {
  const { t } = useI18n()
  const Icon = video ? Video : Phone
  return (
    <Modal open={open} onClose={() => onAnswer(false)} hideHeader className="max-w-sm">
      <div className="flex flex-col items-center gap-4 px-6 pt-8 pb-6 text-center">
        <span className="relative">
          <span className="absolute inset-0 animate-ping-soft rounded-full border-2 border-primary-text" aria-hidden />
          <Avatar name={partner.name} src={partner.photoUrl} size={88} />
        </span>
        <div>
          <h2 className="flex items-center justify-center gap-2 text-lg font-semibold">
            <Icon className="size-5 shrink-0 text-primary-text" aria-hidden />
            {t(video ? 'chat.videoIncoming' : 'chat.voiceIncoming', { name: partner.name })}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">{t(video ? 'chat.videoIncomingBody' : 'chat.voiceIncomingBody')}</p>
        </div>
        <div className="flex w-full gap-3">
          <Button variant="outline" size="lg" className="flex-1" disabled={busy} onClick={() => onAnswer(false)}>
            {t('chat.decline')}
          </Button>
          <Button variant="brand" size="lg" className="flex-1" loading={busy} onClick={() => onAnswer(true)} data-autofocus>
            {t('chat.accept')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/**
 * A friend's call rings anywhere in the app (053): a new 'call' line in my notifications brings this up; Accept opens
 * their thread with the call accepted. It goes away once the call stops asking (cancelled, rung out, answered on
 * another device). `hidden`: their thread (or the random chat) is on screen and asks itself.
 */
export function IncomingCall({ hidden, onAccepted }: { hidden: (matchId: string) => boolean; onAccepted: (caller: Profile, matchId: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const [call, setCall] = useState<Ringing | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(
    () =>
      backend.inboxSignals(() => {
        backend.incomingCall().then((c) => c && setCall((cur) => (cur?.matchId === c.matchId ? cur : c)), () => undefined)
      }),
    [backend],
  )
  const matchId = call?.matchId
  useEffect(() => {
    if (!matchId) return
    return backend.subscribe(matchId, (e) => e.type === 'video' && e.status !== 'requested_by_partner' && setCall(null))
  }, [backend, matchId])

  if (!call) return null
  const answer = async (accept: boolean) => {
    setBusy(true)
    try {
      await backend.answerVideo(call.matchId, accept)
      if (accept) onAccepted(call.caller, call.matchId)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
      setCall(null)
    }
  }
  return <CallRequestModal open={!hidden(call.matchId)} partner={call.caller} video={call.video} busy={busy} onAnswer={(a) => void answer(a)} />
}
