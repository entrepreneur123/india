// Echo guard (lib/echo-guard.ts): takes out of my microphone the room's voices that come back through my own speaker,
// which the browser's echo canceller leaves in on some phones. Input 0 is my mic (after Krisp), input 1 the voices this
// page plays (the reference). A residual echo suppressor, as the last stage of WebRTC's and Speex's cancellers, per
// band of a 512-point STFT (hop 256):
// - the echo expected in the mic is the reference's power at the measured speaker-to-mic delay, smeared like a room's
//   reverb, times the band's coupling (a tracker of mic/reference that leans to the quieter ratios: echo alone is the
//   quietest the mic gets while their voice plays);
// - the band is turned down by how much of it is echo (floor -30 dB);
// - only while there is an echo path: how much of the mic follows the reference linearly (cross-spectra) is ~0 with
//   earbuds or a headset, and then nothing is touched (my voice over theirs stays whole). With no voice playing it
//   passes untouched too.
// Measured (lib/tests/echo-guard.test.ts, and recorded scenarios: 2 talkers, 150-400 ms, reverb, a clipping speaker):
// 23-30 dB of echo taken out, my voice alone within 0.4 dB, nothing changed with earbuds.
// Loadable by node (lib/tests/echo-guard.test.ts).
const N = 512
const HOP = 256
const BINS = N / 2 + 1
const HIST = 360 // hops of reference kept: 1.9 s at 48 kHz
const MAXD = 128 // the longest speaker-to-mic delay looked for: 680 ms at 48 kHz (phone output + input latency)
const FLOOR = 0.03
const OVER = 8 // echo is overestimated (9 dB): what's left of it is worse than a slightly duller voice
const SMEAR = 0.82 // per hop: a ~70 ms reverb tail at 48 kHz
const DOWN = 0.05 // the coupling tracker, log domain, per hop
const UP = 0.01
const TAPS = 6 // reference frames around the delay whose cross-spectra with the mic tell an echo path
const AC = 0.95 // cross-spectra smoothing per hop
const PATH = 0.5 // the share of the mic explained by them, below which there is no echo path (measured: echo >= 0.8,
// earbuds <= 0.3)

/** Band edges (bins), about a third of an octave from 100 Hz, merged below; the first takes in DC and rumble too. */
function bands(rate) {
  const edges = [0]
  for (let f = 100; f < rate / 2; f *= 1.26) {
    const b = Math.round((f * N) / rate)
    if (b > edges[edges.length - 1] + 1) edges.push(b)
  }
  if (edges[edges.length - 1] < BINS) edges.push(BINS)
  return edges
}

// radix-2 FFT of size N in place, with its bit reversal and twiddles worked out once
const REV = Uint16Array.from({ length: N }, (_, i) => {
  let r = 0
  for (let b = 1, v = i; b < N; b <<= 1, v >>= 1) r = (r << 1) | (v & 1)
  return r
})
const COS = Float64Array.from({ length: N / 2 }, (_, k) => Math.cos((-2 * Math.PI * k) / N))
const SIN = Float64Array.from({ length: N / 2 }, (_, k) => Math.sin((-2 * Math.PI * k) / N))
function fft(re, im) {
  for (let i = 0; i < N; i++) {
    const j = REV[i]
    if (i < j) {
      const r = re[i], m = im[i]
      re[i] = re[j], im[i] = im[j], re[j] = r, im[j] = m
    }
  }
  for (let len = 2, step = N / 2; len <= N; len <<= 1, step >>= 1) {
    const half = len >> 1
    for (let i = 0; i < N; i += len)
      for (let k = 0; k < half; k++) {
        const c = COS[k * step], s = SIN[k * step], a = i + k, b = a + half
        const vr = re[b] * c - im[b] * s, vi = re[b] * s + im[b] * c
        re[b] = re[a] - vr
        im[b] = im[a] - vi
        re[a] += vr
        im[a] += vi
      }
  }
}

