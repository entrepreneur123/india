'use client'

import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { ArrowLeft, Check, EllipsisVertical, LogOut, Pencil, Shuffle, UserPlus } from 'lucide-react'
import type { GroupChat, GroupLine, PartyState } from '@/lib/backend'
import { useSWRConfig } from 'swr'
import { savedFlag } from '@/lib/flags'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Field, Skeleton, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { Bubble } from '@/components/screens/chat/message-bubble'
import { Composer } from '@/components/screens/chat/composer'
import { LinkText } from '@/components/screens/chat/link-viewer'
import { useSocialT } from '@/components/social/social-text'
import { UserAvatar } from '@/components/looks/user-avatar'
import { cn } from '@/lib/utils'
import { Wall } from '@/components/looks/wall'
import { useShop } from '@/components/looks/use-shop'
import { WALL_DESIGNS, wallOf } from '@/lib/walls'
import { FriendPicker, GroupFaces, useGroupLineText, useGroups } from './groups'

/**
 * 48: a group chat (069), full screen like a thread: the lines (names over others' bubbles), the box to write in,
 * and a menu to add friends, rename it or leave. New lines arrive over Realtime; what's on screen counts as read.
 */
export function GroupScreen({ groupId, onClose }: { groupId: string; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const { t, locale } = useI18n()
  const clock = new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit' })
  const { me, openProfile } = useApp()
  const wall = wallOf(useShop().data?.wall)
  const { data: groups, mutate } = useGroups()
  const g = groups?.find((x) => x.id === groupId)
  const [lines, setLines] = useState<GroupLine[] | null>(null)
  const [sheet, setSheet] = useState<'menu' | 'add' | 'rename' | 'party' | null>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const note = useGroupLineText()

  const merge = (more: GroupLine[]) =>
    setLines((now) => {
      const byId = new Map((now ?? []).map((l) => [l.id, l]))
      for (const l of more) byId.set(l.id, l)
      return [...byId.values()].sort((a, b) => a.id - b.id)
    })
  // Realtime may drop lines while the socket is down (Supabase promises no replay): the latest page is read again
  // when the tab comes back or the network returns, and merged (Discord's resume, Matrix's gap fill).
  useEffect(() => {
    let live = true
    let loaded = false
    const load = () => backend.groupHistory(groupId).then(
      (h) => live && ((loaded = true), merge(h)),
      // Only the first load says so; a failed re-read leaves what's on screen.
      (err) => live && !loaded && ((loaded = true), toast.error(err), setLines([])),
    )
    void load()
    const stop = backend.watchGroups([groupId], (_, line) => merge([line]))
    const again = () => document.visibilityState === 'visible' && void load()
    document.addEventListener('visibilitychange', again)
    window.addEventListener('online', again)
    return () => {
      live = false
      stop()
      document.removeEventListener('visibilitychange', again)
      window.removeEventListener('online', again)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [backend, groupId])

  // What's on screen while the tab is visible is read (the list's count goes down with it).
  const last = lines?.[lines.length - 1]?.id
  useEffect(() => {
    if (!last) return
    const mark = () => document.visibilityState === 'visible' && void backend.readGroup(groupId, last).then(() => mutate(), () => undefined)
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => document.removeEventListener('visibilitychange', mark)
  }, [backend, groupId, last, mutate])

  // Older lines 50 at a time when scrolled to the top, the view held on the line that was first (Element's
  // ScrollPanel); new lines scroll down only when I was at the bottom or wrote them.
  const [older, setOlder] = useState<'more' | 'loading' | 'end'>('more')
  const pinned = useRef(true)
  const shape = useRef({ first: 0, last: 0, height: 0 })
  useLayoutEffect(() => {
    const el = listRef.current
    if (!el || !lines?.length) return
    const was = shape.current
    const newest = lines[lines.length - 1]
    if (lines[0].id < was.first) el.scrollTop += el.scrollHeight - was.height
    else if (newest.id !== was.last && (pinned.current || newest.senderId === me.id)) el.scrollTop = el.scrollHeight
    shape.current = { first: lines[0].id, last: newest.id, height: el.scrollHeight }
  }, [lines, me.id])
  const onScroll = () => {
    const el = listRef.current
    if (!el) return
    pinned.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    const first = lines?.[0]?.id
    if (el.scrollTop > 40 || older !== 'more' || !first) return
    setOlder('loading')
    void backend.groupHistory(groupId, first).then(
      (h) => (merge(h), setOlder(h.length < 50 ? 'end' : 'more')),
      () => setOlder('more'),
    )
  }

  const send = async (text: string | null) => {
    if (!text?.trim()) return false
    try {
      merge([await backend.sendGroup(groupId, text)])
      return true
    } catch (err) {
      toast.error(err)
      return false
    }
  }
  const leave = async () => {
    try {
      await backend.leaveGroup(groupId)
      await mutate()
      onClose()
    } catch (err) {
      toast.error(err)
    }
  }
  const members = g?.members ?? []
  const who = (id: string | null) => members.find((x) => x.id === id)

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <header className="flex items-center gap-2 border-b border-border px-2 py-2 sm:px-4">
        <Button size="icon" variant="ghost" onClick={onClose} aria-label={t('common.back')}>
          <ArrowLeft className="size-5 rtl:-scale-x-100" aria-hidden />
        </Button>
        {g ? <GroupFaces g={g} size={36} /> : <Skeleton className="size-9 rounded-full" />}
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-base font-bold">{g?.title ?? ''}</h1>
          <p className="truncate text-xs text-muted-foreground">{members.map((x) => (x.id === me.id ? st('group.you') : x.name)).join(', ')}</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => setSheet('party')} aria-label={st('party.button')} title={st('party.button')}>
          <Shuffle className="size-4" aria-hidden /> <span className="hidden sm:inline">{st('party.button')}</span>
        </Button>
        <Button size="icon" variant="ghost" onClick={() => setSheet('menu')} aria-label={t('voice.more')}>
          <EllipsisVertical className="size-5" aria-hidden />
        </Button>
      </header>

      {/* My chat wall here too, as in my other chats (the owner, 2026-10-28); only I see it. */}
      <div className="relative isolate flex min-h-0 flex-1 flex-col bg-chat-wall">
        <Wall id={wall} />
        <ul ref={listRef} onScroll={onScroll} className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto px-3 py-4 scrollbar-thin sm:px-6" aria-live="polite">
          {older === 'loading' ? <li className="mx-auto"><Skeleton className="h-4 w-24 rounded-full" /></li> : null}
          {!lines ? (
            <li className="flex flex-col gap-2">
              <Skeleton className="h-10 w-48 rounded-lg" />
              <Skeleton className="h-10 w-40 self-end rounded-lg" />
            </li>
          ) : (
            lines.map((l, i) => {
              if (l.kind !== 'text') {
                return (
                  <li key={l.id} className={cn('mx-auto my-2 max-w-xs text-center text-xs leading-relaxed', wall && WALL_DESIGNS[wall].dark ? 'text-white/75' : 'text-muted-foreground')}>
                    {note({ kind: l.kind, body: l.body, senderId: l.senderId }, members)}
                  </li>
                )
              }
              const mine = l.senderId === me.id
              const first = lines[i - 1]?.senderId !== l.senderId || lines[i - 1]?.kind !== 'text'
              const p = who(l.senderId)
              return (
                <li key={l.id} className={cn('flex max-w-[85%] items-end gap-2', mine ? 'self-end' : 'self-start', first && 'mt-2')}>
                  {!mine ? (
                    first && p ? (
                      <button type="button" onClick={() => openProfile(p.id)} className="shrink-0 self-start rounded-full" aria-label={p.name}>
                        <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={28} />
                      </button>
                    ) : (
                      <span className="w-7 shrink-0" aria-hidden />
                    )
                  ) : null}
                  <Bubble mine={mine} first={first} className="px-2.5 pt-1 pb-1.5">
                    {!mine && first ? <p className="text-xs font-semibold text-primary-text">{p?.name ?? '…'}</p> : null}
                    <p dir="auto" className="text-[15px] leading-snug break-words whitespace-pre-wrap">
                      <LinkText text={l.body} />
                      <span className="ms-2 inline-block align-bottom text-[10.5px] text-bubble-meta">{clock.format(l.at)}</span>
                    </p>
                  </Bubble>
                </li>
              )
            })
          )}
        </ul>
      </div>

      <div className="border-t border-border bg-background p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <Composer onSend={(text) => send(text)} allowMedia={false} placeholder={st('group.placeholder')} />
      </div>

      <Modal open={sheet === 'menu'} onClose={() => setSheet(null)} variant="sheet" title={g?.title}>
        <div className="flex flex-col pt-2 pb-4">
          {(
            [
              [UserPlus, st('group.add'), () => setSheet('add')],
              [Pencil, st('group.rename'), () => setSheet('rename')],
              [LogOut, st('group.leave'), () => void leave()],
            ] as const
          ).map(([Icon, label, go], i) => (
            <button
              key={label}
              type="button"
              onClick={go}
              className={cn('flex h-13 w-full items-center gap-4 px-6 text-start text-[15px] font-medium transition-colors hover:bg-muted', i === 2 && 'text-destructive')}
            >
              <Icon className={cn('size-5', i === 2 ? 'text-destructive' : 'text-muted-foreground')} aria-hidden /> {label}
            </button>
          ))}
        </div>
      </Modal>
      {sheet === 'party' && g ? <PartySheet groupId={groupId} members={members} onClose={() => setSheet(null)} /> : null}
      <AddSheet open={sheet === 'add'} onClose={() => setSheet(null)} groupId={groupId} exclude={members.map((x) => x.id)} onDone={() => void mutate()} />
      <RenameSheet open={sheet === 'rename'} onClose={() => setSheet(null)} groupId={groupId} title={g?.title ?? ''} onDone={() => void mutate()} />
    </div>
  )
}

/**
 * 49: many-to-many random chat. Everyone in this group who has the sheet open and says "I'm in" is the party; one
 * search pairs it with another party (any size) and everybody lands in a new group chat. The sheet is my heartbeat:
 * closing it takes me out (and stops my search).
 */
function PartySheet({ groupId, members, onClose }: { groupId: string; members: GroupChat['members']; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const { me, openGroup } = useApp()
  const { mutate } = useSWRConfig() // the shell's cache, where 'groups' lives
  const [ready, setReady] = useState(true)
  const [state, setState] = useState<PartyState | null>(null)
  const [searching, setSearching] = useState(false)
  const live = useRef({ ready, searching })
  live.current = { ready, searching }
  const [everywhere] = useState(() => savedFlag('yaari_meet_global'))

  // Every 2 s: my "I'm in" (or out), the party's state, and — while I search — one more search request.
  useEffect(() => {
    let alive = true
    const tick = async () => {
      try {
        const s = await backend.partyState(groupId, live.current.ready)
        if (!alive) return
        setState(s)
        const found = s.matched ?? (live.current.searching ? await backend.partySearch(groupId, everywhere) : null)
        if (alive && found) {
          alive = false
          void mutate('groups')
          onClose()
          openGroup(found)
        }
      } catch (err) {
        if (!alive) return
        setSearching(false)
        toast.error(err)
      }
    }
    void tick()
    const id = setInterval(() => void tick(), 2000)
    return () => {
      alive = false
      clearInterval(id)
      void backend.partyState(groupId, false).catch(() => undefined)
      if (live.current.searching) void backend.partyCancel(groupId).catch(() => undefined)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [backend, groupId])

  const inCount = state?.ready.length ?? 0
  const busy = searching || !!state?.searching
  return (
    <Modal open onClose={onClose} variant="sheet" title={st('party.title')} description={st('party.desc')}>
      <div className="flex flex-col gap-4 p-6 pt-3">
        <ul className="flex flex-col gap-1">
          {members.map((m) => {
            const on = state?.ready.includes(m.id) ?? false
            return (
              <li key={m.id} className="flex items-center gap-3 rounded-xl p-1.5">
                <UserAvatar userId={m.id} name={m.name} src={m.photoUrl} size={36} />
                <span className="min-w-0 flex-1 truncate text-sm font-semibold">{m.id === me.id ? st('group.you') : m.name}</span>
                <span className={cn('flex items-center gap-1 text-xs font-semibold', on ? 'text-primary-text' : 'text-muted-foreground')}>
                  {on ? <Check className="size-3.5" strokeWidth={3} aria-hidden /> : null}
                  {on ? st('party.in') : st('party.notYet')}
                </span>
              </li>
            )
          })}
        </ul>
        <div className="flex gap-2">
          <Button variant="secondary" size="lg" className="flex-1" disabled={busy} onClick={() => setReady((r) => !r)}>
            {ready ? st('party.leave') : st('party.join')}
          </Button>
          {busy ? (
            <Button variant="outline" size="lg" className="flex-1" onClick={() => (setSearching(false), void backend.partyCancel(groupId).catch(() => undefined))}>
              {st('party.cancel')}
            </Button>
          ) : (
            <Button variant="brand" size="lg" className="flex-1" disabled={!ready || inCount < 2} onClick={() => setSearching(true)}>
              <Shuffle className="size-4.5" aria-hidden /> {st('party.find')}
            </Button>
          )}
        </div>
        <p className="text-center text-xs text-muted-foreground" aria-live="polite">
          {busy ? st('party.searching') : inCount < 2 ? st('party.needTwo') : st('party.ready', { n: inCount })}
        </p>
      </div>
    </Modal>
  )
}

function AddSheet({ open, onClose, groupId, exclude, onDone }: { open: boolean; onClose: () => void; groupId: string; exclude: string[]; onDone: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const [picked, setPicked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  useEffect(() => setPicked([]), [open])
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={st('group.add')}>
      <div className="flex flex-col gap-4 p-6 pt-3">
        <FriendPicker picked={picked} onPick={setPicked} exclude={exclude} />
        <Button
          variant="brand"
          size="lg"
          loading={busy}
          disabled={!picked.length}
          onClick={async () => {
            setBusy(true)
            try {
              await backend.addToGroup(groupId, picked)
              onDone()
              onClose()
            } catch (err) {
              toast.error(err)
            } finally {
              setBusy(false)
            }
          }}
        >
          {st('group.add')}
        </Button>
      </div>
    </Modal>
  )
}

function RenameSheet({ open, onClose, groupId, title, onDone }: { open: boolean; onClose: () => void; groupId: string; title: string; onDone: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const { t } = useI18n()
  const id = useId()
  const [name, setName] = useState(title)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (open) setName(title)
  }, [open, title])
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={st('group.rename')}>
      <form
        className="flex flex-col gap-4 p-6 pt-3"
        onSubmit={async (e) => {
          e.preventDefault()
          if (!name.trim() || busy) return
          setBusy(true)
          try {
            await backend.renameGroup(groupId, name.trim())
            onDone()
            onClose()
          } catch (err) {
            toast.error(err)
          } finally {
            setBusy(false)
          }
        }}
      >
        <Field label={st('group.name')} htmlFor={`${id}-n`}>
          <TextInput id={`${id}-n`} value={name} maxLength={40} onChange={(e) => setName(e.target.value)} data-autofocus />
        </Field>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!name.trim() || name.trim() === title}>
          {t('common.save')}
        </Button>
      </form>
    </Modal>
  )
}
