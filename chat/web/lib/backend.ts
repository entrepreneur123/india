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
}
