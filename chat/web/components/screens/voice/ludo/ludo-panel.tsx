'use client'

import { AnimatePresence, useReducedMotion } from 'motion/react'
import * as m from 'motion/react-m'
import { useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Users, X } from 'lucide-react'
import { BackendError, type LudoAction, type LudoEvent, type LudoGame, type VoiceMember } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { HOME, legal, TURN_MS } from '@/lib/ludo'
import { Avatar } from '@/components/ui/avatar'
import { Button } from '@/components/ui/pressable'
import { TopicIcon } from '@/components/ui/topic-icons'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import type { VoiceSession } from '../voice-session'
import { SpeakingRing } from '../voice-stage'
import { isMod } from '../voice-text'
import { LUDO_CSS, LudoBoard, PALETTE, type TokenView } from './ludo-board'
import { Dice, TurnRing } from './ludo-dice'
import { useLudoT, type LudoKey } from './ludo-text'

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const ERRORS = new Set(['not_your_turn', 'seat_taken', 'game_full', 'need_players'])

interface News {
  text: string
  key: number
  /** Worth a caption on the board too (captures, home, three sixes...); rolls go to screen readers only. */
  show: boolean
}

/**
 * Plays the server's changes on screen: each new version's events in order (the die tumbling, a token hopping
 * square by square, a captured one sliding home), then the new state. A jump of more than one version, or a new
 * game, is shown as it is.
 */
function useLudoPlay(game: LudoGame, reduce: boolean, describe: (e: LudoEvent, g: LudoGame) => News | null) {
  const [view, setView] = useState(game)
  const [tokens, setTokens] = useState(game.tokens)
  const [motion, setMotion] = useState<Record<number, 'hop' | 'slide'>>({})
  const [dice, setDice] = useState<{ value: number | null; rolling: boolean }>({ value: game.roll, rolling: false })
  const [busy, setBusy] = useState(false)
  const [news, setNews] = useState<News | null>(null)
  const queue = useRef<LudoGame[]>([])
  const last = useRef({ id: game.id, version: game.version })
  const shown = useRef(game.tokens)
  const running = useRef(false)
  const gen = useRef(0)
  const pressed = useRef(0)
  const opts = useRef({ reduce, describe })
  opts.current = { reduce, describe }

  const snap = useCallback((g: LudoGame) => {
    shown.current = g.tokens
    setTokens(g.tokens)
    setMotion({})
    setView(g)
    setDice((d) => ({ value: g.roll ?? d.value, rolling: false }))
  }, [])

  const run = useCallback(async () => {
    if (running.current) return
    running.current = true
    setBusy(true)
    const my = gen.current
    const live = () => gen.current === my
    while (queue.current.length && live()) {
      const g = queue.current.shift()!
      const cur = [...shown.current]
      const { reduce: still } = opts.current
      for (const e of g.events) {
        if (!live()) break
        const n = opts.current.describe(e, g)
        if (e.k === 'roll') {
          setDice((d) => ({ ...d, rolling: true }))
          await sleep(still ? 0 : Math.max(280, 640 - (Date.now() - pressed.current)))
          pressed.current = 0
          if (!live()) break
          setDice({ value: e.d, rolling: false })
          if (n) setNews(n)
          await sleep(still ? 150 : 420)
        } else if (e.k === 'move') {
          const idx = e.s * 4 + e.t
          const steps = e.f < 0 ? [0] : still ? [e.to] : Array.from({ length: e.to - e.f }, (_, i) => e.f + 1 + i)
          for (const p of steps) {
            if (!live()) break
            cur[idx] = p
            shown.current = [...cur]
            setTokens(shown.current)
            setMotion({ [idx]: 'hop' })
            await sleep(still ? 0 : e.f < 0 ? 200 : 135)
          }
          if (e.cap.length) {
            for (const [s, k] of e.cap) cur[s * 4 + k] = -1
            shown.current = [...cur]
            setTokens(shown.current)
            setMotion(Object.fromEntries(e.cap.map(([s, k]) => [s * 4 + k, 'slide'])))
          }
          if (n) setNews(n)
          await sleep(e.cap.length && !still ? 480 : 120)
        } else {
          if (n) setNews(n)
          if (e.k === 'pass' || e.k === 'triple') await sleep(still ? 300 : 650)
        }
      }
      if (live()) snap(g)
    }
    running.current = false
    if (live()) setBusy(false)
  }, [snap])

  useEffect(() => {
    if (game.id !== last.current.id) {
      gen.current++
      running.current = false
      queue.current = []
      last.current = { id: game.id, version: game.version }
      setBusy(false)
      snap(game)
      return
    }
    if (game.version <= last.current.version) return
    queue.current.push(game.version === last.current.version + 1 ? game : { ...game, events: [] })
    last.current = { id: game.id, version: game.version }
    void run()
  }, [game, run, snap])

  // Pressing the die starts the tumble at once; the server's number lands it.
  const rollPressed = useCallback(() => {
    pressed.current = Date.now()
    setDice((d) => ({ ...d, rolling: true }))
  }, [])
  const rollFailed = useCallback(() => setDice((d) => ({ ...d, rolling: false })), [])

  return { view, tokens, motion, dice, busy, news, rollPressed, rollFailed }
}

