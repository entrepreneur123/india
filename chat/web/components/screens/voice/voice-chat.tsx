'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { ArrowDown, Check, ImagePlus, LoaderCircle, Plus, Reply, SendHorizontal, ShieldCheck, X } from 'lucide-react'
import type { Profile, VoiceLine, VoicePoll, VoiceReply } from '@/lib/backend'
import type { TKey } from '@/lib/i18n'
import { insertMention, mentionMatches, mentionQuery, mentionRanges, replyOf, type Quotable } from '@/lib/voice-chat'
import { pollPercents } from '@/lib/voice-polls'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { IcebreakerIcon, PollIcon, topicColor } from '@/components/ui/topic-icons'
import { Bubble, MediaBubble, MetaSpacer, TypingDots } from '@/components/screens/chat/message-bubble'
import { LinkPreview, LinkText } from '@/components/screens/chat/link-viewer'
import { kindColor } from '@/components/screens/rooms/room-text'
import { cn } from '@/lib/utils'
import { useRoomT, type RoomT } from './room-t'
import type { VoiceSession } from './voice-session'
import { isMod, memberOf, roleLabel, systemText } from './voice-text'

/** A steady colour per person, like names in a group chat. */
function nameColor(id: string) {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return `var(--name-${Math.abs(h) % 8})`
}

type Sent = Extract<VoiceLine, { kind: 'text' | 'photo' }>

/** A swipe this far to the right answers the line (WhatsApp); a press this long does too. */
const SWIPE_PX = 56
const PRESS_MS = 480
const FLASH_MS = 1400

/**
 * The room's chat: compact WhatsApp-style bubbles with replies (a quote inside the bubble; tap it to see the line it
 * answers) and @mentions of me highlighted, photos (blurred until tapped), poll and icebreaker cards and centred
 * system lines. It follows new lines while the reader is at the bottom; scrolled up, it stays put and offers a "new
 * messages" pill instead. `onIcebreaker`: the room offers question cards (an empty talk room invites to post the first).
 */
