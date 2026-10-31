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
  type RemoteParticipant,
  type RemoteTrackPublication,
  type RemoteTrack,
  Room,
  RoomEvent,
  Track,
  TrackEvent,
  VideoPresets,
  type VideoTrack,
} from 'livekit-client'
import type { VoiceVideo } from './backend'
import { FINAL } from './livekit'
import { connectOptions, deviceProblem, isAppleWebKit, preferRelay, RELAY_AFTER, videoCodecFor } from './rtc'
import { cleanMicrophone } from './voice-gate'
import { rms, silenceDetector, talkDetector } from './mic-hints'
import { savedVolumes } from './voice-volumes'

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
  /** A hint about my microphone (null: none): I'm talking while muted, or it sends nothing but silence. */
  micHint(hint: MicHint): void
}

export type MicHint = 'talkingMuted' | 'silent' | null

export interface VoiceAudio {
  readonly canPublish: boolean
  /** Throws 'mic_denied' (no permission or no microphone) or 'not_allowed' (not on a seat yet). */
  setMic(on: boolean): Promise<void>
  /** Throws a DeviceProblem ('denied', 'missing', 'busy', 'other') or 'not_allowed' (not on a seat). */
  setCamera(on: boolean): Promise<void>
  startAudio(): Promise<void>
  /** 46: the room's voices on or off for me. Off, they aren't even downloaded (R4: no bandwidth for a muted room). */
  setSound(on: boolean): void
  /** 51: one person's volume for me, 0-1 (0: not downloaded at all). Kept if they leave and come back. */
  setVolume(identity: string, volume: number): void
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
      // No DTX next to RED: measured to warp the received audio (lib/livekit.ts, docs/SCALE_UP.md §4).
      audioPreset: AudioPresets.music, dtx: false, red: true, forceStereo: false,
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
  let rebuilt = 0
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

  let hint: MicHint = null
  const setHint = (h: MicHint) => void (h !== hint && ev.micHint((hint = h)))
  const micTrack = () => room.localParticipant.getTrackPublication(Track.Source.Microphone)?.track as LocalAudioTrack | undefined
  // Microphone hints (lib/mic-hints.ts, after Jitsi). Talking while muted: a copy of the mic, switched on, measured
  // while LiveKit sends nothing; only where a second audio graph is safe (see `analysers`).
  let stopProbe = () => {}
  const startProbe = () => {
    const raw = micTrack()?.mediaStreamTrack
    if (!analysers || !raw || raw.readyState === 'ended') return
    stopProbe()
    const copy = raw.clone()
    copy.enabled = true
    const ctx = new AudioContext()
    const an = ctx.createAnalyser()
    an.fftSize = 1024
    ctx.createMediaStreamSource(new MediaStream([copy])).connect(an)
    const buf = new Float32Array(an.fftSize)
    const talking = talkDetector()
    const tick = setInterval(() => {
      an.getFloatTimeDomainData(buf)
      if (!talking(rms(buf), performance.now())) return
      setHint('talkingMuted')
      stopProbe()
    }, 50)
    stopProbe = () => {
      clearInterval(tick)
      copy.stop()
      void ctx.close().catch(() => undefined)
      stopProbe = () => {}
    }
  }
  // No signal: the browser's own level of what the mic hands over (getStats media-source), every second while I talk.
  let silent = silenceDetector()
  const silence = setInterval(async () => {
    const sender = micTrack()?.sender
    if (!micWanted || !sender) return
    let level: number | undefined
    ;(await sender.getStats().catch(() => null))?.forEach((r) => {
      if (r.type === 'media-source' && r.kind === 'audio' && typeof r.audioLevel === 'number') level = r.audioLevel
    })
    if (level === undefined) return // a browser without the level: no hint rather than a wrong one
    const heard = silent(level, performance.now())
    if (heard === 'silent') setHint('silent')
    else if (heard === 'sound' && hint === 'silent') setHint(null)
  }, 1_000)

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
    clearInterval(silence)
    stopProbe()
    for (const sid of [...meters.keys()]) unmeter(sid)
    box.remove()
  }
  const abort = () => {
    leaving = true
    teardown()
    void room.disconnect()
  }
  signal.addEventListener('abort', abort)

  // Cameras always; voices only while my sound is on and that person isn't at 0 (the rings then use the server's levels).
  const volumes = new Map(savedVolumes())
  const want = (pub: RemoteTrackPublication, p: RemoteParticipant) =>
    pub.setSubscribed(pub.kind !== Track.Kind.Audio || (sound && (volumes.get(p.identity) ?? 1) > 0))

  room
    .on(RoomEvent.TrackPublished, want)
    .on(RoomEvent.ParticipantConnected, (p: RemoteParticipant) => p.setVolume(volumes.get(p.identity) ?? 1))
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
      if (pub.track && pub.kind === Track.Kind.Audio) {
        meter(pub.trackSid, room.localParticipant.identity, pub.track as LocalAudioTrack)
        silent = silenceDetector()
        // The mic went away while I talk (headset unplugged, Bluetooth gone): LiveKit takes it up again only on the
        // next unmute (LocalAudioTrack.unmute), so ask for the default one now; failing that, say it is silent.
        pub.track.on(TrackEvent.Ended, () => {
          if (micWanted) void (pub.track as LocalAudioTrack).restartTrack().catch(() => setHint('silent'))
        })
      }
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
      startProbe()
      ev.micMutedByServer()
    })
    // As in calls (lib/livekit): a network switch resumes the signal only; a broken media path rebuilds it.
    .on(RoomEvent.SignalReconnecting, () => ev.status('reconnecting'))
    .on(RoomEvent.Reconnecting, () => {
      if (++rebuilt === RELAY_AFTER) preferRelay()
      ev.status('reconnecting')
    })
    .on(RoomEvent.Reconnected, () => ev.status('connected'))
    .on(RoomEvent.Disconnected, (reason?: DisconnectReason) => {
      if (leaving) return
      leaving = true
      if (reason === undefined || !FINAL.has(reason)) preferRelay()
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
    await room.connect(url, token, { autoSubscribe: false, ...connectOptions() })
  } catch (e) {
    if (!leaving) {
      leaving = true
      teardown()
      ev.status('failed')
    }
    throw e
  }
  if (signal.aborted) throw new DOMException('left', 'AbortError')
  // Tracks published before I joined (TrackSubscribed attaches them).
  room.remoteParticipants.forEach((p) => p.trackPublications.forEach((pub) => want(pub, p)))
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
        if (on) {
          stopProbe()
          silent = silenceDetector() // the 4 s count starts at the unmute, not before it
          if (hint === 'talkingMuted') setHint(null)
        } else {
          startProbe()
          if (hint === 'silent') setHint(null) // muted on purpose now: the other hint, if any, is the news
        }
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
      room.remoteParticipants.forEach((p) => p.audioTrackPublications.forEach((pub) => want(pub, p)))
    },
    setVolume: (identity, volume) => {
      volumes.set(identity, volume)
      const p = room.remoteParticipants.get(identity)
      if (!p) return
      if (volume > 0) p.setVolume(volume)
      p.audioTrackPublications.forEach((pub) => want(pub, p))
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
