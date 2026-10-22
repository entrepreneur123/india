// Live audio for a voice room: LiveKit room "voice-<id>" (token from the voice-token edge function). Everyone hears
// the people on the stage; I send my microphone only while I'm on a seat and unmuted. Loaded only when a voice room
// opens (the SDK is a separate chunk, shared with video calls).
import {
  AudioPresets,
  createAudioAnalyser,
  DisconnectReason,
  type LocalAudioTrack,
  type Participant,
  type RemoteAudioTrack,
  type RemoteTrack,
  Room,
  RoomEvent,
  Track,
} from 'livekit-client'
import { isAppleWebKit } from './rtc'

export type AudioStatus = 'connecting' | 'connected' | 'reconnecting' | 'failed'

export interface VoiceAudioEvents {
  status(s: AudioStatus): void
  /** The browser blocked playback until the user touches the page. */
  blocked(needsTap: boolean): void
  /** Audio level 0..1 per identity (user id), about 10 times a second; silent people are left out. */
  levels(levels: Record<string, number>): void
  /** The server changed what I may do (voice-sync after I was moved to or from a seat). */
  permissions(canPublish: boolean): void
  /** A host muted my microphone on the server. */
  micMutedByServer(): void
  /** Disconnected for good: removed from the room, the room ended, or the connection was lost. */
  gone(removed: boolean): void
}

export interface VoiceAudio {
  readonly canPublish: boolean
  /** Throws 'mic_denied' (no permission or no microphone) or 'not_allowed' (not on a seat yet). */
  setMic(on: boolean): Promise<void>
  startAudio(): Promise<void>
  disconnect(): Promise<void>
}

const LEVEL_MS = 100

