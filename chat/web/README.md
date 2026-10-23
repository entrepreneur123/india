# Yaari web (PC + mobile browser)

The web version of Yaari. It uses the **same Supabase server and the same rules** as the Android live app
(`chat/server`), so web and app users meet each other in random chat, rooms and friends.

- Next.js 16 (static export) + Tailwind CSS 4 + Motion (`LazyMotion` + `m`).
- PC: sidebar · content · right panel (friends, live rooms). Phone browser: Material-style bottom navigation.
- White + orange, light only: orange `#EA6A00` fills with bold white text on them (3.2:1; `#B45309` as orange text on
  white, 5:1), stone-grey ink `#1C1917` and neutrals; no gradients, neutral elevation, one radius scale, short
  100–200 ms motion. Random chat, chats and rooms are white with orange accents; only the video call (live video
  fills the screen) is dark, with scrims under its controls.
  `prefers-reduced-motion` turns animation off. Body text contrast ≥ 4.5:1.

## Run

```bash
pnpm install
pnpm dev            # http://localhost:3000 — demo backend (no server, any 6-digit code)
pnpm build          # static site in out/ (next build + scripts/csp-hashes.mjs)
pnpm typecheck      # tsc --noEmit (next build runs it too)
pnpm test           # unit tests of the pure logic in lib/ (node --test, lib/tests/*.test.ts)
```

Live server: set both variables (Vercel → Project → Settings → Environment Variables), then build.

```
NEXT_PUBLIC_SUPABASE_URL=https://dgynsckrqgnpyyvqekzi.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<SUPABASE_ANON_KEY from chat/gradle.properties>
```

Without them the site runs on `lib/mockBackend.ts` (in-browser demo, "Demo preview" shown on the code step).

## Languages

English, हिन्दी (hi), বাংলা (bn), Português do Brasil (pt-BR) and Tiếng Việt (vi). Every user-visible string is in
`lib/locales/<lang>.ts`; `en.ts` is the source and every other file is typed `Record<TKey, string>`, so a missing or
unknown key fails `tsc`. Use `t('key', { name })` (from `useI18n()` or `useApp()`); `[words]` in a string mark a
link/highlight and `{name}` placeholders can be React nodes via `<Rich>`.

- First visit: picked from `navigator.languages` (bn-* → bn, pt-* → pt-BR, vi → vi, hi → hi, else en). The choice is
  saved in `localStorage` (`yaari_lang`) and can be changed in Settings or on the sign-up screen. `<html lang>`
  follows the choice.
- Only English is in the main bundle; the other languages are 6–7 KB (gzip) chunks loaded when used.
- Dates, times, numbers, relative times and country names use `Intl` with the active locale. The phone country on
  the sign-up screen is guessed from the time zone (Asia/Kolkata → +91, Asia/Dhaka → +880 …).
- Fonts: Plus Jakarta Sans (one variable file; Latin preloaded, the Vietnamese face is fetched only when Vietnamese
  text is on screen). Hindi and Bengali use the device's own fonts (Noto on Android, Nirmala UI on Windows, the
  Sangam fonts on Apple): no download, and letter-spacing is reset for them.
- Official room names/descriptions are translated by room id; user content (names, room titles, messages) is not.

## Performance (slow mobile data, low-end phones)

- First load fetches only React, the app shell, English and the screen that is needed. The demo never downloads
  supabase-js and the live site never downloads the demo (`lib/backend-context.tsx`, dynamic import).
- Sign-up and the signed-in app are separate chunks (the sign-up profile form too: it loads while the code is typed);
  Rooms, Recent, Me, Settings, threads, profile sheet, post sheet, paywall, room dialogs, the chat view and the call
  screen load on demand (prefetched on hover/pointer-down). The posts' strings (`components/social/locales/feed/`),
  the post composer, the profile details editor, the Looks shop and the live backend's post/comment parsing
  (`lib/supabaseFeed.ts`) are their own chunks too; the demo's posts are `lib/mockFeed.ts`, its looks
  `lib/mockLooks.ts`.
- ID fonts (`public/fonts/looks/`, `@font-face` in `app/globals.css`, `font-display: swap`, no preload) download only
  when an @ID in that font is on screen.
  `livekit-client` loads only when a call is accepted.
- Motion's animation code loads after the page `load` event; drag only inside the call screen.
- The right panel (friends, live rooms) and its requests exist only on screens ≥ 1280 px.
- While the browser tab is hidden: chat re-sync (10 s), room re-sync (15 s), room heartbeat (30 s) and the Plus
  check pause, and run at once when the tab is shown again. Matching (2 s poll) polls only while the tab is visible
  and cancels the search after 60 s hidden. The exit lock is unchanged.
- `/_next/static` is cached for a year (`vercel.json`); Vercel compresses responses.

Measured on the demo build (Playwright Chromium, phone viewport, gzip as sent by the server):

| | before | after |
|---|---|---|
| JS on first load (sign-up screen) | 301 KB | 218 KB (live build: 278 KB incl. supabase-js) |
| Slow 3G + 4× CPU: first paint / phone input usable | 8.1 s / 8.9 s | 2.1 s / 7.2 s |
| Lighthouse mobile: perf / a11y / best practices / SEO | 78 / 100 / 96 / 100 | 96 / 100 / 100 / 100 |

## Security

