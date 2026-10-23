'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useSWRConfig } from 'swr'
import { LoaderCircle } from 'lucide-react'
import type { KycDemoOutcome, KycResult } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import { canStart, LEVEL_COLORS, mannerLevel, verifyState, type VerifyState } from '@/lib/trust'
import { useApp, type VerifyWhy } from '@/components/app-state'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { cn } from '@/lib/utils'
import { ClockIcon, IdCardIcon, KeepIcon, SelfieIcon, VerifyHero } from './art'
import { VerifiedMark } from './badge'
import { markKycOff } from './store'
import { IdentityFacts, MannerGauge } from './trust-cards'
import { useTrustT, type TrustKey } from './trust-text'

const DEMO_OUTCOMES: KycDemoOutcome[] = ['verified', 'pending', 'declined', 'unreadable', 'duplicate', 'minor']

/** Where the identity check sends the person back: the kyc function accepts only the Yaari website's addresses. */
const returnUrl = () => `${location.origin}/?kyc=done`

/**
 * "Verify your identity": what the check is (ID + selfie, about 2 minutes), what we keep and why, then the hosted
 * check (Didit) in this tab; back here with ?kyc=done the result is read again. The demo simulates the check in
 * place. Every result has its own screen; "coming soon" (no provider yet) is said once and then not asked again.
 */
