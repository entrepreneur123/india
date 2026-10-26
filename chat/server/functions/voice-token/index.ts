// Issues a LiveKit token for a voice room: room "voice-<id>", identity = user id. Only someone present in the
// room (not removed, not banned) gets one; only people on a seat may publish, and only their microphone and camera.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json, preflight } from "../_shared/cors.ts";
import { livekitVoiceToken } from "../_shared/jwt.ts";

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

  const { room_id } = await req.json().catch(() => ({}));
  if (typeof room_id !== "string" || !/^v-[0-9a-f]{16}$/.test(room_id)) return json(400, { error: "bad_request" });

  // voice_grant answers only for the caller (auth.uid()), and only while they are in the live room.
  const { data: rows, error } = await supabase.rpc("voice_grant", { p_room: room_id });
  if (error) return json(500, { error: "server_error" });
  const grant = (rows as { role: string; seat: number | null }[] | null)?.[0];
  if (!grant) return json(403, { error: "not_allowed" });

  const publish = grant.seat !== null;
  const token = await livekitVoiceToken(apiKey, apiSecret, `voice-${room_id}`, user.id, publish);
  return json(200, { url, token, canPublish: publish });
});
