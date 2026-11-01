'use client'

import { useEffect, useRef, useState } from 'react'
import { Pause, Play, SendHorizontal, Trash2 } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { useToast } from '@/components/ui/toast'
import { isAppleWebKit } from '@/lib/rtc'
import { cn } from '@/lib/utils'

// Voice messages (099): the composer's "+" menu starts one (as KakaoTalk's); it records
// (up to 30 s, the owner 2026-10-31), the bar shows my voice as moving bars (WhatsApp's recording waveform) and the time,
// with Delete and Send; the clip goes out as chat media of kind 'audio'.

const MAX_MS = 30_000
const MIN_MS = 800
/**
 * Opus in WebM on Chrome, Firefox and Android; AAC in MP4 on iPhone, iPad and Mac Safari, which every browser plays.
 * (WebKit records WebM too since 18.4, so asking for WebM first gave iPads WebM clips: the production logs of
 * 2026-10-31 show one from an iPad.)
 */
const types = () =>
  isAppleWebKit(navigator.userAgent, navigator.maxTouchPoints)
    ? ['audio/mp4', 'audio/webm;codecs=opus']
    : ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus']
const BARS = 32
const clock = (ms: number) =>
  Number.isFinite(ms) ? `${Math.floor(ms / 60000)}:${String(Math.floor((ms % 60000) / 1000)).padStart(2, '0')}` : '0:00'

export function canRecord() {
  return typeof MediaRecorder !== 'undefined' && !!navigator.mediaDevices?.getUserMedia
}

