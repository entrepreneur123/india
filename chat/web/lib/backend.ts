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
  /** 3-5 of INTERESTS (absent: not loaded for this card). */
  interests?: Interest[]
  /** Up to 4 more photos (id = the stored file). */
  photos?: ProfilePhoto[]
}

/** Interest tags (024_discovery_profiles.sql has the same list); the apps translate them. */
export const INTERESTS = [
  'music', 'movies', 'cricket', 'football', 'gaming', 'travel', 'food', 'fitness', 'books', 'tech',
  'art', 'dance', 'fashion', 'photography', 'anime', 'pets', 'study', 'business', 'spirituality', 'comedy',
] as const
export type Interest = (typeof INTERESTS)[number]
export const MIN_INTERESTS = 3
export const MAX_INTERESTS = 5
export const MAX_PHOTOS = 4

export interface ProfilePhoto {
  id: string
  url: string
}

// ---- notifications (023_notifications.sql) ----

export type InboxKind = 'message' | 'friend_request' | 'friend_accept' | 'room_invite'

export interface InboxItem {
  id: number
  kind: InboxKind
  actor: Profile
  at: number
  read: boolean
  /** Messages folded into a 'message' item. */
  count: number
  matchId: string | null
  roomId: string | null
  roomTitle: string | null
  /** The invited-to room is still live. */
  roomOpen: boolean
  /** A friend request still waiting for my answer. */
  pending: boolean
}

export interface Badges {
  /** Unread inbox items. */
  inbox: number
  /** Friend requests waiting for me. */
  friends: number
  /** Unread messages per chat (match id). */
  chats: Record<string, number>
}

