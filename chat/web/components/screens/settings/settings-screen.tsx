'use client'

import { useState } from 'react'
import useSWR from 'swr'
import { Ban, Baby, Check, ChevronRight, ExternalLink, FileText, Languages, LogOut, Mail, Phone, ScrollText, ShieldCheck, Sparkles, Trash2 } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { CHILD_SAFETY_URL, COMMUNITY_RULES, GRIEVANCE_OFFICER, PRIVACY_URL, SUPPORT_EMAIL, TERMS_URL } from '@/lib/config'
import { LANGS } from '@/lib/i18n'
import { TARGET_NAMES, TARGETS, type Target } from '@/lib/translate'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { useTranslate } from '@/components/translate'
import { useLooksT } from '@/components/looks/looks-text'
import { BlockedSheet } from '@/components/social/people-find'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

export function SettingsScreen() {
  const backend = useBackend()
  const toast = useToast()
  const { t, lang, setLang } = useI18n()
  const { entitlements, openPaywall, signedOut, phone } = useApp()
  const { data: myPhone } = useSWR('my-phone', () => backend.myPhone())
  const [rulesOpen, setRulesOpen] = useState(false)
  const [langOpen, setLangOpen] = useState(false)
  const [targetOpen, setTargetOpen] = useState(false)
  const tr = useTranslate()
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [blocksOpen, setBlocksOpen] = useState(false)
  const { lt } = useLooksT()
  const [busy, setBusy] = useState<'out' | 'delete' | null>(null)

  const signOut = async () => {
    setBusy('out')
    try {
      await backend.cancelSearch().catch(() => undefined) // out of the match queue while still signed in
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
      toast.show(t('settings.deleted'), 'success')
      signedOut()
    } catch (err) {
      toast.error(err)
      setBusy(null)
    }
  }

  const plan = entitlements.premium ? t('common.plus') : entitlements.genderPass ? t('settings.planPass') : t('settings.planFree')
  const current = LANGS.find((l) => l.id === lang)

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto flex max-w-2xl flex-col gap-8 px-4 py-6 sm:px-8 sm:py-10">
        <h1 className="text-[28px] leading-tight font-bold tracking-tight">{t('settings.title')}</h1>

        <Group title={t('settings.account')}>
          <Item icon={Phone} label={t('settings.phone')} value={myPhone ?? phone ?? t('settings.verified')} ltrValue />
          <Item icon={Sparkles} label={t('settings.plan')} value={plan} onClick={entitlements.premium ? undefined : openPaywall} accent={!entitlements.premium} />
          <Item icon={Languages} label={t('settings.language')} value={current?.native} onClick={() => setLangOpen(true)} />
        </Group>

        {tr?.available ? (
          <Group title={t('settings.translation')}>
            <Toggle
              icon={Languages}
              label={t('settings.autoTranslate')}
              body={t('settings.autoTranslateBody')}
              on={tr.autoSetting}
              onChange={tr.setAuto}
            />
            <Item
              icon={Languages}
              label={t('settings.translateTo')}
              value={tr.targetSetting ? TARGET_NAMES[tr.targetSetting] : t('settings.translateToApp', { name: TARGET_NAMES[tr.appTarget] })}
              onClick={() => setTargetOpen(true)}
            />
          </Group>
        ) : null}

        <Group title={t('settings.safety')}>
          <Item icon={Ban} label={lt('blocks.title')} onClick={() => setBlocksOpen(true)} />
          <Item icon={ScrollText} label={t('settings.rules')} onClick={() => setRulesOpen(true)} />
          <Item icon={ShieldCheck} label={t('settings.privacy')} href={PRIVACY_URL} />
          <Item icon={FileText} label={t('settings.terms')} href={TERMS_URL} />
          <Item icon={Baby} label={t('settings.childSafety')} href={CHILD_SAFETY_URL} />
          <Item icon={Mail} label={t('settings.grievance')} value={GRIEVANCE_OFFICER.email} href={`mailto:${GRIEVANCE_OFFICER.email}`} ltrValue />
          <Item icon={Mail} label={t('settings.support')} value={SUPPORT_EMAIL} href={`mailto:${SUPPORT_EMAIL}`} ltrValue />
        </Group>

        <Group>
          <Item icon={LogOut} label={t('settings.signOut')} onClick={() => void signOut()} busy={busy === 'out'} />
          <Item icon={Trash2} label={t('settings.delete')} onClick={() => setDeleteOpen(true)} danger />
        </Group>
        <p className="px-2 text-center text-xs leading-relaxed text-muted-foreground">{t('settings.footer')}</p>
      </div>

      <Modal open={langOpen} onClose={() => setLangOpen(false)} variant="sheet" title={t('settings.chooseLanguage')}>
        <ul className="flex flex-col p-3 pb-5" role="radiogroup" aria-label={t('settings.language')}>
          {LANGS.map((l) => {
            const on = l.id === lang
            return (
              <li key={l.id}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={on}
                  data-autofocus={on ? true : undefined}
                  onClick={() => {
                    setLang(l.id)
                    setLangOpen(false)
                  }}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-xl px-4 py-3 text-start transition-colors duration-150 hover:bg-muted',
                    on && 'bg-primary-soft hover:bg-primary-soft',
                  )}
                >
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span lang={l.id} className={cn('text-[15px] font-semibold', on && 'text-primary-text')}>
                      {l.native}
                    </span>
                    <span className="text-xs text-muted-foreground">{l.english}</span>
                  </span>
                  {on ? <Check className="size-5 shrink-0 text-primary-text" aria-hidden /> : null}
                </button>
              </li>
            )
          })}
        </ul>
      </Modal>

      {tr ? (
        <Modal open={targetOpen} onClose={() => setTargetOpen(false)} variant="sheet" title={t('settings.chooseTarget')}>
          <ul className="flex flex-col p-3 pb-5" role="radiogroup" aria-label={t('settings.translateTo')}>
            {([null, ...TARGETS] as (Target | null)[]).map((id) => {
              const on = id === tr.targetSetting
              return (
                <li key={id ?? 'app'}>
                  <button
                    type="button"
                    role="radio"
                    aria-checked={on}
                    data-autofocus={on ? true : undefined}
                    onClick={() => {
                      tr.setTarget(id)
                      setTargetOpen(false)
                    }}
                    className={cn(
                      'flex w-full items-center gap-3 rounded-xl px-4 py-3 text-start transition-colors duration-150 hover:bg-muted',
                      on && 'bg-primary-soft hover:bg-primary-soft',
                    )}
                  >
                    <span lang={id ?? undefined} className={cn('min-w-0 flex-1 text-[15px] font-semibold', on && 'text-primary-text')} style={{ fontFamily: 'system-ui, sans-serif' }}>
                      {id ? TARGET_NAMES[id] : t('settings.translateToApp', { name: TARGET_NAMES[tr.appTarget] })}
                    </span>
                    {on ? <Check className="size-5 shrink-0 text-primary-text" aria-hidden /> : null}
                  </button>
                </li>
              )
            })}
          </ul>
        </Modal>
      ) : null}

      <BlockedSheet open={blocksOpen} onClose={() => setBlocksOpen(false)} />

      <Modal open={rulesOpen} onClose={() => setRulesOpen(false)} title={t('settings.rules')}>
        <ol className="flex flex-col gap-3 p-6 pt-4">
          {COMMUNITY_RULES.map((rule, i) => (
            <li key={rule} className="flex gap-3 text-[15px] leading-relaxed">
              <span className="grid size-6 shrink-0 place-items-center rounded-full bg-primary-soft text-xs font-semibold text-primary-text">{i + 1}</span>
              {t(rule)}
            </li>
          ))}
        </ol>
      </Modal>

      <Modal
        open={deleteOpen}
        onClose={() => busy !== 'delete' && setDeleteOpen(false)}
        title={t('settings.deleteTitle')}
        description={t('settings.deleteBody')}
        dismissible={busy !== 'delete'}
      >
        <div className="flex gap-3 p-6 pt-5">
          <Button variant="outline" className="flex-1" disabled={busy === 'delete'} onClick={() => setDeleteOpen(false)}>
            {t('settings.keep')}
          </Button>
          <Button variant="danger" className="flex-1" loading={busy === 'delete'} onClick={() => void deleteAccount()}>
            {t('settings.deleteForever')}
          </Button>
        </div>
      </Modal>
    </div>
  )
}

