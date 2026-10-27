'use client'

import useSWR from 'swr'
import { Plus } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { UserAvatar } from '@/components/looks/user-avatar'
import { cn } from '@/lib/utils'
import { ScrollRow } from '@/components/ui/scroll-row'

/**
 * 32: friends' status messages over the feed, newest change first, like Instagram's notes over the DMs: a bubble over
 * each face. Mine comes first (or an invitation to write one).
 */
export function FriendNotes() {
  const backend = useBackend()
  const { t } = useI18n()
  const { me, openProfile, setTab } = useApp()
  const { data: friends } = useSWR('friends', () => backend.friends())
  const { data: activity } = useSWR('friend-activity', () => backend.friendActivity(), { refreshInterval: 60_000, refreshWhenHidden: false })
  const byId = new Map(friends?.filter((f) => f.state === 'friends').map((f) => [f.profile.id, f.profile]))
  const notes = (activity ?? [])
    .flatMap((a) => (a.status && byId.has(a.id) ? [{ ...a, profile: byId.get(a.id)! }] : []))
    .sort((a, b) => (b.statusAt ?? 0) - (a.statusAt ?? 0))
  const mine = me.style?.status?.trim()
  if (!activity) return null
  return (
    <section aria-label={t('notes.title')} className="-mx-4 sm:mx-0">
      <ScrollRow as="ul" className="flex gap-3 overflow-x-auto px-4 pt-1 pb-2 no-scrollbar sm:px-0">
        <Note
          key="me"
          name={t('notes.mine')}
          text={mine || t('notes.add')}
          muted={!mine}
          onOpen={() => setTab('me')}
          face={
            <span className="relative">
              <UserAvatar userId={me.id} name={me.name} src={me.photoUrl} size={64} />
              {mine ? null : (
                <span className="absolute -inset-e-0.5 bottom-0 grid size-5.5 place-items-center rounded-full bg-primary text-primary-foreground ring-2 ring-background" aria-hidden>
                  <Plus className="size-3.5" strokeWidth={3} />
                </span>
              )}
            </span>
          }
        />
        {notes.map((n) => (
          <Note
            key={n.id}
            name={n.profile.name}
            text={n.status!}
            onOpen={() => openProfile(n.id)}
            face={<UserAvatar userId={n.id} name={n.profile.name} src={n.profile.photoUrl} size={64} />}
          />
        ))}
      </ScrollRow>
    </section>
  )
}

function Note({ name, text, face, onOpen, muted }: { name: string; text: string; face: React.ReactNode; onOpen: () => void; muted?: boolean }) {
  return (
    <li className="shrink-0">
      <button type="button" onClick={onOpen} className="flex w-[5.5rem] flex-col items-center gap-1 text-center">
        {/* The bubble, with its little tail towards the face. */}
        <span
          dir="auto"
          className={cn(
            'relative z-10 -mb-2 max-w-full rounded-2xl bg-card px-2.5 py-1.5 text-[11px] leading-tight shadow-e1 ring-1 ring-border',
            muted ? 'text-muted-foreground' : 'font-medium text-foreground',
          )}
        >
          <span className="line-clamp-2 break-words">{text}</span>
        </span>
        {face}
        <span className="w-full truncate text-xs text-muted-foreground">{name}</span>
      </button>
    </li>
  )
}
