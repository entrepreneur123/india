// Real video for an accepted call: LiveKit room = match id (token from the video-token edge function).
// Same settings as the Android app: adaptive stream + dynacast so a weak network lowers quality instead of freezing.
import { Room, RoomEvent, Track, VideoPresets, type RemoteTrack } from 'livekit-client'

export type CallState = 'connecting' | 'connected' | 'reconnecting' | 'failed'

export interface Call {
  /** 'blocked' when the camera/microphone could not be turned on (no device, or permission refused). */
  devices: 'ok' | 'blocked'
  setMic(on: boolean): Promise<void>
  setCamera(on: boolean): Promise<void>
  disconnect(): Promise<void>
}

export async function joinCall(
  url: string,
  token: string,
  els: { remote: HTMLVideoElement; local: HTMLVideoElement; audio: HTMLAudioElement },
  onState: (s: CallState) => void,
  /** Aborting (the call screen closed) disconnects at once, even while still connecting. */
  signal?: AbortSignal,
): Promise<Call> {
  if (signal?.aborted) throw new DOMException('call ended', 'AbortError')
  // Safari (iPhone, iPad) encodes H.264 in hardware but VP8 in software, which looks soft and blocky on an iPad;
  // every other browser and the Android app keep VP8. Retina screens ask for the sharper layer.
  const safari = /^((?!chrome|chromium|crios|fxios|edg|android).)*safari/i.test(navigator.userAgent)
  const room = new Room({
    adaptiveStream: { pixelDensity: 'screen' },
    dynacast: true,
    videoCaptureDefaults: { resolution: VideoPresets.h720.resolution, facingMode: 'user' },
    publishDefaults: { videoCodec: safari ? 'h264' : 'vp8', simulcast: true, videoEncoding: VideoPresets.h720.encoding },
  })
  let leaving = false
  signal?.addEventListener('abort', () => {
    leaving = true
    void room.disconnect()
  })
  const attach = (track: RemoteTrack) => {
    if (track.kind === Track.Kind.Video) track.attach(els.remote)
    else if (track.kind === Track.Kind.Audio) track.attach(els.audio)
  }
  room
    .on(RoomEvent.TrackSubscribed, (track) => attach(track))
    .on(RoomEvent.TrackUnsubscribed, (track) => track.detach())
    .on(RoomEvent.Reconnecting, () => onState('reconnecting'))
    .on(RoomEvent.Reconnected, () => onState('connected'))
    .on(RoomEvent.Disconnected, () => { if (!leaving) onState('failed') })

  onState('connecting')
  try {
    await room.connect(url, token)
  } catch (e) {
    leaving = true
    await room.disconnect()
    onState('failed')
    throw e
  }
  if (signal?.aborted) throw new DOMException('call ended', 'AbortError')
  // Tracks the partner published before we joined.
  room.remoteParticipants.forEach((p) => p.trackPublications.forEach((pub) => pub.track && attach(pub.track)))
  onState('connected')
  // The partner shows even if this browser has no camera or the user blocks it.
  let devices: 'ok' | 'blocked' = 'ok'
  if (signal?.aborted) throw new DOMException('call ended', 'AbortError') // never turn the camera on for an ended call
  try {
    await room.localParticipant.enableCameraAndMicrophone()
    room.localParticipant.getTrackPublication(Track.Source.Camera)?.videoTrack?.attach(els.local)
  } catch {
    devices = 'blocked'
  }

  return {
    devices,
    setMic: async (on) => { await room.localParticipant.setMicrophoneEnabled(on) },
    setCamera: async (on) => {
      await room.localParticipant.setCameraEnabled(on)
      if (on) room.localParticipant.getTrackPublication(Track.Source.Camera)?.videoTrack?.attach(els.local)
    },
    disconnect: async () => { leaving = true; await room.disconnect() },
  }
}
