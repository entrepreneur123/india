import { RoomEvent, Track, type AudioProcessorOptions, type LocalAudioTrack, type LocalTrackPublication, type Room, type TrackProcessor } from 'livekit-client'
import { isAppleWebKit } from './rtc'

/** The fallback gate on the audio thread (public/voice-gate.js), as a LiveKit processor of my microphone. */
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
 * My microphone in [room], cleaned as Discord's is: LiveKit's Krisp noise filter with background voice cancellation
 * (on our LiveKit Cloud project: its /settings says enhancedNoiseCancellation). It keeps the voice of the person at the
 * phone and drops noise, hiss and other voices, the room's own voices coming back from another phone's speaker among
 * them. Loaded only when the microphone goes on. Where Krisp can't run, computers get the voice gate (sent only while
 * I talk; public/voice-gate.js, the Android app's VoiceGate.kt); elsewhere the microphone goes out as before.
 */
export function cleanMicrophone(room: Room) {
  room.on(RoomEvent.LocalTrackPublished, async (pub: LocalTrackPublication) => {
    if (pub.source !== Track.Source.Microphone || !pub.track) return
    const track = pub.track as LocalAudioTrack
    try {
      const { KrispNoiseFilter, isKrispNoiseFilterSupported } = await import('@livekit/krisp-noise-filter')
      if (isKrispNoiseFilterSupported()) return await track.setProcessor(KrispNoiseFilter({ useBVC: true }))
    } catch {
      /* not loaded or refused: the gate below */
    }
    const computer = !isAppleWebKit(navigator.userAgent, navigator.maxTouchPoints) && !/Android/i.test(navigator.userAgent)
    if (computer && 'AudioWorkletNode' in window) await track.setProcessor(gate()).catch(() => undefined)
  })
}
