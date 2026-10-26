'use client'

import { useEffect, useId, useState } from 'react'
import useSWR from 'swr'
import { Check, Plus, Users } from 'lucide-react'
import type { GroupChat } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { EmptyState } from '@/components/ui/ambient'
import { Field, Skeleton, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { CountBadge } from '@/components/social/bits'
import { useSocialT } from '@/components/social/social-text'
import { UserAvatar } from '@/components/looks/user-avatar'
import { cn } from '@/lib/utils'

/** 48: my group chats (069), latest first, with a "New group" button. The shell keeps the list live (useGroups). */
export function GroupList() {
  const { st } = useSocialT()
  const { openGroup } = useApp()
  const { data: groups, error, mutate } = useGroups()
  const [making, setMaking] = useState(false)
  return (
    <div className="flex flex-col gap-3">
      <Button variant="brand" className="self-start" onClick={() => setMaking(true)}>
        <Plus className="size-4.5" aria-hidden /> {st('group.new')}
      </Button>
      {!groups ? (
        error ? (
          <Button variant="outline" className="self-start" onClick={() => void mutate()}>
            {st('group.retry')}
          </Button>
        ) : (
          <Skeleton className="h-16 w-full rounded-2xl" />
        )
      ) : groups.length ? (
        <ul className="flex flex-col">
          {groups.map((g) => (
            <li key={g.id}>
              <GroupRow g={g} onOpen={() => openGroup(g.id)} />
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState art="people" title={st('group.empty')} body={st('group.emptyBody')} />
      )}
      <NewGroupSheet open={making} onClose={() => setMaking(false)} onMade={(id) => (setMaking(false), void mutate(), openGroup(id))} />
    </div>
  )
}

/** The groups list, shared by the Recent tab, the group screen and the shell's unread count. */
export const useGroups = () => {
  const backend = useBackend()
  return useSWR('groups', () => backend.groups())
}

/** A group's last line as one short text (the server's notes worded here). */
export function useGroupLineText() {
  const { st } = useSocialT()
  const { me } = useApp()
  return (line: GroupChat['last'], members: GroupChat['members']) => {
    if (!line) return ''
    const who = line.senderId === me.id ? st('group.you') : members.find((x) => x.id === line.senderId)?.name ?? ''
    switch (line.kind) {
      case 'created':
        return st('group.created', { name: who, names: line.body })
      case 'added':
        return st('group.added', { name: who, names: line.body })
      case 'left':
        return st('group.left', { name: line.body })
      case 'renamed':
        return st('group.renamed', { name: who, title: line.body })
      default:
        return members.length > 2 || line.senderId === me.id ? `${who}: ${line.body}` : line.body
    }
  }
}

/** Up to three faces, overlapping. */
export function GroupFaces({ g, size = 48 }: { g: Pick<GroupChat, 'members'>; size?: number }) {
  const { me } = useApp()
  const faces = g.members.filter((x) => x.id !== me.id).slice(0, 3)
  const s = faces.length > 1 ? Math.round(size * 0.66) : size
  return (
    <span className="relative inline-block shrink-0" style={{ width: size, height: size }} aria-hidden>
      {faces.length ? (
        faces.map((p, i) => (
          <span key={p.id} className="absolute rounded-full ring-2 ring-background" style={{ width: s, height: s, insetInlineStart: faces.length > 1 ? (i * (size - s)) / (faces.length - 1) : 0, top: faces.length > 1 ? (i % 2) * (size - s) : 0 }}>
            <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={s} />
          </span>
        ))
      ) : (
        <span className="grid size-full place-items-center rounded-full bg-muted text-muted-foreground">
          <Users className="size-1/2" />
        </span>
      )}
    </span>
  )
}

function GroupRow({ g, onOpen }: { g: GroupChat; onOpen: () => void }) {
  const { st } = useSocialT()
  const { timeAgo } = useI18n()
  const text = useGroupLineText()
  return (
    <button type="button" onClick={onOpen} className="flex w-full items-center gap-3 rounded-2xl p-2 text-start transition-colors hover:bg-muted">
      <GroupFaces g={g} />
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5">
          <span className="truncate text-[15px] font-semibold">{g.title}</span>
          <span className="shrink-0 text-xs text-muted-foreground tabular-nums">{st('group.members', { n: g.members.length })}</span>
        </span>
        <span className={cn('block truncate text-sm', g.unread ? 'font-semibold text-foreground' : 'text-muted-foreground')}>{text(g.last, g.members)}</span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <span className="text-xs text-muted-foreground">{timeAgo(g.lastAt)}</span>
        <CountBadge n={g.unread} />
      </span>
    </button>
  )
}

/** Friends to choose (with a check), shared by "New group" and "Add friends". `exclude`: already in. */
export function FriendPicker({ picked, onPick, exclude = [] }: { picked: string[]; onPick: (ids: string[]) => void; exclude?: string[] }) {
  const backend = useBackend()
  const { st } = useSocialT()
  const { data } = useSWR('friends', () => backend.friends())
  const list = data?.filter((f) => f.state === 'friends' && !exclude.includes(f.profile.id))
  if (!list) return <Skeleton className="h-40 w-full rounded-2xl" />
  if (!list.length) return <p className="py-6 text-center text-sm text-muted-foreground">{st('group.noFriends')}</p>
  return (
    <ul className="flex max-h-[min(22rem,50dvh)] flex-col overflow-y-auto scrollbar-thin">
      {list.map(({ profile: p }) => {
        const on = picked.includes(p.id)
        return (
          <li key={p.id}>
            <button
              type="button"
              aria-pressed={on}
              onClick={() => onPick(on ? picked.filter((x) => x !== p.id) : [...picked, p.id])}
              className="flex w-full items-center gap-3 rounded-xl p-2 text-start transition-colors hover:bg-muted"
            >
              <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={40} />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold">{p.name}</span>
              <span className={cn('grid size-6 place-items-center rounded-full ring-2', on ? 'bg-primary text-primary-foreground ring-primary' : 'ring-input')} aria-hidden>
                {on ? <Check className="size-4" strokeWidth={3} /> : null}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function NewGroupSheet({ open, onClose, onMade }: { open: boolean; onClose: () => void; onMade: (id: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const id = useId()
  const [title, setTitle] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!open) return
    setTitle('')
    setPicked([])
  }, [open])
  const make = async () => {
    setBusy(true)
    try {
      onMade(await backend.createGroup(title.trim(), picked))
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={st('group.new')}>
      <form
        className="flex flex-col gap-4 p-6 pt-3"
        onSubmit={(e) => {
          e.preventDefault()
          if (title.trim() && picked.length && !busy) void make()
        }}
      >
        <Field label={st('group.name')} htmlFor={`${id}-t`}>
          <TextInput id={`${id}-t`} value={title} maxLength={40} placeholder={st('group.namePlaceholder')} onChange={(e) => setTitle(e.target.value)} data-autofocus />
        </Field>
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-semibold">{st('group.pick')}</span>
          <FriendPicker picked={picked} onPick={setPicked} />
        </div>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!title.trim() || !picked.length}>
          {st('group.create')}
        </Button>
      </form>
    </Modal>
  )
}
