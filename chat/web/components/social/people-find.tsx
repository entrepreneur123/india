'use client'

import { useEffect, useState } from 'react'
import useSWR from 'swr'
import { AtSign, Search } from 'lucide-react'
import type { PersonRow } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { useApp } from '@/components/app-state'
import { LookName } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { Skeleton, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { UserAvatar } from '@/components/looks/user-avatar'

// Finding someone by @ID (042), and Settings' list of people I blocked.

function PersonLine({ p, children, onOpen }: { p: PersonRow; children?: React.ReactNode; onOpen?: () => void }) {
  const body = (
    <>
      <UserAvatar userId={p.id} name={p.name} src={p.photoUrl} size={44} />
      <span className="flex min-w-0 flex-1 flex-col">
        <LookName userId={p.id} handle={p.handle} className="truncate text-[15px] font-semibold" />
        <span className="truncate text-sm text-muted-foreground">{p.name}</span>
      </span>
    </>
  )
  return (
    <li className="flex items-center gap-3 rounded-xl px-3 py-2">
      {onOpen ? (
        <button type="button" onClick={onOpen} className="flex min-w-0 flex-1 items-center gap-3 text-start">
          {body}
        </button>
      ) : (
        body
      )}
      {children}
    </li>
  )
}

export function FindSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const { openProfile } = useApp()
  const { lt } = useLooksT()
  const [q, setQ] = useState('')
  const [asked, setAsked] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setAsked(q.trim().replace(/^@/, '')), 300)
    return () => clearTimeout(timer)
  }, [q])
  useEffect(() => {
    if (!open) setQ('')
  }, [open])
  const found = useSWR(open && asked ? ['find', asked] : null, () => backend.searchHandles(asked), { keepPreviousData: true })

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title={lt('find.title')}>
      <div className="flex flex-col gap-3 p-4 pb-6">
        <label className="relative block">
          <AtSign className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <TextInput
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={lt('find.placeholder')}
            aria-label={lt('find.title')}
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            maxLength={25}
            className="ps-9"
            data-autofocus
          />
        </label>
        {!asked ? (
          <p className="flex items-center gap-2 px-1 text-sm text-muted-foreground">
            <Search className="size-4" aria-hidden />
            {lt('find.hint')}
          </p>
        ) : found.error ? (
          <p className="px-1 text-sm text-destructive">{lt('find.failed')}</p>
        ) : !found.data ? (
          <Skeleton className="h-14 w-full rounded-xl" />
        ) : found.data.length === 0 ? (
          <p className="px-1 text-sm text-muted-foreground">{lt('find.none', { q: asked })}</p>
        ) : (
          <ul className="flex flex-col" aria-busy={found.isValidating}>
            {found.data.map((p) => (
              <PersonLine key={p.id} p={p} onOpen={() => openProfile(p.id)} />
            ))}
          </ul>
        )}
      </div>
    </Modal>
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
