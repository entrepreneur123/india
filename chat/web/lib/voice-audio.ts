// Live audio for a voice room: LiveKit room "voice-<id>" (token from the voice-token edge function). Everyone hears
// the people on the stage; I send my microphone only while I'm on a seat and unmuted. People on a seat may also turn
// their camera on (24): 360p with a 180p layer, and each viewer gets the layer its tile needs (adaptive stream).
// Loaded only when a voice room opens (the SDK is a separate chunk, shared with video calls).
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
  VideoPresets,
  type VideoTrack,
} from 'livekit-client'
import type { VoiceVideo } from './backend'
import { deviceProblem, isAppleWebKit, videoCodecFor } from './rtc'
import { cleanMicrophone } from './voice-gate'

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
  /** The cameras that are on now, by user id (mine too). */
  videos(videos: Record<string, VoiceVideo>): void
  /** Disconnected for good: removed from the room, the room ended, or the connection was lost. */
  gone(removed: boolean): void
}

export interface VoiceAudio {
  readonly canPublish: boolean
  /** Throws 'mic_denied' (no permission or no microphone) or 'not_allowed' (not on a seat yet). */
  setMic(on: boolean): Promise<void>
  /** Throws a DeviceProblem ('denied', 'missing', 'busy', 'other') or 'not_allowed' (not on a seat). */
  setCamera(on: boolean): Promise<void>
  startAudio(): Promise<void>
  /** 46: the room's voices on or off for me (they still arrive; only my speakers are silent). */
  setSound(on: boolean): void
  disconnect(): Promise<void>
}

const LEVEL_MS = 100

export async function joinVoiceAudio(url: string, token: string, ev: VoiceAudioEvents, signal: AbortSignal, sound: boolean): Promise<VoiceAudio> {
  if (signal.aborted) throw new DOMException('left', 'AbortError')
  const room = new Room({
    // Video only: each tile gets the layer its size needs, and nobody encodes a layer nobody watches.
    adaptiveStream: true,
    dynacast: true,
    audioCaptureDefaults: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    videoCaptureDefaults: { resolution: VideoPresets.h360.resolution, facingMode: 'user' },
    // Opus mono capped at 48 kbps, as in 1:1 calls (24 kbps sounded muffled next to Discord or X Spaces); DTX so
    // silence costs nothing (a listener downloads only the people talking), RED so a lost packet is covered by the next.
    publishDefaults: {
      audioPreset: AudioPresets.music, dtx: true, red: true, forceStereo: false,
      videoCodec: videoCodecFor(navigator.userAgent, navigator.maxTouchPoints), simulcast: true,
      videoEncoding: VideoPresets.h360.encoding, videoSimulcastLayers: [VideoPresets.h180],
    },
  })
  // createAudioAnalyser opens a new AudioContext per track. On iPhone/iPad that context (44.1 kHz next to WebRTC's
  // 48 kHz, and one per speaker) can make the whole room crackle or sound metallic, and on Android phones it opens a
  // second audio stream beside the call's. There the rings use LiveKit's own speaker levels only.
  const analysers = !isAppleWebKit(navigator.userAgent, navigator.maxTouchPoints) && !/Android/i.test(navigator.userAgent)
  cleanMicrophone(room)
  let leaving = false
  // Hidden <audio> elements, one per remote track; and an analyser per track for the speaking rings.
  const box = document.createElement('div')
  box.hidden = true
  document.body.appendChild(box)
  const play = (el: HTMLMediaElement) => {
    el.muted = !sound
    box.appendChild(el)
  }
  const meters = new Map<string, { identity: string; volume: () => number; cleanup: () => Promise<void> }>()
  // The cameras on now: everyone's unmuted camera track (mine mirrored, like a mirror).
  const emitVideos = () => {
    const out: Record<string, VoiceVideo> = {}
    const all: Participant[] = [room.localParticipant, ...room.remoteParticipants.values()]
    for (const p of all) {
      const pub = p.getTrackPublication(Track.Source.Camera)
      const track = pub?.track as VideoTrack | undefined
      if (!track || pub!.isMuted) continue
      out[p.identity] = {
        mine: p === room.localParticipant,
        attach: (el) => void track.attach(el),
        detach: (el) => void track.detach(el),
      }
    }
    ev.videos(out)
  }
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
      if (track.kind === Track.Kind.Video) return emitVideos()
      // Once per track: a second <audio> for the same voice (the catch-up below can see it too) doubles it.
      if (track.kind !== Track.Kind.Audio || track.attachedElements.length) return
      play(track.attach())
      meter(pub.trackSid, p.identity, track as RemoteAudioTrack)
    })
    .on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack, pub) => {
      if (track.kind === Track.Kind.Video) return emitVideos() // the tiles detach their own <video>
      track.detach().forEach((el) => el.remove())
      unmeter(pub.trackSid)
    })
    .on(RoomEvent.LocalTrackPublished, (pub) => {
      if (pub.track && pub.kind === Track.Kind.Audio) meter(pub.trackSid, room.localParticipant.identity, pub.track as LocalAudioTrack)
      if (pub.kind === Track.Kind.Video) emitVideos()
    })
    .on(RoomEvent.LocalTrackUnpublished, (pub) => {
      unmeter(pub.trackSid)
      if (pub.kind === Track.Kind.Video) emitVideos()
    })
    .on(RoomEvent.TrackUnmuted, (pub) => pub.kind === Track.Kind.Video && emitVideos())
    .on(RoomEvent.ParticipantDisconnected, emitVideos)
    .on(RoomEvent.AudioPlaybackStatusChanged, () => ev.blocked(!room.canPlaybackAudio))
    .on(RoomEvent.ParticipantPermissionsChanged, (_prev, p: Participant) => {
      if (p === room.localParticipant) ev.permissions(!!p.permissions?.canPublish)
    })
    .on(RoomEvent.TrackMuted, (pub, p: Participant) => {
      if (pub.kind === Track.Kind.Video) return emitVideos()
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
      if (!pub.track || pub.track.attachedElements.length) return
      play(pub.track.attach())
      meter(pub.trackSid, p.identity, pub.track as RemoteAudioTrack)
    }),
  )
  ev.status('connected')
  ev.blocked(!room.canPlaybackAudio)
  emitVideos()

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
    setCamera: async (on) => {
      if (on && !room.localParticipant.permissions?.canPublish) throw new Error('not_allowed')
      try {
        await room.localParticipant.setCameraEnabled(on)
      } catch (e) {
        throw new Error(deviceProblem(e))
      } finally {
        emitVideos()
      }
    },
    setSound: (on) => {
      sound = on
      box.querySelectorAll('audio').forEach((el) => (el.muted = !on))
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
