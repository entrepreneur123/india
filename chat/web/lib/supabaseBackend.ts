// Live server: the same Supabase project and rules as the Android live build
// (chat/app/src/live/.../SupabaseBackend.kt). Every rule is re-checked in Postgres (chat/server/migrations).
import { createClient, type RealtimeChannel, type SupabaseClient } from '@supabase/supabase-js'
import {
  BackendError,
  type Backend,
  type Badges,
  type FeedPost,
  type PostComment,
  type PostVisibility,
  type ProfileStyleInput,
  type Friend,
  type FriendActivity,
  type GiftCheck,
  type GroupChat,
  type GroupLine,
  type GroupLineKind,
  type Presence,
  type PersonRow,
  type FriendState,
  type InboxItem,
  type IncomingCall,
  type KycResult,
  type Look,
  type LookWear,
  type Shop,
  type MyTrust,
  type Trust,
  MAX_PHOTOS,
  type Highlight,
  type ExploreKind,
  type ExplorePerson,
  type FeedItem,
  type ProfileSocial,
  type Gender,
  type Match,
  type MatchEvent,
  type Message,
  type NewProfile,
  type Post,
  type Profile,
  type RecentMatch,
  type ReportReason,
  type RoomQuery,
  type RoomRefusal,
  type NewVoiceRoom,
  type Target,
  type VideoStatus,
  VOICE_SEATS,
  type VoiceAction,
  type VoiceAudioState,
  type VoiceEvent,
  type VoiceLine,
  type VoicePoll,
  type VoiceReaction,
  type VoiceRole,
  type VoiceRoom,
  type VoiceRoomSummary,
  type VoiceSystemEvent,
  type LinkMeta,
  type LookingPost,
  type PartyState,
} from './backend'
import { safeUrl, shareable } from './links'
import { retryImport } from './retry-import'
import { parseReply, reactionClock } from './voice-chat'
import { parsePoll, POLLS_PER_READ } from './voice-polls'
import { parseKycResult, parseMyTrust, parseTrustList } from './trust'
import { parseLook, parseLooks, parseShop } from './looks'
import { CONTACT_APPS, type Contacts } from './contacts'
import type { HandleCheck } from './handle'
import type { VoiceAudio } from './voice-audio'
import { track } from './telemetry'
import { onPageVisibility, pageHidden } from './visibility'
import { droppedOut, nextHost, VOICE_BEAT_MS } from './voice-lost'
import { saveVolume } from './voice-volumes'
import { hasThumb, registerThumb, thumbPath, THUMB_PX } from './thumbs'

const BUCKET = 'media'
const MAX_PHOTO_PX = 1280
const MAX_VIDEO_BYTES = 25 * 1024 * 1024
const SIGNED_URL_SECONDS = 3600
const SIGNED_KEY = 'yaari_media_urls'
const MESSAGE_COLUMNS = 'id, match_id, sender, body, media_path, media_kind, created_at, client_id'
/** One year: stored files never change. */
const CACHE_FOREVER = '31536000'

/** The signed URLs kept from the last visit (see signedUrls). */
function loadSigned() {
  const map = new Map<string, { url: string; reuseUntil: number }>()
  try {
    const now = Date.now()
    for (const [k, v] of JSON.parse(localStorage.getItem(SIGNED_KEY) ?? '[]') as [string, { url: string; reuseUntil: number }][]) {
      if (typeof k === 'string' && v && typeof v.url === 'string' && v.reuseUntil > now) map.set(k, v)
    }
  } catch {
    /* none kept */
  }
  for (const k of map.keys()) {
    const full = map.get(k)?.url
    const small = hasThumb(k) ? map.get(thumbPath(k))?.url : undefined
    if (full && small) registerThumb(full, small)
  }
  return map
}
/** The storage bucket's allowed_mime_types (001_core.sql); anything else is refused by the server. */
const VIDEO_TYPES = ['video/mp4', 'video/3gpp', 'video/quicktime', 'video/webm']
/** A request that hangs on a weak network fails after this long, so the screen can retry (uploads get longer). */
const REQUEST_TIMEOUT_MS = 15_000
const UPLOAD_TIMEOUT_MS = 120_000
/** A listener whose tab stays in the background this long leaves the room. */
const IDLE_LEAVE_MS = 30 * 60_000
/** Random chats: the partner's app must connect to the chat within this long, or the chat ends. */
const PARTNER_JOIN_TIMEOUT_MS = 12_000
/** Random chats: after the partner's app disconnects, how long they have to come back. Same values as the app. */
const PARTNER_GRACE_MS = 20_000
const CODES = [
  'exit_locked', 'no_profile', 'not_signed_in', 'bad_target', 'premium_required', 'banned', 'under_18', 'slow_down', 'not_allowed', 'not_found',
  'video_not_configured', 'billing_not_configured', 'already_redeemed', 'wrong_account', 'too_large',
  'room_closed', 'room_gender', 'room_age', 'room_full', 'room_password', 'room_title', 'room_size', 'voice_removed', 'mic_denied', 'queue_full',
  'poll_closed', 'bad_request', 'in_random_chat', 'bad_media',
  'verify_required', 'manner_low', 'kyc_unavailable', 'already_verified', 'kyc_failed', 'too_late', 'already_rated',
  'handle_invalid', 'handle_reserved', 'handle_taken', 'handle_cooldown', 'name_invalid', 'not_owned',
  'contact_invalid', 'not_friends', 'too_many_posts', 'offline', 'party_small',
]

type PersonRowRaw = { id: string; handle: string; name: string; photo_path: string | null }
type CardRow = {
  id: string; name: string; gender: string; age: number; city: string; bio: string
  photo_path: string | null; country: string | null
}
/** 033: colour, status, languages, prompts (profiles columns, or profile_style rows); 040 keeps the colour null. */
type StyleRow = { accent: string | null; status: string | null; languages: string[] | null; prompts: unknown; mbti: string | null }
type ProfileRow = Omit<CardRow, 'age'> & { birth_date: string; photos: string[] | null; handle: string; dm_from: string; hide_online: boolean } & StyleRow
type ExtrasRow = { id: string; photos: string[] | null }
/** inbox2 (033): inbox's lines plus the post a like or comment is about. */
type InboxRow = {
  id: number; kind: string; n: number; created_at: string; read_at: string | null; match_id: string | null; room_id: string | null
  room_title: string | null; room_open: boolean; pending: boolean; actor: CardRow
  post: { id: string; body: string | null; image_path: string | null } | null
  item?: string | null
}
type RecentRow = CardRow & { match_id: string; met_at: string; last_body: string | null }
type FriendRow = CardRow & { state: string; match_id: string | null }
type MatchRow = {
  id: string; user_a: string; user_b: string; ended_at: string | null; ended_by: string | null
  video_status: string; video_requested_by: string | null; call_video: boolean | null; read_a: number | null; read_b: number | null
}
type MessageRow = {
  id: number; match_id: string; sender: string; body: string | null
  media_path: string | null; media_kind: string | null; created_at: string; client_id: string | null
}
type PostRow = { id: string; user_id: string; body: string; image_path: string | null; created_at: string }
type VoiceListRow = {
  id: string; title: string; category: string; mine: boolean; host: CardRow; speakers: CardRow[]; listeners: number
  members: number; now_playing: string | null; max_members: number; gender_rule: string
  min_age: number; max_age: number; is_private: boolean; announcement: string | null; code: string | null; refusal: string | null
  lang: string | null; cover_path: string | null; tags: string[] | null
}
/** voice_state(): the room, the people present with their cards, and the server clock (ms). */
type VoiceStateRow = {
  id: string; title: string; category: string; mode: string; host: string; rev: number; now: number; locked: number[]
  music: { id: string; videoId: string; title: string; by: string; startedAt: number; pausedAt: number | null } | null
  queue: { id: string; videoId: string; title: string; by: string; votes: number; voted: boolean }[]
  members: VoiceMemberRow[]
  announcement: string | null; max: number; gender: string; minAge: number; maxAge: number; private: boolean; code: string | null
  password?: string | null
  /** 027: the room's language. */
  lang?: string | null
  /** The room's change number (020); events carry the next ones. */
  seq?: number
  /** 036: the page pinned for everyone (voice_share_link). */
  stage?: { url: string; title: string | null; by: string; byId: string; at: number } | null
  /** 068: chat-only (false) and the room's wall. */
  voice?: boolean
  theme?: string | null
  /** 072: the cover photo's file and the hashtags. */
  cover?: string | null
  tags?: string[]
}
/** `ha`, `j`: when the hand went up and when they came in (server ms), for the order voice_state uses. */
type VoiceMemberRow = CardRow & { role: string; seat: number | null; muted: boolean; hand: boolean; ha?: number | null; j?: number; lost?: boolean }
/** One change in a room, over Broadcast (020). `seq` null: sent without a number, the client re-checks. */
type VoiceChange = { seq?: number | null } & (
  | ({ t: 'm'; present: true } & VoiceMemberRow)
  | { t: 'm'; id: string; present: false }
  | ({ t: 'r'; ended: boolean } & Pick<VoiceStateRow, 'title' | 'category' | 'mode' | 'host' | 'locked' | 'music' | 'announcement'
      | 'max' | 'gender' | 'minAge' | 'maxAge' | 'private' | 'stage' | 'theme'> & { queue: Omit<VoiceStateRow['queue'][number], 'voted'>[] })
  | { t: 'v'; item: string; user: string; on: boolean; votes: number }
  /** 029: a poll changed (a vote, closed); `mine` is null. Numbered like the rest, nothing in the state. */
  | { t: 'p'; poll: unknown }
  /** 074: lines hidden, or the chat cleared (by whom). Numbered like the rest, nothing in the state. */
  | { t: 'h'; ids: number[] }
  | { t: 'x'; by: string }
)

/** voice_state's member order: the stage by seat, then raised hands (oldest first), then by arrival. */
const stageOrder = (a: VoiceMemberRow, b: VoiceMemberRow) =>
  (a.seat ?? 99) - (b.seat ?? 99) || (a.hand ? a.ha ?? 0 : Infinity) - (b.hand ? b.ha ?? 0 : Infinity) || (a.j ?? 0) - (b.j ?? 0)

