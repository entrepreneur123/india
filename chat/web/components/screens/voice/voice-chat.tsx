'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useId, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { ArrowDown, ImagePlus, LoaderCircle, SendHorizontal, ShieldCheck } from 'lucide-react'
import type { VoiceLine } from '@/lib/backend'
import type { TKey } from '@/lib/i18n'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { IcebreakerIcon } from '@/components/ui/topic-icons'
import { Bubble, MediaBubble, MetaSpacer } from '@/components/screens/chat/message-bubble'
import { kindColor } from '@/components/screens/rooms/room-text'
import { cn } from '@/lib/utils'
import { systemText } from './voice-text'

/** A steady colour per person, like names in a group chat. */
function nameColor(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return `var(--name-${Math.abs(h) % 8})`
}

type Sent = Extract<VoiceLine, { kind: 'text' | 'photo' }>

/**
 * The room's chat: compact WhatsApp-style bubbles, photos (blurred until tapped), icebreaker cards and centred system
 * lines. It follows new lines while the reader is at the bottom; scrolled up, it stays put and offers a "new messages"
 * pill instead. `onIcebreaker`: the room offers question cards (an empty talk room invites to post the first).
 */
export function VoiceChat({
  lines,
  onPerson,
  onIcebreaker,
  className,
}: {
  lines: VoiceLine[] | null
  onPerson: (userId: string) => void
  onIcebreaker?: () => void
  className?: string
}) {
  const { t, clock } = useI18n()
  const listRef = useRef<HTMLOListElement>(null)
  const stick = useRef(true)
  const seen = useRef(0)
  const [unseen, setUnseen] = useState(0)
  const count = lines?.length ?? 0
  const lastSystem = [...(lines ?? [])].reverse().find((l) => l.kind === 'system')
  const lastIcebreaker = [...(lines ?? [])].reverse().find((l) => l.kind === 'icebreaker')?.id
  const quiet = !!lines && !lines.some((l) => l.kind !== 'system')

  useLayoutEffect(() => {
    const el = listRef.current
    if (!el) return
    if (stick.current) {
      el.scrollTop = el.scrollHeight
      seen.current = count
      setUnseen(0)
    } else {
      setUnseen(Math.max(0, count - seen.current))
    }
  }, [count])

  const toBottom = () => {
    const el = listRef.current
    if (!el) return
    stick.current = true
    seen.current = count
    setUnseen(0)
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }

  const sent = (line: Sent, grouped: boolean) => {
    const meta = clock(line.at)
    const name =
      line.fromMe || grouped ? null : (
        <button
          type="button"
          onClick={() => onPerson(line.sender.id)}
          className={cn('block max-w-full truncate text-[12.5px] leading-5 font-semibold hover:underline', line.kind === 'photo' && 'px-1.5')}
          style={{ color: nameColor(line.sender.id) }}
        >
          {line.sender.name}
        </button>
      )
    return (
      <m.li
        key={line.id}
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
        className={cn('flex max-w-[88%] gap-2', line.fromMe ? 'self-end' : 'self-start', grouped ? 'mt-0.5' : 'mt-2')}
      >
        {line.fromMe ? null : grouped ? (
          <span className="w-7 shrink-0" aria-hidden />
        ) : (
          <button type="button" onClick={() => onPerson(line.sender.id)} className="shrink-0 self-start rounded-full" aria-label={t('room.view', { name: line.sender.name })}>
            <Avatar name={line.sender.name} src={line.sender.photoUrl} size={28} />
          </button>
        )}
        {line.kind === 'photo' ? (
          <Bubble mine={line.fromMe} tail={!grouped} className="p-1">
            {name}
            <div aria-label={t('photo.from', { name: line.sender.name })} role="group">
              <MediaBubble media={{ url: line.url, kind: 'image' }} mine={line.fromMe} meta={meta} />
            </div>
          </Bubble>
        ) : (
          <Bubble mine={line.fromMe} tail={!grouped} className="px-2.5 pt-1 pb-1.5">
            {name}
            <p className="relative text-[14px] leading-[1.35] break-words whitespace-pre-wrap">
              {line.text}
              <MetaSpacer>{meta}</MetaSpacer>
              <span className="absolute right-0 -bottom-0.5 text-[10.5px] leading-none text-bubble-meta">{meta}</span>
            </p>
          </Bubble>
        )}
      </m.li>
    )
  }

  return (
    <section aria-label={t('voice.chat')} className={cn('relative flex min-h-0 flex-col', className)}>
      <p className="mx-3 mt-1 mb-1 flex items-center justify-center gap-1.5 rounded-full px-3 py-1 text-center text-[11px] leading-snug text-muted-foreground">
        <ShieldCheck className="size-3.5 shrink-0" aria-hidden />
        <span>{t('voice.safety')}</span>
      </p>
      <ol
        ref={listRef}
        onScroll={(e) => {
          const el = e.currentTarget
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 48
          if (stick.current) {
            seen.current = count
            setUnseen(0)
          }
        }}
        className="flex min-h-0 flex-1 flex-col overflow-y-auto px-3 pb-2 scrollbar-thin"
        aria-label={t('voice.chat')}
      >
        {lines === null
          ? Array.from({ length: 4 }, (_, i) => (
              <li key={i} className="mt-2 flex gap-2">
                <Skeleton className="size-7 rounded-full" />
                <Skeleton className="h-10 w-48 rounded-lg" />
              </li>
            ))
          : lines.map((line, i) => {
              const prev = lines[i - 1]
              if (line.kind === 'system')
                return (
                  <li key={line.id} className="my-1.5 self-center">
                    <span className="block rounded-full bg-muted/70 px-3 py-1 text-center text-xs text-muted-foreground">{systemText(line, t)}</span>
                  </li>
                )
              if (line.kind === 'icebreaker')
                return (
                  <IcebreakerCard
                    key={line.id}
                    line={line}
                    onAnother={line.id === lastIcebreaker ? onIcebreaker : undefined}
                  />
                )
              const grouped = line.kind === 'text' && prev?.kind === 'text' && prev.sender.id === line.sender.id
              return sent(line, grouped)
            })}
        {quiet && onIcebreaker ? (
          <li className="m-auto w-full max-w-sm py-4">
            <IcebreakerInvite onPost={onIcebreaker} />
          </li>
        ) : null}
      </ol>
      <AnimatePresence>
        {unseen > 0 ? (
          <m.button
            type="button"
            onClick={toBottom}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.15 }}
            className="absolute bottom-3 left-1/2 flex h-9 -translate-x-1/2 items-center gap-1.5 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-e3"
          >
            <ArrowDown className="size-4" aria-hidden /> {t('voice.newMessages')}
          </m.button>
        ) : null}
      </AnimatePresence>
      {/* Joins, seats and music changes are read out; chat lines are not (that would never stop talking). */}
      <p className="sr-only" aria-live="polite">
        {lastSystem?.kind === 'system' ? systemText(lastSystem, t) : ''}
      </p>
    </section>
  )
}

