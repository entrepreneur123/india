'use client'

import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { ArrowLeft, EllipsisVertical, LogOut, Pencil, UserPlus } from 'lucide-react'
import type { GroupLine } from '@/lib/backend'
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
  const { data: groups, mutate } = useGroups()
  const g = groups?.find((x) => x.id === groupId)
  const [lines, setLines] = useState<GroupLine[] | null>(null)
  const [sheet, setSheet] = useState<'menu' | 'add' | 'rename' | null>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const note = useGroupLineText()

  const merge = (more: GroupLine[]) =>
    setLines((now) => {
      const byId = new Map((now ?? []).map((l) => [l.id, l]))
      for (const l of more) byId.set(l.id, l)
      return [...byId.values()].sort((a, b) => a.id - b.id)
    })
  useEffect(() => {
    let live = true
    void backend.groupHistory(groupId).then((h) => live && merge(h), (err) => live && (toast.error(err), setLines([])))
    const stop = backend.watchGroups([groupId], (_, line) => merge([line]))
    return () => {
      live = false
      stop()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [backend, groupId])

  // What's on screen is read (the list's count goes down with it).
  const last = lines?.[lines.length - 1]?.id
  useEffect(() => {
    if (!last) return
    void backend.readGroup(groupId, last).then(() => mutate(), () => undefined)
  }, [backend, groupId, last, mutate])
  useLayoutEffect(() => {
    const el = listRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [lines?.length])

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
        <Button size="icon" variant="ghost" onClick={() => setSheet('menu')} aria-label={t('voice.more')}>
          <EllipsisVertical className="size-5" aria-hidden />
        </Button>
      </header>

      <ul ref={listRef} className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto bg-chat-wall px-3 py-4 scrollbar-thin sm:px-6" aria-live="polite">
        {!lines ? (
          <li className="flex flex-col gap-2">
            <Skeleton className="h-10 w-48 rounded-lg" />
            <Skeleton className="h-10 w-40 self-end rounded-lg" />
          </li>
        ) : (
          lines.map((l, i) => {
            if (l.kind !== 'text') {
              return (
                <li key={l.id} className="mx-auto my-1 max-w-xs rounded-lg bg-bubble-in px-3 py-1.5 text-center text-xs leading-relaxed text-bubble-meta shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]">
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
                <Bubble mine={mine} tail={first} className="px-2.5 pt-1 pb-1.5">
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
      <AddSheet open={sheet === 'add'} onClose={() => setSheet(null)} groupId={groupId} exclude={members.map((x) => x.id)} onDone={() => void mutate()} />
      <RenameSheet open={sheet === 'rename'} onClose={() => setSheet(null)} groupId={groupId} title={g?.title ?? ''} onDone={() => void mutate()} />
    </div>
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
