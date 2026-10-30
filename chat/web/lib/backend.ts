import type { Contacts } from './contacts'
import type { HandleCheck } from './handle'

export type Gender = 'male' | 'female' | 'other'
export type Target = 'any' | 'male' | 'female' // anything but 'any' is paid

export interface Post {
  id: string
  text: string
  imageUrl?: string | null
  createdAt: number
  /** 033: who sees it, its counts and whether I liked it (absent on posts read the old way). */
  visibility?: PostVisibility
  likes?: number
  comments?: number
  liked?: boolean
}

// ---- profile style, posts, likes and comments (033_profiles_feed.sql; lists and limits in lib/profile-style.ts) ----

/** The profile's colour (null: the brand's). */
export type Accent = 'rose' | 'violet' | 'ocean' | 'mint' | 'sunset' | 'gold' | 'berry' | 'slate'
/** Languages someone speaks (not the app's language). */
export type SpokenLang = 'en' | 'hi' | 'bn' | 'ta' | 'te' | 'mr' | 'gu' | 'kn' | 'ml' | 'pa' | 'ur' | 'or' | 'as' | 'pt' | 'vi'
/** The questions a profile can answer (the apps write the question text). */
export type PromptQ = 'weekend' | 'food' | 'song' | 'travel' | 'talk' | 'laugh' | 'goal' | 'secret'
/** 071: the 16 MBTI types (lib/profile-style.ts MBTI_TYPES, the server's check). */
export type Mbti = `${'E' | 'I'}${'S' | 'N'}${'T' | 'F'}${'J' | 'P'}`

export interface ProfilePrompt {
  q: PromptQ
  a: string
}

export interface ProfileStyle {
  /** 040: the server keeps no colour any more (frames are the decoration): null, the brand's. */
  accent: Accent | null
  /** A short line under the name (1-60), like WhatsApp's About. */
  status: string | null
  languages: SpokenLang[]
  prompts: ProfilePrompt[]
  mbti: Mbti | null
}

/** What set_profile_style2 takes: the whole style every time (040: the cover and colour are gone). */
export interface ProfileStyleInput {
  status: string | null
  languages: SpokenLang[]
  prompts: ProfilePrompt[]
  mbti: Mbti | null
}

/** 'friends': friends and people I chatted with (the default); 'public': anyone who opens the profile. */
export type PostVisibility = 'friends' | 'public'

export interface PostAuthor {
  id: string
  name: string
  gender: Gender
  age: number
  city: string | null
  country: string | null
  photoUrl: string | null
  accent: Accent | null
  /** 035: the author's identity is verified (absent: an older server). */
  verified?: boolean
}

/** A post as the server reads it out: with its author, counts and my like. */
export interface FeedPost extends Post {
  visibility: PostVisibility
  likes: number
  comments: number
  liked: boolean
  author: PostAuthor
  /** The server's own time text: the next page starts before it (exact to the microsecond). */
  cursor: string
}

export interface PostComment {
  id: number
  postId: string
  text: string
  at: number
  /** The comment this one answers, and its author's name. */
  replyTo: number | null
  replyName: string | null
  mine: boolean
  /** Mine, or a comment on my post. */
  canRemove: boolean
  author: PostAuthor
}

export interface Profile {
  id: string
  /** Mine only (059): people who aren't my friends may message me. */
  dmEveryone?: boolean
  /** Mine only (067): friends see me offline and can't call me; I'm left out of Explore's online list. */
  hideOnline?: boolean
  /** Mine only: the main photo's file, so it can be picked for the photo gradient (062). */
  photoPath?: string | null
  name: string
  gender: Gender
  age: number
  city: string
  bio: string
  photoUrl?: string | null
  country?: string | null /* ISO-2, show flag emoji */
  posts: Post[]
  /** Up to 4 more photos (id = the stored file). */
  photos?: ProfilePhoto[]
  /** Cover, colour, status, languages and prompts (absent: not loaded for this card). */
  style?: ProfileStyle
  /** 035: identity verified (absent: not known for this card; the screens ask trust()). */
  verified?: boolean
  /** 040: the ID, shown as @handle (absent: not on this card; components/looks/look-name.tsx asks looks()). */
  handle?: string
}

// ---- real identities and manners (035_identity_manners.sql; parsing and levels in lib/trust.ts) ----

/** The kyc function's answer: verified, still being checked, or why not. 'none': no check started. */
export type KycResult = 'verified' | 'pending' | 'declined' | 'unreadable' | 'minor' | 'duplicate' | 'none'

/** Someone's badge and manners: manner 0-100 (starts at 50), ratings in the last 90 days, top good tags. */
export interface Trust {
  id: string
  verified: boolean
  manner: number
  ratings: number
  tags: { tag: 'kind' | 'funny' | 'respectful' | 'good_listener' | 'interesting'; n: number }[]
}

export interface MyTrust extends Trust {
  /** Verification is required to chat, enter rooms, post, comment and ask for friendship ('verify_required'). */
  required: boolean
  /** My latest check (null: never started). */
  kyc: { status: string; result: KycResult | null; at: number } | null
  /** Random chats (match ids) I rated in the last day. */
  rated: string[]
}

// ---- IDs and looks (040_handles_looks.sql; the catalogue and parsing in lib/looks.ts, the ID rules in lib/handle.ts) ----