/** Applies one change to a room's state (in place). `voted` is mine to keep; the code stays the host's. */
function applyChange(s: VoiceStateRow, c: VoiceChange, me: string) {
  if (c.t === 'm') {
    const members = s.members.filter((m) => m.id !== c.id)
    if (c.present) {
      const { t: _t, present: _p, seq: _s, ...m } = c
      members.push(m)
    }
    s.members = members.sort(stageOrder)
  } else if (c.t === 'r') {
    const voted = new Set(s.queue.filter((q) => q.voted).map((q) => q.id))
    Object.assign(s, {
      title: c.title, category: c.category, mode: c.mode, host: c.host, locked: c.locked, music: c.music,
      announcement: c.announcement, max: c.max, gender: c.gender, minAge: c.minAge, maxAge: c.maxAge, private: c.private,
      queue: c.queue.map((q) => ({ ...q, voted: voted.has(q.id) })),
      // 037 sends the pinned page with every room change; an event without it leaves it as it was.
      ...('stage' in c ? { stage: c.stage ?? null } : {}),
      ...('theme' in c ? { theme: c.theme ?? null } : {}),
    })
  } else if (c.t === 'v') {
    const q = s.queue.find((x) => x.id === c.item)
    if (q) {
      q.votes = c.votes
      if (c.user === me) q.voted = c.on
    }
  }
}
type VoiceMessageRow = {
  id: number; room_id: string; sender: string; kind: 'text' | 'system' | 'reaction' | 'icebreaker' | 'photo' | 'poll'; body: string | null
  media_path: string | null
  payload: Record<string, unknown> | null; sender_name: string; sender_gender: string | null; sender_age: number | null
  sender_photo: string | null; sender_country: string | null; created_at: string
}
/** What the voice methods need from an open subscription (subscribeVoice). */
type VoiceLive = {
  room: VoiceRoom | null
  refresh: () => void
  /** After my own action: its change arrives over realtime; fetch only while that connection is down. */
  acted: () => void
  /** What room changes don't carry (the language, the host's password): set here after my change so it stays. */
  keep: (p: Pick<VoiceStateRow, 'lang' | 'password'>) => void
  mic: (on: boolean) => Promise<void>
  camera: (on: boolean) => Promise<void>
  startAudio: () => Promise<void>
  /** 46: the room's voices on or off for me. */
  sound: (on: boolean) => Promise<void>
  volume: (userId: string, volume: number) => void
  joined: (state: VoiceStateRow, sent: number) => Promise<VoiceRoom>
  /** Tells the room I'm writing (public vtyping-<room> channel). */
  typing: (name: string) => void
  /** After I pinned or took down a page: read the room now (everyone else gets the room-change broadcast, 037). */
  staged: () => void
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

/** Known apps with text values only (a newer server may keep more). */
const contactsOnly = (c: unknown): Contacts =>
  Object.fromEntries(CONTACT_APPS.flatMap((k) => {
    const v = (c as Record<string, unknown> | null)?.[k]
    return typeof v === 'string' && v ? [[k, v]] : []
  }))

/** Database and function errors carry the rule name in their text ("exit_locked", ...). */
function codeOf(text: string): string {
  if (text.includes('row-level security')) return 'blocked'
  return CODES.find((c) => text.includes(c)) ?? 'network'
}

function fail(error: { message?: string; details?: string | null; hint?: string | null } | null): never {
  const text = [error?.message, error?.details, error?.hint].filter(Boolean).join(' ')
  throw new BackendError(codeOf(text), error?.details ?? undefined)
}

type GroupRow = { id: number; sender: string | null; kind: string; body: string; created_at: string; client_id?: string | null }
const GROUP_KINDS: GroupLineKind[] = ['text', 'created', 'added', 'left', 'renamed']
const groupKind = (k: string): GroupLineKind => (GROUP_KINDS.includes(k as GroupLineKind) ? (k as GroupLineKind) : 'text')
const toGroupLine = (r: GroupRow): GroupLine => ({ id: Number(r.id), senderId: r.sender, kind: groupKind(r.kind), body: r.body, at: time(r.created_at), clientId: r.client_id ?? null })

/** The chat's lines (reactions come over realtime only). */
const LINE_KINDS = ['text', 'system', 'photo', 'poll']

/** The server writes app languages in lower case (pt-br). */
const appLang = (l: string | null | undefined) => (l === 'pt-br' ? 'pt-BR' : l ?? null)

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

/** Shrinks a photo to `max` px JPEG (1280 by default, as the app) to save mobile data. */
async function compressImage(file: Blob, max = MAX_PHOTO_PX): Promise<Blob> {
  const bitmap = await createImageBitmap(file).catch(() => {
    throw new BackendError('bad_media')
  })
  const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new BackendError('bad_media'))), 'image/jpeg', 0.8),
  )
}

