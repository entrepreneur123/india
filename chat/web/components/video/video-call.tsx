'use client'

import { LazyMotion } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useRef, useState } from 'react'
import { EyeOff, Mic, MicOff, PhoneOff, ShieldCheck, SignalHigh, SignalLow, SignalMedium, SignalZero, Video, VideoOff, Volume2 } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import type { Call, CallState, PartnerNotice } from '@/lib/livekit'
import { deviceNotice, keepScreenOn, signalBars } from '@/lib/rtc'
import { flagEmoji } from '@/lib/format'
import { useToast } from '@/components/ui/toast'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'
import { retryImport } from '@/lib/retry-import'
import { nameAge } from '@/lib/looks'

/** Drag for the self-view: only the call screen needs it, so it downloads with the call. */
const loadDrag = () => import('@/lib/motion-drag').then((mod) => mod.default)

/** Full-screen call over the chat. Real video through LiveKit; the demo backend has no token and shows a preview. */
export function VideoCall({ matchId, partner, onEnd }: { matchId: string; partner: Profile; onEnd: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const stageRef = useRef<HTMLDivElement>(null)
  const remoteRef = useRef<HTMLVideoElement>(null)
  const localRef = useRef<HTMLVideoElement>(null)
  const audioRef = useRef<HTMLAudioElement>(null)
  const callRef = useRef<Call | null>(null)
  const [state, setState] = useState<CallState | 'demo'>('connecting')
  const [mic, setMic] = useState(true)
  const [camera, setCamera] = useState(true)
  // Nudity check on the partner's video (lib/nudity.ts): blurred until the first clean frame, hidden while flagged.
  const [shield, setShield] = useState<'checking' | 'clear' | 'hidden'>('checking')
  // My connection (bars), my partner's (a notice instead of a frozen picture), their camera, and iOS autoplay.
  const [quality, setQuality] = useState('unknown')
  const [partnerState, setPartnerState] = useState<PartnerNotice>(null)
  const [partnerCam, setPartnerCam] = useState(true)
  const [needsTap, setNeedsTap] = useState(false)

  // The screen stays on for the whole call, as in a phone call app.
  useEffect(() => keepScreenOn(), [])

  useEffect(() => {
    let cancelled = false
    const abort = new AbortController()
    let watch: { stop(): void } | null = null
    let flagged = false
    // A phone too slow (or unable) to run the model still gets the call: the start-up blur lifts after 8 s.
    const startShield = async () => {
      const slow = setTimeout(() => !cancelled && setShield((s) => (s === 'checking' ? 'clear' : s)), 8000)
      try {
        const { watchNudity } = await retryImport(() => import('@/lib/nudity'))
        if (cancelled) return
        const w = await watchNudity(remoteRef.current!, (hide) => {
          if (cancelled) return
          setShield(hide ? 'hidden' : 'clear')
          if (hide && !flagged) {
            flagged = true
            void backend.flagNudity(matchId)
          }
        })
        if (cancelled) w.stop()
        else watch = w
      } catch {
        clearTimeout(slow)
        if (!cancelled) setShield('clear')
      }
    }
    ;(async () => {
      try {
        const token = await backend.videoToken(matchId)
        if (cancelled) return
        if (!token) {
          setState('demo')
          setShield('clear')
          return
        }
        void startShield() // the model downloads while the call connects
        const { joinCall } = await retryImport(() => import('@/lib/livekit')) // the video SDK loads only when a call starts
        if (cancelled) return // the call ended while the SDK was loading
        const call = await joinCall(
          token.url,
          token.token,
          { remote: remoteRef.current!, local: localRef.current!, audio: audioRef.current! },
          {
            state: (s) => !cancelled && setState(s),
            quality: (q) => !cancelled && setQuality(q),
            partner: (n) => !cancelled && setPartnerState(n),
            partnerCamera: (on) => !cancelled && setPartnerCam(on),
            blocked: (b) => !cancelled && setNeedsTap(b),
          },
          abort.signal,
        )
        if (cancelled) {
          void call.disconnect()
          return
        }
        callRef.current = call
        if (call.camera) setCamera(false)
        if (call.mic) setMic(false)
        const problem = deviceNotice(call.camera, call.mic)
        if (problem) toast.show(t(`video.${problem}` as const), 'error')
      } catch (err) {
        if (cancelled) return
        toast.error(err)
        setState('failed')
      }
    })()
    return () => {
      cancelled = true
      watch?.stop()
      abort.abort()
      void callRef.current?.disconnect()
      callRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [backend, matchId, toast])

  const toggleMic = async () => {
    const next = !mic
    setMic(next)
    await callRef.current?.setMic(next).catch((e: Error) => {
      setMic(!next)
      showDeviceError(e, 'mic')
    })
  }
  const toggleCamera = async () => {
    const next = !camera
    setCamera(next)
    await callRef.current?.setCamera(next).catch((e: Error) => {
      setCamera(!next)
      showDeviceError(e, 'camera')
    })
  }
  const showDeviceError = (e: Error, kind: 'camera' | 'mic') => {
    const p = (['denied', 'missing', 'busy', 'other'] as const).find((x) => x === e.message) ?? 'other'
    const key = kind === 'camera' ? deviceNotice(p, null) : deviceNotice(null, p)
    if (key) toast.show(t(`video.${key}` as const), 'error')
  }

  const status =
    state === 'connecting' ? t('video.connecting')
    : state === 'reconnecting' ? t('video.reconnecting')
    : state === 'failed' ? t('video.failed')
    : state === 'demo' ? t('video.demo')
    : partnerState === 'lost' ? t('video.partnerLost', { name: partner.name })
    : partnerState === 'paused' ? t('video.partnerPaused', { name: partner.name })
    : null
  const bars = state === 'connected' ? signalBars(quality) : null
  const BarsIcon = bars === 3 ? SignalHigh : bars === 2 ? SignalMedium : bars === 1 ? SignalLow : SignalZero
  // Their camera is off, or their picture stopped: show their photo, not a black or frozen frame.
  const partnerHidden = state === 'connected' && (!partnerCam || partnerState === 'lost' || partnerState === 'paused')

  return (
    <LazyMotion features={loadDrag}>
    <m.div
      ref={stageRef}
      className="theme-night absolute inset-0 z-30 overflow-hidden bg-ink text-ink-foreground"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}
      role="dialog"
      aria-label={t('video.with', { name: partner.name })}
    >
      {partner.photoUrl && (state !== 'connected' || partnerHidden) ? (
        <img src={partner.photoUrl} alt="" className="absolute inset-0 size-full scale-110 object-cover opacity-40 blur-2xl" />
      ) : null}
      <video
        ref={remoteRef}
        autoPlay
        playsInline
        className={cn(
          'absolute inset-0 size-full object-cover transition-[filter] duration-200',
          (state !== 'connected' || partnerHidden) && 'opacity-0',
          shield !== 'clear' && 'scale-110 blur-3xl',
        )}
      />
      <audio ref={audioRef} autoPlay />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-ink/50 via-transparent to-ink/80" />

      <div className="absolute inset-x-0 top-0 flex items-center gap-3 p-5">
        <span className="rounded-full bg-black/55 px-3.5 py-1.5 text-sm font-semibold">
          {nameAge(partner.name, partner.age)} <span aria-hidden>{flagEmoji(partner.country)}</span>
        </span>
        {state === 'connected' ? (
          <span className="flex items-center gap-1.5 rounded-full bg-black/55 px-3 py-1.5 text-xs font-semibold">
            <span className="size-1.5 animate-pulse rounded-full bg-online" aria-hidden /> {t('video.live')}
          </span>
        ) : null}
        {bars !== null ? (
          <span
            className="flex items-center rounded-full bg-black/55 p-1.5"
            role="img"
            aria-label={t(bars >= 3 ? 'video.netGood' : bars === 2 ? 'video.netOk' : 'video.netPoor')}
          >
            <BarsIcon className="size-4" aria-hidden />
          </span>
        ) : null}
        {state === 'connected' && partnerState === 'weak' ? (
          <span className="rounded-full bg-black/55 px-3 py-1.5 text-xs font-semibold" role="status">
            {t('video.partnerWeak', { name: partner.name })}
          </span>
        ) : null}
      </div>

      {state === 'connected' && !partnerCam && !status ? (
        <div className="absolute inset-0 grid place-items-center p-6">
          <p className="flex items-center gap-2 rounded-full bg-black/60 px-4 py-2 text-sm font-semibold" role="status">
            <VideoOff className="size-4" aria-hidden /> {t('video.partnerCamOff', { name: partner.name })}
          </p>
        </div>
      ) : state === 'connected' && shield === 'hidden' ? (
        <div className="absolute inset-0 grid place-items-center bg-black/50 p-6" role="alert">
          <div className="flex max-w-xs flex-col items-center gap-3 text-center">
            <span className="grid size-14 place-items-center rounded-full bg-white/15">
              <EyeOff className="size-7" aria-hidden />
            </span>
            <p className="text-lg font-semibold">{t('video.hiddenTitle')}</p>
            <p className="text-sm text-ink-foreground/80">{t('video.hiddenBody')}</p>
          </div>
        </div>
      ) : state === 'connected' && shield === 'checking' && !status ? (
        <div className="absolute inset-0 grid place-items-center p-6">
          <p className="flex items-center gap-2 rounded-full bg-black/60 px-4 py-2 text-sm font-semibold" role="status">
            <ShieldCheck className="size-4" aria-hidden /> {t('video.checking')}
          </p>
        </div>
      ) : null}

      {status ? (
        <div className="absolute inset-0 grid place-items-center p-6">
          <p className="max-w-xs rounded-full bg-black/60 px-5 py-2.5 text-center text-sm font-semibold" role="status">
            {status}
          </p>
        </div>
      ) : null}

      {needsTap && state === 'connected' ? (
        <button
          type="button"
          onClick={() => void callRef.current?.startAudio().then(() => setNeedsTap(false)).catch(() => undefined)}
          className="absolute inset-x-0 bottom-32 mx-auto flex w-fit items-center gap-2 rounded-full bg-ink-foreground px-5 py-3 text-sm font-semibold text-ink"
        >
          <Volume2 className="size-5" aria-hidden /> {t('video.tapToHear', { name: partner.name })}
        </button>
      ) : null}

      <m.div
        drag
        dragConstraints={stageRef}
        dragElastic={0.15}
        dragMomentum={false}
        whileDrag={{ scale: 1.02 }}
        className="absolute top-20 inset-e-5 h-44 w-32 cursor-grab overflow-hidden rounded-3xl bg-muted shadow-e3 ring-1 ring-ink-foreground/15 active:cursor-grabbing sm:h-56 sm:w-40"
        aria-label={t('video.yourCamera')}
      >
        <video ref={localRef} autoPlay playsInline muted className={cn('size-full -scale-x-100 object-cover', !camera && 'opacity-0')} />
        {!camera || state === 'demo' ? (
          <div className="absolute inset-0 grid place-items-center text-xs font-semibold text-muted-foreground">
            {camera ? t('video.yourCamera') : t('video.cameraOff')}
          </div>
        ) : null}
      </m.div>

      <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-4 p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
        <CallButton label={mic ? t('video.mute') : t('video.unmute')} onClick={() => void toggleMic()} active={!mic}>
          {mic ? <Mic className="size-6" aria-hidden /> : <MicOff className="size-6" aria-hidden />}
        </CallButton>
        <CallButton label={camera ? t('video.camOff') : t('video.camOn')} onClick={() => void toggleCamera()} active={!camera}>
          {camera ? <Video className="size-6" aria-hidden /> : <VideoOff className="size-6" aria-hidden />}
        </CallButton>
        <CallButton label={t('video.end')} onClick={onEnd} danger autoFocus>
          <PhoneOff className="size-6" aria-hidden />
        </CallButton>
      </div>
    </m.div>
    </LazyMotion>
  )
}

function CallButton({
  label,
  onClick,
  children,
  active,
  danger,
  autoFocus,
}: {
  label: string
  onClick: () => void
  children: React.ReactNode
  active?: boolean
  danger?: boolean
  autoFocus?: boolean
}) {
  return (
    <m.button
      type="button"
      onClick={onClick}
      autoFocus={autoFocus}
      whileTap={{ scale: 0.94 }}
      transition={{ duration: 0.1 }}
      aria-label={label}
      aria-pressed={danger ? undefined : active}
      className={cn(
        'grid size-15 place-items-center rounded-full transition-colors duration-150',
        danger
          ? 'bg-[#c5221f] text-white hover:bg-[#a50e0e]'
          : active
            ? 'bg-ink-foreground text-ink'
            : 'bg-ink-foreground/15 text-ink-foreground hover:bg-ink-foreground/25',
      )}
    >
      {children}
    </m.button>
  )
}
