/**
 * The site's public address, for links search engines and share sheets see (metadata, sitemap, robots). Set
 * NEXT_PUBLIC_SITE_URL at build time once the real domain is live; until then the Vercel address.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/$/, '') || 'https://yaari-web-indol.vercel.app'
