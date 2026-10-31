// Pure helpers for calls and voice rooms (no LiveKit import, so they are unit-tested in lib/tests/rtc.test.ts).

/**
 * True for Apple's WebKit: every browser on iPhone and iPad (Safari, Chrome, Firefox, the KakaoTalk or Instagram
 * built-in browsers all use it), iPadOS asking for the desktop site (it says "Macintosh" but has a touch screen),
 * and Safari on a Mac.
 */
export function isAppleWebKit(ua: string, maxTouchPoints = 0): boolean {
  if (/iPad|iPhone|iPod/.test(ua)) return true
  if (/Macintosh/.test(ua) && maxTouchPoints > 1) return true
  return /^((?!chrome|chromium|crios|fxios|edg|android).)*safari/i.test(ua)
}

/** iPhone and iPad (any browser): a page can't set a sound's volume there (HTMLMediaElement.volume is read-only). */
export const fixedVolume = (ua: string, maxTouchPoints = 0) => /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && maxTouchPoints > 1)

/**
 * Apple encodes H.264 in hardware but VP8 in software, which looks soft and blocky (and heats the iPad); everyone
 * else, and the Android app, sends VP8. Both are decoded everywhere, so the two sides need not match.
 */
export function videoCodecFor(ua: string, maxTouchPoints = 0): 'h264' | 'vp8' {
  return isAppleWebKit(ua, maxTouchPoints) ? 'h264' : 'vp8'
}

/** What went wrong turning on the camera or microphone, from the browser's DOMException name. */
export type DeviceProblem = 'denied' | 'missing' | 'busy' | 'other'

export function deviceProblem(err: unknown): DeviceProblem {
  const name = (err as { name?: string } | null)?.name ?? ''
  const message = (err as { message?: string } | null)?.message ?? ''
  if (name === 'NotAllowedError' || name === 'SecurityError' || name === 'PermissionDeniedError') return 'denied'
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') return 'missing'
  // Chrome on Windows and Android says NotReadableError when another app holds the camera; Firefox says AbortError.
  if (name === 'NotReadableError' || name === 'TrackStartError' || (name === 'AbortError' && /start|video|audio/i.test(message))) return 'busy'
  return 'other'
}

const RELAY_KEY = 'yaari_relay_until'
const RELAY_MS = 7 * 86_400_000
/** Media paths broken this many times in one session: this network loses UDP (Gulf carriers throttle it). */
export const RELAY_AFTER = 2

/**
 * True for a week after this browser's media connection broke (LiveKit gave up, or had to rebuild its path twice in
 * one session): calls and rooms then start on the server's relay (TURN, over TLS on 443 where UDP fails), and a
 * friend call skips the direct attempt. LiveKit's advice for networks that throttle UDP: relay first, sticky per user.
 */
export function relayFirst(now = Date.now()): boolean {
  try {
    return Number(localStorage.getItem(RELAY_KEY)) > now
  } catch {
    return false
  }
}

export function preferRelay(now = Date.now()) {
  try {
    localStorage.setItem(RELAY_KEY, String(now + RELAY_MS))
  } catch {
    /* private mode: this session only knows */
  }
}

/** For room.connect: relay only, still with the server's own TURN list (LiveKit fills iceServers when none are given). */
export const connectOptions = () => (relayFirst() ? { rtcConfig: { iceTransportPolicy: 'relay' as const } } : undefined)

/** LiveKit's ConnectionQuality values ('excellent' | 'good' | 'poor' | 'lost' | 'unknown'). */
export type Quality = string

/** Bars for the connection indicator: 3 excellent, 2 good, 1 poor, 0 lost; null while LiveKit doesn't know yet. */
export function signalBars(q: Quality): 0 | 1 | 2 | 3 | null {
  return q === 'excellent' ? 3 : q === 'good' ? 2 : q === 'poor' ? 1 : q === 'lost' ? 0 : null
}

/** A direct call's path (lib/p2p-call: round trip in s, share of packets lost) in LiveKit's words, for the same bars. */
export function pathQuality(rtt: number, loss: number): 'excellent' | 'good' | 'poor' {
  if (rtt < 0.15 && loss < 0.02) return 'excellent'
  if (rtt < 0.4 && loss < 0.08) return 'good'
  return 'poor'
}

/**
 * What to tell me about my partner, most serious first: their connection dropped ('lost': their picture would
 * otherwise freeze on the last frame), their video is paused because the network can't carry it ('paused'), or
 * their connection is poor ('weak'). Null when all is well.
 */
export function partnerNotice(p: { quality: Quality; videoPaused: boolean; left: boolean }): 'lost' | 'paused' | 'weak' | null {
  if (p.left || p.quality === 'lost') return 'lost'
  if (p.videoPaused) return 'paused'
  if (p.quality === 'poor') return 'weak'
  return null
}

/** The message (a `video.*` key) for what couldn't be turned on at the start of a call; null when both are on. */
export function deviceNotice(
  camera: DeviceProblem | null,
  mic: DeviceProblem | null,
): 'blocked' | 'camDenied' | 'micDenied' | 'noCamera' | 'noMic' | 'deviceBusy' | null {
  if (!camera && !mic) return null
  if (camera === 'busy' || mic === 'busy') return 'deviceBusy'
  if (camera === 'denied' && mic === 'denied') return 'blocked'
  if (mic === 'denied') return 'micDenied'
  if (camera === 'denied') return 'camDenied'
  if (mic === 'missing') return 'noMic'
  if (camera === 'missing') return 'noCamera'
  return 'blocked'
}

/**
 * Keeps the screen on (Screen Wake Lock) until the returned function is called. The browser drops the lock whenever
 * the page is hidden, so it is taken again each time the page comes back. Does nothing where unsupported.
 */
export function keepScreenOn(): () => void {
  let lock: { release(): Promise<void> } | null = null
  let done = false
  const take = async () => {
    if (done || document.visibilityState !== 'visible' || !('wakeLock' in navigator)) return
    try {
      const l = await navigator.wakeLock.request('screen')
      if (done) void l.release().catch(() => undefined)
      else lock = l
    } catch {
      /* refused (battery saver, not allowed in this frame): the call works anyway */
    }
  }
  const onVisible = () => void take()
  document.addEventListener('visibilitychange', onVisible)
  void take()
  return () => {
    done = true
    document.removeEventListener('visibilitychange', onVisible)
    void lock?.release().catch(() => undefined)
    lock = null
  }
}
