// Sends one inbox notification to the person's phones through FCM HTTP v1. Called only by the database
// (push_dispatch, migration 023) with a key that lives in its vault; anyone else gets 403.
// Secret FCM_SERVICE_ACCOUNT: the Firebase service-account key (JSON). Without it nothing is sent: 200 {skipped:true}.
// The message is data only - kind, ids and the sender's name, never message text - and the app writes the
// sentence in its own language. Tokens FCM no longer knows are switched off.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json } from "../_shared/cors.ts";
import { googleAccessToken, serviceAccount } from "../_shared/google.ts";

// The app's notification channel for each kind (Android 13+: the user can mute each one).
const CHANNEL: Record<string, string> = { message: "messages", friend_request: "social", friend_accept: "social", room_invite: "rooms" };

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: ok } = await admin.rpc("edge_key_ok", { p_key: req.headers.get("x-edge-key") ?? "" });
  if (ok !== true) return json(403, { error: "forbidden" });

  // x-test-fcm: a stand-in key for a self-test of the failure paths (only with the vault key above).
  const secret = req.headers.get("x-test-fcm") ?? Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!secret) return json(200, { skipped: true });

  const { id } = await req.json().catch(() => ({}));
  if (!Number.isSafeInteger(id)) return json(400, { error: "bad_request" });
  const { data: p, error } = await admin.rpc("push_payload", { p_id: id });
  if (error) {
    console.error("push: payload", id, error.message);
    return json(500, { error: "payload_failed" });
  }
  if (!p || p.read || !p.tokens?.length) return json(200, { sent: 0 });

  let access: string, project: string;
  try {
    const sa = serviceAccount(secret);
    project = sa.project_id ?? Deno.env.get("FCM_PROJECT_ID") ?? "";
    if (!project) throw new Error("no_project_id");
    access = await googleAccessToken(sa, "https://www.googleapis.com/auth/firebase.messaging");
  } catch (e) {
    console.error("push: auth", (e as Error).message);
    return json(502, { error: (e as Error).message });
  }

  // One collapse key per chat / person / room: a newer push replaces the older one on the phone.
  const thread = p.match ?? p.room ?? p.actor;
  const data: Record<string, string> = {
    kind: p.kind, channel: CHANNEL[p.kind] ?? "social", id: String(p.id), actor: p.actor, actor_name: p.actor_name, n: String(p.n),
    ...(p.match ? { match: p.match } : {}), ...(p.room ? { room: p.room } : {}),
  };
  let sent = 0, dead = 0, failed = 0;
  for (const t of p.tokens as { token: string; platform: string }[]) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token: t.token, data,
          android: { priority: "high", collapse_key: `${p.kind}:${thread}`, ttl: p.kind === "room_invite" ? "1800s" : "86400s" },
        },
      }),
    }).catch((e) => {
      console.error("push: fetch", (e as Error).message);
      return null;
    });
    if (res?.ok) {
      sent++;
      continue;
    }
    const body = res ? await res.json().catch(() => ({})) : {};
    const code = body?.error?.details?.find?.((d: { errorCode?: string }) => d.errorCode)?.errorCode ?? body?.error?.status;
    if (res && (res.status === 404 || code === "UNREGISTERED")) {
      dead++;
      await admin.rpc("push_token_dead", { p_token: t.token });
    } else {
      failed++;
      console.error("push: send", res?.status ?? "network", code ?? "");
    }
  }
  return json(200, { sent, dead, failed });
});
