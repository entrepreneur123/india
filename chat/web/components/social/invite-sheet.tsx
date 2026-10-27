'use client'

import { useState } from 'react'
import useSWR from 'swr'
import { Check, ChevronRight, Link2 } from 'lucide-react'
import type { VoiceRoom } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { LookName, NameText } from '@/components/looks/look-name'
import { useSocialT } from './social-text'
import { UserAvatar } from '@/components/looks/user-avatar'

/** The room's link: its code when it is private (the code lets people in), else its id. */
export const roomLink = (room: Pick<VoiceRoom, 'id' | 'code' | 'isPrivate'>) =>
  `${location.origin}/r/${encodeURIComponent(room.isPrivate && room.code ? room.code : room.id)}`

/** A private room's link needs its code, which only the host has. */
export const canShareLink = (room: Pick<VoiceRoom, 'code' | 'isPrivate'>) => !room.isPrivate || !!room.code

/** Invite friends into the room I'm in (the list comes first), or copy (or share) its link under it. */
export function InviteSheet({ room, open, onClose }: { room: VoiceRoom; open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const { data: friends } = useSWR(open ? 'friends' : null, () => backend.friends())
  const [sent, setSent] = useState<Set<string>>(new Set())
  const [busy, setBusy] = useState<string | null>(null)
  const here = new Set(room.members.map((m) => m.profile.id))
  const list = friends?.filter((f) => f.state === 'friends' && !here.has(f.profile.id))

  const invite = async (userId: string) => {
    setBusy(userId)
    try {
      await backend.inviteToRoom(room.id, userId)
      setSent((s) => new Set(s).add(userId))
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  const copy = async () => {
    const url = roomLink(room)
    const nav = navigator as Navigator & { share?: (d: ShareData) => Promise<void> }
    // Phones: the system share sheet (WhatsApp...). Elsewhere, or when it is dismissed: the clipboard.
    if (nav.share && matchMedia('(pointer: coarse)').matches) {
      try {
        await nav.share({ title: room.title, text: st('invite.shareText', { room: room.title }), url })
        return
      } catch (err) {
        if ((err as Error)?.name === 'AbortError') return
      }
    }
    try {
      await navigator.clipboard.writeText(url)
      toast.show(st('invite.copied'), 'success')
    } catch {
      toast.show(url)
    }
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={st('invite.title')} description={st('invite.desc')}>
      <div className="flex flex-col pt-2 pb-4">
        <ul className="flex max-h-[50dvh] flex-col overflow-y-auto scrollbar-thin">
          {!list ? (
            Array.from({ length: 3 }, (_, i) => (
              <li key={i} className="flex items-center gap-3 px-6 py-2">
                <Skeleton className="size-10 rounded-full" />
                <Skeleton className="h-3.5 w-32" />
              </li>
            ))
          ) : list.length === 0 ? (
            <li className="mx-6 rounded-2xl bg-muted px-4 py-5 text-center text-sm leading-relaxed text-muted-foreground">{st('invite.none')}</li>
          ) : (
            list.map((f) => {
              const done = sent.has(f.profile.id)
              return (
                <li key={f.profile.id} className="flex items-center gap-3 px-6 py-2">
                  <UserAvatar userId={f.profile.id} name={f.profile.name} src={f.profile.photoUrl} size={40} />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <NameText userId={f.profile.id} className="truncate text-[15px] font-semibold">{f.profile.name}</NameText>
                    <LookName userId={f.profile.id} handle={f.profile.handle} className="text-xs font-semibold" />
                  </span>
                  <Button
                    size="sm"
                    variant={done ? 'secondary' : 'brand'}
                    disabled={done}
                    loading={busy === f.profile.id}
                    onClick={() => void invite(f.profile.id)}
                    aria-label={done ? `${st('invite.sent')}: ${f.profile.name}` : `${st('invite.button')}: ${f.profile.name}`}
                  >
                    {done ? <Check className="size-4" aria-hidden /> : null}
                    {done ? st('invite.sent') : st('invite.button')}
                  </Button>
                </li>
              )
            })
          )}
        </ul>
        {canShareLink(room) ? (
          <div className="mt-2 border-t border-border pt-2">
            <button
              type="button"
              onClick={() => void copy()}
              className="flex h-13 w-full items-center gap-4 px-6 text-start text-[15px] font-medium transition-colors duration-150 hover:bg-muted"
            >
              <Link2 className="size-5 shrink-0 text-muted-foreground" aria-hidden />
              <span className="min-w-0 flex-1 truncate">{st('invite.copyLink')}</span>
              <ChevronRight className="size-4.5 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
            </button>
            {room.isPrivate ? <p className="px-6 pb-1 text-xs text-muted-foreground">{st('invite.privateNote')}</p> : null}
          </div>
        ) : null}
      </div>
    </Modal>
  )
}
