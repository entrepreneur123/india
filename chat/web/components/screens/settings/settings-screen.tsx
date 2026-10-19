'use client'

import { motion } from 'motion/react'
import { useState } from 'react'
import useSWR from 'swr'
import { ChevronRight, ExternalLink, Languages, LogOut, Mail, Phone, ScrollText, ShieldCheck, Sparkles, Trash2 } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { COMMUNITY_RULES, GRIEVANCE_OFFICER, PRIVACY_URL, SUPPORT_EMAIL } from '@/lib/config'
import { useApp } from '@/components/app-state'
import { Segmented } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

export function SettingsScreen() {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang, setLang, entitlements, openPaywall, signedOut, phone } = useApp()
  const { data: myPhone } = useSWR('my-phone', () => backend.myPhone())
  const [rulesOpen, setRulesOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [busy, setBusy] = useState<'out' | 'delete' | null>(null)

  const signOut = async () => {
    setBusy('out')
    try {
      await backend.signOut()
      signedOut()
    } catch (err) {
      toast.error(err)
      setBusy(null)
    }
  }

  const deleteAccount = async () => {
    setBusy('delete')
    try {
      await backend.deleteAccount()
      toast.show('Your account and everything in it were deleted', 'success')
      signedOut()
    } catch (err) {
      toast.error(err)
      setBusy(null)
    }
  }

  const plan = entitlements.premium ? 'Yaari Plus' : entitlements.genderPass ? '24-hour gender pass' : 'Free'

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-2xl flex-col gap-6 px-4 py-6 sm:px-8 sm:py-10">
        <h1 className="text-3xl font-extrabold tracking-tight">{t('settings.title')}</h1>

        <Group title={t('settings.account')}>
          <Item icon={Phone} label="Phone number" value={myPhone ?? phone ?? 'Verified'} />
          <Item
            icon={Sparkles}
            label="Plan"
            value={plan}
            onClick={entitlements.premium ? undefined : openPaywall}
            accent={!entitlements.premium}
          />
          <div className="flex items-center gap-3.5 px-4 py-3">
            <Icon icon={Languages} />
            <span className="flex-1 text-[15px] font-semibold">{t('settings.language')}</span>
            <Segmented
              label={t('settings.language')}
              value={lang}
              onChange={setLang}
              options={[
                { value: 'en', label: 'English' },
                { value: 'hi', label: 'हिन्दी' },
              ]}
            />
          </div>
        </Group>

        <Group title="Safety & legal">
          <Item icon={ScrollText} label="Community rules" onClick={() => setRulesOpen(true)} />
          <Item icon={ShieldCheck} label="Privacy policy" href={PRIVACY_URL} />
          <Item icon={Mail} label="Grievance officer" value={GRIEVANCE_OFFICER.email} href={`mailto:${GRIEVANCE_OFFICER.email}`} />
          <Item icon={Mail} label="Support" value={SUPPORT_EMAIL} href={`mailto:${SUPPORT_EMAIL}`} />
        </Group>

        <Group>
          <Item icon={LogOut} label={t('settings.signOut')} onClick={() => void signOut()} busy={busy === 'out'} />
          <Item icon={Trash2} label={t('settings.delete')} onClick={() => setDeleteOpen(true)} danger />
        </Group>
        <p className="px-2 text-center text-xs leading-relaxed text-muted-foreground">
          yaari is for adults (18+) only. Report anyone who breaks the rules — reports are anonymous.
        </p>
      </div>

      <Modal open={rulesOpen} onClose={() => setRulesOpen(false)} title="Community rules">
        <ol className="flex flex-col gap-3 p-6 pt-4">
          {COMMUNITY_RULES.map((rule, i) => (
            <li key={rule} className="flex gap-3 text-[15px] leading-relaxed">
              <span className="grid size-6 shrink-0 place-items-center rounded-full bg-primary/12 text-xs font-bold text-primary">{i + 1}</span>
              {rule}
            </li>
          ))}
        </ol>
      </Modal>

      <Modal
        open={deleteOpen}
        onClose={() => busy !== 'delete' && setDeleteOpen(false)}
        title="Delete your account?"
        description="Your profile, posts, chats and photos are deleted for good. This can’t be undone. An active subscription must be cancelled separately in Google Play."
        dismissible={busy !== 'delete'}
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="secondary" className="flex-1" disabled={busy === 'delete'} onClick={() => setDeleteOpen(false)}>
            Keep account
          </Button>
          <Button variant="danger" className="flex-1" loading={busy === 'delete'} onClick={() => void deleteAccount()}>
            Delete forever
          </Button>
        </div>
      </Modal>
    </div>
  )
}

function Group({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      {title ? <h2 className="px-2 text-xs font-bold tracking-wider text-muted-foreground uppercase">{title}</h2> : null}
      <div className="flex flex-col divide-y divide-border overflow-hidden rounded-3xl bg-card ring-1 ring-border">{children}</div>
    </section>
  )
}

function Icon({ icon: I, danger }: { icon: typeof Phone; danger?: boolean }) {
  return (
    <span className={cn('grid size-9 shrink-0 place-items-center rounded-xl', danger ? 'bg-destructive/10 text-destructive' : 'bg-muted text-foreground')}>
      <I className="size-4.5" aria-hidden />
    </span>
  )
}

function Item({
  icon,
  label,
  value,
  onClick,
  href,
  danger,
  accent,
  busy,
}: {
  icon: typeof Phone
  label: string
  value?: string
  onClick?: () => void
  href?: string
  danger?: boolean
  accent?: boolean
  busy?: boolean
}) {
  const body = (
    <>
      <Icon icon={icon} danger={danger} />
      <span className={cn('flex-1 text-[15px] font-semibold', danger && 'text-destructive')}>{label}</span>
      {value ? <span className={cn('truncate text-sm', accent ? 'font-semibold text-primary' : 'text-muted-foreground')}>{value}</span> : null}
      {href && !href.startsWith('mailto:') ? <ExternalLink className="size-4 text-muted-foreground" aria-hidden /> : null}
      {onClick ? <ChevronRight className="size-4 text-muted-foreground" aria-hidden /> : null}
    </>
  )
  const cls = 'flex w-full items-center gap-3.5 px-4 py-3 text-left transition-colors hover:bg-muted disabled:opacity-60'
  if (href) {
    return (
      <motion.a whileTap={{ scale: 0.99 }} href={href} target={href.startsWith('mailto:') ? undefined : '_blank'} rel="noreferrer" className={cls}>
        {body}
      </motion.a>
    )
  }
  if (onClick) {
    return (
      <motion.button whileTap={{ scale: 0.99 }} type="button" onClick={onClick} disabled={busy} className={cls}>
        {body}
      </motion.button>
    )
  }
  return <div className={cn(cls, 'hover:bg-transparent')}>{body}</div>
}
