// After `next build`: writes out/_headers and out/_redirects for Cloudflare Pages from vercel.json, so both hosts serve
// the same headers and rewrites from one source (docs/MIGRATION_ROADMAP.md 0-9). Vercel ignores these two files.
// Format: developers.cloudflare.com/pages/configuration/headers/ and .../redirects/ (100 header rules, 2,000-char lines).
import { readFileSync, writeFileSync } from 'node:fs'

const vercel = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'))
const out = (name) => new URL(`../out/${name}`, import.meta.url)
// Vercel's path-to-regexp patterns we use: '/(.*)' and '/dir/(.*)' become a Pages splat; ':name' is the same in both.
const path = (source) => source.replace('(.*)', '*')

const headers = vercel.headers.map(({ source, headers: list }) =>
  [path(source), ...list.map(({ key, value }) => `  ${key}: ${value}`)].join('\n'))
// The *.pages.dev copies stay out of search results: the site's canonical address is set by NEXT_PUBLIC_SITE_URL.
headers.push('https://:project.pages.dev/*\n  X-Robots-Tag: noindex', 'https://:version.:project.pages.dev/*\n  X-Robots-Tag: noindex')
const long = headers.join('\n').split('\n').find((line) => line.length > 2000)
if (headers.length > 100 || long) throw new Error(`pages-config: over the _headers limits (${headers.length} rules${long ? ', a line over 2,000 chars' : ''})`)
writeFileSync(out('_headers'), headers.join('\n\n') + '\n')

const redirects = (vercel.rewrites ?? []).map(({ source, destination }) => `${path(source)} ${destination} 200`)
writeFileSync(out('_redirects'), redirects.join('\n') + '\n')
console.log(`pages-config: ${headers.length} header rules, ${redirects.length} rewrite(s)`)
