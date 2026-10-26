'use client'

import { useEffect, useMemo, useState } from 'react'
import useSWR from 'swr'
import { Heart, RefreshCw, Search, Users } from 'lucide-react'
import type { ExploreKind, ExplorePerson, FeedItem, PersonRow } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { COUNTRIES } from '@/lib/countries'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { LookName, useLook } from '@/components/looks/look-name'
import { Avatar, useCycle } from '@/components/ui/avatar'
import { Segmented, Skeleton, TextInput } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { cn } from '@/lib/utils'
import { UserAvatar } from '@/components/looks/user-avatar'
import { FriendNotes } from './friend-notes'

// Explore (059): search by ID or name on top; under it Feed (friends' posts and highlights), For you (50 random people
// of a country), Ranking (profile likes or friends) and Online. "Liked" (the heart) lists whom I liked.

type Section = 'feed' | 'recommend' | 'ranking' | 'online' | 'liked'

export function ExploreScreen() {
  const backend = useBackend()
  const { t, locale } = useI18n()
  const app = useApp()
  const [q, setQ] = useState('')
  const [asked, setAsked] = useState('')
  const [section, setSection] = useState<Section>('recommend')
  const [rank, setRank] = useState<'likes' | 'friends'>('likes')
  const [country, setCountry] = useState<string>(app.me?.country ?? '')
  const [round, setRound] = useState(0)
  useEffect(() => {
    const timer = setTimeout(() => setAsked(q.trim().replace(/^@/, '')), 300)
    return () => clearTimeout(timer)
  }, [q])

  const names = useMemo(() => {
    const dn = new Intl.DisplayNames([locale], { type: 'region' })
    return COUNTRIES.map((c) => ({ iso: c.iso, name: dn.of(c.iso) ?? c.name })).sort((a, b) => a.name.localeCompare(b.name, locale))
  }, [locale])

  const kind: ExploreKind | null = section === 'feed' ? null : section === 'ranking' ? rank : section
  const found = useSWR(asked ? ['explore-find', asked] : null, () => backend.searchHandles(asked), { keepPreviousData: true })
  const people = useSWR(!asked && kind ? ['explore', kind, country, kind === 'recommend' ? round : 0] : null, () => backend.explorePeople(kind!, country || null), {
    revalidateOnFocus: false,
  })
  const feed = useSWR(!asked && section === 'feed' ? 'explore-feed' : null, () => backend.exploreFeed())

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-6 sm:px-8">
        <div className="flex items-center gap-2">
          <label className="relative block flex-1">
            <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <TextInput
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t('explore.search')}
              aria-label={t('explore.search')}
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              maxLength={30}
              className="ps-9"
            />
          </label>
          <Button
            size="icon"
            variant={section === 'liked' ? 'brand' : 'secondary'}
            onClick={() => (setQ(''), setSection(section === 'liked' ? 'recommend' : 'liked'))}
            aria-label={t('explore.liked')}
            aria-pressed={section === 'liked'}
          >
            <Heart className="size-5" aria-hidden />
          </Button>
        </div>

        {asked ? (
          !found.data ? (
            <Skeleton className="h-14 w-full rounded-xl" />
          ) : found.data.length ? (
            <ul className="flex flex-col">
              {found.data.map((p) => (
                <PersonLine key={p.id} p={p} onOpen={() => app.openProfile(p.id)} />
              ))}
            </ul>
          ) : (
            <p className="py-10 text-center text-sm text-muted-foreground">{t('explore.noResults')}</p>
          )
        ) : (
          <>
            {section === 'liked' ? (
              <h1 className="text-lg font-semibold">{t('explore.liked')}</h1>
            ) : (
              <Segmented
                label={t('nav.explore')}
                value={section}
                onChange={setSection}
                options={[
                  { value: 'feed', label: t('explore.feed') },
                  { value: 'recommend', label: t('explore.recommend') },
                  { value: 'ranking', label: t('explore.ranking') },
                  { value: 'online', label: t('explore.online') },
                ]}
              />
            )}
            {section !== 'feed' && section !== 'liked' ? (
              <div className="flex flex-wrap items-center gap-2">
                <select
                  value={country}
                  onChange={(e) => setCountry(e.target.value)}
                  aria-label={t('explore.country')}
                  className="h-10 min-w-0 flex-1 rounded-full border border-input bg-card px-4 text-sm font-medium sm:flex-none"
                >
                  {/* No country of mine (rare: a number the server couldn't place): everyone, until one is picked. */}
                  {app.me?.country ? null : <option value="">🌐 {t('explore.country')}</option>}
                  {names.map((c) => (
                    <option key={c.iso} value={c.iso}>
                      {flagEmoji(c.iso)} {c.name}
                    </option>
                  ))}
                </select>
                {section === 'ranking' ? (
                  <Segmented
                    label={t('explore.ranking')}
                    value={rank}
                    onChange={setRank}
                    options={[
                      { value: 'likes', label: t('explore.byLikes') },
                      { value: 'friends', label: t('explore.byFriends') },
                    ]}
                  />
                ) : section === 'recommend' ? (
                  <Button variant="secondary" onClick={() => setRound((r) => r + 1)} loading={people.isValidating}>
                    <RefreshCw className="size-4" aria-hidden /> {t('explore.more')}
                  </Button>
                ) : null}
              </div>
            ) : null}
            {section === 'feed' ? (
              <>
                <FriendNotes />
                <Feed items={feed.data} />
              </>
            ) : (
              <People list={people.data} ranked={section === 'ranking'} rank={rank} />
            )}
          </>
        )}
      </div>
    </div>
  )
}

