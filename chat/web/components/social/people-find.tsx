'use client'

import { useEffect, useRef, useState } from 'react'
import useSWR from 'swr'
import { Search, X } from 'lucide-react'
import type { PersonRow } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { LookName, NameText } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { Skeleton } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { useI18n } from '@/components/i18n'
import { useSocialT } from '@/components/social/social-text'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { UserAvatar } from '@/components/looks/user-avatar'

// The search bar over Recent and Explore, with my recent searches (131); Settings' list of people I blocked.

export function PersonLine({ p, children, onOpen }: { p: PersonRow; children?: React.ReactNode; onOpen?: () => void }) {
  const body = (
    <>
      <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={44} />
      <span className="flex min-w-0 flex-1 flex-col">
        <LookName userId={p.id} handle={p.handle} className="truncate text-[15px] font-semibold" />
        <NameText userId={p.id} className="truncate text-sm text-muted-foreground">{p.name}</NameText>
      </span>
    </>
  )
  const row = 'flex min-w-0 flex-1 items-center gap-3 rounded-xl px-2 py-2 text-start'
  return (
    <li className="flex items-center gap-2">
      {onOpen ? (
        <button type="button" onClick={onOpen} className={cn(row, 'transition-colors hover:bg-muted')}>
          {body}
        </button>
      ) : (
        <div className={row}>{body}</div>
      )}
      {children}
    </li>
  )
}

/**
 * A search bar on top of a screen (owner 2026-11-01, as Instagram, X and Facebook do): tapped, it shows the people I
 * opened from a search, newest first, each with an X and "Clear all"; typed, who matches by ID or name. Cancel or
 * Escape goes back to the screen, which stays mounted under it. `other`: the screen's own answer to a query (Explore's
 * #tags), or null for people.
 */
