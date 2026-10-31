import type { MetadataRoute } from 'next'

// A static site (output: export) must say so for this route to be written at build time.
export const dynamic = 'force-static'

// /manifest.webmanifest: the site as an installable app (home-screen icon, name, colours). Icons: public/icon-*.png.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Yaari — Random Chat & Voice Rooms',
    short_name: 'Yaari',
    description: 'Random chat with people around the world, live voice rooms, new friends. 18+.',
    start_url: '/',
    display: 'standalone',
    background_color: '#ffffff',
    theme_color: '#ea6a00',
    icons: [
      { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  }
}
