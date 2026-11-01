// Type imports only: a P2P call loads this without the LiveKit SDK.
import type { AudioProcessorOptions, Track, TrackProcessor } from 'livekit-client'
import { desktopChromium } from './rtc'

/** The app's own mic processing (Krisp, this gate): desktop Chrome and Edge only (lib/rtc desktopChromium). */
export const canProcessMic = () => desktopChromium(navigator.userAgent, navigator.maxTouchPoints) && 'AudioWorkletNode' in window

let weights: Promise<ArrayBuffer> | undefined

/**
 * [track] through the voice gate (public/voice-gate.js, Silero VAD) in [ctx]: what goes out is silent unless a voice
 * is in it. The model's weights (487 KB, this site's own file) are fetched when a microphone first goes on.
 */
export async function voiceOnly(track: MediaStreamTrack, ctx: AudioContext) {
  weights ??= fetch('/silero-vad.bin').then((r) => (r.ok ? r.arrayBuffer() : Promise.reject(new Error(String(r.status)))))
  const [w] = await Promise.all([weights.catch((e) => ((weights = undefined), Promise.reject(e))), ctx.audioWorklet.addModule('/voice-gate.js')])
  const source = ctx.createMediaStreamSource(new MediaStream([track]))
  const node = new AudioWorkletNode(ctx, 'voice-gate', { processorOptions: { weights: w } })
  const out = ctx.createMediaStreamDestination()
  source.connect(node).connect(out)
  const sent = out.stream.getAudioTracks()[0]
  return {
    track: sent,
    stop() {
      source.disconnect()
      node.disconnect()
      sent.stop()
    },
  }
}

/** The voice gate as a LiveKit processor of my microphone. */
export function gate(): TrackProcessor<Track.Kind.Audio, AudioProcessorOptions> {
  let g: Awaited<ReturnType<typeof voiceOnly>> | undefined
  const p: TrackProcessor<Track.Kind.Audio, AudioProcessorOptions> = {
    name: 'voice-gate',
    async init({ track, audioContext }) {
      g = await voiceOnly(track, audioContext)
      p.processedTrack = g.track
    },
    async restart(opts) {
      await p.destroy()
      await p.init(opts)
    },
    async destroy() {
      g?.stop()
      g = undefined
    },
  }
  return p
}
