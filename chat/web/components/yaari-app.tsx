'use client'

import { AnimatePresence, MotionConfig, motion } from 'motion/react'
import { useCallback, useEffect, useState } from 'react'
import { BackendProvider, useBackend } from '@/lib/backend-context'
import type { Entitlements, Profile } from '@/lib/backend'
import { ToastProvider, useToast } from '@/components/ui/toast'
import { Logo } from '@/components/ui/bits'
import { AuthScreen } from '@/components/screens/auth/auth-screen'
import { AppShell } from '@/components/shell/app-shell'

type Boot =
  | { stage: 'loading' }
  | { stage: 'auth'; step: 'phone' | 'profile' }
  | { stage: 'app'; me: Profile; entitlements: Entitlements }

export function YaariApp() {
  return (
    <MotionConfig reducedMotion="user">
      <BackendProvider>
        <ToastProvider>
          <Root />
        </ToastProvider>
      </BackendProvider>
    </MotionConfig>
  )
}

function Root() {
  const backend = useBackend()
  const toast = useToast()
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
        toast.error(err)
        setBoot({ stage: 'auth', step: 'phone' })
      }
    },
    [backend, toast],
  )

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

  return (
    <AnimatePresence mode="wait">
      {boot.stage === 'loading' ? (
        <motion.div
          key="splash"
          className="theme-night grid min-h-dvh place-items-center bg-background text-foreground"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div animate={{ scale: [1, 1.06, 1] }} transition={{ duration: 1.6, repeat: Infinity }}>
            <Logo />
          </motion.div>
          <span className="sr-only">Loading yaari</span>
        </motion.div>
      ) : boot.stage === 'auth' ? (
        <motion.div key="auth" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <AuthScreen
            initialStep={boot.step}
            onPhoneVerified={setPhone}
            onComplete={(p) => void enterApp(p)}
          />
        </motion.div>
      ) : (
        <motion.div key="app" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <AppShell
            initialMe={boot.me}
            entitlements={boot.entitlements}
            phone={phone}
            onSignedOut={() => {
              setPhone(null)
              setBoot({ stage: 'auth', step: 'phone' })
            }}
          />
        </motion.div>
      )}
    </AnimatePresence>
  )
}
