'use client'

import { useEffect, useId, useState } from 'react'
import { ImagePlus, X } from 'lucide-react'
import { BackendError, type PostVisibility } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { POST_MAX } from '@/lib/feed'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Field, Segmented, TextArea } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { AudienceIcon } from '@/components/ui/topic-icons'
import { cn } from '@/lib/utils'
import { useFeedT } from '../feed-text'
import { postStore } from './post-store'

/** The last audience chosen, for the next post in this visit. */
let lastAudience: PostVisibility = 'friends'

/** A new post: a photo and/or text, for friends or for everyone (Discover). */
export function PostComposer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { me, setMe } = useApp()
  const { t } = useI18n()
  const { ft } = useFeedT()
  const id = useId()
  const [text, setText] = useState('')
  const [image, setImage] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [audience, setAudience] = useState<PostVisibility>(lastAudience)
  const [busy, setBusy] = useState<'check' | 'send' | null>(null)
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

  // The same on-device check as profile and room photos: a photo that looks like nudity never leaves the phone.
  const pick = async (file: File | undefined) => {
    if (!file) return
    setBusy('check')
    try {
      const { classifyImage } = await retryImport(() => import('@/lib/nudity'))
      if (await classifyImage(file)) {
        toast.show(ft('compose.nude'), 'error')
        return
      }
      setImage(file)
    } catch (err) {
      toast.error(err instanceof BackendError ? err : new BackendError('bad_media'))
    } finally {
      setBusy(null)
    }
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!canPost) return
    setBusy('send')
    try {
      const post = await backend.addPost(text.trim(), image, audience)
      lastAudience = audience
      setMe({ ...me, posts: [post, ...me.posts] })
      postStore.posted(post)
      toast.show(t('me.posted'), 'success')
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('me.newPost')} className="md:max-w-lg">
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 p-6 pt-4">
        {preview ? (
          <div className="relative overflow-hidden rounded-3xl bg-muted">
            <img src={preview} alt={t('me.selected')} className="max-h-80 w-full object-cover" />
            <button
              type="button"
              onClick={() => setImage(null)}
              className="absolute top-3 inset-e-3 grid size-8 place-items-center rounded-full bg-black/60 text-white"
              aria-label={t('me.removePhoto')}
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
        ) : (
          <label
            htmlFor={`${id}-img`}
            aria-busy={busy === 'check'}
            className={cn(
              'flex h-36 cursor-pointer flex-col items-center justify-center gap-2 rounded-3xl border border-dashed border-input text-sm font-semibold text-muted-foreground transition-colors duration-150 focus-within:border-primary hover:bg-muted hover:text-foreground',
              busy === 'check' && 'pointer-events-none opacity-60',
            )}
          >
            <ImagePlus className={cn('size-7', busy === 'check' && 'animate-pulse')} aria-hidden />
            {busy === 'check' ? ft('compose.checking') : t('photo.add')}
            <input id={`${id}-img`} type="file" accept="image/*" className="sr-only" disabled={!!busy} onChange={(e) => void pick(e.target.files?.[0])} />
          </label>
        )}
        <Field
          label={t('me.caption')}
          htmlFor={`${id}-text`}
          trailing={<span className={cn('text-xs tabular-nums', text.length >= POST_MAX ? 'text-destructive' : 'text-muted-foreground')}>{text.length}/{POST_MAX}</span>}
        >
          <TextArea id={`${id}-text`} value={text} maxLength={POST_MAX} placeholder={t('me.captionPlaceholder')} onChange={(e) => setText(e.target.value)} />
        </Field>
        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold" id={`${id}-aud`}>
            {ft('compose.audience')}
          </span>
          <Segmented<PostVisibility>
            label={ft('compose.audience')}
            value={audience}
            onChange={setAudience}
            size="lg"
            className="w-full"
            options={[
              { value: 'friends', label: <span className="inline-flex items-center gap-1.5"><AudienceIcon visibility="friends" size={16} />{ft('compose.friends')}</span> },
              { value: 'public', label: <span className="inline-flex items-center gap-1.5"><AudienceIcon visibility="public" size={16} />{ft('compose.everyone')}</span> },
            ]}
          />
          <p className="text-xs leading-relaxed text-muted-foreground">{audience === 'public' ? ft('compose.everyoneHint') : ft('compose.friendsHint')}</p>
        </div>
        <Button type="submit" variant="brand" size="lg" loading={busy === 'send'} disabled={!canPost}>
          {t('me.share')}
        </Button>
      </form>
    </Modal>
  )
}
