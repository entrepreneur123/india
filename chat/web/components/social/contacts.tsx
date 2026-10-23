'use client'

import { useEffect, useId, useRef, useState } from 'react'
import useSWR from 'swr'
import { BookUser, Phone, ShieldCheck } from 'lucide-react'
import { BackendError } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { CONTACT_APPS, CONTACT_NAMES, cleanContact, contactLink, isNumber, type ContactApp, type Contacts } from '@/lib/contacts'
import { Field, Skeleton, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { useI18n } from '@/components/i18n'
import { useSocialT, type SocialT } from './social-text'

// 044: friends exchange messenger contacts. I keep mine once (the editor) and choose each friend who sees them.

const BRAND: Record<ContactApp, [bg: string, fg: string]> = {
  whatsapp: ['#25D366', '#fff'], phone: ['var(--color-primary)', '#fff'], telegram: ['#229ED9', '#fff'], instagram: ['#E4405F', '#fff'],
  line: ['#06C755', '#fff'], kakaotalk: ['#FEE500', '#191919'], wechat: ['#07C160', '#fff'], snapchat: ['#FFFC00', '#191919'],
  discord: ['#5865F2', '#fff'],
}
const appName = (app: ContactApp, st: SocialT) => (app === 'phone' ? st('contacts.phone') : CONTACT_NAMES[app])

function AppBadge({ app }: { app: ContactApp }) {
  const [bg, fg] = BRAND[app]
  return (
    <span className="grid size-8 shrink-0 place-items-center rounded-full text-sm font-bold" style={{ background: bg, color: fg }} aria-hidden>
      {app === 'phone' ? <Phone className="size-4" /> : CONTACT_NAMES[app][0]}
    </span>
  )
}

/** My contacts: one field per app, checked like the server does. `onSaved`: what to do with them next (share). */
export function ContactsEditor({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved?: (c: Contacts) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const { t } = useI18n()
  const id = useId()
  const { data, mutate } = useSWR(open ? 'myContacts' : null, () => backend.myContacts())
  const [values, setValues] = useState<Contacts>({})
  const [refused, setRefused] = useState<ContactApp | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (open && data) setValues(data.contacts)
    setRefused(null)
  }, [open, data])

  const bad = CONTACT_APPS.filter((a) => cleanContact(a, values[a] ?? '') === null || a === refused)
  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (bad.length) return
    setBusy(true)
    try {
      const contacts = await backend.setContacts(values)
      await mutate((d) => d && { ...d, contacts }, { revalidate: false })
      toast.show(st('contacts.saved'), 'success')
      onClose()
      onSaved?.(contacts)
    } catch (err) {
      if (err instanceof BackendError && err.code === 'contact_invalid') setRefused(err.detail as ContactApp)
      else toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={st('contacts.title')} description={st('contacts.note')}>
      <form onSubmit={(e) => void save(e)} className="flex flex-col gap-4 p-6 pt-5">
        <p className="flex gap-2.5 rounded-2xl bg-muted p-3 text-sm leading-relaxed">
          <ShieldCheck className="mt-0.5 size-4.5 shrink-0 text-primary-text" aria-hidden />
          {st('contacts.safety')}
        </p>
        {data ? (
          CONTACT_APPS.map((app) => (
            <Field
              key={app}
              label={appName(app, st)}
              htmlFor={`${id}-${app}`}
              error={bad.includes(app) ? st(isNumber(app) ? 'contacts.badNumber' : 'contacts.badHandle') : null}
            >
              <div className="flex items-center gap-3">
                <AppBadge app={app} />
                <TextInput
                  id={`${id}-${app}`}
                  value={values[app] ?? ''}
                  maxLength={isNumber(app) ? 24 : 41}
                  inputMode={isNumber(app) ? 'tel' : 'text'}
                  autoComplete="off"
                  autoCapitalize="none"
                  spellCheck={false}
                  dir="ltr"
                  placeholder={isNumber(app) ? '+91 98765 43210' : '@username'}
                  aria-invalid={bad.includes(app)}
                  onChange={(e) => {
                    setValues((v) => ({ ...v, [app]: e.target.value }))
                    if (refused === app) setRefused(null)
                  }}
                />
              </div>
            </Field>
          ))
        ) : (
          <Skeleton className="h-64 w-full" />
        )}
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!data || bad.length > 0}>
          {t('common.save')}
        </Button>
      </form>
    </Modal>
  )
}

