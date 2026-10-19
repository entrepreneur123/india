// Before `next build`: the policy pages (privacy, terms, child safety, account deletion) live in chat/docs,
// shared with the Android app's GitHub Pages copy. They are served from this site as /privacy.html etc.
import { copyFileSync, mkdirSync } from 'node:fs'

const DOCS = new URL('../../docs/', import.meta.url)
const PUBLIC = new URL('../public/', import.meta.url)
mkdirSync(PUBLIC, { recursive: true })
for (const page of ['privacy', 'terms', 'child-safety', 'delete-account']) {
  copyFileSync(new URL(`${page}.html`, DOCS), new URL(`${page}.html`, PUBLIC))
}
console.log('copy-legal: 4 policy pages copied')