export type LookKind = 'badge' | 'color' | 'font' | 'frame'
/** Everything the shop sells: the looks, and (046) chat walls and VIP incognito, (062) the photo gradient and bubbles (name effects removed 2026-10-28). */
export type ItemKind = LookKind | 'wall' | 'vip' | 'cycle' | 'bubble'

/** Someone's ID and what they wear (null: none of that kind). Item ids are the catalogue's (lib/looks.ts). */
export interface Look {
  id: string
  handle: string
  badge: string | null
  color: string | null
  font: string | null
  frame: string | null
  /** 062: 2–4 photos that fade one into the next wherever this person's photo shows (a look bought once). */
  cycle?: string[]
  /** 062: the look of their chat bubbles ('bubble_…'), or none. */
  bubble?: string | null
  /** 067: their chat wall, which their partner in a random chat may use for that chat (36). */
  wall?: string | null
}

/** What I wear: one item of each kind at most. */
export type LookWear = Pick<Look, LookKind>

export interface ShopItem {
  id: string
  kind: ItemKind
  /** Google Play's local price is shown in the app; this is the fallback label. */
  priceInr: number
}

export interface Shop {
  items: ShopItem[]
  owned: string[]
  look: Look | null
  /** When I may change my ID to another one again for free (null: now). */
  handleNext: number | null
  /** Paid ID changes I have (042): each lets one change through before handleNext. */
  handlePasses: number
  /** 046: my chat wall (null: the plain one) and whether VIP incognito is on. */
  wall: string | null
  anon: boolean
}

/** The demo's identity check ends as chosen here (the live server's is Didit's hosted page). */
export type KycDemoOutcome = Exclude<KycResult, 'none'>

/** Profile photos besides the main one (094; the owner, 2026-10-28: up to 10). */
export const MAX_PHOTOS = 10

export interface ProfilePhoto {
  id: string
  url: string
}

/** 057: a titled album on the profile (up to MAX_HIGHLIGHTS, each 1–MAX_HIGHLIGHT_PHOTOS photos). */
export interface Highlight {
  id: string
  title: string
  photos: ProfilePhoto[]
}
export const MAX_HIGHLIGHTS = 10
export const MAX_HIGHLIGHT_PHOTOS = 10
export const HIGHLIGHT_TITLE_MAX = 20

// ---- notifications (023_notifications.sql) ----

/** 'call' (053): a friend asked me for a call (read once it is answered, declined or over). 'gift' (066): a look from a friend. */
export type InboxKind = 'message' | 'friend_request' | 'friend_accept' | 'room_invite' | 'post_like' | 'post_comment' | 'call' | 'gift'

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
  /** The post a like or comment is about (`text`: its first 80 characters). */
  post: { id: string; text: string; imageUrl: string | null } | null
  /** A gift's look (066). */
  item?: string | null
}

/** A friend calling me right now (053): their thread, and a voice (`video` false) or a video call. */
export interface IncomingCall {
  matchId: string
  caller: Profile
  video: boolean
}

export interface Badges {
  /** Unread inbox items. */
  inbox: number
  /** Friend requests waiting for me. */
  friends: number
  /** Unread messages per chat (match id). */
  chats: Record<string, number>
}

/**
 * Room search (28): title words, the room's language (an app language), the hosts' countries (empty = every country)
 * and whether rooms with a password show; empty = everything.
 */
/** 072: the room list's order — most people, best for me (my interests, language, country, the search), or busiest. */
export type RoomSort = 'members' | 'relevant' | 'popular'

export interface RoomQuery {
  /** A title, or "#tag" for rooms with that hashtag. */
  text?: string
  lang?: string | null
  countries?: string[]
  locked?: boolean
  sort?: RoomSort
}

export type MediaKind = 'image' | 'video' | 'audio'
/** A chat file's kind from its type (099: voice messages are 'audio'). */
export const mediaKindOf = (type: string): MediaKind => (type.startsWith('video/') ? 'video' : type.startsWith('audio/') ? 'audio' : 'image')

export interface Message {
  id: number
  fromMe: boolean
  text?: string | null
  media?: { url: string; kind: MediaKind } | null
  at: number
  /** The name the sending app gave the line (091): its "sending…" copy and the stored one are matched by it. */
  clientId?: string | null
  /** Mine, not stored yet: shown at once (local echo), then replaced by the stored line or left to be sent again. */
  state?: 'sending' | 'failed'
}

export interface RecentMatch {
  matchId: string
  partner: Profile
  metAt: number
  lastText?: string | null
}

export type ReportReason = 'nudity' | 'minor' | 'harassment' | 'fake' | 'other'
export type FriendState = 'none' | 'requested' | 'incoming' | 'friends'

/** 059 Explore: 'recommend' 50 at random, 'online', 'likes' / 'friends' rankings (count), 'liked' (whom I liked). */
export type ExploreKind = 'recommend' | 'online' | 'likes' | 'friends' | 'liked'
export interface ExplorePerson extends PersonRow {
  age: number
  country: string | null
  /** Rankings: the profile likes or friends. */
  count: number | null
}
/** A friend's post or highlight in the Explore feed. */
export interface FeedItem {
  kind: 'post' | 'highlight'
  id: string
  user: PersonRow
  text: string
  photos: string[]
  at: string
  /** A post in full (081): it shows as a post, liked and opened there. */
  post?: FeedPost | null
}
/** A profile's likes and friends, whether I like it and whether I may message it. */
export interface ProfileSocial {
  likes: number
  friends: number
  liked: boolean
  canMessage: boolean
}

