import { RoomEvent, Track, type AudioProcessorOptions, type LocalAudioTrack, type LocalTrackPublication, type Room, type TrackProcessor } from 'livekit-client'
import { isAppleWebKit } from './rtc'

/** The gate on the audio thread (public/voice-gate.js), as a LiveKit processor of my microphone. */
export function gate(): TrackProcessor<Track.Kind.Audio, AudioProcessorOptions> {
  let nodes: AudioNode[] = []
  const p: TrackProcessor<Track.Kind.Audio, AudioProcessorOptions> = {
    name: 'voice-gate',
    async init({ track, audioContext }) {
      await audioContext.audioWorklet.addModule('/voice-gate.js')
      const source = audioContext.createMediaStreamSource(new MediaStream([track]))
      const node = new AudioWorkletNode(audioContext, 'voice-gate')
      const out = audioContext.createMediaStreamDestination()
      source.connect(node).connect(out)
      nodes = [source, node]
      p.processedTrack = out.stream.getAudioTracks()[0]
    },
    async restart(opts) {
      await p.destroy()
      await p.init(opts)
    },
    async destroy() {
      nodes.forEach((n) => n.disconnect())
      p.processedTrack?.stop()
    },
  }
  return p
}

/**
 * My microphone in [room] goes out only while I'm talking (public/voice-gate.js; the Android app's VoiceGate.kt).
 * Computers only: an AudioContext next to the call crackles on iPhone and opens a second audio stream on Android
 * phones (voice-audio.ts). Where it can't run, the microphone goes out as before.
 */
export function gateMicrophone(room: Room) {
  if (isAppleWebKit(navigator.userAgent, navigator.maxTouchPoints) || /Android/i.test(navigator.userAgent) || !('AudioWorkletNode' in window)) return
  let ctx: AudioContext | null = null
  room
    .on(RoomEvent.LocalTrackPublished, (pub: LocalTrackPublication) => {
      if (pub.source !== Track.Source.Microphone || !pub.track) return
      const track = pub.track as LocalAudioTrack
      ctx ??= new AudioContext()
      void ctx.resume()
      track.setAudioContext(ctx)
      void track.setProcessor(gate()).catch(() => undefined)
    })
    .on(RoomEvent.Disconnected, () => void ctx?.close())
}