type TypingListener = { typing: (from: string) => void; presence: (connected: boolean, partnerHere: boolean) => void; profile: () => void }
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
  /**
   * Signed media URLs and when to stop reusing them (valid for an hour, reused for 50 minutes). Kept in the browser,
   * so a reload asks for the same URLs and the browser shows the photos from its cache at once.
   */
  private signedUrls = loadSigned()
  private saveTimer: ReturnType<typeof setTimeout> | null = null
  private readSent = new Map<string, number>()
  /**
   * One channel per match for typing signals and presence ("my app is in this chat"), shared by every view of it
   * (realtime-js reuses a channel per topic). The Android app uses the same topic and presence key (the user id).
   */
  private typing = new Map<string, TypingHub>()
  /** Newest message id from the last complete fetch of a chat or room; re-syncs ask only for newer ones. */
  private newest = new Map<string, number>()
  /**
   * Zulip's check (web/src/transmit.ts): a line the server stored should come back over Realtime within 5 s; if it
   * doesn't, the socket is quietly dead (subscribed, yet nothing arrives), so that chat polls every 10 s again.
   */
  private echoed = new Set<number>()
  private lostEcho = new Map<string, () => void>()
  /** A line's file once uploaded: sending the line again doesn't upload it again. */
  private uploaded = new WeakMap<File, string>()
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

  /** 033's parsing (posts, comments, styles) downloads when first needed (lib/supabaseFeed.ts). */
  private social_: Promise<typeof import('./supabaseFeed')> | null = null
  private social() {
    this.social_ ??= retryImport(() => import('./supabaseFeed'))
    this.social_.catch(() => (this.social_ = null))
    return this.social_
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
        .select('id, name, gender, birth_date, city, bio, photo_path, country, photos, accent, status, languages, prompts, mbti, handle, dm_from, hide_online')
        .eq('id', me).maybeSingle<ProfileRow>(),
      this.posts(me),
    ])
    if (error) fail(error)
    if (!data) return null
    const p = await this.withExtras(await this.toProfile({ ...data, age: ageOn(data.birth_date) }, posts), data)
    return { ...p, handle: data.handle, style: await this.toStyle(data), dmEveryone: data.dm_from === 'everyone', hideOnline: data.hide_online, photoPath: data.photo_path }
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
    track('signup_done')
    return me
  }

  async signOut() {
    const { error } = await this.client.auth.signOut()
    if (error) fail(error)
    this.userId = null
    this.forgetSigned()
  }

  async deleteAccount() {
    await this.invoke('delete-account')
    // The server already removed the user; only the local session is left.
    await this.client.auth.signOut({ scope: 'local' })
    this.userId = null
    this.forgetSigned()
  }

  // ---- matching & chat ----

  async findMatch(target: Target, lang?: string, countries: readonly string[] = []): Promise<Match | null> {
    let id: string | null
    try {
      // The server prefers someone using the app in the same language for the first few seconds.
      id = await this.rpc<string | null>('find_match4', { p_target: target, p_lang: lang ?? null, p_countries: countries.length ? countries : null })
    } catch (e) {
      // A chat we just left (or abandoned) still holds the exit lock for a moment: keep waiting.
      if (e instanceof BackendError && e.code === 'exit_locked') return null
      throw e
    }
    if (!id) return null
    try {
      // One request for the partner's card; their posts load only if the profile is opened (it fetches its own).
      const rows = await this.rpc<CardRow[]>('match_card2', { p_match: id })
      const c = rows?.[0]
      if (!c) throw new BackendError('not_found')
      const partner = await this.toProfile(c, [])
      this.abandoned.delete(id)
      track('match_found')
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
      .select('id, user_a, user_b, ended_at, ended_by, video_status, video_requested_by, call_video, read_a, read_b').eq('id', id).maybeSingle<MatchRow>()
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
    let lastVideo = ''
    let partnerRead = 0
    let leftSent = false
    let endedSent = false
    const emit = (e: MatchEvent) => { if (!closed) on(e) }
    const partnerLeft = () => {
      if (leftSent) return
      leftSent = true
      emit({ type: 'partner_left' })
    }
    const onMatch = (m: MatchRow | null) => {
      if (m === null || (m.ended_at && m.ended_by !== me)) partnerLeft()
      if (!m) return
      if (m.ended_at && !endedSent) {
        endedSent = true
        emit({ type: 'ended' })
      }
      const read = (m.user_a === me ? m.read_b : m.read_a) ?? 0
      if (read > partnerRead) {
        partnerRead = read
        emit({ type: 'read', upto: read })
      }
      const v = this.videoOf(m, me)
      const camera = m.call_video !== false
      if (`${v}:${camera}` !== lastVideo) {
        lastVideo = `${v}:${camera}`
        emit({ type: 'video', status: v, camera })
      }
    }

    // Catch up: the match row (left? video?) and messages newer than the last complete fetch. Runs when the
    // socket (re)connects, when the tab is shown again, and as a safety net every 10 s while the socket is down but
    // only every 60 s while it is up (one query per open chat every 10 s adds up at scale); paused while hidden.
    let healthy = false
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
          if (message.fromMe && this.echoed.size < 500) this.echoed.add(message.id)
          if (!message.fromMe) partnerAlive()
          emit({ type: 'message', message })
        }, () => undefined))
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'matches', filter: `id=eq.${matchId}` },
        (p) => onMatch(p.new as MatchRow))
      .subscribe((status) => {
        healthy = status === 'SUBSCRIBED'
        if (healthy) void resync()
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
      // 35: they turned VIP off or on; what I may see of them is asked again (the server decides).
      profile: async () => {
        const rows = await this.rpc<CardRow[]>('match_card2', { p_match: matchId }).catch(() => [])
        if (rows[0] && !closed) emit({ type: 'partner', partner: await this.toProfile(rows[0], []) })
      },
    }
    const hub = this.typingHub(matchId)
    hub.listeners.add(listener)
    if (hub.connected) onPresence(true, hub.partnerHere)

    let ticks = 0
    const timer = setInterval(() => (!healthy || ++ticks % 6 === 0) && void resync(), 10_000)
    this.lostEcho.set(matchId, () => {
      healthy = false
      void resync()
    })
    const stopWatching = onPageVisibility((visible) => visible && void resync())

    return () => {
      closed = true
      clearInterval(timer)
      this.lostEcho.delete(matchId)
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
      .on('broadcast', { event: 'profile' }, () => hub.listeners.forEach((l) => void l.profile()))
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

  async markRead(matchId: string, upto: number) {
    if (upto <= (this.readSent.get(matchId) ?? 0)) return
    this.readSent.set(matchId, upto)
    const { error } = await this.client.rpc('mark_read', { p_match: matchId, p_upto: upto })
    if (error && this.readSent.get(matchId) === upto) this.readSent.delete(matchId) // retried on the next call
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
      .select(MESSAGE_COLUMNS)
      .eq('match_id', matchId).order('id', { ascending: false }).limit(200) // the newest 200, shown oldest first
    if (error) fail(error)
    const rows = (data as MessageRow[]).reverse()
    this.noteNewest(matchId, rows)
    return this.toMessages(rows, me)
  }

  /** Messages after `afterId`, oldest first (a re-sync after a dropped connection). */
  private async newerMessages(matchId: string, afterId: number): Promise<Message[]> {
    const me = this.uid
    const { data, error } = await this.client.from('messages')
      .select(MESSAGE_COLUMNS)
      .eq('match_id', matchId).gt('id', afterId).order('id', { ascending: true }).limit(200)
    if (error) fail(error)
    const rows = data as MessageRow[]
    this.noteNewest(matchId, rows)
    return this.toMessages(rows, me)
  }

  /**
   * Only complete fetches move the mark: a message that arrived live (or one I sent) can have a newer id than
   * one the socket dropped, so it must not move it.
   */
  private noteNewest(key: string, rows: { id: number }[]) {
    const last = rows.at(-1)?.id
    if (last !== undefined && last > (this.newest.get(key) ?? -1)) this.newest.set(key, last)
  }

  async send(matchId: string, text: string | null, media: File | null, clientId: string): Promise<Message> {
    const kind = media ? (media.type.startsWith('video/') ? 'video' : 'image') : null
    let path = media ? this.uploaded.get(media) : null
    if (media && !path) this.uploaded.set(media, (path = await this.upload(media, 'chat')))
    let { data, error } = await this.client.from('messages')
      .insert({ match_id: matchId, body: text, media_path: path ?? null, media_kind: kind, client_id: clientId })
      .select(MESSAGE_COLUMNS).single<MessageRow>()
    // 23505: an earlier try was stored but its reply was lost; that stored line is the answer.
    if (error?.code === '23505') {
      ;({ data, error } = await this.client.from('messages').select(MESSAGE_COLUMNS)
        .eq('sender', this.uid).eq('client_id', clientId).single<MessageRow>())
    }
    if (error) {
      // 040: an ended random chat goes on only between friends; the policy refuses the rest (as a row-level violation).
      if (codeOf(error.message ?? '') === 'blocked' && (await this.matchRow(matchId).catch(() => null))?.ended_at) throw new BackendError('chat_closed')
      fail(error)
    }
    track('chat_message_sent')
    const id = data!.id
    setTimeout(() => this.echoed.delete(id) || this.lostEcho.get(matchId)?.(), 5_000)
    return this.toMessage(data!, this.uid)
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

  async requestCall(matchId: string, video: boolean) {
    await this.rpc('request_call', { p_match: matchId, p_video: video })
  }

  async answerVideo(matchId: string, accept: boolean) {
    await this.rpc('respond_video', { p_match: matchId, p_accept: accept })
  }

  async endVideo(matchId: string) {
    await this.rpc('end_video', { p_match: matchId })
  }

  async videoToken(matchId: string) {
    track('call_started')
    return this.invoke<{ url: string; token: string }>('video-token', { match_id: matchId })
  }

  // ---- profiles ----

  async recents(): Promise<RecentMatch[]> {
    const rows = await this.rpc<RecentRow[]>('recent_list')
    return Promise.all(rows.map(async (r) => ({
      matchId: r.match_id, partner: await this.toProfile(r, []), metAt: time(r.met_at), lastText: r.last_body,
    })))
  }

  async hideRecents(userIds: string[]) {
    await this.rpc('hide_recents', { p_users: userIds })
  }

  async profile(userId: string) {
    return this.card(userId)
  }

  private async card(userId: string): Promise<Profile | null> {
    const rows = await this.rpc<CardRow[]>('card', { uid: userId })
    const c = rows?.[0]
    if (!c) return null
    const [posts, extras, styles] = await Promise.all([
      this.posts(userId), this.extras([userId]).catch(() => []),
      this.rpc<(StyleRow & { id: string })[] | null>('profile_style2', { p_users: [userId] }).catch(() => null),
    ])
    const p = await this.toProfile(c, posts)
    const withX = extras[0] ? await this.withExtras(p, extras[0]) : p
    return styles?.[0] ? { ...withX, style: await this.toStyle(styles[0]) } : withX
  }

  private async toStyle(row: StyleRow) {
    return (await this.social()).parseStyle(row)
  }

  /** The extra photos of people I may see (024). */
  private async extras(ids: string[]): Promise<ExtrasRow[]> {
    return (await this.rpc<ExtrasRow[] | null>('profile_extras', { p_users: ids })) ?? []
  }

  private async withExtras(p: Profile, x: { photos: string[] | null }): Promise<Profile> {
    const paths = (x.photos ?? []).slice(0, MAX_PHOTOS)
    await this.signMany(paths)
    return {
      ...p,
      photos: paths.flatMap((id) => {
        const url = this.signedUrls.get(id)?.url
        return url ? [{ id, url }] : []
      }),
    }
  }

  /** A profile's posts (the grid): the newest 50 I may see, with their counts. */
  private async posts(userId: string): Promise<Post[]> {
    return this.postsOf(userId)
  }

  async addPost(text: string, image: File | null, visibility: PostVisibility = 'friends'): Promise<FeedPost> {
    const path = image ? await this.upload(image, 'posts') : null
    const { data, error } = await this.client.from('posts').insert({ body: text, image_path: path, visibility })
      .select('id, user_id, body, image_path, created_at').single<PostRow>()
    if (error) fail(error)
    const full = await this.post(data.id).catch(() => null)
    if (full) return full
    const me = await this.mine()
    return {
      id: data.id, text: data.body, imageUrl: path ? await this.signed(path) : null, createdAt: time(data.created_at), cursor: data.created_at,
      visibility, likes: 0, comments: 0, liked: false,
      author: { id: me.id, name: me.name, gender: me.gender, age: me.age, city: me.city, country: me.country ?? null, photoUrl: me.photoUrl ?? null, accent: me.style?.accent ?? null },
    }
  }

  async deletePost(postId: string) {
    const { error } = await this.client.from('posts').delete().eq('id', postId).eq('user_id', this.uid)
    if (error) fail(error)
  }

  // ---- posts, likes, comments (033) ----

  /** Signs every photo in the rows in one request, then reads them. */
  private async toPosts(rows: unknown): Promise<FeedPost[]> {
    const list = Array.isArray(rows) ? rows : []
    const { mediaPaths, parsePost } = await this.social()
    await this.signMany(mediaPaths(list))
    return list.flatMap((r) => parsePost(r, this.signedNow) ?? [])
  }

  private signedNow = (path: string | null) => (path ? this.signedUrls.get(path)?.url ?? null : null)

  async postsOf(userId: string, before?: string): Promise<FeedPost[]> {
    return this.toPosts(await this.rpc('posts_of', { p_user: userId, p_before: before ?? null, p_limit: 50 }))
  }

  async partyState(groupId: string, ready: boolean): Promise<PartyState> {
    const s = await this.rpc<{ ready: string[]; searching: boolean; matched: string | null }>('party_state', { p_group: groupId, p_ready: ready })
    return { ready: s.ready ?? [], searching: !!s.searching, matched: s.matched ?? null }
  }

  async partySearch(groupId: string, everywhere: boolean) {
    return this.rpc<string | null>('party_search', { p_group: groupId, p_global: everywhere })
  }

  async partyCancel(groupId: string) {
    await this.rpc('party_cancel', { p_group: groupId })
  }

  async lookingFor(country?: string | null): Promise<LookingPost[]> {
    type Row = { id: number; body: string; created_at: string; expires_at: string; mine: boolean; author_id: string } & Omit<CardRow, 'id' | 'city' | 'bio'>
    const rows = (await this.rpc<Row[] | null>('lfg_list', { p_country: country || null })) ?? []
    await this.signMany(rows.map((r) => r.photo_path))
    return rows.map((r) => ({
      id: r.id, text: r.body, at: time(r.created_at), expiresAt: time(r.expires_at), mine: r.mine,
      author: this.cardProfile({ ...r, id: r.author_id, city: '', bio: '' }),
    }))
  }

  async postLooking(text: string) {
    await this.rpc('lfg_create', { p_body: text })
  }

  async hideLooking(id: number) {
    await this.rpc('lfg_hide', { p_id: id })
  }

  async replyLooking(id: number) {
    return this.rpc<string>('lfg_reply', { p_id: id })
  }

  async postsByTag(tag: string, before?: string): Promise<FeedPost[]> {
    return this.toPosts(await this.rpc('posts_by_tag', { p_tag: tag, p_before: before ?? null }))
  }

  async post(postId: string): Promise<FeedPost | null> {
    const row = await this.rpc<unknown>('post_get', { p_post: postId })
    return row ? (await this.toPosts([row]))[0] ?? null : null
  }

  async likePost(postId: string, on: boolean) {
    return this.rpc<{ likes: number; liked: boolean }>('post_like', { p_post: postId, p_on: on })
  }

  async postLikers(postId: string): Promise<PersonRow[]> {
    return this.personRows(await this.rpc<PersonRowRaw[] | null>('post_likers', { p_post: postId }))
  }

  async comments(postId: string, after?: number): Promise<PostComment[]> {
    const { COMMENTS_PAGE, mediaPaths, parseComment } = await this.social()
    const rows = await this.rpc<unknown>('comments_list', { p_post: postId, p_after: after ?? null, p_limit: COMMENTS_PAGE })
    const list = Array.isArray(rows) ? rows : []
    await this.signMany(mediaPaths(list))
    return list.flatMap((r) => parseComment(r, this.signedNow) ?? [])
  }

  async addComment(postId: string, text: string, replyTo?: number | null): Promise<PostComment> {
    const [row, { mediaPaths, parseComment }] = await Promise.all([
      this.rpc<unknown>('comment_add', { p_post: postId, p_body: text, p_reply_to: replyTo ?? null }), this.social(),
    ])
    await this.signMany(mediaPaths([row]))
    const c = parseComment(row, this.signedNow)
    if (!c) throw new BackendError('network')
    return c
  }

  async removeComment(commentId: number) {
    await this.rpc('comment_remove', { p_id: commentId })
  }

  async reportPost(postId: string, reason: ReportReason) {
    await this.rpc('report_post', { p_post: postId, p_reason: reason })
  }

  async reportComment(commentId: number, reason: ReportReason) {
    await this.rpc('report_comment', { p_id: commentId, p_reason: reason })
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
    const list = await Promise.all(rows.map(async (f) => ({
      profile: await this.toProfile(f, []),
      state: (f.state === 'friends' ? 'friends' : f.state === 'requested' ? 'requested' : 'incoming') as FriendState,
      threadId: f.match_id,
    })))
    this.friendCards = new Map(list.filter((f) => f.state === 'friends').map((f) => [f.profile.id, f.profile]))
    return list
  }

  async friendActivity(): Promise<FriendActivity[]> {
    const rows = await this.rpc<{ id: string; presence: string; status: string | null; status_at: string | null }[]>('friend_activity')
    const kinds: Presence[] = ['offline', 'online', 'matching', 'chat', 'room']
    return rows.map((r) => ({
      id: r.id, presence: kinds.includes(r.presence as Presence) ? (r.presence as Presence) : 'online',
      status: r.status, statusAt: r.status_at ? time(r.status_at) : null,
    }))
  }

  async giftCheck(friendId: string, itemId: string) {
    const r = await this.rpc<string>('gift_check', { p_to: friendId, p_item: itemId })
    return (['ok', 'owned', 'not_friends'].includes(r) ? r : 'bad_item') as GiftCheck
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

  async searchHandles(q: string) {
    return this.personRows(await this.rpc<PersonRowRaw[]>('search_handles', { p_q: q }))
  }

  async blocks() {
    return this.personRows(await this.rpc<PersonRowRaw[]>('my_blocks'))
  }

  async unblock(userId: string) {
    await this.rpc('unblock_user', { p_user: userId })
  }

  // ---- messenger contacts (044_friend_contacts.sql) ----

  async setContacts(contacts: Contacts) {
    return contactsOnly(await this.rpc('set_contacts', { p_contacts: contacts }))
  }

  async myContacts() {
    const r = await this.rpc<{ contacts: unknown; shared_with: string[] | null }>('my_contacts')
    return { contacts: contactsOnly(r.contacts), sharedWith: r.shared_with ?? [] }
  }

  async shareContacts(userId: string, on: boolean) {
    await this.rpc('share_contacts', { p_user: userId, p_on: on })
  }

  async contactsOf(userId: string) {
    const r = await this.rpc<{ contacts: unknown; mine_shared: boolean } | null>('contacts_of', { p_user: userId })
    return r && { contacts: r.contacts ? contactsOnly(r.contacts) : null, mineShared: r.mine_shared }
  }

  async explorePeople(kind: ExploreKind, country?: string | null): Promise<ExplorePerson[]> {
    const rows = (await this.rpc<(PersonRowRaw & { age: number; country: string | null; n: number | null })[] | null>('explore_people', { p_kind: kind, p_country: country ?? null })) ?? []
    const people = await this.personRows(rows)
    return people.map((p, i) => ({ ...p, age: rows[i].age, country: rows[i].country, count: rows[i].n }))
  }

  async exploreFeed(): Promise<FeedItem[]> {
    type Row = { kind: 'post' | 'highlight'; item: string; user_id: string; handle: string; name: string; photo_path: string | null; body: string | null; photos: string[]; at: string; post: unknown }
    const [rows0, { mediaPaths }] = await Promise.all([this.rpc<Row[] | null>('explore_feed2'), this.social()])
    const rows = rows0 ?? []
    // Every file of the feed (photos, faces, posts' media) signed in one request: one round trip instead of three.
    await this.signMany([...rows.flatMap((r) => r.photos), ...rows.map((r) => r.photo_path), ...mediaPaths(rows.flatMap((r) => r.post ?? []))])
    const [users, posts] = await Promise.all([
      this.personRows(rows.map((r) => ({ id: r.user_id, handle: r.handle, name: r.name, photo_path: r.photo_path }))),
      this.toPosts(rows.flatMap((r) => r.post ?? [])),
    ])
    const byId = new Map(posts.map((p) => [p.id, p]))
    return rows.map((r, i) => ({
      kind: r.kind, id: r.item, user: users[i], text: r.body ?? '', at: r.at,
      photos: r.photos.flatMap((x) => this.signedUrls.get(x)?.url ?? []),
      post: r.kind === 'post' ? byId.get(r.item) ?? null : null,
    }))
  }

  async profileSocial(userId: string): Promise<ProfileSocial> {
    const r = (await this.rpc<{ likes: number; friends: number; liked: boolean; can_message: boolean }[] | null>('profile_social', { p_user: userId }))?.[0]
    return { likes: r?.likes ?? 0, friends: r?.friends ?? 0, liked: !!r?.liked, canMessage: !!r?.can_message }
  }

  async likeProfile(userId: string, on: boolean) {
    await this.rpc('like_profile', { p_user: userId, p_on: on })
  }

  async setDmEveryone(on: boolean) {
    await this.rpc('set_dm_from', { p_value: on ? 'everyone' : 'friends' })
    return this.mine()
  }

  async setHideOnline(on: boolean) {
    await this.rpc('set_hide_online', { p_on: on })
    return this.mine()
  }

  private async personRows(rows: PersonRowRaw[] | null): Promise<PersonRow[]> {
    await this.signMany((rows ?? []).map((r) => r.photo_path))
    return (rows ?? []).map((r) => ({
      id: r.id, handle: r.handle, name: r.name, photoUrl: r.photo_path ? this.signedUrls.get(r.photo_path)?.url ?? null : null,
    }))
  }

  // ---- rooms (017_voice_rooms.sql, 019_unified_rooms.sql; audio through LiveKit, lib/voice-audio.ts) ----

  async voiceRooms(query: RoomQuery = {}): Promise<VoiceRoomSummary[]> {
    const rows = await this.rpc<VoiceListRow[]>('voice_list5', {
      p_query: query.text?.trim() || null, p_lang: query.lang ?? null, p_countries: query.countries?.length ? query.countries : null, p_locked: query.locked ?? true,
      p_sort: query.sort ?? 'members',
    })
    await this.signMany(rows.flatMap((r) => [r.host?.photo_path, r.cover_path, ...r.speakers.map((c) => c.photo_path)]))
    return rows.filter((r) => r.host).map((r) => ({
      id: r.id, title: r.title, host: this.cardProfile(r.host),
      speakers: r.speakers.map((c) => this.cardProfile(c)), listeners: r.listeners, members: r.members,
      nowPlaying: r.now_playing || null,
      announcement: r.announcement, mine: r.mine, code: r.code, refusal: (r.refusal as RoomRefusal | null) ?? null,
      maxMembers: r.max_members, genderRule: r.gender_rule as Target, minAge: r.min_age, maxAge: r.max_age, isPrivate: r.is_private,
      lang: appLang(r.lang), coverUrl: this.signedNow(r.cover_path), tags: r.tags ?? [],
    }))
  }

  async createVoiceRoom(r: NewVoiceRoom) {
    const room = await this.rpc<{ id: string; code: string }>('create_voice_room4', {
      p_title: r.title, p_category: 'talk', p_max: r.maxMembers, p_gender: r.genderRule, p_min_age: r.minAge,
      p_max_age: r.maxAge, p_private: r.isPrivate, p_announcement: r.announcement, p_lang: r.lang || null, p_voice: r.voice,
    })
    track('room_created')
    if (r.cover || r.tags?.length) await this.setRoomExtras(room.id, { cover: r.cover, tags: r.tags ?? [] }).catch(() => undefined)
    return room
  }

  async setRoomExtras(roomId: string, extras: { cover?: File | null; tags: string[] }) {
    const live = this.voice.get(roomId)
    const cover = extras.cover === undefined ? live?.room?.coverPath ?? null : extras.cover && (await this.upload(extras.cover, 'cover'))
    await this.rpc('voice_set_extras', { p_room: roomId, p_cover: cover, p_tags: extras.tags })
    // The cover and tags are not part of the room's change events: the open room asks again.
    live?.refresh()
  }

  async joinVoiceCode(code: string): Promise<string> {
    // A wrong code comes back as null (so the miss is counted: 10 in 10 minutes and code entry pauses).
    const id = await this.rpc<string | null>('join_voice_code', { p_code: code })
    if (!id) throw new BackendError('not_found')
    return id
  }

  async updateVoiceRoom(roomId: string, patch: { title: string; lang?: string }) {
    // No notice here (null keeps it, 064): it has its own call.
    await this.rpc('voice_settings2', { p_room: roomId, p_title: patch.title, p_announcement: null, p_lang: patch.lang ?? null })
    // The language is not part of the room's change events: keep the open room's copy current.
    const live = this.voice.get(roomId)
    if (patch.lang) live?.keep({ lang: patch.lang })
    live?.acted()
  }

  async unlockRoom(roomId: string, password: string) {
    return (await this.rpc<boolean>('voice_unlock', { p_room: roomId, p_password: password })) === true
  }

  async setRoomPassword(roomId: string, password: string | null) {
    await this.rpc('voice_set_password', { p_room: roomId, p_password: password })
    this.voice.get(roomId)?.keep({ password })
  }

  async setRoomNotice(roomId: string, text: string) {
    await this.rpc('voice_notice', { p_room: roomId, p_text: text })
    this.voice.get(roomId)?.acted()
  }

  async setRoomTheme(roomId: string, itemId: string | null) {
    await this.rpc('voice_set_theme', { p_room: roomId, p_item: itemId })
    this.voice.get(roomId)?.acted()
  }

  // ---- 48 group chats (069) ----

  async groups(): Promise<GroupChat[]> {
    type Row = { id: string; title: string; members: CardRow[] | null; last_kind: string | null; last_body: string | null; last_sender: string | null; last_at: string; unread: number }
    const rows = (await this.rpc<Row[] | null>('group_list')) ?? []
    await this.signMany(rows.flatMap((r) => (r.members ?? []).map((m) => m.photo_path)))
    return rows.map((r) => ({
      id: r.id, title: r.title, members: (r.members ?? []).map((m) => this.cardProfile(m)), lastAt: time(r.last_at), unread: r.unread,
      last: r.last_kind ? { kind: groupKind(r.last_kind), body: r.last_body ?? '', senderId: r.last_sender } : null,
    }))
  }

  async createGroup(title: string, memberIds: string[]) {
    return (await this.rpc<string>('group_create', { p_title: title, p_members: memberIds }))!
  }

  async groupHistory(groupId: string, before?: number) {
    const rows = (await this.rpc<GroupRow[] | null>('group_history', { p_group: groupId, p_before: before ?? null })) ?? []
    return rows.map(toGroupLine).reverse()
  }

  async sendGroup(groupId: string, text: string, clientId: string): Promise<GroupLine> {
    const id = await this.rpc<number>('group_send', { p_group: groupId, p_body: text, p_client: clientId })
    return { id: id!, senderId: this.uid, kind: 'text', body: text.trim(), at: Date.now(), clientId }
  }

  async readGroup(groupId: string, upto: number) {
    await this.rpc('group_read', { p_group: groupId, p_upto: upto })
  }

  async addToGroup(groupId: string, memberIds: string[]) {
    await this.rpc('group_add', { p_group: groupId, p_members: memberIds })
  }

  async renameGroup(groupId: string, title: string) {
    await this.rpc('group_rename', { p_group: groupId, p_title: title })
  }

  async leaveGroup(groupId: string) {
    await this.rpc('group_leave', { p_group: groupId })
  }

  watchGroups(groupIds: string[], onLine: (groupId: string, line: GroupLine) => void) {
    if (!groupIds.length) return () => undefined
    // One channel for all my groups (Postgres Changes, members-only by RLS); at most 100 ids in an `in` filter.
    const channel = this.client.channel(`groups-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'group_messages', filter: `group_id=in.(${groupIds.slice(0, 100).join(',')})` },
        (p) => {
          const r = p.new as GroupRow & { group_id: string }
          onLine(r.group_id, toGroupLine(r))
        })
      .subscribe()
    return () => void this.client.removeChannel(channel)
  }

  async inviteToRoom(roomId: string, userId: string) {
    await this.rpc('invite_to_room', { p_room: roomId, p_user: userId })
  }

  async joinInvite(inboxId: number) {
    return this.rpc<string>('join_invite', { p_id: inboxId })
  }

  async onlineCount() {
    return (await this.rpc<number | null>('online_count')) ?? 0
  }

  // ---- inbox and badges (023) ----

  async inbox(before?: number): Promise<InboxItem[]> {
    const [rows0, { inboxKind }] = await Promise.all([this.rpc<InboxRow[] | null>('inbox2', { p_before: before ?? null, p_limit: 30 }), this.social()])
    const rows = rows0 ?? []
    await this.signMany(rows.flatMap((r) => [r.actor?.photo_path, r.post?.image_path]))
    // A kind this app doesn't know yet (a newer server) is left out.
    return rows.flatMap((r) => {
      const kind = inboxKind(r.kind)
      if (!kind || !r.actor) return []
      const post = r.post ? { id: r.post.id, text: r.post.body ?? '', imageUrl: this.signedNow(r.post.image_path) } : null
      if ((kind === 'post_like' || kind === 'post_comment') && !post) return []
      return [{
        id: r.id, kind, actor: this.cardProfile(r.actor), at: time(r.created_at), read: r.read_at !== null,
        count: r.n, matchId: r.match_id, roomId: r.room_id, roomTitle: r.room_title, roomOpen: r.room_open, pending: r.pending, post,
        item: r.item ?? null,
      }]
    })
  }

  async markInboxRead(ids?: number[]) {
    await this.rpc('inbox_read', { p_ids: ids ?? null })
  }

  async badges(): Promise<Badges> {
    const b = await this.rpc<{ inbox: number; friends: number; chats: Record<string, number> }>('my_badges')
    return { inbox: b.inbox ?? 0, friends: b.friends ?? 0, chats: b.chats ?? {} }
  }

  async incomingCall(): Promise<IncomingCall | null> {
    const item = (await this.inbox()).find((i) => i.kind === 'call' && !i.read && i.matchId)
    const m = item && (await this.matchRow(item.matchId!))
    return m && this.videoOf(m, this.uid) === 'requested_by_partner' ? { matchId: m.id, caller: item.actor, video: m.call_video !== false } : null
  }

  inboxSignals(on: () => void): () => void {
    const channel = this.client.channel(`inbox-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'notifications', filter: `user_id=eq.${this.uid}` }, () => on())
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') on() // anything that arrived while the connection was down
      })
    return () => void this.client.removeChannel(channel)
  }

  presencePing(online: boolean) {
    if (online) return void this.rpc('presence_ping', { p_online: true }).catch(() => undefined)
    // Closing the app: a request that outlives the page (as leave_voice).
    if (this.accessToken)
      void fetch(`${this.url}/rest/v1/rpc/presence_ping`, {
        method: 'POST',
        keepalive: true,
        headers: { apikey: this.anonKey, Authorization: `Bearer ${this.accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ p_online: false }),
      }).catch(() => undefined)
  }

  friendSignals(on: () => void): () => void {
    // Private (RLS on realtime.messages): only I can listen on my topic.
    const channel = this.client.channel(`friends:${this.uid}`, { config: { private: true } })
      .on('broadcast', { event: 'p' }, () => on())
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') on() // anything missed while the connection was down
      })
    return () => void this.client.removeChannel(channel)
  }

  // ---- profile extras (024) ----

  async addProfilePhoto(photo: File) {
    const now = await this.mine()
    const ids = (now.photos ?? []).map((p) => p.id)
    if (ids.length >= MAX_PHOTOS) throw new BackendError('not_allowed')
    const path = await this.upload(photo, 'avatar')
    await this.rpc('set_photos', { p_paths: [...ids, path] })
    return this.mine()
  }

  async removeProfilePhoto(id: string) {
    const now = await this.mine()
    await this.rpc('set_photos', { p_paths: (now.photos ?? []).map((p) => p.id).filter((x) => x !== id) })
    return this.mine()
  }

  async highlights(userId: string): Promise<Highlight[]> {
    const rows = (await this.rpc<{ id: number; title: string; photos: string[] }[] | null>('highlights_of', { p_user: userId })) ?? []
    await this.signMany(rows.flatMap((r) => r.photos))
    return rows.map((r) => ({
      id: String(r.id), title: r.title,
      photos: r.photos.flatMap((id) => {
        const url = this.signedUrls.get(id)?.url
        return url ? [{ id, url }] : []
      }),
    }))
  }

  async saveHighlight(h: { id: string | null; title: string; keep: string[]; add: File[] }) {
    const added = await Promise.all(h.add.map((f) => this.upload(f, 'avatar')))
    await this.rpc('save_highlight', { p_id: h.id === null ? null : Number(h.id), p_title: h.title, p_photos: [...h.keep, ...added] })
  }

  async deleteHighlight(id: string) {
    await this.rpc('delete_highlight', { p_id: Number(id) })
  }

  async setProfileStyle(style: ProfileStyleInput) {
    await this.rpc('set_profile_style2', { p_status: style.status, p_languages: style.languages, p_prompts: style.prompts, p_mbti: style.mbti })
    return this.mine()
  }

  private async mine(): Promise<Profile> {
    const p = await this.myProfile()
    if (!p) throw new BackendError('network')
    return p
  }

  async joinVoice(roomId: string): Promise<VoiceRoom> {
    const sent = Date.now()
    const state = await this.rpc<VoiceStateRow>('join_voice', { p_room: roomId })
    track('room_joined')
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
      .in('kind', LINE_KINDS).order('id', { ascending: false }).limit(100) // the newest 100, shown oldest first
    if (error) fail(error)
    const rows = (data as VoiceMessageRow[]).reverse()
    this.noteNewest(`voice:${roomId}`, rows)
    return this.toVoiceLines(rows)
  }

  async sendVoice(roomId: string, text: string, replyTo?: number | null): Promise<VoiceLine> {
    // voice_send2 (029) answers a line; voice_send stays for older apps.
    const row = await this.rpc<VoiceMessageRow>('voice_send2', { p_room: roomId, p_text: text, p_reply_to: replyTo ?? null })
    return (await this.toVoiceLines([row]))[0]
  }

  async sendVoicePhoto(roomId: string, photo: File): Promise<VoiceLine> {
    if (!photo.type.startsWith('image/')) throw new BackendError('bad_media')
    const path = await this.upload(photo, 'rooms')
    const row = await this.rpc<VoiceMessageRow>('voice_photo', { p_room: roomId, p_path: path })
    return (await this.toVoiceLines([row]))[0]
  }

  // ---- polls (029) ----

  async voicePollCreate(roomId: string, question: string, options: string[]): Promise<VoiceLine> {
    const row = await this.rpc<VoiceMessageRow>('voice_poll_create', { p_room: roomId, p_question: question, p_options: options })
    const [line] = await this.toVoiceLines([row])
    if (!line) throw new BackendError('network')
    return line
  }

  async voicePollVote(pollId: number, choice: number): Promise<VoicePoll> {
    return this.toPoll(await this.rpc<unknown>('voice_poll_vote', { p_poll: pollId, p_choice: choice }))
  }

  async voicePollClose(pollId: number): Promise<VoicePoll> {
    return this.toPoll(await this.rpc<unknown>('voice_poll_close', { p_poll: pollId }))
  }

  async voicePolls(ids: number[]): Promise<VoicePoll[]> {
    if (!ids.length) return []
    const list = await this.rpc<unknown[]>('voice_polls_get', { p_ids: ids.slice(-POLLS_PER_READ) })
    return (Array.isArray(list) ? list : []).flatMap((j) => parsePoll(j) ?? [])
  }

  private toPoll(j: unknown): VoicePoll {
    const p = parsePoll(j)
    if (!p) throw new BackendError('network')
    return p
  }

  async reportVoiceRoom(roomId: string, reason: ReportReason) {
    // A room is reported through its host (the report keeps what the host wrote in it).
    const hostId = this.voice.get(roomId)?.room?.hostId
      ?? (await this.rpc<VoiceStateRow>('voice_state', { p_room: roomId })).host
    if (hostId === this.uid) throw new BackendError('not_allowed')
    await this.report(hostId, reason, null)
  }

  // ---- identity and manners (035) ----

  async myTrust(): Promise<MyTrust> {
    const t = parseMyTrust(await this.rpc<unknown>('my_trust'), time)
    if (!t) throw new BackendError('network')
    return t
  }

  async trust(userIds: string[]): Promise<Trust[]> {
    const ids = [...new Set(userIds)].slice(0, 100)
    if (!ids.length) return []
    return parseTrustList(await this.rpc<unknown>('trust', { p_users: ids }))
  }

  async kycStart(returnUrl: string): Promise<string> {
    const r = await this.invoke<{ url?: string }>('kyc', { action: 'start', returnUrl })
    if (!r?.url) throw new BackendError('kyc_failed')
    return r.url
  }

  async kycCheck(): Promise<KycResult> {
    const r = await this.invoke<{ result?: string }>('kyc', { action: 'check' })
    return parseKycResult(r?.result)
  }

  async rateChat(matchId: string, good: boolean, tags: string[]) {
    await this.rpc('rate_chat', { p_match: matchId, p_good: good, p_tags: tags })
  }

  // ---- IDs and looks (040) ----

  // The server trims and composes (NFC) the ID itself; lib/handle.ts handleSpelling does the same on screen.
  async handleCheck(handle: string): Promise<HandleCheck> {
    const r = await this.rpc<string>('handle_check', { p_handle: handle })
    return r === 'ok' || r === 'mine' || r === 'reserved' || r === 'taken' ? r : 'invalid'
  }

  async setHandle(handle: string) {
    return this.rpc<string>('set_handle', { p_handle: handle })
  }

  async shop(): Promise<Shop> {
    const s = parseShop(await this.rpc<unknown>('shop'), time)
    if (!s) throw new BackendError('network')
    return { ...s, look: await this.signLook(s.look) }
  }

  async equipLook(wear: LookWear): Promise<Look> {
    const look = parseLook(await this.rpc<unknown>('equip_look', { p_badge: wear.badge, p_color: wear.color, p_font: wear.font, p_frame: wear.frame }))
    if (!look) throw new BackendError('network')
    return this.signLook(look)
  }

  async setWall(itemId: string | null) {
    await this.rpc('set_wall', { p_item: itemId })
  }

  async setAnon(on: boolean) {
    await this.rpc('set_anon', { p_on: on })
    // The people of my open chats read my card again (rooms hear it from the server, 066).
    for (const hub of this.typing.values()) void hub.channel.send({ type: 'broadcast', event: 'profile', payload: {} }).catch(() => undefined)
  }

  async genderPass() {
    const row = (await this.rpc<{ premium_until: string | null; gender_until: string | null }[]>('my_entitlements'))?.[0]
    const until = Math.max(...[row?.premium_until, row?.gender_until].map((t) => (t ? time(t) : 0)))
    return until > Date.now() ? until : null
  }

  async looks(userIds: string[]): Promise<Look[]> {
    const ids = [...new Set(userIds)].slice(0, 100)
    if (!ids.length) return []
    return Promise.all(parseLooks(await this.rpc<unknown>('looks', { p_users: ids })).map((l) => this.signLook(l)))
  }

  /** A look's photo gradient as photos (the server gives file paths). */
  private async signLook<T extends Look | null>(l: T): Promise<T> {
    if (!l?.cycle) return l
    await this.signMany(l.cycle)
    const cycle = l.cycle.flatMap((p) => this.signedUrls.get(p)?.url ?? [])
    return { ...l, cycle: cycle.length >= 2 ? cycle : undefined }
  }

  async setWorn(kind: 'bubble', itemId: string | null) {
    await this.rpc('set_worn', { p_kind: kind, p_item: itemId })
  }

  async setCycle(paths: string[] | null) {
    await this.rpc('set_cycle', { p_paths: paths })
  }

  async voiceStartAudio(roomId: string) {
    await this.voice.get(roomId)?.startAudio()
  }

  async voiceSound(roomId: string, on: boolean) {
    await this.voice.get(roomId)?.sound(on)
  }

  async hideVoiceLine(roomId: string, lineId: number) {
    await this.rpc('voice_hide_line', { p_room: roomId, p_id: lineId })
  }

  async clearVoiceChat(roomId: string) {
    await this.rpc('voice_clear_chat', { p_room: roomId })
  }

  voiceVolume(roomId: string, userId: string, volume: number) {
    saveVolume(userId, volume)
    this.voice.get(roomId)?.volume(userId, volume)
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
      case 'camera':
        if (!live) throw new BackendError('network')
        await live.camera(a.on)
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
      case 'makeHost':
        await this.rpc('voice_make_host', { ...room, p_user: a.userId })
        this.voiceSync(roomId, a.userId) // both moved seats: their LiveKit rights follow
        this.voiceSync(roomId, this.uid)
        break
      case 'kick':
        await this.rpc('voice_kick', { ...room, p_user: a.userId })
        this.voiceSync(roomId, a.userId)
        break
      case 'addTrack':
        // The host's track plays at once when nothing does; everything else joins the requests.
        await this.rpc('voice_queue_add', { ...room, p_video: a.videoId, p_title: (a.title ?? '').trim().slice(0, 100) })
        break
      case 'vote':
        await this.rpc('voice_vote', { ...room, p_item: a.itemId, p_on: a.on })
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
      case 'stop':
        await this.rpc('voice_stop', room)
        break
      case 'seek':
        await this.rpc('voice_seek', { ...room, p_ms: Math.max(0, Math.round(a.ms)) })
        break
      case 'shareLink': {
        if (a.url !== null && !shareable(a.url)) throw new BackendError('bad_request')
        const title = a.title?.trim().slice(0, 200) || null
        await this.rpc('voice_share_link', { ...room, p_url: a.url, p_title: a.url === null ? null : title })
        live?.staged()
        return
      }
    }
    live?.acted()
  }

  voiceTyping(roomId: string, name: string) {
    this.voice.get(roomId)?.typing(name)
  }

  async linkMeta(url: string): Promise<LinkMeta> {
    const target = safeUrl(url)
    if (!target) throw new BackendError('bad_request')
    const j = await this.invoke<Record<string, unknown>>('link-meta', { url: target })
    const text = (v: unknown, n: number) => (typeof v === 'string' && v.trim() ? v.trim().slice(0, n) : null)
    const image = typeof j?.image === 'string' && j.image.startsWith('https://') ? safeUrl(j.image) : null
    return {
      // The page the link ended up at (after redirects), if it is still a web address; else the one asked for.
      url: (typeof j?.url === 'string' && safeUrl(j.url)) || target,
      title: text(j?.title, 200),
      description: text(j?.description, 300),
      image,
      site: text(j?.site, 80),
      frameable: j?.frameable === true,
    }
  }

  /** Applies the room's state to LiveKit (edge function voice-sync). Fire and forget: the host's client re-checks every minute. */
  private voiceSync(roomId: string, userId?: string, mute?: boolean) {
    void this.invoke('voice-sync', { room_id: roomId, ...(userId ? { user_id: userId } : {}), ...(mute ? { mute: true } : {}) })
      .catch(() => undefined)
  }

  /**
   * Follows a voice room. Once I'm in: one full state, then every change itself over a private Broadcast topic
   * (020: a member, the room's settings and music, a vote, each numbered), applied here; the full state is fetched
   * again only on a gap in the numbers, a reconnect, or when the heartbeat (every 3 s, also in the background:
   * people keep listening, as on X Spaces) says the room is further on. Chat and reactions come over realtime too.
   * A re-sync runs every 45 s while the realtime connection is up, every 10 s while it isn't, and when the tab is
   * shown. A listener whose tab stays hidden for 30 minutes leaves. Failing beats or a dropped realtime connection
   * show as 'connection'; back after more than 13 s, the server has taken me out and the room closes ('dropped'). LiveKit audio while someone is on the stage.
   */
  subscribeVoice(roomId: string, on: (e: VoiceEvent) => void): () => void {
    const me = this.uid
    let closed = false
    let joined = false
    let over = false
    let beats = 0
    const emit = (e: VoiceEvent) => { if (!closed) on(e) }
    const finish = (e: VoiceEvent) => {
      if (over) return
      over = true
      stopAudio()
      emit(e)
    }
    // Anything the server says about my being here (the heartbeat brings me back after a gap, and the room may
    // have filled, banned me, ...) ends the session with its reason, instead of a room nobody else sees me in.
    const failed = (err: unknown) => {
      const code = err instanceof BackendError ? err.code : ''
      if (code === 'room_closed' || code === 'not_found') finish({ type: 'ended' })
      // 031: I'm in a random chat now (it took me out of the room): the room just closes here, without a word.
      else if (code === 'in_random_chat') finish({ type: 'left' })
      else if (code === 'voice_removed') finish({ type: 'removed' })
      else if (['room_full', 'room_gender', 'room_age', 'not_allowed', 'banned', 'not_signed_in'].includes(code)) {
        finish({ type: 'refused', code })
      }
    }

    // ---- state: the last full state with every change since applied to it ----
    let base: VoiceStateRow | null = null
    let seq = -1
    let pending: VoiceChange[] = [] // arrived while a full state was on its way
    let mine: { seat: number | null; muted: boolean } | null = null
    let stage = false
    let shown = 0
    const show = async (state: VoiceStateRow) => {
      const n = ++shown
      const room = await this.toVoiceRoom(state)
      if (closed || over || n !== shown) return room
      live.room = room
      const m = room.members.find((x) => x.profile.id === me)
      const before = mine
      mine = m ? { seat: m.seat, muted: m.muted } : null
      if (mine && before) {
        // Brought up to a seat: ask LiveKit for the right to talk. Sent back to the audience, or muted by a host:
        // the microphone goes off here too.
        if (before.seat === null && mine.seat !== null) this.voiceSync(roomId, me)
        if ((mine.seat === null && before.seat !== null) || (mine.muted && !before.muted)) void audio?.setMic(false).catch(() => undefined)
        if (mine.seat === null && before.seat !== null) void audio?.setCamera(false).catch(() => undefined)
      }
      // Audio only while someone is on the stage: a room nobody talks in is a chat room and costs no LiveKit minutes.
      stage = room.members.some((x) => x.seat !== null)
      if (mine && stage && !audio && !audioCtl) void connectAudio()
      else if (!stage && (audio || audioCtl)) stopAudio(true)
      emit({ type: 'room', room })
      return room
    }
    let showTimer: ReturnType<typeof setTimeout> | undefined
    /** Changes come in bursts (someone joins: a card and a line): one render, one photo-signing request. */
    const showSoon = () => {
      clearTimeout(showTimer)
      showTimer = setTimeout(() => base && void show(base), 60)
    }
    const apply = async (state: VoiceStateRow, sent: number) => {
      this.noteClock(state.now, sent)
      base = state
      seq = typeof state.seq === 'number' ? state.seq : -1
      const later = pending.filter((c) => typeof c.seq === 'number' && c.seq > seq).sort((a, b) => a.seq! - b.seq!)
      pending = []
      for (const c of later) {
        if (c.seq !== seq + 1) {
          refresh()
          break
        }
        seq = c.seq
        applyChange(state, c, me)
        pollChanged(c)
        linesChanged(c)
      }
      return show(state)
    }
    const linesChanged = (c: VoiceChange) => {
      if (c.t === 'h') emit({ type: 'hidden', ids: c.ids })
      else if (c.t === 'x') emit({ type: 'cleared', by: c.by })
    }
    /** A poll's change goes to the screen as it is: counts and totals for everyone, my own vote stays mine. */
    const pollChanged = (c: VoiceChange) => {
      if (c.t !== 'p') return
      const poll = parsePoll(c.poll)
      if (poll) emit({ type: 'poll', poll: { ...poll, mine: null } })
    }
    const onChange = (c: VoiceChange) => {
      if (closed || over) return
      if (c.t === 'r' && c.ended) return finish({ type: 'ended' })
      if (!base || fetching) {
        pending.push(c)
        return
      }
      if (typeof c.seq !== 'number') {
        // Sent without a number (a rare collision between two rooms): apply it, then check against the server.
        applyChange(base, c, me)
        pollChanged(c)
        linesChanged(c)
        showSoon()
        refresh()
        return
      }
      if (c.seq <= seq) return
      if (c.seq !== seq + 1) return refresh() // missed one
      seq = c.seq
      if (c.t === 'p') return pollChanged(c) // the room itself is unchanged
      if (c.t === 'h' || c.t === 'x') return linesChanged(c)
      // Handed the room: the code is the host's only, the full state has it.
      if (c.t === 'r' && c.host === me && base.host !== me) refresh()
      // Taken out of the room myself (removed, or dropped while away): a heartbeat brings me back in, or says
      // why not (voice_removed, room_full, ...).
      if (c.t === 'm' && c.id === me && !c.present) void heartbeat()
      applyChange(base, c, me)
      showSoon()
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
        // The state has no polls: the screen reads the ones it shows again (a vote may be among what was missed).
        emit({ type: 'resync' })
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
    /** The server's number for the room (heartbeat, re-sync): behind it a moment later means a change was missed. */
    let behindTimer: ReturnType<typeof setTimeout> | undefined
    const serverAt = (n: number) => {
      if (n <= seq || behindTimer) return
      behindTimer = setTimeout(() => {
        behindTimer = undefined
        if (n > seq) refresh()
      }, 3_000)
    }

    // ---- lines ----
    const resyncLines = async () => {
      const after = this.newest.get(`voice:${roomId}`)
      if (after === undefined) return // the history hasn't loaded yet; it covers everything so far
      const { data, error } = await this.client.from('voice_messages').select('*').eq('room_id', roomId)
        .in('kind', LINE_KINDS).gt('id', after).order('id', { ascending: true }).limit(200)
      if (error) return
      const rows = data as VoiceMessageRow[]
      this.noteNewest(`voice:${roomId}`, rows)
      for (const line of await this.toVoiceLines(rows)) emit({ type: 'line', line })
    }
    const fresh = reactionClock()
    const onMessage = async (row: VoiceMessageRow) => {
      const show = fresh(time(row.created_at), row.kind === 'reaction')
      if (row.kind === 'reaction') {
        const kind = row.payload?.kind
        if (typeof kind === 'string' && show) {
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
    // I hear the room as soon as I'm in (like X Spaces, Clubhouse, Discord); my choice is kept across a reconnect.
    let sound = true
    let audioTries = 0
    let audioRetry: ReturnType<typeof setTimeout> | undefined
    const setAudio = (patch: Partial<VoiceAudioState>) => {
      audioState = { ...audioState, ...patch }
      emit({ type: 'audio', audio: audioState })
    }
    const retryAudio = () => {
      clearTimeout(audioRetry)
      if (!stage) return
      // A weak network gets a few quick tries, then one every 30 s; the room itself (chat, seats) carries on.
      audioRetry = setTimeout(() => void connectAudio(), Math.min(30_000, 2_000 * 2 ** audioTries++))
    }
    const connectAudio = async () => {
      if (closed || over || audioCtl || !stage) return
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
          micHint: (micHint) => setAudio({ micHint }),
          videos: (videos) => emit({ type: 'videos', videos }),
          gone: (removed) => {
            if (audioCtl !== ctl) return
            audio = null
            audioCtl = null
            emit({ type: 'levels', levels: {} })
            emit({ type: 'videos', videos: {} })
            if (removed) void fetchState() // removed or ended: the state says which
            retryAudio()
          },
        }, ctl.signal, sound)
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
    /** `idle`: the stage emptied (the room goes on without audio until someone takes a seat). */
    const stopAudio = (idle = false) => {
      clearTimeout(audioRetry)
      audioCtl?.abort()
      audioCtl = null
      void audio?.disconnect().catch(() => undefined)
      audio = null
      audioWaiters.splice(0).forEach((f) => f(null))
      if (idle) {
        audioTries = 0
        audioState = { status: 'connecting', needsTap: false }
        emit({ type: 'audio', audio: null })
        emit({ type: 'levels', levels: {} })
        emit({ type: 'videos', videos: {} })
      }
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

    // My camera (24): only on a seat; nothing is stored, the others see the LiveKit track.
    const camera = async (on: boolean) => {
      if (!on) return void (await audio?.setCamera(false).catch(() => undefined))
      if (mine?.seat == null) throw new BackendError('not_allowed')
      const a = await waitAudio(8_000)
      if (!a) throw new BackendError('network')
      const enable = async () => {
        try {
          await a.setCamera(true)
          return true
        } catch (e) {
          const p = e instanceof Error ? e.message : ''
          if (p === 'denied' || p === 'missing' || p === 'busy') throw new BackendError(`camera_${p}`)
          return false
        }
      }
      if (!(await enable())) {
        // LiveKit hasn't heard I'm on a seat yet: ask for it and try once more.
        this.voiceSync(roomId, me)
        await waitPermission(4_000)
        if (!(await enable())) throw new BackendError('not_allowed')
      }
    }

    // ---- typing (a public channel, like random chat's typing: nothing stored) ----
    let typingChannel: RealtimeChannel | null = null
    const openTyping = () => {
      if (typingChannel || closed) return
      const topic = `vtyping-${roomId}`
      for (const c of this.client.getChannels()) if (c.topic === `realtime:${topic}`) void this.client.removeChannel(c)
      typingChannel = this.client.channel(topic)
        .on('broadcast', { event: 'typing' }, ({ payload }) => {
          const id = typeof payload?.id === 'string' ? payload.id : ''
          if (!id || id === me) return
          emit({ type: 'typing', userId: id, name: typeof payload?.name === 'string' ? payload.name.slice(0, 40) : '' })
        })
        .subscribe()
    }

    // ---- keeping up ----
    let channel: RealtimeChannel | null = null
    let healthy = false
    let syncTimer: ReturnType<typeof setTimeout> | undefined
    const nextResync = () => {
      clearTimeout(syncTimer)
      syncTimer = setTimeout(() => {
        resync()
        nextResync()
      }, healthy ? 45_000 : 10_000)
    }
    const resync = () => {
      if (pageHidden() || closed || !joined || over) return
      void (async () => {
        const { data } = await this.client.from('voice_seq').select('seq').eq('room_id', roomId).maybeSingle<{ seq: number }>()
        if (data) serverAt(data.seq)
        await resyncLines()
      })().catch(() => undefined)
    }
    // The topic is private (RLS on realtime.messages: people who entered the room), so it opens once I'm in.
    const openChannel = () => {
      if (channel || closed) return
      const topic = `voice:${roomId}`
      for (const c of this.client.getChannels()) if (c.topic === `realtime:${topic}`) void this.client.removeChannel(c)
      let wasUp = false
      channel = this.client.channel(topic, { config: { private: true } })
        .on('broadcast', { event: 'v' }, ({ payload }) => onChange(payload as VoiceChange))
        .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'voice_messages', filter: `room_id=eq.${roomId}` },
          (p) => void onMessage(p.new as VoiceMessageRow).catch(() => undefined))
        .subscribe((status) => {
          const up = status === 'SUBSCRIBED'
          if (up !== healthy) {
            healthy = up
            nextResync()
          }
          if (wasUp) connection({ realtime: !up })
          if (!up) return
          // Back after a drop: changes may have been missed while away.
          if (wasUp) refresh()
          else resync()
          wasUp = true
        })
    }

    const live: VoiceLive = {
      room: null,
      refresh,
      acted: () => {
        if (!healthy) refresh()
      },
      keep: (p) => {
        if (!base) return
        Object.assign(base, p)
        void show(base)
      },
      mic,
      camera,
      startAudio: async () => {
        await audio?.startAudio()
        setAudio({ needsTap: false })
      },
      volume: (id, v) => audio?.setVolume(id, v),
      sound: async (on) => {
        sound = on
        audio?.setSound(on)
        // Turning it on is a tap: the moment the browser allows the room's audio to play.
        if (on) {
          await audio?.startAudio()
          setAudio({ needsTap: false })
        }
      },
      joined: async (state, sent) => {
        joined = true
        lastBeat = Date.now()
        openChannel()
        openTyping()
        return apply(state, sent)
      },
      typing: (name) => {
        if (!joined || closed || over) return
        void typingChannel?.send({ type: 'broadcast', event: 'typing', payload: { id: me, name } }).catch(() => undefined)
      },
      // The room-change broadcast tells everyone else (037); the sharer reads the room at once.
      staged: () => refresh(),
    }
    this.voice.set(roomId, live)

    // ---- my connection (045): beats every 3 s; the server shows me lost after 6 s and takes me out 7 s later ----
    let lastBeat = Date.now()
    const down = { beat: false, realtime: false }
    let shownLost = false
    const connection = (patch: Partial<typeof down>) => {
      Object.assign(down, patch)
      const lost = down.beat || down.realtime
      if (lost !== shownLost) emit({ type: 'connection', lost: (shownLost = lost) })
    }
    const heartbeat = async () => {
      if (!joined || closed || over) return
      // Gone longer than the server waits: a beat would bring me back in silently. Once the network answers, the
      // room closes instead. A host with no co-host to take over keeps their place (the room waits 2 minutes).
      const r = live.room
      const keepsPlace = r?.hostId === me && !nextHost(r)
      if (droppedOut(lastBeat, Date.now()) && !keepsPlace) {
        const { error } = await this.client.from('voice_seq').select('seq').eq('room_id', roomId).maybeSingle()
        if (error || closed || over) return connection({ beat: true })
        return finish({ type: 'dropped' }) // the room screen closes and leaves, to be sure
      }
      try {
        serverAt(await this.rpc<number>('voice_heartbeat3', { p_room: roomId }))
        lastBeat = Date.now()
        connection({ beat: false })
        // The host re-checks LiveKit against the room every minute (anyone a sync missed).
        if (++beats % 20 === 0 && live.room?.hostId === me) this.voiceSync(roomId)
      } catch (err) {
        failed(err)
        if (!over) connection({ beat: true })
      }
    }
    const beatTimer = setInterval(() => void heartbeat(), VOICE_BEAT_MS)
    nextResync()

    // A listener who left the tab in the background for 30 minutes has gone: leave (the count, the battery).
    let hiddenAt = pageHidden() ? Date.now() : 0
    let idleTimer: ReturnType<typeof setTimeout> | undefined
    const idleCheck = () => {
      if (!hiddenAt || Date.now() - hiddenAt < IDLE_LEAVE_MS || !joined || over) return
      if (live.room?.hostId === me || mine?.seat != null) return // on the stage or hosting: they're the room
      void this.rpc('leave_voice', { p_room: roomId }).catch(() => undefined)
      finish({ type: 'idle' })
    }
    const stopWatching = onPageVisibility((visible) => {
      clearTimeout(idleTimer)
      if (visible) {
        idleCheck() // timers sleep in a background tab: check on the way back too
        hiddenAt = 0
        void heartbeat() // at once: a frozen tab may have been away longer than the server waits
        resync()
      } else {
        hiddenAt = Date.now()
        idleTimer = setTimeout(idleCheck, IDLE_LEAVE_MS + 1_000)
      }
    })
    if (hiddenAt) idleTimer = setTimeout(idleCheck, IDLE_LEAVE_MS + 1_000)

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
      clearTimeout(showTimer)
      clearTimeout(behindTimer)
      clearTimeout(syncTimer)
      clearTimeout(idleTimer)
      clearInterval(beatTimer)
      stopWatching()
      window.removeEventListener('pagehide', onPageHide)
      stopAudio()
      if (this.voice.get(roomId) === live) this.voice.delete(roomId)
      if (channel) void this.client.removeChannel(channel)
      if (typingChannel) void this.client.removeChannel(typingChannel)
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
    await this.signMany([...s.members.map((m) => m.photo_path), s.cover])
    const local = (ms: number) => ms - this.clockOffset // server time → this device's clock
    return {
      id: s.id, title: s.title, mode: s.mode === 'open' ? 'open' : 'approval',
      hostId: s.host, meId: this.uid, announcement: s.announcement ?? null, code: s.code ?? null, password: s.password ?? null,
      maxMembers: s.max ?? 50, genderRule: (s.gender as Target) ?? 'any', minAge: s.minAge ?? 18, maxAge: s.maxAge ?? 100,
      isPrivate: !!s.private, lang: appLang(s.lang), voice: s.voice !== false, theme: s.theme ?? null,
      coverPath: s.cover ?? null, coverUrl: this.signedNow(s.cover ?? null), tags: s.tags ?? [],
      members: s.members.map((m) => ({
        profile: this.cardProfile(m), role: m.role as VoiceRole, seat: m.seat, muted: m.muted, hand: m.hand, lost: m.lost === true, joinedAt: m.j,
      })),
      seats: Array.from({ length: VOICE_SEATS }, (_, index) => ({ index, locked: s.locked.includes(index) })),
      track: s.music ? {
        id: s.music.id, videoId: s.music.videoId, title: s.music.title, addedBy: s.music.by,
        startedAt: local(s.music.startedAt), pausedAt: s.music.pausedAt === null ? null : local(s.music.pausedAt),
      } : null,
      queue: s.queue.map((q) => ({ id: q.id, videoId: q.videoId, title: q.title, addedBy: q.by, votes: q.votes ?? 0, voted: !!q.voted })),
      stage: s.stage && typeof s.stage.url === 'string' && shareable(s.stage.url) ? {
        url: s.stage.url, title: s.stage.title || null, by: String(s.stage.by ?? ''), byId: String(s.stage.byId ?? ''),
        at: local(Number(s.stage.at) || 0),
      } : null,
    }
  }

  private async toVoiceLines(rows: VoiceMessageRow[]): Promise<VoiceLine[]> {
    const me = this.uid
    // One request signs every face and photo in the batch.
    await this.signMany(rows.flatMap((r) => (r.kind === 'text' || r.kind === 'photo' || r.kind === 'poll' ? [r.sender_photo, r.media_path] : [])))
    return rows.flatMap((r): VoiceLine[] => {
      const at = time(r.created_at)
      const sender = () => this.cardProfile({
        id: r.sender, name: r.sender_name, gender: r.sender_gender ?? 'other', age: r.sender_age ?? 0, city: '', bio: '',
        photo_path: r.sender_photo, country: r.sender_country,
      })
      if (r.kind === 'text') {
        return [{ id: r.id, kind: 'text', fromMe: r.sender === me, text: r.body ?? '', at, sender: sender(), reply: parseReply(r.payload) }]
      }
      if (r.kind === 'photo') {
        const url = r.media_path ? this.signedUrls.get(r.media_path)?.url : undefined
        return url ? [{ id: r.id, kind: 'photo', fromMe: r.sender === me, url, at, sender: sender() }] : [] // no longer readable
      }
      if (r.kind === 'poll') {
        const p = r.payload ?? {}
        const pollId = Number(p.poll_id)
        const options = Array.isArray(p.options) ? p.options.filter((o): o is string => typeof o === 'string') : []
        if (!Number.isFinite(pollId) || typeof p.question !== 'string' || options.length < 2) return []
        return [{ id: r.id, kind: 'poll', fromMe: r.sender === me, pollId, question: p.question, options, at, sender: sender() }]
      }
      if (r.kind !== 'system') return [] // a kind this app doesn't know yet
      const p = r.payload ?? {}
      return [{
        id: r.id, kind: 'system', event: String(p.event) as VoiceSystemEvent, name: String(p.name ?? r.sender_name), at,
        ...(typeof p.seat === 'number' ? { seat: p.seat } : {}),
        ...(typeof p.title === 'string' ? { title: p.title } : {}),
      }]
    })
  }

  /**
   * My friends as friend_list shows them (35: friends always see someone's real profile, VIP or not). Rooms send
   * everyone the same cards, VIP ones masked: a friend's card is shown from here instead.
   */
  private friendCards = new Map<string, Profile>()
  private unmasked = (c: CardRow) => (c.name === 'VIP' && !c.photo_path ? this.friendCards.get(c.id) : undefined)

  /** A card from a voice room; the photo comes from signMany (one request for the whole room), else the initial. */
  private cardProfile(c: CardRow): Profile {
    const friend = this.unmasked(c)
    if (friend) return friend
    const hit = c.photo_path ? this.signedUrls.get(c.photo_path) : undefined
    return {
      id: c.id, name: c.name, gender: gender(c.gender), age: c.age, city: c.city, bio: c.bio,
      photoUrl: hit?.url ?? null, country: c.country, posts: [],
    }
  }

  /**
   * Signs every photo not signed yet in one request (a voice room shows dozens of faces at once), profile photos with
   * their small copies (lib/thumbs.ts). A photo without one (older ones, until their owner's app adds it) is
   * remembered as such for the same 50 minutes, so it isn't asked for again.
   */
  private async signMany(paths: (string | null | undefined)[]) {
    const now = Date.now()
    const wanted = [...new Set(paths.filter((p): p is string => !!p))]
    const need = [...wanted, ...wanted.filter(hasThumb).map(thumbPath)].filter((p) => !((this.signedUrls.get(p)?.reuseUntil ?? 0) > now))
    if (need.length) {
      const { data } = await this.client.storage.from(BUCKET).createSignedUrls(need, SIGNED_URL_SECONDS).catch(() => ({ data: null }))
      for (const d of data ?? []) {
        if (d.path && (d.signedUrl || d.path.endsWith('_s.jpg'))) this.signedUrls.set(d.path, { url: d.signedUrl ?? '', reuseUntil: now + 50 * 60_000 })
      }
      this.saveSigned()
    }
    for (const p of wanted) if (hasThumb(p)) this.linkThumb(p)
  }

  private linkThumb(path: string) {
    const full = this.signedUrls.get(path)?.url
    const small = this.signedUrls.get(thumbPath(path))?.url
    if (full && small) registerThumb(full, small)
  }

  private saveSigned() {
    this.saveTimer ??= setTimeout(() => {
      this.saveTimer = null
      const now = Date.now()
      // The newest 800 still in use.
      const keep = [...this.signedUrls].filter(([, v]) => v.reuseUntil > now).slice(-800)
      try {
        localStorage.setItem(SIGNED_KEY, JSON.stringify(keep))
      } catch {
        /* storage full or blocked: they are signed again next time */
      }
    }, 1_000)
  }

  private forgetSigned() {
    this.signedUrls.clear()
    try {
      localStorage.removeItem(SIGNED_KEY)
    } catch {
      /* nothing kept */
    }
  }

  /**
   * My profile photos from before small copies existed get theirs here, once per browser: the photo is read, shrunk
   * on this device and uploaded beside it (only I may write in my folder).
   */
  async backfillThumbs() {
    const flag = `yaari_thumbs_${this.uid}`
    try {
      if (localStorage.getItem(flag)) return
    } catch {
      return
    }
    const me = await this.mine()
    const paths = [me.photoPath, ...(me.photos ?? []).map((p) => p.id)].filter((p): p is string => !!p && hasThumb(p))
    await this.signMany(paths)
    for (const p of paths) {
      const full = this.signedUrls.get(p)?.url
      if (!full || this.signedUrls.get(thumbPath(p))?.url) continue
      const small = await compressImage(await (await fetch(full)).blob(), THUMB_PX)
      const { error } = await this.client.storage.from(BUCKET).upload(thumbPath(p), small, { contentType: 'image/jpeg', cacheControl: CACHE_FOREVER })
      if (error && !/exists/i.test(error.message)) throw error
      this.signedUrls.delete(thumbPath(p))
    }
    try {
      localStorage.setItem(flag, '1')
    } catch {
      /* asked again next time */
    }
  }

  // ---- mapping & media ----

  private async toProfile(c: CardRow, posts: Post[]): Promise<Profile> {
    const friend = this.unmasked(c)
    if (friend) return { ...friend, posts }
    return {
      id: c.id, name: c.name, gender: gender(c.gender), age: c.age, city: c.city, bio: c.bio,
      photoUrl: c.photo_path ? await this.signed(c.photo_path).catch(() => null) : null, country: c.country, posts,
    }
  }

  private async toMessage(r: MessageRow, me: string): Promise<Message> {
    return (await this.toMessages([r], me))[0]
  }

  /**
   * Messages with their media signed in one request. A file that can't be signed (gone, or the request failed) leaves
   * its line without the picture instead of failing the whole history (it used to empty the chat).
   */
  private async toMessages(rows: MessageRow[], me: string): Promise<Message[]> {
    await this.signMany(rows.map((r) => r.media_path))
    return rows.map((r) => {
      const url = r.media_path ? this.signedUrls.get(r.media_path)?.url : undefined
      return {
        id: r.id, fromMe: r.sender === me, text: r.body,
        media: url ? { url, kind: r.media_kind === 'video' ? 'video' : 'image' } : null,
        at: time(r.created_at), clientId: r.client_id,
      }
    })
  }

  private async signed(path: string): Promise<string> {
    await this.signMany([path])
    const url = this.signedUrls.get(path)?.url
    if (!url) throw new BackendError('not_found')
    return url
  }


  /** Uploads under "<uid>/<folder>/"; photos are shrunk to 1280 px JPEG, videos are capped at 25 MB. */
  private async upload(file: File, folder: 'avatar' | 'chat' | 'posts' | 'rooms' | 'cover'): Promise<string> {
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
    // A file never changes under its name (a new photo gets a new one), so it may be cached for good.
    const opts = { contentType: type, cacheControl: CACHE_FOREVER }
    const small = !isVideo && hasThumb(path) ? await compressImage(file, THUMB_PX) : null
    const [{ error }] = await Promise.all([
      this.client.storage.from(BUCKET).upload(path, body, opts),
      // Without its small copy a photo still shows (the whole photo); its owner's app adds the copy later.
      small ? this.client.storage.from(BUCKET).upload(thumbPath(path), small, opts).catch(() => null) : null,
    ])
    if (error) throw new BackendError(codeOf(error.message))
    return path
  }
}
