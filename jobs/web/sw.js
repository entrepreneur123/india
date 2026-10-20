// Offline support: network first for everything, the last good copy when offline.
// (Cache-first for the page shell could pair a new app.js with an old i18n.js from the
// cache right after an update; network-first never mixes versions while online.)
const VERSION = 'jobs-v3';
const SHELL = ['./', 'index.html', 'app.js', 'eligibility.js', 'i18n.js', 'tools.js', 'tracker.js', 'passages.js', 'manifest.webmanifest', 'icon.svg'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return; // ssc.gov.in PDFs go straight through
  e.respondWith(fetch(req).then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(VERSION).then((c) => c.put(req, copy)); }
    return res;
  }).catch(() => caches.match(req, { ignoreSearch: true }).then((hit) => hit || Response.error())));
});
