'use client'

import { lazy, Suspense, useEffect, useId, useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { BookUser, ChevronRight, GalleryHorizontalEnd, Settings, Sparkles, SquarePen, UserPen } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { MAX_HIGHLIGHTS } from '@/lib/backend'
import { errorCode } from '@/lib/format'
import { handleSpelling, namePlain } from '@/lib/handle'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { HandleField, handleUsable, useHandleCheck } from '@/components/looks/handle-field'
import { lookKey } from '@/components/looks/look-name'
import { useLooksT } from '@/components/looks/looks-text'
import { Field, TextArea, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { ContactsEditor } from '@/components/social/contacts'
import { Gallery } from '@/components/social/gallery'
import { HighlightEditor } from '@/components/social/highlights'
import { useSocialT } from '@/components/social/social-text'
import { VerifyCard } from '@/components/trust/trust-cards'
import { ProfileContent } from '@/components/screens/profile/profile-sheet'
import { PhotoPicker } from './photo-picker'

const MAX_NAME = 30
const MAX_BIO = 150

// The composer and the profile details download when first opened (the Looks shop is the shell's, openShop).
const loadComposer = () => retryImport(() => import('@/components/social/posts/post-composer'))
const PostComposer = lazy(() => loadComposer().then((m) => ({ default: m.PostComposer })))
const loadStyle = () => retryImport(() => import('@/components/social/style-editor'))
const StyleEditor = lazy(() => loadStyle().then((m) => ({ default: m.StyleEditor })))

/**
 * My profile exactly as others see it (ProfileContent): only Edit profile (and Settings on phones) on it. Everything
 * else — a new post, a new highlight, the Looks shop, details, contacts — is in Edit profile (the owner).
 */
export function MeScreen() {
  const { me, t, setTab, trust, openShop } = useApp()
  const [editOpen, setEditOpen] = useState(false)
  const [postOpen, setPostOpen] = useState(false)
  const [styleOpen, setStyleOpen] = useState(false)
  const [used, setUsed] = useState({ post: false, style: false })
  const [contactsOpen, setContactsOpen] = useState(false)
  const [highlightOpen, setHighlightOpen] = useState(false)
  const openPost = () => {
    setUsed((u) => ({ ...u, post: true }))
    setPostOpen(true)
  }
  const openStyle = () => {
    setUsed((u) => ({ ...u, style: true }))
    setStyleOpen(true)
  }
  const from = (open: () => void) => () => {
    setEditOpen(false)
    open()
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto max-w-xl pt-3 pb-10 sm:pt-8">
        <ProfileContent
          profile={me}
          trust={trust}
          slot={
            <div className="absolute top-3 inset-e-3 z-10 flex items-center gap-2">
              <Button size="sm" variant="outline" className="bg-card shadow-e1" onClick={() => setEditOpen(true)} onPointerEnter={() => void loadComposer()}>
                <UserPen className="size-4" aria-hidden /> {t('me.edit')}
              </Button>
              <Button size="icon-sm" variant="ghost" className="md:hidden" onClick={() => setTab('settings')} aria-label={t('nav.settings')}>
                <Settings className="size-5" aria-hidden />
              </Button>
            </div>
          }
        >
          <VerifyCard className="mx-4 mt-6 w-[calc(100%-2rem)]" />
        </ProfileContent>
      </div>
      <EditProfile
        open={editOpen}
        onClose={() => setEditOpen(false)}
        onPost={from(openPost)}
        onHighlight={from(() => setHighlightOpen(true))}
        onLooks={from(openShop)}
        onDetails={from(openStyle)}
        onContacts={from(() => setContactsOpen(true))}
      />
      <ContactsEditor open={contactsOpen} onClose={() => setContactsOpen(false)} />
      {highlightOpen ? <HighlightEditor userId={me.id} highlight={null} onClose={() => setHighlightOpen(false)} /> : null}
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
    </div>
  )
}

/** Everything about my profile: the photo, name, @ID and bio here; photos too; the rest one tap on. */
function EditProfile({ open, onClose, onPost, onHighlight, onLooks, onDetails, onContacts }: {
  open: boolean; onClose: () => void; onPost: () => void; onHighlight: () => void; onLooks: () => void; onDetails: () => void
  onContacts: () => void
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
  const [bio, setBio] = useState(me.bio)
  const [photo, setPhoto] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const handleState = useHandleCheck(handle, me.handle)
  // When the ID can change to another one again (the app's free one can change at once).
  const { data: shop } = useSWR(open ? 'shop' : null, () => backend.shop())
  // The same list as my profile's highlights (one request serves both): a new one only below the limit.
  const { data: highlights } = useSWR(['highlights', me.id], () => backend.highlights(me.id))
  const next = shop?.handleNext && shop.handleNext > Date.now() ? shop.handleNext : null

  // Filled in when the sheet opens only: adding a photo below changes `me` and must not undo typing.
  useEffect(() => {
    if (!open) return
    setName(me.name)
    setHandle(me.handle ?? '')
    setBio(me.bio)
    setPhoto(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

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
      let p = await backend.updateProfile(name.trim(), '', bio.trim(), photo)
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
        <Field label={t('pf.bio')} htmlFor={`${id}-bio`} trailing={<Count n={bio.length} max={MAX_BIO} />}>
          <TextArea id={`${id}-bio`} value={bio} maxLength={MAX_BIO} onChange={(e) => setBio(e.target.value)} />
        </Field>
        <Gallery photos={me.photos ?? []} owner={me.name} />
        <div className="-mx-2 flex flex-col">
          <MoreRow icon={<SquarePen />} label={t('me.newPost')} onClick={onPost} />
          {(highlights?.length ?? 0) < MAX_HIGHLIGHTS ? <MoreRow icon={<GalleryHorizontalEnd />} label={st('hl.create')} onClick={onHighlight} /> : null}
          <MoreRow icon={<Sparkles className="text-primary-text" />} label={lt('looks.title')} onClick={onLooks} />
          <MoreRow icon={<UserPen />} label={lt('details.title')} note={lt('details.desc')} onClick={onDetails} />
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