export async function joinVoiceAudio(url: string, token: string, ev: VoiceAudioEvents, signal: AbortSignal): Promise<VoiceAudio> {
  if (signal.aborted) throw new DOMException('left', 'AbortError')
  const room = new Room({
    adaptiveStream: false,
    dynacast: false,
    audioCaptureDefaults: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    // Speech: Opus mono capped at 24 kbps (a listener on 4G downloads one stream per speaker), DTX so silence costs
    // nothing, RED so a lost packet is covered by the next one.
    publishDefaults: { audioPreset: AudioPresets.speech, dtx: true, red: true, forceStereo: false },
  })
  // createAudioAnalyser opens a new AudioContext per track. On iPhone/iPad that context (44.1 kHz next to WebRTC's
  // 48 kHz, and one per speaker) can make the whole room crackle or sound metallic, and on Android phones it opens a
  // second audio stream beside the call's. There the rings use LiveKit's own speaker levels only.
  const analysers = !isAppleWebKit(navigator.userAgent, navigator.maxTouchPoints) && !/Android/i.test(navigator.userAgent)
  let leaving = false
  // Hidden <audio> elements, one per remote track; and an analyser per track for the speaking rings.
  const box = document.createElement('div')
  box.hidden = true
  document.body.appendChild(box)
  const meters = new Map<string, { identity: string; volume: () => number; cleanup: () => Promise<void> }>()
  let micWanted = false
  let timer: ReturnType<typeof setInterval> | undefined

  const meter = (sid: string, identity: string, track: RemoteAudioTrack | LocalAudioTrack) => {
    if (!analysers || meters.has(sid)) return
    try {
      const a = createAudioAnalyser(track, { cloneTrack: true })
      meters.set(sid, { identity, volume: a.calculateVolume, cleanup: a.cleanup })
    } catch {
      /* no Web Audio here: the server's speaker levels still drive the rings */
    }
  }
  const unmeter = (sid: string) => {
    void meters.get(sid)?.cleanup().catch(() => undefined)
    meters.delete(sid)
  }
  const teardown = () => {
    clearInterval(timer)
    for (const sid of [...meters.keys()]) unmeter(sid)
    box.remove()
  }
  const abort = () => {
    leaving = true
    teardown()
    void room.disconnect()
  }
  signal.addEventListener('abort', abort)

  room
    .on(RoomEvent.TrackSubscribed, (track: RemoteTrack, pub, p) => {
      if (track.kind !== Track.Kind.Audio) return
      box.appendChild(track.attach())
      meter(pub.trackSid, p.identity, track as RemoteAudioTrack)
    })
    .on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack, pub) => {
      track.detach().forEach((el) => el.remove())
      unmeter(pub.trackSid)
    })
    .on(RoomEvent.LocalTrackPublished, (pub) => {
      if (pub.track && pub.kind === Track.Kind.Audio) meter(pub.trackSid, room.localParticipant.identity, pub.track as LocalAudioTrack)
    })
    .on(RoomEvent.LocalTrackUnpublished, (pub) => unmeter(pub.trackSid))
    .on(RoomEvent.AudioPlaybackStatusChanged, () => ev.blocked(!room.canPlaybackAudio))
    .on(RoomEvent.ParticipantPermissionsChanged, (_prev, p: Participant) => {
      if (p === room.localParticipant) ev.permissions(!!p.permissions?.canPublish)
    })
    .on(RoomEvent.TrackMuted, (pub, p: Participant) => {
      // My own mute arrives here too; only a mute while I wanted to talk (the server's) is news.
      if (p !== room.localParticipant || pub.source !== Track.Source.Microphone || !micWanted) return
      micWanted = false
      ev.micMutedByServer()
    })
    .on(RoomEvent.Reconnecting, () => ev.status('reconnecting'))
    .on(RoomEvent.Reconnected, () => ev.status('connected'))
    .on(RoomEvent.Disconnected, (reason?: DisconnectReason) => {
      if (leaving) return
      leaving = true
      teardown()
      ev.status('failed')
      ev.gone(reason === DisconnectReason.PARTICIPANT_REMOVED || reason === DisconnectReason.ROOM_DELETED)
    })

  // Speaking rings: my analysers where they run, the server's speaker levels otherwise.
  timer = setInterval(() => {
    const levels: Record<string, number> = {}
    const all: Participant[] = [room.localParticipant, ...room.remoteParticipants.values()]
    for (const p of all) if (p.audioLevel > 0.02) levels[p.identity] = Math.min(1, p.audioLevel)
    for (const m of meters.values()) {
      const v = Math.min(1, m.volume() * 2.5)
      if (v > 0.02 && v > (levels[m.identity] ?? 0)) levels[m.identity] = Math.round(v * 100) / 100
    }
    ev.levels(levels)
  }, LEVEL_MS)

  ev.status('connecting')
  try {
    await room.connect(url, token, { autoSubscribe: true })
  } catch (e) {
    if (!leaving) {
      leaving = true
      teardown()
      ev.status('failed')
    }
    throw e
  }
  if (signal.aborted) throw new DOMException('left', 'AbortError')
  // Tracks published before I joined.
  room.remoteParticipants.forEach((p) =>
    p.audioTrackPublications.forEach((pub) => {
      if (!pub.track) return
      box.appendChild(pub.track.attach())
      meter(pub.trackSid, p.identity, pub.track as RemoteAudioTrack)
    }),
  )
  ev.status('connected')
  ev.blocked(!room.canPlaybackAudio)

  return {
    get canPublish() {
      return !!room.localParticipant.permissions?.canPublish
    },
    setMic: async (on) => {
      micWanted = on
      if (on && !room.localParticipant.permissions?.canPublish) throw new Error('not_allowed')
      try {
        await room.localParticipant.setMicrophoneEnabled(on)
      } catch (e) {
        micWanted = false
        const name = (e as { name?: string })?.name ?? ''
        // The browser or the person said no, or there is no microphone at all.
        if (['NotAllowedError', 'NotFoundError', 'NotReadableError', 'SecurityError', 'OverconstrainedError'].includes(name)) {
          throw new Error('mic_denied')
        }
        throw new Error('not_allowed')
      }
    },
    startAudio: () => room.startAudio(),
    disconnect: async () => {
      if (leaving) return
      leaving = true
      teardown()
      await room.disconnect()
    },
  }
}
