'use client'

import { motion } from 'motion/react'
import { MessagesSquare, Settings, Shuffle, UserRound, Users, type LucideIcon } from 'lucide-react'
import { useApp, type Tab } from '@/components/app-state'
import type { TKey } from '@/lib/i18n'
import { Avatar } from '@/components/ui/avatar'
import { Logo } from '@/components/ui/bits'
import { cn } from '@/lib/utils'

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
      aria-label="Main"
    >
      <div className="flex justify-center px-2 lg:justify-start">
        <span className="lg:hidden">
          <Logo className="[&>span:last-child]:hidden" />
        </span>
        <span className="hidden lg:inline-flex">
          <Logo />
        </span>
      </div>
      <nav className="mt-10 flex flex-col gap-1.5">
        {ITEMS.map((item) => {
          const active = tab === item.tab
          const Icon = item.icon
          return (
            <button
              key={item.tab}
              type="button"
              onClick={() => setTab(item.tab)}
              aria-current={active ? 'page' : undefined}
              title={t(item.label)}
              className={cn(
                'group relative flex h-12 items-center justify-center gap-3.5 rounded-2xl px-3.5 text-[15px] font-semibold transition-colors lg:justify-start',
                active ? 'text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              {active ? (
                <motion.span
                  layoutId="sidebar-active"
                  className="absolute inset-0 rounded-2xl bg-muted"
                  transition={{ type: 'spring', stiffness: 480, damping: 36 }}
                />
              ) : null}
              <span className="relative z-10 grid place-items-center">
                <Icon
                  className={cn('size-5.5 transition-transform group-hover:scale-110', active && 'text-primary')}
                  strokeWidth={active ? 2.4 : 2}
                  aria-hidden
                />
              </span>
              <span className="relative z-10 hidden lg:inline">{t(item.label)}</span>
              {item.tab === 'random' ? (
                <span className="absolute top-2.5 right-2.5 z-10 size-2 rounded-full bg-primary lg:static lg:ml-auto" aria-hidden />
              ) : null}
            </button>
          )
        })}
      </nav>

      <div className="mt-auto flex flex-col gap-3">
        {!entitlements.premium ? (
          <motion.button
            type="button"
            onClick={openPaywall}
            whileHover={{ y: -2 }}
            whileTap={{ scale: 0.97 }}
            className="relative hidden overflow-hidden rounded-2xl bg-brand p-4 text-left text-primary-foreground shadow-[0_12px_30px_-14px_rgb(255_61_104/0.8)] lg:block"
          >
            <span className="block text-sm font-bold">Yaari Plus</span>
            <span className="mt-0.5 block text-xs leading-relaxed text-primary-foreground/85">
              {t('panel.plusTitle')}
            </span>
          </motion.button>
        ) : null}
        <button
          type="button"
          onClick={() => setTab('me')}
          className="flex items-center justify-center gap-3 rounded-2xl p-1.5 text-left transition-colors hover:bg-muted lg:justify-start lg:p-2"
        >
          <Avatar name={me.name} src={me.photoUrl} size={36} />
          <span className="hidden min-w-0 lg:block">
            <span className="block truncate text-sm font-semibold">{me.name}</span>
            <span className="block truncate text-xs text-muted-foreground">
              {entitlements.premium ? 'Yaari Plus' : 'Free plan'}
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
    <motion.nav
      aria-label="Main"
      initial={false}
      animate={{ y: hidden ? '110%' : 0 }}
      transition={{ type: 'spring', stiffness: 420, damping: 38 }}
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background/85 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl md:hidden"
    >
      <div className="mx-auto flex h-16 max-w-md items-stretch">
        {ITEMS.filter((i) => i.tab !== 'settings').map((item) => {
          const active = tab === item.tab || (item.tab === 'me' && tab === 'settings')
          const Icon = item.icon
          if (item.tab === 'random') {
            return (
              <button
                key={item.tab}
                type="button"
                onClick={() => setTab(item.tab)}
                aria-current={active ? 'page' : undefined}
                className="relative flex flex-1 flex-col items-center justify-end gap-1 pb-1.5 text-[11px] font-semibold text-foreground"
              >
                <motion.span
                  whileTap={{ scale: 0.88 }}
                  animate={{ y: active ? -2 : 0 }}
                  transition={{ type: 'spring', stiffness: 500, damping: 24 }}
                  className="-mt-7 grid size-14 place-items-center rounded-full bg-brand text-primary-foreground shadow-[0_10px_26px_-8px_rgb(255_61_104/0.85)] ring-4 ring-background"
                >
                  <Icon className="size-6" strokeWidth={2.4} aria-hidden />
                </motion.span>
                {t(item.label)}
              </button>
            )
          }
          return (
            <motion.button
              key={item.tab}
              type="button"
              whileTap={{ scale: 0.88 }}
              onClick={() => setTab(item.tab)}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'relative flex flex-1 flex-col items-center justify-center gap-1 text-[11px] font-semibold transition-colors',
                active ? 'text-foreground' : 'text-muted-foreground',
              )}
            >
              {active ? (
                <motion.span
                  layoutId="tab-active"
                  className="absolute top-0 h-0.5 w-8 rounded-full bg-brand"
                  transition={{ type: 'spring', stiffness: 500, damping: 36 }}
                />
              ) : null}
              <Icon className={cn('size-5.5', active && 'text-primary')} strokeWidth={active ? 2.4 : 2} aria-hidden />
              {t(item.label)}
            </motion.button>
          )
        })}
      </div>
    </motion.nav>
  )
}
