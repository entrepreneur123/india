'use client'

import { useEffect, useRef, useState } from 'react'
import { Mic, Pause, Play, SendHorizontal, Trash2 } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

// Voice messages (099), as Instagram's and WhatsApp's: the mic sits where Send is while nothing is typed; a tap records
// (up to 60 s), the bar shows the time with Delete and Send; the clip goes out as chat media of kind 'audio'.

const MAX_MS = 60_000
const MIN_MS = 800
/** Opus in WebM where the browser has it (Chrome, Firefox, Android), else AAC in MP4 (Safari, iPhone). */
const TYPES = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus']
const clock = (ms: number) => `${Math.floor(ms / 60000)}:${String(Math.floor((ms % 60000) / 1000)).padStart(2, '0')}`

export function canRecord() {
  return typeof MediaRecorder !== 'undefined' && !!navigator.mediaDevices?.getUserMedia
}

/** The recording bar in place of the text field: a red dot and the time, Delete, Send. */
export function VoiceRecorder({ onSend, onDone }: { onSend: (clip: File) => Promise<boolean>; onDone: () => void }) {
  const { t } = useI18n()
  const toast = useToast()
  const [ms, setMs] = useState(0)
  const rec = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const started = useRef(0)

  useEffect(() => {
    let stream: MediaStream | null = null
    let timer: ReturnType<typeof setInterval> | undefined
    let gone = false
    void navigator.mediaDevices
      .getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } })
      .then((s) => {
        stream = s
        if (gone) return s.getTracks().forEach((x) => x.stop())
        const mimeType = TYPES.find((x) => MediaRecorder.isTypeSupported(x))
        const r = new MediaRecorder(s, mimeType ? { mimeType, audioBitsPerSecond: 32_000 } : undefined)
        r.ondataavailable = (e) => e.data.size && chunks.current.push(e.data)
        r.start(250)
        rec.current = r
        started.current = Date.now()
        timer = setInterval(() => {
          const now = Date.now() - started.current
          setMs(Math.min(now, MAX_MS))
          if (now >= MAX_MS && r.state === 'recording') r.pause() // 60 s: it waits for Send or Delete
        }, 200)
      })
      .catch(() => {
        toast.show(t('video.micDenied'), 'error')
        onDone()
      })
    return () => {
      gone = true
      clearInterval(timer)
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
      <span className="flex-1 text-[15px] tabular-nums">{clock(ms)}</span>
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

/** The mic button in Send's place. */
export function MicButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  const { t } = useI18n()
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="grid size-9 shrink-0 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-background hover:text-foreground disabled:opacity-40"
      aria-label={t('composer.voice')}
      title={t('composer.voice')}
    >
      <Mic className="size-5" aria-hidden />
    </button>
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
    if (a.duration === Infinity) {
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
