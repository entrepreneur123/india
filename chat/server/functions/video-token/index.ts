// Issues a LiveKit join token for one accepted video call. Room = match id, identity = user id.
// Secrets (Supabase dashboard > Edge Functions > Secrets): LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { livekitToken } from "../_shared/jwt.ts";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const url = Deno.env.get("LIVEKIT_URL"), apiKey = Deno.env.get("LIVEKIT_API_KEY"), apiSecret = Deno.env.get("LIVEKIT_API_SECRET");
  if (!url || !apiKey || !apiSecret) return json(503, { error: "video_not_configured" });

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return json(401, { error: "not_signed_in" });

  const { match_id } = await req.json().catch(() => ({}));
  if (typeof match_id !== "string") return json(400, { error: "bad_request" });

  // RLS returns the row only to the two participants.
  const { data: m } = await supabase.from("matches").select("id, ended_at, video_status").eq("id", match_id).maybeSingle();
  if (!m || m.ended_at || m.video_status !== "accepted") return json(403, { error: "not_allowed" });

  const token = await livekitToken(apiKey, apiSecret, match_id, user.id);
  return json(200, { url, token });
});
