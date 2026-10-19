'use client'

import { motion } from 'motion/react'
import { useState } from 'react'
import { Check, Users, Video, Zap, Smartphone } from 'lucide-react'
import { Modal } from '@/components/ui/modal'
import { Aurora } from '@/components/ui/ambient'
import { PLAY_STORE_URL } from '@/lib/config'
import { cn } from '@/lib/utils'

const BENEFITS = [
  { icon: Users, title: 'Choose who you meet', body: 'Filter random matches by women or men.' },
  { icon: Video, title: 'Video calls', body: 'Ask your random-chat partner for a video call.' },
  { icon: Zap, title: '0.2 s skip', body: 'Leave a chat instantly instead of waiting 5 s.' },
]

type Plan = 'monthly' | 'pass'

// Prices come from Google Play in the Android app (they depend on the country), so none are shown here.
const PLANS: { id: Plan; title: string; note: string }[] = [
  { id: 'monthly', title: 'Monthly', note: 'All Plus features · renews monthly, cancel anytime in Google Play' },
  { id: 'pass', title: '24-hour gender pass', note: 'Gender filter for one day · one-time purchase' },
]

export function Paywall({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [plan, setPlan] = useState<Plan>('monthly')
  return (
    <Modal open={open} onClose={onClose} hideHeader night className="max-w-md">
      <div className="relative overflow-hidden">
        <div className="relative isolate overflow-hidden px-6 pt-8 pb-6 text-center">
          <Aurora intensity="soft" />
          <button
            type="button"
            onClick={onClose}
            className="absolute top-4 right-4 z-10 grid size-9 place-items-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label="Close"
          >
            <span aria-hidden className="text-xl leading-none">×</span>
          </button>
          <motion.div
            initial={{ scale: 0.6, rotate: -12, opacity: 0 }}
            animate={{ scale: 1, rotate: 0, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 300, damping: 16, delay: 0.05 }}
            className="relative mx-auto grid size-16 place-items-center rounded-[20px] bg-brand text-2xl font-extrabold text-primary-foreground shadow-[0_16px_40px_-12px_rgb(255_61_104/0.9)]"
            aria-hidden
          >
            +
          </motion.div>
          <h2 className="relative mt-5 text-2xl font-extrabold tracking-tight">
            Yaari <span className="text-brand">Plus</span>
          </h2>
          <p className="relative mt-1.5 text-sm leading-relaxed text-muted-foreground">
            More control over every conversation.
          </p>
        </div>

        <ul className="flex flex-col gap-3 px-6">
          {BENEFITS.map((b, i) => (
            <motion.li
              key={b.title}
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.12 + i * 0.06 }}
              className="flex items-start gap-3.5"
            >
              <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary/15 text-primary">
                <b.icon className="size-5" aria-hidden />
              </span>
              <span>
                <span className="block text-sm font-bold">{b.title}</span>
                <span className="block text-sm leading-relaxed text-muted-foreground">{b.body}</span>
              </span>
            </motion.li>
          ))}
        </ul>

        <div role="radiogroup" aria-label="Plans" className="mt-6 flex flex-col gap-2.5 px-6">
          {PLANS.map((p) => {
            const active = plan === p.id
            return (
              <motion.button
                key={p.id}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setPlan(p.id)}
                whileHover={{ y: -2 }}
                whileTap={{ scale: 0.98 }}
                className={cn(
                  'relative flex items-center gap-3.5 rounded-2xl border p-4 text-left transition-[border-color,box-shadow,background-color]',
                  active
                    ? 'border-primary bg-primary/8 shadow-[0_0_0_4px_rgb(255_61_104/0.14)]'
                    : 'border-input hover:border-muted-foreground/40',
                )}
              >
                <span
                  className={cn(
                    'grid size-5.5 shrink-0 place-items-center rounded-full border-2 transition-colors',
                    active ? 'border-primary bg-primary text-primary-foreground' : 'border-input',
                  )}
                  aria-hidden
                >
                  {active ? <Check className="size-3.5" strokeWidth={3} /> : null}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2 text-sm font-bold">
                    {p.title}
                  </span>
                  <span className="block text-xs text-muted-foreground">{p.note}</span>
                </span>
              </motion.button>
            )
          })}
        </div>

        <div className="mt-6 flex flex-col gap-3 px-6 pb-6">
          <div className="flex items-start gap-3 rounded-2xl bg-muted p-4">
            <Smartphone className="mt-0.5 size-5 shrink-0 text-accent" aria-hidden />
            <p className="text-sm leading-relaxed text-muted-foreground">
              <span className="font-semibold text-foreground">Get it in the Android app.</span> Purchases are
              made in the app. Plus then works here too, on the same account.
            </p>
          </div>
          {PLAY_STORE_URL ? (
          <motion.a
            href={PLAY_STORE_URL}
            target="_blank"
            rel="noreferrer"
            whileHover={{ y: -2 }}
            whileTap={{ scale: 0.97 }}
            className="flex h-14 items-center justify-center gap-3 rounded-2xl bg-ink-foreground px-5 text-ink ring-1 ring-ink-foreground/20"
          >
            <svg viewBox="0 0 24 24" className="size-6" aria-hidden>
              <path d="M4 2.8v18.4c0 .4.4.6.7.4L15 12 4.7 2.4c-.3-.2-.7 0-.7.4z" fill="#34a853" />
              <path d="M15 12l3.2-3.1-11.6-6.6c-.6-.3-1.3-.4-1.9-.3z" fill="#ea4335" opacity="0.9" />
              <path d="M15 12l3.2 3.1-11.6 6.6c-.6.3-1.3.4-1.9.3z" fill="#4285f4" />
              <path d="M18.2 8.9l2.7 1.6c1.1.6 1.1 2.4 0 3l-2.7 1.6L15 12z" fill="#fbbc04" />
            </svg>
            <span className="flex flex-col text-left leading-tight">
              <span className="text-[10px] font-semibold tracking-wide uppercase opacity-70">Get it on</span>
              <span className="text-lg font-bold">Google Play</span>
            </span>
          </motion.a>
          ) : (
            <p className="text-center text-xs font-medium text-muted-foreground">The Android app is coming to Google Play soon.</p>
          )}
        </div>
      </div>
    </Modal>
  )
}