export function VerifySheet({ open, why, onClose }: { open: boolean; why: VerifyWhy; onClose: () => void }) {
  const backend = useBackend()
  const { mutate } = useSWRConfig()
  const { me, setMe, trust, kyc, setKyc, refreshTrust } = useApp()
  const { tt } = useTrustT()
  const [result, setResult] = useState<KycResult | null>(null)
  const [phase, setPhase] = useState<'idle' | 'opening' | 'checking' | 'demo'>('idle')
  const [error, setError] = useState<TrustKey | null>(null)
  const kycRef = useRef(kyc)
  kycRef.current = kyc

  const verifiedNow = useCallback(async () => {
    refreshTrust()
    void mutate(['trust', me.id])
    // Gender, birth date and country now come from the document.
    const p = await backend.myProfile().catch(() => null)
    if (p) setMe({ ...p, verified: true })
  }, [backend, me.id, mutate, refreshTrust, setMe])

  const check = useCallback(async () => {
    setPhase('checking')
    setError(null)
    try {
      const r = await backend.kycCheck()
      setResult(r)
      setKyc({ avail: 'on', result: r })
      if (r === 'verified') await verifiedNow()
      else refreshTrust()
    } catch (err) {
      if (errorCode(err) === 'kyc_unavailable') {
        markKycOff(true)
        setKyc({ avail: 'off', result: null })
      } else setError('verify.failed')
    } finally {
      setPhase('idle')
    }
  }, [backend, setKyc, verifiedNow, refreshTrust])

  // Each opening starts from what is known; coming back from the check (or a check still pending) reads it again.
  useEffect(() => {
    if (!open) return
    const known = kycRef.current.result ?? trust?.kyc?.result ?? null
    setResult(known)
    setError(null)
    setPhase('idle')
    if (!trust?.verified && kycRef.current.avail !== 'off' && (why === 'return' || known === 'pending')) void check()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const start = async () => {
    setPhase('opening')
    setError(null)
    try {
      const url = await backend.kycStart(returnUrl())
      if (url.startsWith('demo:')) return setPhase('demo')
      location.assign(url) // the hosted check, in this tab; it comes back to /?kyc=done
    } catch (err) {
      const code = errorCode(err)
      setPhase('idle')
      if (code === 'kyc_unavailable') {
        markKycOff(true)
        setKyc({ avail: 'off', result: null })
      } else if (code === 'already_verified') void check()
      else setError(code === 'slow_down' ? 'verify.slowDown' : 'verify.failed')
    }
  }

  const state: VerifyState = verifyState({
    verified: !!trust?.verified,
    result,
    unavailable: kyc.avail === 'off',
    opening: phase === 'opening',
  })
  // Right after sign-up, while verification is required, the check can't be put off.
  const dismissible = !(why === 'signup' && trust?.required && canStart(state))

  return (
    <Modal open={open} onClose={onClose} variant="sheet" hideHeader dismissible={dismissible} className="md:max-w-[440px]" title={tt('verify.label')}>
      <AnimatePresence mode="wait" initial={false}>
        <m.div
          key={phase === 'demo' ? 'demo' : phase === 'checking' ? 'checking' : state}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.18, ease: [0.2, 0, 0, 1] }}
        >
          {phase === 'demo' ? (
            <DemoCheck
              onDone={async (o) => {
                await backend.kycDemo?.(o)
                await check()
              }}
            />
          ) : phase === 'checking' ? (
            <Screen hero={<VerifyHero tone="wait" />} title={tt('verify.checking')}>
              <div className="grid place-items-center py-4">
                <LoaderCircle className="size-7 animate-spin text-primary-text" aria-hidden />
              </div>
            </Screen>
          ) : state === 'verified' ? (
            <Screen hero={<VerifyHero tone="ok" />} title={tt('verify.doneTitle')} body={tt('verify.doneBody')}>
              <IdentityFacts profile={me} verified className="mx-auto mt-1" />
              <Button variant="brand" size="lg" className="mt-6 w-full" onClick={onClose} data-autofocus>
                {tt('verify.done')}
              </Button>
            </Screen>
          ) : state === 'pending' ? (
            <Screen hero={<VerifyHero tone="wait" />} title={tt('verify.pendingTitle')} body={tt('verify.pendingBody')} error={error}>
              <Actions>
                <Button variant="brand" size="lg" onClick={() => void check()} data-autofocus>
                  {tt('verify.checkAgain')}
                </Button>
                <Button variant="ghost" size="lg" onClick={onClose}>
                  {tt('verify.ok')}
                </Button>
              </Actions>
            </Screen>
          ) : state === 'unavailable' ? (
            <Screen hero={<VerifyHero tone="wait" />} title={tt('verify.unavailableTitle')} body={tt('verify.unavailableBody')}>
              <Actions>
                <Button variant="brand" size="lg" onClick={onClose} data-autofocus>
                  {tt('verify.ok')}
                </Button>
              </Actions>
            </Screen>
          ) : state === 'duplicate' || state === 'minor' ? (
            <Screen
              hero={<VerifyHero tone="stop" />}
              title={tt(state === 'duplicate' ? 'verify.duplicateTitle' : 'verify.minorTitle')}
              body={tt(state === 'duplicate' ? 'verify.duplicateBody' : 'verify.minorBody')}
            >
              <Actions>
                <Button variant="secondary" size="lg" onClick={onClose} data-autofocus>
                  {tt('verify.ok')}
                </Button>
              </Actions>
            </Screen>
          ) : state === 'declined' || state === 'unreadable' ? (
            <Screen
              hero={<VerifyHero tone="warn" />}
              title={tt(state === 'declined' ? 'verify.declinedTitle' : 'verify.unreadableTitle')}
              body={tt(state === 'declined' ? 'verify.declinedBody' : 'verify.unreadableBody')}
              error={error}
            >
              <Actions>
                <Button variant="brand" size="lg" onClick={() => void start()} data-autofocus>
                  {tt('verify.tryAgain')}
                </Button>
                {dismissible ? (
                  <Button variant="ghost" size="lg" onClick={onClose}>
                    {tt('verify.later')}
                  </Button>
                ) : null}
              </Actions>
            </Screen>
          ) : (
            <Intro gate={why === 'gate'} opening={state === 'opening'} error={error} dismissible={dismissible} onStart={() => void start()} onLater={onClose} />
          )}
        </m.div>
      </AnimatePresence>
    </Modal>
  )
}

