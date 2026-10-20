// Live server: the same Supabase project and rules as the Android live build
// (chat/app/src/live/.../SupabaseBackend.kt). Every rule is re-checked in Postgres (chat/server/migrations).
import { createClient, type RealtimeChannel, type SupabaseClient } from '@supabase/supabase-js'
import {
  BackendError,
  type Backend,
  type Entitlements,
  type Friend,
  type FriendState,
  type Gender,
  type Match,
  type MatchEvent,
  type Message,
  type NewProfile,
  type NewRoom,
  type Post,
  type Profile,
  type RecentMatch,
  type ReportReason,
  type Room,
  type RoomCategory,
  type RoomMessage,
  type RoomRefusal,
  type NewVoiceRoom,
  type Target,
  type VideoStatus,
  VOICE_SEATS,
  type LudoAction,
  type LudoEvent,
  type LudoGame,
  type LudoStatus,
  type VoiceAction,
  type VoiceAudioState,
  type VoiceEvent,
  type VoiceLine,
  type VoiceReaction,
  type VoiceRole,
  type VoiceRoom,
  type VoiceRoomSummary,
  type VoiceSystemEvent,
} from './backend'
import { retryImport } from './retry-import'
import type { VoiceAudio } from './voice-audio'
import { onPageVisibility, pageHidden } from './visibility'

const BUCKET = 'media'
const MAX_PHOTO_PX = 1280
const MAX_VIDEO_BYTES = 25 * 1024 * 1024
const SIGNED_URL_SECONDS = 3600
/** The storage bucket's allowed_mime_types (001_core.sql); anything else is refused by the server. */
const VIDEO_TYPES = ['video/mp4', 'video/3gpp', 'video/quicktime', 'video/webm']
/** A request that hangs on a weak network fails after this long, so the screen can retry (uploads get longer). */
const REQUEST_TIMEOUT_MS = 15_000
const UPLOAD_TIMEOUT_MS = 120_000
/** Random chats: the partner's app must connect to the chat within this long, or the chat ends. */
const PARTNER_JOIN_TIMEOUT_MS = 12_000
/** Random chats: after the partner's app disconnects, how long they have to come back. Same values as the app. */
const PARTNER_GRACE_MS = 20_000
const CODES = [
  'exit_locked', 'no_profile', 'not_signed_in', 'bad_target', 'premium_required', 'banned', 'under_18', 'slow_down', 'not_allowed', 'not_found',
  'video_not_configured', 'billing_not_configured', 'already_redeemed', 'wrong_account', 'too_large',
  'room_closed', 'room_gender', 'room_age', 'room_full', 'room_title', 'room_size', 'voice_removed', 'mic_denied', 'queue_full',
  'not_your_turn', 'seat_taken', 'game_full', 'need_players', 'stale',
]

type CardRow = {
  id: string; name: string; gender: string; age: number; city: string; bio: string
  photo_path: string | null; country: string | null
}
type ProfileRow = Omit<CardRow, 'age'> & { birth_date: string }
type RecentRow = CardRow & { match_id: string; met_at: string; last_body: string | null }
type FriendRow = CardRow & { state: string; match_id: string | null }
type MatchRow = {
  id: string; user_a: string; user_b: string; ended_at: string | null; ended_by: string | null
  video_status: string; video_requested_by: string | null; read_a: number | null; read_b: number | null
}
type MessageRow = {
  id: number; match_id: string; sender: string; body: string | null
  media_path: string | null; media_kind: string | null; created_at: string
}
type RoomRow = {
  id: string; title: string; emoji: string; online: number; official: boolean; mine: boolean; owner_name: string | null
  category: string; max_members: number | null; gender_rule: string; min_age: number; max_age: number
  is_private: boolean; code: string | null; refusal: string | null
}
type PostRow = { id: string; user_id: string; body: string; image_path: string | null; created_at: string }
type VoiceListRow = {
  id: string; title: string; category: string; mine: boolean; host: CardRow; speakers: CardRow[]; listeners: number
  now_playing: string | null
}
/** voice_state(): the room, the people present with their cards, and the server clock (ms). */
type VoiceStateRow = {
  id: string; title: string; category: string; mode: string; host: string; rev: number; now: number; locked: number[]
  music: { id: string; videoId: string; title: string; by: string; startedAt: number; pausedAt: number | null } | null
  queue: { id: string; videoId: string; title: string; by: string; suggested: boolean }[]
  members: (CardRow & { role: string; seat: number | null; muted: boolean; hand: boolean })[]
}
type VoiceMessageRow = {
  id: number; room_id: string; sender: string; kind: 'text' | 'system' | 'reaction'; body: string | null
  payload: Record<string, unknown> | null; sender_name: string; sender_gender: string | null; sender_age: number | null
  sender_photo: string | null; sender_country: string | null; created_at: string
}
/** What the voice methods need from an open subscription (subscribeVoice). */
type VoiceLive = {
  room: VoiceRoom | null
  refresh: () => void
  mic: (on: boolean) => Promise<void>
  startAudio: () => Promise<void>
  joined: (state: VoiceStateRow, sent: number) => Promise<VoiceRoom>
}
/** ludo_json() (functions): timestamps in server ms. */
type LudoJson = {
  id: string; room: string; status: string; players: (string | null)[]; names: (string | null)[]; out: boolean[]
  tokens: number[]; turn: number | null; dice: number | null; roll: number | null; winner: number | null
  events: LudoEvent[]; version: number; deadline: number | null; endedAt: number | null; now: number
}
/** A ludo_games row (realtime). */
type LudoRow = Omit<LudoJson, 'room' | 'deadline' | 'endedAt' | 'now'> & { room_id: string; deadline: string | null; ended_at: string | null }
type RoomMessageRow = {
  id: number; room_id: string; sender: string; sender_name: string; sender_gender: string; sender_age: number
  sender_photo: string | null; sender_country: string | null; body: string; created_at: string
}

function fetchWithTimeout(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  const upload = url.includes('/storage/v1/object/') && (init?.method ?? 'GET') !== 'GET'
  if (typeof AbortSignal.timeout !== 'function') return fetch(input, init)
  const timeout = AbortSignal.timeout(upload ? UPLOAD_TIMEOUT_MS : REQUEST_TIMEOUT_MS)
  let signal: AbortSignal = timeout
  if (init?.signal) {
    // Without AbortSignal.any (older browsers) the caller's own signal wins and there is no timeout.
    signal = typeof AbortSignal.any === 'function' ? AbortSignal.any([init.signal, timeout]) : init.signal
  }
  return fetch(input, { ...init, signal })
}

/** Database and function errors carry the rule name in their text ("exit_locked", ...). */
function codeOf(text: string): string {
  if (text.includes('row-level security')) return 'blocked'
  return CODES.find((c) => text.includes(c)) ?? 'network'
}

function fail(error: { message?: string; details?: string | null; hint?: string | null } | null): never {
  const text = [error?.message, error?.details, error?.hint].filter(Boolean).join(' ')
  throw new BackendError(codeOf(text))
}

function gender(g: string): Gender {
  return g === 'male' || g === 'female' ? g : 'other'
}

function ageOn(birth: string, today = new Date()): number {
  const [y, m, d] = birth.split('-').map(Number)
  let age = today.getFullYear() - y
  if (today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d)) age--
  return age
}

