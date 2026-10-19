'use client'

import { motion } from 'motion/react'
import { useEffect, useId, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Lock, Minus, Plus } from 'lucide-react'
import type { NewRoom, Room, RoomCategory, Target } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { Field, Segmented, TextInput } from '@/components/ui/bits'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'

export const CATEGORIES: { id: RoomCategory; label: string; emoji: string }[] = [
  { id: 'chat', label: 'Just chat', emoji: '💬' },
  { id: 'friends', label: 'Make friends', emoji: '🤝' },
  { id: 'language', label: 'Language', emoji: '🗣️' },
  { id: 'music', label: 'Music', emoji: '🎧' },
  { id: 'movies', label: 'Movies & TV', emoji: '🎬' },
  { id: 'gaming', label: 'Gaming', emoji: '🎮' },
  { id: 'sports', label: 'Sports', emoji: '⚽' },
  { id: 'study', label: 'Study', emoji: '🎓' },
  { id: 'travel', label: 'Travel', emoji: '✈️' },
  { id: 'night', label: 'Late night', emoji: '🌙' },
]

/** Short labels for who may join a user room, e.g. ["Women only", "Ages 20–30", "Private"]. */
export function roomConditions(r: Room): string[] {
  const out: string[] = []
  if (r.genderRule === 'female') out.push('Women only')
  if (r.genderRule === 'male') out.push('Men only')
  if (r.minAge > 18 || r.maxAge < 100) out.push(r.maxAge >= 100 ? `Ages ${r.minAge}+` : `Ages ${r.minAge}–${r.maxAge}`)
  if (r.isPrivate) out.push('Private')
  return out
}

const AGES = Array.from({ length: 83 }, (_, i) => 18 + i) // 18..100

export function CreateRoomDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (id: string, code: string) => void
}) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const id = useId()
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<RoomCategory>('chat')
  const [max, setMax] = useState(10)
  const [gender, setGender] = useState<Target>('any')
  const [minAge, setMinAge] = useState(18)
  const [maxAge, setMaxAge] = useState(100)
  const [isPrivate, setPrivate] = useState(false)
  const [busy, setBusy] = useState(false)
  const valid = title.trim().length >= 2 && title.trim().length <= 40 && minAge <= maxAge

  useEffect(() => {
    if (!open) return
    setTitle('')
    setCategory('chat')
    setMax(10)
    setGender('any')
    setMinAge(18)
    setMaxAge(100)
    setPrivate(false)
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true)
    const room: NewRoom = { title: title.trim(), category, maxMembers: max, genderRule: gender, minAge, maxAge, isPrivate }
    try {
      const made = await backend.createRoom(room)
      await mutate('rooms')
      onCreated(made.id, made.code)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} variant="sheet" title="Create a room" description="You’re the host. Opening a new room closes your previous one.">
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-5 p-6 pt-4">
        <Field label="Room name" htmlFor={`${id}-t`} trailing={<span className="text-xs tabular-nums text-muted-foreground">{title.length}/40</span>}>
          <TextInput id={`${id}-t`} value={title} maxLength={40} placeholder="e.g. Sunday cricket chat" onChange={(e) => setTitle(e.target.value)} data-autofocus />
        </Field>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold">Category</span>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Category">
            {CATEGORIES.map((c) => (
              <motion.button
                key={c.id}
                type="button"
                role="radio"
                aria-checked={category === c.id}
                whileTap={{ scale: 0.94 }}
                onClick={() => setCategory(c.id)}
                className={cn(
                  'flex h-9 items-center gap-1.5 rounded-full px-3.5 text-sm font-semibold ring-1 transition-colors',
                  category === c.id ? 'bg-brand text-primary-foreground ring-transparent' : 'bg-card ring-border hover:bg-muted',
                )}
              >
                <span aria-hidden>{c.emoji}</span> {c.label}
              </motion.button>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-between gap-4">
          <span className="text-sm font-semibold" id={`${id}-max`}>
            Max people
          </span>
          <div className="flex items-center gap-2" aria-labelledby={`${id}-max`}>
            <Button size="icon-sm" variant="secondary" onClick={() => setMax((m) => Math.max(2, m - 1))} aria-label="Fewer people" disabled={max <= 2}>
              <Minus className="size-4" aria-hidden />
            </Button>
            <span className="w-8 text-center text-lg font-bold tabular-nums" aria-live="polite">
              {max}
            </span>
            <Button size="icon-sm" variant="secondary" onClick={() => setMax((m) => Math.min(50, m + 1))} aria-label="More people" disabled={max >= 50}>
              <Plus className="size-4" aria-hidden />
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold">Who can join</span>
          <Segmented
            className="w-full"
            label="Who can join"
            value={gender}
            onChange={setGender}
            options={[
              { value: 'any', label: 'Everyone' },
              { value: 'female', label: 'Women' },
              { value: 'male', label: 'Men' },
            ]}
          />
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold">Ages</span>
          <div className="flex items-center gap-3">
            <AgeSelect label="Youngest" value={minAge} onChange={(v) => { setMinAge(v); if (v > maxAge) setMaxAge(v) }} />
            <span className="text-muted-foreground">–</span>
            <AgeSelect label="Oldest" value={maxAge} onChange={(v) => { setMaxAge(v); if (v < minAge) setMinAge(v) }} />
          </div>
        </div>

        <label className="flex cursor-pointer items-start gap-3 rounded-2xl bg-muted p-4">
          <input type="checkbox" checked={isPrivate} onChange={(e) => setPrivate(e.target.checked)} className="mt-0.5 size-5 accent-[#ff3d68]" />
          <span className="text-sm leading-relaxed">
            <span className="flex items-center gap-1.5 font-semibold">
              <Lock className="size-3.5" aria-hidden /> Private room
            </span>
            <span className="text-muted-foreground">Hidden from the list. People join only with the room code you share.</span>
          </span>
        </label>

        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={!valid}>
          Create room
        </Button>
      </form>
    </Modal>
  )
}

function AgeSelect({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(Number(e.target.value))}
      className="h-11 flex-1 rounded-2xl border border-input bg-card px-3 text-[15px] focus:border-primary focus:outline-none"
    >
      {AGES.map((a) => (
        <option key={a} value={a}>
          {a === 100 ? '100+' : a}
        </option>
      ))}
    </select>
  )
}

export function JoinCodeDialog({ open, onClose, onJoined }: { open: boolean; onClose: () => void; onJoined: (id: string) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const id = useId()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) setCode('')
  }, [open])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (code.length !== 6 || busy) return
    setBusy(true)
    try {
      const roomId = await backend.joinRoomCode(code)
      await mutate('rooms')
      onJoined(roomId)
    } catch (err) {
      toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Enter a room code" description="Ask the host for the 6-character code.">
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4 p-6 pt-4">
        <label htmlFor={`${id}-c`} className="sr-only">
          Room code
        </label>
        <input
          id={`${id}-c`}
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6))}
          placeholder="ABC123"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          data-autofocus
          className="h-16 w-full rounded-2xl border border-input bg-card text-center font-mono text-3xl font-bold tracking-[0.4em] focus:border-primary focus:shadow-[0_0_0_4px_rgb(255_61_104/0.14)] focus:outline-none"
        />
        <Button type="submit" variant="brand" size="lg" loading={busy} disabled={code.length !== 6}>
          Join room
        </Button>
      </form>
    </Modal>
  )
}