/**
 * Ludo in a voice room: the lobby (colours, join, start), the board with the four players around it, and the
 * result. Everyone in the room sees it; people without a colour watch. The voice and chat carry on around it.
 */
export function LudoPanel({
  s,
  game,
  wide,
  onGame,
  onStage,
  onDismiss,
  onClose,
  onPerson,
}: {
  s: VoiceSession
  game: LudoGame
  wide: boolean
  onGame: (g: LudoGame | null) => void
  onStage: () => void
  onDismiss: () => void
  onClose: () => void
  onPerson: (userId: string) => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { lt, colour } = useLudoT()
  const reduce = !!useReducedMotion()
  const room = s.room!
  const me = s.me!
  const mod = isMod(me)
  const pal = PALETTE
  const seq = useRef(0)

  const member = (id: string | null) => (id ? room.members.find((x) => x.profile.id === id) ?? null : null)
  const nameOf = (g: LudoGame, seat: number) => member(g.players[seat])?.profile.name ?? g.names[seat] ?? colour(seat)
  const mine = (g: LudoGame, seat: number) => g.players[seat] === me.profile.id

  const describe = (e: LudoEvent, g: LudoGame): News | null => {
    const who = nameOf(g, e.s)
    const n = (text: string, show: boolean): News => ({ text, show, key: ++seq.current })
    switch (e.k) {
      case 'start':
        return n(lt('started', { name: who }), true)
      case 'roll':
        return n(mine(g, e.s) ? lt('rolledYou', { n: e.d }) : lt('rolled', { name: who, n: e.d }), false)
      case 'move':
        if (e.cap.length) return n(lt('captured', { name: who, colour: colour(e.cap[0][0], true) }), true)
        return e.to === HOME ? n(lt('home', { name: who }), true) : null
      case 'pass':
        return n(lt('noMove', { name: who }), false)
      case 'triple':
        return n(lt('triple', { name: who }), true)
      case 'timeout':
        return n(lt('timedOut', { name: who }), true)
      case 'out':
        return n(lt(e.why === 'timeouts' ? 'outTimeouts' : 'outLeft', { name: who }), true)
      case 'win':
        return n(mine(g, e.s) ? lt('youWin') : lt('wins', { name: who }), false)
    }
  }

  const play = useLudoPlay(game, reduce, describe)
  // A capture, a token home, three sixes...: the banner shows it for a moment.
  const [flash, setFlash] = useState<News | null>(null)
  useEffect(() => {
    if (play.news?.show) setFlash(play.news)
  }, [play.news])
  useEffect(() => {
    if (!flash) return
    const id = setTimeout(() => setFlash(null), 2400)
    return () => clearTimeout(id)
  }, [flash])
  const view = play.view
  const mySeat = game.players.indexOf(me.profile.id)
  const seated = mySeat >= 0 && !game.out[mySeat]
  const myTurn = seated && game.status === 'playing' && game.turn === mySeat && !play.busy
  const canRoll = myTurn && game.dice === null && !play.dice.rolling
  const movable = new Set(myTurn && game.dice !== null ? legal(game, game.dice).map((k) => mySeat * 4 + k) : [])

  const act = async (a: LudoAction) => {
    try {
      onGame(await backend.ludoAction(room.id, game.id, a))
      return true
    } catch (err) {
      const code = err instanceof BackendError ? err.code : ''
      if (code === 'stale') onGame(await backend.ludoGame(room.id).catch(() => null))
      else if (ERRORS.has(code)) toast.show(lt(`e.${code}` as LudoKey), 'info')
      else toast.error(err)
      return false
    }
  }
  const roll = async () => {
    if (!canRoll) return
    play.rollPressed()
    if (!(await act({ type: 'roll', version: game.version }))) play.rollFailed()
  }
  const move = (idx: number) => movable.has(idx) && void act({ type: 'move', token: idx & 3, version: game.version })

  // The turn's time is up and the player hasn't moved: players and hosts ask the server to play it (it checks the
  // clock and the version, so the first call wins and the others change nothing).
  const [nudge, setNudge] = useState(0)
  const deadline = game.status === 'playing' ? game.deadline : null
  useEffect(() => {
    if (deadline === null || (mySeat < 0 && !mod)) return
    const version = game.version
    const id = setTimeout(async () => {
      const g = await backend.ludoAction(room.id, game.id, { type: 'timeout', version }).catch(() => null)
      if (g) onGame(g)
      if (!g || g.version === version) setNudge((x) => x + 1) // our clocks differ a little: try again shortly
    }, Math.max(0, deadline - Date.now()) + 700 + Math.random() * 1300)
    return () => clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadline, game.version, game.id, nudge, mySeat, mod])

  const tokenViews: TokenView[] = play.tokens.map((pos, idx) => {
    const seat = idx >> 2
    const vars = { colour: colour(seat), n: (idx & 3) + 1, steps: HOME - pos }
    return {
      idx,
      pos,
      label: pos < 0 ? lt('tokenBase', vars) : pos === HOME ? lt('tokenHome', vars) : lt('token', vars),
      movable: !play.busy && movable.has(idx),
      motion: play.motion[idx],
    }
  })
  const inPlay = [0, 1, 2, 3].map((seat) => view.players[seat] !== null && !view.out[seat])

  const toolbar = (
    <div className="flex items-center gap-2 px-3 pb-2 sm:px-4">
      <SpeakerStrip s={s} onPerson={onPerson} />
      {mySeat < 0 && game.status !== 'lobby' ? (
        <span className="hidden shrink-0 rounded-full bg-muted px-2.5 py-1 text-[11px] font-semibold text-muted-foreground sm:inline">
          {lt('watching')}
        </span>
      ) : null}
      <Button size="icon-sm" variant="ghost" onClick={onStage} aria-label={lt('showStage')} title={lt('showStage')}>
        <Users className="size-4.5" aria-hidden />
      </Button>
      {mod && game.status !== 'finished' ? (
        <Button size="icon-sm" variant="ghost" onClick={onClose} aria-label={lt('close')} title={lt('close')}>
          <X className="size-5" aria-hidden />
        </Button>
      ) : null}
    </div>
  )

  const live = (
    <p className="sr-only" aria-live="polite">
      {play.news?.text}
    </p>
  )

  if (game.status === 'lobby') {
    return (
      <section aria-label={lt('title')} className="ludo flex flex-col">
        <style>{LUDO_CSS}</style>
        {toolbar}
        <Lobby game={game} me={me} mod={mod} member={member} act={act} wide={wide} />
        {live}
      </section>
    )
  }

  const diceSize = wide ? 46 : 34
  const chip = (seat: number, side: 'left' | 'right') => {
    const active = view.status === 'playing' && view.turn === seat && view.players[seat] !== null
    const who = member(view.players[seat])
    const isMe = view.players[seat] === me.profile.id
    // The ring, the glow and the die say whose turn it is; the line under my name says what to do.
    const status = view.out[seat] ? lt('out') : active && isMe ? (game.dice !== null && !play.busy ? lt('pick') : lt('yourTurn')) : isMe ? lt('you') : colour(seat)
    return (
      <PlayerChip
        key={seat}
        side={side}
        colour={pal.seats[seat]}
        name={view.players[seat] ? nameOf(view, seat) : null}
        photo={who?.profile.photoUrl ?? null}
        status={status}
        out={view.out[seat]}
        active={active}
        mine={isMe}
        deadline={active && !play.busy ? view.deadline : null}
        dice={
          active ? (
            <Dice
              value={play.dice.value}
              rolling={play.dice.rolling}
              size={diceSize}
              colour={pal.seats[seat]}
              enabled={isMe && canRoll}
              label={isMe && canRoll ? lt('roll') : `${lt('turnOf', { name: nameOf(view, seat) })}, ${lt('diceShows', { n: play.dice.value ?? '-' })}`}
              reduce={reduce}
              onRoll={() => void roll()}
            />
          ) : null
        }
      />
    )
  }

  const winner = view.status === 'finished' ? view.winner : null
  const turnSeat = view.status === 'playing' ? view.turn : null
  const banner: { key: string; text: string; colour?: string } = flash
    ? { key: `n${flash.key}`, text: flash.text, colour: 'var(--foreground)' }
    : turnSeat === null
      ? { key: 'none', text: '' }
      : turnSeat === mySeat
        ? { key: `me${game.dice !== null}`, text: `${lt('yourTurn')} · ${game.dice !== null && !play.busy ? lt('pick') : lt('roll')}`, colour: pal.seats[turnSeat] }
        : { key: `t${turnSeat}`, text: lt('turnOf', { name: nameOf(view, turnSeat) }), colour: 'var(--muted-foreground)' }
  return (
    <section aria-label={lt('title')} className="ludo flex flex-col">
      <style>{LUDO_CSS}</style>
      {toolbar}
      <div className={cn('mx-auto flex w-full flex-col gap-2 px-3 sm:px-4', wide ? 'max-w-[min(100%,calc(100dvh-300px))]' : 'max-w-[560px]')}>
        <div className="flex items-center gap-2">
          {chip(1, 'left')}
          {chip(2, 'right')}
        </div>
        <div className="relative">
          <LudoBoard
            tokens={tokenViews}
            seats={inPlay}
            turn={view.status === 'playing' ? view.turn : null}
            reduce={reduce}
            label={lt('board')}
            onToken={move}
            className="block aspect-square w-full rounded-[18px] shadow-[0_10px_30px_-8px_rgb(0_0_0/0.55),0_0_0_1px_rgb(255_255_255/0.06)]"
          />
          {view.status === 'finished' && !play.busy ? (
            <Result
              game={view}
              winnerName={winner !== null ? nameOf(view, winner) : null}
              winnerPhoto={winner !== null ? member(view.players[winner])?.profile.photoUrl ?? null : null}
              colour={winner !== null ? pal.seats[winner] : null}
              youWon={winner !== null && view.players[winner] === me.profile.id}
              mod={mod}
              onAgain={() => void act({ type: 'open' })}
              onBack={onDismiss}
            />
          ) : null}
        </div>
        {/* The turn banner: what just happened (a capture, a token home...) for a moment, else whose turn it is.
            Off the board, so it never hides a token; one fixed line, so nothing below it moves. */}
        <p aria-hidden className="relative h-5 overflow-hidden text-center text-[12px] leading-5 font-semibold sm:text-[13px]">
          <AnimatePresence initial={false}>
            <m.span
              key={banner.key}
              className="absolute inset-0 truncate px-2"
              style={banner.colour ? { color: banner.colour } : undefined}
              initial={reduce ? { opacity: 0 } : { opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduce ? { opacity: 0 } : { opacity: 0, y: -8 }}
              transition={{ duration: 0.18 }}
            >
              {banner.text}
            </m.span>
          </AnimatePresence>
        </p>
        <div className="flex items-center gap-2">
          {chip(0, 'left')}
          {chip(3, 'right')}
        </div>
      </div>
      {live}
    </section>
  )
}

/** The people on the stage, small, with their speaking rings: the voice room goes on while the board is up. */
function SpeakerStrip({ s, onPerson }: { s: VoiceSession; onPerson: (userId: string) => void }) {
  const stage = s.room!.members.filter((x) => x.seat !== null).sort((a, b) => a.seat! - b.seat!)
  return (
    <ol className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto py-1 pl-1 no-scrollbar">
      {stage.map((x) => (
        <li key={x.profile.id} className="shrink-0">
          <button type="button" onClick={() => onPerson(x.profile.id)} aria-label={x.profile.name} className="relative block rounded-full">
            <SpeakingRing store={s.levels} id={x.profile.id} />
            <Avatar name={x.profile.name} src={x.profile.photoUrl} size={30} />
          </button>
        </li>
      ))}
    </ol>
  )
}

function PlayerChip({
  side,
  colour,
  name,
  photo,
  status,
  out,
  active,
  mine,
  deadline,
  dice,
}: {
  side: 'left' | 'right'
  colour: string
  name: string | null
  photo: string | null
  status: string
  out: boolean
  active: boolean
  mine: boolean
  deadline: number | null
  dice: ReactNode
}) {
  return (
    <div className={cn('flex h-12 min-w-0 flex-1 items-center gap-2', side === 'right' && 'flex-row-reverse text-right', out && 'opacity-45')}>
      <span className="relative grid size-11 shrink-0 place-items-center">
        {deadline !== null ? <TurnRing deadline={deadline} total={TURN_MS} size={44} colour={colour} /> : null}
        {name ? (
          <Avatar name={name} src={photo} size={34} className={cn('rounded-full', active && 'shadow-[0_0_14px_2px_var(--glow)]')} />
        ) : (
          <span className="size-[34px] rounded-full border-[1.5px] border-dashed" style={{ borderColor: `color-mix(in srgb, ${colour} 55%, transparent)` }} />
        )}
        <span
          aria-hidden
          className={cn('absolute bottom-0 size-3.5 rounded-full ring-2 ring-background', side === 'left' ? 'right-0' : 'left-0')}
          style={{ background: colour, '--glow': `color-mix(in srgb, ${colour} 60%, transparent)` } as CSSProperties}
        />
      </span>
      <span className="min-w-0">
        <span className={cn('block truncate text-[13px] leading-tight font-semibold', !name && 'text-muted-foreground')}>{name ?? '—'}</span>
        <span
          className={cn('block truncate text-[11px] leading-tight', active && mine ? 'font-semibold' : 'text-muted-foreground')}
          style={active ? { color: mine ? colour : undefined } : undefined}
        >
          {status}
        </span>
      </span>
      {dice}
    </div>
  )
}

function Lobby({
  game,
  me,
  mod,
  member,
  act,
  wide,
}: {
  game: LudoGame
  me: VoiceMember
  mod: boolean
  member: (id: string | null) => VoiceMember | null
  act: (a: LudoAction) => Promise<boolean>
  wide: boolean
}) {
  const { lt, colour } = useLudoT()
  const reduce = !!useReducedMotion()
  const pal = PALETTE
  const [busy, setBusy] = useState(false)
  const mySeat = game.players.indexOf(me.profile.id)
  const count = game.players.filter(Boolean).length
  const run = async (a: LudoAction) => {
    setBusy(true)
    await act(a)
    setBusy(false)
  }
  const preview: TokenView[] = Array.from({ length: 16 }, (_, idx) => ({ idx, pos: -1, label: '', movable: false }))

  return (
    <div className="mx-3 rounded-3xl bg-card/80 p-4 ring-1 ring-border sm:mx-4 sm:p-5">
      <div className={cn('flex gap-4', wide ? 'items-center' : 'flex-col items-center')}>
        <LudoBoard
          tokens={preview}
          seats={game.players.map((p) => p !== null)}
          turn={null}
          reduce={reduce}
          label={lt('board')}
          className={cn('block aspect-square shrink-0 rounded-[14px] shadow-[0_8px_24px_-8px_rgb(0_0_0/0.6)]', wide ? 'w-56' : 'w-40')}
        />
        <div className="w-full min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-bold">
            <TopicIcon topic="gaming" size={22} /> {lt('title')}
          </h2>
          <p className="mt-1 text-[13px] leading-snug text-muted-foreground">{lt('hint')}</p>
          <ul className="mt-3 grid grid-cols-2 gap-2">
            {[0, 1, 2, 3].map((seat) => {
              const id = game.players[seat]
              const who = member(id)
              const c = pal.seats[seat]
              return (
                <li
                  key={seat}
                  className="flex h-14 min-w-0 items-center gap-2.5 rounded-2xl px-2.5"
                  style={{ background: `color-mix(in srgb, ${c} var(--ludo-wash), var(--card))`, boxShadow: `inset 0 0 0 1px color-mix(in srgb, ${c} 30%, transparent)` }}
                >
                  {id ? (
                    <>
                      <span className="relative shrink-0">
                        <Avatar name={who?.profile.name ?? game.names[seat] ?? '?'} src={who?.profile.photoUrl} size={34} />
                        <span aria-hidden className="absolute -right-0.5 -bottom-0.5 size-3.5 rounded-full ring-2 ring-card" style={{ background: c }} />
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-[13px] leading-tight font-semibold">{who?.profile.name ?? game.names[seat]}</span>
                        <span className="block truncate text-[11px] leading-tight text-muted-foreground">
                          {id === me.profile.id ? lt('you') : colour(seat)}
                        </span>
                      </span>
                    </>
                  ) : (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => void run({ type: 'join', seat })}
                      aria-label={lt('joinAs', { colour: colour(seat, true) })}
                      className="flex size-full min-w-0 items-center gap-2.5 text-left"
                    >
                      <span className="grid size-[34px] shrink-0 place-items-center rounded-full border-[1.5px] border-dashed" style={{ borderColor: c, color: c }}>
                        <span className="text-lg leading-none font-semibold" aria-hidden>
                          +
                        </span>
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate text-[13px] leading-tight font-semibold" style={{ color: c }}>
                          {lt('join')}
                        </span>
                        <span className="block truncate text-[11px] leading-tight text-muted-foreground">{colour(seat)}</span>
                      </span>
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {mod ? (
              <Button variant="brand" disabled={count < 2} loading={busy} onClick={() => void run({ type: 'start' })}>
                {lt('start')}
              </Button>
            ) : null}
            {mySeat >= 0 ? (
              <Button variant="secondary" disabled={busy} onClick={() => void run({ type: 'leave' })}>
                {lt('leaveSeat')}
              </Button>
            ) : null}
            <span className="min-w-0 flex-1 text-xs text-muted-foreground" role="status">
              {count < 2 ? lt('needTwo') : mod ? '' : lt('waitHost')}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

/** The first player home: the game is over (short sessions). Hosts can start another with the same colours. */
function Result({
  game,
  winnerName,
  winnerPhoto,
  colour,
  youWon,
  mod,
  onAgain,
  onBack,
}: {
  game: LudoGame
  winnerName: string | null
  winnerPhoto: string | null
  colour: string | null
  youWon: boolean
  mod: boolean
  onAgain: () => void
  onBack: () => void
}) {
  const { lt } = useLudoT()
  const reduce = useReducedMotion()
  const c = colour ?? 'var(--primary)'
  return (
    <m.div
      key={game.id}
      role="dialog"
      aria-label={youWon ? lt('youWin') : winnerName ? lt('wins', { name: winnerName }) : lt('title')}
      className="absolute inset-0 grid place-items-center rounded-[18px] bg-black/60 p-4 backdrop-blur-[3px]"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.25 }}
    >
      <m.div
        className="flex w-full max-w-[300px] flex-col items-center rounded-3xl bg-card px-5 pt-5 pb-4 text-center text-foreground shadow-e3 ring-1 ring-border"
        initial={reduce ? false : { scale: 0.9, y: 12 }}
        animate={{ scale: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 380, damping: 26 }}
        style={{ backgroundImage: `radial-gradient(120% 70% at 50% 0%, color-mix(in srgb, ${c} 26%, transparent), transparent)` }}
      >
        <Trophy size={44} />
        {winnerName ? (
          <span className="relative mt-2">
            <Avatar name={winnerName} src={winnerPhoto} size={64} className="rounded-full" />
            <span aria-hidden className="absolute -inset-1 rounded-full" style={{ boxShadow: `0 0 0 3px ${c}, 0 0 24px 4px color-mix(in srgb, ${c} 55%, transparent)` }} />
          </span>
        ) : null}
        <h3 className="mt-3 text-xl font-bold">{youWon ? lt('youWin') : winnerName ? lt('wins', { name: winnerName }) : lt('title')}</h3>
        <p className="mt-1 text-xs leading-snug text-muted-foreground">{lt('endsNote')}</p>
        <div className="mt-4 flex w-full flex-col gap-2">
          {mod ? (
            <Button variant="brand" onClick={onAgain} data-autofocus>
              {lt('playAgain')}
            </Button>
          ) : null}
          <Button variant="secondary" onClick={onBack}>
            {lt('back')}
          </Button>
        </div>
      </m.div>
    </m.div>
  )
}

/** A two-tone cup, drawn like the topic glyphs (components/ui/topic-icons.tsx). */
function Trophy({ size }: { size: number }) {
  const gold = '#E9B23A'
  const light = 'color-mix(in srgb, #E9B23A 45%, #fff)'
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden>
      <path d="M5.2 4.6H3.4a1 1 0 0 0-1 1.1c.3 3 1.9 5 4.6 5.6l.4-2C5.6 8.8 4.8 7.7 4.6 6.6h.8zM18.8 4.6h1.8a1 1 0 0 1 1 1.1c-.3 3-1.9 5-4.6 5.6l-.4-2c1.8-.5 2.6-1.6 2.8-2.7h-.8z" fill={light} />
      <path d="M6 3h12v5.2a6 6 0 0 1-12 0z" fill={gold} />
      <path d="M9.4 5.2c0 2.6.5 4.4 1.6 5.6" fill="none" stroke={light} strokeWidth={1.3} strokeLinecap="round" />
      <rect x="10.7" y="13.6" width="2.6" height="3.6" rx=".6" fill={gold} />
      <rect x="7.2" y="17.2" width="9.6" height="3.6" rx="1.4" fill={gold} />
      <rect x="9.2" y="18.4" width="5.6" height="1.2" rx=".6" fill={light} />
    </svg>
  )
}