Headers (`vercel.json`, all paths): `Content-Security-Policy` (self; the Supabase project over https/wss; LiveKit
Cloud over https/wss; `data:`/`blob:` for images and media previews; `frame-ancestors 'none'`, `object-src 'none'`,
`base-uri 'self'`, `form-action 'self'`), `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` (camera and
microphone for this site only, everything else off), `Cross-Origin-Opener-Policy: same-origin`.

Next.js puts page data in inline scripts, so the header's `script-src` has to allow inline. `scripts/csp-hashes.mjs`
(run by `pnpm build`) adds a second CSP as a `<meta>` tag with the SHA-256 of each inline script it finds in `out/`;
browsers enforce both policies, so only those exact inline scripts can run. When adding a host (e.g. a CDN or an
analytics service), add it to `vercel.json`.

- Only the public Supabase anon key is in the bundle; every rule is enforced by the server (RLS, RPCs).
- User text is always rendered as React text (no `dangerouslySetInnerHTML` anywhere); media comes from Supabase
  signed URLs (12 h) or local `blob:` previews.
- The session: supabase-js keeps it in `localStorage` (`yaari_auth`). That is the standard for a static SPA (no
  server to set httpOnly cookies); the CSP above is what keeps third-party script away from it.

## Structure

```
lib/backend.ts          the Backend interface every screen uses
lib/supabaseBackend.ts  live server (same calls as the Android SupabaseBackend.kt)
lib/mockBackend.ts      in-browser demo
lib/livekit.ts          video calls (LiveKit, adaptive stream + dynacast like the app)
lib/i18n.ts, locales/   languages
components/i18n.tsx     language provider, useI18n(), <Rich>
components/screens/     auth, random (lobby/matching/chat), rooms, recent, me, profile, settings, paywall
components/shell/       sidebar, bottom tabs, right panel, lazy screen loaders
```

## IDs and looks (040_handles_looks.sql, docs/LOOKS_SPEC.md)

- **IDs**: every profile has an `@handle` (`Profile.handle`). Sign-up asks for one (prefilled from the name, checked
  400 ms after typing with `handle_check`), Edit profile changes it (`set_handle`; once every 7 days, `shop().handle_next`
  says when). The server's rules are mirrored in `lib/handle.ts` (unit-tested with the server's own examples); names
  refuse fancy-font letters (`name_invalid`).
- **Looks** (`components/looks/`): `<LookName>` draws an @ID in its colour and font with its badge; looks are read with
  `looks(ids)`, batched like the trust badge (one call per frame, 2 min cache). Badges and frames (ring, wash, canvas
  particles: ≤ 30, ≤ 30 fps, paused off screen, still with reduced motion) are drawn in code. The shop (Me → Looks)
  shows prices and "Get it in the Yaari app" on the web (looks are bought on Android); the demo sells them at once.
- **Rooms**: the header's count opens the People sheet (friend buttons), "Invite" opens the friends list, "Leave the
  stage" sits next to the mic. The room and its chat share the screen by category (talk: chat first, music: player
  first); phones drag a handle (snap 0/30/50/70 %, double tap), wide screens a splitter (keyboard ←/→), remembered in
  `localStorage` (`yaari_room_split_<talk|music>_<phone|wide>`, `lib/room-split.ts`).
- **Recent**: a thread whose random chat ended goes on only between friends; until then a friends bar replaces the
  composer.
- The Feed tab is gone; posts stay on profiles.

## Same rules as the app (enforced by the server)

Exit lock 5 s (Plus 0.2 s), report & leave always allowed, gender filter and video requests are paid,
flag from the verified phone number, profiles only visible to people you met. Purchases happen in the
Android app (Google Play); Plus then works on the web for the same account.

## Real identities and manners (035_identity_manners.sql)

- **Verification** (`components/trust/verify-sheet.tsx`, lazy): ID + liveness selfie through the `kyc` Edge Function
  (Didit's hosted page, in the same tab; it comes back to `/?kyc=done`, which is read with `kyc {action:'check'}` and
  removed from the address). Every result has its own screen: pending, verified, declined, unreadable, duplicate ID,
  under 18, and "coming soon" while the server has no provider (`kyc_unavailable`; then nobody is asked again for
  12 h). Opened from the card on Me, right after sign-up (once per browser; at every start and without "Not now"
  while `my_trust().required`), and by any `verify_required` refusal instead of a message (`lib/gate.ts`).
- **Badge**: `components/trust/mark.tsx` (Yaari's own two-tone shield, never an emoji). Posts and comments carry
  `verified`; everyone else's comes from `trust(ids)`, batched (one call per frame for a whole list, `useTrust`).
  Profiles show "ID verified · gender · age · country" with a lock (those come from the ID and can't be changed).
- **Manners**: after a random chat where both wrote, "How was your chat with …?" (Good / Not good, up to 3 words,
  Submit or Skip; once per chat, `rate_chat`). Profiles and Me show the score on a gauge with its level and the top
  kind words; under 3 ratings "New here". `manner_low` opens an explanation instead of a message. Pure rules in
  `lib/trust.ts` (unit-tested).
- **Demo switches** (in the address): `?demo_required=1` (verification required), `?demo_required=later` (switched
  on while the app runs), `?demo_kyc=off` (no provider yet), `?demo_manner=12` (my score). The demo's check is
  simulated in place (choose how it ends).
