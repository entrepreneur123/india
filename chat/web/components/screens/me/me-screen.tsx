'use client'

import { lazy, Suspense, useEffect, useId, useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { BookUser, ChevronRight, Plus, Settings, Sparkles, UserPen } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import { handleSpelling, namePlain } from '@/lib/handle'
import { EMPTY_STYLE } from '@/lib/profile-style'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { HandleField, handleUsable, useHandleCheck } from '@/components/looks/handle-field'
import { lookKey } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { Field, TextArea, TextInput } from '@/components/ui/bits'
import { IcebreakerIcon } from '@/components/ui/topic-icons'
import { useFeedT } from '@/components/social/feed-text'
import { ProfileHero, PromptCards } from '@/components/social/profile-bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { InterestChips } from '@/components/social/bits'
import { ContactsEditor } from '@/components/social/contacts'
import { Gallery } from '@/components/social/gallery'
import { InterestsSheet } from '@/components/social/interests-sheet'
import { useSocialT } from '@/components/social/social-text'
import { IdentityFacts, MannerCard, VerifyCard } from '@/components/trust/trust-cards'
import { PhotoPicker } from './photo-picker'
import { PostGrid } from './post-grid'

const MAX_NAME = 30
const MAX_CITY = 30
const MAX_BIO = 150

// The composer, the profile details and the Looks shop download when first opened.
const loadComposer = () => retryImport(() => import('@/components/social/posts/post-composer'))
const PostComposer = lazy(() => loadComposer().then((m) => ({ default: m.PostComposer })))
const loadStyle = () => retryImport(() => import('@/components/social/style-editor'))
const StyleEditor = lazy(() => loadStyle().then((m) => ({ default: m.StyleEditor })))
const loadLooks = () => retryImport(() => import('@/components/looks/looks-sheet'))
const LooksSheet = lazy(() => loadLooks().then((m) => ({ default: m.LooksSheet })))

export function MeScreen() {
  const backend = useBackend()
  const { me, t, setTab, entitlements, trust } = useApp()
  const verified = !!trust?.verified
  const { data: friends } = useSWR('friends', () => backend.friends())
  const [editOpen, setEditOpen] = useState(false)
  const [postOpen, setPostOpen] = useState(false)
  const [styleOpen, setStyleOpen] = useState(false)
  const [looksOpen, setLooksOpen] = useState(false)
  const [used, setUsed] = useState({ post: false, style: false, looks: false })
  const [interestsOpen, setInterestsOpen] = useState(false)
  const [contactsOpen, setContactsOpen] = useState(false)
  const { st } = useSocialT()
  const { ft } = useFeedT()
  const { lt } = useLooksT()
  const friendCount = friends?.filter((f) => f.state === 'friends').length
  const style = me.style ?? EMPTY_STYLE
  const likes = me.posts.reduce((n, p) => n + (p.likes ?? 0), 0)
  const openPost = () => {
    setUsed((u) => ({ ...u, post: true }))
    setPostOpen(true)
  }
  const openStyle = () => {
    setUsed((u) => ({ ...u, style: true }))
    setStyleOpen(true)
  }
  const openLooks = () => {
    setUsed((u) => ({ ...u, looks: true }))
    setLooksOpen(true)
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto max-w-3xl px-3 pt-3 pb-10 sm:px-8 sm:pt-8">
        <ProfileHero
          profile={me}
          verified={verified}
          facts={verified ? <IdentityFacts profile={me} verified /> : null}
          badge={
            entitlements.premium ? (
              <span className="rounded-md bg-primary-soft px-2 py-0.5 text-[11px] font-semibold text-primary-text">{t('me.plusBadge')}</span>
            ) : null
          }
          slot={
            <div className="absolute top-3 inset-e-3 z-10 flex items-center gap-2">
              <Button size="sm" variant="outline" className="bg-card shadow-e1" onClick={openLooks} onPointerEnter={() => void loadLooks()}>
                <Sparkles className="size-4 text-primary-text" aria-hidden /> {lt('looks.title')}
              </Button>
              <Button size="icon-sm" variant="ghost" className="md:hidden" onClick={() => setTab('settings')} aria-label={t('nav.settings')}>
                <Settings className="size-5" aria-hidden />
              </Button>
            </div>
          }
          statusSlot={
            <button
              type="button"
              onClick={openStyle}
              className="mt-3 rounded-full border border-dashed border-input px-4 py-1.5 text-sm font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
            >
              {ft('profile.addStatus')}
            </button>
          }
        />

        <dl className="mx-auto mt-5 grid max-w-sm grid-cols-3 text-center">
          <Stat label={t('me.posts')} value={me.posts.length} />
          <Stat label={t('me.friends')} value={friendCount} />
          <Stat label={ft('profile.likes')} value={likes} />
        </dl>

        <VerifyCard className="mx-auto mt-5 max-w-xl" />

        <div className="mx-auto mt-5 flex max-w-md gap-2">
          <Button variant="outline" className="flex-1" onClick={() => setEditOpen(true)}>
            {t('me.edit')}
          </Button>
          <Button variant="brand" className="flex-1" onClick={openPost} onPointerEnter={() => void loadComposer()}>
            <Plus className="size-4.5" aria-hidden /> {t('me.newPost')}
          </Button>
        </div>

        <section className="mt-8 flex flex-col gap-6">
          <MannerCard trust={trust} className="mx-auto w-full max-w-xl" />
          {style.prompts.length ? (
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold">{ft('style.prompts')}</h2>
                <Button size="sm" variant="ghost" className="-me-3" onClick={openStyle}>
                  {ft('style.promptsEdit')}
                </Button>
              </div>
              <PromptCards prompts={style.prompts} accent={style.accent} className="sm:grid-cols-2" />
            </div>
          ) : (
            <button
              type="button"
              onClick={openStyle}
              className="flex items-center gap-4 rounded-3xl border border-dashed border-input p-4 text-start transition-colors duration-150 hover:bg-muted"
            >
              <IcebreakerIcon size={36} />
              <span className="min-w-0">
                <span className="block text-[15px] font-bold">{ft('style.promptAdd')}</span>
                <span className="block text-sm text-muted-foreground">{ft('profile.promptsEmpty')}</span>
              </span>
            </button>
          )}
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold">{st('interests.label')}</h2>
              {me.interests?.length ? (
                <Button size="sm" variant="ghost" className="-me-3" onClick={() => setInterestsOpen(true)}>
                  {st('interests.edit')}
                </Button>
              ) : null}
            </div>
            {me.interests?.length ? (
              <InterestChips tags={me.interests} />
            ) : (
              <button
                type="button"
                onClick={() => setInterestsOpen(true)}
                className="rounded-2xl border border-dashed border-input px-4 py-3 text-start text-sm font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
              >
                {st('interests.add')}
              </button>
            )}
          </div>
          <Gallery photos={me.photos ?? []} owner={me.name} editable />
        </section>

        <section className="mt-8 flex flex-col gap-3 border-t border-border pt-6">
          <h2 className="text-sm font-semibold">{ft('profile.posts')}</h2>
          <PostGrid
            posts={me.posts}
            owner={me.name}
            accent={style.accent}
            emptyAction={<Button variant="brand" onClick={openPost}>{t('me.firstPost')}</Button>}
          />
        </section>
      </div>
      <EditProfile
        open={editOpen}
        onClose={() => setEditOpen(false)}
        onLooks={() => {
          setEditOpen(false)
          openLooks()
        }}
        onDetails={() => {
          setEditOpen(false)
          openStyle()
        }}
        onContacts={() => {
          setEditOpen(false)
          setContactsOpen(true)
        }}
      />
      <ContactsEditor open={contactsOpen} onClose={() => setContactsOpen(false)} />
      {used.post ? (
        <Suspense fallback={null}>
          <PostComposer open={postOpen} onClose={() => setPostOpen(false)} />
        </Suspense>
      ) : null}
      {used.style ? (
        <Suspense fallback={null}>
          <StyleEditor open={styleOpen} onClose={() => setStyleOpen(false)} />
        </Suspense>
      ) : null}
      {used.looks ? (
        <Suspense fallback={null}>
          <LooksSheet open={looksOpen} onClose={() => setLooksOpen(false)} />
        </Suspense>
      ) : null}
      <InterestsSheet open={interestsOpen} onClose={() => setInterestsOpen(false)} />
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number | undefined }) {
  const { num } = useI18n()
  return (
    <div className="flex flex-col-reverse">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-xl font-bold tabular-nums">{value === undefined ? '–' : num(value)}</dd>
    </div>
  )
}

function EditProfile({ open, onClose, onLooks, onDetails, onContacts }: {
  open: boolean; onClose: () => void; onLooks: () => void; onDetails: () => void; onContacts: () => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { me, setMe } = useApp()
  const { t, locale } = useI18n()
  const { lt } = useLooksT()
  const { st } = useSocialT()
  const id = useId()
  const [name, setName] = useState(me.name)
  const [handle, setHandle] = useState(me.handle ?? '')
  const [city, setCity] = useState(me.city)
  const [bio, setBio] = useState(me.bio)
  const [photo, setPhoto] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const handleState = useHandleCheck(handle, me.handle)
  // When the ID can change to another one again (the app's free one can change at once).
  const { data: shop } = useSWR(open ? 'shop' : null, () => backend.shop())
  const next = shop?.handleNext && shop.handleNext > Date.now() ? shop.handleNext : null

  useEffect(() => {
    if (!open) return
    setName(me.name)
    setHandle(me.handle ?? '')
    setCity(me.city)
    setBio(me.bio)
    setPhoto(null)
  }, [open, me])

  const plainName = namePlain(name)
  const wanted = handleSpelling(handle)
  const handleChanged = !!wanted && wanted !== me.handle
  // Another ID inside the 30 days needs a paid ID change (a new spelling of the same one is free).
  const passes = shop?.handlePasses ?? 0
  const waiting = !!next && handleChanged && wanted.toLowerCase() !== me.handle?.toLowerCase() && passes < 1
  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim() || !plainName || waiting || (handleChanged && handleState !== 'checking' && !handleUsable(handleState))) return
    setBusy(true)
    try {
      let p = await backend.updateProfile(name.trim(), city.trim(), bio.trim(), photo)
      setMe(p)
      if (handleChanged) {
        p = { ...p, handle: await backend.setHandle(wanted) }
        setMe(p)
        void mutate(lookKey(me.id))
        void mutate('shop')
      }
      toast.show(t('me.updated'), 'success')
      onClose()
    } catch (err) {
      // The server's word on the ID (taken meanwhile, too soon) shows under the field too.
      if (errorCode(err).startsWith('handle_')) void mutate('shop')
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('me.edit')} description={t('me.editDesc')}>
      <form onSubmit={(e) => void save(e)} className="flex flex-col gap-4 p-6 pt-5">
        <PhotoPicker file={photo} onChange={setPhoto} name={name} currentUrl={me.photoUrl} />
        <Field
          label={t('pf.name')}
          htmlFor={`${id}-name`}
          trailing={<Count n={name.length} max={MAX_NAME} />}
          error={plainName ? null : t('error.name_invalid')}
        >
          <TextInput id={`${id}-name`} value={name} maxLength={MAX_NAME} required onChange={(e) => setName(e.target.value)} />
        </Field>
        <HandleField
          id={`${id}-handle`}
          value={handle}
          onChange={setHandle}
          state={handleChanged ? handleState : null}
          note={next ? lt(passes > 0 ? 'id.cooldownPass' : 'id.cooldown', { date: new Date(next).toLocaleDateString(locale, { day: 'numeric', month: 'long' }), n: passes }) : undefined}
          blocked={waiting}
        />
        <Field label={t('me.city')} htmlFor={`${id}-city`}>
          <TextInput id={`${id}-city`} value={city} maxLength={MAX_CITY} onChange={(e) => setCity(e.target.value)} />
        </Field>
        <Field label={t('pf.bio')} htmlFor={`${id}-bio`} trailing={<Count n={bio.length} max={MAX_BIO} />}>
          <TextArea id={`${id}-bio`} value={bio} maxLength={MAX_BIO} onChange={(e) => setBio(e.target.value)} />
        </Field>
        <div className="-mx-2 flex flex-col">
          <MoreRow icon={<Sparkles className="text-primary-text" />} label={lt('looks.title')} onClick={onLooks} />
          <MoreRow icon={<UserPen />} label={lt('details.title')} onClick={onDetails} />
          <MoreRow icon={<BookUser />} label={st('contacts.title')} note={st('contacts.note')} onClick={onContacts} />
        </div>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!name.trim() || !plainName || waiting}>
          {t('common.save')}
        </Button>
      </form>
    </Modal>
  )
}

/** A way on from Edit profile (to the Looks shop, the profile details, my contacts). */
function MoreRow({ icon, label, note, onClick }: { icon: React.ReactNode; label: string; note?: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-12 items-center gap-3 rounded-xl px-2 py-1.5 text-start text-[15px] font-semibold transition-colors duration-150 hover:bg-muted"
    >
      <span className="shrink-0 text-muted-foreground [&>svg]:size-5" aria-hidden>
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate">{label}</span>
        {note ? <span className="block text-xs font-normal text-muted-foreground">{note}</span> : null}
      </span>
      <ChevronRight className="size-4.5 shrink-0 text-muted-foreground rtl:rotate-180" aria-hidden />
    </button>
  )
}

function Count({ n, max }: { n: number; max: number }) {
  return <span className={`text-xs tabular-nums ${n >= max ? 'text-destructive' : 'text-muted-foreground'}`}>{n}/{max}</span>
}
