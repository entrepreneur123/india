'use client'

import { useState, type FormEvent } from 'react'
import { Info } from 'lucide-react'
import type { Gender, Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { ageFromBirthDate, maxBirthDate, minBirthDate } from '@/lib/format'
import { COMMUNITY_RULES } from '@/lib/config'
import { Button } from '@/components/ui/pressable'
import { Field, Segmented, TextArea, TextInput } from '@/components/ui/bits'
import { useToast } from '@/components/ui/toast'
import { PhotoPicker } from '@/components/screens/me/photo-picker'

const BIO_MAX = 150

export function ProfileStep({ onCreated }: { onCreated: (p: Profile) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const [photo, setPhoto] = useState<File | null>(null)
  const [name, setName] = useState('')
  const [gender, setGender] = useState<Gender | ''>('')
  const [birthDate, setBirthDate] = useState('')
  const [city, setCity] = useState('')
  const [bio, setBio] = useState('')
  const [busy, setBusy] = useState(false)
  const [touched, setTouched] = useState(false)
  const [agreed, setAgreed] = useState(false)

  const age = birthDate ? ageFromBirthDate(birthDate) : null
  const ageError = birthDate && (age === null || age < 18) ? 'You must be 18 or older to use yaari.' : null
  const valid = name.trim().length >= 2 && gender !== '' && age !== null && age >= 18 && age <= 100 && city.trim().length >= 2 && agreed

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
        city: city.trim(),
        bio: bio.trim(),
        photo,
      })
      onCreated(p)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl font-extrabold tracking-tight">Make your profile</h2>
        <p className="text-sm leading-relaxed text-muted-foreground">This is what people see when you match.</p>
      </div>

      <PhotoPicker file={photo} onChange={setPhoto} name={name} />

      <Field label="Name" htmlFor="pf-name" error={touched && name.trim().length < 2 ? 'Add your first name' : null}>
        <TextInput id="pf-name" value={name} maxLength={30} onChange={(e) => setName(e.target.value)} autoComplete="given-name" placeholder="Your first name" />
      </Field>

      <div className="flex flex-col gap-1.5">
        <span id="pf-gender" className="text-sm font-semibold">
          I am
        </span>
        <Segmented<Gender | ''>
          label="Gender"
          value={gender}
          onChange={setGender}
          className="w-full"
          options={[
            { value: 'female', label: 'Woman' },
            { value: 'male', label: 'Man' },
            { value: 'other', label: 'Other' },
          ]}
        />
        {touched && gender === '' ? (
          <p className="text-xs font-medium text-destructive" role="alert">
            Choose one
          </p>
        ) : null}
      </div>

      <Field label="Birth date" htmlFor="pf-dob" error={ageError ?? (touched && !birthDate ? 'Add your birth date' : null)} hint={age !== null && age >= 18 ? `You’re ${age}` : undefined}>
        <TextInput id="pf-dob" type="date" value={birthDate} max={maxBirthDate()} min={minBirthDate()} onChange={(e) => setBirthDate(e.target.value)} />
      </Field>

      <p className="flex gap-2.5 rounded-2xl bg-accent/10 px-4 py-3 text-xs leading-relaxed text-foreground">
        <Info className="mt-px size-4 shrink-0 text-accent" aria-hidden />
        Gender and birth date can’t be changed later, so make sure they’re right.
      </p>

      <Field label="City" htmlFor="pf-city" error={touched && city.trim().length < 2 ? 'Add your city' : null}>
        <TextInput id="pf-city" value={city} maxLength={30} onChange={(e) => setCity(e.target.value)} autoComplete="address-level2" placeholder="e.g. Pune" />
      </Field>

      <Field
        label="Bio"
        htmlFor="pf-bio"
        trailing={<span className="text-xs tabular-nums text-muted-foreground">{bio.length}/{BIO_MAX}</span>}
      >
        <TextArea id="pf-bio" value={bio} maxLength={BIO_MAX} onChange={(e) => setBio(e.target.value)} placeholder="Chai or coffee? What are you into?" />
      </Field>

      <details className="rounded-2xl bg-muted px-4 py-3 text-sm">
        <summary className="cursor-pointer font-semibold">Community rules</summary>
        <ul className="mt-2 flex list-disc flex-col gap-1 pl-5 text-muted-foreground">
          {COMMUNITY_RULES.map((r) => (
            <li key={r}>{r}</li>
          ))}
        </ul>
      </details>
      <label className="flex cursor-pointer items-start gap-3 text-sm leading-relaxed">
        <input
          type="checkbox"
          checked={agreed}
          onChange={(e) => setAgreed(e.target.checked)}
          className="mt-0.5 size-5 shrink-0 accent-[#ff3d68]"
        />
        <span>I am 18 or older and I agree to the community rules.</span>
      </label>
      {touched && !agreed ? <p className="-mt-3 text-xs font-medium text-destructive" role="alert">Please accept the community rules.</p> : null}

      <Button type="submit" variant="brand" size="lg" loading={busy} className="w-full">
        Start chatting
      </Button>
    </form>
  )
}
