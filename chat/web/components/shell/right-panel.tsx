'use client'

import { NameText } from '@/components/looks/look-name'
import useSWR from 'swr'
import { useState, type ReactNode } from 'react'
import { ChevronRight, MessageCircle } from 'lucide-react'
import type { Presence } from '@/lib/backend'
import type { TKey } from '@/lib/i18n'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { PresenceDot, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { flagEmoji } from '@/lib/format'
import { CountBadge } from '@/components/social/bits'
import { cn } from '@/lib/utils'
import { TrustBadge } from '@/components/trust/badge'
import { UserAvatar } from '@/components/looks/user-avatar'
import { RoomCountry } from '@/components/screens/rooms/room-country'
import { useRoomPreview } from '@/components/screens/rooms/room-preview'

/** 31: green idle, blue busy (searching, in a random chat or a room), black offline. */
const DOT: Record<Presence, string> = {
  online: 'bg-online', matching: 'bg-busy', chat: 'bg-busy', room: 'bg-busy', offline: 'bg-foreground',
}
const LABEL: Record<Presence, TKey> = {
  online: 'presence.online', matching: 'presence.matching', chat: 'presence.chat', room: 'presence.room', offline: 'presence.offline',
}
/** Idle first, then busy; names A to Z within each. */
const RANK: Record<Presence, number> = { online: 0, matching: 1, chat: 1, room: 1, offline: 2 }

export function RightPanel({ className }: { className?: string }) {
  const backend = useBackend()
  const { t, me, myPresence, openProfile, openChat, badges } = useApp()
  const preview = useRoomPreview()
  const { num } = useI18n()
  const { data: friends } = useSWR('friends', () => backend.friends())
  // What friends are doing: asked every 20 s while the window is shown (the server answers from its presence stamps).
  const { data: activity } = useSWR('friend-activity', () => backend.friendActivity(), { refreshInterval: 20_000, refreshWhenHidden: false })
  // The same list as the Rooms tab (one request serves both).
  const { data: rooms } = useSWR('voiceRooms', () => backend.voiceRooms(), {
    // The list is a server summary rebuilt every 5 s: every 30 s while the tab is shown is plenty, never hidden.
    refreshInterval: 30_000, refreshWhenHidden: false, focusThrottleInterval: 30_000, dedupingInterval: 5_000,
  })
  const [offlineOpen, setOfflineOpen] = useState(false)

  const presenceOf = new Map(activity?.map((a) => [a.id, a.presence]))
  const friendList = friends
    ?.filter((f) => f.state === 'friends')
    .map((f) => ({ ...f, presence: presenceOf.get(f.profile.id) ?? ('offline' as Presence) }))
    .sort((a, b) => RANK[a.presence] - RANK[b.presence] || a.profile.name.localeCompare(b.profile.name)) ?? null
  const online = friendList?.filter((f) => f.presence !== 'offline') ?? []
  const offline = friendList?.filter((f) => f.presence === 'offline') ?? []

  const row = (f: NonNullable<typeof friendList>[number]) => (
    <li key={f.profile.id} className="group flex items-center gap-3 rounded-xl p-2 transition-colors duration-150 hover:bg-muted">
      <button type="button" onClick={() => openProfile(f.profile.id)} className="flex min-w-0 flex-1 items-center gap-3 rounded-lg text-start">
        <PresenceAvatar presence={f.presence} label={t(LABEL[f.presence])}>
          <UserAvatar userId={f.profile.id} name={f.profile.name} src={f.profile.photoUrl} size={40} />
        </PresenceAvatar>
        <span className={cn('min-w-0', f.presence === 'offline' && 'opacity-60')}>
          <span className="flex items-center gap-1 truncate text-sm font-semibold">
            <NameText userId={f.profile.id} className="truncate">{f.profile.name}</NameText>
            <TrustBadge userId={f.profile.id} size={14} />
            <span aria-hidden>{flagEmoji(f.profile.country)}</span>
          </span>
          <PresenceText presence={f.presence} />
        </span>
      </button>
      <CountBadge n={f.threadId ? badges?.chats[f.threadId] ?? 0 : 0} />
      <Button size="icon-sm" variant="ghost" onClick={() => openChat(f.profile)} aria-label={t('panel.message', { name: f.profile.name })}>
        <MessageCircle className="size-4.5" aria-hidden />
      </Button>
    </li>
  )

  return (
    <aside
      className={cn(
        'sticky top-0 h-dvh w-80 shrink-0 flex-col gap-8 overflow-y-auto border-s border-border bg-background px-5 py-6 scrollbar-thin',
        className,
      )}
      aria-label={t('panel.activity')}
    >
      <section aria-labelledby="panel-friends" className="flex flex-col gap-2">
        {/* Me first, as in League of Legends: what my friends see I'm doing. */}
        <div className="flex items-center gap-3 rounded-2xl bg-muted/70 p-2.5">
          {/* Hiding my status (settings): friends see me offline, so my dot is the one they see. */}
          <PresenceAvatar presence={me.hideOnline ? 'offline' : myPresence} label={t(me.hideOnline ? 'presence.hidden' : LABEL[myPresence])}>
            <UserAvatar userId={me.id} name={me.name} src={me.photoUrl} size={40} />
          </PresenceAvatar>
          <span className="min-w-0">
            <span className="block truncate text-sm font-semibold">{me.name}</span>
            {me.hideOnline ? <span className="block truncate text-xs font-medium text-muted-foreground">{t('presence.hidden')}</span> : <PresenceText presence={myPresence} />}
          </span>
        </div>
        <h2 id="panel-friends" className="mt-2 flex items-center justify-between px-2 text-sm font-semibold">
          {t('panel.friends')}
          {friendList ? <span className="text-xs font-medium text-muted-foreground">{t('panel.onlineCount', { n: num(online.length) })}</span> : null}
        </h2>
        <ul className="flex flex-col">
          {friendList === null
            ? Array.from({ length: 3 }, (_, i) => (
                <li key={i} className="flex items-center gap-3 p-2">
                  <Skeleton className="size-10 rounded-full" />
                  <div className="flex flex-1 flex-col gap-1.5">
                    <Skeleton className="h-3 w-24" />
                    <Skeleton className="h-2.5 w-16" />
                  </div>
                </li>
              ))
            : friendList.length === 0
              ? (
                  <li className="rounded-2xl bg-muted px-4 py-5 text-center text-xs leading-relaxed text-muted-foreground">
                    {t('panel.noFriends')}
                  </li>
                )
              : online.length === 0
                ? <li className="px-2 py-2 text-xs text-muted-foreground">{t('panel.noneOnline')}</li>
                : online.map(row)}
        </ul>
        {offline.length ? (
          <>
            <button
              type="button"
              onClick={() => setOfflineOpen((o) => !o)}
              aria-expanded={offlineOpen}
              className="flex items-center gap-1 px-2 text-start text-xs font-semibold text-muted-foreground transition-colors duration-150 hover:text-foreground"
            >
              <ChevronRight className={cn('size-3.5 transition-transform duration-150 rtl:-scale-x-100', offlineOpen && 'rotate-90 rtl:rotate-90')} aria-hidden />
              {t('panel.offlineCount', { n: num(offline.length) })}
            </button>
            {offlineOpen ? <ul className="flex flex-col">{offline.map(row)}</ul> : null}
          </>
        ) : null}
      </section>

      <section aria-labelledby="panel-rooms" className="flex flex-col gap-2">
        <h2 id="panel-rooms" className="px-2 text-sm font-semibold">
          {t('panel.rooms')}
        </h2>
        <ul className="flex flex-col">
          {!rooms
            ? Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="my-1 h-12 w-full" />)
            : rooms.filter((r) => !r.refusal).length === 0
              ? (
                  <li className="rounded-2xl bg-muted px-4 py-5 text-center text-xs leading-relaxed text-muted-foreground">
                    {t('rooms.noneTitle')}
                  </li>
                )
              : rooms
                .filter((r) => !r.refusal)
                .sort((a, b) => b.members - a.members)
                .slice(0, 5)
                .map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => preview.open(r)}
                      className="flex w-full items-center gap-3 rounded-xl p-2 text-start transition-colors duration-150 hover:bg-muted"
                    >
                      {/* The room's cover photo (072) when it has one; its country small under the title. */}
                      {r.coverUrl ? (
                        <img src={r.coverUrl} alt="" className="size-11 shrink-0 rounded-xl object-cover" loading="lazy" decoding="async" />
                      ) : (
                        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-muted text-xl" aria-hidden>
                          {flagEmoji(r.host.country) || '🌐'}
                        </span>
                      )}
                      <span className="flex min-w-0 flex-1 flex-col items-start gap-0.5">
                        <span className="line-clamp-2 w-full text-sm leading-snug font-semibold break-words">{r.title}</span>
                        <RoomCountry iso={r.host.country} className="h-auto bg-transparent p-0 text-[11px] font-medium text-muted-foreground ring-0" />
                      </span>
                      <span className="flex items-center gap-1.5 text-xs tabular-nums text-muted-foreground">
                        <PresenceDot />
                        {num(r.members)}
                      </span>
                    </button>
                  </li>
                ))}
        </ul>
      </section>
      {preview.sheet}
    </aside>
  )
}

/** A face with its presence dot in the corner (green, blue or black). */
function PresenceAvatar({ presence, label, children }: { presence: Presence; label: string; children: ReactNode }) {
  return (
    <span className="relative shrink-0">
      {children}
      <span className={cn('absolute -inset-e-0.5 -bottom-0.5 size-3.5 rounded-full ring-2 ring-background', DOT[presence])} role="img" aria-label={label} />
    </span>
  )
}

/** "Online" in green, "In a room" and the like in blue, "Offline" in grey. */
function PresenceText({ presence }: { presence: Presence }) {
  const { t } = useI18n()
  return (
    <span className={cn('block truncate text-xs font-medium', presence === 'online' ? 'text-online' : presence === 'offline' ? 'text-muted-foreground' : 'text-busy')}>
      {t(LABEL[presence])}
    </span>
  )
}
