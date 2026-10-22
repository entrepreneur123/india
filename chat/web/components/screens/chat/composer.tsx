'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { ImagePlus, SendHorizontal, X } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

export function Composer({
  onSend,
  onTyping,
  disabled,
  allowMedia = true,
  placeholder,
  maxLength = 1000,
  disabledPlaceholder,
}: {
  onSend: (text: string | null, media: File | null) => Promise<boolean>
  onTyping?: () => void
  disabled?: boolean
  allowMedia?: boolean
  placeholder?: string
  maxLength?: number
  disabledPlaceholder?: string
}) {
  const { t } = useI18n()
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
    <div className="bg-chat-wall px-2 pt-1.5 pb-[max(0.5rem,env(safe-area-inset-bottom))] sm:px-[max(1.25rem,calc((100%-48rem)/2))]">
      <AnimatePresence>
        {preview && file ? (
          <m.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.15 }}
            className="overflow-hidden"
          >
            <div className="relative mb-2 w-fit">
              {file.type.startsWith('video') ? (
                <video src={preview} className="h-20 rounded-xl object-cover" muted />
              ) : (
                <img src={preview} alt={t('composer.preview')} className="h-20 rounded-xl object-cover" />
              )}
              <button
                type="button"
                onClick={() => setFile(null)}
                className="absolute -top-2 -inset-e-2 grid size-6 place-items-center rounded-full bg-foreground text-background"
                aria-label={t('composer.remove')}
              >
                <X className="size-3.5" aria-hidden />
              </button>
            </div>
          </m.div>
        ) : null}
      </AnimatePresence>
      <div className="flex items-end gap-2">
        {allowMedia ? (
          <label
            htmlFor={fileId}
            className={cn(
              'grid size-11 shrink-0 cursor-pointer place-items-center rounded-full text-muted-foreground transition-colors duration-150 focus-within:ring-2 focus-within:ring-ring hover:bg-muted hover:text-foreground',
              disabled && 'pointer-events-none opacity-40',
            )}
          >
            <ImagePlus className="size-5" aria-hidden />
            <span className="sr-only">{t('composer.attach')}</span>
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
        <div className="flex min-h-11 flex-1 items-center rounded-3xl bg-bubble-in px-4 text-bubble-text shadow-[0_1px_0.5px_rgb(11_20_26/0.13)] ring-1 ring-transparent transition-shadow duration-150 focus-within:ring-primary">
          <label htmlFor={`${fileId}-text`} className="sr-only">
            {t('composer.message')}
          </label>
          <textarea
            id={`${fileId}-text`}
            ref={inputRef}
            rows={1}
            value={text}
            disabled={disabled}
            maxLength={maxLength}
            placeholder={disabled ? (disabledPlaceholder ?? t('composer.ended')) : (placeholder ?? t('chat.placeholder'))}
            onChange={(e) => {
              setText(e.target.value)
              onTyping?.()
            }}
            onKeyDown={onKeyDown}
            className="max-h-33 w-full resize-none bg-transparent py-2.5 text-[15px] leading-relaxed outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
          />
        </div>
        <m.button
          type="button"
          onClick={() => void submit()}
          disabled={!canSend}
          whileTap={canSend ? { scale: 0.94 } : undefined}
          transition={{ duration: 0.1 }}
          className="grid size-11 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground transition-[opacity,background-color] duration-150 hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-40"
          aria-label={t('composer.send')}
        >
          <SendHorizontal className="size-5 rtl:rotate-180" aria-hidden />
        </m.button>
      </div>
    </div>
  )
}