class EchoGuard extends (typeof AudioWorkletProcessor === 'undefined' ? class {} : AudioWorkletProcessor) {
  constructor(o, rate = typeof sampleRate === 'number' ? sampleRate : 48000) {
    super(o)
    this.edges = bands(rate)
    const nb = this.edges.length - 1
    this.win = Float32Array.from({ length: N }, (_, n) => Math.sqrt(0.5 - 0.5 * Math.cos((2 * Math.PI * n) / N))) // √Hann, analysis and synthesis
    this.micIn = new Float32Array(N)
    this.refIn = new Float32Array(N)
    this.out = new Float32Array(N + HOP)
    this.fill = 0
    this.re = new Float64Array(N)
    this.im = new Float64Array(N)
    this.hist = Array.from({ length: HIST }, () => new Float32Array(nb)) // reference band power, newest at `at`
    this.spec = Array.from({ length: HIST }, () => new Float32Array(2 * BINS)) // ... and its spectrum
    this.at = 0
    this.envM = new Float32Array(HIST) // log total power, for the delay
    this.envR = new Float32Array(HIST)
    this.delay = Math.round((0.12 * rate) / HOP)
    this.known = false // the delay has been measured
    this.candidate = -99
    this.tick = 0
    this.smear = new Float32Array(nb)
    this.smearX = new Float32Array(nb)
    this.coup = new Float32Array(nb) // log of the band's coupling: starts at 0 dB and comes down to the room's
    this.sm = new Float32Array(nb) // the mic's band power, smoothed over ~20 ms
    this.sr = new Float32Array(nb) // the delayed reference's, smoothed the same
    this.mic = new Float32Array(nb)
    this.late = new Float32Array(nb)
    this.gain = new Float32Array(nb).fill(1)
    this.sxd = Array.from({ length: TAPS }, () => new Float32Array(2 * BINS)) // smoothed conj(ref) x mic, per tap and bin
    this.sxx = Array.from({ length: TAPS }, () => new Float32Array(BINS))
    this.sdd = new Float32Array(BINS)
    this.path = 1 // the share of the mic that follows their voice (slowly; it starts as if there were an echo path)
    this.pending = new Float32Array(HOP)
    this.pendingAt = HOP
  }

  /** The speaker-to-mic delay (hops) where the mic's loudness follows the reference's best, if it clearly does. */
  findDelay() {
    let best = -1, bestC = 0.45, cur = 0
    const n = HIST - MAXD
    for (let d = 1; d < MAXD; d++) {
      let sm = 0, sr = 0, smm = 0, srr = 0, smr = 0
      for (let i = 0; i < n; i++) {
        const m = this.envM[(this.at - i + HIST) % HIST], r = this.envR[(this.at - i - d + 2 * HIST) % HIST]
        sm += m; sr += r; smm += m * m; srr += r * r; smr += m * r
      }
      const c = (smr - (sm * sr) / n) / Math.sqrt(Math.max(1e-9, (smm - (sm * sm) / n) * (srr - (sr * sr) / n)))
      if (d === this.delay) cur = c
      if (c > bestC) (bestC = c), (best = d)
    }
    // a new delay only when it clearly beats the one in use, twice in a row (my own talking makes passing peaks)
    const sure = best > 0 && bestC > 0.75 && bestC > cur + 0.1
    if (sure && Math.abs(best - this.candidate) <= 2) (this.delay = best), (this.known = true)
    this.candidate = sure ? best : -99
  }

  /** Band powers of [x] windowed, into [into]; returns the total. The spectrum stays in re/im. */
  spectrum(x, into) {
    const { edges, re, im, win } = this
    for (let i = 0; i < N; i++) (re[i] = x[i] * win[i]), (im[i] = 0)
    fft(re, im)
    let tot = 0
    for (let b = 0; b < edges.length - 1; b++) {
      let p = 0
      for (let k = edges[b]; k < edges[b + 1]; k++) p += re[k] * re[k] + im[k] * im[k]
      into[b] = p / (edges[b + 1] - edges[b])
      tot += p
    }
    return tot
  }

  /**
   * How much of the mic's power (0-1, more where reference frames overlap) the reference frames around the delay
   * explain linearly, from smoothed cross-spectra: echo is a copy of their voice, my voice and noise are not.
   */
  explained(re, im) {
    let num = 0, den = 0
    for (let k = 0; k < BINS; k++) (this.sdd[k] = AC * this.sdd[k] + (1 - AC) * (re[k] * re[k] + im[k] * im[k])), (den += this.sdd[k])
    for (let j = 0; j < TAPS; j++) {
      const x = this.spec[(this.at - Math.max(0, this.delay - 2 + j) + HIST) % HIST], s = this.sxd[j], p = this.sxx[j]
      for (let k = 0; k < BINS; k++) {
        const xr = x[2 * k], xi = x[2 * k + 1]
        s[2 * k] = AC * s[2 * k] + (1 - AC) * (xr * re[k] + xi * im[k])
        s[2 * k + 1] = AC * s[2 * k + 1] + (1 - AC) * (xr * im[k] - xi * re[k])
        p[k] = AC * p[k] + (1 - AC) * (xr * xr + xi * xi)
        if (p[k] > 1e-12) num += (s[2 * k] * s[2 * k] + s[2 * k + 1] * s[2 * k + 1]) / p[k]
      }
    }
    return den > 1e-12 ? num / den : 0
  }

