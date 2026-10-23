// Google Play refunds and revocations end Plus / the gender pass at once (play_revoke, migration 025).
//
// 1. Real-time developer notifications (Pub/Sub push subscription to this URL). Accepted only with proof it is
//    Pub/Sub, either:
//    - OIDC (preferred): the push subscription sends a Google-signed ID token. Secrets PLAY_RTDN_AUDIENCE (the
//      audience set on the subscription, e.g. this function's URL) and optionally PLAY_RTDN_SA_EMAIL (the
//      subscription's service account) are checked.
//    - or a shared secret: the push URL ends in ?token=<PLAY_RTDN_TOKEN>.
//    Neither secret set: every call is refused (401). SUBSCRIPTION_REVOKED (12) and voided purchases call
//    play_revoke; everything else is acknowledged and ignored (verify-purchase reads the rest from Play).
// 2. ?mode=voided (pg_cron, daily, with the database's vault key): reads Play's Voided Purchases API for the last
//    3 days and revokes each one. Needs PLAY_SERVICE_ACCOUNT (or GOOGLE_SERVICE_ACCOUNT_JSON, as verify-purchase);
//    without it: 200 {skipped:true}.
// PLAY_PACKAGE_NAME defaults to com.yaari.chat.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json } from "../_shared/cors.ts";
import { googleAccessToken, serviceAccount, verifyGoogleIdToken } from "../_shared/google.ts";

const REVOKED = 12;

const equal = (a: string, b: string) => {
  const x = new TextEncoder().encode(a), y = new TextEncoder().encode(b);
  if (x.length !== y.length) return false;
  let d = 0;
  for (let i = 0; i < x.length; i++) d |= x[i] ^ y[i];
  return d === 0;
};

async function fromPubSub(req: Request, url: URL): Promise<boolean> {
  const audience = Deno.env.get("PLAY_RTDN_AUDIENCE");
  const shared = Deno.env.get("PLAY_RTDN_TOKEN");
  const bearer = req.headers.get("Authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (audience && bearer && await verifyGoogleIdToken(bearer, audience, Deno.env.get("PLAY_RTDN_SA_EMAIL") || undefined).catch(() => null)) return true;
  const given = url.searchParams.get("token");
  return !!shared && shared.length >= 16 && !!given && equal(given, shared);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const url = new URL(req.url);
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const pkg = Deno.env.get("PLAY_PACKAGE_NAME") ?? "com.yaari.chat";

  const revoke = async (token: string, reason: string) => {
    const { data, error } = await admin.rpc("play_revoke", { p_token: token, p_reason: reason });
    if (error) throw new Error(`revoke_failed: ${error.message}`);
    console.log("play-rtdn: revoke", reason, JSON.stringify(data));
    return data as { found: boolean };
  };

  if (url.searchParams.get("mode") === "voided") {
    const { data: ok } = await admin.rpc("edge_key_ok", { p_key: req.headers.get("x-edge-key") ?? "" });
    if (ok !== true) return json(403, { error: "forbidden" });
    const secret = Deno.env.get("PLAY_SERVICE_ACCOUNT") ?? Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
    if (!secret) return json(200, { skipped: true });
    try {
      const access = await googleAccessToken(serviceAccount(secret), "https://www.googleapis.com/auth/androidpublisher");
      const start = Date.now() - 3 * 24 * 3600_000;
      let page: string | undefined, seen = 0, revoked = 0;
      do {
        const q = new URLSearchParams({ startTime: String(start), type: "1", maxResults: "1000", ...(page ? { token: page } : {}) });
        const r = await fetch(`https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/voidedpurchases?${q}`,
          { headers: { Authorization: `Bearer ${access}` } });
        if (!r.ok) throw new Error(`play_${r.status}`);
        const body = await r.json();
        for (const v of body.voidedPurchases ?? []) {
          seen++;
          if ((await revoke(v.purchaseToken, "voided")).found) revoked++;
        }
        page = body.tokenPagination?.nextPageToken;
      } while (page);
      return json(200, { seen, revoked });
    } catch (e) {
      console.error("play-rtdn: voided", (e as Error).message);
      return json(502, { error: (e as Error).message });
    }
  }

  if (!(await fromPubSub(req, url))) return json(401, { error: "unauthorized" });
  const body = await req.json().catch(() => null);
  let n: Record<string, any> | null = null;
  try {
    n = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(body?.message?.data ?? ""), (c) => c.charCodeAt(0))));
  } catch {
    n = null;
  }
  // Malformed or another app's: acknowledged (200), or Pub/Sub would retry it for a week.
  if (!n || n.packageName !== pkg) return json(200, { ignored: true });
  try {
    if (n.subscriptionNotification?.notificationType === REVOKED && n.subscriptionNotification.purchaseToken) {
      await revoke(n.subscriptionNotification.purchaseToken, "revoked");
    } else if (n.voidedPurchaseNotification?.purchaseToken) {
      await revoke(n.voidedPurchaseNotification.purchaseToken, "voided");
    }
  } catch (e) {
    console.error("play-rtdn:", (e as Error).message);
    return json(500, { error: "retry" }); // Pub/Sub retries
  }
  return json(200, { ok: true });
});
