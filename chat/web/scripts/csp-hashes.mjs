// After `next build`: pins the inline <script> tags of every exported page with a CSP of SHA-256 hashes.
//
// vercel.json sends the full Content-Security-Policy header. Its script-src must allow inline scripts, because
// Next.js streams page data through inline scripts whose contents change on every build. This adds a second,
// stricter policy as a <meta> tag: browsers enforce both, so the only inline scripts that can run are the
// exact ones Next.js wrote at build time (plus scripts from this origin, and YouTube's IFrame player API, which
// voice rooms load from https://www.youtube.com only when a shared track plays).
import { createHash } from 'node:crypto'
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const OUT = new URL('../out/', import.meta.url).pathname

function htmlFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return htmlFiles(path)
    return name.endsWith('.html') ? [path] : []
  })
}

let pages = 0
for (const file of htmlFiles(OUT)) {
  let html = readFileSync(file, 'utf8')
  html = html.replace(/<meta http-equiv="Content-Security-Policy"[^>]*>/, '') // idempotent
  const hashes = new Set()
  for (const match of html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)) {
    hashes.add(`'sha256-${createHash('sha256').update(match[1], 'utf8').digest('base64')}'`)
  }
  // 'wasm-unsafe-eval': TensorFlow.js (the on-device nudity check) compiles a tiny WebAssembly helper (long.js); no JS eval.
  const policy = `script-src 'self' 'wasm-unsafe-eval' https://www.youtube.com ${[...hashes].join(' ')}; object-src 'none'; base-uri 'self'`
  html = html.replace('<head>', `<head><meta http-equiv="Content-Security-Policy" content="${policy}"/>`)
  writeFileSync(file, html)
  pages++
}
console.log(`csp-hashes: pinned inline scripts in ${pages} page(s)`)
