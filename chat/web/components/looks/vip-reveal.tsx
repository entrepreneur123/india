'use client'

import { VenetianMask } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useLooksT } from './looks-text'
import { useAnonSwitch, useShop } from './use-shop'

/**
 * 35: while I'm in VIP, a slim black strip in random chats and rooms: the other side can't see who I am, and
 * "Turn off VIP" shows them my profile at once (they hear it from the server or my chat's channel).
 */
export function VipReveal({ className }: { className?: string }) {
  const { lt } = useLooksT()
  const { data: shop } = useShop()
  const anon = useAnonSwitch()
  if (!shop?.anon) return null
  return (
    <div className={cn('vip-strip flex items-center gap-2 px-3 py-1.5 text-xs font-semibold text-[#f5d27a]', className)} role="status">
      <VenetianMask className="size-4 shrink-0" aria-hidden />
      <span className="min-w-0 flex-1 truncate">{lt('vip.hidden')}</span>
      <button
        type="button"
        disabled={anon.busy}
        onClick={() => void anon.set(false)}
        className="shrink-0 rounded-full bg-[#f5d27a] px-3 py-1 text-[11px] font-bold text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-60"
      >
        {lt('vip.reveal')}
      </button>
    </div>
  )
}
