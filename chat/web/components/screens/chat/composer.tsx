'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { ImagePlus, SendHorizontal, X } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Composer({
  onSend,
  onTyping,
  disabled,
  allowMedia = true,
  placeholder = 'Say something nice…',
  maxLength = 1000,
  disabledPlaceholder = 'Chat has ended',
}: {
  onSend: (text: string | null, media: File | null) => Promise<boolean>
  onTyping?: () => void
  disabled?: boolean
  allowMedia?: boolean
  placeholder?: string
  maxLength?: number
  disabledPlaceholder?: string
}) {
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const fileId = useId()
  const canSend = !disabled && !sending && (text.trim().length > 0 || file !== null)

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  useEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 132)}px`
  }, [text])

  const submit = async () => {
    if (!canSend) return
    setSending(true)
    const ok = await onSend(text.trim() || null, file)
    setSending(false)
    if (ok) {
      setText('')
      setFile(null)
    }
    inputRef.current?.focus()
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      if (e.nativeEvent.isComposing || e.keyCode === 229) return
      e.preventDefault()
      void submit()
    }
  }

  return (
    <div className="border-t border-border bg-background/80 px-3 pt-2.5 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur-xl sm:px-5">
      <AnimatePresence>
        {preview && file ? (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <div className="relative mb-2 w-fit">
              {file.type.startsWith('video') ? (
                <video src={preview} className="h-20 rounded-2xl object-cover" muted />
              ) : (
                <img src={preview} alt="Attachment preview" className="h-20 rounded-2xl object-cover" />
              )}
              <button
                type="button"
                onClick={() => setFile(null)}
                className="absolute -top-2 -right-2 grid size-6 place-items-center rounded-full bg-foreground text-background"
                aria-label="Remove attachment"
              >
                <X className="size-3.5" aria-hidden />
              </button>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
      <div className="flex items-end gap-2">
        {allowMedia ? (
          <label
            htmlFor={fileId}
            className={cn(
              'grid size-11 shrink-0 cursor-pointer place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-within:ring-2 focus-within:ring-ring',
              disabled && 'pointer-events-none opacity-40',
            )}
          >
            <ImagePlus className="size-5" aria-hidden />
            <span className="sr-only">Attach photo or video</span>
            <input
              id={fileId}
              type="file"
              accept="image/*,video/*"
              className="sr-only"
              disabled={disabled}
              onChange={(e) => {
                setFile(e.target.files?.[0] ?? null)
                e.target.value = ''
              }}
            />
          </label>
        ) : null}
        <div className="flex min-h-11 flex-1 items-center rounded-3xl bg-muted px-4 ring-1 ring-transparent transition-shadow focus-within:ring-primary/50">
          <label htmlFor={`${fileId}-text`} className="sr-only">
            Message
          </label>
          <textarea
            id={`${fileId}-text`}
            ref={inputRef}
            rows={1}
            value={text}
            disabled={disabled}
            maxLength={maxLength}
            placeholder={disabled ? disabledPlaceholder : placeholder}
            onChange={(e) => {
              setText(e.target.value)
              onTyping?.()
            }}
            onKeyDown={onKeyDown}
            className="max-h-33 w-full resize-none bg-transparent py-2.5 text-[15px] leading-relaxed outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
          />
        </div>
        <motion.button
          type="button"
          onClick={() => void submit()}
          disabled={!canSend}
          whileTap={canSend ? { scale: 0.88 } : undefined}
          animate={{ scale: canSend ? 1 : 0.92, opacity: canSend ? 1 : 0.45 }}
          transition={{ type: 'spring', stiffness: 520, damping: 26 }}
          className="grid size-11 shrink-0 place-items-center rounded-full bg-brand text-primary-foreground shadow-[0_8px_20px_-8px_rgb(255_61_104/0.7)] disabled:cursor-not-allowed"
          aria-label="Send message"
        >
          <SendHorizontal className="size-5" aria-hidden />
        </motion.button>
      </div>
    </div>
  )
}