export function VoiceChat({
  s,
  onPerson,
  onReply,
  onIcebreaker,
  className,
}: {
  s: VoiceSession
  onPerson: (userId: string) => void
  /** Answer this line (hover button, swipe right, long press). */
  onReply: (line: Quotable) => void
  onIcebreaker?: () => void
  className?: string
}) {
  const { t, clock } = useI18n()
  const { rt } = useRoomT()
  const toast = useToast()
  const lines = s.lines
  const meName = s.me?.profile.name ?? ''
  const listRef = useRef<HTMLOListElement>(null)
  const stick = useRef(true)
  const seen = useRef(0)
  const [unseen, setUnseen] = useState(0)
  const [flash, setFlash] = useState<number | null>(null)
  const count = lines?.length ?? 0
  const lastSystem = [...(lines ?? [])].reverse().find((l) => l.kind === 'system')
  const lastIcebreaker = [...(lines ?? [])].reverse().find((l) => l.kind === 'icebreaker')?.id
  const quiet = !!lines && !lines.some((l) => l.kind !== 'system')
  const room = s.room

  // The list gets shorter (the typing row, the phone's keyboard, the stage opening): the newest line stays in view.
  useEffect(() => {
    const el = listRef.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(() => {
      if (stick.current) el.scrollTop = el.scrollHeight
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

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

  useEffect(() => {
    if (flash === null) return
    const id = setTimeout(() => setFlash(null), FLASH_MS)
    return () => clearTimeout(id)
  }, [flash])

  const toBottom = () => {
    const el = listRef.current
    if (!el) return
    stick.current = true
    seen.current = count
    setUnseen(0)
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }

  /** The line a quote stands for, brought into view and lit up for a moment (if it is still loaded). */
  const showOriginal = (id: number) => {
    const el = listRef.current?.querySelector<HTMLElement>(`[data-line="${id}"]`)
    if (!el) return toast.show(rt('notLoaded'), 'info')
    stick.current = false
    el.scrollIntoView({ block: 'center', behavior: 'smooth' })
    setFlash(id)
  }

  const sent = (line: Sent, grouped: boolean) => {
    const meta = clock(line.at)
    const toMe = line.kind === 'text' && !line.fromMe ? mentionRanges(line.text, meName) : []
    const sender = room ? memberOf(room, line.sender.id) : null
    const role = sender ? roleLabel(sender, t) : null
    const name =
      line.fromMe || grouped ? null : (
        <span className={cn('flex max-w-full min-w-0 items-center gap-1.5', line.kind === 'photo' && 'px-1.5')}>
          <button
            type="button"
            onClick={() => onPerson(line.sender.id)}
            className="block min-w-0 truncate text-[12.5px] leading-5 font-semibold hover:underline"
            style={{ color: nameColor(line.sender.id) }}
          >
            {line.sender.name}
          </button>
          {role ? <RoleBadge role={sender!.role} label={role} /> : null}
        </span>
      )
    return (
      <SwipeRow
        key={line.id}
        line={line}
        grouped={grouped}
        flash={flash === line.id}
        onReply={() => onReply(line)}
        label={rt('replyTo', { name: line.sender.name })}
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
          <Bubble
            mine={line.fromMe}
            tail={!grouped}
            className={cn('px-2.5 pt-1 pb-1.5', toMe.length && 'ring-2 ring-primary-text/70 ring-offset-0')}
          >
            {name}
            {line.reply ? <Quote reply={line.reply} rt={rt} meId={s.room?.meId} onShow={() => showOriginal(line.reply!.id)} /> : null}
            <LinkPreview text={line.text} className="mb-1 w-64 max-w-full" />
            <p className="relative text-[14px] leading-[1.35] break-words whitespace-pre-wrap">
              {toMe.length ? <span className="sr-only">{rt('mentionsYou')}: </span> : null}
              <LinkText text={line.text} decorate={(part, offset) => withMentions(part, within(toMe, offset, part.length))} />
              <MetaSpacer>{meta}</MetaSpacer>
              <span className="absolute right-0 -bottom-0.5 text-[10.5px] leading-none text-bubble-meta">{meta}</span>
            </p>
          </Bubble>
        )}
      </SwipeRow>
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
        className="flex min-h-0 flex-1 flex-col overflow-x-hidden overflow-y-auto px-3 pb-2 scrollbar-thin"
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
              switch (line.kind) {
                case 'system': {
                  const text = systemText(line, t)
                  return text ? (
                    <li key={line.id} className="my-1.5 self-center">
                      <span className="block rounded-full bg-muted/70 px-3 py-1 text-center text-xs text-muted-foreground">{text}</span>
                    </li>
                  ) : null
                }
                case 'icebreaker':
                  return <IcebreakerCard key={line.id} line={line} onAnother={line.id === lastIcebreaker ? onIcebreaker : undefined} />
                case 'poll':
                  return (
                    <SwipeRow key={line.id} line={line} grouped={false} flash={flash === line.id} center onReply={() => onReply(line)} label={rt('replyTo', { name: line.sender.name })}>
                      <PollCard
                        line={line}
                        poll={s.polls[line.pollId]}
                        canClose={line.fromMe || isMod(s.me)}
                        onVote={(choice) => s.votePoll(line.pollId, choice)}
                        onClose={() => s.closePoll(line.pollId)}
                      />
                    </SwipeRow>
                  )
                case 'text':
                case 'photo': {
                  const grouped = line.kind === 'text' && prev?.kind === 'text' && prev.sender.id === line.sender.id && !line.reply
                  return sent(line, grouped)
                }
                default:
                  return null // a kind this app doesn't know yet
              }
            })}
        {quiet && onIcebreaker ? (
          <li className="m-auto w-full max-w-sm py-4">
            <IcebreakerInvite onPost={onIcebreaker} />
          </li>
        ) : null}
      </ol>
      <TypingRow ids={s.typing} s={s} />
      <AnimatePresence>
        {unseen > 0 ? (
          <m.button
            type="button"
            onClick={toBottom}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.15 }}
            className="absolute bottom-12 left-1/2 flex h-9 -translate-x-1/2 items-center gap-1.5 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-e3"
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

/** The mention ranges that fall in one part of the text (from `offset`, `length` long), relative to that part. */
function within(ranges: [number, number][], offset: number, length: number): [number, number][] {
  return ranges.flatMap(([a, b]): [number, number][] => {
    const from = Math.max(a, offset) - offset
    const to = Math.min(b, offset + length) - offset
    return to > from ? [[from, to]] : []
  })
}

/** "Host" / "Co-host" next to a name in the chat. */
function RoleBadge({ role, label }: { role: string; label: string }) {
  return (
    <span
      className={cn(
        'shrink-0 rounded-full px-1.5 py-px text-[9.5px] leading-tight font-bold tracking-wide uppercase',
        role === 'host' ? 'bg-primary text-primary-foreground' : 'bg-primary-soft text-primary-text',
      )}
    >
      {label}
    </span>
  )
}

/**
 * "Asha is typing…" / "3 people are typing…" under the chat, with the three dots in a little bubble (Instagram,
 * KakaoTalk). The names are the room's own (the member list), never what a signal claimed. Reserved height, so the
 * chat doesn't jump when it comes and goes.
 */
function TypingRow({ ids, s }: { ids: string[]; s: VoiceSession }) {
  const { num } = useI18n()
  const { rt } = useRoomT()
  const room = s.room
  const people = room ? ids.flatMap((id) => memberOf(room, id) ?? []) : []
  const text = people.length === 0 ? '' : people.length === 1 ? rt('typingOne', { name: people[0].profile.name }) : rt('typingMany', { n: num(people.length) })
  return (
    <div className="relative h-8 shrink-0 px-3" aria-live="polite" aria-atomic>
      <AnimatePresence>
        {people.length ? (
          <m.div
            key="typing"
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 4 }}
            transition={{ type: 'tween', duration: 0.16, ease: [0.2, 0, 0, 1] }}
            className="flex h-full min-w-0 items-center gap-2"
          >
            <span className="flex shrink-0 -space-x-1.5" aria-hidden>
              {people.slice(0, 3).map((p) => (
                <Avatar key={p.profile.id} name={p.profile.name} src={p.profile.photoUrl} size={20} className="rounded-full ring-2 ring-background" />
              ))}
            </span>
            <span className="grid h-6 shrink-0 place-items-center rounded-full rounded-bl-sm bg-bubble-in px-2.5 shadow-[0_1px_0.5px_rgb(11_20_26/0.13)]" aria-hidden>
              <TypingDots />
            </span>
            <span className="min-w-0 truncate text-xs text-muted-foreground">{text}</span>
          </m.div>
        ) : null}
      </AnimatePresence>
    </div>
  )
}

