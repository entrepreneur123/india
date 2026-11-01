import type { Metadata } from 'next'
import { EN } from '@/lib/i18n'
import { Logo } from '@/components/ui/bits'

// Any unknown address (out/404.html, served with a 404 status): the brand and a way home instead of Next's bare
// default page. One static file for every visitor, so in English, from the app's own strings.
export const metadata: Metadata = { title: 'Yaari — 404' } // Next adds noindex to this page itself

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-6 px-6 text-center">
      <Logo />
      <h1 className="text-5xl font-bold tracking-tight">404</h1>
      <p className="text-lg text-muted-foreground">{EN['error.not_found']}</p>
      <a href="/" className="rounded-full bg-primary px-8 py-3 text-xl font-bold text-primary-foreground hover:bg-primary-hover">
        {EN['nav.home']}
      </a>
    </main>
  )
}
