// Applies the voice room's state (Postgres) to the people connected in LiveKit: after someone is brought up to
// a seat or sent back to the audience (publish allowed or not), muted by a host, removed, or when the room ends.
// Called by the apps right after the change: by the person concerned, or by the room's host/co-host for anyone
// ({room_id, user_id?, mute?}; without user_id the host/co-host reconciles everyone in the room).
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json, preflight } from "../_shared/cors.ts";
import { livekitAdminToken } from "../_shared/jwt.ts";
import { canPublish, httpHost, isMic, type Participant, roomService } from "../_shared/livekit.ts";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

type Want = { user_id: string; publish: boolean; remove: boolean; muted: boolean; ended: boolean };

Deno.serve(async (req) => {
  const pre = preflight(req);
  if (pre) return pre;
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const url = Deno.env.get("LIVEKIT_URL"), apiKey = Deno.env.get("LIVEKIT_API_KEY"), apiSecret = Deno.env.get("LIVEKIT_API_SECRET");
  if (!url || !apiKey || !apiSecret) return json(503, { error: "video_not_configured" });

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return json(401, { error: "not_signed_in" });

  const { room_id, user_id, mute } = await req.json().catch(() => ({}));
  if (typeof room_id !== "string" || !/^v-[0-9a-f]{16}$/.test(room_id)) return json(400, { error: "bad_request" });
  if (user_id !== undefined && user_id !== null && (typeof user_id !== "string" || !UUID.test(user_id))) {
    return json(400, { error: "bad_request" });
  }

  // The database decides who may ask (the person, or a host/co-host) and what LiveKit should allow.
  const { data, error } = await supabase.rpc("voice_sync_info", { p_room: room_id, p_user: user_id ?? null });
  if (error) return json(error.message.includes("not_allowed") ? 403 : 500, { error: error.message.includes("not_allowed") ? "not_allowed" : "server_error" });
  const want = new Map((data as Want[]).map((w) => [w.user_id, w]));

  const host = httpHost(url);
  const room = `voice-${room_id}`;
  const token = await livekitAdminToken(apiKey, apiSecret, room);
  const actions: { op: string; identity?: string; status: number }[] = [];

  // An ended room: everyone out at once.
  if (!user_id && [...want.values()].some((w) => w.ended)) {
    const r = await roomService(host, token, "DeleteRoom", { room });
    actions.push({ op: "DeleteRoom", status: r.status });
    return json(200, { ok: r.status === 200 || r.status === 404, actions });
  }

  const list = await roomService(host, token, "ListParticipants", { room });
  actions.push({ op: "ListParticipants", status: list.status });
  // 404: nobody has connected yet, so there is nothing to change.
  if (list.status !== 200) return json(list.status === 404 ? 200 : 502, { ok: list.status === 404, actions });
  const people = ((list.data.participants as Participant[] | undefined) ?? []).filter((p) => !user_id || p.identity === user_id);

  for (const p of people) {
    const w = want.get(p.identity);
    if (!w || w.remove) {
      const r = await roomService(host, token, "RemoveParticipant", { room, identity: p.identity });
      actions.push({ op: "RemoveParticipant", identity: p.identity, status: r.status });
      continue;
    }
    if (canPublish(p) !== w.publish) {
      const r = await roomService(host, token, "UpdateParticipant", {
        room, identity: p.identity,
        permission: {
          can_subscribe: true, can_publish: w.publish, can_publish_data: false,
          can_publish_sources: w.publish ? ["MICROPHONE", "CAMERA"] : [],
        },
      });
      actions.push({ op: "UpdateParticipant", identity: p.identity, status: r.status });
    }
    // A host's "mute": the track goes quiet on the server; the person can unmute it again.
    if (mute === true && w.muted && p.identity === user_id) {
      for (const t of (p.tracks ?? []).filter((t) => isMic(t) && !t.muted)) {
        const r = await roomService(host, token, "MutePublishedTrack", { room, identity: p.identity, track_sid: t.sid, muted: true });
        actions.push({ op: "MutePublishedTrack", identity: p.identity, status: r.status });
      }
    }
  }
  return json(200, { ok: actions.every((a) => a.status === 200), actions });
});
