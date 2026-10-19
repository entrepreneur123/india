import { jwtVerify } from "npm:jose@5.9.6";
import { livekitToken } from "./jwt.ts";

Deno.test("LiveKit token verifies with the secret and carries the right grant", async () => {
  const now = Math.floor(Date.now() / 1000);
  const t = await livekitToken("APIkey123", "s3cret-ünïcode", "match-1", "user-1", now);
  const { payload, protectedHeader } = await jwtVerify(t, new TextEncoder().encode("s3cret-ünïcode"), { issuer: "APIkey123" });
  if (protectedHeader.alg !== "HS256") throw new Error("alg");
  if (payload.sub !== "user-1" || payload.exp !== now + 900) throw new Error("claims");
  const v = payload.video as Record<string, unknown>;
  if (v.room !== "match-1" || v.roomJoin !== true || v.canPublishData !== false) throw new Error("grant");
});

Deno.test("Token signed with another secret is rejected", async () => {
  const t = await livekitToken("k", "right", "r", "u");
  let failed = false;
  try { await jwtVerify(t, new TextEncoder().encode("wrong")); } catch { failed = true; }
  if (!failed) throw new Error("accepted a bad signature");
});
