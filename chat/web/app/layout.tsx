import type { Metadata, Viewport } from 'next'
import { Plus_Jakarta_Sans } from 'next/font/google'
import './globals.css'

// One variable font file for every weight. Only the Latin file is preloaded; the Vietnamese (and Latin
// extended) faces are in the same CSS with a unicode-range, so a browser fetches them only when Vietnamese
// text is on screen. Devanagari and Bengali use the device's own fonts (see --font-stack in globals.css).
const jakarta = Plus_Jakarta_Sans({
  subsets: ['latin'],
  variable: '--font-jakarta',
  display: 'swap',
})

export const metadata: Metadata = {
  title: 'yaari — meet someone new, anywhere',
  description:
    'Random video and text chat with people around the world, live public rooms, and a profile to share your moments.',
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