  /** One hop: the last N samples of mic and reference in, HOP samples out (into `pending`). */
  hop() {
    const { edges, re, im, win, mic } = this
    const nb = edges.length - 1
    this.at = (this.at + 1) % HIST
    const rTot = this.spectrum(this.refIn, this.hist[this.at])
    const sp = this.spec[this.at]
    for (let k = 0; k < BINS; k++) (sp[2 * k] = re[k]), (sp[2 * k + 1] = im[k])
    const mTot = this.spectrum(this.micIn, mic) // the mic's spectrum stays in re/im for the output
    this.envM[this.at] = Math.log(mTot + 1e-9)
    this.envR[this.at] = Math.log(rTot + 1e-9)
    if (++this.tick % 24 === 0 && rTot > 1e-6) this.findDelay()
    // the reference around the delay (a loudness-based delay reads a little late; the echo's start must be covered)
    const late = this.late
    late.fill(0)
    for (let d = this.delay - 4; d <= this.delay + 1; d++) {
      const h = this.hist[(this.at - Math.max(0, d) + HIST) % HIST]
      for (let b = 0; b < nb; b++) late[b] = Math.max(late[b], h[b])
    }
    const exact = this.hist[(this.at - this.delay + HIST) % HIST]
    let fresh = 0, all = 0
    for (let b = 0; b < nb; b++) {
      this.smear[b] = Math.max(late[b], this.smear[b] * SMEAR)
      // the coupling learns from the reference at the delay itself (the wider window is ahead of the echo's start)
      this.smearX[b] = Math.max(exact[b], this.smearX[b] * SMEAR)
      fresh += exact[b]
      all += this.smearX[b]
    }
    // the echo path, learnt while their voice is on at the delay (slower until the delay is measured)
    const share = this.explained(re, im)
    if (rTot > 1e-6 && fresh > 0.5 * all) this.path += (this.known ? 0.01 : 0.003) * (Math.min(2, share) - this.path)
    const path = this.path >= PATH
    for (let b = 0; b < nb; b++) {
      this.sm[b] = 0.7 * this.sm[b] + 0.3 * mic[b]
      this.sr[b] = 0.7 * this.sr[b] + 0.3 * this.smearX[b]
      let g = 1
      if (this.sr[b] > 1e-10) {
        // Down only while their voice is on at the delay: in its fading tail a mic gone quiet (Krisp or the browser
        // gating between words) would read as no coupling, and the next echo would pass until it relearnt (measured:
        // ~1 s). Up in the tail too (a room's reverb). A silent mic says nothing.
        const lr = Math.log((this.sm[b] + 1e-12) / this.sr[b])
        if (this.sm[b] > 1e-7 && (lr > this.coup[b] || exact[b] > 0.5 * this.smearX[b]))
          this.coup[b] += (lr < this.coup[b] ? DOWN : UP) * (lr - this.coup[b])
        if (path) g = Math.max(FLOOR, 1 - (OVER * Math.exp(this.coup[b]) * this.smear[b]) / (mic[b] + 1e-12))
      }
      // down at once (echo arrives), back up over ~30 ms
      this.gain[b] = g < this.gain[b] ? g : this.gain[b] + 0.15 * (g - this.gain[b])
    }
    for (let b = 0; b < nb; b++)
      for (let k = edges[b]; k < edges[b + 1]; k++) {
        const g = this.gain[b]
        re[k] *= g
        im[k] *= g
        if (k > 0 && k < N / 2) (re[N - k] = re[k]), (im[N - k] = -im[k])
      }
    // inverse FFT by conjugation, then √Hann and overlap-add
    for (let i = 0; i < N; i++) im[i] = -im[i]
    fft(re, im)
    for (let i = 0; i < N; i++) this.out[i] += (re[i] / N) * win[i]
    this.pending.set(this.out.subarray(0, HOP)) // read out over the next HOP samples, before the next hop
    this.out.copyWithin(0, HOP)
    this.out.fill(0, N)
    this.micIn.copyWithin(0, HOP)
    this.refIn.copyWithin(0, HOP)
  }

  process(inputs, outputs) {
    const mic = inputs[0]?.[0]
    const ref = inputs[1]?.[0]
    const out = outputs[0]?.[0]
    if (!out) return true
    for (let i = 0; i < out.length; i++) {
      this.micIn[N - HOP + this.fill] = mic ? mic[i] : 0
      this.refIn[N - HOP + this.fill] = ref ? ref[i] : 0
      if (++this.fill === HOP) {
        this.fill = 0
        this.hop()
        this.pendingAt = 0
      }
      out[i] = this.pendingAt < HOP ? this.pending[this.pendingAt++] : 0
    }
    for (let c = 1; c < outputs[0].length; c++) outputs[0][c].set(out)
    return true
  }
}

if (typeof registerProcessor === 'function') registerProcessor('echo-guard', EchoGuard)
if (typeof module === 'object') module.exports = { EchoGuard }
