'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { lazy, Suspense, useState } from 'react'
import { EyeOff, Flag, ShieldCheck } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import type { TKey } from '@/lib/i18n'
import { Rich, useI18n } from '@/components/i18n'
import { Logo, Skeleton } from '@/components/ui/bits'
import { LanguageSelect } from '@/components/language-select'
import { HeroPreview } from './hero-preview'
import { PhoneStep } from './phone-step'
import { OtpStep } from './otp-step'
import { VerifiedMark } from '@/components/trust/mark'
import { requiredCached } from '@/components/trust/required'
import { retryImport } from '@/lib/retry-import'

// The profile form (with the ID's rules and strings) is its own chunk: fetched while the code is being typed, so
// the first screen stays small.
const loadProfileStep = () => retryImport(() => import('./profile-step'))
const ProfileStep = lazy(() => loadProfileStep().then((mod) => ({ default: mod.ProfileStep })))

type Step = 'phone' | 'otp' | 'profile'

const HOW: { title: TKey; body: TKey }[] = [
  { title: 'auth.how1Title', body: 'auth.how1Body' },
  { title: 'auth.how2Title', body: 'auth.how2Body' },
  { title: 'auth.how3Title', body: 'auth.how3Body' },
]

const SAFETY: { icon: typeof ShieldCheck; text: TKey }[] = [
  { icon: ShieldCheck, text: 'auth.safety1' },
  { icon: Flag, text: 'auth.safety2' },
  { icon: EyeOff, text: 'auth.safety3' },
]

export function AuthScreen({
  initialStep,
  onPhoneVerified,
  onComplete,
}: {
  initialStep: 'phone' | 'profile'
  onPhoneVerified: (phone: string) => void
  onComplete: (p: Profile | null) => void
}) {
  const { t, rtl } = useI18n()
  const [step, setStep] = useState<Step>(initialStep)
  const [phone, setPhone] = useState<{ digits: string; display: string } | null>(null)
  // "Everyone here is ID-verified" only once the server has said verification is required (seen on this browser).
  const [required] = useState(requiredCached)

  return (
    <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[1.1fr_1fr]">
      <section className="relative bg-muted px-5 pt-6 pb-12 sm:px-10 lg:flex lg:min-h-dvh lg:flex-col lg:px-14 lg:py-10">
        <header className="flex items-center justify-between gap-3">
          <Logo />
          <LanguageSelect className="bg-background" />
        </header>

        <div className="mt-10 grid gap-10 lg:my-auto 2xl:grid-cols-[1fr_minmax(0,380px)] 2xl:items-center">
          <div className="flex flex-col gap-8">
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap gap-2">
                <span className="w-fit rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-muted-foreground">
                  {t('auth.badge')}
                </span>
                <span className="inline-flex w-fit items-center gap-1.5 rounded-full bg-background py-1 pe-3 ps-1.5 text-xs font-semibold text-foreground shadow-[0_1px_2px_rgb(0_0_0/0.06)] ring-1 ring-[color-mix(in_srgb,#ff3d68_30%,transparent)]">
                  <VerifiedMark size={16} label={null} />
                  {t(required ? 'auth.everyoneVerified' : 'auth.verifiedBadge')}
                </span>
              </div>
              <h1 className="text-[32px] leading-[1.15] font-bold tracking-tight text-balance sm:text-5xl">
                <Rich text={t('auth.headline')} mark={(s) => <span className="text-primary-text">{s}</span>} />
              </h1>
              <p className="max-w-md text-base leading-relaxed text-pretty text-muted-foreground">{t('auth.intro')}</p>
            </div>

            <ol className="flex flex-col gap-4">
              {HOW.map((s, i) => (
                <li key={s.title} className="flex gap-4">
                  <span className="grid size-8 shrink-0 place-items-center rounded-full bg-background text-sm font-semibold text-primary-text ring-1 ring-border">
                    {i + 1}
                  </span>
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <p className="text-[15px] font-semibold">{t(s.title)}</p>
                    <p className="text-sm leading-relaxed text-muted-foreground">{t(s.body)}</p>
                  </div>
                </li>
              ))}
            </ol>

            <ul className="hidden flex-col gap-2.5 border-t border-border pt-6 sm:flex">
              {SAFETY.map(({ icon: Icon, text }) => (
                <li key={text} className="flex items-center gap-3 text-sm text-muted-foreground">
                  <Icon className="size-4 shrink-0 text-primary-text" aria-hidden />
                  {t(text)}
                </li>
              ))}
            </ul>
          </div>
          <div className="hidden md:block lg:hidden 2xl:block">
            <HeroPreview />
          </div>
        </div>
      </section>

      <section className="relative -mt-6 flex items-start justify-center rounded-t-[28px] bg-background px-5 pt-8 pb-12 sm:px-10 lg:mt-0 lg:items-center lg:rounded-none lg:py-10">
        <div className="w-full max-w-[400px]">
          <AnimatePresence mode="wait" initial={false}>
            <m.div
              key={step}
              initial={{ opacity: 0, x: rtl ? -12 : 12 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: rtl ? 12 : -12 }}
              transition={{ type: 'tween', duration: 0.18, ease: [0.2, 0, 0, 1] }}
            >
              {step === 'phone' ? (
                <PhoneStep
                  onSent={(digits, display) => {
                    setPhone({ digits, display })
                    setStep('otp')
                    void loadProfileStep().catch(() => undefined)
                  }}
                />
              ) : step === 'otp' && phone ? (
                <OtpStep
                  phone={phone.digits}
                  display={phone.display}
                  onBack={() => setStep('phone')}
                  onVerified={(existing) => {
                    onPhoneVerified(phone.digits)
                    if (existing) onComplete(existing)
                    else setStep('profile')
                  }}
                />
              ) : (
                <Suspense
                  fallback={
                    <div className="flex flex-col gap-5" aria-busy>
                      <Skeleton className="h-8 w-56" />
                      <Skeleton className="h-24 w-full rounded-2xl" />
                      <Skeleton className="h-12 w-full" />
                      <Skeleton className="h-12 w-full" />
                    </div>
                  }
                >
                  <ProfileStep onCreated={onComplete} />
                </Suspense>
              )}
            </m.div>
          </AnimatePresence>
          <StepDots step={step} />
        </div>
      </section>
    </div>
  )
}

function StepDots({ step }: { step: Step }) {
  const { t } = useI18n()
  const order: Step[] = ['phone', 'otp', 'profile']
  const idx = order.indexOf(step)
  return (
    <div className="mt-8 flex justify-center gap-1.5" aria-label={t('auth.step', { n: idx + 1, total: 3 })} role="img">
      {order.map((s, i) => (
        <span
          key={s}
          className="h-1.5 rounded-full bg-primary transition-[width,opacity] duration-200"
          style={{ width: i === idx ? 24 : 6, opacity: i <= idx ? 1 : 0.25 }}
        />
      ))}
    </div>
  )
}
