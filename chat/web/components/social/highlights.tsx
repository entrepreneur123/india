'use client'

import { useEffect, useId, useRef, useState } from 'react'
import useSWR, { useSWRConfig } from 'swr'
import { ChevronLeft, ChevronRight, ImagePlus, Plus, X } from 'lucide-react'
import { BackendError, HIGHLIGHT_TITLE_MAX, MAX_HIGHLIGHT_PHOTOS, MAX_HIGHLIGHTS, type Highlight } from '@/lib/backend'
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
import { ScrollRow } from '@/components/ui/scroll-row'
import { cn } from '@/lib/utils'
import { useSocialT } from './social-text'

// Profile highlights (057) as Instagram's: a row of circles with a title under each, sliding sideways; my own profile
// ends with a "New" circle (until the limit). A tap opens the album; the viewer goes to the next photo only when asked.

const key = (userId: string) => ['highlights', userId]

export function Highlights({ userId, owner, className }: { userId: string; owner: string; className?: string }) {
  const backend = useBackend()
  const { me } = useApp()
  const { st } = useSocialT()
  const mine = me?.id === userId
  const { data } = useSWR(key(userId), () => backend.highlights(userId))
  const [viewing, setViewing] = useState<Highlight | null>(null)
  const [editing, setEditing] = useState<Highlight | 'new' | null>(null)
  const list = data ?? []
  if (!list.length && !(mine && data)) return null

  return (
    <section className={className} aria-label={st('hl.title')}>
      <ScrollRow as="ul" className="flex gap-4 overflow-x-auto px-4 pt-1 pb-1 no-scrollbar">
        {list.map((c) => (
          <li key={c.id} className="shrink-0">
            <Circle label={c.title} onClick={() => setViewing(c)}>
              <img src={c.photos[0]?.url} alt="" className="size-full object-cover" loading="lazy" draggable={false} />
            </Circle>
          </li>
        ))}
        {mine && list.length < MAX_HIGHLIGHTS ? (
          <li className="shrink-0">
            <Circle label={st('hl.create')} onClick={() => setEditing('new')} empty>
              <Plus className="size-7 text-foreground" strokeWidth={1.75} aria-hidden />
            </Circle>
          </li>
        ) : null}
      </ScrollRow>
      <Viewer
        highlight={viewing}
        owner={owner}
        onClose={() => setViewing(null)}
        onEdit={mine && viewing ? () => (setEditing(viewing), setViewing(null)) : undefined}
      />
      {mine && editing ? <HighlightEditor userId={userId} highlight={editing === 'new' ? null : editing} onClose={() => setEditing(null)} /> : null}
    </section>
  )
}

/** One highlight: its cover in a circle with a thin grey ring, its title below (New: a + in an empty circle). */
function Circle({ label, onClick, empty, children }: { label: string; onClick: () => void; empty?: boolean; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick} className="group flex w-[4.5rem] flex-col items-center gap-1.5">
      <span className="grid size-[4.5rem] place-items-center rounded-full ring-1 ring-border">
        <span className={cn('grid size-16 place-items-center overflow-hidden rounded-full bg-muted transition-transform duration-150 group-active:scale-95', empty && 'bg-card ring-1 ring-border')}>
          {children}
        </span>
      </span>
      {/* Titles stay on one line; "New highlight" may take two. */}
      <span className={cn('w-full text-center text-xs font-medium', empty ? 'line-clamp-2 leading-tight' : 'truncate')}>{label}</span>
    </button>
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
