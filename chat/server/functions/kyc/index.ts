// Identity verification (migration 035): ID document + liveness selfie + face match through Didit's hosted flow.
//   POST {action: "start", returnUrl?}  (signed-in user)  -> {url}: open it; Didit sends the person back to returnUrl
//   POST {action: "check"}              (signed-in user)  -> {result}: verified | pending | declined | unreadable | minor | duplicate
//   POST <Didit webhook>                (no user)         -> 200; the session named in it is re-read from Didit
// The result is always read from Didit's API with our key, never taken from a request body, so a forged webhook can
// at most make us re-read a real session. Secrets: DIDIT_API_KEY, DIDIT_WORKFLOW_ID (the workflow with ID
// verification + liveness + face match, made in the Didit console). Without them: 503 {error: "kyc_unavailable"}.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json, preflight } from "../_shared/cors.ts";

const API = "https://verification.didit.me/v3/session/";
const KEY = Deno.env.get("DIDIT_API_KEY");
const WORKFLOW = Deno.env.get("DIDIT_WORKFLOW_ID");
// Where Didit may send people back to: the Yaari website (any of its addresses) and the app.
const RETURN_OK = [/^https:\/\/yaari-web[a-z0-9-]*\.vercel\.app\//, /^yaari:\/\//];

type Decision = {
  status?: string;
  id_verifications?: Array<Record<string, unknown> & { status?: string }>;
  liveness_checks?: Array<{ status?: string }>;
  face_matches?: Array<{ status?: string }>;
};

async function decision(session: string): Promise<Decision | null> {
  const r = await fetch(`${API}${encodeURIComponent(session)}/decision/`, { headers: { "x-api-key": KEY! } });
  if (r.status === 404) return null;
  if (!r.ok) throw new Error(`didit ${r.status}`);
  return await r.json();
}

// Approved only when the whole session and each of its checks are; anything else that is final counts as declined.
function verdict(d: Decision): { status: string; doc: Record<string, unknown> | null } {
  const doc = d.id_verifications?.[0] ?? null;
  const parts = [doc?.status, ...(d.liveness_checks ?? []).map((x) => x.status), ...(d.face_matches ?? []).map((x) => x.status)];
  const status = d.status ?? "Not Started";
  if (status === "Approved" && (!doc || parts.some((s) => s !== "Approved"))) return { status: "Declined", doc: null };
  return { status, doc };
}

Deno.serve(async (req) => {
  const pre = preflight(req);
  if (pre) return pre;
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  if (!KEY || !WORKFLOW) return json(503, { error: "kyc_unavailable" });
  const body = await req.json().catch(() => ({}));

  const apply = async (user: string, session: string) => {
    const d = await decision(session);
    if (!d) return "pending";
    const v = verdict(d);
    const { data, error } = await admin.rpc("kyc_apply", { p_user: user, p_session: session, p_status: v.status, p_doc: v.doc });
    if (error) throw new Error(error.message);
    return data as string;
  };

  try {
    // Didit's webhook: no user token, a session id in the body.
    const auth = req.headers.get("Authorization") ?? "";
    if (!body?.action && typeof body?.session_id === "string") {
      const { data: owner } = await admin.rpc("kyc_owner", { p_session: body.session_id });
      if (owner) await apply(owner as string, body.session_id);
      return json(200, { ok: true });
    }

    const { data: who } = await admin.auth.getUser(auth.replace(/^Bearer\s+/i, ""));
    const user = who?.user?.id;
    if (!user) return json(401, { error: "not_signed_in" });

    if (body.action === "start") {
      const { data: me } = await admin.from("profiles").select("id_verified_at").eq("id", user).maybeSingle();
      if (me?.id_verified_at) return json(400, { error: "already_verified" });
      const returnUrl = typeof body.returnUrl === "string" && RETURN_OK.some((r) => r.test(body.returnUrl)) ? body.returnUrl : undefined;
      const r = await fetch(API, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-api-key": KEY },
        body: JSON.stringify({ workflow_id: WORKFLOW, vendor_data: user, ...(returnUrl ? { callback: returnUrl } : {}) }),
      });
      if (!r.ok) {
        console.error("kyc: create", r.status, await r.text());
        return json(502, { error: "kyc_failed" });
      }
      const s = await r.json() as { session_id: string; url: string };
      const { error } = await admin.rpc("kyc_session_new", { p_user: user, p_session: s.session_id });
      if (error) return json(400, { error: error.message }); // already_verified | slow_down
      return json(200, { url: s.url });
    }

    if (body.action === "check") {
      const { data: session } = await admin.rpc("kyc_latest", { p_user: user });
      if (!session) return json(200, { result: "none" });
      return json(200, { result: await apply(user, session as string) });
    }
    return json(400, { error: "bad_request" });
  } catch (e) {
    console.error("kyc:", (e as Error).message);
    return json(502, { error: "kyc_failed" });
  }
});
