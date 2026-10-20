export type Gender = 'male' | 'female' | 'other'
export type Target = 'any' | 'male' | 'female' // anything but 'any' is paid

export interface Post {
  id: string
  text: string
  imageUrl?: string | null
  createdAt: number
}

export interface Profile {
  id: string
  name: string
  gender: Gender
  age: number
  city: string
  bio: string
  photoUrl?: string | null
  country?: string | null /* ISO-2, show flag emoji */
  posts: Post[]
}

export interface Message {
  id: number
  fromMe: boolean
  text?: string | null
  media?: { url: string; kind: 'image' | 'video' } | null
  at: number
}

export interface RecentMatch {
  matchId: string
  partner: Profile
  metAt: number
  lastText?: string | null
}

export type ReportReason = 'nudity' | 'minor' | 'harassment' | 'fake' | 'other'
export type FriendState = 'none' | 'requested' | 'incoming' | 'friends'

export interface Friend {
  profile: Profile
  state: FriendState
  threadId?: string | null
}

export type RoomCategory = 'chat' | 'friends' | 'language' | 'music' | 'movies' | 'gaming' | 'sports' | 'study' | 'travel' | 'night'
/** Why I can't enter a user room (null/absent = I can). */
export type RoomRefusal = 'room_closed' | 'room_gender' | 'room_age' | 'room_full' | 'not_allowed'

export interface Room {
  id: string
  title: string
  emoji: string
  online: number
  /** Official rooms are run by yaari; the rest were opened by a user. */
  official: boolean
  mine: boolean
  ownerName?: string | null
  category: RoomCategory
  maxMembers?: number | null
  genderRule: Target
  minAge: number
  maxAge: number
  isPrivate: boolean
  /** Only for my own rooms. */
  code?: string | null
  refusal?: RoomRefusal | null
}

export interface NewRoom {
  title: string
  category: RoomCategory
  maxMembers: number
  genderRule: Target
  minAge: number
  maxAge: number
  isPrivate: boolean
}

export interface RoomMessage {
  id: number
  sender: Profile
  fromMe: boolean
  text: string
  at: number
}

// ---- voice rooms (X Spaces-style party rooms: a stage of seats, an audience, chat and shared music) ----

/** Co-hosts can invite, move and mute people; only the host makes co-hosts, removes people and ends the room. */
export type VoiceRole = 'host' | 'cohost' | 'speaker' | 'listener'
/** Who may take a seat: listeners raise a hand and the host invites them up, or anyone takes a free seat. */
export type SpeakMode = 'approval' | 'open'

/** Seat 0 is the host's; seats 1–8 are the stage. */
export const VOICE_SEATS = 9

export interface VoiceMember {
  profile: Profile
  role: VoiceRole
  /** 0–8 on the stage, null in the audience. */
  seat: number | null
  muted: boolean
  hand: boolean
}

export interface VoiceSeat {
  index: number
  locked: boolean
}

/**
 * The shared track. Everyone plays the same YouTube video at the same position: a client seeks to
 * now − startedAt (or pausedAt − startedAt while paused).
 */
export interface VoiceTrack {
  id: string
  videoId: string
  /** Empty when unknown (a pasted link): the player's own title is shown. */
  title: string
  addedBy: string
  startedAt: number
  pausedAt: number | null
}

export interface VoiceQueueItem {
  id: string
  videoId: string
  title: string
  addedBy: string
  /** A listener's suggestion the host has not approved yet. */
  pending: boolean
}

export interface VoiceRoomSummary {
  id: string
  title: string
  category: RoomCategory
  host: Profile
  /** Up to 4 people on the stage, the host first. */
  speakers: Profile[]
  listeners: number
  nowPlaying: string | null
  mine: boolean
}

export interface VoiceRoom {
  id: string
  title: string
  category: RoomCategory
  mode: SpeakMode
  hostId: string
  meId: string
  members: VoiceMember[]
  seats: VoiceSeat[]
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
}

export interface NewVoiceRoom {
  title: string
  category: RoomCategory
  mode: SpeakMode
}

/** Reactions are drawn glyphs (components/ui/topic-icons.tsx), never emoji. */
export type VoiceReaction = 'heart' | 'laugh' | 'fire' | 'clap' | 'wow'

export type VoiceSystemEvent = 'joined' | 'left' | 'seat' | 'audience' | 'cohost' | 'host' | 'track' | 'removed'

export type VoiceLine =
  | { id: number; kind: 'text'; sender: Profile; fromMe: boolean; text: string; at: number }
  | { id: number; kind: 'system'; event: VoiceSystemEvent; name: string; seat?: number; title?: string; at: number }

/**
 * The room's live audio (live server only; the demo has none). `needsTap`: the browser blocked playback until
 * the user touches the page (autoplay rules).
 */
export interface VoiceAudioState {
  status: 'connecting' | 'connected' | 'reconnecting' | 'failed'
  needsTap: boolean
}

