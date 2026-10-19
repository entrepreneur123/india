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
  type Target,
  type VideoStatus,
} from './backend'
import { onPageVisibility, pageHidden } from './visibility'

const BUCKET = 'media'
const MAX_PHOTO_PX = 1280
const MAX_VIDEO_BYTES = 25 * 1024 * 1024
const SIGNED_URL_SECONDS = 12 * 3600
/** The storage bucket's allowed_mime_types (001_core.sql); anything else is refused by the server. */
const VIDEO_TYPES = ['video/mp4', 'video/3gpp', 'video/quicktime', 'video/webm']
const CODES = [
  'exit_locked', 'premium_required', 'banned', 'under_18', 'slow_down', 'not_allowed', 'not_found',
  'video_not_configured', 'billing_not_configured', 'already_redeemed', 'wrong_account', 'too_large',
  'room_closed', 'room_gender', 'room_age', 'room_full', 'room_title', 'room_size',
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
  video_status: string; video_requested_by: string | null
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
type RoomMessageRow = {
  id: number; room_id: string; sender: string; sender_name: string; sender_gender: string; sender_age: number
  sender_photo: string | null; sender_country: string | null; body: string; created_at: string
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

export class SupabaseBackend implements Backend {
  private client: SupabaseClient
  /** Signed media URL and when to stop reusing it (they are valid for 12 h). */
  private signedUrls = new Map<string, { url: string; reuseUntil: number }>()
  /** One typing channel per match, shared by every view of it (realtime-js reuses a channel per topic). */
  private typing = new Map<string, { channel: RealtimeChannel; listeners: Set<(from: string) => void> }>()
  /** Matches the user never saw, waiting to be ended; opening one again (the server can hand it back) keeps it. */
  private abandoned = new Set<string>()
  private userId: string | null = null

  constructor(url: string, anonKey: string) {
    this.client = createClient(url, anonKey, { auth: { persistSession: true, storageKey: 'yaari_auth' } })
    this.client.auth.onAuthStateChange((_event, session) => {
      this.userId = session?.user.id ?? null
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

  async findMatch(target: Target): Promise<Match | null> {
    let id: string | null
    try {
      id = await this.rpc<string | null>('find_match', { p_target: target })
    } catch (e) {
      // A chat we just left (or abandoned) still holds the exit lock for a moment: keep waiting.
      if (e instanceof BackendError && e.code === 'exit_locked') return null
      throw e
    }
    if (!id) return null
    try {
      const m = await this.matchRow(id)
      if (!m) throw new BackendError('not_found')
      const partner = await this.card(m.user_a === this.uid ? m.user_b : m.user_a)
      if (!partner) throw new BackendError('not_found')
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

  /** Null when the match is gone (the other person deleted their account). */
  private async matchRow(id: string): Promise<MatchRow | null> {
    const { data, error } = await this.client.from('matches')
      .select('id, user_a, user_b, ended_at, ended_by, video_status, video_requested_by').eq('id', id).maybeSingle<MatchRow>()
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

  subscribe(matchId: string, on: (e: MatchEvent) => void): () => void {
    const me = this.uid
    let closed = false
    let lastVideo: VideoStatus | null = null
    let leftSent = false
    const emit = (e: MatchEvent) => { if (!closed) on(e) }
    const onMatch = (m: MatchRow | null) => {
      if ((m === null || (m.ended_at && m.ended_by !== me)) && !leftSent) {
        leftSent = true
        emit({ type: 'partner_left' })
      }
      if (!m) return
      const v = this.videoOf(m, me)
      if (v !== lastVideo) {
        lastVideo = v
        emit({ type: 'video', status: v })
      }
    }

    // One bad record (or a failed media link) must not stop the chat: skip it, the 10 s re-sync retries.
    const channel = this.client.channel(`match-${matchId}-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages', filter: `match_id=eq.${matchId}` },
        (p) => void this.toMessage(p.new as MessageRow, me).then((message) => emit({ type: 'message', message }), () => undefined))
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'matches', filter: `id=eq.${matchId}` },
        (p) => onMatch(p.new as MatchRow))
      .subscribe()

    // Typing signals ride a broadcast channel shared with the Android app (not stored anywhere).
    let typingOff: ReturnType<typeof setTimeout> | undefined
    const onTyping = (from: string) => {
      if (from === me) return
      emit({ type: 'typing', on: true })
      clearTimeout(typingOff)
      typingOff = setTimeout(() => emit({ type: 'typing', on: false }), 4_000)
    }
    let hub = this.typing.get(matchId)
    if (!hub) {
      const listeners = new Set<(from: string) => void>()
      const ch = this.client.channel(`typing-${matchId}`)
        .on('broadcast', { event: 'typing' }, ({ payload }) => listeners.forEach((l) => l(String(payload?.from ?? ''))))
        .subscribe()
      hub = { channel: ch, listeners }
      this.typing.set(matchId, hub)
    }
    hub.listeners.add(onTyping)

    // Safety net if the realtime socket drops on a weak network: re-sync every 10 s (the UI de-duplicates by id).
    // Paused while the browser tab is hidden (realtime keeps delivering); runs at once when it is shown again.
    const resync = async () => {
      if (pageHidden()) return
      try {
        onMatch(await this.matchRow(matchId))
        for (const message of await this.history(matchId)) emit({ type: 'message', message })
      } catch { /* next round */ }
    }
    void resync()
    const timer = setInterval(resync, 10_000)
    const stopWatching = onPageVisibility((visible) => visible && void resync())

    return () => {
      closed = true
      clearInterval(timer)
      stopWatching()
      clearTimeout(typingOff)
      const h = this.typing.get(matchId)
      h?.listeners.delete(onTyping)
      if (h && h.listeners.size === 0) {
        this.typing.delete(matchId)
        void this.client.removeChannel(h.channel)
      }
      void this.client.removeChannel(channel)
    }
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
    return Promise.all((data as MessageRow[]).reverse().map((r) => this.toMessage(r, me)))
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

  async joinRoomCode(code: string) {
    return this.rpc<string>('join_room_code', { p_code: code })
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
    return Promise.all((data as RoomMessageRow[]).reverse().map((r) => this.toRoomMessage(r, me)))
  }

  subscribeRoom(roomId: string, on: (m: RoomMessage) => void): () => void {
    const me = this.uid
    let closed = false
    const emit = (m: RoomMessage) => { if (!closed) on(m) }
    const channel = this.client.channel(`room-${roomId}-${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'room_messages', filter: `room_id=eq.${roomId}` },
        (p) => void this.toRoomMessage(p.new as RoomMessageRow, me).then(emit, () => undefined))
      .subscribe()
    // Re-sync after a dropped socket (the UI de-duplicates by id); paused while the browser tab is hidden.
    const resync = () => {
      if (pageHidden()) return
      this.roomHistory(roomId).then((h) => h.slice(-30).forEach(emit), () => undefined)
    }
    const timer = setInterval(resync, 15_000)
    const stopWatching = onPageVisibility((visible) => visible && resync())
    return () => {
      closed = true
      clearInterval(timer)
      stopWatching()
      void this.client.removeChannel(channel)
    }
  }

  async sendRoom(roomId: string, text: string): Promise<RoomMessage> {
    const { data, error } = await this.client.from('room_messages').insert({ room_id: roomId, body: text })
      .select('*').single<RoomMessageRow>()
    if (error) fail(error)
    return this.toRoomMessage(data, this.uid)
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
    this.signedUrls.set(path, { url: data.signedUrl, reuseUntil: now + 11 * 3_600_000 })
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
