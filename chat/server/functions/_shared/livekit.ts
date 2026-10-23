// LiveKit's server API (RoomService) over Twirp: plain JSON POSTs, no SDK.
// https://docs.livekit.io/reference/server/server-apis/

/** wss://x.livekit.cloud → https://x.livekit.cloud */
export const httpHost = (url: string) => url.replace(/^ws(s?):\/\//, "http$1://").replace(/\/+$/, "");

export interface Participant {
  identity: string;
  permission?: Record<string, unknown>;
  tracks?: { sid: string; source?: string | number; muted?: boolean }[];
}

/** Calls one RoomService method; returns the HTTP status and the parsed body (Twirp errors carry {code, msg}). */
export async function roomService(host: string, token: string, method: string, body: Record<string, unknown>) {
  const res = await fetch(`${host}/twirp/livekit.RoomService/${method}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data: data as Record<string, unknown> };
}

/** Twirp JSON uses the proto field names; accept the camelCase spelling too. */
export const canPublish = (p: Participant) => Boolean(p.permission?.can_publish ?? p.permission?.canPublish ?? false);
export const isMic = (t: { source?: string | number }) => t.source === "MICROPHONE" || t.source === 2;