/** 042: someone found by ID, or on my block list. */
/** 100: a megaphone line on top of everyone's screen in one country. */
export interface MegaphoneLine {
  id: number
  user: PersonRow
  text: string
  at: number
}

export interface PersonRow {
  id: string
  handle: string
  name: string
  photoUrl: string | null
}

/** 49 (076): a group's party search — who said "I'm in", whether it is searching, and the chat it was paired into. */
export interface PartyState {
  ready: string[]
  searching: boolean
  matched: string | null
}

/** 52 (075): a "looking for…" line in Explore, open for 24 hours to people of its country. */
export interface LookingPost {
  id: number
  text: string
  at: number
  expiresAt: number
  mine: boolean
  author: Profile
}
export const LOOKING_MAX = 120

/** 069: a group chat's lines: messages, and the server's notes ('created' / 'added': names, 'renamed': the title). */
export type GroupLineKind = 'text' | 'created' | 'added' | 'left' | 'renamed'
export interface GroupLine {
  id: number
  senderId: string | null
  kind: GroupLineKind
  body: string
  at: number
  /** As Message's (092): the name my app gave the line, and its state while not stored yet. */
  clientId?: string | null
  state?: 'sending' | 'failed'
}
/** 48: a group chat among friends (069). */
export interface GroupChat {
  id: string
  title: string
  members: Profile[]
  last: Pick<GroupLine, 'kind' | 'body' | 'senderId'> | null
  lastAt: number
  unread: number
}

export interface Friend {
  profile: Profile
  state: FriendState
  /** The pair's message thread (083), once there is one. */
  threadId?: string | null
  /** 103: the thread's last line (Recent shows it as a chat list does). */
  last?: { text: string | null; kind: MediaKind | null; mine: boolean; at: number } | null
}

/** 066: what a friend is doing now (League of Legends' friend list): app closed, idle, searching, in a random chat or a room. */
export type Presence = 'offline' | 'online' | 'matching' | 'chat' | 'room'

/** 066: a friend's presence and status message, with when it last changed (ms; null: never or none). */
export interface FriendActivity {
  id: string
  presence: Presence
  status: string | null
  statusAt: number | null
}

/** 066 gift_check: whether a friend can get this look ('owned': they have it; 'not_friends'; 'bad_item'). */
export type GiftCheck = 'ok' | 'owned' | 'not_friends' | 'bad_item'

/** 044: the contacts I keep, and the friends I show them to. */
export interface MyContacts {
  contacts: Contacts
  sharedWith: string[]
}

/** 044: a friend's side: their contacts when they show them to me (null: they don't), and whether I show mine. */
export interface FriendContacts {
  contacts: Contacts | null
  mineShared: boolean
}

/** Topics the room glyphs are drawn for (components/ui/topic-icons.tsx). */
export type RoomCategory = 'chat' | 'friends' | 'language' | 'music' | 'movies' | 'gaming' | 'sports' | 'study' | 'travel' | 'night'
/** Why I can't enter a room (null/absent = I can). */
export type RoomRefusal = 'room_closed' | 'room_gender' | 'room_age' | 'room_full' | 'not_allowed' | 'room_password' | 'room_private'

// ---- rooms (019_unified_rooms.sql): one kind of room. Every room has the chat (text, photos, polls), a
// stage of seats with an audience and shared music. No categories (28): a room is its host's country. ----

/**
 * Co-hosts (036) can invite, move and mute people, run the music and pin a page; they remove listeners and speakers
 * (not the host, not another co-host). Only the host makes co-hosts and ends the room.
 */
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
  /** 045: their connection dropped; the server takes them out 7 s later unless they come back. */
  lost?: boolean
  /** When they came in (server ms): the earliest co-host takes over from a host who leaves. */
  joinedAt?: number
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
  /** 072: the room's cover photo (null: none) and its hashtags (up to 5, without "#"). */
  coverUrl: string | null
  tags: string[]
}

export interface VoiceRoom extends RoomRules {
  id: string
  title: string
  mode: SpeakMode
  hostId: string
  meId: string
  members: VoiceMember[]
  seats: VoiceSeat[]
  track: VoiceTrack | null
  queue: VoiceQueueItem[]
  /** The notice the host or a co-host pinned at the top (27). */
  announcement: string | null
  /** The room's 6-character code, for its host only. */
  code: string | null
  /** 058: the room's password, for its host only (null: none). */
  password: string | null
  lang: string | null
  /** 036: the web page the host or a co-host shows everyone next to the chat (null: none). */
  stage: VoiceStage | null
  /** 068 (46): false — a chat-only room (no stage, nobody talks); chosen when it's made. */
  voice: boolean
  /** 068 (45): the wall the host or a co-host dressed the room in (one they own), or null. */
  theme: string | null
  /** 072: the cover photo (the stored file, and a link to show it) and the hashtags; the host or a co-host sets them. */
  coverPath: string | null
  coverUrl: string | null
  tags: string[]
}

