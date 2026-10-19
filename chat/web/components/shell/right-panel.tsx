'use client'

import { motion } from 'motion/react'
import useSWR from 'swr'
import { MessageCircle } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { Avatar } from '@/components/ui/avatar'
import { PresenceDot, Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { compactNumber, flagEmoji } from '@/lib/format'
import { cn } from '@/lib/utils'

export function RightPanel({ className, onOpenRoom }: { className?: string; onOpenRoom: (id: string) => void }) {
  const backend = useBackend()
  const { t, openProfile, openThread, entitlements, openPaywall } = useApp()
  const { data: friends } = useSWR('friends', () => backend.friends())
  const { data: rooms } = useSWR('rooms', () => backend.rooms(), { refreshInterval: 30_000 })

  const friendList = friends?.filter((f) => f.state === 'friends') ?? null // presence isn't tracked, so no online dots

  return (
    <aside
      className={cn(
        'sticky top-0 h-dvh w-80 shrink-0 flex-col gap-8 overflow-y-auto border-l border-border bg-background px-5 py-6 scrollbar-thin',
        className,
      )}
      aria-label="Activity"
    >
      <section aria-labelledby="panel-friends" className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 id="panel-friends" className="text-sm font-bold tracking-tight">
            {t('panel.friends')}
          </h2>
          {friendList ? (
            <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              {friendList.length} {friendList.length === 1 ? 'friend' : 'friends'}
            </span>
          ) : null}
        </div>
        <ul className="flex flex-col gap-1">
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
                    Friends you add from chats will show up here.
                  </li>
                )
              : friendList.map((f, i) => (
                  <motion.li
                    key={f.profile.id}
                    initial={{ opacity: 0, x: 12 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.05 }}
                    className="group flex items-center gap-3 rounded-2xl p-2 transition-colors hover:bg-muted"
                  >
                    <button
                      type="button"
                      onClick={() => openProfile(f.profile.id)}
                      className="flex min-w-0 flex-1 items-center gap-3 text-left"
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
                      className="opacity-70 group-hover:opacity-100"
                      onClick={() => openThread(f.profile)}
                      aria-label={`Message ${f.profile.name}`}
                    >
                      <MessageCircle className="size-4.5" aria-hidden />
                    </Button>
                  </motion.li>
                ))}
        </ul>
      </section>

      <section aria-labelledby="panel-rooms" className="flex flex-col gap-3">
        <h2 id="panel-rooms" className="text-sm font-bold tracking-tight">
          {t('panel.rooms')}
        </h2>
        <ul className="flex flex-col gap-1">
          {!rooms
            ? Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-12 w-full rounded-2xl" />)
            : [...rooms]
                .sort((a, b) => b.online - a.online)
                .slice(0, 5)
                .map((r) => (
                  <li key={r.id}>
                    <motion.button
                      type="button"
                      whileHover={{ x: 3 }}
                      onClick={() => onOpenRoom(r.id)}
                      className="flex w-full items-center gap-3 rounded-2xl p-2 text-left transition-colors hover:bg-muted"
                    >
                      <span className="grid size-10 place-items-center rounded-xl bg-muted text-xl" aria-hidden>
                        {r.emoji}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-sm font-semibold">{r.title}</span>
                      <span className="flex items-center gap-1.5 text-xs font-medium tabular-nums text-muted-foreground">
                        <PresenceDot />
                        {compactNumber(r.online)}
                      </span>
                    </motion.button>
                  </li>
                ))}
        </ul>
      </section>

      {!entitlements.premium ? (
        <div className="relative mt-auto overflow-hidden rounded-3xl bg-ink p-5 text-ink-foreground">
          <div
            className="pointer-events-none absolute -top-16 -right-10 size-44 animate-drift rounded-full opacity-60 blur-3xl"
            style={{ background: 'radial-gradient(circle, #ff3d68, transparent 65%)' }}
            aria-hidden
          />
          <p className="relative text-xs font-bold tracking-widest text-accent uppercase">Yaari Plus</p>
          <p className="relative mt-2 text-base font-bold text-balance">{t('panel.plusTitle')}</p>
          <p className="relative mt-1 text-sm leading-relaxed text-ink-foreground/70">{t('panel.plusBody')}</p>
          <Button variant="brand" size="sm" className="relative mt-4" onClick={openPaywall}>
            {t('panel.plusCta')}
          </Button>
        </div>
      ) : null}
    </aside>
  )
}
