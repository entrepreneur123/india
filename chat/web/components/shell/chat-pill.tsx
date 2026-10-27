'use client'

import * as m from 'motion/react-m'
import { ChevronUp, X } from 'lucide-react'
import type { ChatBar } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { useSocialT } from '@/components/social/social-text'
import { UserAvatar } from '@/components/looks/user-avatar'

/**
 * The random chat while I'm on another tab: who, whether they're typing or wrote meanwhile; a tap opens the chat
 * again, X ends it (the exit lock still applies). Same place and look as a room's bar (voice-session.tsx VoicePill).
 */
export function ChatPill({ bar, onOpen, onEnd }: { bar: ChatBar; onOpen: () => void; onEnd: () => void }) {
  const { t } = useI18n()
  const { st } = useSocialT()
  const { partner, typing, unread } = bar
  return (
    <m.div
      className="absolute inset-x-3 bottom-[calc(4.75rem+env(safe-area-inset-bottom))] z-20 md:inset-x-auto md:bottom-6 md:inset-s-6 md:w-[24rem]"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 16 }}
      transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
      role="region"
      aria-label={st('chatbar.label', { name: partner.name })}
    >
      <div className="relative flex items-center gap-1 overflow-hidden rounded-[22px] bg-card p-2 text-foreground shadow-e3 ring-1 ring-primary-text/40">
        {/* The brand colour, washed in from the left. */}
        <button type="button" onClick={onOpen} className="relative flex min-w-0 flex-1 items-center gap-3 rounded-2xl p-1 text-start" aria-label={st('chatbar.open', { name: partner.name })}>
          <span className="relative shrink-0">
            <UserAvatar userId={partner.id} name={partner.name} src={partner.photoUrl} size={42} />
            {unread > 0 ? <span aria-hidden className="absolute -top-0.5 -inset-e-0.5 size-3.5 rounded-full bg-primary ring-2 ring-card" /> : null}
          </span>
          <span className="min-w-0">
            <span className="block truncate text-sm font-bold">{st('chatbar.with', { name: partner.name })}</span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground" aria-live="polite">
              {typing ? (
                <span className="text-primary-text">{t('chat.typing')}</span>
              ) : unread > 0 ? (
                <span className="font-semibold text-foreground">{t('voice.newMessages')}</span>
              ) : (
                st('chatbar.live')
              )}
            </span>
          </span>
        </button>
        <Button size="icon" variant="ghost" onClick={onOpen} aria-label={st('chatbar.open', { name: partner.name })} className="relative max-sm:hidden">
          <ChevronUp className="size-5" aria-hidden />
        </Button>
        <Button size="icon" variant="ghost" onClick={onEnd} aria-label={st('chatbar.end')} className="relative text-destructive hover:text-destructive">
          <X className="size-5" aria-hidden />
        </Button>
      </div>
    </m.div>
  )
}