const TALK = { '--k': kindColor('talk') } as CSSProperties

/** A conversation starter, shown as a card in the talk colour (not a bubble): the question in my language. */
function IcebreakerCard({ line, onAnother }: { line: Extract<VoiceLine, { kind: 'icebreaker' }>; onAnother?: () => void }) {
  const { t } = useI18n()
  return (
    <m.li
      initial={{ opacity: 0, scale: 0.96 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', stiffness: 420, damping: 30 }}
      className="my-2.5 w-full max-w-sm self-center"
      style={TALK}
    >
      <article
        aria-label={t('ib.button')}
        className="relative overflow-hidden rounded-2xl bg-[color-mix(in_srgb,var(--k)_13%,var(--card))] px-4 pt-3 pb-3.5 shadow-[0_1px_2px_rgb(0_0_0/0.08)] ring-1 ring-[color-mix(in_srgb,var(--k)_32%,transparent)]"
      >
        <h3 className="flex items-center gap-1.5 text-[11px] font-bold tracking-wider text-[var(--k)] uppercase">
          <IcebreakerIcon size={16} /> {t('ib.button')}
        </h3>
        <p className="relative mt-1.5 text-[16.5px] leading-snug font-semibold text-foreground">{t(`ib.${line.prompt}` as TKey)}</p>
        <div className="relative mt-2.5 flex items-center justify-between gap-3">
          <span className="min-w-0 truncate text-xs text-muted-foreground">{line.fromMe ? t('ib.byYou') : t('ib.by', { name: line.name })}</span>
          {onAnother ? (
            <button type="button" onClick={onAnother} className="shrink-0 rounded-full px-2 py-1 text-xs font-semibold text-[var(--k)] transition-colors duration-150 hover:bg-[color-mix(in_srgb,var(--k)_12%,transparent)]">
              {t('ib.another')}
            </button>
          ) : null}
        </div>
      </article>
    </m.li>
  )
}

/** An empty talk room: one tap posts the first question card. */
function IcebreakerInvite({ onPost }: { onPost: () => void }) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col items-center gap-3 rounded-3xl bg-[color-mix(in_srgb,var(--k)_9%,transparent)] px-6 py-6 text-center" style={TALK}>
      <IcebreakerIcon size={44} />
      <div>
        <p className="text-base font-bold">{t('ib.emptyTitle')}</p>
        <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{t('ib.emptyBody')}</p>
      </div>
      <Button variant="brand" onClick={onPost}>
        {t('ib.post')}
      </Button>
    </div>
  )
}

