// Real video for an accepted call: LiveKit room = match id (token from the video-token edge function).
// Same settings as the Android app: adaptive stream + dynacast so a weak network lowers quality instead of freezing.
//
// The token lives 5 minutes, which only matters for the first connect: once in, LiveKit's server hands the client a
// fresh token every few minutes, and a reconnect after a network switch (Wi-Fi to 4G) uses that one
// (docs.livekit.io/frontends/authentication/tokens). Nothing here sets rtcConfig, so LiveKit Cloud's TURN over TLS
// on 443 stays available for networks that block UDP.
import {
  AudioPresets,
  DisconnectReason,
  type LocalVideoTrack,
  type Participant,
  type RemoteParticipant,
  type RemoteTrack,
  type RemoteTrackPublication,
  Room,
  RoomEvent,
  Track,
  VideoPresets,
} from 'livekit-client'
import { cleanMicrophone } from './voice-gate'
import { connectOptions, type DeviceProblem, deviceProblem, partnerNotice, preferRelay, type Quality, RELAY_AFTER, videoCodecFor } from './rtc'

/** 'lost': LiveKit gave up for a reason a fresh join can fix (the call screen joins again); 'failed': it can't. */
export type CallState = 'connecting' | 'connected' | 'reconnecting' | 'lost' | 'failed'
/** Ended on purpose (I left, kicked, same account elsewhere, room closed): joining again would be wrong. */
export const FINAL = new Set([DisconnectReason.CLIENT_INITIATED, DisconnectReason.DUPLICATE_IDENTITY, DisconnectReason.PARTICIPANT_REMOVED,
  DisconnectReason.ROOM_DELETED, DisconnectReason.ROOM_CLOSED, DisconnectReason.USER_UNAVAILABLE, DisconnectReason.USER_REJECTED])
export type PartnerNotice = ReturnType<typeof partnerNotice>

export interface CallEvents {
  state(s: CallState): void
  /** My own connection as LiveKit rates it ('excellent' | 'good' | 'poor' | 'lost' | 'unknown'). */
  quality(q: Quality): void
  /** My partner's connection: dropped, video paused for bandwidth, or weak; null when fine. */
  partner(n: PartnerNotice): void
  /** My partner turned their camera off (or on). */
  partnerCamera(on: boolean): void
  /** The browser (iPhone, iPad) blocked their sound until I touch the page: show a "tap to hear" button. */
  blocked(needsTap: boolean): void
}

export interface Call {
  /** Why the camera / microphone could not be turned on (permission refused, none, in use by another app); null if on. */
  camera: DeviceProblem | null
  mic: DeviceProblem | null
  /** Throws a DeviceProblem string when the device cannot be turned on. */
  setMic(on: boolean): Promise<void>
  setCamera(on: boolean): Promise<void>
  /** Front ↔ back camera (phones and tablets); says which one is on now. */
  flipCamera(): Promise<'user' | 'environment'>
  /** Call from a tap: lets a browser that blocked autoplay play the partner's sound. */
  startAudio(): Promise<void>
  disconnect(): Promise<void>
}

