# Yaari web (PC + mobile browser)

The web version of Yaari. It uses the **same Supabase server and the same rules** as the Android live app
(`chat/server`), so web and app users meet each other in random chat, rooms and friends.

- Next.js 16 (static export) + Tailwind CSS 4 + Motion. Design: v0 (Vercel) for the system and first screens,
  following the Magic Patterns mobile mockup; remaining screens written by hand in the same style.
- PC: sidebar · content · right panel (friends, live rooms). Phone browser: bottom tabs with a raised Random button.
- Dark immersive random chat, light social screens, hover lift/glow, spring presses, aurora background,
  match reveal, message pop-in, typing dots. `prefers-reduced-motion` turns animation off.

## Run

```bash
pnpm install
pnpm dev            # http://localhost:3000 — demo backend (no server, any 6-digit code)
pnpm build          # static site in out/
```

Live server: set both variables (Vercel → Project → Settings → Environment Variables), then build.

```
NEXT_PUBLIC_SUPABASE_URL=https://dgynsckrqgnpyyvqekzi.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<SUPABASE_ANON_KEY from chat/gradle.properties>
```

Without them the site runs on `lib/mockBackend.ts` (in-browser demo, "Demo preview" shown on the code step).

## Structure

```
lib/backend.ts          the Backend interface every screen uses
lib/supabaseBackend.ts  live server (same calls as the Android SupabaseBackend.kt)
lib/mockBackend.ts      in-browser demo
lib/livekit.ts          video calls (LiveKit, adaptive stream + dynacast like the app)
components/screens/     auth, random (lobby/matching/chat), rooms, recent, me, profile, settings, paywall
components/shell/       sidebar, bottom tabs, right panel
```

## Same rules as the app (enforced by the server)

Exit lock 5 s (Plus 0.2 s), report & leave always allowed, gender filter and video requests are paid,
flag from the verified phone number, profiles only visible to people you met. Purchases happen in the
Android app (Google Play); Plus then works on the web for the same account.
