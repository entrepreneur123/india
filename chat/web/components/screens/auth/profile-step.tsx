'use client'

import { useEffect, useState, type FormEvent } from 'react'
import { Info } from 'lucide-react'
import type { Gender, Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { ageFromBirthDate, errorCode, maxBirthDate, minBirthDate } from '@/lib/format'
import { handleSpelling, nameClean, suggestHandle } from '@/lib/handle'
import { COMMUNITY_RULES } from '@/lib/config'
import { useI18n } from '@/components/i18n'
import { HandleField, handleUsable, useHandleCheck } from '@/components/looks/handle-field'
import { useLooksT } from '@/components/looks/looks-text'
import { Button } from '@/components/ui/pressable'
import { Field, Segmented, TextArea, TextInput } from '@/components/ui/bits'
import { useToast } from '@/components/ui/toast'
import { PhotoPicker } from '@/components/screens/me/photo-picker'
import { guessCountry, type Country } from '@/lib/countries'
import { CountrySelect } from './country-select'

const BIO_MAX = 150

/** `guest` (123): no phone to take the country from, so the person picks it (the sign-up's own country list). */
export function ProfileStep({ onCreated, guest = false }: { onCreated: (p: Profile) => void; guest?: boolean }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const { lt } = useLooksT()
  const [photo, setPhoto] = useState<File | null>(null)
  const [name, setName] = useState('')
  // The ID follows the name until it is typed in itself (the step comes after the code, so the person is signed in
  // and the server can be asked while typing).
  const [handle, setHandle] = useState('')
  const [handleEdited, setHandleEdited] = useState(false)
  const handleState = useHandleCheck(handle, null)
  const [gender, setGender] = useState<Gender | ''>('')
  const [birthDate, setBirthDate] = useState('')
  const [bio, setBio] = useState('')
  const [country, setCountry] = useState<Country>(() => guessCountry(typeof navigator === 'undefined' ? [] : navigator.languages ?? [navigator.language]))
  const [busy, setBusy] = useState(false)
  const [touched, setTouched] = useState(false)
  const [agreed, setAgreed] = useState(false)

  useEffect(() => {
    if (!handleEdited) setHandle(suggestHandle(name))
  }, [name, handleEdited])
  // The suggestion is someone else's already: offer it with a few digits instead.
  useEffect(() => {
    if (!handleEdited && handleState === 'taken') setHandle(suggestHandle(name, String(100 + Math.floor(Math.random() * 900))))
  }, [handleState, handleEdited, name])

  const age = birthDate ? ageFromBirthDate(birthDate) : null
  const ageError = birthDate && (age === null || age < 18) ? t('pf.under18') : null
  const plainName = nameClean(name)
  // An empty ID keeps the free one the server gives; one being checked is judged by set_handle.
  const handleOk = !handle.trim() || handleState === 'checking' || handleUsable(handleState)
  // A profile photo is required to start (the owner, 2026-10-26).
  const valid = photo !== null && name.trim().length >= 1 && plainName && handleOk && gender !== '' && age !== null && age >= 18 && age <= 100 && agreed

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setTouched(true)
    if (!valid || busy || !gender) return
    setBusy(true)
    try {
      const p = await backend.createProfile({
        name: name.trim(),
        gender,
        birthDate,
        city: '', // country only (the owner, 2026-10-26)
        bio: bio.trim(),
        photo,
        country: guest ? country.iso : undefined,
      })
      // The profile comes with a free ID (lisa_4821); then the chosen one. Taken meanwhile: the free one stays.
      const wanted = handleSpelling(handle)
      let mine = p.handle
      if (wanted && wanted !== mine) {
        try {
          mine = await backend.setHandle(wanted)
        } catch (err) {
          if (errorCode(err) === 'handle_taken') toast.show(lt('id.keptFree', { handle: mine ?? '' }), 'info')
          else toast.error(err)
        }
      }
      onCreated({ ...p, handle: mine })
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-bold tracking-tight">{t('pf.title')}</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">{t('pf.body')}</p>
      </div>

      <div className="flex flex-col gap-1.5">
        <PhotoPicker file={photo} onChange={setPhoto} name={name} />
        {touched && !photo ? (
          <p role="alert" className="text-sm font-medium text-destructive">
            {t('pf.photoRequired')}
          </p>
        ) : null}
      </div>

      <Field
        label={t('pf.name')}
        htmlFor="pf-name"
        error={!plainName ? t('error.name_invalid') : touched && name.trim().length < 1 ? t('pf.nameError') : null}
      >
        <TextInput
          id="pf-name"
          value={name}
          maxLength={30}
          onChange={(e) => setName(e.target.value)}
          autoComplete="given-name"
          placeholder={t('pf.namePlaceholder')}
        />
      </Field>

      <HandleField
        id="pf-handle"
        value={handle}
        state={handleState}
        onChange={(v) => {
          setHandleEdited(true)
          setHandle(v)
        }}
      />

      <div className="flex flex-col gap-1.5">
        <span id="pf-gender" className="text-sm font-semibold">
          {t('pf.iam')}
        </span>
        <Segmented<Gender | ''>
          label={t('pf.gender')}
          value={gender}
          onChange={setGender}
          className="w-full"
          options={[
            { value: 'female', label: t('pf.woman') },
            { value: 'male', label: t('pf.man') },
            { value: 'other', label: t('pf.other') },
          ]}
        />
        {touched && gender === '' ? (
          <p className="text-xs font-medium text-destructive" role="alert">
            {t('pf.chooseOne')}
          </p>
        ) : null}
      </div>

      <Field
        label={t('pf.birth')}
        htmlFor="pf-dob"
        error={ageError ?? (touched && !birthDate ? t('pf.birthError') : null)}
        hint={age !== null && age >= 18 ? t('pf.age', { age }) : undefined}
      >
        <TextInput id="pf-dob" type="date" value={birthDate} max={maxBirthDate()} min={minBirthDate()} onChange={(e) => setBirthDate(e.target.value)} />
      </Field>

      {guest ? (
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-semibold">{t('pf.country')}</span>
          <CountrySelect value={country} onChange={setCountry} />
        </div>
      ) : null}

      <p className="flex gap-2.5 rounded-xl bg-primary-soft px-4 py-3 text-xs leading-relaxed text-foreground">
        <Info className="mt-px size-4 shrink-0 text-primary-text" aria-hidden />
        {t('pf.fixed')}
      </p>


      <Field
        label={t('pf.bio')}
        htmlFor="pf-bio"
        trailing={<span className="text-xs tabular-nums text-muted-foreground">{bio.length}/{BIO_MAX}</span>}
      >
        <TextArea id="pf-bio" value={bio} maxLength={BIO_MAX} onChange={(e) => setBio(e.target.value)} placeholder={t('pf.bioPlaceholder')} />
      </Field>

      <details className="rounded-xl border border-border px-4 py-3 text-sm">
        <summary className="cursor-pointer font-semibold">{t('pf.rules')}</summary>
        <ul className="mt-2 flex list-disc flex-col gap-1 ps-5 text-muted-foreground">
          {COMMUNITY_RULES.map((r) => (
            <li key={r}>{t(r)}</li>
          ))}
        </ul>
      </details>
      <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed">
        <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-0.5 size-5 shrink-0 accent-primary" />
        <span>{t('pf.agree')}</span>
      </label>
      {touched && !agreed ? (
        <p className="-mt-3 text-xs font-medium text-destructive" role="alert">
          {t('pf.agreeError')}
        </p>
      ) : null}

      <Button type="submit" variant="brand" size="lg" loading={busy} className="w-full">
        {t('pf.submit')}
      </Button>
    </form>
  )
}
