# Yaari 0.15: IDs, looks you buy, rooms that fit (spec shared by web and Android)

The owner's requests (2026-10-22), and how each is built. Server: `server/migrations/040_handles_looks.sql` (live),
`server/functions/verify-purchase` (v10, live). Web and Android implement the same screens and the same designs.

## 1. Voice rooms

1. **Leaving the stage.** On the stage the only control was the microphone; "Leave stage" was hidden in my own person
   sheet. Now the control bar shows, next to the mic, a clearly labelled button **"Leave stage"** (icon: arrow down
   from a line / `ArrowDownToLine`) that sends the existing `unseat` action. The host's seat 0 too (the host can step
   down and come back with "Join voice").
2. **Inviting friends, not codes.** The header's main action is a labelled **"Invite"** button (person-plus icon +
   text on wide screens, icon on phones) that opens the friends invite sheet (the existing `invite_to_room` RPC).
   The private-room code chip leaves the header. In the invite sheet the friends list comes first; "Copy link" is
   a secondary row under it. The code is only in the host's ⋮ options ("Copy room code") for a private room.
   Android's "room created" dialog: primary button **"Invite friends"** (opens the invite sheet), secondary
   "Share link"; the code is a small line, not the hero.
3. **Room layout follows the room.** Talk rooms: the chat is the big part (top block — stage strip, listeners — about
   30 % of the height on phones). Music rooms: the player and stage are big (about 55 %). And people can change it:
   - **Phones**: a drag handle (a 36×4 pill in a 24 px tall touch strip) between the top block and the chat. Drag
     down = bigger top, up = bigger chat. Snap points: chat-only (the top folds to a one-line strip with the
     speakers' faces), 30 %, 50 %, 70 %. Double-tap the handle toggles chat-only ↔ the room's default.
   - **Wide screens / tablets**: a vertical splitter between the room column and the chat column, dragged with the
     mouse or touch (cursor `col-resize`, keyboard: ←/→ by 32 px when focused, `role="separator"` +
     `aria-valuenow`). Chat column 300 px … 60 % of the width.
   - The choice is remembered per category (talk / music) on the device (web: `localStorage` key
     `yaari_room_split_<talk|music>_<phone|wide>`, Android: `SharedPreferences`).
4. **Who's here.** The header's people count opens a **People** sheet: everyone in the room (on stage first, then
   listeners), with search. Each row: face, name, decorated @ID (§3), role badge, and a friend button with the real
   state (`Add friend` / `Requested` / `Accept` / `Friends`), using the existing friend RPCs. A tap on the row opens the
   person's profile (the profile sheet has Add friend too). The person sheet in a room also gets **Add friend**.

## 2. Recent chats: friends to keep talking

The server now refuses a new line in an ended random chat unless the two are friends (RLS `chat_open`). The history
stays readable. In a thread whose chat ended and who isn't a friend, the composer is replaced by a bar:
"Chat ended. Add {name} as a friend to keep talking." + the friend button (Add friend / Requested — "waiting for
{name}" / Accept). Once friends, the composer comes back (re-check friend state when the screen resumes and after
the button). A live random chat is unchanged.

## 3. IDs (handles)

- Every profile has `handle` (unique, case-blind) apart from `name`. Shown as `@handle`.
- Rules (server is the judge, apps mirror them for instant feedback): 2–24 characters after trimming and Unicode NFC;
  letters of real scripts (Latin incl. accents, Greek, Cyrillic, Armenian, Hebrew, Arabic/Urdu/Persian, all Indic
  scripts, Sinhala, Thai, Lao, Myanmar, Georgian, Ethiopic, Khmer, Japanese kana, Hangul, CJK), digits 0-9 and the
  ASCII specials `! # $ % & ( ) * + , - . / : ; = ? [ ] ^ _ { | } ~`. **No emoji, no spaces, no `@ " ' \` < > \`**, no
  "fancy font" look-alikes (𝓛𝓲𝓼𝓪, Ｌｉｓａ, ʟɪsᴀ, ⓛⓘⓢⓐ, upside-down letters, Cherokee/Canadian look-alikes). At least one
  letter or digit. Latin, Greek and Cyrillic may not be mixed in one ID. Reserved: anything starting `yaari`, `admin`,
  `official`, `moderator`, and exactly `support staff help team system security root null undefined`.
  Exact code-point ranges: `handle_valid()` in migration 040 — copy them, don't approximate.
- RPCs: `handle_check(p_handle) → 'ok' | 'mine' | 'invalid' | 'reserved' | 'taken'` (debounce 400 ms while typing),
  `set_handle(p_handle) → text` (the stored spelling). Errors (P0001 message): `handle_invalid`, `handle_reserved`,
  `handle_taken`, `handle_cooldown` (a different ID once every 7 days; the app-given free ID can be changed at once;
  changing only upper/lower case is always allowed). `shop().handle_next` = when the next change is allowed (null:
  now).
- Every profile already has a free ID (`lisa_4821`, `user_0042`). Sign-up asks for an ID (field under the name,
  prefilled with a suggestion from the name, live check). The profile row is created first, then `set_handle`; if
  the chosen one was taken in between, keep the free one and tell the person to change it in Edit profile.
- Edit profile: an "ID" field with the same live check and the cooldown message ("You can change your ID again on
  {date}").
- Names: the server now refuses fancy-font letters in names too (`name_invalid`); emoji in names stay allowed. Show a
  clear message for `name_invalid` on sign-up and Edit profile.

## 4. Looks (bought, seen by everyone)

Server:
- `shop() → { items: [{id, kind, price_inr}], owned: [id], look: {id, handle, badge, color, font, frame}, handle_next }`
- `equip_look(p_badge, p_color, p_font, p_frame) → look` — sets all four at once (null = none of that kind); errors
  `not_owned`, `bad_request`.
- `looks(p_users uuid[]) → [{id, handle, badge, color, font, frame}]` for up to 100 people I may see (same rule as
  `trust()`); batch it exactly like the trust badge lookup (all names on screen in one call per frame, cached ~2 min).
- Purchase (Android, Google Play): one-time **non-consumable** in-app products whose product id equals the item id
  (`badge_crown`…). After `PURCHASED`, call edge function `verify-purchase` with `{product_id, purchase_token}`; 200 =
  owned (`{item, already_redeemed}`), then refresh `shop()`. **Never consume** these (they are owned for good); on app
  start, `queryPurchases(INAPP)` and re-send any look token not yet owned (lost responses). Play shows the real local
  price (`ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`); `price_inr` is only the fallback label. The
  first item of a kind is worn automatically by the server.
- Web has no payments: items not owned show the price and "Get it in the Yaari app" (link to the Play listing
  `https://play.google.com/store/apps/details?id=com.yaari.chat`); owned items can be worn on the web.
- Demo builds (mock/fake backend): buying is instant, like the existing demo payments.

Catalogue (ids, names to show, design). Every design is drawn in code (SVG on web, Compose `Canvas`/`Path` on
Android) — no image files — the same shapes and colours on both.

### Badges (beside the @ID, height = 1.0× the ID's font size, 2 px gap)
Redrawn 2026-10-28 as one family of insignia (source of truth: `web/components/looks/badges.tsx`): a pointy-top hexagon
pin, 24 units, rim r 10.6 (metal gradient, 1.6-unit round-join stroke for the rounded corners), enamel face r 8.7 with a
0.5-unit dark inner line and a faint light band at its top, the symbol in a vertical gradient, a light band sweeping across.
| id | name | rim metal | face | symbol |
|---|---|---|---|---|
| `badge_crown` | Crown | gold `#FFF4C2 #E8B84A #9A6B12` | `#3B2606 → #140C02` | crown + band, `#FFF1B8 → #F2C14E` |
| `badge_diamond` | Diamond | silver `#FFFFFF #C3CAD5 #6E7787` | `#0E3160 → #05142B` | brilliant with facet lines, `#FFFFFF → #9FE3FF` |
| `badge_flame` | Flame | copper `#FFD9BF #D9824F #7E3A17` | `#45100A → #1C0503` | flame, `#FFE08A → #FF6A1A` |
| `badge_star` | Star | silver | `#25215E → #0E0B2B` | 5-point star, `#FFF4C2 → #F2C14E` |
| `badge_heart` | Heart | rose `#FFE6E1 #E7A59A #9E5A50` | `#4F0A25 → #220410` | heart, `#FFC2D6 → #FF4D8D` |
| `badge_bolt` | Bolt | graphite `#C9CED6 #6B7280 #252A33` | `#1A1F2B → #07090D` | bolt, `#FFF7A8 → #FACC15` |
| `badge_moon` | Moon | silver | `#152042 → #060B1C` | crescent + small star, `#FFFFFF → #C9D4FF` |
| `badge_lotus` | Lotus | gold | `#3E0A4F → #190322` | three petals, `#FFD6EC → #F062A8` |

### ID colours (the @ID text)
| id | name | light theme | dark theme |
|---|---|---|---|
| `color_gold` | Gold | `#A87400` | `#FFD54A` |
| `color_rose` | Rose | `#C2185B` | `#FF8AB3` |
| `color_ocean` | Ocean | `#0277BD` | `#4FC3F7` |
| `color_emerald` | Emerald | `#00796B` | `#4DD0A8` |
| `color_violet` | Violet | `#6A1B9A` | `#CE93D8` |
| `color_sunset` | Sunset | gradient `#FF6A3D → #FF2E8B → #A63DFF` (both themes) | same |
| `color_aurora` | Aurora | gradient `#00C9A7 → #3D8BFF → #B44DFF` | same |
| `color_rainbow` | Rainbow | gradient `#FF4D4D #FFA53D #F5D300 #34C759 #3DB8FF #9B5CFF`, sliding slowly (6 s loop; static with reduced motion) | same |
| `color_platinum` | Platinum | gradient `#2E3746 #7D8899 #2E3746 #4A5566`, sliding like the rainbow | same |
| `color_cyber` | Cyber | gradient `#0096C7 → #7C3AED → #DB2777` | same |
Gradients are horizontal across the text (web: `background-clip: text`; Compose: `TextStyle(brush = …)`).

### ID fonts (bundled; Latin letters only, other scripts fall back to the app font automatically)
| id | name | file (web `public/fonts/looks/*.woff2`, Android `res/font/*.ttf`) | family name inside the file |
|---|---|---|---|
| `font_script` | Script | `look_script` | Yaari Look Script |
| `font_retro` | Retro | `look_retro` | Yaari Look Retro |
| `font_bold` | Bold | `look_bold` | Yaari Look Bold |
| `font_pixel` | Pixel | `look_pixel` (draw at 0.8× size: it is wide) | Yaari Look Pixel |
| `font_round` | Round | `look_round` | Yaari Look Round |
| `font_future` | Future | `look_future` | Yaari Look Future |
| `font_elegant` | Elegant | `look_elegant` (Playfair Display Bold Italic) | Yaari Look Elegant |
| `font_classic` | Classic | `look_classic` (Cinzel Bold, 0.95×) | Yaari Look Classic |
| `font_hand` | Handwritten | `look_hand` (Caveat Bold, 1.15×) | Yaari Look Hand |
| `font_mono` | Mono | `look_mono` (Space Mono Bold, 0.92×) | Yaari Look Mono |
| `font_neon` | Neon | `look_neon` (Monoton, 0.9×) | Yaari Look Neon |
| `font_sport` | Sport | `look_sport` (Bebas Neue, 1.15×) | Yaari Look Sport |
Web: one `@font-face` per font with `font-display: swap`; the browser downloads a file only when a name in that font
is on screen (no preload). Licences: `docs/font-licenses/` (OFL 1.1; the subsets are renamed as the OFL requires).
People can't bring their own font: names and IDs refuse the fancy-font Unicode letters (server), and nothing else in
the app lets a person choose a font.

### Frames (the profile: the hero card and the photo ring)
A frame replaces the old free cover photo and colour. It draws: (a) a 3 px gradient ring around the profile card
(radius = the card's), (b) a 3 px ring around the big profile photo, (c) a soft background wash at the top of the card
(top → transparent at 60 % height), (d) a light particle animation inside the card behind the content (≤ 30
particles, 30 fps cap, paused when off screen / app in background; with reduced motion: particles drawn still).
| id | name | ring gradient | wash light / dark | particles |
|---|---|---|---|---|
| `frame_frost` | Frozen | `#E3F6FF → #8FD3FF → #4AA8FF` | `#DFF4FF` / `#0B2A44` | 18 six-arm snowflakes (4–9 px, white 90 % with `#8FD3FF` stroke) falling slowly, slight sway |
| `frame_fire` | Blaze | `#FFD54A → #FF6A00 → #D50000`, flickering (opacity 0.8–1, 0.6 s) | `#FFF1E6` / `#2A0D05` | 22 embers (2–4 px, `#FFB300`/`#FF6A00`) rising and fading |
| `frame_jewel` | Jewels | `#FFE8A3 → #D4A017 → #FFE8A3` with 8 round gems on the ring (`#E0115F`, `#0F52BA`, `#50C878`, repeating) | `#FFF8E1` / `#2A2208` | 12 four-point sparkles twinkling in place |
| `frame_sand` | Sandstorm | `#F4D9A6 → #C99A5B → #8B5E34` | `#FFF6E6` / `#2B1E0E` | 30 sand grains (1–3 px, `#C99A5B`/`#E6C48F`) blowing left→right with a wave, plus 3 faint streaks |
| `frame_neon` | Neon | `#00F0FF → #FF00E5`, glow (blur 8 px) pulsing 2 s | `#E9FDFF` / `#100A24` | none (the glow pulses) |
| `frame_sakura` | Sakura | `#FFD1E1 → #FF8AB3` | `#FFF0F5` / `#2A1220` | 14 petals (5-lobe-less teardrop, `#FFB7CF`) falling with rotation |

### Where an ID and its look show
A shared component (web `components/looks/look-name.tsx` `<LookName userId | profile …>`, Android
`ui/Looks.kt` `@Composable LookName(…)`) draws `@handle` in the person's colour and font with their badge after it
(+ the verified mark where names already show one). Use it in: the profile hero (big plain name, then the decorated
@ID under it), the voice room person sheet, the People sheet, the room chat sender label (the decorated @ID replaces
the plain name there), the friends and recent lists (a second line), the random chat header (under the name), and
the invite sheet rows. People without a look show a plain `@handle` in the secondary text colour.

### The Looks shop
From Me (button **"Looks"** with a sparkle icon, where the old "Style" button was) and from Edit profile. A sheet /
full screen with: a live preview on top (my hero card with the frame, my name and decorated @ID, using the selection
being tried), four tabs **Badges · Colours · Fonts · Frames**, a grid of item cards (each card previews the item on my
@ID or, for frames, a small framed avatar). Tapping a card tries it in the preview. Owned → "Wear" / "Wearing" (one
per kind, "None" card first in each tab to take it off), saved with `equip_look`. Not owned → the price button
(Android: Play purchase; demo: instant; web: "Get it in the app"). Show "Everyone sees your look" under the preview.

## 5. Gone

- The Feed tab (web nav and Android bottom bar). Posts stay on profiles (Me → new post, the profile's post grid).
- The free cover photo and accent colour in the profile style editor (the server ignores them since 040). The editor
  keeps status, languages and prompts and is renamed "Profile details".