export async function joinCall(
  url: string,
  token: string,
  els: { remote: HTMLVideoElement; local: HTMLVideoElement; audio: HTMLAudioElement },
  ev: CallEvents,
  /** Aborting (the call screen closed) disconnects at once, even while still connecting. */
  signal?: AbortSignal,
  /** False: a voice call, the camera starts off (it can be turned on later). */
  video = true,
): Promise<Call> {
  if (signal?.aborted) throw new DOMException('call ended', 'AbortError')
  const room = new Room({
    // Retina screens ask for the sharper layer.
    adaptiveStream: { pixelDensity: 'screen' },
    dynacast: true,
    // Echo cancellation, noise suppression and auto gain are LiveKit's defaults; said here so nobody turns them off.
    audioCaptureDefaults: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    videoCaptureDefaults: { resolution: VideoPresets.h720.resolution, facingMode: 'user' },
    publishDefaults: {
      // H.264 on iPhone/iPad/Mac Safari (hardware encoder), VP8 elsewhere; 720p top layer + LiveKit's default
      // 180p/360p simulcast layers, so a weak receiver gets a smaller layer instead of a frozen one.
      videoCodec: videoCodecFor(navigator.userAgent, navigator.maxTouchPoints),
      simulcast: true,
      videoEncoding: VideoPresets.h720.encoding,
      // Opus mono, 48 kbps cap; DTX (silence costs nothing) and RED (redundant packets on lossy mobile networks).
      audioPreset: AudioPresets.music,
      dtx: true,
      red: true,
      forceStereo: false,
    },
  })
  cleanMicrophone(room)
  let leaving = false
  let rebuilt = 0
  signal?.addEventListener('abort', () => {
    leaving = true
    void room.disconnect()
  })

  // The other person in the room (a call has exactly one).
  const partner = { quality: 'unknown' as Quality, videoPaused: false, left: false }
  let lastNotice: PartnerNotice = null
  const tellPartner = () => {
    const n = partnerNotice(partner)
    if (n !== lastNotice) ev.partner((lastNotice = n))
  }
  const isPartner = (p: Participant) => p !== room.localParticipant

  const attach = (track: RemoteTrack) => {
    if (track.kind === Track.Kind.Video) track.attach(els.remote)
    else if (track.kind === Track.Kind.Audio) track.attach(els.audio)
  }
  room
    .on(RoomEvent.TrackSubscribed, (track, pub) => {
      attach(track)
      if (track.kind === Track.Kind.Video) ev.partnerCamera(!pub.isMuted)
    })
    .on(RoomEvent.TrackUnsubscribed, (track) => {
      track.detach()
      if (track.kind === Track.Kind.Video) ev.partnerCamera(false)
    })
    .on(RoomEvent.TrackMuted, (pub, p) => {
      if (isPartner(p) && pub.source === Track.Source.Camera) ev.partnerCamera(false)
    })
    .on(RoomEvent.TrackUnmuted, (pub, p) => {
      if (isPartner(p) && pub.source === Track.Source.Camera) ev.partnerCamera(true)
    })
    // Paused: LiveKit stopped their video because the network can't carry even the smallest layer.
    .on(RoomEvent.TrackStreamStateChanged, (pub: RemoteTrackPublication, state) => {
      if (pub.kind !== Track.Kind.Video) return
      partner.videoPaused = state === Track.StreamState.Paused
      tellPartner()
    })
    .on(RoomEvent.ConnectionQualityChanged, (q, p) => {
      if (p === room.localParticipant) ev.quality(q)
      else {
        partner.quality = q
        tellPartner()
      }
    })
    .on(RoomEvent.ParticipantDisconnected, () => {
      partner.left = true
      tellPartner()
    })
    .on(RoomEvent.ParticipantConnected, (p: RemoteParticipant) => {
      partner.left = false
      partner.quality = p.connectionQuality
      tellPartner()
    })
    .on(RoomEvent.AudioPlaybackStatusChanged, () => ev.blocked(!room.canPlaybackAudio))
    // A network switch resumes the signal only (SignalReconnecting); a broken media path rebuilds it (Reconnecting).
    .on(RoomEvent.SignalReconnecting, () => ev.state('reconnecting'))
    .on(RoomEvent.Reconnecting, () => {
      if (++rebuilt === RELAY_AFTER) preferRelay()
      ev.state('reconnecting')
    })
    .on(RoomEvent.Reconnected, () => ev.state('connected'))
    .on(RoomEvent.Disconnected, (reason) => {
      if (leaving) return
      const final = reason !== undefined && FINAL.has(reason)
      if (!final) preferRelay()
      ev.state(final ? 'failed' : 'lost')
    })

  ev.state('connecting')
  try {
    await room.connect(url, token, connectOptions())
  } catch (e) {
    leaving = true
    await room.disconnect()
    ev.state('failed')
    throw e
  }
  if (signal?.aborted) throw new DOMException('call ended', 'AbortError')
  // Tracks the partner published before we joined.
  room.remoteParticipants.forEach((p) =>
    p.trackPublications.forEach((pub) => {
      if (!pub.track) return
      attach(pub.track)
      if (pub.kind === Track.Kind.Video) ev.partnerCamera(!pub.isMuted)
    }),
  )
  ev.state('connected')
  ev.blocked(!room.canPlaybackAudio)

  const lp = room.localParticipant
  let facing: 'user' | 'environment' = 'user'
  const showSelf = () => lp.getTrackPublication(Track.Source.Camera)?.videoTrack?.attach(els.local)
  const enable = async (turnOn: () => Promise<unknown>): Promise<DeviceProblem | null> => {
    try {
      await turnOn()
      return null
    } catch (e) {
      return deviceProblem(e)
    }
  }
  // The partner shows even if this browser has no camera or the user blocks it. One prompt for both first; if that
  // fails, each on its own, so a missing or busy camera still leaves the microphone on (and the other way round).
  let camera: DeviceProblem | null = null
  let mic: DeviceProblem | null = null
  if (signal?.aborted) throw new DOMException('call ended', 'AbortError') // never turn the camera on for an ended call
  if (!video) mic = await enable(() => lp.setMicrophoneEnabled(true))
  else if ((await enable(() => lp.enableCameraAndMicrophone())) !== null) {
    mic = await enable(() => lp.setMicrophoneEnabled(true))
    camera = await enable(() => lp.setCameraEnabled(true))
  }
  if (video && !camera) showSelf()

  const toggle = async (turn: () => Promise<unknown>) => {
    const problem = await enable(turn)
    if (problem) throw new Error(problem)
  }
  return {
    camera,
    mic,
    setMic: (on) => toggle(() => lp.setMicrophoneEnabled(on)),
    setCamera: async (on) => {
      await toggle(() => lp.setCameraEnabled(on))
      if (on) showSelf()
    },
    flipCamera: async () => {
      facing = facing === 'user' ? 'environment' : 'user'
      const track = lp.getTrackPublication(Track.Source.Camera)?.videoTrack as LocalVideoTrack | undefined
      await track?.restartTrack({ facingMode: facing, resolution: VideoPresets.h720.resolution })
      return facing
    },
    startAudio: () => room.startAudio(),
    disconnect: async () => { leaving = true; await room.disconnect() },
  }
}
