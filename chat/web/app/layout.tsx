import type { Metadata, Viewport } from 'next'
import { Plus_Jakarta_Sans } from 'next/font/google'
import './globals.css'
import { LANGUAGES } from './[lang]/page'
import { OG_IMAGES, SITE_URL } from '@/lib/site'
import { THEME_SCRIPT } from '@/lib/theme'

// One variable font file for every weight. Only the Latin file is preloaded; the Vietnamese (and Latin
// extended) faces are in the same CSS with a unicode-range, so a browser fetches them only when Vietnamese
// text is on screen. Devanagari and Bengali use the device's own fonts (see --font-stack in globals.css).
const jakarta = Plus_Jakarta_Sans({
  subsets: ['latin'],
  variable: '--font-jakarta',
  display: 'swap',
})

// Search and link-preview text; the store listing (docs/STORE_LISTING.md) uses the same words.
const description =
  'Random video chat and random text chat with people around the world. Join live voice rooms, make foreign friends and share your moments. 18+.'

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: 'Yaari — Random Video Chat & Voice Rooms | Make Friends Worldwide',
  description,
  keywords: ['random video chat', 'random chat', 'video chat', 'voice rooms', 'make friends', 'foreign friends',
    'talk to strangers', 'meet new people', 'social app', 'रैंडम वीडियो चैट', '랜덤 영상채팅', '해외 친구 만들기', 'ランダムビデオチャット'],
  // Each app language has its own page (app/[lang]); search engines are told about all of them.
  alternates: { canonical: '/', languages: LANGUAGES },
  openGraph: { title: 'Yaari — Random Video Chat', description, siteName: 'Yaari', type: 'website', images: OG_IMAGES },
  twitter: { card: 'summary_large_image', title: 'Yaari — Random Video Chat', description, images: ['/og.png'] },
  // app/favicon.ico, app/icon.png and app/apple-icon.png are picked up by their file names; the SVG is the crisp one.
  icons: {
    icon: [{ url: '/icon.svg', type: 'image/svg+xml' }],
  },
}

// The site's name and logo for search results (schema.org WebSite + Organization; Google reads the site name from it).
const LD = JSON.stringify({
  '@context': 'https://schema.org',
  '@graph': [
    { '@type': 'WebSite', name: 'Yaari', url: `${SITE_URL}/` },
    { '@type': 'Organization', name: 'Yaari', url: `${SITE_URL}/`, logo: `${SITE_URL}/icon-512.png` },
  ],
})

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  // The device's light or dark until the page's own choice (lib/theme.ts) takes over.
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#ffffff' },
    { media: '(prefers-color-scheme: dark)', color: '#0f0e0d' },
  ],
  colorScheme: 'light dark',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  // `lang` is set to the user's language on the client (components/i18n.tsx).
  return (
    // suppressHydrationWarning: THEME_SCRIPT may add `dark` to <html> before React hydrates.
    <html lang="en" className={`${jakarta.variable} bg-background`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_SCRIPT }} />
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: LD }} />
      </head>
      <body className="font-sans antialiased">{children}</body>
    </html>
  )
}