/** The text with the mentions of me picked out. */
function withMentions(text: string, ranges: [number, number][]): ReactNode {
  if (!ranges.length) return text
  const out: ReactNode[] = []
  let at = 0
  for (const [a, b] of ranges) {
    if (a > at) out.push(text.slice(at, a))
    out.push(
      <span key={a} className="rounded bg-primary-soft px-0.5 font-semibold text-primary-text">
        {text.slice(a, b)}
      </span>,
    )
    at = b
  }
  if (at < text.length) out.push(text.slice(at))
  return out
}

/**
 * One line of the chat that can be answered: a reply button on hover (mouse), a swipe to the right or a long press
 * (touch). The row lights up for a moment when a quote brought the reader here.
 */
function SwipeRow({
  line,
  grouped,
  flash,
  center,
  onReply,
  label,
  children,
}: {
  line: Quotable
  grouped: boolean
  flash: boolean
  /** A card in the middle of the chat (a poll), not a bubble on one side. */
  center?: boolean
  onReply: () => void
  label: string
  children: ReactNode
}) {
  const [dx, setDx] = useState(0)
  const start = useRef<{ x: number; y: number; timer: ReturnType<typeof setTimeout> } | null>(null)
  const pressed = useRef(false)
  const end = () => {
    if (start.current) clearTimeout(start.current.timer)
    start.current = null
    setDx(0)
  }
  useEffect(() => () => end(), [])

  return (
    <m.li
      data-line={line.id}
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
      className={cn(
        'group relative flex touch-pan-y gap-2 rounded-2xl transition-[background-color,box-shadow] duration-300 pointer-coarse:select-none pointer-coarse:[-webkit-touch-callout:none]',
        center ? 'my-2.5 w-full max-w-sm self-center' : cn('max-w-[88%]', line.fromMe ? 'flex-row-reverse self-end' : 'self-start', grouped ? 'mt-0.5' : 'mt-2'),
        flash && 'bg-primary-soft ring-4 ring-primary-soft',
      )}
      onPointerDown={(e) => {
        if (e.pointerType === 'mouse') return
        pressed.current = false
        const timer = setTimeout(() => {
          pressed.current = true
          start.current = null
          navigator.vibrate?.(12)
          onReply()
        }, PRESS_MS)
        start.current = { x: e.clientX, y: e.clientY, timer }
      }}
      onPointerMove={(e) => {
        const s = start.current
        if (!s) return
        const x = e.clientX - s.x
        const y = e.clientY - s.y
        if (Math.abs(x) > 6 || Math.abs(y) > 6) clearTimeout(s.timer)
        if (Math.abs(y) > Math.abs(x)) return
        setDx(Math.max(0, Math.min(x, SWIPE_PX + 16)))
      }}
      onPointerUp={() => {
        if (dx >= SWIPE_PX) onReply()
        end()
      }}
      onPointerCancel={end}
      onClickCapture={(e) => {
        // The tap that ends a long press is not also a tap on what's under it (a photo, a poll option).
        if (pressed.current) {
          pressed.current = false
          e.preventDefault()
          e.stopPropagation()
        }
      }}
      style={dx ? { transform: `translateX(${dx}px)` } : undefined}
    >
      {dx ? (
        <span
          aria-hidden
          className="absolute top-1/2 -left-9 grid size-7 -translate-y-1/2 place-items-center rounded-full bg-muted text-foreground"
          style={{ opacity: Math.min(1, dx / SWIPE_PX) }}
        >
          <Reply className="size-4" />
        </span>
      ) : null}
      {children}
      <button
        type="button"
        onClick={onReply}
        aria-label={label}
        title={label}
        className={cn(
          'grid shrink-0 place-items-center self-center rounded-full text-muted-foreground opacity-0 transition-opacity duration-150 group-hover:opacity-100 hover:text-foreground focus-visible:opacity-100 pointer-coarse:hidden',
          center ? 'absolute -top-3 right-4 z-10 size-8 bg-card shadow-e2 ring-1 ring-border' : 'size-8 hover:bg-muted',
        )}
      >
        <Reply className="size-4" aria-hidden />
      </button>
    </m.li>
  )
}