/**
 * Postgres sends "2026-10-19 12:41:01.00537+00" / "...T12:41:01.005372+00:00". Older Safari rejects more than
 * three fraction digits, a space separator and a "+00" offset, so normalise to strict ISO first.
 */
export const time = (ts: string) =>
  Date.parse(
    ts
      .replace(' ', 'T')
      .replace(/(\.\d{3})\d+/, '$1')
      .replace(/([+-]\d{2})$/, '$1:00'),
  )

/** Shrinks a photo to 1280 px JPEG to save mobile data (same as the app). */
async function compressImage(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file).catch(() => {
    throw new BackendError('bad_media')
  })
  const scale = Math.min(1, MAX_PHOTO_PX / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new BackendError('bad_media'))), 'image/jpeg', 0.8),
  )
}

type TypingListener = { typing: (from: string) => void; presence: (connected: boolean, partnerHere: boolean) => void }
type TypingHub = {
  channel: RealtimeChannel
  listeners: Set<TypingListener>
  /** Our own connection to the channel is up. */
  connected: boolean
  /** The partner's app is in the chat right now (presence). */
  partnerHere: boolean
  /** The partner's presence was seen at least once (only then does a disconnect start the grace period). */
  presenceSeen: boolean
}

export class SupabaseBackend implements Backend {
  private client: SupabaseClient
  /** Signed media URL and when to stop reusing it (they are valid for 12 h). */
  private signedUrls = new Map<string, { url: string; reuseUntil: number }>()
  private readSent = new Map<string, number>()
  /**
   * One channel per match for typing signals and presence ("my app is in this chat"), shared by every view of it
   * (realtime-js reuses a channel per topic). The Android app uses the same topic and presence key (the user id).
   */
  private typing = new Map<string, TypingHub>()
  /** Newest message id from the last complete fetch of a chat or room; re-syncs ask only for newer ones. */
  private newest = new Map<string, number>()
  /** Matches the user never saw, waiting to be ended; opening one again (the server can hand it back) keeps it. */
  private abandoned = new Set<string>()
  private userId: string | null = null
  /** For the leave request sent while the page closes (no time to wait for the session then). */
  private accessToken: string | null = null
  /** Open voice rooms (subscribeVoice), for the actions taken in them. */
  private voice = new Map<string, VoiceLive>()
  /** Server clock minus this device's, and the round trip it was measured with. */
  private clockOffset = 0
  private clockRtt = Infinity

  constructor(private url: string, private anonKey: string) {
    this.client = createClient(url, anonKey, {
      auth: { persistSession: true, storageKey: 'yaari_auth' },
      global: { fetch: fetchWithTimeout },
    })
    this.client.auth.onAuthStateChange((_event, session) => {
      this.userId = session?.user.id ?? null
      this.accessToken = session?.access_token ?? null
    })
  }

  private get uid(): string {
    if (!this.userId) throw new BackendError('not_signed_in')
    return this.userId
  }

  private async rpc<T>(fn: string, args?: Record<string, unknown>): Promise<T> {
    const { data, error } = await this.client.rpc(fn, args)
    if (error) fail(error)
    return data as T
  }

  /** Edge function errors come back as a Response whose JSON body names the rule. */
  private async invoke<T>(fn: string, body?: Record<string, unknown>): Promise<T> {
    const { data, error } = await this.client.functions.invoke(fn, body === undefined ? {} : { body })
    if (error) {
      const ctx = (error as { context?: Response }).context
      const detail = ctx ? await ctx.clone().json().then((j) => String(j?.error ?? ''), () => '') : ''
      throw new BackendError(codeOf(`${detail} ${error.message}`))
    }
    return data as T
  }

  // ---- account ----

  async sendOtp(phone: string) {
    const { error } = await this.client.auth.signInWithOtp({ phone: `+${phone}` })
    if (error) {
      const code = (error as { code?: string }).code ?? ''
      if (code === 'over_sms_send_rate_limit' || error.status === 429) throw new BackendError('slow_down')
      if (code === 'sms_send_failed' || code === 'phone_provider_disabled') throw new BackendError('sms_unavailable')
      fail(error)
    }
  }

  async verifyOtp(phone: string, code: string) {
    const { data, error } = await this.client.auth.verifyOtp({ phone: `+${phone}`, token: code, type: 'sms' })
    if (error || !data.session) return false
    this.userId = data.session.user.id
    return true
  }

  async isSignedIn() {
    const { data } = await this.client.auth.getSession()
    this.userId = data.session?.user.id ?? null
    return this.userId !== null
  }

  /** Phone number of the signed-in account, for Settings. */
  async myPhone(): Promise<string | null> {
    const { data } = await this.client.auth.getSession()
    const phone = data.session?.user.phone
    return phone ? `+${phone}` : null
  }

  async myProfile(): Promise<Profile | null> {
    const me = this.uid
    // Profile and posts together: one round trip instead of two on every app start.
    const [{ data, error }, posts] = await Promise.all([
      this.client.from('profiles')
        .select('id, name, gender, birth_date, city, bio, photo_path, country').eq('id', me).maybeSingle<ProfileRow>(),
      this.posts(me),
    ])
    if (error) fail(error)
    if (!data) return null
    return this.toProfile({ ...data, age: ageOn(data.birth_date) }, posts)
  }

  async createProfile(p: NewProfile) {
    const photo = p.photo ? await this.upload(p.photo, 'avatar') : null
    // Country is set by the server from the verified phone number.
    const { error } = await this.client.from('profiles').insert({
      id: this.uid, name: p.name, gender: p.gender, birth_date: p.birthDate, city: p.city, bio: p.bio, photo_path: photo,
    })
    // 23505: the row already exists — an earlier attempt was saved but its reply was lost. That is success.
    if (error && error.code !== '23505') fail(error)
    const me = await this.myProfile()
    if (!me) throw new BackendError('network')
    return me
  }

  async entitlements(): Promise<Entitlements> {
    const rows = await this.rpc<{ premium_until: string | null; gender_until: string | null }[]>('my_entitlements')
    const row = rows?.[0]
    const now = Date.now()
    return {
      premium: !!row?.premium_until && time(row.premium_until) > now,
      genderPass: !!row?.gender_until && time(row.gender_until) > now,
    }
  }

  async signOut() {
    const { error } = await this.client.auth.signOut()
    if (error) fail(error)
    this.userId = null
  }

  async deleteAccount() {
    await this.invoke('delete-account')
    // The server already removed the user; only the local session is left.
    await this.client.auth.signOut({ scope: 'local' })
    this.userId = null
  }

  // ---- matching & chat ----

  async findMatch(target: Target, lang?: string): Promise<Match | null> {
    let id: string | null
    try {
      // The server prefers someone using the app in the same language for the first few seconds.
      id = await this.rpc<string | null>('find_match2', { p_target: target, p_lang: lang ?? null })
    } catch (e) {
      // A chat we just left (or abandoned) still holds the exit lock for a moment: keep waiting.
      if (e instanceof BackendError && e.code === 'exit_locked') return null
      throw e
    }
    if (!id) return null
    try {
      // One request for the partner's card; their posts load only if the profile is opened (it fetches its own).
      const rows = await this.rpc<CardRow[]>('match_card', { p_match: id })
      if (!rows?.[0]) throw new BackendError('not_found')
      const partner = await this.toProfile(rows[0], [])
      this.abandoned.delete(id)
      return { id, partner }
    } catch {
      // The server paired us but the partner can't be shown: end that chat once the exit lock allows, keep looking.
      this.abandon(id)
      return null
    }
  }

