'use client'

import * as m from 'motion/react-m'
import { ChevronUp, X } from 'lucide-react'
import { useApp, type ChatBar } from '@/components/app-state'
import { useI18n } from '@/components/i18n'
import { Button } from '@/components/ui/pressable'
import { useSocialT } from '@/components/social/social-text'
import { UserAvatar } from '@/components/looks/user-avatar'

/**
 * The random tab while I'm on another tab. In a chat: who, whether they're typing or wrote meanwhile; X ends it (the
 * exit lock still applies). While searching: that it goes on and for whom; X stops it. A tap opens the random tab
 * again. Same place and look as a room's bar (voice-session.tsx VoicePill).
 */
export function ChatPill({ bar, onOpen, onEnd }: { bar: ChatBar; onOpen: () => void; onEnd: () => void }) {
  const { t } = useI18n()
  const { st } = useSocialT()
  const { me } = useApp()
  const { partner } = bar
  const who = partner ?? me
  const [title, open, end] = partner
    ? [st('chatbar.with', { name: partner.name }), st('chatbar.open', { name: partner.name }), st('chatbar.end')]
    : [t('matching.title'), st('chatbar.openSearch'), st('chatbar.stopSearch')]
  return (
    <m.div
      className="absolute inset-x-3 bottom-[calc(4.75rem+env(safe-area-inset-bottom))] z-20 md:inset-x-auto md:bottom-6 md:inset-s-6 md:w-[24rem]"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 16 }}
      transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
      role="region"
      aria-label={partner ? st('chatbar.label', { name: partner.name }) : t('matching.title')}
    >
      <div className="relative flex items-center gap-1 overflow-hidden rounded-[22px] bg-card p-2 text-foreground shadow-e3 ring-1 ring-primary-text/40">
        <button type="button" onClick={onOpen} className="relative flex min-w-0 flex-1 items-center gap-3 rounded-2xl p-1 text-start" aria-label={open}>
          <span className="relative shrink-0">
            {/* Searching: my photo with the matching screen's ring growing out of it. */}
            {partner ? null : <span className="absolute inset-1.5 animate-ping-soft rounded-full border-2 border-primary-text/60" aria-hidden />}
            <UserAvatar userId={who.id} name={who.name} src={who.photoUrl} size={42} />
            {partner && bar.unread > 0 ? <span aria-hidden className="absolute -top-0.5 -inset-e-0.5 size-3.5 rounded-full bg-primary ring-2 ring-card" /> : null}
          </span>
          <span className="min-w-0">
            <span className="block truncate text-sm font-bold">{title}</span>
            <span className="mt-0.5 block truncate text-xs text-muted-foreground" aria-live="polite">
              {!partner ? (
                bar.reconnecting ? t('matching.reconnecting') : bar.scope
              ) : bar.typing ? (
                <span className="text-primary-text">{t('chat.typing')}</span>
              ) : bar.unread > 0 ? (
                <span className="font-semibold text-foreground">{t('voice.newMessages')}</span>
              ) : (
                st('chatbar.live')
              )}
            </span>
          </span>
        </button>
        <Button size="icon" variant="ghost" onClick={onOpen} aria-label={open} className="relative max-sm:hidden">
          <ChevronUp className="size-5" aria-hidden />
        </Button>
        <Button size="icon" variant="ghost" onClick={onEnd} aria-label={end} className="relative text-destructive hover:text-destructive">
          <X className="size-5" aria-hidden />
        </Button>
      </div>
    </m.div>
  )
}
