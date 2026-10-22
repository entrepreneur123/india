'use client'

import { useState } from 'react'
import { Check, Smartphone, Users, Video, X, Zap } from 'lucide-react'
import type { TKey } from '@/lib/i18n'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { PLAY_STORE_URL } from '@/lib/config'
import { cn } from '@/lib/utils'

const BENEFITS: { icon: typeof Users; title: TKey; body: TKey }[] = [
  { icon: Users, title: 'plus.b1Title', body: 'plus.b1Body' },
  { icon: Video, title: 'plus.b2Title', body: 'plus.b2Body' },
  { icon: Zap, title: 'plus.b3Title', body: 'plus.b3Body' },
]

type Plan = 'monthly' | 'pass'

// Prices come from Google Play in the Android app (they depend on the country), so none are shown here.
const PLANS: { id: Plan; title: TKey; note: TKey }[] = [
  { id: 'monthly', title: 'plus.monthly', note: 'plus.monthlyNote' },
  { id: 'pass', title: 'plus.pass', note: 'plus.passNote' },
]

export function Paywall({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useI18n()
  const [plan, setPlan] = useState<Plan>('monthly')
  return (
    <Modal open={open} onClose={onClose} hideHeader className="max-w-md">
      <div className="relative">
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 inset-e-4 z-10 grid size-9 place-items-center rounded-full text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
          aria-label={t('common.close')}
        >
          <X className="size-5" aria-hidden />
        </button>
        <div className="px-6 pt-8 pb-6 text-center">
          <div className="mx-auto grid size-14 place-items-center rounded-2xl bg-primary-soft text-2xl font-bold text-primary-text" aria-hidden>
            +
          </div>
          <h2 className="mt-4 text-2xl font-bold tracking-tight">{t('common.plus')}</h2>
          <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{t('plus.tagline')}</p>
        </div>

        <ul className="flex flex-col gap-4 px-6">
          {BENEFITS.map((b) => (
            <li key={b.title} className="flex items-start gap-3.5">
              <span className="grid size-10 shrink-0 place-items-center rounded-full bg-primary-soft text-primary-text">
                <b.icon className="size-5" aria-hidden />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold">{t(b.title)}</span>
                <span className="block text-sm leading-relaxed text-muted-foreground">{t(b.body)}</span>
              </span>
            </li>
          ))}
        </ul>

        <div role="radiogroup" aria-label={t('plus.plans')} className="mt-6 flex flex-col gap-2.5 px-6">
          {PLANS.map((p) => {
            const active = plan === p.id
            return (
              <button
                key={p.id}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setPlan(p.id)}
                className={cn(
                  'relative flex items-center gap-3.5 rounded-2xl border p-4 text-start transition-colors duration-150',
                  active ? 'border-primary bg-primary-soft' : 'border-input hover:bg-muted',
                )}
              >
                <span
                  className={cn(
                    'grid size-5.5 shrink-0 place-items-center rounded-full border-2 transition-colors duration-150',
                    active ? 'border-primary bg-primary text-primary-foreground' : 'border-input',
                  )}
                  aria-hidden
                >
                  {active ? <Check className="size-3.5" strokeWidth={3} /> : null}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold">{t(p.title)}</span>
                  <span className="block text-xs text-muted-foreground">{t(p.note)}</span>
                </span>
              </button>
            )
          })}
        </div>

        <div className="mt-6 flex flex-col gap-3 px-6 pb-6">
          <div className="flex items-start gap-3 rounded-2xl bg-muted p-4">
            <Smartphone className="mt-0.5 size-5 shrink-0 text-primary-text" aria-hidden />
            <p className="text-sm leading-relaxed text-muted-foreground">
              <span className="font-semibold text-foreground">{t('plus.getApp')}</span> {t('plus.getAppBody')}
            </p>
          </div>
          {PLAY_STORE_URL ? (
            <a
              href={PLAY_STORE_URL}
              target="_blank"
              rel="noreferrer"
              className="flex h-14 items-center justify-center gap-3 rounded-2xl bg-foreground px-5 text-background transition-opacity duration-150 hover:opacity-90"
            >
              <svg viewBox="0 0 24 24" className="size-6" aria-hidden>
                <path d="M4 2.8v18.4c0 .4.4.6.7.4L15 12 4.7 2.4c-.3-.2-.7 0-.7.4z" fill="#34a853" />
                <path d="M15 12l3.2-3.1-11.6-6.6c-.6-.3-1.3-.4-1.9-.3z" fill="#ea4335" opacity="0.9" />
                <path d="M15 12l3.2 3.1-11.6 6.6c-.6.3-1.3.4-1.9.3z" fill="#4285f4" />
                <path d="M18.2 8.9l2.7 1.6c1.1.6 1.1 2.4 0 3l-2.7 1.6L15 12z" fill="#fbbc04" />
              </svg>
              <span className="flex flex-col text-start leading-tight">
                <span className="text-[10px] font-semibold tracking-wide uppercase opacity-80">{t('plus.getItOn')}</span>
                <span className="text-lg font-bold">Google Play</span>
              </span>
            </a>
          ) : (
            <p className="text-center text-xs font-medium text-muted-foreground">{t('plus.soon')}</p>
          )}
        </div>
      </div>
    </Modal>
  )
}
