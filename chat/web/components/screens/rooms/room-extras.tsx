'use client'

import { useEffect, useId, useMemo, useRef } from 'react'
import { ImagePlus, Trash2 } from 'lucide-react'
import { roomTags, ROOM_TAGS_MAX } from '@/lib/hashtags'
import { Field, TextInput } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { useSocialT } from '@/components/social/social-text'

/**
 * 072: a room's cover photo and hashtags, in the create form and the host's settings. `cover`: a new photo, null
 * (none / removed) or undefined (keep `coverUrl`, the current one). `tags`: what was typed ("#music travel").
 */
export function RoomExtrasFields({
  cover,
  coverUrl,
  onCover,
  tags,
  onTags,
}: {
  cover: File | null | undefined
  coverUrl?: string | null
  onCover: (f: File | null) => void
  tags: string
  onTags: (s: string) => void
}) {
  const { st } = useSocialT()
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const picked = useMemo(() => (cover ? URL.createObjectURL(cover) : null), [cover])
  useEffect(() => () => void (picked && URL.revokeObjectURL(picked)), [picked])
  const shown = cover === undefined ? coverUrl ?? null : picked
  const chips = typedTags(tags)
  return (
    <>
      <div className="flex flex-col gap-2">
        <span className="text-sm font-semibold">{st('rooms.cover')}</span>
        {shown ? <img src={shown} alt="" className="aspect-[16/7] w-full rounded-2xl object-cover ring-1 ring-border" /> : null}
        <div className="flex gap-2">
          <Button type="button" variant="secondary" onClick={() => input.current?.click()}>
            <ImagePlus className="size-4.5" aria-hidden /> {shown ? st('rooms.coverChange') : st('rooms.coverAdd')}
          </Button>
          {shown ? (
            <Button type="button" variant="ghost" onClick={() => onCover(null)}>
              <Trash2 className="size-4.5" aria-hidden /> {st('rooms.coverRemove')}
            </Button>
          ) : null}
        </div>
        <input
          ref={input}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (f) onCover(f)
            e.target.value = ''
          }}
        />
      </div>
      <Field label={st('rooms.tags')} htmlFor={`${id}-tags`} hint={st('rooms.tagsHint', { n: ROOM_TAGS_MAX })}>
        <TextInput id={`${id}-tags`} value={tags} maxLength={160} placeholder="#music #chill" onChange={(e) => onTags(e.target.value)} />
      </Field>
      {chips.length ? (
        <p className="-mt-3 flex flex-wrap gap-x-2 text-[13px] font-semibold text-primary-text">
          {chips.map((t) => (
            <span key={t}>#{t}</span>
          ))}
        </p>
      ) : null}
    </>
  )
}

/** "#music, travel #chill" → the tags the server keeps. */
export const typedTags = (s: string) => roomTags(s.split(/[\s,]+/).filter(Boolean))
