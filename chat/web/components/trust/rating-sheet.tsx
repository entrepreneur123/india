'use client'

import { AnimatePresence } from 'motion/react'
import * as m from 'motion/react-m'
import { useEffect, useState } from 'react'
import { useSWRConfig } from 'swr'
import { Check } from 'lucide-react'
import type { Profile } from '@/lib/backend'
import { useBackend } from '@/lib/backend-context'
import { errorCode } from '@/lib/format'
import { BAD_TAGS, GOOD_TAGS, MAX_RATING_TAGS, toggleTag, type RatingTag } from '@/lib/trust'
import { useApp } from '@/components/app-state'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { cn } from '@/lib/utils'
import { ChatFeelIcon } from './art'
import { skipRating } from './store'
import { useTrustT, type TrustKey } from './trust-text'
import { UserAvatar } from '@/components/looks/user-avatar'

export interface RateTarget {
  matchId: string
  partner: Profile
}

/**
 * After a random chat where both wrote: "How was your chat with {name}?" Good / Not good, up to 3 words for the
 * chosen side, then Submit or Skip. Once per chat (rate_chat); a skipped chat isn't asked again.
 */
export function RateChatSheet({ target, onClose }: { target: RateTarget | null; onClose: (rated: boolean) => void }) {
  return (
    <Modal open={target !== null} onClose={() => target && (skipRating(target.matchId), onClose(false))} variant="sheet" hideHeader className="md:max-w-[440px]">
      {target ? <RateBody key={target.matchId} target={target} onClose={onClose} /> : null}
    </Modal>
  )
}

function RateBody({ target, onClose }: { target: RateTarget; onClose: (rated: boolean) => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { mutate } = useSWRConfig()
  const { refreshTrust } = useApp()
  const { tt } = useTrustT()
  const [good, setGood] = useState<boolean | null>(null)
  const [tags, setTags] = useState<RatingTag[]>([])
  const [busy, setBusy] = useState(false)
  useEffect(() => setTags([]), [good])
  const list: readonly RatingTag[] = good === false ? BAD_TAGS : GOOD_TAGS

  const skip = () => {
    skipRating(target.matchId)
    onClose(false)
  }
  const submit = async () => {
    if (good === null) return
    setBusy(true)
    try {
      await backend.rateChat(target.matchId, good, tags)
      toast.show(tt('rate.thanks'), 'success')
      void mutate(['trust', target.partner.id])
      refreshTrust()
      onClose(true)
    } catch (err) {
      const code = errorCode(err)
      // Too late, already rated (another device), or one of us didn't write after all: nothing to ask any more.
      if (code === 'too_late' || code === 'already_rated' || code === 'not_allowed' || code === 'not_found') {
        skipRating(target.matchId)
        onClose(false)
      } else toast.error(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col items-center px-6 pt-7 pb-6 text-center" aria-label={tt('rate.label')}>
      <UserAvatar userId={target.partner.anon ? '' : target.partner.id} name={target.partner.name} src={target.partner.photoUrl} size={64} />
      <h2 className="mt-3 text-[22px] leading-tight font-extrabold tracking-tight text-balance">{tt('rate.title', { name: target.partner.name })}</h2>
      <p className="mt-1.5 text-sm text-muted-foreground">{tt('rate.body')}</p>

      <div className="mt-5 grid w-full grid-cols-2 gap-3" role="radiogroup" aria-label={tt('rate.label')}>
        {[true, false].map((g) => {
          const on = good === g
          const tone = g ? '#22a35a' : '#e5484d'
          return (
            <button
              key={String(g)}
              type="button"
              role="radio"
              aria-checked={on}
              onClick={() => setGood(g)}
              className={cn(
                'relative flex flex-col items-center gap-2 rounded-[24px] px-3 pt-4 pb-3.5 ring-2 transition-[background-color,box-shadow,transform] duration-150 active:scale-[.98]',
                on ? 'shadow-e2' : 'ring-transparent bg-muted hover:bg-input/40',
              )}
              style={on ? { background: `color-mix(in srgb, ${tone} 13%, var(--card))`, '--tw-ring-color': tone } as React.CSSProperties : undefined}
            >
              {on ? (
                <span className="absolute top-2.5 inset-e-2.5 grid size-5 place-items-center rounded-full text-white" style={{ background: tone }}>
                  <Check className="size-3.5" strokeWidth={3} aria-hidden />
                </span>
              ) : null}
              <ChatFeelIcon good={g} size={48} className={cn('transition-transform duration-150', on && 'scale-110')} />
              <span className="text-[15px] font-bold">{tt(g ? 'rate.good' : 'rate.bad')}</span>
            </button>
          )
        })}
      </div>

      <AnimatePresence initial={false}>
        {good !== null ? (
          <m.div
            key={String(good)}
            className="w-full overflow-hidden"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.18, ease: [0.2, 0, 0, 1] }}
          >
            <div className="mt-5 flex items-baseline justify-between gap-3 text-start">
              <h3 className="text-sm font-bold">{tt(good ? 'rate.goodTags' : 'rate.badTags')}</h3>
              <span className="text-xs font-semibold text-muted-foreground tabular-nums">
                {tags.length}/{MAX_RATING_TAGS} · {tt('rate.upTo')}
              </span>
            </div>
            <ul className="mt-2.5 flex flex-wrap gap-2">
              {list.map((tag) => {
                const on = tags.includes(tag)
                const full = !on && tags.length >= MAX_RATING_TAGS
                return (
                  <li key={tag}>
                    <button
                      type="button"
                      aria-pressed={on}
                      disabled={full}
                      onClick={() => setTags((x) => toggleTag(x, tag))}
                      className={cn(
                        'inline-flex h-10 items-center gap-1.5 rounded-full px-4 text-sm font-semibold ring-1 transition-colors duration-150 disabled:opacity-40',
                        on
                          ? good
                            ? 'bg-[#22a35a] text-white ring-[#22a35a]'
                            : 'bg-[#e5484d] text-white ring-[#e5484d]'
                          : 'bg-card ring-input hover:bg-muted',
                      )}
                    >
                      {on ? <Check className="size-4" strokeWidth={2.6} aria-hidden /> : null}
                      {tt(`tag.${tag}` as TrustKey)}
                    </button>
                  </li>
                )
              })}
            </ul>
            {good === false ? <p className="mt-3 text-start text-xs leading-snug text-muted-foreground">{tt('rate.reportHint')}</p> : null}
          </m.div>
        ) : null}
      </AnimatePresence>

      <div className="mt-6 flex w-full gap-2">
        <Button variant="ghost" size="lg" className="flex-1" onClick={skip} disabled={busy}>
          {tt('rate.skip')}
        </Button>
        <Button variant="brand" size="lg" className="flex-[2]" disabled={good === null} loading={busy} onClick={() => void submit()}>
          {tt('rate.submit')}
        </Button>
      </div>
    </div>
  )
}
