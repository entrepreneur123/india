'use client'

import useSWR from 'swr'
import { MessageCircle } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { PresenceDot, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { flagEmoji } from '@/lib/format'
import { kindTopic } from '@/components/screens/rooms/room-text'
import { TopicTile } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'

export function RightPanel({ className }: { className?: string }) {
  const backend = useBackend()
  const { t, openProfile, openThread, entitlements, openPaywall, openVoice } = useApp()
  const { plural, num } = useI18n()
  const { data: friends } = useSWR('friends', () => backend.friends())
  // The same list as the Rooms tab (one request serves both).
  const { data: rooms } = useSWR('voiceRooms', () => backend.voiceRooms(), {
    // The list is a server summary rebuilt every 5 s: every 30 s while the tab is shown is plenty, never hidden.
    refreshInterval: 30_000, refreshWhenHidden: false, focusThrottleInterval: 30_000, dedupingInterval: 5_000,
  })

  const friendList = friends?.filter((f) => f.state === 'friends') ?? null // presence isn't tracked, so no online dots

  return (
    <aside
      className={cn(
        'sticky top-0 h-dvh w-80 shrink-0 flex-col gap-8 overflow-y-auto border-l border-border bg-background px-5 py-6 scrollbar-thin',
        className,
      )}
      aria-label={t('panel.activity')}
    >
      <section aria-labelledby="panel-friends" className="flex flex-col gap-2">
        <div className="flex items-center justify-between px-2">
          <h2 id="panel-friends" className="text-sm font-semibold">
            {t('panel.friends')}
          </h2>
          {friendList ? (
            <span className="text-xs text-muted-foreground">
              {plural(friendList.length, 'panel.friendCount.one', 'panel.friendCount.other')}
            </span>
          ) : null}
        </div>
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
              : friendList.map((f) => (
                  <li key={f.profile.id} className="group flex items-center gap-3 rounded-xl p-2 transition-colors duration-150 hover:bg-muted">
                    <button
                      type="button"
                      onClick={() => openProfile(f.profile.id)}
                      className="flex min-w-0 flex-1 items-center gap-3 rounded-lg text-left"
                    >
                      <Avatar name={f.profile.name} src={f.profile.photoUrl} size={40} />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold">
                          {f.profile.name} <span aria-hidden>{flagEmoji(f.profile.country)}</span>
                        </span>
                        <span className="block truncate text-xs text-muted-foreground">{f.profile.city}</span>
                      </span>
                    </button>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      onClick={() => openThread(f.profile)}
                      aria-label={t('panel.message', { name: f.profile.name })}
                    >
                      <MessageCircle className="size-4.5" aria-hidden />
                    </Button>
                  </li>
                ))}
        </ul>
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
                      onClick={() => openVoice(r.id)}
                      className="flex w-full items-center gap-3 rounded-xl p-2 text-left transition-colors duration-150 hover:bg-muted"
                    >
                      <TopicTile topic={kindTopic(r.category)} size={40} />
                      <span className="min-w-0 flex-1 truncate text-sm font-semibold">{r.title}</span>
                      <span className="flex items-center gap-1.5 text-xs tabular-nums text-muted-foreground">
                        <PresenceDot />
                        {num(r.members)}
                      </span>
                    </button>
                  </li>
                ))}
        </ul>
      </section>

      {!entitlements.premium ? (
        <div className="mt-auto rounded-2xl border border-border bg-card p-5">
          <p className="text-xs font-semibold tracking-wide text-primary-text uppercase">{t('common.plus')}</p>
          <p className="mt-2 text-base font-semibold text-balance">{t('panel.plusTitle')}</p>
          <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{t('panel.plusBody')}</p>
          <Button variant="brand" size="sm" className="mt-4" onClick={openPaywall}>
            {t('panel.plusCta')}
          </Button>
        </div>
      ) : null}
    </aside>
  )
}