/** The answered line inside a reply's bubble (WhatsApp): who, and what they said (or "Photo" / the poll). */
function Quote({ reply, rt, meId, onShow }: { reply: VoiceReply; rt: RoomT; meId?: string; onShow: () => void }) {
  const who = reply.sender && reply.sender === meId ? rt('you') : reply.name
  return (
    <button
      type="button"
      onClick={onShow}
      aria-label={`${rt('showOriginal')}: ${who}`}
      className="relative mt-0.5 mb-1 flex w-full min-w-0 overflow-hidden rounded-md bg-black/5 text-left transition-colors duration-150 hover:bg-black/10 night:bg-white/8 night:hover:bg-white/12"
      style={{ '--q': nameColor(reply.sender || reply.name) } as CSSProperties}
    >
      <span aria-hidden className="w-1 shrink-0 bg-[var(--q)]" />
      <span className="min-w-0 flex-1 px-2 py-1">
        <span className="block truncate text-[12px] leading-4 font-semibold text-[var(--q)]">{who}</span>
        <QuoteText reply={reply} rt={rt} />
      </span>
    </button>
  )
}

function QuoteText({ reply, rt }: { reply: VoiceReply; rt: RoomT }) {
  return (
    <span className="flex min-w-0 items-center gap-1 text-[12.5px] leading-4 text-bubble-meta">
      {reply.kind === 'photo' ? <ImagePlus className="size-3.5 shrink-0" aria-hidden /> : reply.kind === 'poll' ? <PollIcon size={14} /> : null}
      <span className="line-clamp-2 break-words">{reply.kind === 'photo' ? rt('photo') : reply.kind === 'poll' ? `${rt('poll')}: ${reply.text ?? ''}` : reply.text}</span>
    </span>
  )
}

