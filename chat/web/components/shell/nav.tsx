'use client'

import * as m from 'motion/react-m'
import { MessagesSquare, Settings, Shuffle, UserRound, Users, type LucideIcon } from 'lucide-react'
import { useApp, type Tab } from '@/components/app-state'
import type { TKey } from '@/lib/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Logo } from '@/components/ui/bits'
import { cn } from '@/lib/utils'
import { preloadScreen } from './lazy-screens'

interface NavItem {
  tab: Tab
  label: TKey
  icon: LucideIcon
}

const ITEMS: NavItem[] = [
  { tab: 'rooms', label: 'nav.rooms', icon: MessagesSquare },
  { tab: 'random', label: 'nav.random', icon: Shuffle },
  { tab: 'recent', label: 'nav.recent', icon: Users },
  { tab: 'me', label: 'nav.me', icon: UserRound },
  { tab: 'settings', label: 'nav.settings', icon: Settings },
]

export function Sidebar({ className }: { className?: string }) {
  const { tab, setTab, t, me, entitlements, openPaywall } = useApp()
  return (
    <aside
      className={cn(
        'sticky top-0 h-dvh w-20 shrink-0 flex-col border-r border-border bg-background px-3 py-6 lg:w-64 lg:px-4',
        className,
      )}
      aria-label={t('nav.main')}
    >
      <div className="flex justify-center px-2 lg:justify-start">
        <span className="lg:hidden">
          <Logo compact />
        </span>
        <span className="hidden lg:inline-flex">
          <Logo />
        </span>
      </div>
      <nav className="mt-8 flex flex-col gap-1">
        {ITEMS.map((item) => {
          const active = tab === item.tab
          const Icon = item.icon
          return (
            <button
              key={item.tab}
              type="button"
              onClick={() => setTab(item.tab)}
              onPointerEnter={() => preloadScreen(item.tab)}
              onFocus={() => preloadScreen(item.tab)}
              aria-current={active ? 'page' : undefined}
              title={t(item.label)}
              className={cn(
                'relative flex h-12 items-center justify-center gap-4 rounded-full px-4 text-[15px] font-semibold transition-colors duration-150 lg:justify-start',
                active ? 'text-primary-text' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              <span
                className={cn('absolute inset-0 rounded-full bg-primary-soft transition-opacity duration-150', active ? 'opacity-100' : 'opacity-0')}
                aria-hidden
              />
              <Icon className="relative z-10 size-5.5 shrink-0" strokeWidth={active ? 2.4 : 2} aria-hidden />
              <span className="relative z-10 hidden truncate lg:inline">{t(item.label)}</span>
            </button>
          )
        })}
      </nav>

      <div className="mt-auto flex flex-col gap-3">
        {!entitlements.premium ? (
          <button
            type="button"
            onClick={openPaywall}
            onPointerEnter={() => preloadScreen('paywall')}
            className="hidden rounded-2xl border border-border p-4 text-left transition-colors duration-150 hover:bg-muted lg:block"
          >
            <span className="block text-sm font-bold text-primary-text">{t('common.plus')}</span>
            <span className="mt-0.5 block text-xs leading-relaxed text-muted-foreground">{t('panel.plusTitle')}</span>
          </button>
        ) : null}
        <button
          type="button"
          onClick={() => setTab('me')}
          className="flex items-center justify-center gap-3 rounded-full p-1.5 text-left transition-colors duration-150 hover:bg-muted lg:justify-start lg:p-2"
        >
          <Avatar name={me.name} src={me.photoUrl} size={36} />
          <span className="hidden min-w-0 lg:block">
            <span className="block truncate text-sm font-semibold">{me.name}</span>
            <span className="block truncate text-xs text-muted-foreground">
              {entitlements.premium ? t('common.plus') : t('nav.freePlan')}
            </span>
          </span>
        </button>
      </div>
    </aside>
  )
}

export function BottomTabs({ hidden }: { hidden?: boolean }) {
  const { tab, setTab, t } = useApp()
  return (
    <m.nav
      aria-label={t('nav.main')}
      initial={false}
      animate={{ y: hidden ? '110%' : 0 }}
      transition={{ type: 'tween', duration: 0.2, ease: [0.2, 0, 0, 1] }}
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <div className="mx-auto flex h-16 max-w-md items-stretch">
        {ITEMS.filter((i) => i.tab !== 'settings').map((item) => {
          const active = tab === item.tab || (item.tab === 'me' && tab === 'settings')
          const Icon = item.icon
          return (
            <button
              key={item.tab}
              type="button"
              onClick={() => setTab(item.tab)}
              onPointerDown={() => preloadScreen(item.tab)}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'relative flex min-w-0 flex-1 flex-col items-center justify-center gap-1 px-1 text-[11px] font-semibold transition-colors duration-150',
                active ? 'text-foreground' : 'text-muted-foreground',
              )}
            >
              {/* Material 3 navigation bar: the active icon sits in a pill. Random keeps a filled brand pill. */}
              <span
                className={cn(
                  'relative grid h-8 w-14 place-items-center rounded-full transition-colors duration-150',
                  item.tab === 'random' ? 'bg-primary text-primary-foreground' : active && 'bg-primary-soft text-primary-text',
                )}
              >
                <Icon className="size-5" strokeWidth={active ? 2.4 : 2} aria-hidden />
              </span>
              <span className="max-w-full truncate">{t(item.label)}</span>
            </button>
          )
        })}
      </div>
    </m.nav>
  )
}
