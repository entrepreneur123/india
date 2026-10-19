'use client'

import { motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import { Mic, MicOff, PhoneOff, Video, VideoOff } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import type { Call, CallState } from '@/lib/livekit'
import { flagEmoji } from '@/lib/format'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

/** Full-screen call over the chat. Real video through LiveKit; the demo backend has no token and shows a preview. */
export function VideoCall({ matchId, partner, onEnd }: { matchId: string; partner: Profile; onEnd: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const stageRef = useRef<HTMLDivElement>(null)
  const remoteRef = useRef<HTMLVideoElement>(null)
  const localRef = useRef<HTMLVideoElement>(null)
  const audioRef = useRef<HTMLAudioElement>(null)
  const callRef = useRef<Call | null>(null)
  const [state, setState] = useState<CallState | 'demo'>('connecting')
  const [mic, setMic] = useState(true)
  const [camera, setCamera] = useState(true)

  useEffect(() => {
    let cancelled = false
    const abort = new AbortController()
    ;(async () => {
      try {
        const token = await backend.videoToken(matchId)
        if (cancelled) return
        if (!token) {
          setState('demo')
          return
        }
        const { joinCall } = await import('@/lib/livekit') // the video SDK loads only when a call starts
        if (cancelled) return // the call ended while the SDK was loading
        const call = await joinCall(
          token.url,
          token.token,
          { remote: remoteRef.current!, local: localRef.current!, audio: audioRef.current! },
          (s) => !cancelled && setState(s),
          abort.signal,
        )
        if (cancelled) {
          void call.disconnect()
          return
        }
        callRef.current = call
        if (call.devices === 'blocked') {
          setCamera(false)
          setMic(false)
          toast.show('Allow camera and microphone in your browser so they can see and hear you', 'error')
        }
      } catch (err) {
        if (cancelled) return
        toast.error(err)
        setState('failed')
      }
    })()
    return () => {
      cancelled = true
      abort.abort()
      void callRef.current?.disconnect()
      callRef.current = null
    }
  }, [backend, matchId, toast])

  const toggleMic = async () => {
    const next = !mic
    setMic(next)
    await callRef.current?.setMic(next).catch(() => setMic(!next))
  }
  const toggleCamera = async () => {
    const next = !camera
    setCamera(next)
    await callRef.current?.setCamera(next).catch(() => setCamera(!next))
  }

  const status =
    state === 'connecting' ? 'Connecting…'
    : state === 'reconnecting' ? 'Reconnecting…'
    : state === 'failed' ? 'Call couldn’t connect'
    : state === 'demo' ? 'Demo preview — real video runs on the live server'
    : null

  return (
    <motion.div
      ref={stageRef}
      className="theme-night absolute inset-0 z-30 overflow-hidden bg-ink text-ink-foreground"
      initial={{ opacity: 0, scale: 1.04 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 1.02 }}
      transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
      role="dialog"
      aria-label={`Video call with ${partner.name}`}
    >
      {partner.photoUrl && state !== 'connected' ? (
        <img src={partner.photoUrl} alt="" className="absolute inset-0 size-full scale-110 object-cover opacity-40 blur-2xl" />
      ) : null}
      <video ref={remoteRef} autoPlay playsInline className={cn('absolute inset-0 size-full object-cover', state !== 'connected' && 'opacity-0')} />
      <audio ref={audioRef} autoPlay />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-ink/50 via-transparent to-ink/80" />

      <div className="absolute inset-x-0 top-0 flex items-center gap-3 p-5">
        <span className="rounded-full bg-ink/50 px-3.5 py-1.5 text-sm font-semibold backdrop-blur-md">
          {partner.name}, {partner.age} <span aria-hidden>{flagEmoji(partner.country)}</span>
        </span>
        {state === 'connected' ? (
          <span className="flex items-center gap-1.5 rounded-full bg-ink/50 px-3 py-1.5 text-xs font-semibold backdrop-blur-md">
            <span className="size-1.5 animate-pulse rounded-full bg-online" aria-hidden /> Live
          </span>
        ) : null}
      </div>

      {status ? (
        <div className="absolute inset-0 grid place-items-center p-6">
          <p className="max-w-xs rounded-full bg-ink/60 px-5 py-2.5 text-center text-sm font-semibold backdrop-blur-md" role="status">
            {status}
          </p>
        </div>
      ) : null}

      <motion.div
        drag
        dragConstraints={stageRef}
        dragElastic={0.15}
        dragMomentum={false}
        whileDrag={{ scale: 1.04 }}
        className="absolute top-20 right-5 h-44 w-32 cursor-grab overflow-hidden rounded-3xl bg-muted shadow-2xl ring-2 ring-ink-foreground/15 active:cursor-grabbing sm:h-56 sm:w-40"
        aria-label="Your camera"
      >
        <video ref={localRef} autoPlay playsInline muted className={cn('size-full -scale-x-100 object-cover', !camera && 'opacity-0')} />
        {!camera || state === 'demo' ? (
          <div className="absolute inset-0 grid place-items-center text-xs font-semibold text-muted-foreground">
            {camera ? 'Your camera' : 'Camera off'}
          </div>
        ) : null}
      </motion.div>

      <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-4 p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
        <CallButton label={mic ? 'Mute' : 'Unmute'} onClick={() => void toggleMic()} active={!mic}>
          {mic ? <Mic className="size-6" aria-hidden /> : <MicOff className="size-6" aria-hidden />}
        </CallButton>
        <CallButton label={camera ? 'Turn camera off' : 'Turn camera on'} onClick={() => void toggleCamera()} active={!camera}>
          {camera ? <Video className="size-6" aria-hidden /> : <VideoOff className="size-6" aria-hidden />}
        </CallButton>
        <CallButton label="End call" onClick={onEnd} danger autoFocus>
          <PhoneOff className="size-6" aria-hidden />
        </CallButton>
      </div>
    </motion.div>
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
    <motion.button
      type="button"
      onClick={onClick}
      autoFocus={autoFocus}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.9 }}
      aria-label={label}
      aria-pressed={danger ? undefined : active}
      className={cn(
        'grid size-15 place-items-center rounded-full backdrop-blur-md transition-colors',
        danger
          ? 'bg-destructive text-primary-foreground shadow-[0_10px_30px_-8px_rgb(229_38_61/0.8)]'
          : active
            ? 'bg-ink-foreground text-ink'
            : 'bg-ink-foreground/15 text-ink-foreground hover:bg-ink-foreground/25',
      )}
    >
      {children}
    </motion.button>
  )
}