export type VoiceEvent =
  | { type: 'room'; room: VoiceRoom }
  | { type: 'audio'; audio: VoiceAudioState }
  /** Audio level 0..1 per user id, several times a second (absent = silent). */
  | { type: 'levels'; levels: Record<string, number> }
  | { type: 'line'; line: VoiceLine }
  | { type: 'reaction'; userId: string; kind: VoiceReaction }
  | { type: 'ended' }
  | { type: 'removed' }

export type VoiceAction =
  | { type: 'hand'; up: boolean }
  | { type: 'mic'; on: boolean }
  /** Take this free seat (open rooms, host and co-hosts); in a hand-raise room it raises my hand. */
  | { type: 'seat'; seat: number }
  | { type: 'unseat' }
  | { type: 'react'; kind: VoiceReaction }
  // host and co-hosts
  | { type: 'invite'; userId: string; seat?: number }
  | { type: 'answer'; userId: string; accept: boolean }
  | { type: 'toAudience'; userId: string }
  | { type: 'mute'; userId: string }
  | { type: 'lock'; seat: number; locked: boolean }
  // host only
  | { type: 'cohost'; userId: string; on: boolean }
  | { type: 'kick'; userId: string }
  // music: the host (DJ) adds and controls; everyone else suggests
  | { type: 'addTrack'; videoId: string }
  | { type: 'suggest'; videoId: string }
  | { type: 'approve'; itemId: string; accept: boolean }
  | { type: 'play'; itemId: string }
  | { type: 'removeItem'; itemId: string }
  | { type: 'pause' }
  | { type: 'resume' }
  /** `trackId`: the track being skipped (a player that ended); nothing happens if another one already plays. */
  | { type: 'skip'; trackId?: string }

// ---- Ludo in a voice room (018_ludo.sql; rules in lib/ludo.ts) ----

/** Seats 0-3 are red, green, yellow and blue, played in that order. */
export type LudoStatus = 'lobby' | 'playing' | 'finished' | 'cancelled'

/**
 * What the last action did, in order (a roll, the move it made, a capture...): the apps animate and announce it.
 * Token positions: -1 base, 0-50 track (from the colour's start square), 51-55 home column, 56 home.
 */
export type LudoEvent =
  | { k: 'start'; s: number }
  | { k: 'roll'; s: number; d: number }
  /** `cap`: tokens sent home, as [seat, token, position]. */
  | { k: 'move'; s: number; t: number; f: number; to: number; cap: [number, number, number][] }
  | { k: 'pass' | 'triple' | 'timeout' | 'win'; s: number }
  | { k: 'out'; s: number; why: 'left' | 'timeouts' | 'quit' }

export interface LudoGame {
  id: string
  roomId: string
  status: LudoStatus
  /** User id per colour (null = free) and the name they sat down with. */
  players: (string | null)[]
  names: (string | null)[]
  out: boolean[]
  /** 16 positions: tokens[seat * 4 + k]. */
  tokens: number[]
  turn: number | null
  /** A roll waiting for a move (null: the player rolls next). */
  dice: number | null
  /** The last value shown on the die. */
  roll: number | null
  /** The turn ends then (this device's clock); anyone in the room may then play it for the absent player. */
  deadline: number | null
  winner: number | null
  events: LudoEvent[]
  /** Bumped by every change; actions carry the one the screen showed. */
  version: number
  endedAt: number | null
}

export type LudoAction =
  // host and co-hosts
  | { type: 'open' }
  | { type: 'start' }
  | { type: 'close' }
  // anyone in the room
  | { type: 'join'; seat?: number }
  | { type: 'leave' }
  | { type: 'roll'; version: number }
  | { type: 'move'; token: number; version: number }
  /** The turn's time is up: the server plays it (or does nothing when it was already played). */
  | { type: 'timeout'; version: number }

export interface Match {
  id: string
  partner: Profile
}

export type VideoStatus = 'none' | 'requested_by_me' | 'requested_by_partner' | 'accepted' | 'declined'

export type MatchEvent =
  | { type: 'message'; message: Message }
  | { type: 'typing'; on: boolean }
  | { type: 'video'; status: VideoStatus }
  | { type: 'partner_left' }
  | { type: 'read'; upto: number } // the partner has read my messages up to this id

export interface Entitlements {
  premium: boolean
  genderPass: boolean
}

export interface NewProfile {
  name: string
  gender: Gender
  birthDate: string /* YYYY-MM-DD */
  city: string
  bio: string
  photo?: File | null
}

export type BackendErrorCode =
  | 'exit_locked'
  | 'premium_required'
  | 'banned'
  | 'under_18'
  | 'slow_down'
  | 'not_allowed'
  | 'not_found'
  | 'network'
  | 'blocked'
  | 'too_large'
  | 'bad_media'
  | 'video_not_configured'
  | 'sms_unavailable'
  | 'room_closed'
  | 'room_gender'
  | 'room_age'
  | 'room_full'
  | 'room_title'
  | 'room_size'
  | 'voice_removed'
  | 'mic_denied'
  | 'queue_full'
  | 'not_your_turn'
  | 'seat_taken'
  | 'game_full'
  | 'need_players'
  | 'stale'

export class BackendError extends Error {
  constructor(public code: string) {
    super(code)
    this.name = 'BackendError'
  }
}

