import { jwtVerify } from "npm:jose@5.9.6";
import { livekitAdminToken, livekitToken, livekitVoiceToken } from "./jwt.ts";

Deno.test("LiveKit token verifies with the secret and carries the right grant", async () => {
  const now = Math.floor(Date.now() / 1000);
  const t = await livekitToken("APIkey123", "s3cret-ünïcode", "match-1", "user-1", now);
  const { payload, protectedHeader } = await jwtVerify(t, new TextEncoder().encode("s3cret-ünïcode"), { issuer: "APIkey123" });
  if (protectedHeader.alg !== "HS256") throw new Error("alg");
  if (payload.sub !== "user-1" || payload.exp !== now + 300) throw new Error("claims");
  const v = payload.video as Record<string, unknown>;
  if (v.room !== "match-1" || v.roomJoin !== true || v.canPublishData !== false) throw new Error("grant");
});

Deno.test("Token signed with another secret is rejected", async () => {
  const t = await livekitToken("k", "right", "r", "u");
  let failed = false;
  try { await jwtVerify(t, new TextEncoder().encode("wrong")); } catch { failed = true; }
  if (!failed) throw new Error("accepted a bad signature");
});

Deno.test("Voice token: listeners subscribe only; speakers publish the microphone and camera only", async () => {
  const now = Math.floor(Date.now() / 1000);
  const listener = await jwtVerify(await livekitVoiceToken("k", "s", "voice-v-1", "u", false, now), new TextEncoder().encode("s"));
  const l = listener.payload.video as Record<string, unknown>;
  if (l.canPublish !== false || l.canSubscribe !== true || l.canPublishData !== false || (l.canPublishSources as string[]).length) throw new Error("listener grant");
  const speaker = await jwtVerify(await livekitVoiceToken("k", "s", "voice-v-1", "u", true, now), new TextEncoder().encode("s"));
  const s = speaker.payload.video as Record<string, unknown>;
  if (s.canPublish !== true || JSON.stringify(s.canPublishSources) !== '["microphone","camera"]' || s.room !== "voice-v-1") throw new Error("speaker grant");
});

Deno.test("Admin token is limited to one room", async () => {
  const { payload } = await jwtVerify(await livekitAdminToken("k", "s", "voice-v-1"), new TextEncoder().encode("s"));
  const v = payload.video as Record<string, unknown>;
  if (v.room !== "voice-v-1" || v.roomAdmin !== true || v.roomJoin) throw new Error("admin grant");
});
