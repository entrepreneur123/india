'use client'

import { useEffect, useId, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { ChevronLeft, ChevronRight, ImagePlus, X } from 'lucide-react'
import { BackendError, HIGHLIGHT_TITLE_MAX, MAX_HIGHLIGHT_PHOTOS, type Highlight } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { namePlain } from '@/lib/handle'
import { retryImport } from '@/lib/retry-import'
import { useApp } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Field, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { PhotoViewer } from '@/components/ui/photo-viewer'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

// Profile highlights (057): titled albums as cards standing round a ring (the owner's sketch, 2026-10-28): seen from a
// little above, the front card is at the bottom at full size and the others stand upright round the circle behind it,
// smaller and dimmer the further back. It turns only when the person drags, taps a card behind or an arrow, never by
// itself; the viewer too goes to the next photo only when asked.

const key = (userId: string) => ['highlights', userId]
/** The ring's half width and half depth on screen (px): the back card stands 2 × RING_Y above the front one. */
const RING_X = 128
const RING_Y = 64
/** A card's place from the front one, the shorter way round the ring. */
const offsetOf = (i: number, active: number, n: number) => {
  if (n < 3) return i - active
  const m = (((i - active) % n) + n) % n
  return m > n / 2 ? m - n : m
}

export function Highlights({ userId, owner, className }: { userId: string; owner: string; className?: string }) {
  const backend = useBackend()
  const { me } = useApp()
  const { t } = useI18n()
  const { st } = useSocialT()
  const mine = me?.id === userId
  const { data } = useSWR(key(userId), () => backend.highlights(userId))
  const [active, setActive] = useState(0)
  const [viewing, setViewing] = useState<Highlight | null>(null)
  const [editing, setEditing] = useState<Highlight | null>(null)
  const drag = useRef<number | null>(null)
  const moved = useRef(false)
  const list = data ?? []
  // A new one is made from Edit profile (the owner: the profile itself stays as others see it).
  const n = list.length
  if (!n) return null
  const at = Math.min(active, n - 1)
  const turn = (by: number) => setActive((a) => (n >= 3 ? (Math.min(a, n - 1) + by + n) % n : Math.max(0, Math.min(n - 1, Math.min(a, n - 1) + by))))
  const onUp = (e: ReactPointerEvent) => {
    const dx = drag.current === null ? 0 : e.clientX - drag.current
    drag.current = null
    moved.current = Math.abs(dx) > 40
    if (moved.current) turn(dx < 0 ? 1 : -1)
  }

  return (
    <section className={cn('flex flex-col gap-2', className)} aria-label={st('hl.title')}>
      <h2 className="px-4 text-sm font-semibold">{st('hl.title')}</h2>
      <div
        className="relative isolate h-[15.5rem] touch-pan-y overflow-hidden select-none"
        onPointerDown={(e) => (drag.current = e.clientX)}
        onPointerUp={onUp}
        onPointerCancel={() => (drag.current = null)}
        // A drag is not a tap: the card it ends on stays as it is.
        onClickCapture={(e) => {
          if (moved.current) e.stopPropagation()
          moved.current = false
        }}
        // The cards sit left to right on screen in every language, so the arrows are physical too.
        onKeyDown={(e) => {
          if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') turn(e.key === 'ArrowRight' ? 1 : -1)
        }}
      >
        {list.map((c, i) => {
          const d = offsetOf(i, at, n)
          // Evenly round the ring; cos 1 = the front, -1 = the back.
          const a = (2 * Math.PI * d) / n
          const near = (1 + Math.cos(a)) / 2
          return (
            <button
              key={c.id}
              type="button"
              tabIndex={d === 0 ? 0 : -1}
              onClick={() => (d === 0 ? setViewing(c) : setActive(i))}
              className="absolute bottom-2 left-1/2 -ml-16 flex aspect-[3/4] w-32 origin-bottom flex-col overflow-hidden rounded-2xl bg-muted text-start shadow-e2 ring-2 ring-card transition-[transform,filter] duration-500 ease-out motion-reduce:transition-none"
              style={{
                transform: `translate(${RING_X * Math.sin(a)}px, ${-RING_Y * (1 - Math.cos(a))}px) scale(${0.45 + 0.55 * near * near})`,
                zIndex: Math.round(near * 100),
                filter: d ? `brightness(${0.7 + 0.3 * near})` : undefined,
              }}
            >
              <img src={c.photos[0]?.url} alt="" className="absolute inset-0 size-full object-cover" loading="lazy" draggable={false} />
              <span className="relative mt-auto bg-linear-to-t from-black/70 to-transparent px-2 pt-6 pb-2 text-sm font-semibold text-white">
                <span className="line-clamp-2">{c.title}</span>
              </span>
            </button>
          )
        })}
        {n > 1 ? (
          <>
            <Button size="icon-sm" variant="secondary" className="absolute top-1/2 left-2 z-[110] -translate-y-1/2" onClick={() => turn(-1)} aria-label={t('common.back')}>
              <ChevronLeft className="size-5" aria-hidden />
            </Button>
            <Button size="icon-sm" variant="secondary" className="absolute top-1/2 right-2 z-[110] -translate-y-1/2" onClick={() => turn(1)} aria-label={st('tour.next')}>
              <ChevronRight className="size-5" aria-hidden />
            </Button>
          </>
        ) : null}
      </div>
      <Viewer
        highlight={viewing}
        owner={owner}
        onClose={() => setViewing(null)}
        onEdit={mine && viewing ? () => (setEditing(viewing), setViewing(null)) : undefined}
      />
      {mine && editing ? <HighlightEditor userId={userId} highlight={editing} onClose={() => setEditing(null)} /> : null}
    </section>
  )
}

function Viewer({ highlight, owner, onClose, onEdit }: { highlight: Highlight | null; owner: string; onClose: () => void; onEdit?: () => void }) {
  const { t } = useI18n()
  const { st } = useSocialT()
  const [i, setI] = useState(0)
  const [large, setLarge] = useState<number | null>(null)
  const drag = useRef<number | null>(null)
  useEffect(() => setI(0), [highlight])
  const photos = highlight?.photos ?? []
  const photo = photos[Math.min(i, photos.length - 1)]
  const go = (by: number) => setI((x) => Math.max(0, Math.min(photos.length - 1, x + by)))
  const label = st('photos.view', { n: i + 1, total: photos.length })
  return (
    <Modal open={!!highlight && !!photo} onClose={onClose} title={highlight?.title ?? ''} description={`${owner} · ${label}`} className="md:max-w-2xl">
      {photo ? (
        <div className="relative px-4 pb-4">
          <img
            src={photo.url}
            alt={label}
            draggable={false}
            className="max-h-[70dvh] w-full cursor-zoom-in touch-pan-y rounded-2xl bg-muted object-contain select-none"
            onPointerDown={(e) => (drag.current = e.clientX)}
            onPointerUp={(e) => {
              const dx = drag.current === null ? 0 : e.clientX - drag.current
              drag.current = null
              if (Math.abs(dx) > 40) go((dx < 0) !== (document.dir === 'rtl') ? 1 : -1)
              // 39: a tap opens it on the whole screen (zoom, save).
              else if (Math.abs(dx) < 8) setLarge(i)
            }}
          />
          <PhotoViewer photos={photos.map((p) => p.url)} index={large} onIndex={(n) => (setLarge(n), n !== null && setI(n))} label={highlight?.title} />
          <div className="mt-3 flex items-center justify-center gap-3">
            {photos.length > 1 ? (
              <>
                <Button size="icon" variant="secondary" disabled={i === 0} onClick={() => go(-1)} aria-label={t('common.back')}>
                  <ChevronLeft className="size-5 rtl:rotate-180" aria-hidden />
                </Button>
                <span className="flex gap-1.5" aria-hidden>
                  {photos.map((p, k) => (
                    <span key={p.id} className={cn('size-1.5 rounded-full bg-muted-foreground/30', k === i && 'bg-primary')} />
                  ))}
                </span>
                <Button size="icon" variant="secondary" disabled={i === photos.length - 1} onClick={() => go(1)} aria-label={st('tour.next')}>
                  <ChevronRight className="size-5 rtl:rotate-180" aria-hidden />
                </Button>
              </>
            ) : null}
            {onEdit ? (
              <Button variant="outline" onClick={onEdit}>
                {st('hl.edit')}
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}
    </Modal>
  )
}

type Draft = { id: string; url: string; file?: File }

/** Makes a highlight (null) or changes one; Edit profile opens it for a new one. */
export function HighlightEditor({ userId, highlight, onClose }: { userId: string; highlight: Highlight | null; onClose: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { st } = useSocialT()
  const inputId = useId()
  const [title, setTitle] = useState(highlight?.title ?? '')
  const [photos, setPhotos] = useState<Draft[]>(highlight?.photos ?? [])
  const [busy, setBusy] = useState<'check' | 'save' | 'delete' | null>(null)
  const made = useRef<string[]>([])
  useEffect(() => () => made.current.forEach((u) => URL.revokeObjectURL(u)), [])
  const plain = namePlain(title)
  const ok = title.trim().length > 0 && plain && photos.length > 0 && !busy

  const add = async (files: File[]) => {
    const room = files.slice(0, MAX_HIGHLIGHT_PHOTOS - photos.length)
    if (!room.length) return
    setBusy('check')
    try {
      // The same on-device check as profile photos: a nude photo never leaves the phone.
      const { classifyImage } = await retryImport(() => import('@/lib/nudity'))
      const fine: Draft[] = []
      for (const file of room) {
        if (await classifyImage(file)) toast.show(st('photos.nude'), 'error')
        else {
          const url = URL.createObjectURL(file)
          made.current.push(url)
          fine.push({ id: url, url, file })
        }
      }
      setPhotos((p) => [...p, ...fine].slice(0, MAX_HIGHLIGHT_PHOTOS))
    } catch {
      toast.error(new BackendError('bad_media'))
    } finally {
      setBusy(null)
    }
  }
  const run = async (what: 'save' | 'delete') => {
    setBusy(what)
    try {
      if (what === 'delete') await backend.deleteHighlight(highlight!.id)
      else
        await backend.saveHighlight({
          id: highlight?.id ?? null,
          title: title.trim(),
          keep: photos.filter((p) => !p.file).map((p) => p.id),
          add: photos.flatMap((p) => (p.file ? [p.file] : [])),
        })
      await mutate(key(userId))
      onClose()
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(null)
    }
  }

  return (
    <Modal open onClose={onClose} title={highlight ? st('hl.edit') : st('hl.create')} className="md:max-w-lg">
      <div className="flex flex-col gap-4 px-4 pb-4">
        <Field label={st('hl.name')} htmlFor={`${inputId}-t`} error={plain ? null : st('hl.plain')}>
          <TextInput id={`${inputId}-t`} value={title} maxLength={HIGHLIGHT_TITLE_MAX} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <ul className="grid grid-cols-4 gap-2">
          {photos.map((p) => (
            <li key={p.id} className="relative aspect-square overflow-hidden rounded-2xl bg-muted">
              <img src={p.url} alt="" className="size-full object-cover" />
              <button
                type="button"
                onClick={() => setPhotos((list) => list.filter((x) => x !== p))}
                className="absolute top-1.5 inset-e-1.5 grid size-7 place-items-center rounded-full bg-black/60 text-white"
                aria-label={st('photos.remove')}
              >
                <X className="size-4" aria-hidden />
              </button>
            </li>
          ))}
          {photos.length < MAX_HIGHLIGHT_PHOTOS ? (
            <li className="aspect-square">
              <label
                htmlFor={`${inputId}-f`}
                className={cn(
                  'flex size-full cursor-pointer flex-col items-center justify-center gap-1 rounded-2xl border border-dashed border-input text-center text-xs font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted hover:text-foreground',
                  busy && 'pointer-events-none opacity-60',
                )}
              >
                <ImagePlus className={cn('size-6', busy === 'check' && 'animate-pulse')} aria-hidden />
                <span className="px-1">{busy === 'check' ? st('photos.checking') : st('hl.add')}</span>
                <input
                  id={`${inputId}-f`}
                  type="file"
                  accept="image/*"
                  multiple
                  className="sr-only"
                  disabled={!!busy}
                  onChange={(e) => {
                    void add([...(e.target.files ?? [])])
                    e.target.value = ''
                  }}
                />
              </label>
            </li>
          ) : null}
        </ul>
        <div className="flex gap-2">
          {highlight ? (
            <Button variant="outline" className="text-destructive hover:text-destructive" loading={busy === 'delete'} disabled={!!busy} onClick={() => void run('delete')}>
              {st('hl.delete')}
            </Button>
          ) : null}
          <Button variant="brand" className="flex-1" loading={busy === 'save'} disabled={!ok} onClick={() => void run('save')}>
            {st('hl.save')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
