'use client'

import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { DEMO_MODE } from '@/lib/config'
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
          toast.show('That code didn’t match. Try again.', 'error')
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
      toast.show('New code sent', 'success')
    } catch (err) {
      toast.error(err)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <button
        type="button"
        onClick={onBack}
        className="-ml-2 flex w-fit items-center gap-1.5 rounded-full px-2 py-1 text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Change number
      </button>
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-extrabold tracking-tight">Enter the code</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">
          Sent to <span className="font-semibold text-foreground tabular-nums">{display}</span>
        </p>
      </div>
      <OtpInput value={code} onChange={setCode} onComplete={verify} invalid={invalid} disabled={busy} />
      {DEMO_MODE ? (
        <p className="rounded-2xl bg-muted px-4 py-3 text-xs leading-relaxed text-muted-foreground">
          Demo preview: any 6 digits will work.
        </p>
      ) : null}
      <Button
        variant="brand"
        size="lg"
        className="w-full"
        disabled={code.length !== 6}
        loading={busy}
        onClick={() => void verify(code)}
      >
        Verify
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        {wait > 0 ? (
          <>
            Resend code in <span className="font-semibold tabular-nums text-foreground">0:{String(wait).padStart(2, '0')}</span>
          </>
        ) : (
          <button type="button" onClick={() => void resend()} className="font-semibold text-primary hover:underline">
            Resend code
          </button>
        )}
      </p>
    </div>
  )
}
