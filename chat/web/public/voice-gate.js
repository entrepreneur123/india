// The microphone gate (lib/voice-gate.ts): my microphone goes out only while a human voice is in it, as Discord's
// voice activity does. Silero VAD v6 (MIT, Silero Team; its 16 kHz model, weights in /silero-vad.bin from
// scripts/silero-weights.py) scores every 32 ms for speech, run here in plain JS on the audio thread: a chunk over OPEN
// opens the gate, HOLD seconds under SHUT close it. Keyboard, music, fans and hiss stay out however loud. The sound
// goes out LEAD seconds late so the first syllable isn't cut while the gate opens; 5 ms ramps so it never clicks.
// The audio itself passes untouched. OPEN and SHUT are Silero's own defaults (threshold, threshold - 0.15).
const OPEN = 0.5
const SHUT = 0.35
const HOLD = 0.4
const LEAD = 0.064

/** An IEEE half float (the weights file has no infinities or NaNs). */
const half = (u) => (u >> 15 ? -1 : 1) * ((u >> 10) & 31 ? 2 ** (((u >> 10) & 31) - 15) * (1 + (u & 1023) / 1024) : 2 ** -24 * (u & 1023))

/** Silero VAD at 16 kHz: 576 samples (the last 64 of the previous chunk, then 512 new) → chance of speech. */
function silero(w) {
  let at = 0
  const take = (n) => w.subarray(at, (at += n))
  // Its first layer is a Hann-windowed 256-point STFT (hop 128), done here as an FFT; activations are time-major.
  const layers = [[129, 128, 1, 4], [128, 64, 2, 4], [64, 64, 2, 2], [64, 128, 1, 1]].map(([cin, cout, stride, t]) => {
    const raw = take(cout * cin * 3)
    const wk = new Float32Array(raw.length) // [out][tap][in], so the inner loop runs along memory
    for (let o = 0; o < cout; o++) for (let i = 0; i < cin; i++) for (let k = 0; k < 3; k++) wk[(o * 3 + k) * cin + i] = raw[(o * cin + i) * 3 + k]
    return { cin, cout, stride, t, w: wk, b: take(cout), out: new Float32Array((cout * t) / stride) }
  })
  const [wi, wh, bi, bh] = [take(512 * 128), take(512 * 128), take(512), take(512)]
  const dw = take(128)
  const db = take(1)[0]
  const x = new Float32Array(640)
  const mag = new Float32Array(4 * 129)
  const re = new Float64Array(256)
  const im = new Float64Array(256)
  const cos = Float64Array.from({ length: 128 }, (_, k) => Math.cos((Math.PI * k) / 128))
  const sin = Float64Array.from({ length: 128 }, (_, k) => Math.sin((Math.PI * k) / 128))
  const hann = Float64Array.from({ length: 256 }, (_, n) => 0.5 - 0.5 * Math.cos((Math.PI * n) / 128))
  const rev = Uint8Array.from({ length: 256 }, (_, n) => parseInt(n.toString(2).padStart(8, '0').split('').reverse().join(''), 2))
  const h = new Float32Array(128)
  const c = new Float32Array(128)
  const g = new Float32Array(512)
  const sig = (v) => 1 / (1 + Math.exp(-v))
  return (chunk) => {
    x.set(chunk)
    for (let j = 0; j < 64; j++) x[576 + j] = chunk[574 - j] // reflect padding on the right
    for (let t = 0; t < 4; t++) {
      for (let n = 0; n < 256; n++) {
        re[rev[n]] = x[t * 128 + n] * hann[n]
        im[n] = 0
      }
      for (let size = 2; size <= 256; size *= 2)
        for (let i = 0, half = size / 2, step = 256 / size; i < 256; i += size)
          for (let j = 0; j < half; j++) {
            const a = i + j
            const b = a + half
            const wc = cos[j * step]
            const ws = sin[j * step]
            const tr = re[b] * wc + im[b] * ws
            const ti = im[b] * wc - re[b] * ws
            re[b] = re[a] - tr
            im[b] = im[a] - ti
            re[a] += tr
            im[a] += ti
          }
      for (let f = 0; f < 129; f++) mag[t * 129 + f] = Math.sqrt(re[f] * re[f] + im[f] * im[f])
    }
    let input = mag
    for (const { cin, cout, stride, t, w, b, out } of layers) {
      for (let u = 0, tout = t / stride; u < tout; u++)
        for (let o = 0; o < cout; o++) {
          let s = b[o]
          for (let k = 0; k < 3; k++) {
            const v = u * stride + k - 1
            if (v < 0 || v >= t) continue
            for (let i = 0, q = (o * 3 + k) * cin, r = v * cin; i < cin; i++) s += w[q + i] * input[r + i]
          }
          out[u * cout + o] = s > 0 ? s : 0
        }
      input = out
    }
    for (let r = 0; r < 512; r++) {
      let s = bi[r] + bh[r]
      for (let i = 0, q = r * 128; i < 128; i++) s += wi[q + i] * input[i] + wh[q + i] * h[i]
      g[r] = s
    }
    let p = db
    for (let i = 0; i < 128; i++) {
      // LSTM cell, PyTorch's gate order: in, forget, cell, out
      c[i] = sig(g[128 + i]) * c[i] + sig(g[i]) * Math.tanh(g[256 + i])
      h[i] = sig(g[384 + i]) * Math.tanh(c[i])
      if (h[i] > 0) p += dw[i] * h[i]
    }
    return sig(p)
  }
}

class VoiceGate extends AudioWorkletProcessor {
  constructor({ processorOptions }) {
    super()
    this.vad = silero(Float32Array.from(new Uint16Array(processorOptions.weights), half))
    this.chunk = new Float32Array(576)
    this.filled = 64
    this.sum = 0
    this.count = 0
    this.phase = 0
    this.delay = new Float32Array(Math.round(LEAD * sampleRate))
    this.pos = 0
    this.open = false
    this.quiet = 0
    this.gain = 0
    this.step = 1 / (0.005 * sampleRate)
  }

  process([input], [output]) {
    const x = input?.[0]
    const y = output?.[0]
    if (!x || !y) return true
    const { delay, step, chunk } = this
    let { pos, gain, sum, count, phase, filled } = this
    for (let i = 0; i < x.length; i++) {
      // To 16 kHz for the model: each of its samples is the mean of the ones it spans.
      sum += x[i]
      count++
      if ((phase += 16000) >= sampleRate) {
        phase -= sampleRate
        chunk[filled++] = sum / count
        sum = count = 0
        if (filled === 576) {
          const p = this.vad(chunk)
          chunk.copyWithin(0, 512)
          filled = 64
          if (p >= OPEN) this.open = true
          if (p >= SHUT) this.quiet = 0
          else if (this.open && (this.quiet += 0.032) >= HOLD) this.open = false
        }
      }
      const target = this.open ? 1 : 0
      if (gain !== target) gain = gain < target ? Math.min(target, gain + step) : Math.max(target, gain - step)
      y[i] = delay[pos] * gain
      delay[pos] = x[i]
      if (++pos === delay.length) pos = 0
    }
    Object.assign(this, { pos, gain, sum, count, phase, filled })
    for (let c = 1; c < output.length; c++) output[c].set(y)
    return true
  }
}

registerProcessor('voice-gate', VoiceGate)
