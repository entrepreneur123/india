/**
 * The site's public address, for links search engines and share sheets see (metadata, sitemap, robots). Set
 * NEXT_PUBLIC_SITE_URL at build time once the real domain is live; until then the Vercel address.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/$/, '') || 'https://yaari-web-indol.vercel.app'

/** The share image (public/og.png); a page that sets its own openGraph must repeat it (Next merges metadata shallowly). */
export const OG_IMAGES = [{ url: '/og.png', width: 1200, height: 630 }]