export interface Backend {
  sendOtp(phone: string): Promise<void> // digits incl. country code, e.g. "919876543210"
  verifyOtp(phone: string, code: string): Promise<boolean>
  isSignedIn(): Promise<boolean>
  myProfile(): Promise<Profile | null>
  createProfile(p: NewProfile): Promise<Profile>
  entitlements(): Promise<Entitlements>
  signOut(): Promise<void>
  deleteAccount(): Promise<void>
  /** Phone number of the signed-in account, shown in Settings. */
  myPhone(): Promise<string | null>
  /** Null = still queued; the UI calls again every 2 s. `lang` (the app language) is preferred in a partner. */
  findMatch(target: Target, lang?: string): Promise<Match | null>
  cancelSearch(): Promise<void>
  /** While searching: called when the server pairs us, so the UI can ask again at once. Returns the stop function. */
  matchSignals(on: () => void): () => void
  /** Ends a match the user never saw (search cancelled while it was being made), once the exit lock allows. */
  abandon(matchId: string): void
  /**
   * Live updates for one chat. With `live` (an open random chat) it also reports `partner_left` when the
   * partner's app never connects or disconnects for good.
   */
  subscribe(matchId: string, on: (e: MatchEvent) => void, opts?: { live?: boolean }): () => void
  history(matchId: string): Promise<Message[]>
  sendTyping(matchId: string): void
  /** Tells the partner I've read their messages up to this id (the read ticks). Fire and forget. */
  markRead(matchId: string, upto: number): void
  /** The call's nudity check hid the partner's video: files an automatic report for review. Never throws. */
  flagNudity(matchId: string): Promise<void>
  send(matchId: string, text: string | null, media: File | null): Promise<Message>
  leave(matchId: string): Promise<void> // throws BackendError('exit_locked') inside the lock window
  report(partnerId: string, reason: ReportReason, matchId: string | null): Promise<void>
  /** Blocks without reporting: open chats with them end and neither side sees the other again. */
  block(userId: string): Promise<void>
  requestVideo(matchId: string): Promise<void> // paid
  answerVideo(matchId: string, accept: boolean): Promise<void>
  endVideo(matchId: string): Promise<void>
  videoToken(matchId: string): Promise<{ url: string; token: string } | null>
  recents(): Promise<RecentMatch[]>
  profile(userId: string): Promise<Profile | null>
  addPost(text: string, image: File | null): Promise<Post>
  updateProfile(name: string, city: string, bio: string, photo: File | null): Promise<Profile>
  friends(): Promise<Friend[]>
  requestFriend(userId: string): Promise<FriendState>
  answerFriend(userId: string, accept: boolean): Promise<void>
  removeFriend(userId: string): Promise<void>
  friendThread(userId: string): Promise<string> // a matchId to open the chat in
  rooms(): Promise<Room[]>
  /** Opens a room owned by me (closing my previous one); I am inside it. */
  createRoom(r: NewRoom): Promise<{ id: string; code: string }>
  /** Enters the room with this code (private rooms are only reachable this way); returns its id. */
  joinRoomCode(code: string): Promise<string>
  /** Owner only: nobody can enter or write any more. */
  closeRoom(roomId: string): Promise<void>
  joinRoom(roomId: string): Promise<void> // call again every 30 s while open
  leaveRoom(roomId: string): Promise<void>
  roomHistory(roomId: string): Promise<RoomMessage[]>
  subscribeRoom(roomId: string, on: (m: RoomMessage) => void): () => void
  sendRoom(roomId: string, text: string): Promise<RoomMessage>
  voiceRooms(): Promise<VoiceRoomSummary[]>
  /** Opens a voice room with me as host (ending my previous one); returns its id. */
  createVoiceRoom(r: NewVoiceRoom): Promise<string>
  /** Enters as a listener (or as the host of my own room). */
  joinVoice(roomId: string): Promise<VoiceRoom>
  leaveVoice(roomId: string): Promise<void>
  /** Host only: everyone is moved out and the room closes. */
  endVoice(roomId: string): Promise<void>
  voiceLines(roomId: string): Promise<VoiceLine[]>
  subscribeVoice(roomId: string, on: (e: VoiceEvent) => void): () => void
  sendVoice(roomId: string, text: string): Promise<VoiceLine>
  /** Throws BackendError('not_allowed') when my role may not do it. */
  voiceAction(roomId: string, action: VoiceAction): Promise<void>
  /** Starts the room's audio after a tap, when the browser blocked it (VoiceAudioState.needsTap). */
  voiceStartAudio(roomId: string): Promise<void>
  reportVoiceRoom(roomId: string, reason: ReportReason): Promise<void>
  /** The room's game: open, or one that ended in the last few minutes (its result); null when there is none. */
  ludoGame(roomId: string): Promise<LudoGame | null>
  /** Every change to the room's game (a new game, each roll and move), while I'm in the room. */
  subscribeLudo(roomId: string, on: (game: LudoGame) => void): () => void
  /** Returns the game as it is after the action. Refusals: not_your_turn, seat_taken, game_full, need_players, stale. */
  ludoAction(roomId: string, gameId: string | null, action: LudoAction): Promise<LudoGame | null>
}