  /** Ends a match the user never saw, after the longest exit lock (5 s), so the other person isn't left waiting. */
  abandon(matchId: string) {
    this.abandoned.add(matchId)
    setTimeout(() => {
      if (this.abandoned.delete(matchId)) void this.leave(matchId).catch(() => undefined)
    }, 5_500)
  }

  async cancelSearch() {
    await this.rpc('cancel_search')
  }

  /** The waiting side of a new match is user_a: its row appearing means "ask again now" (no 2 s wait). */
  matchSignals(on: () => void): () => void {
    const channel = this.client.channel(`match-wake-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'matches', filter: `user_a=eq.${this.uid}` }, () => on())
      .subscribe()
    return () => void this.client.removeChannel(channel)
  }

  /** Null when the match is gone (the other person deleted their account). */
  private async matchRow(id: string): Promise<MatchRow | null> {
    const { data, error } = await this.client.from('matches')
      .select('id, user_a, user_b, ended_at, ended_by, video_status, video_requested_by, read_a, read_b').eq('id', id).maybeSingle<MatchRow>()
    if (error) fail(error)
    return data
  }

  private videoOf(m: MatchRow, me: string): VideoStatus {
    switch (m.video_status) {
      case 'requested': return m.video_requested_by === me ? 'requested_by_me' : 'requested_by_partner'
      case 'accepted': return 'accepted'
      case 'declined': return 'declined'
      default: return 'none'
    }
  }

  subscribe(matchId: string, on: (e: MatchEvent) => void, opts: { live?: boolean } = {}): () => void {
    const me = this.uid
    let closed = false
    let lastVideo: VideoStatus | null = null
    let partnerRead = 0
    let leftSent = false
    const emit = (e: MatchEvent) => { if (!closed) on(e) }
    const partnerLeft = () => {
      if (leftSent) return
      leftSent = true
      emit({ type: 'partner_left' })
    }
    const onMatch = (m: MatchRow | null) => {
      if (m === null || (m.ended_at && m.ended_by !== me)) partnerLeft()
      if (!m) return
      const read = (m.user_a === me ? m.read_b : m.read_a) ?? 0
      if (read > partnerRead) {
        partnerRead = read
        emit({ type: 'read', upto: read })
      }
      const v = this.videoOf(m, me)
      if (v !== lastVideo) {
        lastVideo = v
        emit({ type: 'video', status: v })
      }
    }

    // Catch up: the match row (left? video?) and messages newer than the last complete fetch. Runs when the
    // socket (re)connects, every 10 s as a safety net, and when the tab is shown again; paused while hidden.
    const resync = async () => {
      if (pageHidden() || closed) return
      try {
        onMatch(await this.matchRow(matchId))
        const after = this.newest.get(matchId)
        const list = after === undefined ? await this.history(matchId) : await this.newerMessages(matchId, after)
        for (const message of list) emit({ type: 'message', message })
      } catch { /* next round */ }
    }

    // One bad record (or a failed media link) must not stop the chat: skip it, the re-sync retries.
    const channel = this.client.channel(`match-${matchId}-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages', filter: `match_id=eq.${matchId}` },
        (p) => void this.toMessage(p.new as MessageRow, me).then((message) => {
          if (!message.fromMe) partnerAlive()
          emit({ type: 'message', message })
        }, () => undefined))
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'matches', filter: `id=eq.${matchId}` },
        (p) => onMatch(p.new as MatchRow))
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') void resync()
      })

    // Random chats end by themselves when the partner's app never connects (closed before the match reached it)
    // or disconnects for good, instead of leaving this person in front of a silent chat. Judged only while our
    // own connection is up; any message or typing from them also counts as connected (older app versions).
    let partnerSeen = false
    let joinTimer: ReturnType<typeof setTimeout> | undefined
    let graceTimer: ReturnType<typeof setTimeout> | undefined
    const giveUp = () => {
      if (closed || leftSent) return
      void this.leave(matchId).catch(() => undefined)
      partnerLeft()
    }
    function partnerAlive() {
      partnerSeen = true
      clearTimeout(joinTimer)
      joinTimer = undefined
    }
    const onPresence = (connected: boolean, partnerHere: boolean) => {
      if (!opts.live || closed) return
      if (!connected) {
        clearTimeout(joinTimer)
        clearTimeout(graceTimer)
        joinTimer = graceTimer = undefined
        return
      }
      if (partnerHere) {
        partnerAlive()
        clearTimeout(graceTimer)
        graceTimer = undefined
      } else if (!partnerSeen) {
        joinTimer ??= setTimeout(giveUp, PARTNER_JOIN_TIMEOUT_MS)
      } else if (hub.presenceSeen) {
        graceTimer ??= setTimeout(giveUp, PARTNER_GRACE_MS)
      }
    }

    // Typing signals and presence ride a channel shared with the Android app (nothing is stored).
    let typingOff: ReturnType<typeof setTimeout> | undefined
    const listener: TypingListener = {
      typing: (from) => {
        if (from === me) return
        partnerAlive()
        emit({ type: 'typing', on: true })
        clearTimeout(typingOff)
        typingOff = setTimeout(() => emit({ type: 'typing', on: false }), 4_000)
      },
      presence: onPresence,
    }
    const hub = this.typingHub(matchId)
    hub.listeners.add(listener)
    if (hub.connected) onPresence(true, hub.partnerHere)

    const timer = setInterval(() => void resync(), 10_000)
    const stopWatching = onPageVisibility((visible) => visible && void resync())

    return () => {
      closed = true
      clearInterval(timer)
      stopWatching()
      clearTimeout(typingOff)
      clearTimeout(joinTimer)
      clearTimeout(graceTimer)
      hub.listeners.delete(listener)
      if (hub.listeners.size === 0) {
        this.typing.delete(matchId)
        void this.client.removeChannel(hub.channel)
      }
      void this.client.removeChannel(channel)
    }
  }

  private typingHub(matchId: string): TypingHub {
    const existing = this.typing.get(matchId)
    if (existing) return existing
    const me = this.uid
    const hub: TypingHub = {
      channel: this.client.channel(`typing-${matchId}`, { config: { presence: { key: me } } }),
      listeners: new Set(),
      connected: false,
      partnerHere: false,
      presenceSeen: false,
    }
    const tell = () => hub.listeners.forEach((l) => l.presence(hub.connected, hub.partnerHere))
    hub.channel
      .on('broadcast', { event: 'typing' }, ({ payload }) => hub.listeners.forEach((l) => l.typing(String(payload?.from ?? ''))))
      .on('presence', { event: 'sync' }, () => {
        hub.partnerHere = Object.keys(hub.channel.presenceState()).some((key) => key !== me)
        if (hub.partnerHere) hub.presenceSeen = true
        tell()
      })
      .subscribe((status) => {
        hub.connected = status === 'SUBSCRIBED'
        if (hub.connected) void hub.channel.track({ user: me }).catch(() => undefined)
        tell()
      })
    this.typing.set(matchId, hub)
    return hub
  }

  markRead(matchId: string, upto: number) {
    if (upto <= (this.readSent.get(matchId) ?? 0)) return
    this.readSent.set(matchId, upto)
    void this.client.rpc('mark_read', { p_match: matchId, p_upto: upto }).then(({ error }) => {
      if (error && this.readSent.get(matchId) === upto) this.readSent.delete(matchId) // retried on the next call
    })
  }

  async flagNudity(matchId: string) {
    await this.client.rpc('flag_nudity', { p_match: matchId }).then(() => undefined, () => undefined)
  }

  sendTyping(matchId: string) {
    const ch = this.typing.get(matchId)?.channel
    if (!ch || !this.userId) return
    void ch.send({ type: 'broadcast', event: 'typing', payload: { from: this.userId } }).catch(() => undefined)
  }

  async history(matchId: string): Promise<Message[]> {
    const me = this.uid
    const { data, error } = await this.client.from('messages')
      .select('id, match_id, sender, body, media_path, media_kind, created_at')
      .eq('match_id', matchId).order('id', { ascending: false }).limit(200) // the newest 200, shown oldest first
    if (error) fail(error)
    const rows = (data as MessageRow[]).reverse()
    this.noteNewest(matchId, rows)
    return Promise.all(rows.map((r) => this.toMessage(r, me)))
  }

  /** Messages after `afterId`, oldest first (a re-sync after a dropped connection). */
  private async newerMessages(matchId: string, afterId: number): Promise<Message[]> {
    const me = this.uid
    const { data, error } = await this.client.from('messages')
      .select('id, match_id, sender, body, media_path, media_kind, created_at')
      .eq('match_id', matchId).gt('id', afterId).order('id', { ascending: true }).limit(200)
    if (error) fail(error)
    const rows = data as MessageRow[]
    this.noteNewest(matchId, rows)
    return Promise.all(rows.map((r) => this.toMessage(r, me)))
  }

  /**
   * Only complete fetches move the mark: a message that arrived live (or one I sent) can have a newer id than
   * one the socket dropped, so it must not move it.
   */
  private noteNewest(key: string, rows: { id: number }[]) {
    const last = rows.at(-1)?.id
    if (last !== undefined && last > (this.newest.get(key) ?? -1)) this.newest.set(key, last)
  }

  async send(matchId: string, text: string | null, media: File | null): Promise<Message> {
    const kind = media ? (media.type.startsWith('video/') ? 'video' : 'image') : null
    const path = media ? await this.upload(media, 'chat') : null
    const { data, error } = await this.client.from('messages')
      .insert({ match_id: matchId, body: text, media_path: path, media_kind: kind })
      .select('id, match_id, sender, body, media_path, media_kind, created_at').single<MessageRow>()
    if (error) fail(error)
    return this.toMessage(data, this.uid)
  }

  async leave(matchId: string) {
    await this.rpc('leave_match', { p_match: matchId })
  }

  async report(partnerId: string, reason: ReportReason, matchId: string | null) {
    await this.rpc('report_user', matchId ? { p_reported: partnerId, p_reason: reason, p_match: matchId } : { p_reported: partnerId, p_reason: reason })
  }

  async block(userId: string) {
    await this.rpc('block_user', { p_user: userId })
  }

  async requestVideo(matchId: string) {
    await this.rpc('request_video', { p_match: matchId })
  }

  async answerVideo(matchId: string, accept: boolean) {
    await this.rpc('respond_video', { p_match: matchId, p_accept: accept })
  }

  async endVideo(matchId: string) {
    await this.rpc('end_video', { p_match: matchId })
  }

  async videoToken(matchId: string) {
    return this.invoke<{ url: string; token: string }>('video-token', { match_id: matchId })
  }

  // ---- profiles ----

  async recents(): Promise<RecentMatch[]> {
    const rows = await this.rpc<RecentRow[]>('recent_list')
    return Promise.all(rows.map(async (r) => ({
      matchId: r.match_id, partner: await this.toProfile(r, []), metAt: time(r.met_at), lastText: r.last_body,
    })))
  }

  async profile(userId: string) {
    return this.card(userId)
  }

  private async card(userId: string): Promise<Profile | null> {
    const rows = await this.rpc<CardRow[]>('card', { uid: userId })
    const c = rows?.[0]
    if (!c) return null
    return this.toProfile(c, await this.posts(userId))
  }

  private async posts(userId: string): Promise<Post[]> {
    const { data, error } = await this.client.from('posts').select('id, user_id, body, image_path, created_at')
      .eq('user_id', userId).order('created_at', { ascending: false }).limit(60)
    if (error) fail(error)
    return Promise.all((data as PostRow[]).map(async (p) => ({
      id: p.id, text: p.body, imageUrl: p.image_path ? await this.signed(p.image_path) : null, createdAt: time(p.created_at),
    })))
  }

  async addPost(text: string, image: File | null): Promise<Post> {
    const path = image ? await this.upload(image, 'posts') : null
    const { data, error } = await this.client.from('posts').insert({ body: text, image_path: path })
      .select('id, user_id, body, image_path, created_at').single<PostRow>()
    if (error) fail(error)
    return { id: data.id, text: data.body, imageUrl: path ? await this.signed(path) : null, createdAt: time(data.created_at) }
  }

  /** Gender and birth date never change (the server refuses). [photo] is a new picture, or null to keep the current one. */
  async updateProfile(name: string, city: string, bio: string, photo: File | null) {
    const me = this.uid
    const edit: Record<string, string> = { name, city, bio }
    if (photo) edit.photo_path = await this.upload(photo, 'avatar')
    const { error } = await this.client.from('profiles').update(edit).eq('id', me)
    if (error) fail(error)
    const p = await this.myProfile()
    if (!p) throw new BackendError('network')
    return p
  }

  // ---- friends ----

  async friends(): Promise<Friend[]> {
    const rows = await this.rpc<FriendRow[]>('friend_list')
    return Promise.all(rows.map(async (f) => ({
      profile: await this.toProfile(f, []),
      state: (f.state === 'friends' ? 'friends' : f.state === 'requested' ? 'requested' : 'incoming') as FriendState,
      threadId: f.match_id,
    })))
  }

  async requestFriend(userId: string): Promise<FriendState> {
    const state = await this.rpc<string>('request_friend', { p_user: userId })
    return state === 'friends' ? 'friends' : 'requested'
  }

  async answerFriend(userId: string, accept: boolean) {
    await this.rpc('answer_friend', { p_user: userId, p_accept: accept })
  }

  async removeFriend(userId: string) {
    await this.rpc('remove_friend', { p_user: userId })
  }

  async friendThread(userId: string) {
    return this.rpc<string>('friend_thread', { p_user: userId })
  }

  // ---- rooms ----

  async rooms(): Promise<Room[]> {
    const rows = await this.rpc<RoomRow[]>('room_list2')
    return rows.map((r) => ({
      id: r.id, title: r.title, emoji: r.emoji, online: r.online, official: r.official, mine: r.mine,
      ownerName: r.owner_name, category: r.category as RoomCategory, maxMembers: r.max_members,
      genderRule: r.gender_rule as Target, minAge: r.min_age, maxAge: r.max_age, isPrivate: r.is_private,
      code: r.code, refusal: r.refusal as RoomRefusal | null,
    }))
  }

  async createRoom(r: NewRoom) {
    const rows = await this.rpc<{ room_id: string; room_code: string }[]>('create_room', {
      p_title: r.title, p_category: r.category, p_max: r.maxMembers, p_gender: r.genderRule,
      p_min_age: r.minAge, p_max_age: r.maxAge, p_private: r.isPrivate,
    })
    const row = rows?.[0]
    if (!row) throw new BackendError('network')
    return { id: row.room_id, code: row.room_code }
  }

  /** A wrong code comes back as null (the server counts it; 10 misses in 10 minutes → slow_down). */
  async joinRoomCode(code: string) {
    const id = await this.rpc<string | null>('join_room_code', { p_code: code })
    if (!id) throw new BackendError('not_found')
    return id
  }

  async closeRoom(roomId: string) {
    await this.rpc('close_room', { p_room: roomId })
  }

  async joinRoom(roomId: string) {
    await this.rpc('join_room', { p_room: roomId })
  }

  async leaveRoom(roomId: string) {
    await this.rpc('leave_room', { p_room: roomId })
  }

  async roomHistory(roomId: string): Promise<RoomMessage[]> {
    const me = this.uid
    const { data, error } = await this.client.from('room_messages').select('*')
      .eq('room_id', roomId).order('id', { ascending: false }).limit(100) // the newest 100, shown oldest first
    if (error) fail(error)
    const rows = (data as RoomMessageRow[]).reverse()
    this.noteNewest(`room:${roomId}`, rows)
    return Promise.all(rows.map((r) => this.toRoomMessage(r, me)))
  }

  subscribeRoom(roomId: string, on: (m: RoomMessage) => void): () => void {
    const me = this.uid
    let closed = false
    const emit = (m: RoomMessage) => { if (!closed) on(m) }
    // Re-sync after a dropped socket: only messages newer than the last complete fetch (the UI de-duplicates by
    // id). On (re)connect, every 15 s, and when the tab is shown again; paused while it is hidden.
    const resync = () => {
      if (pageHidden() || closed) return
      this.newerRoomMessages(roomId).then((list) => list.forEach(emit), () => undefined)
    }
    const channel = this.client.channel(`room-${roomId}-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'room_messages', filter: `room_id=eq.${roomId}` },
        (p) => void this.toRoomMessage(p.new as RoomMessageRow, me).then(emit, () => undefined))
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') resync()
      })
    const timer = setInterval(resync, 15_000)
    const stopWatching = onPageVisibility((visible) => visible && resync())
    return () => {
      closed = true
      clearInterval(timer)
      stopWatching()
      void this.client.removeChannel(channel)
    }
  }

  private async newerRoomMessages(roomId: string): Promise<RoomMessage[]> {
    const after = this.newest.get(`room:${roomId}`)
    if (after === undefined) return [] // the room's history hasn't loaded yet; it covers everything so far
    const me = this.uid
    const { data, error } = await this.client.from('room_messages').select('*')
      .eq('room_id', roomId).gt('id', after).order('id', { ascending: true }).limit(200)
    if (error) fail(error)
    const rows = data as RoomMessageRow[]
    this.noteNewest(`room:${roomId}`, rows)
    return Promise.all(rows.map((r) => this.toRoomMessage(r, me)))
  }

  async sendRoom(roomId: string, text: string): Promise<RoomMessage> {
    const { data, error } = await this.client.from('room_messages').insert({ room_id: roomId, body: text })
      .select('*').single<RoomMessageRow>()
    if (error) fail(error)
    return this.toRoomMessage(data, this.uid)
  }

  // ---- voice rooms (017_voice_rooms.sql; audio through LiveKit, lib/voice-audio.ts) ----

  async voiceRooms(): Promise<VoiceRoomSummary[]> {
    const rows = await this.rpc<VoiceListRow[]>('voice_list')
    await this.signMany(rows.flatMap((r) => [r.host?.photo_path, ...r.speakers.map((c) => c.photo_path)]))
    return rows.filter((r) => r.host).map((r) => ({
      id: r.id, title: r.title, category: r.category as RoomCategory, host: this.cardProfile(r.host),
      speakers: r.speakers.map((c) => this.cardProfile(c)), listeners: r.listeners,
      nowPlaying: r.now_playing || null, mine: r.mine,
    }))
  }

  async createVoiceRoom(r: NewVoiceRoom): Promise<string> {
    return this.rpc<string>('create_voice_room', { p_title: r.title, p_category: r.category, p_mode: r.mode })
  }

  async joinVoice(roomId: string): Promise<VoiceRoom> {
    const sent = Date.now()
    const state = await this.rpc<VoiceStateRow>('join_voice', { p_room: roomId })
    const live = this.voice.get(roomId)
    if (live) return live.joined(state, sent)
    this.noteClock(state.now, sent)
    return this.toVoiceRoom(state)
  }

  async leaveVoice(roomId: string) {
    await this.rpc('leave_voice', { p_room: roomId })
  }

  async endVoice(roomId: string) {
    await this.rpc('end_voice_room', { p_room: roomId })
    this.voiceSync(roomId) // LiveKit closes the room for everyone still connected
  }

  async voiceLines(roomId: string): Promise<VoiceLine[]> {
    const { data, error } = await this.client.from('voice_messages').select('*').eq('room_id', roomId)
      .in('kind', ['text', 'system']).order('id', { ascending: false }).limit(100) // the newest 100, shown oldest first
    if (error) fail(error)
    const rows = (data as VoiceMessageRow[]).reverse()
    this.noteNewest(`voice:${roomId}`, rows)
    return this.toVoiceLines(rows)
  }

  async sendVoice(roomId: string, text: string): Promise<VoiceLine> {
    const row = await this.rpc<VoiceMessageRow>('voice_send', { p_room: roomId, p_text: text })
    return (await this.toVoiceLines([row]))[0]
  }

  async reportVoiceRoom(roomId: string, reason: ReportReason) {
    // A room is reported through its host (the report keeps what the host wrote in it).
    const hostId = this.voice.get(roomId)?.room?.hostId
      ?? (await this.rpc<VoiceStateRow>('voice_state', { p_room: roomId })).host
    if (hostId === this.uid) throw new BackendError('not_allowed')
    await this.report(hostId, reason, null)
  }

  async voiceStartAudio(roomId: string) {
    await this.voice.get(roomId)?.startAudio()
  }

  async voiceAction(roomId: string, a: VoiceAction) {
    const room = { p_room: roomId }
    const live = this.voice.get(roomId)
    switch (a.type) {
      case 'hand':
        await this.rpc('voice_hand', { ...room, p_up: a.up })
        break
      case 'mic':
        if (!live) throw new BackendError('network')
        await live.mic(a.on)
        break
      case 'seat':
        // An open room seats me; a hand-raise room raises my hand instead. Either way LiveKit hears about it.
        if ((await this.rpc<string>('voice_take_seat', { ...room, p_seat: a.seat })) === 'seated') this.voiceSync(roomId, this.uid)
        break
      case 'unseat':
        await live?.mic(false).catch(() => undefined)
        await this.rpc('voice_leave_seat', room)
        this.voiceSync(roomId, this.uid)
        break
      case 'react':
        await this.rpc('voice_react', { ...room, p_kind: a.kind })
        return // it comes back over realtime, like everyone else's
      case 'invite':
        await this.rpc('voice_invite', { ...room, p_user: a.userId, p_seat: a.seat ?? null })
        this.voiceSync(roomId, a.userId)
        break
      case 'answer':
        await this.rpc('voice_answer', { ...room, p_user: a.userId, p_accept: a.accept })
        if (a.accept) this.voiceSync(roomId, a.userId)
        break
      case 'toAudience':
        await this.rpc('voice_to_audience', { ...room, p_user: a.userId })
        this.voiceSync(roomId, a.userId)
        break
      case 'mute':
        await this.rpc('voice_mute_member', { ...room, p_user: a.userId })
        this.voiceSync(roomId, a.userId, true)
        break
      case 'lock':
        await this.rpc('voice_lock_seat', { ...room, p_seat: a.seat, p_on: a.locked })
        break
      case 'cohost':
        await this.rpc('voice_set_cohost', { ...room, p_user: a.userId, p_on: a.on })
        break
      case 'kick':
        await this.rpc('voice_kick', { ...room, p_user: a.userId })
        this.voiceSync(roomId, a.userId)
        break
      case 'addTrack':
      case 'suggest':
        // The server decides: the host's track plays or queues, anyone else's waits for the host.
        await this.rpc('voice_queue_add', { ...room, p_video: a.videoId, p_title: '' })
        break
      case 'approve':
        await this.rpc('voice_queue_approve', { ...room, p_item: a.itemId, p_accept: a.accept })
        break
      case 'play':
        await this.rpc('voice_play_item', { ...room, p_item: a.itemId })
        break
      case 'removeItem':
        await this.rpc('voice_queue_remove', { ...room, p_item: a.itemId })
        break
      case 'pause':
        await this.rpc('voice_pause', room)
        break
      case 'resume':
        await this.rpc('voice_resume', room)
        break
      case 'skip':
        await this.rpc('voice_skip', { ...room, p_current: a.trackId ?? live?.room?.track?.id ?? null })
        break
    }
    live?.refresh() // don't wait for the realtime round trip
  }

  /** Applies the room's state to LiveKit (edge function voice-sync). Fire and forget: the host's client re-checks every minute. */
  private voiceSync(roomId: string, userId?: string, mute?: boolean) {
    void this.invoke('voice-sync', { room_id: roomId, ...(userId ? { user_id: userId } : {}), ...(mute ? { mute: true } : {}) })
      .catch(() => undefined)
  }

  /**
   * Follows a voice room: its row (every change bumps `rev`, then the state is fetched again), its chat and
   * reactions over realtime, a heartbeat every 20 s (also in the background: people keep listening, as on
   * X Spaces), and the LiveKit audio once I'm in. Re-syncs on (re)connect, every 10 s and when the tab is shown.
   */
  subscribeVoice(roomId: string, on: (e: VoiceEvent) => void): () => void {
    const me = this.uid
    let closed = false
    let joined = false
    let rev = -1
    let over = false
    let beats = 0
    const emit = (e: VoiceEvent) => { if (!closed) on(e) }
    const finish = (e: 'ended' | 'removed') => {
      if (over) return
      over = true
      stopAudio()
      emit({ type: e })
    }
    const failed = (err: unknown) => {
      const code = err instanceof BackendError ? err.code : ''
      if (code === 'room_closed' || code === 'not_found') finish('ended')
      else if (code === 'voice_removed') finish('removed')
    }

    // ---- state ----
    let mine: { seat: number | null; muted: boolean } | null = null
    const apply = async (state: VoiceStateRow, sent: number) => {
      this.noteClock(state.now, sent)
      const room = await this.toVoiceRoom(state)
      if (closed || over) return room
      rev = Math.max(rev, state.rev)
      live.room = room
      const m = room.members.find((x) => x.profile.id === me)
      const before = mine
      mine = m ? { seat: m.seat, muted: m.muted } : null
      if (mine && before) {
        // Brought up to a seat: ask LiveKit for the right to talk. Sent back to the audience, or muted by a host:
        // the microphone goes off here too.
        if (before.seat === null && mine.seat !== null) this.voiceSync(roomId, me)
        if ((mine.seat === null && before.seat !== null) || (mine.muted && !before.muted)) void audio?.setMic(false).catch(() => undefined)
      }
      if (mine && !audio && !audioCtl) void connectAudio()
      emit({ type: 'room', room })
      return room
    }
    let fetching = false
    let again = false
    let debounce: ReturnType<typeof setTimeout> | undefined
    const fetchState = async () => {
      if (!joined || closed || over) return
      if (fetching) {
        again = true
        return
      }
      fetching = true
      try {
        const sent = Date.now()
        await apply(await this.rpc<VoiceStateRow>('voice_state', { p_room: roomId }), sent)
      } catch (err) {
        failed(err)
      } finally {
        fetching = false
        if (again) {
          again = false
          void fetchState()
        }
      }
    }
    const refresh = () => {
      clearTimeout(debounce)
      debounce = setTimeout(() => void fetchState(), 150)
    }

    // ---- lines ----
    const resyncLines = async () => {
      const after = this.newest.get(`voice:${roomId}`)
      if (after === undefined) return // the history hasn't loaded yet; it covers everything so far
      const { data, error } = await this.client.from('voice_messages').select('*').eq('room_id', roomId)
        .in('kind', ['text', 'system']).gt('id', after).order('id', { ascending: true }).limit(200)
      if (error) return
      const rows = data as VoiceMessageRow[]
      this.noteNewest(`voice:${roomId}`, rows)
      for (const line of await this.toVoiceLines(rows)) emit({ type: 'line', line })
    }
    const onMessage = async (row: VoiceMessageRow) => {
      if (row.kind === 'reaction') {
        // A reaction is a moment: an old one (replayed after a reconnect) is not shown.
        const kind = row.payload?.kind
        if (typeof kind === 'string' && Date.now() - time(row.created_at) < 15_000) {
          emit({ type: 'reaction', userId: row.sender, kind: kind as VoiceReaction })
        }
        return
      }
      for (const line of await this.toVoiceLines([row])) emit({ type: 'line', line })
    }

    // ---- audio ----
    let audio: VoiceAudio | null = null
    let audioCtl: AbortController | null = null
    let audioState: VoiceAudioState = { status: 'connecting', needsTap: false }
    let audioTries = 0
    let audioRetry: ReturnType<typeof setTimeout> | undefined
    const setAudio = (patch: Partial<VoiceAudioState>) => {
      audioState = { ...audioState, ...patch }
      emit({ type: 'audio', audio: audioState })
    }
    const retryAudio = () => {
      clearTimeout(audioRetry)
      // A weak network gets a few quick tries, then one every 30 s; the room itself (chat, seats) carries on.
      audioRetry = setTimeout(() => void connectAudio(), Math.min(30_000, 2_000 * 2 ** audioTries++))
    }
    const connectAudio = async () => {
      if (closed || over || audioCtl) return
      const ctl = new AbortController()
      audioCtl = ctl
      try {
        const [{ url, token }, { joinVoiceAudio }] = await Promise.all([
          this.invoke<{ url: string; token: string }>('voice-token', { room_id: roomId }),
          retryImport(() => import('./voice-audio')),
        ])
        if (ctl.signal.aborted) return
        const a = await joinVoiceAudio(url, token, {
          status: (status) => setAudio({ status }),
          blocked: (needsTap) => setAudio({ needsTap }),
          levels: (levels) => emit({ type: 'levels', levels }),
          permissions: () => permissionWaiters.splice(0).forEach((f) => f()),
          micMutedByServer: () => refresh(),
          gone: (removed) => {
            if (audioCtl !== ctl) return
            audio = null
            audioCtl = null
            emit({ type: 'levels', levels: {} })
            if (removed) void fetchState() // removed or ended: the state says which
            retryAudio()
          },
        }, ctl.signal)
        if (ctl.signal.aborted) return void a.disconnect()
        audio = a
        audioTries = 0
        audioWaiters.splice(0).forEach((f) => f(a))
        // Back after a dropped connection while unmuted on a seat: talk again (or show the mic as off).
        if (mine?.seat !== null && mine?.muted === false) {
          a.setMic(true).catch(() => void this.rpc('voice_mic', { p_room: roomId, p_on: false }).then(refresh, () => undefined))
        }
      } catch (err) {
        if (ctl.signal.aborted || audioCtl !== ctl) return
        audioCtl = null
        setAudio({ status: 'failed' })
        // Not in the room any more (403): the state will say why; anything else is worth another try.
        if (err instanceof BackendError && err.code === 'not_allowed') void fetchState()
        retryAudio()
      }
    }
    const stopAudio = () => {
      clearTimeout(audioRetry)
      audioCtl?.abort()
      audioCtl = null
      void audio?.disconnect().catch(() => undefined)
      audio = null
      audioWaiters.splice(0).forEach((f) => f(null))
    }
    const audioWaiters: ((a: VoiceAudio | null) => void)[] = []
    const permissionWaiters: (() => void)[] = []
    const waitAudio = (ms: number) =>
      audio ? Promise.resolve(audio) : new Promise<VoiceAudio | null>((resolve) => {
        audioWaiters.push(resolve)
        setTimeout(() => resolve(audio), ms)
      })
    const waitPermission = (ms: number) => new Promise<void>((resolve) => {
      permissionWaiters.push(resolve)
      setTimeout(resolve, ms)
    })

    const mic = async (on: boolean) => {
      if (!on) {
        await audio?.setMic(false).catch(() => undefined)
        await this.rpc('voice_mic', { p_room: roomId, p_on: false })
        return
      }
      const a = await waitAudio(8_000)
      if (!a) throw new BackendError('network')
      const enable = async () => {
        try {
          await a.setMic(true)
          return true
        } catch (e) {
          if (e instanceof Error && e.message === 'mic_denied') throw new BackendError('mic_denied')
          return false
        }
      }
      if (!(await enable())) {
        // LiveKit hasn't heard I'm on a seat yet: ask for it and try once more.
        this.voiceSync(roomId, me)
        await waitPermission(4_000)
        if (!(await enable())) throw new BackendError('not_allowed')
      }
      try {
        await this.rpc('voice_mic', { p_room: roomId, p_on: true })
      } catch (e) {
        await a.setMic(false).catch(() => undefined)
        throw e
      }
    }

    const live: VoiceLive = {
      room: null,
      refresh,
      mic,
      startAudio: async () => {
        await audio?.startAudio()
        setAudio({ needsTap: false })
      },
      joined: async (state, sent) => {
        joined = true
        return apply(state, sent)
      },
    }
    this.voice.set(roomId, live)

    // ---- keeping up ----
    const heartbeat = async () => {
      if (!joined || closed || over) return
      try {
        const now = await this.rpc<number>('voice_heartbeat', { p_room: roomId })
        if (now !== rev) refresh()
        // The host re-checks LiveKit against the room every minute (anyone a sync missed).
        if (++beats % 3 === 0 && live.room?.hostId === me) this.voiceSync(roomId)
      } catch (err) {
        failed(err)
      }
    }
    const resync = () => {
      if (pageHidden() || closed || !joined) return
      void (async () => {
        const { data } = await this.client.from('voice_rooms').select('rev, ended_at').eq('id', roomId).maybeSingle<{ rev: number; ended_at: string | null }>()
        if (data?.ended_at) finish('ended')
        else if (data && data.rev !== rev) refresh()
        await resyncLines()
      })().catch(() => undefined)
    }
    const channel = this.client.channel(`voice-${roomId}-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'voice_rooms', filter: `id=eq.${roomId}` }, (p) => {
        const row = p.new as { rev: number; ended_at: string | null }
        if (row.ended_at) finish('ended')
        else if (row.rev !== rev) refresh()
      })
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'voice_messages', filter: `room_id=eq.${roomId}` },
        (p) => void onMessage(p.new as VoiceMessageRow).catch(() => undefined))
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') resync()
      })
    const beatTimer = setInterval(() => void heartbeat(), 20_000)
    const syncTimer = setInterval(resync, 10_000)
    const stopWatching = onPageVisibility((visible) => visible && resync())
    // Closing the tab leaves at once (a listener's seat and count free up); the host keeps their 2 minutes
    // to come back after a reload.
    const onPageHide = () => {
      if (!joined || over || live.room?.hostId === me || !this.accessToken) return
      void fetch(`${this.url}/rest/v1/rpc/leave_voice`, {
        method: 'POST',
        keepalive: true,
        headers: { apikey: this.anonKey, Authorization: `Bearer ${this.accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_room: roomId }),
      }).catch(() => undefined)
    }
    window.addEventListener('pagehide', onPageHide)

    return () => {
      closed = true
      clearTimeout(debounce)
      clearInterval(beatTimer)
      clearInterval(syncTimer)
      stopWatching()
      window.removeEventListener('pagehide', onPageHide)
      stopAudio()
      if (this.voice.get(roomId) === live) this.voice.delete(roomId)
      void this.client.removeChannel(channel)
    }
  }

  // ---- Ludo in a voice room (018_ludo.sql): every change bumps the game's version; clients follow its row ----

  async ludoGame(roomId: string): Promise<LudoGame | null> {
    const sent = Date.now()
    const j = await this.rpc<LudoJson | null>('ludo_state', { p_room: roomId })
    return j ? this.fromLudoJson(j, sent) : null
  }

  async ludoAction(roomId: string, gameId: string | null, a: LudoAction): Promise<LudoGame | null> {
    const game = { p_game: gameId }
    const sent = Date.now()
    let j: LudoJson
    switch (a.type) {
      case 'open':
        j = await this.rpc('ludo_open', { p_room: roomId })
        break
      case 'join':
        j = await this.rpc('ludo_join', { ...game, p_seat: a.seat ?? null })
        break
      case 'leave':
      case 'start':
      case 'close':
        j = await this.rpc(`ludo_${a.type}`, game)
        break
      case 'roll':
      case 'timeout':
        j = await this.rpc(`ludo_${a.type}`, { ...game, p_version: a.version })
        break
      case 'move':
        j = await this.rpc('ludo_move', { ...game, p_token: a.token, p_version: a.version })
        break
    }
    return this.fromLudoJson(j, sent)
  }

  /** The game row over realtime (a new game is an insert), re-read when the channel (re)connects or the tab is shown. */
  subscribeLudo(roomId: string, on: (game: LudoGame) => void): () => void {
    let closed = false
    const resync = () => {
      if (closed || pageHidden()) return
      this.ludoGame(roomId).then((g) => !closed && g && on(g), () => undefined)
    }
    const row = (p: { new: unknown }) => {
      if (!closed) on(this.fromLudoRow(p.new as LudoRow))
    }
    const channel = this.client.channel(`ludo-${roomId}-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'ludo_games', filter: `room_id=eq.${roomId}` }, row)
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'ludo_games', filter: `room_id=eq.${roomId}` }, row)
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') resync()
      })
    const stopWatching = onPageVisibility((visible) => visible && resync())
    return () => {
      closed = true
      stopWatching()
      void this.client.removeChannel(channel)
    }
  }

  private fromLudoJson(j: LudoJson, sent: number): LudoGame {
    this.noteClock(j.now, sent)
    return this.toLudo({ ...j, roomId: j.room })
  }

  private fromLudoRow(r: LudoRow): LudoGame {
    const ms = (ts: string | null) => (ts ? time(ts) : null) // the server's clock, as ludo_json() sends it
    return this.toLudo({ ...r, roomId: r.room_id, deadline: ms(r.deadline), endedAt: ms(r.ended_at) })
  }

  /** Server times → this device's clock. */
  private toLudo(g: Omit<LudoJson, 'room' | 'now'> & { roomId: string }): LudoGame {
    const local = (ms: number | null) => (ms === null ? null : ms - this.clockOffset)
    return {
      id: g.id, roomId: g.roomId, status: g.status as LudoStatus, players: g.players, names: g.names, out: g.out,
      tokens: g.tokens, turn: g.turn, dice: g.dice, roll: g.roll, winner: g.winner, events: g.events ?? [],
      version: g.version, deadline: local(g.deadline), endedAt: local(g.endedAt),
    }
  }

  /** Server clock minus mine, from the reply with the shortest round trip (the music position depends on it). */
  private noteClock(serverNow: number, sent: number) {
    const received = Date.now()
    const rtt = received - sent
    if (!Number.isFinite(serverNow) || rtt > this.clockRtt + 150) {
      this.clockRtt += 20 // a better sample is welcome again after a while
      return
    }
    this.clockRtt = rtt
    this.clockOffset = serverNow - (sent + rtt / 2)
  }

  private async toVoiceRoom(s: VoiceStateRow): Promise<VoiceRoom> {
    await this.signMany(s.members.map((m) => m.photo_path))
    const local = (ms: number) => ms - this.clockOffset // server time → this device's clock
    return {
      id: s.id, title: s.title, category: s.category as RoomCategory, mode: s.mode === 'open' ? 'open' : 'approval',
      hostId: s.host, meId: this.uid,
      members: s.members.map((m) => ({
        profile: this.cardProfile(m), role: m.role as VoiceRole, seat: m.seat, muted: m.muted, hand: m.hand,
      })),
      seats: Array.from({ length: VOICE_SEATS }, (_, index) => ({ index, locked: s.locked.includes(index) })),
      track: s.music ? {
        id: s.music.id, videoId: s.music.videoId, title: s.music.title, addedBy: s.music.by,
        startedAt: local(s.music.startedAt), pausedAt: s.music.pausedAt === null ? null : local(s.music.pausedAt),
      } : null,
      queue: s.queue.map((q) => ({ id: q.id, videoId: q.videoId, title: q.title, addedBy: q.by, pending: q.suggested })),
    }
  }

  private async toVoiceLines(rows: VoiceMessageRow[]): Promise<VoiceLine[]> {
    const me = this.uid
    await this.signMany(rows.map((r) => (r.kind === 'text' ? r.sender_photo : null)))
    return rows.map((r): VoiceLine => {
      const at = time(r.created_at)
      if (r.kind === 'text') {
        return {
          id: r.id, kind: 'text', fromMe: r.sender === me, text: r.body ?? '', at,
          sender: this.cardProfile({
            id: r.sender, name: r.sender_name, gender: r.sender_gender ?? 'other', age: r.sender_age ?? 0, city: '', bio: '',
            photo_path: r.sender_photo, country: r.sender_country,
          }),
        }
      }
      const p = r.payload ?? {}
      return {
        id: r.id, kind: 'system', event: String(p.event) as VoiceSystemEvent, name: String(p.name ?? r.sender_name), at,
        ...(typeof p.seat === 'number' ? { seat: p.seat } : {}),
        ...(typeof p.title === 'string' ? { title: p.title } : {}),
      }
    })
  }

  /** A card from a voice room; the photo comes from signMany (one request for the whole room), else the initial. */
  private cardProfile(c: CardRow): Profile {
    const hit = c.photo_path ? this.signedUrls.get(c.photo_path) : undefined
    return {
      id: c.id, name: c.name, gender: gender(c.gender), age: c.age, city: c.city, bio: c.bio,
      photoUrl: hit?.url ?? null, country: c.country, posts: [],
    }
  }

  /** Signs every photo not signed yet in one request (a voice room shows dozens of faces at once). */
  private async signMany(paths: (string | null | undefined)[]) {
    const now = Date.now()
    const need = [...new Set(paths.filter((p): p is string => !!p && !((this.signedUrls.get(p)?.reuseUntil ?? 0) > now)))]
    if (!need.length) return
    const { data } = await this.client.storage.from(BUCKET).createSignedUrls(need, SIGNED_URL_SECONDS).catch(() => ({ data: null }))
    for (const d of data ?? []) {
      if (d.path && d.signedUrl) this.signedUrls.set(d.path, { url: d.signedUrl, reuseUntil: now + 50 * 60_000 })
    }
  }

  /** A photo the viewer may no longer see (that person left the room long ago) shows the initial instead. */
  private async toRoomMessage(r: RoomMessageRow, me: string): Promise<RoomMessage> {
    const photoUrl = r.sender_photo ? await this.signed(r.sender_photo).catch(() => null) : null
    return {
      id: r.id,
      sender: {
        id: r.sender, name: r.sender_name, gender: gender(r.sender_gender), age: r.sender_age, city: '', bio: '',
        photoUrl, country: r.sender_country, posts: [],
      },
      fromMe: r.sender === me, text: r.body, at: time(r.created_at),
    }
  }

  // ---- mapping & media ----

  private async toProfile(c: CardRow, posts: Post[]): Promise<Profile> {
    return {
      id: c.id, name: c.name, gender: gender(c.gender), age: c.age, city: c.city, bio: c.bio,
      photoUrl: c.photo_path ? await this.signed(c.photo_path).catch(() => null) : null, country: c.country, posts,
    }
  }

  private async toMessage(r: MessageRow, me: string): Promise<Message> {
    return {
      id: r.id, fromMe: r.sender === me, text: r.body,
      media: r.media_path ? { url: await this.signed(r.media_path), kind: r.media_kind === 'video' ? 'video' : 'image' } : null,
      at: time(r.created_at),
    }
  }

  private async signed(path: string): Promise<string> {
    const now = Date.now()
    const hit = this.signedUrls.get(path)
    if (hit && now < hit.reuseUntil) return hit.url
    const { data, error } = await this.client.storage.from(BUCKET).createSignedUrl(path, SIGNED_URL_SECONDS)
    if (error || !data) fail(error)
    this.signedUrls.set(path, { url: data.signedUrl, reuseUntil: now + 50 * 60_000 })
    return data.signedUrl
  }

  /** Uploads under "<uid>/<folder>/"; photos are shrunk to 1280 px JPEG, videos are capped at 25 MB. */
  private async upload(file: File, folder: 'avatar' | 'chat' | 'posts'): Promise<string> {
    const isVideo = file.type.startsWith('video/')
    let body: Blob = file
    let type = file.type
    let ext: string
    if (isVideo) {
      if (!VIDEO_TYPES.includes(file.type)) throw new BackendError('bad_media')
      if (file.size > MAX_VIDEO_BYTES) throw new BackendError('too_large')
      ext = type === 'video/mp4' ? 'mp4' : type.split('/')[1] || 'mp4'
    } else {
      body = await compressImage(file)
      type = 'image/jpeg'
      ext = 'jpg'
    }
    const path = `${this.uid}/${folder}/${crypto.randomUUID()}.${ext}`
    const { error } = await this.client.storage.from(BUCKET).upload(path, body, { contentType: type })
    if (error) throw new BackendError(codeOf(error.message))
    return path
  }
}
