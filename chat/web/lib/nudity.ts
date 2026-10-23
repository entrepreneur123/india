// On-device nudity check (nsfwjs MobileNetV2 on TensorFlow.js): the partner's video in a call, and a photo before it
// is sent to a room. Frames and photos never leave the phone for it. Loaded only when needed; the model files come
// from this site (scripts/nsfw-model.mjs).
//
// A frame is "nude" when Porn + Hentai >= 0.85 ("Sexy" alone, e.g. swimwear or a bare chest, is not; a face filling
// the frame often scores 0.6-0.8, so lower thresholds hid ordinary close-ups). The video is
// hidden after 3 nude frames in a row and shown again after 4 clean ones, so one odd frame doesn't flicker it.

const SAMPLE_MS = 1200
const NUDE_SCORE = 0.85
const HIDE_AFTER = 3
const SHOW_AFTER = 4

let webgl: boolean | null = null
function hasWebGL() {
  if (webgl === null) {
    try {
      // Same test as TensorFlow.js: a software-only WebGL (no GPU) doesn't count.
      const c = document.createElement('canvas')
      const opts = { failIfMajorPerformanceCaveat: true }
      webgl = !!(c.getContext('webgl2', opts) ?? c.getContext('webgl', opts))
    } catch {
      webgl = false
    }
  }
  return webgl
}

async function loadModel() {
  const tf = await import('@tensorflow/tfjs')
  const { load } = await import('nsfwjs/core')
  // Without WebGL (some phones, headless browsers) go straight to the CPU, instead of a failed GPU start first.
  if (!hasWebGL()) await tf.setBackend('cpu')
  await tf.ready()
  if (tf.getBackend() !== 'webgl' && hasWebGL()) await tf.setBackend('webgl').catch(() => tf.setBackend('cpu'))
  return load('/models/nsfw/', { size: 224 })
}

type Model = Awaited<ReturnType<typeof loadModel>>
const nudeScore = async (model: Model, input: HTMLCanvasElement) => {
  const scores = await model.classify(input, 5)
  const p = (name: string) => scores.find((s) => s.className === name)?.probability ?? 0
  return p('Porn') + p('Hentai')
}

/** Kept after the first photo: the next one is checked at once. */
let photoModel: Promise<Model> | null = null

/**
 * True when a photo should not be sent: the whole picture or its centre square scores as nude (same threshold as
 * calls). Throws if the model can't load or the file isn't an image (the caller then doesn't send it either).
 */
export async function classifyImage(file: Blob): Promise<boolean> {
  photoModel ??= loadModel().catch((e) => {
    photoModel = null
    throw e
  })
  const [model, bitmap] = await Promise.all([photoModel, createImageBitmap(file)])
  try {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 224
    const ctx = canvas.getContext('2d', { willReadFrequently: true })!
    ctx.drawImage(bitmap, 0, 0, 224, 224)
    if ((await nudeScore(model, canvas)) >= NUDE_SCORE) return true
    const side = Math.min(bitmap.width, bitmap.height)
    ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, 224, 224)
    return (await nudeScore(model, canvas)) >= NUDE_SCORE
  } finally {
    bitmap.close()
  }
}

export interface NudityWatch {
  stop(): void
}

/**
 * Watches `video` and calls `onVerdict(true)` when it should be hidden, `onVerdict(false)` when it is clean
 * (the first clean frame too, so the caller can lift its start-up blur). Throws if the model can't load.
 */
export async function watchNudity(video: HTMLVideoElement, onVerdict: (hide: boolean) => void): Promise<NudityWatch> {
  const model = await loadModel()

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
        const nude = (await nudeScore(model, canvas)) >= NUDE_SCORE
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