/** Room search: title words, the room's language (an app language) and its category; empty = everything. */
export interface RoomQuery {
  text?: string
  lang?: string | null
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

/** Topics the room glyphs are drawn for (components/ui/topic-icons.tsx). */
export type RoomCategory = 'chat' | 'friends' | 'language' | 'music' | 'movies' | 'gaming' | 'sports' | 'study' | 'travel' | 'night'
/** Why I can't enter a room (null/absent = I can). */
export type RoomRefusal = 'room_closed' | 'room_gender' | 'room_age' | 'room_full' | 'not_allowed'

// ---- rooms (019_unified_rooms.sql): one kind of room. Every room has the chat (text, icebreakers, photos, polls), a
// stage of seats with an audience and shared music; its category sets the layout and one signature feature. ----

/**
 * Talk: chat first, icebreakers. Music: the player first, song requests by vote. Games are gone (029): the server
 * makes 'games' a talk room, and the apps read any category they don't know as talk.
 */
export type RoomKind = 'talk' | 'music'

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

/** A song request: anyone adds one; the host's "next" plays the most voted (ties: the oldest). */
export interface VoiceQueueItem {
  id: string
  videoId: string
  title: string
  addedBy: string
  votes: number
  /** I voted for it. */
  voted: boolean
}

/** Who may come in. */
export interface RoomRules {
  maxMembers: number
  genderRule: Target
  minAge: number
  maxAge: number
  /** Found only by its code. */
  isPrivate: boolean
}

export interface VoiceRoomSummary extends RoomRules {
  id: string
  title: string
  category: RoomKind
  host: Profile
  /** Up to 4 people on the stage, the host first (empty: a chat-only room right now). */
  speakers: Profile[]
  listeners: number
  /** People present, me included. */
  members: number
  nowPlaying: string | null
  announcement: string | null
  mine: boolean
  /** Only for my own room. */
  code: string | null
  refusal: RoomRefusal | null
  /** The language the room speaks (en, hi, bn, pt-BR, vi), null when unknown. */
  lang: string | null
}

export interface VoiceRoom extends RoomRules {
  id: string
  title: string
  category: RoomKind
  mode: SpeakMode
  hostId: string
  meId: string
  members: VoiceMember[]
  seats: VoiceSeat[]
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
  /** Pinned at the top by the host. */
  announcement: string | null
  /** The room's 6-character code, for its host only. */
  code: string | null
  lang: string | null
}

export interface NewVoiceRoom extends RoomRules {
  title: string
  category: RoomKind
  announcement: string
  /** The language the room speaks (the app language by default). */
  lang: string
}

/** Reactions are drawn glyphs (components/ui/topic-icons.tsx), never emoji. */
export type VoiceReaction = 'heart' | 'laugh' | 'fire' | 'clap' | 'wow'

export type VoiceSystemEvent = 'joined' | 'left' | 'seat' | 'audience' | 'cohost' | 'host' | 'track' | 'removed'

/**
 * The line a reply answers, as the server copied it into the reply (029): shown without looking it up, also when the
 * original is far back. `text`: up to 120 characters of a text line, a poll's question, null for a photo.
 */
export interface VoiceReply {
  id: number
  /** The sender's user id. */
  sender: string
  name: string
  kind: 'text' | 'photo' | 'poll'
  text: string | null
}

/**
 * A poll (029): the host or a co-host asks, everyone in the room votes once and may change it while it is open.
 * `mine`: my choice (0-based), null when I haven't voted (or, on a live update, unknown: each app keeps its own).
 */
export interface VoicePoll {
  id: number
  question: string
  options: string[]
  counts: number[]
  total: number
  closed: boolean
  /** Who asked (user id). */
  by: string
  mine: number | null
}

export type VoiceLine =
  | { id: number; kind: 'text'; sender: Profile; fromMe: boolean; text: string; at: number; reply?: VoiceReply | null }
  | { id: number; kind: 'system'; event: VoiceSystemEvent; name: string; seat?: number; title?: string; at: number }
  /** A conversation starter: prompt 1-40 (the text is in each app's own language). */
  | { id: number; kind: 'icebreaker'; prompt: number; name: string; fromMe: boolean; at: number }
  /** A photo (checked on the sender's device; shown blurred until tapped). */
  | { id: number; kind: 'photo'; sender: Profile; fromMe: boolean; url: string; at: number }
  /** A poll's place in the chat; its votes are a VoicePoll (Backend.voicePolls, VoiceEvent 'poll'). */
  | { id: number; kind: 'poll'; sender: Profile; fromMe: boolean; pollId: number; question: string; options: string[]; at: number }

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
  /** Null: no audio connection (nobody is on the stage, so the room is a chat room for now). */
  | { type: 'audio'; audio: VoiceAudioState | null }
  /** Audio level 0..1 per user id, several times a second (absent = silent). */
  | { type: 'levels'; levels: Record<string, number> }
  | { type: 'line'; line: VoiceLine }
  | { type: 'reaction'; userId: string; kind: VoiceReaction }
  /** A poll changed (a vote, closed): counts and totals; `mine` is null (keep my own). */
  | { type: 'poll'; poll: VoicePoll }
  /** The room was read again after a gap: polls shown may have missed changes meanwhile. */
  | { type: 'resync' }
  | { type: 'ended' }
  | { type: 'removed' }
  /** The server no longer lets me be here (the room filled while I was away, I was banned, ...): `code` says why. */
  | { type: 'refused'; code: string }
  /** Left on my own after 30 minutes in the background as a listener. */
  | { type: 'idle' }
  /** Out of the room because I'm in a random chat now (the server leaves rooms for it): no message needed. */
  | { type: 'left' }

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
  // music: anyone adds a song request and votes; the host (DJ) plays, removes and controls
  /** `title`: the video's real title (lib/youtube.ts videoTitle), empty when it couldn't be looked up. */
  | { type: 'addTrack'; videoId: string; title?: string }
  | { type: 'vote'; itemId: string; on: boolean }
  | { type: 'play'; itemId: string }
  | { type: 'removeItem'; itemId: string }
  | { type: 'pause' }
  | { type: 'resume' }
  /** The most voted request plays. `trackId`: the track being skipped (a player that ended); nothing happens if another one already plays. */
  | { type: 'skip'; trackId?: string }

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
  /** A vote on a poll that has closed. */
  | 'poll_closed'
  /** Input the server refused (a poll with repeated options, an empty line ...). */
  | 'bad_request'
  /** The photo looked like nudity on this device's check: it was not sent. */
  | 'nude_photo'

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
  /** Live rooms, most people first; `query` narrows them on the server. */
  voiceRooms(query?: RoomQuery): Promise<VoiceRoomSummary[]>
  /** Opens a room with me as host, not on the stage yet (ending my previous one). */
  createVoiceRoom(r: NewVoiceRoom): Promise<{ id: string; code: string }>
  /** Enters the room with this code (private rooms are only reachable this way); returns its id. */
  joinVoiceCode(code: string): Promise<string>
  /** Host only: the title, the pinned announcement (empty removes it) and the language. */
  updateVoiceRoom(roomId: string, patch: { title: string; announcement: string; lang?: string }): Promise<void>
  /** Tells a friend about the room I'm in (their inbox, and a push when phones get them). */
  inviteToRoom(roomId: string, userId: string): Promise<void>
  /** Opens an invite: lets me into the room even when it is private; returns its id (then joinVoice). */
  joinInvite(inboxId: number): Promise<string>
  /** People with the app open right now (a server count, at most 10 s old). */
  onlineCount(): Promise<number>
  // ---- inbox and badges ----
  inbox(before?: number): Promise<InboxItem[]>
  /** Marks these items (or all of them) read. */
  markInboxRead(ids?: number[]): Promise<void>
  /** Unread counts; also tells the server I have the app open (no pushes meanwhile). */
  badges(): Promise<Badges>
  /** Called whenever an inbox item of mine arrives or changes. Returns the stop function. */
  inboxSignals(on: () => void): () => void
  // ---- profile extras ----
  setInterests(tags: Interest[]): Promise<Profile>
  /** Adds a photo to my gallery (the screen has run classifyImage() on it first). */
  addProfilePhoto(photo: File): Promise<Profile>
  removeProfilePhoto(id: string): Promise<Profile>
  /** Enters as a listener (or as the host of my own room). */
  joinVoice(roomId: string): Promise<VoiceRoom>
  leaveVoice(roomId: string): Promise<void>
  /** Host only: everyone is moved out and the room closes. */
  endVoice(roomId: string): Promise<void>
  voiceLines(roomId: string): Promise<VoiceLine[]>
  subscribeVoice(roomId: string, on: (e: VoiceEvent) => void): () => void
  /** `replyTo`: the line this one answers (voice_send2); its quote comes back in the line's `reply`. */
  sendVoice(roomId: string, text: string, replyTo?: number | null): Promise<VoiceLine>
  /** Shrinks the photo to 1280 px JPEG and sends it (the screen has run classifyImage() on it first). */
  sendVoicePhoto(roomId: string, photo: File): Promise<VoiceLine>
  /** Posts a conversation starter (one per room every 20 s: 'slow_down'). */
  voiceIcebreaker(roomId: string): Promise<VoiceLine>
  /** Host or co-host: asks the room (2-4 distinct options); closes the room's open poll. Returns the poll's line. */
  voicePollCreate(roomId: string, question: string, options: string[]): Promise<VoiceLine>
  /** My vote (0-based), or a new one in place of it; refused with 'poll_closed' once it has closed. */
  voicePollVote(pollId: number, choice: number): Promise<VoicePoll>
  /** The host, a co-host or who asked closes it. */
  voicePollClose(pollId: number): Promise<VoicePoll>
  /** Polls by id (at most 50), with my votes: for poll lines from the history. */
  voicePolls(ids: number[]): Promise<VoicePoll[]>
  /** Throws BackendError('not_allowed') when my role may not do it. */
  voiceAction(roomId: string, action: VoiceAction): Promise<void>
  /** Starts the room's audio after a tap, when the browser blocked it (VoiceAudioState.needsTap). */
  voiceStartAudio(roomId: string): Promise<void>
  reportVoiceRoom(roomId: string, reason: ReportReason): Promise<void>
}
