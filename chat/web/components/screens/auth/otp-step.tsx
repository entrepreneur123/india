'use client'

import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { DEMO_MODE } from '@/lib/config'
import { Rich, useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { OtpInput } from './otp-input'

const RESEND_AFTER = 30

export function OtpStep({
  phone,
  display,
  onBack,
  onVerified,
}: {
  phone: string
  display: string
  onBack: () => void
  onVerified: (existing: Profile | null) => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [invalid, setInvalid] = useState(false)
  const [wait, setWait] = useState(RESEND_AFTER)

  useEffect(() => {
    if (wait <= 0) return
    const id = setTimeout(() => setWait((w) => w - 1), 1000)
    return () => clearTimeout(id)
  }, [wait])

  const verify = async (value: string) => {
    if (busy) return
    setBusy(true)
    setInvalid(false)
    try {
      // A code works once: after it succeeded, a retry (profile load failed) only loads the profile again.
      if (!(await backend.isSignedIn())) {
        const ok = await backend.verifyOtp(phone, value)
        if (!ok) {
          setInvalid(true)
          setCode('')
          toast.show(t('otp.wrong'), 'error')
          return
        }
      }
      onVerified(await backend.myProfile())
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  const resend = async () => {
    try {
      await backend.sendOtp(phone)
      setWait(RESEND_AFTER)
      toast.show(t('otp.sent'), 'success')
    } catch (err) {
      toast.error(err)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <button
        type="button"
        onClick={onBack}
        className="-ms-2 flex w-fit items-center gap-1.5 rounded-full px-2 py-1 text-sm font-semibold text-muted-foreground transition-colors duration-150 hover:text-foreground"
      >
        <ArrowLeft className="size-4 rtl:rotate-180" aria-hidden />
        {t('otp.change')}
      </button>
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-bold tracking-tight">{t('otp.title')}</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">
          <Rich
            text={t('otp.sentTo')}
            nodes={{
              phone: (
                <span className="font-semibold text-foreground tabular-nums" dir="ltr">
                  {display}
                </span>
              ),
            }}
          />
        </p>
      </div>
      <OtpInput value={code} onChange={setCode} onComplete={verify} invalid={invalid} disabled={busy} />
      {DEMO_MODE ? <p className="rounded-xl bg-muted px-4 py-3 text-xs leading-relaxed text-muted-foreground">{t('otp.demo')}</p> : null}
      <Button variant="brand" size="lg" className="w-full" disabled={code.length !== 6} loading={busy} onClick={() => void verify(code)}>
        {t('otp.verify')}
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        {wait > 0 ? (
          <Rich
            text={t('otp.resendIn')}
            nodes={{ time: <span className="font-semibold tabular-nums text-foreground">0:{String(wait).padStart(2, '0')}</span> }}
          />
        ) : (
          <button type="button" onClick={() => void resend()} className="rounded font-semibold text-primary-text hover:underline">
            {t('otp.resend')}
          </button>
        )}
      </p>
    </div>
  )
}
