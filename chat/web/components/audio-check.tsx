'use client'
// /audio-check: measures this one device's voice path with nothing of ours in it (no LiveKit, no Krisp, no network):
// the microphone settings the browser really applied, how much of a sound played the way the rooms play voices
// (a WebRTC stream in an <audio>) the browser's own echo cancellation takes out of the microphone, and a recording
// of the microphone to listen to. Also the per-browser switch that sends the microphone untouched in rooms and
// calls (lib/rtc micRaw), to tell a bad browser path from a bad room one device at a time.
import { useRef, useState } from 'react'
import { canGate } from '@/lib/voice-gate'
import { isAppleWebKit, micRaw, setMicRaw } from '@/lib/rtc'

const ON = { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
const OFF = { echoCancellation: false, noiseSuppression: false, autoGainControl: false }
/** The test sound: bursts of band-limited noise (speech energy, no words), half a second on, half a second off. */
const SECONDS = 6
const db = (v: number) => Math.round(20 * Math.log10(Math.max(v, 1e-6)))

const concat = (parts: Float32Array[]) => {
  const out = new Float32Array(parts.reduce((n, p) => n + p.length, 0))
  let at = 0
  for (const p of parts) out.set(p, (at += p.length) - p.length)
  return out
}

/**
 * Up to [seconds] of [track] as PCM, and when its first sound came (−1: none; a phone can hand over digital silence
 * for seconds after the microphone opens). [untilSound]: stop at that first sound. A ScriptProcessor, which every
 * phone browser still runs; its output stays silent.
 */
function record(ctx: AudioContext, track: MediaStreamTrack, seconds: number, untilSound = false) {
  return new Promise<{ pcm: Float32Array; first: number }>((resolve) => {
    const src = ctx.createMediaStreamSource(new MediaStream([track]))
    const node = ctx.createScriptProcessor(4096, 1, 1)
    const parts: Float32Array[] = []
    let n = 0
    let first = -1
    const want = seconds * ctx.sampleRate
    const done = () => {
      src.disconnect()
      node.disconnect()
      resolve({ pcm: concat(parts), first })
    }
    node.onaudioprocess = (e) => {
      if (n >= want) return
      const d = e.inputBuffer.getChannelData(0).slice()
      parts.push(d)
      if (first < 0) {
        const i = d.findIndex((v) => Math.abs(v) > 1e-4)
        if (i >= 0) first = (n + i) / ctx.sampleRate
      }
      n += d.length
      if (n >= want || (untilSound && first >= 0)) done()
    }
    src.connect(node).connect(ctx.destination)
  })
}

const started = (first: number) => (first < 0 ? '15초 넘게 무음' : `${first.toFixed(1)}초 뒤`)
/** Waits for the microphone to hand over sound (max 15 s) and says how long that took. */
async function warm(ctx: AudioContext, t: MediaStreamTrack, log: (l: string) => void, what: string) {
  const { first } = await record(ctx, t, 15, true)
  log(`${what}: 마이크 소리 시작 ${started(first)}`)
  return first >= 0
}

/** Mean level of the loudest and the quietest 30 % of 100 ms frames. Echo left in the mic = loud − quiet. */
function levels(x: Float32Array, rate: number) {
  const frame = Math.round(rate / 10)
  const r: number[] = []
  for (let i = 0; i + frame <= x.length; i += frame) {
    let s = 0
    for (let j = i; j < i + frame; j++) s += x[j] * x[j]
    r.push(Math.sqrt(s / frame))
  }
  r.sort((a, b) => a - b)
  const k = Math.max(1, Math.floor(r.length * 0.3))
  const mean = (a: number[]) => a.reduce((s, v) => s + v, 0) / a.length
  return { quiet: db(mean(r.slice(0, k))), loud: db(mean(r.slice(-k))) }
}

function farEnd(ctx: AudioContext) {
  const buf = ctx.createBuffer(1, SECONDS * ctx.sampleRate, ctx.sampleRate)
  const d = buf.getChannelData(0)
  for (let i = 0; i < d.length; i++) d[i] = (i / ctx.sampleRate) % 1 < 0.5 ? (Math.random() * 2 - 1) * 0.5 : 0
  const s = ctx.createBufferSource()
  s.buffer = buf
  const f = ctx.createBiquadFilter()
  f.type = 'bandpass'
  f.frequency.value = 1000
  f.Q.value = 0.4
  const out = ctx.createMediaStreamDestination()
  s.connect(f).connect(out)
  return { start: () => s.start(), track: out.stream.getAudioTracks()[0] }
}

/** [track] through a local WebRTC loop, so the browser plays it as it plays a room's voices (and its echo canceller sees it). */
async function loop(track: MediaStreamTrack) {
  const a = new RTCPeerConnection()
  const b = new RTCPeerConnection()
  a.onicecandidate = (e) => e.candidate && void b.addIceCandidate(e.candidate)
  b.onicecandidate = (e) => e.candidate && void a.addIceCandidate(e.candidate)
  const got = new Promise<MediaStream>((r) => (b.ontrack = (e) => r(new MediaStream([e.track]))))
  a.addTrack(track)
  await a.setLocalDescription(await a.createOffer())
  await b.setRemoteDescription(a.localDescription!)
  await b.setLocalDescription(await b.createAnswer())
  await a.setRemoteDescription(b.localDescription!)
  return { stream: await got, close: () => (a.close(), b.close()) }
}

const mic = (c: MediaTrackConstraints) => navigator.mediaDevices.getUserMedia({ audio: c }).then((s) => s.getAudioTracks()[0])
const settings = (t: MediaStreamTrack) => {
  const s = t.getSettings() as Record<string, unknown>
  return ['echoCancellation', 'noiseSuppression', 'autoGainControl', 'voiceIsolation', 'sampleRate', 'channelCount', 'latency']
    .filter((k) => s[k] !== undefined).map((k) => `${k}=${String(s[k])}`).join(' ')
}

export function AudioCheck() {
  const [lines, setLines] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [raw, setRaw] = useState(micRaw)
  const ctxRef = useRef<AudioContext | null>(null)
  const audioRef = useRef<HTMLAudioElement>(null)
  const voice = useRef<Float32Array | null>(null)
  const log = (l: string) => setLines((x) => [...x, l])
  const ctx = () => (ctxRef.current ??= new AudioContext())

  const run = (job: () => Promise<void>) => async () => {
    if (busy) return
    setBusy(true)
    try {
      await ctx().resume()
      await job()
    } catch (e) {
      log(`오류: ${(e as Error)?.name ?? ''} ${(e as Error)?.message ?? String(e)}`)
    } finally {
      setBusy(false)
    }
  }

  const info = run(async () => {
    const ua = navigator.userAgent
    log(`브라우저: ${ua}`)
    log(`AudioContext ${ctx().sampleRate} Hz, AudioWorklet ${'AudioWorkletNode' in window ? '있음' : '없음'}, Apple WebKit ${isAppleWebKit(ua, navigator.maxTouchPoints) ? '예' : '아니오'}`)
    const { isKrispNoiseFilterSupported } = await import('@livekit/krisp-noise-filter')
    const krisp = isKrispNoiseFilterSupported()
    log(`이 기기의 마이크 경로: ${raw ? '원본(Krisp 끔)' : krisp ? 'Krisp BVC' : canGate() ? '음성 게이트' : '원본'} (Krisp 지원 ${krisp ? '예' : '아니오'})`)
    const t = await mic(ON)
    log(`마이크 설정(요청: 에코제거·잡음제거·자동음량 켬): ${settings(t)}`)
    const caps = (t.getCapabilities?.() ?? {}) as Record<string, unknown>
    const cap = ['echoCancellation', 'noiseSuppression', 'autoGainControl', 'voiceIsolation', 'sampleRate'].filter((k) => caps[k] !== undefined).map((k) => `${k}=${JSON.stringify(caps[k])}`).join(' ')
    if (cap) log(`마이크 가능 범위: ${cap}`)
    t.stop()
  })

  /** Plays the test sound the way rooms play voices and says how much of it the microphone [t] still carries. */
  const measure = async (name: string, t: MediaStreamTrack) => {
    const far = farEnd(ctx())
    const { stream, close } = await loop(far.track)
    const el = audioRef.current!
    el.srcObject = stream
    await el.play()
    far.start()
    const { pcm, first } = await record(ctx(), t, SECONDS + 1)
    close()
    el.srcObject = null
    const { loud, quiet } = levels(pcm, ctx().sampleRate)
    log(`${name}: 소리 날 때 ${loud} dB, 조용할 때 ${quiet} dB → 마이크에 남은 소리 ${loud - quiet} dB${first < 0 ? ' (무음: 측정 무효)' : ''} (${settings(t)})`)
  }

  const echo = run(async () => {
    log(`에코 검사: 스피커로 ${SECONDS}초간 "쉬-쉬-" 소리가 두 번 납니다. 조용히 계세요.`)
    for (const [name, c] of [['에코제거 켬', ON], ['에코제거 끔', OFF]] as const) {
      const t = await mic(c)
      if (await warm(ctx(), t, log, name)) await measure(name, t)
      t.stop()
    }
    log('읽는 법: "켬"의 남은 소리가 "끔"보다 20 dB 이상 작으면 이 브라우저의 에코 제거가 일하는 것. 켬·끔 차이가 10 dB도 안 되면 이 브라우저 자체가 스피커 에코를 못 지우는 것.')
  })

  // The rooms' order: the microphone opens with everything on, then Krisp re-applies its settings with noise
  // suppression off (node_modules/@livekit/krisp-noise-filter: applyConstraints({...getSettings(), noiseSuppression:
  // false, voiceIsolation: false, sampleRate})). Does the echo cancellation survive that on this phone?
  const krispOrder = run(async () => {
    log('방 순서 검사: 마이크 열기 → Krisp식 설정 변경 → 에코 측정. 조용히 계세요.')
    const t = await mic(ON)
    if (!(await warm(ctx(), t, log, '열기'))) return t.stop()
    await t.applyConstraints({ ...t.getSettings(), noiseSuppression: false, voiceIsolation: false, sampleRate: ctx().sampleRate } as MediaTrackConstraints)
    if (await warm(ctx(), t, log, 'Krisp식 설정 변경 뒤')) await measure('설정 변경 뒤', t)
    t.stop()
  })

  const rec = run(async () => {
    log('5초 동안 말해 보세요…')
    const t = await mic(ON)
    const { pcm, first } = await record(ctx(), t, 5)
    t.stop()
    voice.current = pcm
    const { loud, quiet } = levels(pcm, ctx().sampleRate)
    log(`녹음 끝: 마이크 소리 시작 ${started(first)}, 말할 때 ${loud} dB, 조용할 때 ${quiet} dB. "들어보기"로 이 기기의 마이크 소리(우리 처리 없음)를 확인하세요.`)
  })

  const play = run(async () => {
    if (!voice.current) return log('먼저 녹음하세요.')
    const buf = ctx().createBuffer(1, voice.current.length, ctx().sampleRate)
    buf.copyToChannel(voice.current, 0)
    const s = ctx().createBufferSource()
    s.buffer = buf
    s.connect(ctx().destination)
    s.start()
  })

  const flag = (on: boolean) => {
    setMicRaw(on)
    setRaw(on)
    log(on ? '이 브라우저는 이제 방·통화에서 마이크를 원본 그대로 보냅니다(Krisp·게이트 끔). 방에 다시 들어가면 적용.' : '이 브라우저는 다시 Krisp(또는 게이트)를 씁니다. 방에 다시 들어가면 적용.')
  }

  const copy = () => void navigator.clipboard?.writeText(lines.join('\n')).catch(() => undefined)
  const btn = 'rounded-xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground disabled:opacity-50'
  return (
    <main className="mx-auto max-w-xl space-y-3 p-4">
      <h1 className="text-xl font-bold">Yaari 오디오 검사</h1>
      <p className="text-sm text-muted-foreground">기기 하나씩, 이어폰 없이, 평소 통화하는 음량으로. 결과는 아래에 쌓이고 "복사"로 보낼 수 있습니다.</p>
      <div className="flex flex-wrap gap-2">
        <button type="button" className={btn} disabled={busy} onClick={info}>1. 기기·마이크 정보</button>
        <button type="button" className={btn} disabled={busy} onClick={echo}>2. 에코 제거 검사</button>
        <button type="button" className={btn} disabled={busy} onClick={krispOrder}>4. 방 순서(Krisp 설정) 검사</button>
        <button type="button" className={btn} disabled={busy} onClick={rec}>3. 내 목소리 녹음 (5초)</button>
        <button type="button" className={btn} disabled={busy} onClick={play}>들어보기</button>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span>방·통화의 마이크: {raw ? '원본(Krisp 끔)' : '기본(Krisp)'}</span>
        <button type="button" className="rounded-lg border px-3 py-2" onClick={() => flag(!raw)}>{raw ? 'Krisp 다시 켜기' : '이 기기에서 Krisp 끄기'}</button>
        <button type="button" className="rounded-lg border px-3 py-2" onClick={copy}>복사</button>
      </div>
      <pre className="whitespace-pre-wrap break-all rounded-xl bg-muted p-3 text-xs">{lines.join('\n') || '아직 결과 없음'}</pre>
      <audio ref={audioRef} autoPlay playsInline />
    </main>
  )
}
