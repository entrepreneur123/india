'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { ImagePlus, Mic, Plus, SendHorizontal, X } from 'lucide-react'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'
import { canRecord, VoiceRecorder } from './voice-note'

const ITEM = 'flex min-h-11 items-center gap-3 rounded-xl px-3 text-start text-sm font-semibold transition-colors duration-150 hover:bg-muted'

export function Composer({
  onSend,
  onTyping,
  disabled,
  allowMedia = true,
  allowVoice = allowMedia,
  placeholder,
  maxLength = 1000,
  disabledPlaceholder,
  className,
}: {
  onSend: (text: string | null, media: File | null) => Promise<boolean>
  onTyping?: () => void
  disabled?: boolean
  allowMedia?: boolean
  /** Voice messages (099); friends' threads have them without photos. */
  allowVoice?: boolean
  placeholder?: string
  maxLength?: number
  disabledPlaceholder?: string
  className?: string
}) {
  const { t } = useI18n()
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [recording, setRecording] = useState(false)
  // Voice messages (099) where the browser can record; started from the "+" menu.
  const [mic, setMic] = useState(false)
  const [menu, setMenu] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!menu) return
    const away = (e: PointerEvent) => !menuRef.current?.contains(e.target as Node) && setMenu(false)
    const esc = (e: globalThis.KeyboardEvent) => e.key === 'Escape' && setMenu(false)
    document.addEventListener('pointerdown', away)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('pointerdown', away)
      document.removeEventListener('keydown', esc)
    }
  }, [menu])
  useEffect(() => setMic(allowVoice && canRecord()), [allowVoice])
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
    <div className={cn('bg-chat-wall px-3 pt-2 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:px-[max(1.25rem,calc((100%-48rem)/2))]', className)}>
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
      {/* One rounded field (Instagram, iMessage): the photo button, the text and the send button inside it. */}
      <div className="flex min-h-11 items-end gap-1 rounded-[22px] bg-muted p-1 ring-1 ring-transparent transition-[box-shadow,background-color] duration-150 focus-within:bg-background focus-within:ring-border">
        {recording ? (
          <VoiceRecorder onSend={(clip) => onSend(null, clip)} onDone={() => setRecording(false)} />
        ) : (
          <>
            {allowMedia || mic ? (
              // "+" holds the photo and the voice message (the owner, 2026-10-30, as KakaoTalk's "+").
              <div ref={menuRef} className="relative shrink-0">
                <button
                  type="button"
                  onClick={() => setMenu((o) => !o)}
                  disabled={disabled}
                  aria-label={t('composer.more')}
                  aria-expanded={menu}
                  aria-haspopup="menu"
                  className={cn(
                    'grid size-9 place-items-center rounded-full transition-[background-color,transform,color] duration-150 disabled:opacity-40',
                    menu ? 'rotate-45 bg-foreground text-background' : 'text-muted-foreground hover:bg-background hover:text-foreground',
                  )}
                >
                  <Plus className="size-5" aria-hidden />
                </button>
                <input
                  id={fileId}
                  type="file"
                  accept="image/*,video/*"
                  className="sr-only"
                  tabIndex={-1}
                  disabled={disabled}
                  onChange={(e) => {
                    setFile(e.target.files?.[0] ?? null)
                    e.target.value = ''
                  }}
                />
                <AnimatePresence>
                  {menu ? (
                    <m.div
                      role="menu"
                      aria-label={t('composer.more')}
                      initial={{ opacity: 0, y: 8, scale: 0.96 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 8, scale: 0.96 }}
                      transition={{ type: 'tween', duration: 0.15, ease: [0.2, 0, 0, 1] }}
                      className="absolute bottom-full start-0 z-20 mb-2 flex w-52 origin-bottom-left flex-col gap-0.5 rounded-2xl bg-card p-1.5 shadow-e3 ring-1 ring-border"
                    >
                      {allowMedia ? (
                        <button type="button" role="menuitem" onClick={() => (setMenu(false), document.getElementById(fileId)?.click())} className={ITEM}>
                          <ImagePlus className="size-5 text-muted-foreground" aria-hidden /> {t('composer.attach')}
                        </button>
                      ) : null}
                      {mic ? (
                        <button type="button" role="menuitem" onClick={() => (setMenu(false), setRecording(true))} className={ITEM}>
                          <Mic className="size-5 text-muted-foreground" aria-hidden /> {t('composer.voice')}
                        </button>
                      ) : null}
                    </m.div>
                  ) : null}
                </AnimatePresence>
              </div>
            ) : null}
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
              className={cn('max-h-33 min-w-0 flex-1 resize-none bg-transparent py-1.5 text-[15px] leading-relaxed text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed', !allowMedia && !mic && 'ps-3')}
            />
              <m.button
                type="button"
                onClick={() => void submit()}
                disabled={!canSend}
                whileTap={canSend ? { scale: 0.92 } : undefined}
                transition={{ duration: 0.1 }}
                className="grid size-9 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground transition-[opacity,background-color] duration-150 hover:bg-primary-hover disabled:cursor-not-allowed disabled:bg-transparent disabled:text-muted-foreground disabled:opacity-60"
                aria-label={t('composer.send')}
              >
                <SendHorizontal className="size-[18px] rtl:rotate-180" aria-hidden />
              </m.button>
          </>
        )}
      </div>
    </div>
  )
}
