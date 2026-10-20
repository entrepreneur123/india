// On-device nudity check for the partner's video in a call (nsfwjs MobileNetV2 on TensorFlow.js). Frames never
// leave the phone. Loaded only when a call starts; the model files come from this site (scripts/nsfw-model.mjs).
//
// A frame is "nude" when Porn + Hentai >= 0.6 ("Sexy" alone, e.g. swimwear or a bare chest, is not). The video is
// hidden after 2 nude frames in a row and shown again after 4 clean ones, so one odd frame doesn't flicker it.

const SAMPLE_MS = 1200
const NUDE_SCORE = 0.6
const HIDE_AFTER = 2
const SHOW_AFTER = 4

export interface NudityWatch {
  stop(): void
}

/**
 * Watches `video` and calls `onVerdict(true)` when it should be hidden, `onVerdict(false)` when it is clean
 * (the first clean frame too, so the caller can lift its start-up blur). Throws if the model can't load.
 */
export async function watchNudity(video: HTMLVideoElement, onVerdict: (hide: boolean) => void): Promise<NudityWatch> {
  const tf = await import('@tensorflow/tfjs')
  const { load } = await import('nsfwjs/core')
  await tf.ready()
  if (tf.getBackend() !== 'webgl') await tf.setBackend('webgl').catch(() => tf.setBackend('cpu'))
  const model = await load('/models/nsfw/', { size: 224 })

  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 224
  const ctx = canvas.getContext('2d', { willReadFrequently: true })!
  let stopped = false
  let timer: ReturnType<typeof setTimeout> | undefined
  let nudeRun = 0
  let cleanRun = 0
  let hidden: boolean | null = null

  const tick = async () => {
    if (stopped) return
    const started = performance.now()
    if (document.visibilityState === 'visible' && video.readyState >= 2 && video.videoWidth > 0) {
      // Centre square of the frame, like the person sees it (object-cover).
      const side = Math.min(video.videoWidth, video.videoHeight)
      ctx.drawImage(video, (video.videoWidth - side) / 2, (video.videoHeight - side) / 2, side, side, 0, 0, 224, 224)
      try {
        const scores = await model.classify(canvas, 5)
        const p = (name: string) => scores.find((s) => s.className === name)?.probability ?? 0
        const nude = p('Porn') + p('Hentai') >= NUDE_SCORE
        nudeRun = nude ? nudeRun + 1 : 0
        cleanRun = nude ? 0 : cleanRun + 1
        if (!stopped) {
          if (hidden !== true && nudeRun >= HIDE_AFTER) { hidden = true; onVerdict(true) }
          else if (hidden === null && cleanRun >= 1) { hidden = false; onVerdict(false) }
          else if (hidden === true && cleanRun >= SHOW_AFTER) { hidden = false; onVerdict(false) }
        }
      } catch { /* a dropped frame; try the next one */ }
    }
    // A phone without WebGL runs the model on the CPU: give it 4x the time a check took, so the call stays smooth.
    if (!stopped) timer = setTimeout(() => void tick(), Math.max(SAMPLE_MS, (performance.now() - started) * 4))
  }
  void tick()

  return {
    stop() {
      stopped = true
      clearTimeout(timer)
      model.model.dispose()
    },
  }
}
