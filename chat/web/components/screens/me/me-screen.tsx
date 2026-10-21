'use client'

import { useEffect, useId, useState } from 'react'
import useSWR from 'swr'
import { ImagePlus, Plus, Settings, X } from 'lucide-react'
import { useBackend } from '@/lib/backend-context'
import { flagEmoji } from '@/lib/format'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Field, TextArea, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { InterestChips } from '@/components/social/bits'
import { Gallery } from '@/components/social/gallery'
import { InterestsSheet } from '@/components/social/interests-sheet'
import { useSocialT } from '@/components/social/social-text'
import { PhotoPicker } from './photo-picker'
import { PostGrid } from './post-grid'

const MAX_NAME = 30
const MAX_CITY = 30
const MAX_BIO = 150
const MAX_POST = 500

export function MeScreen() {
  const backend = useBackend()
  const { me, t, setTab, entitlements } = useApp()
  const { data: friends } = useSWR('friends', () => backend.friends())
  const [editOpen, setEditOpen] = useState(false)
  const [postOpen, setPostOpen] = useState(false)
  const [interestsOpen, setInterestsOpen] = useState(false)
  const { st } = useSocialT()
  const friendCount = friends?.filter((f) => f.state === 'friends').length

  return (
    <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
      <div className="mx-auto max-w-3xl pb-10">
        <div className="flex items-center justify-between px-4 pt-6 sm:px-8 sm:pt-10">
          <h1 className="truncate text-xl font-bold tracking-tight">{me.name}</h1>
          <Button size="icon" variant="secondary" className="md:hidden" onClick={() => setTab('settings')} aria-label={t('nav.settings')}>
            <Settings className="size-5" aria-hidden />
          </Button>
        </div>

        <section className="flex flex-col gap-5 px-4 pt-6 sm:px-8">
          <div className="flex items-center gap-6 sm:gap-10">
            <Avatar name={me.name} src={me.photoUrl} size={96} />
            <dl className="flex flex-1 justify-around text-center sm:justify-start sm:gap-12">
              <Stat label={t('me.posts')} value={me.posts.length} />
              <Stat label={t('me.friends')} value={friendCount} />
            </dl>
          </div>
          <div>
            <p className="flex flex-wrap items-center gap-2 text-[15px] font-bold">
              {me.name}
              {entitlements.premium ? (
                <span className="rounded-md bg-primary-soft px-2 py-0.5 text-[11px] font-semibold text-primary-text">{t('me.plusBadge')}</span>
              ) : null}
            </p>
            <p className="text-sm text-muted-foreground">
              {me.age} · <span aria-hidden>{flagEmoji(me.country)}</span> {me.city}
            </p>
            {me.bio ? <p className="mt-2 max-w-md text-[15px] leading-relaxed text-pretty">{me.bio}</p> : null}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" className="flex-1 sm:flex-none sm:px-8" onClick={() => setEditOpen(true)}>
              {t('me.edit')}
            </Button>
            <Button variant="brand" className="flex-1 sm:flex-none sm:px-8" onClick={() => setPostOpen(true)}>
              <Plus className="size-4.5" aria-hidden /> {t('me.newPost')}
            </Button>
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold">{st('interests.label')}</h2>
              {me.interests?.length ? (
                <Button size="sm" variant="ghost" className="-mr-3" onClick={() => setInterestsOpen(true)}>
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
                className="rounded-2xl border border-dashed border-input px-4 py-3 text-left text-sm font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground"
              >
                {st('interests.add')}
              </button>
            )}
          </div>
          <Gallery photos={me.photos ?? []} owner={me.name} editable />
        </section>

        <div className="mt-8 border-t border-border sm:mx-8">
          <PostGrid
            posts={me.posts}
            owner={me.name}
            emptyAction={<Button variant="brand" onClick={() => setPostOpen(true)}>{t('me.firstPost')}</Button>}
          />
        </div>
      </div>
      <EditProfile open={editOpen} onClose={() => setEditOpen(false)} />
      <NewPost open={postOpen} onClose={() => setPostOpen(false)} />
      <InterestsSheet open={interestsOpen} onClose={() => setInterestsOpen(false)} />
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number | undefined }) {
  return (
    <div className="flex flex-col-reverse">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-xl font-semibold tabular-nums">{value ?? '–'}</dd>
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

function NewPost({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, setMe } = useApp()
  const { t } = useI18n()
  const id = useId()
  const [text, setText] = useState('')
  const [image, setImage] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const canPost = !busy && (text.trim().length > 0 || image !== null)

  useEffect(() => {
    if (!image) return setPreview(null)
    const url = URL.createObjectURL(image)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [image])

  useEffect(() => {
    if (open) return
    setText('')
    setImage(null)
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!canPost) return
    setBusy(true)
    try {
      const post = await backend.addPost(text.trim(), image)
      setMe({ ...me, posts: [post, ...me.posts] })
      toast.show(t('me.posted'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('me.newPost')} description={t('me.postDesc')}>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 p-6 pt-5">
        {preview ? (
          <div className="relative overflow-hidden rounded-2xl bg-muted">
            <img src={preview} alt={t('me.selected')} className="max-h-80 w-full object-cover" />
            <button
              type="button"
              onClick={() => setImage(null)}
              className="absolute top-3 right-3 grid size-8 place-items-center rounded-full bg-black/60 text-white"
              aria-label={t('me.removePhoto')}
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
        ) : (
          <label
            htmlFor={`${id}-img`}
            className="flex h-40 cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-input text-sm font-semibold text-muted-foreground transition-colors duration-150 focus-within:border-primary hover:bg-muted hover:text-foreground"
          >
            <ImagePlus className="size-7" aria-hidden />
            {t('photo.add')}
            <input id={`${id}-img`} type="file" accept="image/*" className="sr-only" onChange={(e) => setImage(e.target.files?.[0] ?? null)} />
          </label>
        )}
        <Field label={t('me.caption')} htmlFor={`${id}-text`} trailing={<Count n={text.length} max={MAX_POST} />}>
          <TextArea id={`${id}-text`} value={text} maxLength={MAX_POST} placeholder={t('me.captionPlaceholder')} onChange={(e) => setText(e.target.value)} />
        </Field>
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!canPost}>
          {t('me.share')}
        </Button>
      </form>
    </Modal>
  )
}

function Count({ n, max }: { n: number; max: number }) {
  return <span className={`text-xs tabular-nums ${n >= max ? 'text-destructive' : 'text-muted-foreground'}`}>{n}/{max}</span>
}
