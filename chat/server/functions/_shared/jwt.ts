export const b64url = (data: Uint8Array | string) => {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

export async function signHs256(payload: Record<string, unknown>, secret: string) {
  const head = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = b64url(JSON.stringify(payload));
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${head}.${body}`)));
  return `${head}.${body}.${b64url(sig)}`;
}

/** LiveKit join token: room = match id, identity = user id, 5 minutes (LiveKit refreshes it while the call is connected). */
export function livekitToken(apiKey: string, apiSecret: string, room: string, identity: string, nowSec = Math.floor(Date.now() / 1000)) {
  return signHs256({
    iss: apiKey, sub: identity, nbf: nowSec, exp: nowSec + 5 * 60,
    video: { room, roomJoin: true, canPublish: true, canSubscribe: true, canPublishData: false },
  }, apiSecret);
}

/**
 * Voice room token: room = "voice-<id>", identity = user id. Everyone listens; only people on a seat may send
 * from the microphone and the camera (24; no screen, no data). LiveKit keeps it fresh while connected,
 * carrying any permission voice-sync set since.
 */
export function livekitVoiceToken(
  apiKey: string, apiSecret: string, room: string, identity: string, canPublish: boolean, nowSec = Math.floor(Date.now() / 1000),
) {
  return signHs256({
    iss: apiKey, sub: identity, nbf: nowSec, exp: nowSec + 5 * 60,
    video: {
      room, roomJoin: true, canSubscribe: true, canPublish, canPublishData: false,
      canPublishSources: canPublish ? ["microphone", "camera"] : [],
    },
  }, apiSecret);
}

/** Server-side token for the RoomService API (voice-sync), limited to one room, valid for a minute. */
export function livekitAdminToken(apiKey: string, apiSecret: string, room: string, nowSec = Math.floor(Date.now() / 1000)) {
  return signHs256({ iss: apiKey, sub: "yaari-voice-sync", nbf: nowSec, exp: nowSec + 60, video: { room, roomAdmin: true, roomCreate: true } }, apiSecret);
}
