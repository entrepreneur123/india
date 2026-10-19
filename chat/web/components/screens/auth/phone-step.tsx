'use client'

import { useState, type FormEvent } from 'react'
import { ArrowRight } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { phoneDigits } from '@/lib/format'
import { DEFAULT_COUNTRY, type Country } from '@/lib/countries'
import { PRIVACY_URL } from '@/lib/config'
import { Button } from '@/components/ui/pressable'
import { TextInput } from '@/components/ui/bits'
import { useToast } from '@/components/ui/toast'
import { CountrySelect } from './country-select'

export function PhoneStep({ onSent }: { onSent: (digits: string, display: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const [country, setCountry] = useState<Country>(DEFAULT_COUNTRY)
  const [number, setNumber] = useState('')
  const [busy, setBusy] = useState(false)
  const digits = phoneDigits(country.dial, number)
  const valid = digits !== null

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!digits || busy) return
    setBusy(true)
    try {
      await backend.sendOtp(digits)
      onSent(digits, `+${country.dial} ${digits.slice(country.dial.length)}`)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-extrabold tracking-tight text-balance">{'What’s your number?'}</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">
          {'We’ll text you a 6-digit code. Your number is never shown to anyone.'}
        </p>
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="phone" className="text-sm font-semibold">
          Phone number
        </label>
        <div className="flex gap-2">
          <CountrySelect value={country} onChange={setCountry} />
          <TextInput
            id="phone"
            inputMode="numeric"
            autoComplete="tel-national"
            placeholder="98765 43210"
            value={number}
            onChange={(e) => setNumber(e.target.value.replace(/[^\d+\s-]/g, '').slice(0, 20))}
            className="flex-1 tabular-nums tracking-wide"
            autoFocus
          />
        </div>
      </div>
      <Button type="submit" variant="brand" size="lg" disabled={!valid} loading={busy} className="w-full">
        Send code
        <ArrowRight className="size-4.5" aria-hidden />
      </Button>
      <p className="text-center text-xs leading-relaxed text-muted-foreground">
        By continuing you confirm you are 18+ and have read our{' '}
        <a href={PRIVACY_URL} target="_blank" rel="noreferrer" className="font-semibold text-foreground underline-offset-2 hover:underline">
          Privacy Policy
        </a>
        .
      </p>
    </form>
  )
}
