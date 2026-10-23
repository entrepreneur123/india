// A short chime when someone comes into or leaves the voice room (me included). It plays from an <audio> element, as
// the room's voices do: a Web Audio context next to WebRTC can make the call crackle on iPhone and opens a second
// audio stream on Android (lib/voice-audio.ts). The two sounds are made here, once, as tiny WAV files.

const RATE = 22_050
const KEY = 'yaari_room_sounds'

/** A soft two-note chime as a 16-bit mono WAV: each note fades in fast and out slowly. */
export function chimeWav(notes: number[], noteMs = 90, gain = 0.22): Uint8Array {
  const per = Math.round((RATE * noteMs) / 1000)
  const n = per * notes.length
  const out = new Uint8Array(44 + n * 2)
  const v = new DataView(out.buffer)
  const text = (at: number, s: string) => [...s].forEach((c, i) => v.setUint8(at + i, c.charCodeAt(0)))
  text(0, 'RIFF'); v.setUint32(4, 36 + n * 2, true); text(8, 'WAVE')
  text(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true)
  v.setUint32(24, RATE, true); v.setUint32(28, RATE * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true)
  text(36, 'data'); v.setUint32(40, n * 2, true)
  for (let i = 0; i < n; i++) {
    const t = (i % per) / per
    const env = Math.min(1, t * 20) * (1 - t) ** 2
    v.setInt16(44 + i * 2, Math.round(Math.sin((2 * Math.PI * notes[Math.floor(i / per)] * i) / RATE) * env * gain * 32_767), true)
  }
  return out
}

/** Who is in the room, and who of them is on the stage (talking in the voice chat). */
export type Crowd = { all: readonly string[]; stage: readonly string[] }

/**
 * What changed worth a sound: someone else came into the room, or anyone (me too) came up on the stage → 'join';
 * someone else left the room, or anyone stepped off the stage → 'leave'.
 */
export function crowdChange(before: Crowd, after: Crowd, meId: string): 'join' | 'leave' | null {
  const was = new Set(before.all), now = new Set(after.all), wasUp = new Set(before.stage), up = new Set(after.stage)
  if (after.all.some((id) => id !== meId && !was.has(id)) || after.stage.some((id) => !wasUp.has(id))) return 'join'
  if (before.all.some((id) => id !== meId && !now.has(id)) || before.stage.some((id) => !up.has(id))) return 'leave'
  return null
}

export function roomSoundsOn() {
  try {
    return localStorage.getItem(KEY) !== '0'
  } catch {
    return true
  }
}

export function setRoomSounds(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? '1' : '0')
  } catch {
    /* private mode: on for this visit only */
  }
}

/** In: two high notes going up (A5 → D6). Out: two low notes going down (G4 → C4). */
export const JOIN = [880, 1175]
export const LEAVE = [392, 262]

const urls: Partial<Record<'join' | 'leave', string>> = {}
let last = 0

/**
 * Plays the chime: others' at most one every 1.2 s, so a busy room doesn't keep ringing; `mine` (my own steps)
 * always. A blocked autoplay is silent.
 */
export function playRoomSound(kind: 'join' | 'leave', mine = false) {
  const now = Date.now()
  if (!roomSoundsOn() || (!mine && now - last < 1200)) return
  last = now
  urls[kind] ??= URL.createObjectURL(new Blob([chimeWav(kind === 'join' ? JOIN : LEAVE) as BlobPart], { type: 'audio/wav' }))
  const a = new Audio(urls[kind])
  a.volume = 0.6
  void a.play().catch(() => undefined)
}