function Screen({ hero, title, body, error, children }: { hero: ReactNode; title: string; body?: string; error?: TrustKey | null; children?: ReactNode }) {
  const { tt } = useTrustT()
  return (
    <div className="flex flex-col items-center px-6 pt-7 pb-6 text-center">
      {hero}
      <h2 className="mt-4 text-[22px] leading-tight font-extrabold tracking-tight text-balance">{title}</h2>
      {body ? <p className="mt-2 max-w-sm text-[15px] leading-relaxed text-pretty text-muted-foreground">{body}</p> : null}
      {error ? (
        <p role="alert" className="mt-3 rounded-2xl bg-[color-mix(in_srgb,var(--destructive)_10%,var(--card))] px-4 py-2 text-sm font-semibold text-destructive">
          {tt(error)}
        </p>
      ) : null}
      <div className="mt-4 w-full">{children}</div>
    </div>
  )
}

function Actions({ children }: { children: ReactNode }) {
  return <div className="mt-2 flex w-full flex-col gap-2">{children}</div>
}

function Intro({
  gate,
  opening,
  error,
  dismissible,
  onStart,
  onLater,
}: {
  gate: boolean
  opening: boolean
  error: TrustKey | null
  dismissible: boolean
  onStart: () => void
  onLater: () => void
}) {
  const { tt } = useTrustT()
  const steps: { icon: ReactNode; title: TrustKey; body: TrustKey }[] = [
    { icon: <IdCardIcon size={26} />, title: 'verify.step1', body: 'verify.step1Body' },
    { icon: <SelfieIcon size={26} />, title: 'verify.step2', body: 'verify.step2Body' },
  ]
  return (
    <div className="flex flex-col">
      <div className="flex flex-col items-center px-6 pt-7 pb-5 text-center">
        <VerifyHero />
        <h2 className="mt-3 text-[24px] leading-tight font-extrabold tracking-tight text-balance">
          {tt(gate ? 'verify.gateTitle' : 'verify.title')}
        </h2>
        <p className="mt-2 max-w-sm text-[15px] leading-relaxed text-pretty text-muted-foreground">{tt(gate ? 'verify.gateBody' : 'verify.lead')}</p>
      </div>
      <div className="flex flex-col gap-3 px-5 pb-6">
        <ol className="overflow-hidden rounded-3xl bg-muted/70 night:bg-white/5">
          {steps.map((s, i) => (
            <li key={s.title} className={cn('flex items-center gap-3.5 px-4 py-3', i > 0 && 'border-t border-border/70')}>
              <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-card shadow-[0_1px_2px_rgb(0_0_0/0.06)] night:bg-white/8">{s.icon}</span>
              <span className="min-w-0 text-start">
                <span className="block text-[15px] font-bold">{tt(s.title)}</span>
                <span className="block text-[13px] leading-snug text-muted-foreground">{tt(s.body)}</span>
              </span>
            </li>
          ))}
          <li className="flex items-center gap-3.5 border-t border-border/70 px-4 py-2.5">
            <span className="grid size-11 shrink-0 place-items-center">
              <ClockIcon size={24} />
            </span>
            <span className="text-[13px] font-bold text-primary-text">{tt('verify.time')}</span>
          </li>
        </ol>
        <div className="flex gap-3 rounded-3xl bg-card px-4 py-3.5 text-start ring-1 ring-border">
          <KeepIcon size={26} className="mt-0.5" />
          <span className="min-w-0">
            <span className="block text-sm font-bold">{tt('verify.keepTitle')}</span>
            <span className="block text-[13px] leading-snug text-muted-foreground">{tt('verify.keepBody')}</span>
          </span>
        </div>
        <div className="flex gap-3 px-1 text-start">
          <VerifiedMark size={22} label={null} className="mt-0.5" />
          <span className="min-w-0">
            <span className="block text-sm font-bold">{tt('verify.whyTitle')}</span>
            <span className="block text-[13px] leading-snug text-muted-foreground">{tt('verify.whyBody')}</span>
          </span>
        </div>
        {error ? (
          <p role="alert" className="rounded-2xl bg-[color-mix(in_srgb,var(--destructive)_10%,var(--card))] px-4 py-2 text-center text-sm font-semibold text-destructive">
            {tt(error)}
          </p>
        ) : null}
        <Button variant="brand" size="lg" className="mt-1 h-14 text-base" loading={opening} onClick={onStart} data-autofocus>
          {opening ? tt('verify.opening') : tt('verify.start')}
        </Button>
        {dismissible ? (
          <Button variant="ghost" size="lg" onClick={onLater} disabled={opening}>
            {tt('verify.later')}
          </Button>
        ) : null}
        <p className="px-2 text-center text-[11px] leading-snug text-muted-foreground">
          {tt('verify.fixed')} {tt('verify.provider')}
        </p>
      </div>
    </div>
  )
}

