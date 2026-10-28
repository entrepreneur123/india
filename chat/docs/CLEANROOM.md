# Clean-room record (2026-10-28)

The owner asked for the app's weak spots to be rebuilt after studying how large apps solve them, "clean-room" and
nothing illegal. What that meant here:

- **Studied only public material**: engineering blogs, official docs and specs, open-source code under its licence,
  and behaviour anyone can see in the apps. No app was decompiled, no private API was called, no binary was unpacked.
- **Wrote our own spec** from what was learned (the "rule" column below), then wrote the code from that spec in our own
  words. No source code, UI strings or assets were copied. AGPL projects (Element, Signal) were read for ideas only.
- Anything a source didn't clearly say is marked *ours* or *unverified*.

## What was studied, and what we built from it

| Problem in Yaari | Studied (public) | Rule we took | Where it lives |
|---|---|---|---|
| A retry after a lost reply stored a message twice; a line appeared only after the server answered | Matrix client-server spec, "Transaction identifiers" and "Local echo" (spec.matrix.org); matrix-js-sdk `event-status.ts`, `scheduler.ts` (Apache-2.0); Slack engineering, "Real-time messaging" | The client names each line; the same name is stored once. The line shows at once while sending and is matched with the stored copy by that name. Lines go out one after another per chat. | `server/migrations/091_message_client_id.sql`; `use-match-chat.ts` (`merge`, `deliver`, outbox) |
| A failed send only showed a toast (the text stayed in the box) and the box was blocked while sending | Mattermost webapp (Apache-2.0): pending post id, "Retry"/"Cancel"; Element's string file (`en_EN.json`): sending / failed / resend wording | A failed line stays where it was with "Not sent · Retry · Delete"; going back online sends it again by itself | `message-bubble.tsx` (status row), `use-match-chat.ts` (`online` listener) |
| Status could go back (read → sent) when events arrive out of order | Signal Desktop `MessageSendState` (AGPL, read only): states only move forward | The stored line replaces its local echo; ticks come from the read mark, which only grows | `use-match-chat.ts` (`partnerRead` = max) |
| One photo that couldn't be signed emptied the whole chat history | — (our audit) | Sign all files in one request; a line whose file can't be signed still shows ("Shared photo") | `supabaseBackend.ts` `toMessages` |
| A Realtime socket can stay "subscribed" yet deliver nothing | Zulip `web/src/transmit.ts` (Apache-2.0, source read on GitHub): if the server acked a sent message but its event doesn't arrive within 5 s, restart the event stream | A stored 1:1 line that doesn't come back live within 5 s puts that chat back on the 10 s poll and re-syncs | `supabaseBackend.ts` (`echoed`, `lostEcho`) |
| Group chat sent only after the server answered, and could store a retry twice | Zulip `web/src/echo.ts` (Apache-2.0, source read): `local_id`, reify on the server's id, inline retry/cancel, abort | Same rules as the 1:1 chat, shared in one hook; the server stores each `(sender, client_id)` once | `server/migrations/092_group_client_id.sql`; `use-outbox.ts` (shared by 1:1 and group) |
| Group chat lost lines while the socket was down | Discord gateway docs (resume and replay); Supabase Realtime docs (Postgres Changes promise no replay) | Treat Realtime as a fast hint, the database as the truth: read the newest page again when the tab returns or the network comes back, and merge | `group-screen.tsx` |
| Group chat showed only the last 50 lines | Element `ScrollPanel.tsx` (AGPL, read only): fill on scroll, keep the reader's place | Scroll to the top loads 50 older lines (`group_history` `p_before`); the view stays on the line that was first; new lines pull down only when at the bottom or mine | `group-screen.tsx` |
| Group lines marked read while the tab was hidden | Matrix "fully read" marker; Discord read states blog | Read only while visible; mark again when shown | `group-screen.tsx` |
| 1:1 chat polled the database every 10 s per open chat | Slack "Making Slack faster by being lazy"; Supabase Realtime limits page | Poll every 10 s only while the socket is down, every 60 s while it is up | `supabaseBackend.ts` `subscribe` |
| A dropped call ended for good | LiveKit docs "Connecting" (resume vs full reconnect, disconnect reasons); livekit client-sdk-js (Apache-2.0) | LiveKit resumes short drops; when it gives up for a fixable reason, join again with a fresh token, up to 3 times, after the network is back; mic/camera as I left them. Not after leave/kick/duplicate/room closed | `lib/livekit.ts` (`FINAL`), `video-call.tsx` (`rejoin`) |
| Link viewer could host a phishing form | OWASP Content Security Policy cheat sheet (framing and form risks) | No `allow-forms` in the framed page; sign-in and payment go to "Open in browser" | `link-viewer.tsx` |
| link-meta could be scripted into a page fetcher | *ours* (no public source used); Element's "Retry after" wording shows the idea | 30 previews a minute per person, then 429 (*ours*: in-memory per instance) | `server/functions/link-meta/index.ts` |

## Checked and left as they were (with the reason)

- **Room chat rate limit race** (`voice_send2` counts recent lines without a lock): every room action first locks the
  room row in `voice_actor` (`select … for update`), so two sends to one room are already serialised.
- **DNS rebinding in link-meta**: only `https` on port 443 is fetched, so a host re-pointed at an internal address
  would also need a valid certificate for the attacker's name there.
- **Analytics loading up front**: Sentry and PostHog already load after the page's `load` event (`lib/telemetry.ts`).

## Not done yet (recorded for later)

- Voice-room chat still sends without a local echo (1:1 and group chats have it).
- Voice rooms beat every 3 s per member through the database (shared with Android and the server's lost-member
  rule). Moving presence to LiveKit or Realtime presence needs the Android app and the server changed together.
- Typing/presence channels are public by name (the match id is a random UUID). Making them private needs the
  Android app to join private channels too.
- Signed media links in a chat left open over an hour are not re-signed when they expire.

## Not verified

- KakaoTalk's unread "1": no public engineering write-up was found; nothing here is based on it.
- Azar/Hyperconnect safety features: only release notes and press, no technical source.
- The call rejoin was checked by reading the LiveKit SDK types and docs; it was not tried on two real devices.

## Design (Figma)

File `EbnxbHHvre8tPF9EJX6MEO`, page "Chat · clean-room states": the 1:1 send states (read, sent, sending, not sent),
the group chat loading older lines, and the call reconnecting, built from the Yaari colour variables and local
bubble components.
