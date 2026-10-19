'use client'

import { useState, type FormEvent } from 'react'
import { ArrowRight } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { phoneDigits } from '@/lib/format'
import { guessCountry, type Country } from '@/lib/countries'
import { PRIVACY_URL } from '@/lib/config'
import { Rich, useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { TextInput } from '@/components/ui/bits'
import { useToast } from '@/components/ui/toast'
import { CountrySelect } from './country-select'

export function PhoneStep({ onSent }: { onSent: (digits: string, display: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [country, setCountry] = useState<Country>(() => {
    if (typeof navigator === 'undefined') return guessCountry([])
    let zone: string | undefined
    try {
      zone = Intl.DateTimeFormat().resolvedOptions().timeZone
    } catch {
      /* no time zone support */
    }
    return guessCountry(navigator.languages?.length ? navigator.languages : [navigator.language], zone)
  })
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
        <h2 className="text-2xl font-bold tracking-tight text-balance">{t('phone.title')}</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">{t('phone.body')}</p>
      </div>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="phone" className="text-sm font-semibold">
          {t('phone.label')}
        </label>
        <div className="flex gap-2">
          <CountrySelect value={country} onChange={setCountry} />
          <TextInput
            id="phone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            placeholder={country.iso === 'IN' ? '98765 43210' : ''}
            value={number}
            onChange={(e) => setNumber(e.target.value.replace(/[^\d+\s-]/g, '').slice(0, 20))}
            className="min-w-0 flex-1 tabular-nums tracking-wide"
            dir="ltr"
            autoFocus
          />
        </div>
      </div>
      <Button type="submit" variant="brand" size="lg" disabled={!valid} loading={busy} className="w-full">
        {t('phone.send')}
        <ArrowRight className="size-4.5 rtl:rotate-180" aria-hidden />
      </Button>
      <p className="text-center text-xs leading-relaxed text-muted-foreground">
        <Rich
          text={t('phone.legal')}
          mark={(s) => (
            <a href={PRIVACY_URL} target="_blank" rel="noreferrer" className="font-semibold text-foreground underline underline-offset-2">
              {s}
            </a>
          )}
        />
      </p>
    </form>
  )
}
