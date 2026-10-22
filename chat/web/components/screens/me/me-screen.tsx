'use client'

import { lazy, Suspense, useEffect, useId, useState } from 'react'
import useSWR from 'swr'
import { Palette, Plus, Settings } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { EMPTY_STYLE } from '@/lib/profile-style'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Field, TextArea, TextInput } from '@/components/ui/bits'
import { IcebreakerIcon } from '@/components/ui/topic-icons'
import { useFeedT } from '@/components/social/feed-text'
import { ProfileHero, PromptCards } from '@/components/social/profile-bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { InterestChips } from '@/components/social/bits'
import { Gallery } from '@/components/social/gallery'
import { InterestsSheet } from '@/components/social/interests-sheet'
import { useSocialT } from '@/components/social/social-text'
import { IdentityFacts, MannerCard, VerifyCard } from '@/components/trust/trust-cards'
import { PhotoPicker } from './photo-picker'
import { PostGrid } from './post-grid'

const MAX_NAME = 30
const MAX_CITY = 30
const MAX_BIO = 150

// The composer and the style editor download when first opened.
const loadComposer = () => retryImport(() => import('@/components/social/posts/post-composer'))
const PostComposer = lazy(() => loadComposer().then((m) => ({ default: m.PostComposer })))
const loadStyle = () => retryImport(() => import('@/components/social/style-editor'))
const StyleEditor = lazy(() => loadStyle().then((m) => ({ default: m.StyleEditor })))

export function MeScreen() {
  const backend = useBackend()
  const { me, t, setTab, entitlements, trust } = useApp()
  const verified = !!trust?.verified
  const { data: friends } = useSWR('friends', () => backend.friends())
  const [editOpen, setEditOpen] = useState(false)
  const [postOpen, setPostOpen] = useState(false)
  const [styleOpen, setStyleOpen] = useState(false)
  const [used, setUsed] = useState({ post: false, style: false })
  const [interestsOpen, setInterestsOpen] = useState(false)
  const { st } = useSocialT()
  const { ft } = useFeedT()
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
          coverSlot={
            <div className="absolute top-3 inset-e-3 flex items-center gap-2">
              <Button
                size="sm"
                className="bg-white/90 text-[#1f1f1f] shadow-e1 backdrop-blur hover:bg-white"
                onClick={openStyle}
                onPointerEnter={() => void loadStyle()}
              >
                <Palette className="size-4" aria-hidden /> {ft('style.button')}
              </Button>
              <Button
                size="icon-sm"
                variant="glass"
                className="bg-black/25 text-white ring-white/25 backdrop-blur md:hidden"
                onClick={() => setTab('settings')}
                aria-label={t('nav.settings')}
              >
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
      <EditProfile open={editOpen} onClose={() => setEditOpen(false)} />
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

function EditProfile({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, setMe } = useApp()
  const { t } = useI18n()
  const id = useId()
  const [name, setName] = useState(me.name)
  const [city, setCity] = useState(me.city)
  const [bio, setBio] = useState(me.bio)
  const [photo, setPhoto] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(me.name)
    setCity(me.city)
    setBio(me.bio)
    setPhoto(null)
  }, [open, me])

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    try {
      setMe(await backend.updateProfile(name.trim(), city.trim(), bio.trim(), photo))
      toast.show(t('me.updated'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('me.edit')} description={t('me.editDesc')}>
      <form onSubmit={(e) => void save(e)} className="flex flex-col gap-4 p-6 pt-5">
        <PhotoPicker file={photo} onChange={setPhoto} name={name} currentUrl={me.photoUrl} />
        <Field label={t('pf.name')} htmlFor={`${id}-name`} trailing={<Count n={name.length} max={MAX_NAME} />}>
          <TextInput id={`${id}-name`} value={name} maxLength={MAX_NAME} required onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label={t('me.city')} htmlFor={`${id}-city`}>
          <TextInput id={`${id}-city`} value={city} maxLength={MAX_CITY} onChange={(e) => setCity(e.target.value)} />
        </Field>
        <Field label={t('pf.bio')} htmlFor={`${id}-bio`} trailing={<Count n={bio.length} max={MAX_BIO} />}>
          <TextArea id={`${id}-bio`} value={bio} maxLength={MAX_BIO} onChange={(e) => setBio(e.target.value)} />
        </Field>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!name.trim()}>
          {t('common.save')}
        </Button>
      </form>
    </Modal>
  )
}

function Count({ n, max }: { n: number; max: number }) {
  return <span className={`text-xs tabular-nums ${n >= max ? 'text-destructive' : 'text-muted-foreground'}`}>{n}/{max}</span>
}