/** A friend's contacts (tap opens the app, long-press or right-click copies) and my share switch. Hidden unless we're friends. */
export function FriendContacts({ userId, name, className }: { userId: string; name: string; className?: string }) {
  const backend = useBackend()
  const toast = useToast()
  const { st } = useSocialT()
  const { data, mutate } = useSWR(['contactsOf', userId], () => backend.contactsOf(userId))
  const { data: mine } = useSWR('myContacts', () => backend.myContacts())
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  if (!data) return null

  const share = async (on: boolean) => {
    setBusy(true)
    try {
      await backend.shareContacts(userId, on)
      await mutate({ ...data, mineShared: on }, { revalidate: false })
      toast.show(st(on ? 'contacts.sharedToast' : 'contacts.stoppedToast', { name }), 'success')
    } catch (err) {
      toast.error(err)
      void mutate()
    } finally {
      setBusy(false)
    }
  }
  const theirs = CONTACT_APPS.filter((a) => data.contacts?.[a])

  return (
    <section className={className}>
      <h2 className="text-sm font-semibold">{st('contacts.section')}</h2>
      {theirs.length ? (
        <>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {theirs.map((app) => <ContactChip key={app} app={app} value={data.contacts![app]!} />)}
          </div>
          <p className="mt-1.5 text-xs text-muted-foreground">{st('contacts.hint')}</p>
        </>
      ) : (
        <p className="mt-1 text-sm text-muted-foreground">{st('contacts.none', { name })}</p>
      )}
      {data.mineShared ? (
        <div className="mt-3 flex items-center gap-3 rounded-2xl border border-border p-3">
          <p className="min-w-0 flex-1 text-sm">{st('contacts.sharing', { name })}</p>
          <Button size="sm" variant="outline" loading={busy} onClick={() => void share(false)}>
            {st('contacts.stop')}
          </Button>
        </div>
      ) : (
        <Button
          variant="outline"
          className="mt-3 w-full"
          loading={busy}
          disabled={!mine}
          onClick={() => (Object.keys(mine!.contacts).length ? void share(true) : setEditing(true))}
        >
          <BookUser className="size-4.5" aria-hidden /> {st('contacts.share', { name })}
        </Button>
      )}
      <ContactsEditor open={editing} onClose={() => setEditing(false)} onSaved={(c) => Object.keys(c).length && void share(true)} />
    </section>
  )
}

function ContactChip({ app, value }: { app: ContactApp; value: string }) {
  const toast = useToast()
  const { st } = useSocialT()
  const timer = useRef<number | undefined>(undefined)
  const held = useRef(false)
  const link = contactLink(app, value)
  const copy = async () => {
    held.current = true
    window.clearTimeout(timer.current)
    try {
      await navigator.clipboard.writeText(value)
      toast.show(st('contacts.copied'), 'success')
    } catch {
      toast.show(value)
    }
  }
  const stop = () => window.clearTimeout(timer.current)

  return (
    <button
      type="button"
      onPointerDown={() => {
        held.current = false
        timer.current = window.setTimeout(() => void copy(), 500)
      }}
      onPointerUp={stop}
      onPointerLeave={stop}
      onPointerCancel={stop}
      onContextMenu={(e) => {
        e.preventDefault()
        if (!held.current) void copy()
      }}
      onClick={() => {
        if (held.current) return
        if (!link) void copy()
        else if (link.startsWith('tel:')) location.href = link
        else window.open(link, '_blank', 'noopener')
      }}
      className="flex min-w-0 select-none items-center gap-2.5 rounded-2xl border border-border p-2.5 text-start transition-colors duration-150 hover:bg-muted [-webkit-touch-callout:none]"
    >
      <AppBadge app={app} />
      <span className="min-w-0">
        <span className="block text-xs text-muted-foreground">{appName(app, st)}</span>
        <span className="block truncate text-sm font-semibold" dir="ltr">{value}</span>
      </span>
    </button>
  )
}

/** The same, from a friend's chat header. */
export function FriendContactsSheet({ open, onClose, userId, name }: { open: boolean; onClose: () => void; userId: string; name: string }) {
  const { st } = useSocialT()
  return (
    <Modal open={open} onClose={onClose} title={st('contacts.section')}>
      <FriendContacts userId={userId} name={name} className="p-6 pt-2 [&>h2]:sr-only" />
    </Modal>
  )
}
