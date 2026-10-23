'use client'

import { LazyMotion, MotionConfig } from 'motion/react'
import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { BackendProvider, preloadBackend, useBackend } from '@/lib/backend-context'
import type { Entitlements, Profile } from '@/lib/backend'
import { DEMO_MODE } from '@/lib/config'
import { I18nProvider, useI18n } from '@/components/i18n'
import { ToastProvider, useToast } from '@/components/ui/toast'
import { Logo } from '@/components/ui/bits'
import { Button } from '@/components/ui/pressable'
import { Splash } from '@/components/splash'
import { ScreenBoundary } from '@/components/ui/screen-boundary'
import { retryImport } from '@/lib/retry-import'
import { InAppBanner } from '@/components/shell/in-app-banner'
import { installErrorReporting } from '@/lib/telemetry'

// The sign-up flow and the signed-in app are separate downloads; only one is needed to start.
const loadAuth = () => retryImport(() => import('@/components/screens/auth/auth-screen'))
const loadShell = () => retryImport(() => import('@/components/shell/app-shell'))
const AuthScreen = lazy(() => loadAuth().then((mod) => ({ default: mod.AuthScreen })))
const AppShell = lazy(() => loadShell().then((mod) => ({ default: mod.AppShell })))
// Motion's animation code is not needed for the first screen: fetch it after the page has loaded, so on a slow
// connection it never competes with the sign-up screen or the backend.
const afterLoad = () =>
  new Promise<void>((resolve) => {
    if (document.readyState === 'complete') resolve()
    else window.addEventListener('load', () => resolve(), { once: true })
  })
const loadMotionFeatures = () => afterLoad().then(() => import('@/lib/motion-features')).then((mod) => mod.default)

/** supabase-js keeps the session in localStorage under this key (see lib/supabaseBackend.ts). */
function probablySignedIn() {
  if (DEMO_MODE) return false // the demo always starts signed out
  try {
    return localStorage.getItem('yaari_auth') !== null
  } catch {
    return false
  }
}

if (typeof window !== 'undefined') {
  // A room link (/r/<id or code>): kept until the app is ready to open it, even through sign-up.
  const link = /^\/r\/([^/?#]+)/.exec(location.pathname)?.[1]
  if (link) {
    try {
      sessionStorage.setItem('yaari_link', decodeURIComponent(link))
    } catch {
      /* storage blocked: the link is lost, the app still opens */
    }
  }
  installErrorReporting()
  // Start every download the first screen needs at once, instead of one after another.
  void preloadBackend().catch(() => undefined)
  void (probablySignedIn() ? loadShell() : loadAuth()).catch(() => undefined)
}

type Boot =
  | { stage: 'loading' }
  | { stage: 'auth'; step: 'phone' | 'profile' }
  | { stage: 'offline' }
  | { stage: 'app'; me: Profile; entitlements: Entitlements }

export function YaariApp() {
  return (
    <MotionConfig reducedMotion="user">
      <LazyMotion features={loadMotionFeatures} strict>
        <I18nProvider fallback={<Splash />}>
          <Localized />
        </I18nProvider>
      </LazyMotion>
    </MotionConfig>
  )
}

function Localized() {
  const { t } = useI18n()
  const splash = <Splash label={t('common.loading')} />
  return (
    <BackendProvider fallback={splash}>
      <ToastProvider>
        <InAppBanner />
        <ScreenBoundary>
          <Suspense fallback={splash}>
            <Root />
          </Suspense>
        </ScreenBoundary>
      </ToastProvider>
    </BackendProvider>
  )
}

function Root() {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [boot, setBoot] = useState<Boot>({ stage: 'loading' })
  const [phone, setPhone] = useState<string | null>(null)

  const enterApp = useCallback(
    async (known?: Profile | null) => {
      try {
        const [me, entitlements] = await Promise.all([known ?? backend.myProfile(), backend.entitlements()])
        if (!me) {
          setBoot({ stage: 'auth', step: 'profile' })
          return
        }
        setBoot({ stage: 'app', me, entitlements })
      } catch (err) {
        // Signed in but the server couldn't be reached: keep the session and offer a retry (no new SMS code).
        toast.error(err)
        setBoot({ stage: 'offline' })
      }
    },
    [backend, toast],
  )

  // A room link's address goes back to "/" once kept (after Next's router has set up its history entry).
  useEffect(() => {
    if (!location.pathname.startsWith('/r/')) return
    const id = setTimeout(() => history.replaceState(history.state, '', '/'), 0)
    return () => clearTimeout(id)
  }, [])

  useEffect(() => {
    let alive = true
    backend
      .isSignedIn()
      .then((signedIn) => {
        if (!alive) return
        if (signedIn) void enterApp()
        else setBoot({ stage: 'auth', step: 'phone' })
      })
      .catch(() => alive && setBoot({ stage: 'auth', step: 'phone' }))
    return () => {
      alive = false
    }
  }, [backend, enterApp])

  // Stage changes fade in with CSS, so the first screen never waits for Motion's animation code to download.
  const fade = 'animate-in fade-in duration-150 motion-reduce:animate-none'
  return (
    <>
      {boot.stage === 'loading' ? (
        <Splash key="splash" label={t('common.loading')} />
      ) : boot.stage === 'offline' ? (
        <div key="offline" className={`grid min-h-dvh place-items-center bg-background px-6 text-center text-foreground ${fade}`}>
          <div className="flex flex-col items-center gap-4">
            <Logo />
            <h1 className="text-xl font-bold">{t('boot.offlineTitle')}</h1>
            <p className="max-w-xs text-sm text-muted-foreground">{t('boot.offlineBody')}</p>
            <Button
              variant="brand"
              size="lg"
              onClick={() => {
                setBoot({ stage: 'loading' })
                void enterApp()
              }}
            >
              {t('common.tryAgain')}
            </Button>
          </div>
        </div>
      ) : boot.stage === 'auth' ? (
        <div key="auth" className={fade}>
          <AuthScreen initialStep={boot.step} onPhoneVerified={setPhone} onComplete={(p) => void enterApp(p)} />
        </div>
      ) : (
        <div key="app" className={fade}>
          <AppShell
            initialMe={boot.me}
            entitlements={boot.entitlements}
            phone={phone}
            onSignedOut={() => {
              setPhone(null)
              setBoot({ stage: 'auth', step: 'phone' })
            }}
          />
        </div>
      )}
    </>
  )
}
