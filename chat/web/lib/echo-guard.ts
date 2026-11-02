// Type imports only: a P2P call loads this without the LiveKit SDK.
import type { AudioProcessorOptions, Room, Track, TrackProcessor } from 'livekit-client'

/**
 * The voices this page plays right now (room speakers, a call's partner): the echo guard's reference. One page plays
 * one room or call at a time, so one list; each guard follows it. `audible` false while the room's sound is off (what
 * isn't heard can't come back through the mic).
 */
const playing = new Set<MediaStreamTrack>()
const followers = new Set<() => void>()
let audible = true
const changed = () => followers.forEach((f) => f())
export const echoRefs = {
  add: (t: MediaStreamTrack) => void (playing.add(t), changed()),
  remove: (t: MediaStreamTrack) => void (playing.delete(t) && changed()),
  setAudible: (on: boolean) => void (on !== audible && ((audible = on), changed())),
}

/**
 * [track] through public/echo-guard.js in [ctx], with the playing voices as its second input: what goes out has the
 * echo of those voices taken out (measured: 23-30 dB less, my voice alone within 0.4 dB, nothing changed with earbuds).
 */
const loaded = new WeakMap<BaseAudioContext, Promise<void>>() // the worklet module, once per context (a restart rebuilds)
export async function guardEcho(track: MediaStreamTrack, ctx: AudioContext) {
  if (!loaded.has(ctx)) loaded.set(ctx, ctx.audioWorklet.addModule('/echo-guard.js'))
  await loaded.get(ctx)
  const node = new AudioWorkletNode(ctx, 'echo-guard', { numberOfInputs: 2, numberOfOutputs: 1, outputChannelCount: [1] })
  const mic = ctx.createMediaStreamSource(new MediaStream([track]))
  const mix = ctx.createGain()
  mic.connect(node, 0, 0)
  mix.connect(node, 0, 1)
  const out = ctx.createMediaStreamDestination()
  node.connect(out)
  const sources = new Map<MediaStreamTrack, MediaStreamAudioSourceNode>()
  const follow = () => {
    mix.gain.value = audible ? 1 : 0
    for (const [t, s] of sources) if (!playing.has(t) || t.readyState === 'ended') (s.disconnect(), sources.delete(t))
    for (const t of playing) {
      if (sources.has(t) || t.readyState === 'ended') continue
      const s = ctx.createMediaStreamSource(new MediaStream([t]))
      s.connect(mix)
      sources.set(t, s)
    }
  }
  follow()
  followers.add(follow)
  const sent = out.stream.getAudioTracks()[0]
  return {
    track: sent,
    stop() {
      followers.delete(follow)
      sources.forEach((s) => s.disconnect())
      mic.disconnect()
      mix.disconnect()
      node.disconnect()
      sent.stop()
    },
  }
}

type AudioProcessor = TrackProcessor<Track.Kind.Audio, AudioProcessorOptions>

/** [inner] (Krisp, the voice gate, or none) and then the echo guard, as one LiveKit processor of my microphone. */
export function withEchoGuard(inner: AudioProcessor | null): AudioProcessor {
  let g: Awaited<ReturnType<typeof guardEcho>> | undefined
  const build = async (opts: AudioProcessorOptions) => {
    g?.stop()
    const before = inner?.processedTrack as MediaStreamTrack | undefined
    // failing (no AudioWorklet), [inner] alone: Krisp is never lost to the guard
    g = await guardEcho(before ?? opts.track, opts.audioContext!).catch(() => undefined)
    p.processedTrack = g?.track ?? before
  }
  const p: AudioProcessor = {
    name: inner ? `${inner.name}+echo-guard` : 'echo-guard',
    async init(opts) {
      if (inner) await inner.init(opts)
      await build(opts)
    },
    async restart(opts) {
      if (inner) await inner.restart(opts)
      await build(opts)
    },
    async destroy() {
      g?.stop()
      g = undefined
      await inner?.destroy()
    },
    async onPublish(room: Room) {
      await inner?.onPublish?.(room)
    },
    async onUnpublish() {
      await inner?.onUnpublish?.()
    },
  }
  return p
}
