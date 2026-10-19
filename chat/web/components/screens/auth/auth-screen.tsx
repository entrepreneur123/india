'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { EyeOff, Flag, ShieldCheck } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { Aurora } from '@/components/ui/ambient'
import { Logo } from '@/components/ui/bits'
import { HeroPreview } from './hero-preview'
import { PhoneStep } from './phone-step'
import { OtpStep } from './otp-step'
import { ProfileStep } from './profile-step'

type Step = 'phone' | 'otp' | 'profile'

const HOW = [
  { title: 'Verify your number', body: 'One quick code. No passwords, no email.' },
  { title: 'Tap Start', body: 'We pair you with someone random, anywhere on Earth.' },
  { title: 'Keep the good ones', body: 'Add friends, see their posts, message them anytime.' },
]

const SAFETY = [
  { icon: ShieldCheck, text: 'Adults only (18+), one account per phone number' },
  { icon: Flag, text: 'Report anyone in one tap, instantly, from any chat' },
  { icon: EyeOff, text: 'Photos and videos stay blurred until you choose to look' },
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
  const [step, setStep] = useState<Step>(initialStep)
  const [phone, setPhone] = useState<{ digits: string; display: string } | null>(null)

  return (
    <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[1.15fr_1fr]">
      <section className="theme-night relative isolate overflow-hidden bg-background px-6 pt-8 pb-14 text-foreground sm:px-10 lg:flex lg:min-h-dvh lg:flex-col lg:px-14 lg:py-10">
        <Aurora />
        <header className="flex items-center justify-between">
          <Logo />
          <span className="rounded-full bg-ink-foreground/8 px-3 py-1.5 text-xs font-semibold ring-1 ring-ink-foreground/10">
            18+ · phone-verified
          </span>
        </header>

        <div className="mt-10 grid gap-10 lg:my-auto 2xl:grid-cols-[1fr_minmax(0,400px)] 2xl:items-center 2xl:gap-10">
          <div className="flex flex-col gap-8">
            <div className="flex flex-col gap-4">
              <motion.h1
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
                className="text-4xl leading-[1.05] font-extrabold tracking-tight text-balance sm:text-5xl 2xl:text-6xl"
              >
                Start chatting with <span className="text-brand">the whole world.</span>
              </motion.h1>
              <motion.p
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
                className="max-w-md text-base leading-relaxed text-muted-foreground text-pretty"
              >
                Random 1:1 chats with people around the world. Text first, video only when you both say yes.
              </motion.p>
            </div>

            <ol className="flex flex-col gap-4">
              {HOW.map((s, i) => (
                <motion.li
                  key={s.title}
                  initial={{ opacity: 0, x: -12 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.2 + i * 0.08, duration: 0.5 }}
                  className="flex gap-4"
                >
                  <span className="grid size-8 shrink-0 place-items-center rounded-full bg-brand text-sm font-bold text-primary-foreground">
                    {i + 1}
                  </span>
                  <div className="flex flex-col gap-0.5">
                    <p className="text-[15px] font-semibold">{s.title}</p>
                    <p className="text-sm leading-relaxed text-muted-foreground">{s.body}</p>
                  </div>
                </motion.li>
              ))}
            </ol>

            <ul className="hidden flex-col gap-2.5 border-t border-border pt-6 sm:flex">
              {SAFETY.map(({ icon: Icon, text }) => (
                <li key={text} className="flex items-center gap-3 text-sm text-muted-foreground">
                  <Icon className="size-4 shrink-0 text-accent" aria-hidden />
                  {text}
                </li>
              ))}
            </ul>
          </div>
          <div className="hidden md:block lg:hidden 2xl:block">
            <HeroPreview />
          </div>
        </div>
      </section>

      <section className="relative -mt-6 flex items-start justify-center rounded-t-[32px] bg-background px-5 pt-8 pb-12 sm:px-10 lg:mt-0 lg:items-center lg:rounded-none lg:py-10">
        <div className="w-full max-w-[420px]">
          <AnimatePresence mode="wait">
            <motion.div
              key={step}
              initial={{ opacity: 0, x: 24 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -24 }}
              transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
            >
              {step === 'phone' ? (
                <PhoneStep
                  onSent={(digits, display) => {
                    setPhone({ digits, display })
                    setStep('otp')
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
                <ProfileStep onCreated={onComplete} />
              )}
            </motion.div>
          </AnimatePresence>
          <StepDots step={step} />
        </div>
      </section>
    </div>
  )
}

function StepDots({ step }: { step: Step }) {
  const order: Step[] = ['phone', 'otp', 'profile']
  const idx = order.indexOf(step)
  return (
    <div className="mt-8 flex justify-center gap-1.5" aria-label={`Step ${idx + 1} of 3`} role="img">
      {order.map((s, i) => (
        <motion.span
          key={s}
          className="h-1.5 rounded-full bg-primary"
          animate={{ width: i === idx ? 24 : 6, opacity: i <= idx ? 1 : 0.25 }}
          transition={{ type: 'spring', stiffness: 400, damping: 30 }}
        />
      ))}
    </div>
  )
}
