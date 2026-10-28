// Microphone hints, the rules of Jitsi Meet's lib-jitsi-meet (modules/detection/VADTalkMutedDetection.ts and
// NoAudioSignalDetection.ts, Apache-2.0, read 2026-10-28), written again here for our levels. No imports
// (node --test).
//  * Talking while muted: a loud moment opens a 700 ms window; if the window's average is loud too, it is speech
//    (said once per muted spell). Jitsi scores speech with RNNoise (start > 0.9, average > 0.6); we have the level
//    (RMS) of the echo-cancelled mic, so the bars are levels.
//  * No signal: a source level of exactly 0 for 4 s means the device sends nothing at all (muted on the headset or
//    in the system); said once per microphone, taken back as soon as any sound comes.

export const TALK_START = 0.06
export const TALK_AVG = 0.03
export const TALK_WINDOW_MS = 700
export const SILENCE_MS = 4_000

/** Fed the muted mic's level with a time (ms); true once, when it heard speech. */
export function talkDetector() {
  let start = -1
  let sum = 0
  let n = 0
  let done = false
  return (level: number, now: number) => {
    if (done) return false
    if (start < 0) {
      if (level > TALK_START) [start, sum, n] = [now, level, 1]
      return false
    }
    sum += level
    n++
    if (now - start < TALK_WINDOW_MS) return false
    start = -1
    return (done = sum / n > TALK_AVG)
  }
}

/** Fed the mic's source level with a time (ms): 'silent' once after 4 s of exact zeros, 'sound' when sound comes. */
export function silenceDetector() {
  let since = -1
  let said = false
  return (level: number, now: number): 'silent' | 'sound' | null => {
    if (level > 0) {
      since = -1
      return 'sound'
    }
    if (said) return null
    if (since < 0) since = now
    if (now - since < SILENCE_MS) return null
    said = true
    return 'silent'
  }
}

/** The RMS of one block of samples. */
export const rms = (buf: Float32Array) => Math.sqrt(buf.reduce((s, x) => s + x * x, 0) / buf.length)
