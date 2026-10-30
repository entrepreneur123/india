// Offline support: network first for everything, the last good copy when offline.
// (Cache-first for the page shell could pair a new app.js with an old i18n.js from the
// cache right after an update; network-first never mixes versions while online.)
importScripts('alerts.js');
const VERSION = 'jobs-v15';
const ALERTS = 'jobs-alerts'; // alert settings and what was announced: kept across app updates
const SHELL = ['./', 'index.html', 'app.js', 'eligibility.js', 'i18n.js', 'tools.js', 'tracker.js', 'passages.js', 'alerts.js', 'manifest.webmanifest', 'icon.svg', 'icon-192.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION && k !== ALERTS).map((k) => caches.delete(k))))
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

// ---------- alerts (see alerts.js) ----------
async function readJSON(key) {
  const hit = await (await caches.open(ALERTS)).match(key);
  return hit ? hit.json() : null;
}
async function writeJSON(key, value) {
  await (await caches.open(ALERTS)).put(key, new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } }));
}
async function checkAlerts() {
  const config = await readJSON('alerts/config');
  if (!config || !config.exams || !config.exams.length) return;
  const get = async (path) => {
    const res = await fetch(new URL(`v1/${path}`, self.registration.scope), { cache: 'no-cache' });
    if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
    return res.json();
  };
  const [feed, index] = await Promise.all([get('feed.json'), get('rules/index.json').catch(() => null)]);
  const today = new Date(Date.now() + 5.5 * 3600e3).toISOString().slice(0, 10); // IST
  const { notes, state } = self.JobsAlerts.pick(feed, index, await readJSON('alerts/state'), config, today);
  if (self.Notification && Notification.permission === 'granted') {
    for (const n of notes) {
      await self.registration.showNotification(n.title, { body: n.body, tag: n.tag, icon: 'icon-192.png', data: { url: n.url } }); // Android shows no SVG icons
    }
  }
  await writeJSON('alerts/state', state);
}
// From the push server (India server): instant, worded here in the phone's language.
self.addEventListener('push', (e) => {
  let data = {};
  try { data = e.data ? e.data.json() : {}; } catch { /* not ours: still shown, browsers require it */ }
  e.waitUntil(readJSON('alerts/config').catch(() => null).then((config) => {
    const n = self.JobsAlerts.fromPush(data, config);
    return self.registration.showNotification(n.title, { body: n.body, tag: n.tag, icon: 'icon-192.png', data: { url: n.url } });
  }));
});
self.addEventListener('periodicsync', (e) => { if (e.tag === 'jobs-alerts') e.waitUntil(checkAlerts()); });
// The app asks for one check right after alerts are switched on: it records what is already
// there, so the first background check only announces what is new after that.
self.addEventListener('message', (e) => { if (e.data === 'check-alerts') e.waitUntil(checkAlerts()); });
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const url = new URL((e.notification.data && e.notification.data.url) || '', self.registration.scope).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
    const win = wins.find((w) => w.url.startsWith(self.registration.scope));
    return win ? win.navigate(url).then((w) => (w || win).focus()).catch(() => self.clients.openWindow(url)) : self.clients.openWindow(url);
  }));
});
