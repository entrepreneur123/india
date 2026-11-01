import { RoomEvent, Track, type AudioProcessorOptions, type LocalAudioTrack, type LocalTrackPublication, type Room, type TrackProcessor } from 'livekit-client'
import { isAppleWebKit } from './rtc'

/** Computers only: on iPhone/iPad and Android a second AudioContext beside the call's can crackle (lib/voice-audio). */
export const canGate = () =>
  !isAppleWebKit(navigator.userAgent, navigator.maxTouchPoints) && !/Android/i.test(navigator.userAgent) && 'AudioWorkletNode' in window

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

/**
 * My microphone in [room], cleaned as Discord's is: LiveKit's Krisp noise filter with background voice cancellation
 * (on our LiveKit Cloud project: its /settings says enhancedNoiseCancellation). It keeps the voice of the person at the
 * phone and drops noise, music and other voices. Loaded only when the microphone goes on. Where Krisp can't run,
 * computers get the voice gate (sent only while someone talks); elsewhere the microphone goes out as before.
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
    if (canGate()) await track.setProcessor(gate()).catch(() => undefined)
  })
}