export function PeopleSearch({ placeholder, other, children }: { placeholder: string; other?: (q: string) => React.ReactNode; children: React.ReactNode }) {
  const backend = useBackend()
  const { openProfile } = useApp()
  const { lt } = useLooksT()
  const { st } = useSocialT()
  const { t } = useI18n()
  const input = useRef<HTMLInputElement>(null)
  const [q, setQ] = useState('')
  const [asked, setAsked] = useState('')
  const [active, setActive] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => setAsked(q.trim().replace(/^@/, '')), 300)
    return () => clearTimeout(timer)
  }, [q])
  const own = asked && other ? other(asked) : null
  const found = useSWR(asked && !own ? ['find', asked] : null, () => backend.searchHandles(asked), { keepPreviousData: true })
  const recents = useSWR(active ? 'search-recents' : null, () => backend.searchRecents())

  // Opening someone from a search puts them on top of the recent ones (also when opened from that list).
  const pick = (p: PersonRow) => {
    openProfile(p.id)
    void backend.pickSearch(p.id).then(() => recents.mutate(), () => undefined)
  }
  const forget = (id?: string) => {
    void recents.mutate((l) => (id ? l?.filter((p) => p.id !== id) : []), { revalidate: false })
    backend.forgetSearch(id).catch(() => void recents.mutate())
  }
  const close = () => {
    setQ('')
    setAsked('')
    setActive(false)
    input.current?.blur()
  }

  const failed = <p className="px-1 text-sm text-destructive">{lt('find.failed')}</p>
  const loading = <Skeleton className="h-14 w-full rounded-xl" />
  const results = own ?? (asked ? (
    found.error ? failed : !found.data ? loading : found.data.length ? (
      <ul className="flex flex-col" aria-busy={found.isValidating}>
        {found.data.map((p) => <PersonLine key={p.id} p={p} onOpen={() => pick(p)} />)}
      </ul>
    ) : (
      <p className="py-10 text-center text-sm text-muted-foreground">{t('explore.noResults')}</p>
    )
  ) : (
    <section className="flex flex-col gap-1" aria-label={lt('find.recent')}>
      <div className="flex min-h-9 items-center justify-between gap-2 px-1">
        <h2 className="text-[15px] font-semibold">{lt('find.recent')}</h2>
        {recents.data?.length ? (
          <button type="button" onClick={() => forget()} className="rounded-full px-2 py-1 text-sm font-semibold text-primary-text hover:bg-muted">
            {lt('find.clearAll')}
          </button>
        ) : null}
      </div>
      {recents.error ? failed : !recents.data ? loading : recents.data.length ? (
        <ul className="flex flex-col">
          {recents.data.map((p) => (
            <PersonLine key={p.id} p={p} onOpen={() => pick(p)}>
              <button
                type="button"
                onClick={() => forget(p.id)}
                aria-label={lt('find.remove', { name: p.name })}
                className="grid size-9 shrink-0 place-items-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <X className="size-4.5" aria-hidden />
              </button>
            </PersonLine>
          ))}
        </ul>
      ) : (
        <p className="px-1 py-6 text-center text-sm text-muted-foreground">{lt('find.hint')}</p>
      )}
    </section>
  ))

  return (
    <>
      <div className="flex items-center gap-2">
        <label className="relative flex h-11 min-w-0 flex-1 items-center rounded-full border border-input bg-card transition-colors duration-150 focus-within:border-primary focus-within:ring-1 focus-within:ring-primary">
          <Search className="pointer-events-none absolute inset-s-3.5 size-4.5 text-muted-foreground" aria-hidden />
          <input
            ref={input}
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onFocus={() => setActive(true)}
            onKeyDown={(e) => e.key === 'Escape' && close()}
            placeholder={placeholder}
            aria-label={placeholder}
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            enterKeyHint="search"
            maxLength={30}
            className={cn('h-full min-w-0 flex-1 rounded-full bg-transparent ps-10 text-[15px] text-ellipsis outline-none [&::-webkit-search-cancel-button]:hidden', q ? 'pe-10' : 'pe-3')}
          />
          {q ? (
            <button
              type="button"
              onClick={() => (setQ(''), input.current?.focus())}
              className="absolute inset-e-1.5 grid size-8 place-items-center rounded-full text-muted-foreground hover:bg-muted"
              aria-label={st('rooms.clear')}
            >
              <X className="size-4" aria-hidden />
            </button>
          ) : null}
        </label>
        {active ? (
          <Button variant="ghost" onClick={close} className="shrink-0">
            {t('common.cancel')}
          </Button>
        ) : null}
      </div>
      {active ? results : null}
      <div className={active ? 'hidden' : 'contents'}>{children}</div>
    </>
  )
}

export function BlockedSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { lt } = useLooksT()
  const list = useSWR(open ? 'blocks' : null, () => backend.blocks())
  const [busy, setBusy] = useState<string | null>(null)

  const unblock = async (p: PersonRow) => {
    setBusy(p.id)
    try {
      await backend.unblock(p.id)
      await list.mutate((l) => l?.filter((x) => x.id !== p.id), { revalidate: false })
      toast.show(lt('blocks.done', { name: p.name }), 'success')
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={lt('blocks.title')} description={lt('blocks.note')}>
      <div className="p-3 pb-6">
        {list.error ? (
          <p className="px-3 text-sm text-destructive">{lt('find.failed')}</p>
        ) : !list.data ? (
          <Skeleton className="h-14 w-full rounded-xl" />
        ) : list.data.length === 0 ? (
          <p className="px-3 py-4 text-center text-sm text-muted-foreground">{lt('blocks.empty')}</p>
        ) : (
          <ul className="flex flex-col">
            {list.data.map((p) => (
              <PersonLine key={p.id} p={p}>
                <Button variant="outline" size="sm" loading={busy === p.id} disabled={!!busy} onClick={() => void unblock(p)}>
                  {lt('blocks.unblock')}
                </Button>
              </PersonLine>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
