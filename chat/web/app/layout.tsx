import type { Metadata, Viewport } from 'next'
import { Plus_Jakarta_Sans } from 'next/font/google'
import './globals.css'
import { LANGUAGES } from './[lang]/page'
import { SITE_URL } from '@/lib/site'

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
  openGraph: { title: 'Yaari — Random Video Chat', description, siteName: 'Yaari', type: 'website', images: [{ url: '/og.png', width: 1200, height: 630 }] },
  twitter: { card: 'summary_large_image', title: 'Yaari — Random Video Chat', description, images: ['/og.png'] },
  // app/favicon.ico, app/icon.png and app/apple-icon.png are picked up by their file names; the SVG is the crisp one.
  icons: {
    icon: [{ url: '/icon.svg', type: 'image/svg+xml' }],
  },
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#ffffff',
  colorScheme: 'light',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  // `lang` is set to the user's language on the client (components/i18n.tsx).
  return (
    <html lang="en" className={`${jakarta.variable} bg-background`}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  )
}
