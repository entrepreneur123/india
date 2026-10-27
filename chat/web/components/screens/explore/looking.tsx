'use client'

import { PersonName } from '@/components/looks/look-name'
import { useState } from 'react'
import useSWR from 'swr'
import { MessageCircle, Megaphone, Trash2 } from 'lucide-react'
import { LOOKING_MAX, type LookingPost } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Skeleton, TextInput } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { TagText } from '@/components/social/hashtags'
import { UserAvatar } from '@/components/looks/user-avatar'

/**
 * 52: "Looking for…" — short lines people of a country see for 24 hours ("anyone up for BGMI tonight?"), answered
 * with a message. My own lines can be taken down; at most 3 open at once (the server says slow_down).
 */
export function LookingFor({ country }: { country: string }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { me } = useApp()
  const { data, mutate } = useSWR(['looking', country], () => backend.lookingFor(country || null), { refreshInterval: 60_000, refreshWhenHidden: false })
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const mine = !country || country === me.country
  const post = async (e: React.FormEvent) => {
    e.preventDefault()
    if (text.trim().length < 2 || busy) return
    setBusy(true)
    try {
      await backend.postLooking(text)
      setText('')
      await mutate()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="flex flex-col gap-3">
      {/* Lines go to my own country only (the server files them there). */}
      {mine ? (
        <form onSubmit={(e) => void post(e)} className="flex gap-2">
          <TextInput value={text} maxLength={LOOKING_MAX} onChange={(e) => setText(e.target.value)} placeholder={t('looking.placeholder')} aria-label={t('looking.placeholder')} className="flex-1" />
          <Button type="submit" variant="brand" loading={busy} disabled={text.trim().length < 2}>
            <Megaphone className="size-4" aria-hidden /> {t('looking.post')}
          </Button>
        </form>
      ) : null}
      <p className="-mt-1 text-xs text-muted-foreground">{t('looking.hint')}</p>
      {!data ? (
        <Skeleton className="h-40 w-full rounded-2xl" />
      ) : data.length ? (
        <ul className="flex flex-col gap-2">
          {data.map((p) => (
            <Line key={p.id} p={p} onGone={() => void mutate()} />
          ))}
        </ul>
      ) : (
        <p className="py-10 text-center text-sm text-muted-foreground">{t('looking.empty')}</p>
      )}
    </div>
  )
}

function Line({ p, onGone }: { p: LookingPost; onGone: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t, timeAgo } = useI18n()
  const { openProfile, openThread } = useApp()
  const [busy, setBusy] = useState(false)
  const run = async (f: () => Promise<void>) => {
    setBusy(true)
    try {
      await f()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }
  return (
    <li className="flex gap-3 rounded-2xl border border-border bg-card p-3">
      <button type="button" onClick={() => openProfile(p.author.id)} className="shrink-0 self-start rounded-full" aria-label={p.author.name}>
        <UserAvatar userId={p.author.id} name={p.author.name} src={p.author.photoUrl} size={44} />
      </button>
      <div className="min-w-0 flex-1">
        <p className="flex items-baseline gap-2 text-sm">
          <span className="truncate font-semibold">
            <PersonName p={p.author} /> <span aria-hidden>{flagEmoji(p.author.country)}</span>
          </span>
          <span className="shrink-0 text-xs text-muted-foreground">{timeAgo(p.at)}</span>
        </p>
        <p className="mt-0.5 text-[15px] leading-snug break-words whitespace-pre-wrap">
          <TagText text={p.text} />
        </p>
      </div>
      {p.mine ? (
        <Button size="icon" variant="ghost" loading={busy} onClick={() => void run(async () => (await backend.hideLooking(p.id), onGone()))} aria-label={t('looking.remove')} title={t('looking.remove')}>
          <Trash2 className="size-4.5" aria-hidden />
        </Button>
      ) : (
        <Button variant="secondary" size="sm" className="self-center" loading={busy} onClick={() => void run(async () => openThread(p.author, await backend.replyLooking(p.id)))}>
          <MessageCircle className="size-4" aria-hidden /> {t('looking.reply')}
        </Button>
      )}
    </li>
  )
}
