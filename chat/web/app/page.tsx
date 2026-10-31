import { YaariApp } from '@/components/yaari-app'
import { Landing } from '@/components/landing'
import { EN } from '@/lib/i18n'

// The English landing is in the HTML itself (search engines, link previews, a slow first visit); the app takes
// over once it runs, and someone with a session never sees it (components/yaari-app FirstPaint).
export default function Page() {
  return <YaariApp landing={<Landing lang="en" t={EN} />} />
}
