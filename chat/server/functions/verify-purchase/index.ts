// Verifies a Google Play purchase server-side and grants the entitlement.
// Secrets: GOOGLE_SERVICE_ACCOUNT_JSON (service account with Play Console "View financial data" + "Manage orders"),
//          PLAY_PACKAGE_NAME (default com.yaari.chat).
import { createClient } from "jsr:@supabase/supabase-js@2";
import { b64url } from "../_shared/jwt.ts";

const PREMIUM = "yaari_premium_monthly"; // subscription: gender filter + video + 0.2 s skip
const GENDER_PASS = "gender_pass_24h";    // one-time, consumable: gender filter for 24 h

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

async function googleAccessToken(sa: { client_email: string; private_key: string }) {
  const now = Math.floor(Date.now() / 1000);
  const enc = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  })}`;
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${b64url(sig)}` }),
  });
  if (!res.ok) throw new Error(`google_auth_${res.status}`);
  return (await res.json()).access_token as string;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const saJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
  if (!saJson) return json(503, { error: "billing_not_configured" });
  const pkg = Deno.env.get("PLAY_PACKAGE_NAME") ?? "com.yaari.chat";

  const userClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json(401, { error: "not_signed_in" });

  const { product_id, purchase_token } = await req.json().catch(() => ({}));
  if (![PREMIUM, GENDER_PASS].includes(product_id) || typeof purchase_token !== "string") return json(400, { error: "bad_request" });

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const access = await googleAccessToken(JSON.parse(saJson));
  const api = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases`;
  const auth = { Authorization: `Bearer ${access}` };
  const tok = encodeURIComponent(purchase_token);

  // A token belongs to the first account that redeems it.
  const { data: seen } = await admin.from("purchases").select("user_id").eq("token", purchase_token).maybeSingle();
  if (seen && seen.user_id !== user.id) return json(409, { error: "token_used_by_other_account" });

  const { data: ent } = await admin.from("subscriptions").select("gender_until").eq("user_id", user.id).maybeSingle();

  if (product_id === PREMIUM) {
    const r = await fetch(`${api}/subscriptionsv2/tokens/${tok}`, { headers: auth });
    if (!r.ok) return json(402, { error: `play_${r.status}` });
    const s = await r.json();
    if (s.externalAccountIdentifiers?.obfuscatedExternalAccountId !== user.id) return json(403, { error: "wrong_account" });
    if (!["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"].includes(s.subscriptionState)) return json(402, { error: "not_active" });
    const item = (s.lineItems ?? []).find((l: { productId: string }) => l.productId === PREMIUM);
    if (!item?.expiryTime) return json(402, { error: "no_line_item" });
    if (!seen) await admin.from("purchases").insert({ token: purchase_token, user_id: user.id, product_id });
    // Only this column: a gender-pass redemption running at the same time keeps its own value.
    await admin.from("subscriptions").upsert({
      user_id: user.id, premium_until: item.expiryTime, purchase_token, updated_at: new Date().toISOString(),
    });
    if (s.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
      await fetch(`${api}/subscriptions/${PREMIUM}/tokens/${tok}:acknowledge`, { method: "POST", headers: { ...auth, "Content-Type": "application/json" }, body: "{}" });
    }
    return json(200, { premium_until: item.expiryTime });
  }

  // Gender pass: one-time, redeemed once, then consumed by the app so it can be bought again.
  // Same user retrying (lost response, or re-check at app start): success, nothing granted twice.
  if (seen) return json(200, { gender_until: ent?.gender_until ?? null, already_redeemed: true });
  const r = await fetch(`${api}/products/${GENDER_PASS}/tokens/${tok}`, { headers: auth });
  if (!r.ok) return json(402, { error: `play_${r.status}` });
  const p = await r.json();
  if (p.purchaseState !== 0) return json(402, { error: "not_purchased" });
  if (p.obfuscatedExternalAccountId !== user.id) return json(403, { error: "wrong_account" });
  const { error: dup } = await admin.from("purchases").insert({ token: purchase_token, user_id: user.id, product_id });
  if (dup) {
    // Lost a race: success only if the winner was this same user (a parallel retry).
    const { data: owner } = await admin.from("purchases").select("user_id").eq("token", purchase_token).maybeSingle();
    if (owner?.user_id !== user.id) return json(409, { error: "token_used_by_other_account" });
    return json(200, { gender_until: ent?.gender_until ?? null, already_redeemed: true });
  }
  const base = Math.max(Date.now(), ent?.gender_until ? Date.parse(ent.gender_until) : 0);
  const genderUntil = new Date(base + 24 * 3600 * 1000).toISOString();
  await admin.from("subscriptions").upsert({ user_id: user.id, gender_until: genderUntil, updated_at: new Date().toISOString() });
  return json(200, { gender_until: genderUntil });
});