/** The demo's stand-in for the hosted check: two short steps, then the result to pretend. */
function DemoCheck({ onDone }: { onDone: (o: KycDemoOutcome) => Promise<void> }) {
  const { tt } = useTrustT()
  const [step, setStep] = useState(0)
  const [busy, setBusy] = useState<KycDemoOutcome | null>(null)
  useEffect(() => {
    const a = setTimeout(() => setStep(1), 1100)
    const b = setTimeout(() => setStep(2), 2200)
    return () => {
      clearTimeout(a)
      clearTimeout(b)
    }
  }, [])
  return (
    <div className="flex flex-col items-center px-6 pt-7 pb-6 text-center">
      <span className="rounded-full bg-primary-soft px-3 py-1 text-xs font-bold text-primary-text">{tt('verify.demoTitle')}</span>
      <div className="mt-5 flex w-full max-w-xs flex-col gap-2.5">
        {(['verify.demoScan', 'verify.demoFace'] as const).map((k, i) => (
          <div key={k} className="flex items-center gap-3 rounded-2xl bg-muted px-4 py-3 text-start">
            {i === 0 ? <IdCardIcon size={26} /> : <SelfieIcon size={26} />}
            <span className="min-w-0 flex-1 text-sm font-semibold">{tt(k)}</span>
            {step > i ? <VerifiedMark size={18} label={null} /> : step === i ? <LoaderCircle className="size-4.5 animate-spin text-muted-foreground" aria-hidden /> : null}
          </div>
        ))}
      </div>
      {step >= 2 ? (
        <>
          <p className="mt-5 text-sm text-muted-foreground">{tt('verify.demoBody')}</p>
          <div className="mt-3 grid w-full grid-cols-2 gap-2">
            {DEMO_OUTCOMES.map((o) => (
              <Button
                key={o}
                variant={o === 'verified' ? 'brand' : 'outline'}
                size="md"
                className={cn('h-11', o === 'verified' && 'col-span-2')}
                loading={busy === o}
                disabled={!!busy}
                onClick={() => {
                  setBusy(o)
                  void onDone(o)
                }}
              >
                {tt(`verify.demo.${o}` as TrustKey)}
              </Button>
            ))}
          </div>
        </>
      ) : null}
    </div>
  )
}

/** 'manner_low': random chat is closed for now. What the score is, why, and how it comes back. */
export function MannerLowSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { trust } = useApp()
  const { tt } = useTrustT()
  const score = trust?.manner ?? 0
  const color = LEVEL_COLORS[mannerLevel(score)]
  return (
    <Modal open={open} onClose={onClose} variant="sheet" hideHeader className="md:max-w-[420px]" title={tt('low.title')}>
      <div className="flex flex-col items-center px-6 pt-8 pb-6 text-center">
        <div className="relative">
          <MannerGauge score={score} size={180} />
          <span className="absolute inset-x-0 bottom-0 text-[36px] leading-none font-extrabold tabular-nums" style={{ color }}>
            {score}
          </span>
        </div>
        <h2 className="mt-4 text-[22px] leading-tight font-extrabold tracking-tight text-balance">{tt('low.title')}</h2>
        <p className="mt-2 text-[15px] leading-relaxed text-pretty text-muted-foreground">{tt('low.body', { score })}</p>
        <ul className="mt-4 flex w-full flex-col gap-2 text-start">
          <li className="rounded-2xl bg-muted px-4 py-3 text-sm leading-snug">{tt('low.how')}</li>
          <li className="rounded-2xl bg-muted px-4 py-3 text-sm leading-snug">{tt('low.tips')}</li>
        </ul>
        <Button variant="brand" size="lg" className="mt-5 w-full" onClick={onClose} data-autofocus>
          {tt('low.ok')}
        </Button>
      </div>
    </Modal>
  )
}
