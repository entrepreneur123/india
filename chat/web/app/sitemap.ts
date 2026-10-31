import type { MetadataRoute } from 'next'
import { LANGUAGES } from './[lang]/page'
import { SITE_URL as SITE } from '@/lib/site'

export const dynamic = 'force-static'

/** The home page and each language's page (app/[lang]), each listing the others (hreflang), and the legal pages. */
export default function sitemap(): MetadataRoute.Sitemap {
  const languages = Object.fromEntries(Object.entries(LANGUAGES).filter(([k]) => k !== 'x-default').map(([k, v]) => [k, SITE + (v === '/' ? '' : v)]))
  return [
    ...Object.values(languages).map((url) => ({ url, changeFrequency: 'weekly' as const, priority: url === SITE ? 1 : 0.8, alternates: { languages } })),
    ...['privacy.html', 'terms.html', 'child-safety.html'].map((p) => ({ url: `${SITE}/${p}`, changeFrequency: 'yearly' as const, priority: 0.3 })),
  ]
}