const POLL = { '--k': topicColor('study') } as CSSProperties

/**
 * A poll in the chat: the question and its options. Before I vote, the options are buttons; once I have (or it has
 * closed) they show the results as bars, my choice marked. While it is open I can tap another option to change.
 */
function PollCard({
  line,
  poll,
  canClose,
  onVote,
  onClose,
}: {
  line: Extract<VoiceLine, { kind: 'poll' }>
  /** Undefined while its votes are being read. */
  poll: VoicePoll | undefined
  canClose: boolean
  onVote: (choice: number) => void
  onClose: () => Promise<boolean>
}) {
  const { num } = useI18n()
  const { rt, votes } = useRoomT()
  const toast = useToast()
  const [closing, setClosing] = useState(false)
  const options = poll?.options ?? line.options
  const results = !!poll && (poll.mine !== null || poll.closed)
  const pct = poll ? pollPercents(poll.counts, poll.total) : options.map(() => 0)
  const open = !!poll && !poll.closed
  const id = useId()

  return (
    <article
      aria-labelledby={`${id}-q`}
      style={POLL}
      className="relative w-full overflow-hidden rounded-3xl bg-[color-mix(in_srgb,var(--k)_13%,var(--card))] px-4 pt-3.5 pb-3 shadow-[0_1px_2px_rgb(0_0_0/0.08)] ring-1 ring-[color-mix(in_srgb,var(--k)_34%,transparent)]"
    >
      <span aria-hidden className="pointer-events-none absolute -top-16 -right-12 size-40 rounded-full bg-[radial-gradient(closest-side,color-mix(in_srgb,var(--k)_30%,transparent),transparent)]" />
      <h3 className="relative flex items-center gap-1.5 text-[11px] font-bold tracking-wider text-[var(--k)] uppercase">
        <PollIcon size={16} /> {rt('poll')}
        {poll?.closed ? (
          <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-[10.5px] tracking-normal text-muted-foreground normal-case">{rt('closed')}</span>
        ) : null}
      </h3>
      <p id={`${id}-q`} className="relative mt-1.5 text-[16.5px] leading-snug font-semibold break-words text-foreground">
        {poll?.question ?? line.question}
      </p>
      <ul className="relative mt-3 flex flex-col gap-2" aria-busy={!poll}>
        {options.map((o, i) => {
          const mine = poll?.mine === i
          return (
            <li key={i}>
              <button
                type="button"
                disabled={!open}
                onClick={() => onVote(i)}
                aria-pressed={poll ? mine : undefined}
                aria-label={results ? `${rt('result', { option: o, pct: num(pct[i]) })}, ${votes(poll!.counts[i] ?? 0)}${mine ? `, ${rt('yourVote')}` : ''}` : o}
                className={cn(
                  'relative flex min-h-11 w-full items-center gap-2 overflow-hidden rounded-2xl px-3.5 py-2 text-left text-sm font-semibold transition-[background-color,box-shadow] duration-150',
                  mine ? 'bg-card ring-2 ring-[var(--k)]' : 'bg-card/80 ring-1 ring-border',
                  open && !mine && 'hover:bg-card hover:ring-[color-mix(in_srgb,var(--k)_60%,transparent)]',
                  !poll && 'animate-pulse',
                )}
              >
                {results ? (
                  <span
                    aria-hidden
                    className="absolute inset-y-0 left-0 rounded-2xl bg-[color-mix(in_srgb,var(--k)_24%,transparent)] transition-[width] duration-500 ease-out"
                    style={{ width: `${pct[i]}%` }}
                  />
                ) : null}
                <span
                  aria-hidden
                  className={cn(
                    'relative grid size-5 shrink-0 place-items-center rounded-full ring-2',
                    mine ? 'bg-[var(--k)] text-white ring-[var(--k)]' : 'ring-[color-mix(in_srgb,var(--k)_45%,transparent)]',
                  )}
                >
                  {mine ? <Check className="size-3.5" strokeWidth={3} /> : null}
                </span>
                <span className="relative min-w-0 flex-1 break-words">{o}</span>
                {results ? <span className="relative shrink-0 text-xs font-bold tabular-nums">{num(pct[i])}%</span> : null}
              </button>
            </li>
          )
        })}
      </ul>
      <div className="relative mt-2.5 flex items-center justify-between gap-3">
        <span className="min-w-0 text-xs text-muted-foreground">
          <span className="block truncate">
            {poll ? votes(poll.total) : ' '} · {line.fromMe ? rt('byYou') : rt('by', { name: line.sender.name })}
          </span>
          {open ? <span className="block truncate">{poll.mine === null ? rt('tapToVote') : rt('canChange')}</span> : null}
        </span>
        {open && canClose ? (
          <button
            type="button"
            disabled={closing}
            onClick={async () => {
              setClosing(true)
              if (await onClose()) toast.show(rt('ended'), 'success')
              setClosing(false)
            }}
            className="flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold text-[var(--k)] transition-colors duration-150 hover:bg-[color-mix(in_srgb,var(--k)_12%,transparent)] disabled:opacity-60"
          >
            {closing ? <LoaderCircle className="size-3.5 animate-spin" aria-hidden /> : null}
            {rt('end')}
          </button>
        ) : null}
      </div>
    </article>
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

/**
 * The room's composer: text (with "@" to mention someone here), a photo, a question card; hosts and co-hosts get a
 * "+" menu with the photo and a poll. While answering a line, its quote sits above the box.
 */
export function VoiceComposer({
  onSend,
  onPhoto,
  onIcebreaker,
  onPoll,
  onMenu,
  onTyping,
  members,
  reply,
  onCancelReply,
  className,
}: {
  onSend: (text: string) => Promise<boolean>
  /** The text changed (tells the room I'm writing; the session sends it at most every few seconds). */
  onTyping?: () => void
  /** Checks the photo on this device, then sends it; false when it wasn't sent. */
  onPhoto?: (photo: File) => Promise<boolean>
  /** Posts a question card. */
  onIcebreaker?: () => void
  /** Hosts and co-hosts: opens the poll form (the "+" menu then holds the photo and the poll). */
  onPoll?: () => void
  /** The "+" menu is about to open (its poll form can start downloading). */
  onMenu?: () => void
  /** People in the room besides me, for @mentions. */
  members: Profile[]
  reply: Quotable | null
  onCancelReply: () => void
  className?: string
}) {
  const { t } = useI18n()
  const { rt } = useRoomT()
  const id = useId()
  const [text, setText] = useState('')
  const [caret, setCaret] = useState(0)
  const [sending, setSending] = useState(false)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [menu, setMenu] = useState(false)
  const [pick, setPick] = useState(0)
  const [dismissed, setDismissed] = useState<number | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const can = text.trim().length > 0 && !sending

  // "@..." right before the caret: the people whose names fit (Escape hides the list until the next "@").
  const q = mentionQuery(text, caret)
  const matches = q && q.at !== dismissed ? mentionMatches(members, q.query) : []
  const listOpen = matches.length > 0
  useEffect(() => setPick(0), [q?.query, q?.at])

  // A reply was chosen: write it.
  const replyId = reply?.id
  useEffect(() => {
    if (replyId !== undefined) inputRef.current?.focus()
  }, [replyId])

  useEffect(() => {
    if (!menu) return
    const away = (e: PointerEvent) => !menuRef.current?.contains(e.target as Node) && setMenu(false)
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setMenu(false)
    document.addEventListener('pointerdown', away)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('pointerdown', away)
      document.removeEventListener('keydown', esc)
    }
  }, [menu])

  const choose = (p: Profile) => {
    if (!q) return
    const next = insertMention(text, caret, q.at, p.name)
    setText(next.text)
    setCaret(next.caret)
    requestAnimationFrame(() => {
      inputRef.current?.focus()
      inputRef.current?.setSelectionRange(next.caret, next.caret)
    })
  }
  const photo = () => {
    setMenu(false)
    fileRef.current?.click()
  }
  const quoted = reply ? replyOf(reply) : null

  return (
    <div className={cn('relative', className)}>
      <AnimatePresence initial={false}>
        {quoted ? (
          <m.div
            key="quote"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
            className="overflow-hidden"
          >
            <div
              className="mb-2 flex items-center gap-2 overflow-hidden rounded-2xl bg-muted py-1.5 pr-1.5 pl-0"
              style={{ '--q': nameColor(quoted.sender) } as CSSProperties}
              role="group"
              aria-label={rt('replyingTo', { name: reply!.fromMe ? rt('you') : quoted.name })}
            >
              <span aria-hidden className="ml-2 w-1 self-stretch rounded-full bg-[var(--q)]" />
              <Reply className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-xs font-semibold text-[var(--q)]">
                  {rt('replyingTo', { name: reply!.fromMe ? rt('you') : quoted.name })}
                </span>
                <span className="block truncate text-[13px] text-muted-foreground">
                  {quoted.kind === 'photo' ? rt('photo') : quoted.kind === 'poll' ? `${rt('poll')}: ${quoted.text ?? ''}` : quoted.text}
                </span>
              </span>
              <button
                type="button"
                onClick={onCancelReply}
                aria-label={rt('cancelReply')}
                className="grid size-8 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-input/60 hover:text-foreground"
              >
                <X className="size-4" aria-hidden />
              </button>
            </div>
          </m.div>
        ) : null}
      </AnimatePresence>

      {listOpen ? (
        <div className="absolute inset-x-0 bottom-full z-20 mb-2 overflow-hidden rounded-2xl bg-card shadow-e3 ring-1 ring-border">
          <p className="px-3.5 pt-2.5 pb-1 text-[11px] font-bold tracking-wider text-muted-foreground uppercase">{rt('people')}</p>
          <ul id={`${id}-people`} role="listbox" aria-label={rt('people')} className="max-h-56 overflow-y-auto pb-1.5 scrollbar-thin">
            {matches.map((p, i) => (
              <li key={p.id} role="option" aria-selected={i === pick} id={`${id}-p${i}`}>
                <button
                  type="button"
                  tabIndex={-1}
                  // Keep the caret where it is: choosing happens before the box would lose focus.
                  onPointerDown={(e) => e.preventDefault()}
                  onClick={() => choose(p)}
                  className={cn('flex w-full items-center gap-2.5 px-3.5 py-1.5 text-left text-sm font-semibold transition-colors duration-100', i === pick ? 'bg-muted' : 'hover:bg-muted/70')}
                >
                  <Avatar name={p.name} src={p.photoUrl} size={28} />
                  <span className="truncate" style={{ color: nameColor(p.id) }}>
                    {p.name}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <form
        className="flex items-center gap-2"
        onSubmit={async (e) => {
          e.preventDefault()
          if (!can) return
          setSending(true)
          const ok = await onSend(text.trim())
          setSending(false)
          if (ok) {
            setText('')
            setCaret(0)
          }
          inputRef.current?.focus()
        }}
      >
        {onPhoto ? (
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
        ) : null}
        {onPoll ? (
          <div ref={menuRef} className="relative shrink-0">
            <button
              type="button"
              onClick={() => {
                if (!menu) onMenu?.()
                setMenu((o) => !o)
              }}
              aria-label={rt('attach')}
              aria-expanded={menu}
              aria-haspopup="menu"
              disabled={photoBusy}
              className={cn(
                'grid size-11 place-items-center rounded-full transition-[background-color,transform] duration-150 disabled:opacity-70',
                menu ? 'rotate-45 bg-foreground text-background' : 'bg-muted text-foreground hover:bg-input/60',
              )}
            >
              {photoBusy ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <Plus className="size-5" aria-hidden />}
            </button>
            <AnimatePresence>
              {menu ? (
                <m.div
                  role="menu"
                  aria-label={rt('attach')}
                  initial={{ opacity: 0, y: 8, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 8, scale: 0.96 }}
                  transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
                  className="absolute bottom-full left-0 z-20 mb-2 flex w-52 origin-bottom-left flex-col gap-0.5 rounded-2xl bg-card p-1.5 shadow-e3 ring-1 ring-border rtl:right-0 rtl:left-auto"
                >
                  {onPhoto ? (
                    <MenuItem icon={<ImagePlus className="size-5 text-[var(--topic-travel)]" aria-hidden />} label={t('photo.send')} onClick={photo} />
                  ) : null}
                  <MenuItem
                    icon={<PollIcon size={22} />}
                    label={rt('pollNew')}
                    onClick={() => {
                      setMenu(false)
                      onPoll()
                    }}
                  />
                </m.div>
              ) : null}
            </AnimatePresence>
          </div>
        ) : onPhoto ? (
          <button
            type="button"
            onClick={photo}
            disabled={photoBusy}
            aria-label={photoBusy ? t('photo.checking') : t('photo.send')}
            className="grid size-11 shrink-0 place-items-center rounded-full bg-muted text-foreground transition-colors duration-150 hover:bg-input/60 disabled:opacity-70"
          >
            {photoBusy ? <LoaderCircle className="size-5 animate-spin" aria-hidden /> : <ImagePlus className="size-5" aria-hidden />}
          </button>
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
          onChange={(e) => {
            setText(e.target.value)
            setCaret(e.target.selectionStart ?? e.target.value.length)
            if (e.target.value.trim()) onTyping?.()
          }}
          onSelect={(e) => setCaret(e.currentTarget.selectionStart ?? 0)}
          onKeyDown={(e) => {
            if (e.key === 'Escape' && reply && !listOpen) return onCancelReply()
            if (!listOpen) return
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
              e.preventDefault()
              setPick((i) => (i + (e.key === 'ArrowDown' ? 1 : matches.length - 1)) % matches.length)
            } else if (e.key === 'Enter' || e.key === 'Tab') {
              e.preventDefault()
              choose(matches[pick] ?? matches[0])
            } else if (e.key === 'Escape') {
              e.preventDefault()
              setDismissed(q!.at)
            }
          }}
          maxLength={300}
          autoComplete="off"
          enterKeyHint="send"
          role="combobox"
          aria-expanded={listOpen}
          aria-controls={listOpen ? `${id}-people` : undefined}
          aria-activedescendant={listOpen ? `${id}-p${pick}` : undefined}
          aria-autocomplete="list"
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
    </div>
  )
}

function MenuItem({ icon, label, onClick }: { icon: ReactNode; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className="flex h-11 items-center gap-3 rounded-xl px-3 text-left text-sm font-semibold transition-colors duration-150 hover:bg-muted"
    >
      <span className="grid size-8 place-items-center rounded-full bg-muted">{icon}</span>
      {label}
    </button>
  )
}

