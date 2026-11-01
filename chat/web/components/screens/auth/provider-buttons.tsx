'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/pressable'
import { useToast } from '@/components/ui/toast'
import { useBackend } from '@/lib/backend-context'
import type { OAuthProvider } from '@/lib/backend'
import { useI18n } from '@/components/i18n'
import { cn } from '@/lib/utils'

// "Continue with Google / Kakao / Facebook / Apple" (the owner, 2026-11-01), each in its maker's own button colours
// (Google: white with the four-colour G; Kakao: #FEE500 with a black bubble; Facebook: #1877F2; Apple: black).
// Only the providers turned on in Supabase are passed in, so nothing shows until one is set up.

const NAME: Record<OAuthProvider, string> = { google: 'Google', kakao: 'Kakao', facebook: 'Facebook', apple: 'Apple' }

const LOOK: Record<OAuthProvider, string> = {
  google: 'border border-border bg-white text-[#1F1F1F] hover:bg-[#F7F8F8]',
  kakao: 'bg-[#FEE500] text-black/85 hover:bg-[#F5DC00]',
  facebook: 'bg-[#1877F2] text-white hover:bg-[#166FE5]',
  apple: 'bg-black text-white hover:bg-black/85',
}

function Logo({ p }: { p: OAuthProvider }) {
  if (p === 'google')
    return (
      <svg viewBox="0 0 48 48" className="size-5" aria-hidden>
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
      </svg>
    )
  const d = {
    kakao: 'M12 3C6.48 3 2 6.58 2 11c0 2.83 1.84 5.31 4.6 6.72l-.94 3.44c-.08.3.26.54.52.37l4.1-2.72c.56.06 1.13.09 1.72.09 5.52 0 10-3.58 10-8S17.52 3 12 3z',
    facebook: 'M24 12.07C24 5.41 18.63 0 12 0S0 5.4 0 12.07C0 18.1 4.39 23.1 10.13 24v-8.44H7.08v-3.49h3.04V9.41c0-3.02 1.8-4.7 4.54-4.7 1.31 0 2.68.24 2.68.24v2.97h-1.5c-1.5 0-1.96.93-1.96 1.89v2.26h3.33l-.53 3.5h-2.8V24C19.62 23.1 24 18.1 24 12.07z',
    apple: 'M16.37 12.6c-.03-2.8 2.29-4.15 2.39-4.21-1.3-1.9-3.33-2.17-4.05-2.2-1.72-.18-3.36 1.01-4.24 1.01-.87 0-2.21-.99-3.64-.96-1.87.03-3.6 1.09-4.56 2.76-1.95 3.38-.5 8.38 1.4 11.12.93 1.34 2.03 2.85 3.48 2.8 1.4-.06 1.93-.9 3.62-.9 1.69 0 2.17.9 3.65.88 1.51-.03 2.46-1.37 3.38-2.71 1.07-1.55 1.5-3.06 1.53-3.14-.03-.01-2.93-1.12-2.96-4.45zM13.6 4.37c.77-.94 1.29-2.24 1.15-3.54-1.11.05-2.46.74-3.26 1.67-.71.82-1.34 2.15-1.17 3.42 1.24.1 2.5-.63 3.28-1.55z',
  }[p]
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden>
      <path d={d} />
    </svg>
  )
}

export function ProviderButtons({ providers, onSignedIn }: { providers: OAuthProvider[]; onSignedIn: () => void }) {
  const backend = useBackend()
  const toast = useToast()
  const { t } = useI18n()
  const [busy, setBusy] = useState<OAuthProvider | null>(null)
  if (!providers.length) return null
  return (
    <div className="flex flex-col gap-2">
      {providers.map((p) => (
        <Button
          key={p}
          type="button"
          size="lg"
          loading={busy === p}
          disabled={busy !== null}
          className={cn('w-full gap-2.5', LOOK[p])}
          onClick={async () => {
            setBusy(p)
            try {
              await backend.signInWith(p) // the real site leaves for the provider here; the demo signs in at once
              onSignedIn()
            } catch (err) {
              toast.error(err)
              setBusy(null)
            }
          }}
        >
          <Logo p={p} />
          {t('auth.with', { name: NAME[p] })}
        </Button>
      ))}
    </div>
  )
}

export const providerName = (p: OAuthProvider) => NAME[p]
