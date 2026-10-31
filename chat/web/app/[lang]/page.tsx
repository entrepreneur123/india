import type { Metadata } from 'next'
import { EN, LANGS, type Dict } from '@/lib/i18n'
import { Landing } from '@/components/landing'

// A page per app language for search engines and shared links (owner's item 6): what Yaari is, in that language,
// from the app's own translated strings (no copy written just for these pages), and Start into the app in it.
// English is the home page itself.
export const dynamicParams = false
export const generateStaticParams = () => LANGS.filter((l) => l.id !== 'en').map((l) => ({ lang: l.id }))

/** hreflang for every page: each language's address, and the home page for anyone else. */
export const LANGUAGES = Object.fromEntries([['x-default', '/'], ...LANGS.map((l) => [l.id, l.id === 'en' ? '/' : `/${l.id}`])])

/** That language's strings, English where one is missing (as the app shows them). */
const dict = async (lang: string) => ({ ...EN, ...((await import(`@/lib/locales/${lang}`)).default as Dict) })
const plain = (s: string) => s.replace(/[[\]]/g, '')

export async function generateMetadata({ params }: { params: Promise<{ lang: string }> }): Promise<Metadata> {
  const { lang } = await params
  const t = await dict(lang)
  const title = `Yaari — ${plain(t['auth.headline'])}`
  return {
    title,
    description: t['auth.intro'],
    alternates: { canonical: `/${lang}`, languages: LANGUAGES },
    openGraph: { title, description: t['auth.intro'], siteName: 'Yaari', type: 'website', locale: lang },
  }
}

export default async function Page({ params }: { params: Promise<{ lang: string }> }) {
  const { lang } = await params
  return <Landing lang={lang} t={await dict(lang)} />
}