export function VoiceComposer({
  onSend,
  onPhoto,
  onIcebreaker,
  className,
}: {
  onSend: (text: string) => Promise<boolean>
  /** Checks the photo on this device, then sends it; false when it wasn't sent. */
  onPhoto?: (photo: File) => Promise<boolean>
  /** Posts a question card. */
  onIcebreaker?: () => void
  className?: string
}) {
  const { t } = useI18n()
  const id = useId()
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [photoBusy, setPhotoBusy] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const can = text.trim().length > 0 && !sending

  return (
    <form
      className={cn('flex items-center gap-2', className)}
      onSubmit={async (e) => {
        e.preventDefault()
        if (!can) return
        setSending(true)
        const ok = await onSend(text.trim())
        setSending(false)
        if (ok) setText('')
        inputRef.current?.focus()
      }}
    >
      {onPhoto ? (
        <>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="hidden"
            tabIndex={-1}
            onChange={async (e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (!file) return
              setPhotoBusy(true)
              await onPhoto(file)
              setPhotoBusy(false)
            }}
          />
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            disabled={photoBusy}
            aria-label={photoBusy ? t('photo.checking') : t('photo.send')}
            className="grid size-11 shrink-0 place-items-center rounded-full bg-muted text-foreground transition-colors duration-150 hover:bg-input/60 disabled:opacity-70"
          >
            {photoBusy ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <ImagePlus className="size-5" aria-hidden />}
          </button>
        </>
      ) : null}
      {onIcebreaker ? (
        <button
          type="button"
          onClick={onIcebreaker}
          aria-label={t('ib.post')}
          title={t('ib.button')}
          className="grid size-11 shrink-0 place-items-center rounded-full bg-muted transition-colors duration-150 hover:bg-input/60"
        >
          <IcebreakerIcon size={24} />
        </button>
      ) : null}
      <label htmlFor={id} className="sr-only">
        {t('voice.chatPlaceholder')}
      </label>
      <input
        id={id}
        ref={inputRef}
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={300}
        autoComplete="off"
        enterKeyHint="send"
        placeholder={photoBusy ? t('photo.checking') : t('voice.chatPlaceholder')}
        className="h-11 min-w-0 flex-1 rounded-full bg-muted px-4 text-[15px] text-foreground ring-1 ring-transparent transition-shadow duration-150 outline-none placeholder:text-muted-foreground focus:ring-primary"
      />
      <m.button
        type="submit"
        disabled={!can}
        whileTap={can ? { scale: 0.94 } : undefined}
        aria-label={t('voice.send')}
        className="grid size-11 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground transition-opacity duration-150 disabled:opacity-40"
      >
        <SendHorizontal className="size-5" aria-hidden />
      </m.button>
    </form>
  )
}
