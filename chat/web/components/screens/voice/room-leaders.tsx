'use client'

import useSWR from 'swr'
import { useBackend } from '@/lib/backend-context'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { TopicIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'

/** Gold, silver and bronze for the first three places. */
const MEDALS = ['#E8B13A', '#A9B4C2', '#C98552']

/** The games room's leaderboard: Ludo wins in this room, top 5. Read again when a game in the room ends. */
export function Leaderboard({ roomId, meId, after, className }: { roomId: string; meId: string; after: string | null; className?: string }) {
  const backend = useBackend()
  const { t, plural } = useI18n()
  const { data } = useSWR(['leaders', roomId, after], () => backend.voiceLeaders(roomId), { revalidateOnFocus: false })

  return (
    <section aria-labelledby={`leaders-${roomId}`} className={cn('rounded-3xl bg-card/80 p-4 ring-1 ring-border', className)}>
      <h2 id={`leaders-${roomId}`} className="flex items-center gap-2 text-sm font-bold">
        <TopicIcon topic="gaming" size={20} /> {t('games.leaders')}
      </h2>
      {!data ? (
        <div className="mt-3 flex flex-col gap-2" aria-busy>
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-9 w-full rounded-xl" />
          ))}
        </div>
      ) : data.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground">{t('games.noWins')}</p>
      ) : (
        <ol className="mt-2 flex flex-col">
          {data.map((l, i) => (
            <li key={l.profile.id} className={cn('flex items-center gap-3 rounded-xl px-1 py-1.5', l.profile.id === meId && 'bg-primary-soft/60')}>
              <span
                className="grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold tabular-nums"
                style={i < 3 ? { background: MEDALS[i], color: '#2b1a00' } : undefined}
                aria-label={t('games.rank', { n: i + 1 })}
              >
                {i < 3 ? i + 1 : <span className="text-muted-foreground">{i + 1}</span>}
              </span>
              <Avatar name={l.profile.name} src={l.profile.photoUrl} size={30} />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold">{l.profile.name}</span>
              <span className="shrink-0 text-xs font-semibold text-muted-foreground tabular-nums">{plural(l.wins, 'games.wins.one', 'games.wins.other')}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
