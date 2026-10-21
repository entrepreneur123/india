'use client'

import { useId, useState } from 'react'
import { ChevronLeft, ChevronRight, ImagePlus, X } from 'lucide-react'
import { BackendError, MAX_PHOTOS, type ProfilePhoto } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

/** Someone's extra photos in a row; a tap opens them large. `editable`: my own, with add and remove. */
export function Gallery({ photos, owner, editable, className }: { photos: ProfilePhoto[]; owner: string; editable?: boolean; className?: string }) {
  const [shown, setShown] = useState<number | null>(null)
  const { st } = useSocialT()
  if (!photos.length && !editable) return null
  return (
    <section className={cn('flex flex-col gap-2', className)} aria-label={st('photos.title')}>
      {editable ? <h2 className="text-sm font-semibold">{st('photos.title')}</h2> : null}
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
            {editable ? <RemoveButton id={p.id} /> : null}
          </li>
        ))}
        {editable && photos.length < MAX_PHOTOS ? (
          <li className="aspect-square">
            <AddTile />
          </li>
        ) : null}
      </ul>
      {editable ? <p className="text-xs leading-relaxed text-muted-foreground">{st('photos.hint')}</p> : null}
      <Viewer photos={photos} owner={owner} index={shown} onIndex={setShown} />
    </section>
  )
}

function AddTile() {
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
      setMe(await backend.addProfilePhoto(file))
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
      className="absolute top-1.5 right-1.5 grid size-7 place-items-center rounded-full bg-black/60 text-white transition-opacity duration-150 disabled:opacity-50"
      aria-label={st('photos.remove')}
    >
      <X className="size-4" aria-hidden />
    </button>
  )
}

function Viewer({ photos, owner, index, onIndex }: { photos: ProfilePhoto[]; owner: string; index: number | null; onIndex: (i: number | null) => void }) {
  const { t } = useI18n()
  const { st } = useSocialT()
  const i = index ?? 0
  const photo = photos[i]
  return (
    <Modal open={index !== null && !!photo} onClose={() => onIndex(null)} title={owner} description={st('photos.view', { n: i + 1, total: photos.length })} className="md:max-w-2xl">
      {photo ? (
        <div className="relative px-4 pb-4">
          <img src={photo.url} alt={st('photos.view', { n: i + 1, total: photos.length })} className="max-h-[70dvh] w-full rounded-2xl bg-muted object-contain" />
          {photos.length > 1 ? (
            <div className="mt-3 flex justify-center gap-3">
              <Button size="icon" variant="secondary" disabled={i === 0} onClick={() => onIndex(i - 1)} aria-label={t('common.back')}>
                <ChevronLeft className="size-5 rtl:rotate-180" aria-hidden />
              </Button>
              <Button size="icon" variant="secondary" disabled={i === photos.length - 1} onClick={() => onIndex(i + 1)} aria-label={st('tour.next')}>
                <ChevronRight className="size-5 rtl:rotate-180" aria-hidden />
              </Button>
            </div>
          ) : null}
        </div>
      ) : null}
    </Modal>
  )
}