function Group({ title, children }: { title?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      {title ? <h2 className="px-4 text-sm font-semibold text-primary-text">{title}</h2> : null}
      <div className="flex flex-col divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">{children}</div>
    </section>
  )
}

function Icon({ icon: I, danger }: { icon: typeof Phone; danger?: boolean }) {
  return (
    <span className={cn('grid size-9 shrink-0 place-items-center', danger ? 'text-destructive' : 'text-muted-foreground')}>
      <I className="size-5" aria-hidden />
    </span>
  )
}

/** An on/off setting (a switch), with a line of explanation under its label. */
function Toggle({ icon, label, body, on, onChange }: { icon: typeof Phone; label: string; body?: string; on: boolean; onChange: (on: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      onClick={() => onChange(!on)}
      className="flex w-full items-center gap-3 px-3 py-3 text-start transition-colors duration-150 hover:bg-muted"
    >
      <Icon icon={icon} />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="text-[15px] font-medium">{label}</span>
        {body ? <span className="mt-0.5 text-xs leading-relaxed text-muted-foreground">{body}</span> : null}
      </span>
      <span
        aria-hidden
        className={cn('relative h-6 w-10 shrink-0 rounded-full transition-colors duration-150', on ? 'bg-primary' : 'bg-input')}
      >
        <span
          className={cn(
            'absolute top-0.5 size-5 rounded-full bg-white shadow transition-[inset-inline-start] duration-150',
            on ? 'inset-s-[1.125rem]' : 'inset-s-0.5',
          )}
        />
      </span>
    </button>
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
  ltrValue,
}: {
  icon: typeof Phone
  label: string
  value?: string
  onClick?: () => void
  href?: string
  danger?: boolean
  accent?: boolean
  busy?: boolean
  /** Phone numbers and e-mail addresses always read left to right. */
  ltrValue?: boolean
}) {
  const body = (
    <>
      <Icon icon={icon} danger={danger} />
      <span className={cn('min-w-0 flex-1 text-[15px] font-medium', danger && 'text-destructive')}>{label}</span>
      {value ? (
        <span dir={ltrValue ? 'ltr' : undefined} className={cn('max-w-[50%] truncate text-sm', accent ? 'font-semibold text-primary-text' : 'text-muted-foreground')}>
          {value}
        </span>
      ) : null}
      {href && !href.startsWith('mailto:') ? <ExternalLink className="size-4 shrink-0 text-muted-foreground" aria-hidden /> : null}
      {onClick ? <ChevronRight className="size-4 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden /> : null}
    </>
  )
  const cls = 'flex w-full items-center gap-3 px-3 py-3 text-start transition-colors duration-150 hover:bg-muted disabled:opacity-60'
  if (href) {
    return (
      <a href={href} target={href.startsWith('mailto:') ? undefined : '_blank'} rel="noreferrer" className={cls}>
        {body}
      </a>
    )
  }
  if (onClick) {
    return (
      <button type="button" onClick={onClick} disabled={busy} className={cls}>
        {body}
      </button>
    )
  }
  return <div className={cn(cls, 'hover:bg-transparent')}>{body}</div>
}