/** A page pinned for the whole room (voice_share_link). The same page for everyone; scrolling is not shared. */
export interface VoiceStage {
  url: string
  title: string | null
  /** Who shared it: name and user id. */
  by: string
  byId: string
  /** When, on this device's clock. */
  at: number
}

/** A link's preview (edge function link-meta). `frameable`: the site lets other apps show it inside a frame. */
export interface LinkMeta {
  url: string
  title: string | null
  description: string | null
  image: string | null
  site: string | null
  frameable: boolean
}

export interface NewVoiceRoom extends RoomRules {
  title: string
  /** 46: false makes a chat-only room. */
  voice: boolean
  announcement: string
  /** The language the room speaks (the app language by default). */
  lang: string
  /** 072: a cover photo and hashtags, set right after the room is made. */
  cover?: File | null
  tags?: string[]
}

/** Reactions are drawn glyphs (components/ui/topic-icons.tsx), never emoji. */
export type VoiceReaction = 'heart' | 'laugh' | 'fire' | 'clap' | 'wow'

/** 'cleared' (074): shown only on screen, where the chat was emptied by the host or a co-host. */
export type VoiceSystemEvent = 'joined' | 'left' | 'seat' | 'audience' | 'cohost' | 'host' | 'track' | 'removed' | 'notice' | 'cleared'

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
  /** A photo (checked on the sender's device; shown blurred until tapped). */
  | { id: number; kind: 'photo'; sender: Profile; fromMe: boolean; url: string; at: number }
  /** A poll's place in the chat; its votes are a VoicePoll (Backend.voicePolls, VoiceEvent 'poll'). */
  | { id: number; kind: 'poll'; sender: Profile; fromMe: boolean; pollId: number; question: string; options: string[]; at: number }

/**
 * A room's read marks (104): the highest line read (`hi`) and by whom (`by`), and the highest read by anyone else
 * (`hi2`). Both only grow (lib/voice-chat.ts reads them).
 */
export interface VoiceReads {
  hi: number
  by: string | null
  hi2: number
}

/**
 * The room's live audio (live server only; the demo has none). `needsTap`: the browser blocked playback until
 * the user touches the page (autoplay rules).
 */
export interface VoiceAudioState {
  status: 'connecting' | 'connected' | 'reconnecting' | 'failed'
  needsTap: boolean
  /** A hint about my microphone: talking while muted, or it sends only silence (lib/voice-audio.ts). */
  micHint?: 'talkingMuted' | 'silent' | null
}

/** A camera on in a voice room (24): shown by attaching it to a <video>; `mine` is drawn mirrored. */
export interface VoiceVideo {
  mine: boolean
  attach(el: HTMLVideoElement): void
  detach(el: HTMLVideoElement): void
}

export type VoiceEvent =
  | { type: 'room'; room: VoiceRoom }
  /** The cameras on now, by user id (people on a seat only). */
  | { type: 'videos'; videos: Record<string, VoiceVideo> }
  /** Null: no audio connection (nobody is on the stage, so the room is a chat room for now). */
  | { type: 'audio'; audio: VoiceAudioState | null }
  /** Audio level 0..1 per user id, several times a second (absent = silent). */
  | { type: 'levels'; levels: Record<string, number> }
  | { type: 'line'; line: VoiceLine }
  /** 50 (074): lines the host or a co-host hid, and the whole chat cleared (by whom). */
  | { type: 'hidden'; ids: number[] }
  | { type: 'cleared'; by: string }
  | { type: 'reaction'; userId: string; kind: VoiceReaction }
  /** A poll changed (a vote, closed): counts and totals; `mine` is null (keep my own). */
  | { type: 'poll'; poll: VoicePoll }
  /** The room's read marks moved (104). */
  | { type: 'read'; reads: VoiceReads }
  /** The room was read again after a gap: polls shown may have missed changes meanwhile. */
  | { type: 'resync' }
  | { type: 'ended' }
  | { type: 'removed' }
  /** The server no longer lets me be here (the room filled while I was away, I was banned, ...): `code` says why. */
  | { type: 'refused'; code: string }
  /** Left on my own after 30 minutes in the background as a listener. */
  | { type: 'idle' }
  /** My own connection: heartbeats failing or realtime down (true) and back (false). */
  | { type: 'connection'; lost: boolean }
  /** Back after more than 13 s away: the server had already taken me out, so the room closes. */
  | { type: 'dropped' }
  /** Out of the room because I'm in a random chat now (the server leaves rooms for it): no message needed. */
  | { type: 'left' }
  /** Someone in the room is writing in the chat (public 'vtyping-<room>' channel; nothing is stored). Never me. */
  | { type: 'typing'; userId: string; name: string }

