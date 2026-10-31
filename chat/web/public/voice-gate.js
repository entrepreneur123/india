// The microphone gate on computers (lib/voice-gate.ts), the web twin of the Android app's VoiceGate.kt: the microphone
// goes out only while I'm talking. Louder than -40 dBFS opens it; 400 ms under -46 dBFS closes it (silence), with a
// 5 ms ramp so it never clicks. The hiss of a quiet room and what's left of the room's voices after echo cancellation
// are never sent, so they can't build up into a hiss or an echo loop.
const OPEN = 0.01
const CLOSE = 0.005
const HOLD = 0.4

class VoiceGate extends AudioWorkletProcessor {
  open = false
  quiet = 0
  gain = 0

  process([input], [output]) {
    const x = input?.[0]
    const y = output?.[0]
    if (!x || !y) return true
    let sum = 0
    for (let i = 0; i < x.length; i++) sum += x[i] * x[i]
    const rms = Math.sqrt(sum / x.length)
    if (rms >= OPEN) {
      this.open = true
      this.quiet = 0
    } else if (rms < CLOSE && this.open) {
      this.quiet += x.length / sampleRate
      if (this.quiet >= HOLD) this.open = false
    } else this.quiet = 0
    const target = this.open ? 1 : 0
    const step = 1 / (0.005 * sampleRate)
    for (let i = 0; i < x.length; i++) {
      this.gain += Math.max(-step, Math.min(step, target - this.gain))
      y[i] = x[i] * this.gain
    }
    for (let c = 1; c < output.length; c++) output[c].set(y)
    return true
  }
}

registerProcessor('voice-gate', VoiceGate)