/** The recording bar in place of the text field: Delete, my voice as moving bars, the time, Send. */
export function VoiceRecorder({ onSend, onDone }: { onSend: (clip: File) => Promise<boolean>; onDone: () => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const [ms, setMs] = useState(0)
  const rec = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const started = useRef(0)
  const bars = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    let stream: MediaStream | null = null
    let timer: ReturnType<typeof setInterval> | undefined
    let ctx: AudioContext | undefined
    let gone = false
    void navigator.mediaDevices
      .getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
      .then((s) => {
        stream = s
        if (gone) return s.getTracks().forEach((x) => x.stop())
        const mimeType = types().find((x) => MediaRecorder.isTypeSupported(x))
        const r = new MediaRecorder(s, mimeType ? { mimeType, audioBitsPerSecond: 32_000 } : undefined)
        r.ondataavailable = (e) => e.data.size && chunks.current.push(e.data)
        r.start(250)
        rec.current = r
        started.current = Date.now()
        // The level of my voice: a new bar on the right every 80 ms, the rest move left (-60..-10 dBFS → 10..100%).
        ctx = new AudioContext()
        void ctx.resume()
        const meter = ctx.createAnalyser()
        meter.fftSize = 1024
        ctx.createMediaStreamSource(s).connect(meter)
        const wave = new Float32Array(meter.fftSize)
        timer = setInterval(() => {
          const now = Date.now() - started.current
          setMs(Math.min(now, MAX_MS))
          if (now >= MAX_MS && r.state === 'recording') r.pause() // the limit: it waits for Send or Delete
          if (r.state !== 'recording' || !bars.current) return
          meter.getFloatTimeDomainData(wave)
          const db = 10 * Math.log10(wave.reduce((sum, v) => sum + v * v, 0) / wave.length + 1e-10)
          const kids = bars.current.children as HTMLCollectionOf<HTMLElement>
          for (let i = 0; i < BARS - 1; i++) kids[i].style.height = kids[i + 1].style.height
          kids[BARS - 1].style.height = `${Math.round(10 + 90 * Math.min(1, Math.max(0, (db + 60) / 50)))}%`
        }, 80)
      })
      .catch(() => {
        toast.show(t('video.micDenied'), 'error')
        onDone()
      })
    return () => {
      gone = true
      clearInterval(timer)
      void ctx?.close()
      if (rec.current && rec.current.state !== 'inactive') rec.current.stop()
      stream?.getTracks().forEach((x) => x.stop())
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const stop = (send: boolean) => {
    const r = rec.current
    if (!r) return onDone()
    const length = Math.min(Date.now() - started.current, MAX_MS)
    r.onstop = async () => {
      const type = (r.mimeType || 'audio/webm').split(';')[0]
      const clip = new File(chunks.current, `voice.${type === 'audio/mp4' ? 'm4a' : type.split('/')[1]}`, { type })
      if (send && length >= MIN_MS && clip.size) await onSend(clip)
      onDone()
    }
    r.stop()
  }

  return (
    <div className="flex min-w-0 flex-1 items-center gap-2 ps-1" role="status">
      <button
        type="button"
        onClick={() => stop(false)}
        className="grid size-9 shrink-0 place-items-center rounded-full text-destructive transition-colors hover:bg-background"
        aria-label={t('common.cancel')}
      >
        <Trash2 className="size-5" aria-hidden />
      </button>
      <span className="size-2.5 shrink-0 animate-pulse rounded-full bg-destructive" aria-hidden />
      <span ref={bars} className="flex h-6 min-w-0 flex-1 items-center justify-end gap-[2px] overflow-hidden" aria-hidden>
        {Array.from({ length: BARS }, (_, i) => (
          <span key={i} className="w-[3px] shrink-0 rounded-full bg-primary transition-[height] duration-75" style={{ height: '10%' }} />
        ))}
      </span>
      <span className="shrink-0 text-[15px] tabular-nums">
        {clock(ms)} <span className="text-muted-foreground">/ {clock(MAX_MS)}</span>
      </span>
      <button
        type="button"
        onClick={() => stop(true)}
        className="grid size-9 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground transition-colors hover:bg-primary-hover"
        aria-label={t('composer.send')}
      >
        <SendHorizontal className="size-[18px] rtl:rotate-180" aria-hidden />
      </button>
    </div>
  )
}

/** A voice message in the chat: play / pause, how far along, its length. */
export function AudioBubble({ url, mine }: { url: string; mine: boolean }) {
  const { t } = useI18n()
  const ref = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const [at, setAt] = useState(0)
  const [length, setLength] = useState(0)
  const onMeta = () => {
    const a = ref.current!
    // A recorder's WebM carries no length: seeking far makes the browser find it, then back to the start.
    if (!Number.isFinite(a.duration)) {
      a.currentTime = 1e101
      a.ontimeupdate = () => {
        a.ontimeupdate = null
        a.currentTime = 0
        setLength(a.duration)
      }
    } else setLength(a.duration)
  }
  return (
    <div className="flex w-56 max-w-full items-center gap-2.5 px-2.5 py-2">
      <audio
        ref={ref}
        src={url}
        preload="metadata"
        onLoadedMetadata={onMeta}
        onTimeUpdate={(e) => setAt(e.currentTarget.currentTime)}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => (setPlaying(false), setAt(0))}
      />
      <button
        type="button"
        onClick={() => (playing ? ref.current?.pause() : void ref.current?.play())}
        className={cn('grid size-9 shrink-0 place-items-center rounded-full', mine ? 'bg-white/25' : 'bg-foreground/10')}
        aria-label={t('composer.voice')}
        aria-pressed={playing}
      >
        {playing ? <Pause className="size-4 fill-current" aria-hidden /> : <Play className="size-4 translate-x-px fill-current" aria-hidden />}
      </button>
      <span className="relative h-1 flex-1 overflow-hidden rounded-full bg-current/25" aria-hidden>
        <span className="absolute inset-y-0 start-0 rounded-full bg-current" style={{ width: `${length ? Math.min(100, (at / length) * 100) : 0}%` }} />
      </span>
      <span className="shrink-0 text-xs tabular-nums opacity-80">{clock((playing || at ? at : length) * 1000)}</span>
    </div>
  )
}