function PersonLine({ p, onOpen }: { p: PersonRow; onOpen: () => void }) {
  return (
    <li>
      <button type="button" onClick={onOpen} className="flex w-full items-center gap-3 rounded-xl px-2 py-2 text-start transition-colors hover:bg-muted">
        <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={44} />
        <span className="flex min-w-0 flex-col">
          <LookName userId={p.id} handle={p.handle} className="truncate text-[15px] font-semibold" />
          <span className="truncate text-sm text-muted-foreground">{p.name}</span>
        </span>
      </button>
    </li>
  )
}

function People({ list, ranked, rank }: { list: ExplorePerson[] | undefined; ranked: boolean; rank: 'likes' | 'friends' }) {
  const { openProfile } = useApp()
  const { t, num } = useI18n()
  if (!list) return <Skeleton className="h-60 w-full rounded-2xl" />
  if (!list.length) return <p className="py-10 text-center text-sm text-muted-foreground">{t('explore.empty')}</p>
  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
      {list.map((p, i) => (
        <li key={p.id}>
          <button type="button" onClick={() => openProfile(p.id)} className="group flex w-full flex-col overflow-hidden rounded-2xl border border-border bg-card text-start transition-shadow hover:shadow-e2">
            <span className="relative block aspect-square bg-muted">
              {p.photoUrl ? (
                <CardPhoto userId={p.id} src={p.photoUrl} />
              ) : (
                <span className="grid size-full place-items-center">
                  <Avatar name={p.name} size={72} />
                </span>
              )}
              {ranked ? (
                <span className="absolute top-2 start-2 grid size-7 place-items-center rounded-full bg-black/60 text-xs font-bold text-white">{num(i + 1)}</span>
              ) : null}
            </span>
            <span className="flex flex-col gap-0.5 p-2.5">
              <span className="truncate text-sm font-semibold">
                {p.name}, {num(p.age)} <span aria-hidden>{flagEmoji(p.country)}</span>
              </span>
              <LookName userId={p.id} handle={p.handle} className="truncate text-xs text-muted-foreground" />
              {ranked && p.count !== null ? (
                <span className="mt-0.5 flex items-center gap-1 text-xs font-semibold text-primary-text">
                  {rank === 'likes' ? <Heart className="size-3.5" aria-hidden /> : <Users className="size-3.5" aria-hidden />}
                  {num(p.count)}
                </span>
              ) : null}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}

/** A card's square photo, with the person's photo gradient (062) fading through it. */
function CardPhoto({ userId, src }: { userId: string; src: string }) {
  const photos = useLook(userId)?.cycle ?? [src]
  const at = useCycle(photos.length)
  return photos.map((u, i) => (
    <img
      key={u}
      src={u}
      alt=""
      loading="lazy"
      className={cn('absolute inset-0 size-full object-cover transition-opacity duration-700 motion-reduce:transition-none', i === at ? 'opacity-100' : 'opacity-0')}
    />
  ))
}

function Feed({ items }: { items: FeedItem[] | undefined }) {
  const { openProfile } = useApp()
  const { t, timeAgo } = useI18n()
  if (!items) return <Skeleton className="h-80 w-full rounded-2xl" />
  if (!items.length) return <p className="py-10 text-center text-sm text-muted-foreground">{t('explore.feedEmpty')}</p>
  return (
    <ul className="mx-auto flex w-full max-w-lg flex-col gap-4">
      {items.map((it) => (
        <li key={`${it.kind}:${it.id}`} className="overflow-hidden rounded-2xl border border-border bg-card">
          <button type="button" onClick={() => openProfile(it.user.id)} className="flex w-full items-center gap-3 p-3 text-start">
            <UserAvatar userId={it.user.id} name={it.user.name} src={it.user.photoUrl} size={36} />
            <span className="flex min-w-0 flex-col">
              <span className="truncate text-sm font-semibold">{it.user.name}</span>
              <span className="text-xs text-muted-foreground">{timeAgo(Date.parse(it.at))}</span>
            </span>
          </button>
          {it.photos.length ? (
            <div className={cn('flex snap-x snap-mandatory overflow-x-auto no-scrollbar', it.photos.length > 1 && 'gap-1')}>
              {it.photos.map((u) => (
                <img key={u} src={u} alt="" className="aspect-square w-full shrink-0 snap-center bg-muted object-cover" loading="lazy" />
              ))}
            </div>
          ) : null}
          {it.text ? <p className={cn('px-3 py-3 text-sm', it.kind === 'highlight' && 'font-semibold')}>{it.text}</p> : null}
        </li>
      ))}
    </ul>
  )
}
