'use client'

import { useId, useState } from 'react'
import { ImagePlus, X } from 'lucide-react'
import { BackendError, MAX_PHOTOS, type Profile, type ProfilePhoto } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { PhotoViewer } from '@/components/ui/photo-viewer'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/**
 * My extra photos in Edit profile, with add and remove; a tap opens them large. Others see them by tapping the profile
 * photo (they open after it), not as a row of their own (the owner, 2026-10-28).
 */
export function Gallery({ photos, owner }: { photos: ProfilePhoto[]; owner: string }) {
  const [shown, setShown] = useState<number | null>(null)
  const { st } = useSocialT()
  return (
    <section className="flex flex-col gap-2" aria-label={st('photos.title')}>
      <h2 className="text-sm font-semibold">{st('photos.title')}</h2>
      <ul className="grid grid-cols-4 gap-2">
        {photos.map((p, i) => (
          <li key={p.id} className="relative aspect-square">
            <button
              type="button"
              onClick={() => setShown(i)}
              className="block size-full overflow-hidden rounded-2xl bg-muted"
              aria-label={st('photos.view', { n: i + 1, total: photos.length })}
            >
              <img src={p.url} alt="" className="size-full object-cover" loading="lazy" />
            </button>
            <RemoveButton id={p.id} />
          </li>
        ))}
        {photos.length < MAX_PHOTOS ? (
          <li className="aspect-square">
            <AddTile />
          </li>
        ) : null}
      </ul>
      {photos.length < MAX_PHOTOS ? <p className="text-xs leading-relaxed text-muted-foreground">{st('photos.hint', { n: MAX_PHOTOS - photos.length })}</p> : null}
      <PhotoViewer photos={photos.map((p) => p.url)} index={shown} onIndex={setShown} label={owner} />
    </section>
  )
}

/** Adds a profile photo (also used by the photo gradient's picker, which picks the new photo at once). */
export function AddTile({ onAdded }: { onAdded?: (me: Profile) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { setMe } = useApp()
  const { st } = useSocialT()
  const id = useId()
  const [busy, setBusy] = useState(false)
  const add = async (file: File | undefined) => {
    if (!file || busy) return
    setBusy(true)
    try {
      // The same on-device check as photos sent to rooms: a nude photo never leaves the phone.
      const { classifyImage } = await retryImport(() => import('@/lib/nudity'))
      if (await classifyImage(file)) {
        toast.show(st('photos.nude'), 'error')
        return
      }
      const fresh = await backend.addProfilePhoto(file)
      setMe(fresh)
      onAdded?.(fresh)
    } catch (err) {
      toast.error(err instanceof BackendError ? err : new BackendError('bad_media'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <label
      htmlFor={id}
      aria-busy={busy}
      className={cn(
        'flex size-full cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl border border-dashed border-input text-center text-xs font-semibold text-muted-foreground transition-colors duration-150 focus-within:border-primary hover:bg-muted hover:text-foreground',
        busy && 'pointer-events-none opacity-60',
      )}
    >
      <ImagePlus className={cn('size-6', busy && 'animate-pulse')} aria-hidden />
      <span className="px-1">{busy ? st('photos.checking') : st('photos.add')}</span>
      <input id={id} type="file" accept="image/*" className="sr-only" disabled={busy} onChange={(e) => void add(e.target.files?.[0])} />
    </label>
  )
}

function RemoveButton({ id }: { id: string }) {
  const backend = useBackend()
  const toast = useToast()
  const { setMe } = useApp()
  const { st } = useSocialT()
  const [busy, setBusy] = useState(false)
  return (
    <button
      type="button"
      disabled={busy}
      onClick={async () => {
        setBusy(true)
        try {
          setMe(await backend.removeProfilePhoto(id))
        } catch (err) {
          toast.error(err)
        } finally {
          setBusy(false)
        }
      }}
      className="absolute top-1.5 inset-e-1.5 grid size-7 place-items-center rounded-full bg-black/60 text-white transition-opacity duration-150 disabled:opacity-50"
      aria-label={st('photos.remove')}
    >
      <X className="size-4" aria-hidden />
    </button>
  )
}