export type VoiceAction =
  | { type: 'hand'; up: boolean }
  | { type: 'mic'; on: boolean }
  /** My camera, on a seat only (24). Throws 'camera_denied', 'camera_missing' or 'camera_busy'. */
  | { type: 'camera'; on: boolean }
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
  /** 036: the host removes anyone; a co-host removes listeners and speakers only. */
  | { type: 'kick'; userId: string }
  /** 036: shows a web page (https) to the whole room next to the chat; null takes it down. */
  | { type: 'shareLink'; url: string | null; title?: string | null }
  // host only
  | { type: 'cohost'; userId: string; on: boolean }
  /** 079: the host hands the room to a co-host; they swap places. */
  | { type: 'makeHost'; userId: string }
  // music: anyone adds a song request and votes; the host and co-hosts (036) play, remove, seek and control
  /** `title`: the video's real title (lib/youtube.ts videoTitle), empty when it couldn't be looked up. */
  | { type: 'addTrack'; videoId: string; title?: string }
  | { type: 'vote'; itemId: string; on: boolean }
  | { type: 'play'; itemId: string }
  | { type: 'removeItem'; itemId: string }
  | { type: 'pause' }
  | { type: 'resume' }
  /** The most voted request plays. `trackId`: the track being skipped (a player that ended); nothing happens if another one already plays. */
  | { type: 'skip'; trackId?: string }
  /** 054: the music off for everyone; the requests stay. */
  | { type: 'stop' }
  /** 036: jump to this position (ms) in the song for everyone; a paused song stays paused there. */
  | { type: 'seek'; ms: number }

export interface Match {
  id: string
  partner: Profile
}

export type VideoStatus = 'none' | 'requested_by_me' | 'requested_by_partner' | 'accepted' | 'declined'

export type MatchEvent =
  | { type: 'message'; message: Message }
  | { type: 'typing'; on: boolean }
  /** A call (051): `camera` false = a voice call (the camera starts off; either side can turn it on). */
  | { type: 'video'; status: VideoStatus; camera: boolean }
  | { type: 'partner_left' }
  | { type: 'read'; upto: number } // the partner has read my messages up to this id
  /** 35: the partner turned VIP off or on: their card as I may see it now. */
  | { type: 'partner'; partner: Profile }
  /** The chat has ended (either side). 040: after that only friends write on (threads show the friends bar). */
  | { type: 'ended' }

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
  /** 067: a call to a friend who isn't online (or hides it). */
  | 'offline'
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
  /** 058: the room has a password I haven't given yet (unlockRoom). */
  | 'room_password'
  | 'room_title'
  | 'room_size'
  | 'voice_removed'
  | 'mic_denied'
  | 'camera_denied'
  | 'camera_missing'
  | 'camera_busy'
  | 'queue_full'
  /** A vote on a poll that has closed. */
  | 'poll_closed'
  /** Input the server refused (a poll with repeated options, an empty line ...). */
  | 'bad_request'
  /** The photo looked like nudity on this device's check: it was not sent. */
  | 'nude_photo'
  /** 035: verification is required for this (the verification sheet opens instead of a message). */
  | 'verify_required'
  /** 035: the manner score is below 20, so random chat is closed. */
  | 'manner_low'
  /** 035: the identity check has no provider yet (kyc 503). */
  | 'kyc_unavailable'
  | 'already_verified'
  | 'kyc_failed'
  /** 035 ratings: more than a day after the chat, or rated already. */
  | 'too_late'
  | 'already_rated'
  /** 040 IDs: not an ID the server takes, kept for staff, someone else's, or changed less than 30 days ago without a paid ID change. */
  | 'handle_invalid'
  | 'handle_reserved'
  | 'handle_taken'
  | 'handle_cooldown'
  /** 040: a name with fancy-font letters (𝓛𝓲𝓼𝓪) or stacked accents. */
  | 'name_invalid'
  | 'too_many_posts'
  /** 040: wearing a look I don't own (046: also a wall or incognito). */
  | 'not_owned'
  /** 040: a new line in a random chat that ended, between two people who aren't friends. */
  | 'chat_closed'
  /** 044: a contact the server won't keep (the error's detail names the app); sharing with someone who isn't a friend. */
  | 'contact_invalid'
  | 'not_friends'

export class BackendError extends Error {
  /** `detail`: what the rule was about (contact_invalid: the app). */
  constructor(public code: string, public detail?: string) {
    super(code)
    this.name = 'BackendError'
  }
}

/** What the two browsers of a 1:1 call tell each other while connecting straight (lib/p2p-call.ts). */
export type CallMsg =
  | { t: 'hi' }
  | { t: 'sdp'; sdp: RTCSessionDescriptionInit }
  | { t: 'ice'; c: RTCIceCandidateInit }
  | { t: 'cam'; on: boolean }
  /** Going through the server (LiveKit) instead: the other side follows. */
  | { t: 'relay' }
  | { t: 'bye' }
export interface CallSignal {
  me: string
  send(m: CallMsg): void
  close(): void
}

export interface Backend {
  sendOtp(phone: string): Promise<void> // digits incl. country code, e.g. "919876543210"
  verifyOtp(phone: string, code: string): Promise<boolean>
  isSignedIn(): Promise<boolean>
  myProfile(): Promise<Profile | null>
  createProfile(p: NewProfile): Promise<Profile>
  signOut(): Promise<void>
  deleteAccount(): Promise<void>
  /** Phone number of the signed-in account, shown in Settings. */
  myPhone(): Promise<string | null>
  /**
   * Null = still queued; the UI calls again every 2 s. `lang` (the app language) is preferred in a partner.
   * `countries` (043, free): only people from these (ISO alpha-2, at most 10; empty = anywhere) whose own choice fits me.
   */
  /** `anon` (070): meet only people who chose anonymous chat too; the partner comes back as `anon`. */
  findMatch(target: Target, lang?: string, countries?: readonly string[]): Promise<Match | null>
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
  /** Tells the partner I've read their messages up to this id (the read ticks); settles once the server has it. Never throws. */
  markRead(matchId: string, upto: number): Promise<void>
  /** The call's nudity check hid the partner's video: files an automatic report for review. Never throws. */
  flagNudity(matchId: string): Promise<void>
  /** Sending again with the same clientId never stores the line twice (091): the stored one comes back. */
  send(matchId: string, text: string | null, media: File | null, clientId: string): Promise<Message>
  leave(matchId: string): Promise<void> // throws BackendError('exit_locked') inside the lock window
  report(partnerId: string, reason: ReportReason, matchId: string | null): Promise<void>
  /** Blocks without reporting: open chats with them end and neither side sees the other again. */
  block(userId: string): Promise<void>
  /** Asks the other person for a call, free (051): `video` false = voice only. Random chats and friends' threads. */
  requestCall(matchId: string, video: boolean): Promise<void>
  answerVideo(matchId: string, accept: boolean): Promise<void>
  endVideo(matchId: string): Promise<void>
  videoToken(matchId: string): Promise<{ url: string; token: string } | null>
  /** 095: the call's private topic for the two browsers to connect straight (lib/p2p-call); null: no server (demo). */
  callSignal(matchId: string, on: (m: CallMsg) => void): Promise<CallSignal | null>
  /** The latest 50 people I met (random chats), newest first. */
  recents(): Promise<RecentMatch[]>
  /** 048: takes these people (at most 100) off my recents until we meet again. */
  hideRecents(userIds: string[]): Promise<void>
  profile(userId: string): Promise<Profile | null>
  /** `visibility`: 'friends' unless given. At most 20 a day ('slow_down'). */
  addPost(text: string, image: File | null, visibility?: PostVisibility): Promise<FeedPost>
  /** My own post only. */
  deletePost(postId: string): Promise<void>
  updateProfile(name: string, city: string, bio: string, photo: File | null): Promise<Profile>
  friends(): Promise<Friend[]>
  /** 066: my friends' presence and status messages. */
  friendActivity(): Promise<FriendActivity[]>
  requestFriend(userId: string): Promise<FriendState>
  answerFriend(userId: string, accept: boolean): Promise<void>
  removeFriend(userId: string): Promise<void>
  friendThread(userId: string): Promise<string> // a matchId to open the chat in
  /** 042: people whose ID starts with q (@ and case ignored), up to 20; found people can be viewed for a day. */
  searchHandles(q: string): Promise<PersonRow[]>
  /** `country` (ISO 2) null: mine. */
  explorePeople(kind: ExploreKind, country?: string | null): Promise<ExplorePerson[]>
  exploreFeed(): Promise<FeedItem[]>
  profileSocial(userId: string): Promise<ProfileSocial>
  likeProfile(userId: string, on: boolean): Promise<void>
  /** Messages from anyone (true) or friends only (false, the default). */
  setDmEveryone(on: boolean): Promise<Profile>
  setHideOnline(on: boolean): Promise<Profile>
  /** 062: the photos (file ids: photoPath or photos[].id) of my photo gradient, 2–4; null turns it off. */
  setCycle(paths: string[] | null): Promise<void>
  /** 062: the chat bubble I wear (an owned one), or none. */
  setWorn(kind: 'bubble', itemId: string | null): Promise<void>
  /** 042: people I blocked, newest first; unblocking does not bring back the friendship or old chats. */
  blocks(): Promise<PersonRow[]>
  unblock(userId: string): Promise<void>
  // ---- messenger contacts (044) ----
  /** Keeps these (empty ones dropped); returns them as stored. Errors: contact_invalid (detail: the app), bad_request. */
  setContacts(contacts: Contacts): Promise<Contacts>
  myContacts(): Promise<MyContacts>
  /** Shows my contacts to a friend, or stops. Error: not_friends. Unfriending or blocking stops it both ways. */
  shareContacts(userId: string, on: boolean): Promise<void>
  /** Null when we aren't friends. */
  contactsOf(userId: string): Promise<FriendContacts | null>
  /** Live rooms, most people first; `query` narrows them on the server. */
  voiceRooms(query?: RoomQuery): Promise<VoiceRoomSummary[]>
  /**
   * 072: the cover and hashtags of a room (host or co-host). `cover`: a new photo, null to remove it, undefined to keep
   * it. Errors: not_allowed, bad_request.
   */
  setRoomExtras(roomId: string, extras: { cover?: File | null; tags: string[] }): Promise<void>
  /** Opens a room with me as host, not on the stage yet (ending my previous one). */
  createVoiceRoom(r: NewVoiceRoom): Promise<{ id: string; code: string }>
  /** Enters the room with this code (private rooms are only reachable this way); returns its id. */
  joinVoiceCode(code: string): Promise<string>
  /** Host only: the title and the language. */
  updateVoiceRoom(roomId: string, patch: { title: string; lang?: string }): Promise<void>
  /** 058: gives a room's password; true lets me in from then on. Errors: room_closed, slow_down (10 misses in 10 minutes). */
  unlockRoom(roomId: string, password: string): Promise<boolean>
  /** 058, host only: the room's password (4-12 characters; null removes it). A new one asks everyone outside again. */
  setRoomPassword(roomId: string, password: string | null): Promise<void>
  /** 064, the host or a co-host: the room's pinned notice (empty removes it); the chat says who pinned a new one. */
  setRoomNotice(roomId: string, text: string): Promise<void>
  /** 45: the host or a co-host dresses the room in one of their walls (null: plain). Errors: not_owned, not_allowed. */
  setRoomTheme(roomId: string, itemId: string | null): Promise<void>

  // ---- 48 group chats among friends (069) ----
  /** My groups, latest first. */
  groups(): Promise<GroupChat[]>
  /** Friends only (others are left out; none left: not_friends). Returns the group's id. */
  createGroup(title: string, memberIds: string[]): Promise<string>
  /** 50 lines at a time, oldest first; `before`: older than this line. */
  groupHistory(groupId: string, before?: number): Promise<GroupLine[]>
  /** Sending again with the same clientId never stores the line twice (092). */
  sendGroup(groupId: string, text: string, clientId: string): Promise<GroupLine>
  readGroup(groupId: string, upto: number): Promise<void>
  addToGroup(groupId: string, memberIds: string[]): Promise<void>
  renameGroup(groupId: string, title: string): Promise<void>
  leaveGroup(groupId: string): Promise<void>
  /** New lines in these groups as they're written (mine too). Returns the unsubscribe. */
  watchGroups(groupIds: string[], onLine: (groupId: string, line: GroupLine) => void): () => void
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
  /** 088: tells my online friends at once that I came online, changed what I'm doing, or (false) closed the app. */
  presencePing(online: boolean): void
  /** 088: called whenever a friend's presence may have changed. Returns the stop function. */
  friendSignals(on: () => void): () => void
  /** 100: how many megaphones I have (-1: unlimited, 102); sending uses one (errors: no_megaphone, slow_down, bad_request). */
  megaphoneCount(): Promise<number>
  sendMegaphone(text: string): Promise<void>
  /** 100: megaphone lines in my country as they come; returns stop. */
  megaphones(on: (m: MegaphoneLine) => void): () => void
  /** 101: my country's megaphone lines of the past day, newest first (50 at most; blocked people left out). */
  megaphoneHistory(): Promise<MegaphoneLine[]>
  /** Demo only: one megaphone at once (bought in the Android app). */
  buyMegaphone?(): Promise<void>
  /** A friend's call ringing for me now (an unread 'call' line whose thread still asks), else null. */
  incomingCall(): Promise<IncomingCall | null>
  // ---- profile extras ----
  /** Adds a photo to my gallery (the screen has run classifyImage() on it first). */
  addProfilePhoto(photo: File): Promise<Profile>
  removeProfilePhoto(id: string): Promise<Profile>
  /** Adds the small copies my older profile photos lack (lib/thumbs.ts), once per browser; live server only. */
  backfillThumbs?(): Promise<void>
  highlights(userId: string): Promise<Highlight[]>
  /** `id` null: a new one. `keep`: photo ids already in it, in order; `add`: new photos (checked for nudity before). */
  saveHighlight(h: { id: string | null; title: string; keep: string[]; add: File[] }): Promise<void>
  deleteHighlight(id: string): Promise<void>
  /** Sets the whole style: status, languages, prompts. */
  setProfileStyle(style: ProfileStyleInput): Promise<Profile>
  // ---- posts, likes, comments (033) ----
  /** Newest first; `before`: the last post's cursor, for the next page. */
  postsOf(userId: string, before?: string): Promise<FeedPost[]>
  /** 072: posts I may see with this hashtag (with or without "#"), newest first, 30 at a time. */
  postsByTag(tag: string, before?: string): Promise<FeedPost[]>
  /**
   * 49 (076): a friends' group as a party. partyState is also my heartbeat ("I'm in" while `ready`, every few
   * seconds); partySearch searches with everyone in (2 or more, else party_small) and returns the new chat with the
   * other party once paired; partyCancel stops.
   */
  partyState(groupId: string, ready: boolean): Promise<PartyState>
  partySearch(groupId: string, everywhere: boolean): Promise<string | null>
  partyCancel(groupId: string): Promise<void>
  /** 52: open "looking for" lines of a country (null: mine), newest first. */
  lookingFor(country?: string | null): Promise<LookingPost[]>
  /** Errors: bad_request, slow_down (3 open or 10 a day), verify_required, banned. */
  postLooking(text: string): Promise<void>
  hideLooking(id: number): Promise<void>
  /** A message thread with the line's poster (open even if neither takes messages from everyone). Error: not_found. */
  replyLooking(id: number): Promise<string>
  /** Null: gone, or not mine to see. */
  post(postId: string): Promise<FeedPost | null>
  likePost(postId: string, on: boolean): Promise<{ likes: number; liked: boolean }>
  /** 080: who liked it, newest first (100 at most). */
  postLikers(postId: string): Promise<PersonRow[]>
  /** 098: a profile's friends, who liked them, or whom they liked (newest first, 100 at most). */
  profilePeople(userId: string, kind: 'friends' | 'likers' | 'liked'): Promise<PersonRow[]>
  /** Oldest first; `after`: the last comment's id, for more. */
  comments(postId: string, after?: number): Promise<PostComment[]>
  /** 1-300 characters, 10 a minute ('slow_down'). */
  addComment(postId: string, text: string, replyTo?: number | null): Promise<PostComment>
  /** Mine, or any on my post. */
  removeComment(commentId: number): Promise<void>
  reportPost(postId: string, reason: ReportReason): Promise<void>
  reportComment(commentId: number, reason: ReportReason): Promise<void>
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
  /** Host or co-host: asks the room (2-4 distinct options); closes the room's open poll. Returns the poll's line. */
  voicePollCreate(roomId: string, question: string, options: string[]): Promise<VoiceLine>
  /** My vote (0-based), or a new one in place of it; refused with 'poll_closed' once it has closed. */
  voicePollVote(pollId: number, choice: number): Promise<VoicePoll>
  /** The host, a co-host or who asked closes it. */
  voicePollClose(pollId: number): Promise<VoicePoll>
  /** Polls by id (at most 50), with my votes: for poll lines from the history. */
  voicePolls(ids: number[]): Promise<VoicePoll[]>
  /** I have read the room's lines up to `upto` (0: only asking); returns the room's read marks. */
  voiceRead(roomId: string, upto: number): Promise<VoiceReads>
  /** Throws BackendError('not_allowed') when my role may not do it. */
  voiceAction(roomId: string, action: VoiceAction): Promise<void>
  /** Tells the room I'm writing (fire and forget; the screen sends it at most every few seconds). */
  voiceTyping(roomId: string, name: string): void
  /** A link's title, picture and whether it can be shown in a frame (edge function link-meta). */
  linkMeta(url: string): Promise<LinkMeta>
  /** Starts the room's audio after a tap, when the browser blocked it (VoiceAudioState.needsTap). */
  voiceStartAudio(roomId: string): Promise<void>
  /** 46: the room's voices on or off for me (a room is entered with them off). */
  voiceSound(roomId: string, on: boolean): Promise<void>
  /** 50 (074): host or co-host — hide one line for everyone, or clear the whole chat. Error: not_allowed. */
  hideVoiceLine(roomId: string, lineId: number): Promise<void>
  clearVoiceChat(roomId: string): Promise<void>
  /** 51: one person's volume for me (0-1), remembered for every room (lib/voice-volumes.ts); 0 stops their voice. */
  voiceVolume(roomId: string, userId: string, volume: number): void
  reportVoiceRoom(roomId: string, reason: ReportReason): Promise<void>
  // ---- identity and manners (035) ----
  myTrust(): Promise<MyTrust>
  /** Badges and manners of these people (at most 100; people I may not see are left out). */
  trust(userIds: string[]): Promise<Trust[]>
  /**
   * Starts an identity check; returns the hosted page to send the person to (it comes back to `returnUrl`). The demo
   * returns a "demo:" address: the check is simulated in place (kycDemo). Errors: kyc_unavailable, already_verified,
   * slow_down, kyc_failed.
   */
  kycStart(returnUrl: string): Promise<string>
  /** The latest check's result (read again from the provider). */
  kycCheck(): Promise<KycResult>
  /** Demo only: how the simulated check ends. */
  kycDemo?(outcome: KycDemoOutcome): Promise<void>
  /** Once per random chat after it ends, when both wrote; `tags` from the chosen side (lib/trust.ts), at most 3. */
  rateChat(matchId: string, good: boolean, tags: string[]): Promise<void>
  // ---- IDs and looks (040) ----
  /** Whether I can take this ID (asked while typing, 400 ms after the last key). */
  handleCheck(handle: string): Promise<HandleCheck>
  /** Takes this ID; returns the stored spelling. Errors: handle_invalid, handle_reserved, handle_taken, handle_cooldown. */
  setHandle(handle: string): Promise<string>
  /** Everything for sale, what I own and wear, and when my ID can change again. */
  shop(): Promise<Shop>
  /** Wears these (null: none of that kind), all four at once. Errors: not_owned, bad_request. */
  equipLook(wear: LookWear): Promise<Look>
  /** 046: my chat wall (null: none). Errors: not_owned. */
  setWall(itemId: string | null): Promise<void>
  /** 046: VIP incognito on or off. Errors: not_owned. */
  setAnon(on: boolean): Promise<void>
  /** IDs and looks of these people (at most 100; people I may not see are left out). */
  looks(userIds: string[]): Promise<Look[]>
  /** Demo only: owns the item at once (the live web has no payments; looks are bought in the Android app). */
  buyLook?(itemId: string): Promise<void>
  /** 066: before a gift is bought, whether this friend can get it. */
  giftCheck(friendId: string, itemId: string): Promise<GiftCheck>
  /** Demo only: the friend gets the look at once (gifts are bought in the Android app). */
  giftLook?(friendId: string, itemId: string): Promise<void>
  /** 002 + 26: until when (ms) I may choose women or men (the 24-hour gender pass, like Azar's paid filter), or null. */
  genderPass(): Promise<number | null>
  /** Demo only: a pass at once (the live web has no payments; the pass is bought in the Android app). */
  buyGenderPass?(): Promise<void>
}
