// Verifies a Google Play purchase server-side and grants the entitlement.
// Secrets: GOOGLE_SERVICE_ACCOUNT_JSON (service account with Play Console "View financial data" + "Manage orders"),
//          PLAY_PACKAGE_NAME (default com.yaari.chat).
import { createClient } from "jsr:@supabase/supabase-js@2";
import { googleAccessToken, serviceAccount } from "../_shared/google.ts";

const PREMIUM = "yaari_premium_monthly"; // subscription: gender filter + video + 0.2 s skip
const GENDER_PASS = "gender_pass_24h";    // one-time, consumable: gender filter for 24 h
// Looks (040) and walls, sticker packs, incognito (046): one-time, non-consumable products named like their item, owned for good.
const LOOK = /^(badge|color|font|frame|wall|vip|cycle|effect|bubble)_[a-z0-9]{2,20}$/;
const HANDLE_PASS = "handle_change";        // consumable (042): one ID change before the 30 days are up
const MEGAPHONE = "megaphone";              // consumable (100): one line on top of everyone's screen in my country
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

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

  const { product_id, purchase_token, gift_to } = await req.json().catch(() => ({}));
  // A look bought for a friend (066): its own consumable product "gift_<item>" (Play won't sell a product twice to one
  // account, and the buyer may own the item themselves), granted to the friend; the app consumes it after the 200.
  const gift = typeof product_id === "string" && product_id.startsWith("gift_");
  const item = gift ? product_id.slice(5) : product_id;
  const look = typeof item === "string" && LOOK.test(item);
  if (gift !== (gift_to !== undefined && gift_to !== null) || (gift && (!look || typeof gift_to !== "string" || !UUID.test(gift_to)))) {
    return json(400, { error: "bad_request" });
  }
  const pass = product_id === HANDLE_PASS || product_id === MEGAPHONE;
  if ((![PREMIUM, GENDER_PASS].includes(product_id) && !look && !pass) || typeof purchase_token !== "string") return json(400, { error: "bad_request" });

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  // The shared helper (play-rtdn and push use it too): a broken secret answers 503, not a crash.
  let access: string;
  try {
    access = await googleAccessToken(serviceAccount(saJson), "https://www.googleapis.com/auth/androidpublisher");
  } catch (e) {
    console.error("verify-purchase google auth", (e as Error).message);
    return json(503, { error: "billing_not_configured" });
  }
  const api = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases`;
  const auth = { Authorization: `Bearer ${access}` };
  const tok = encodeURIComponent(purchase_token);

  // A token belongs to the first account that redeems it.
  const { data: seen } = await admin.from("purchases").select("user_id").eq("token", purchase_token).maybeSingle();
  // (A gift's token is kept under the friend it went to; redeem_gift checks that the giver is the same.)
  if (seen && seen.user_id !== user.id && !gift) return json(409, { error: "token_used_by_other_account" });

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
    const { error: grantError } = await admin.from("subscriptions").upsert({
      user_id: user.id, premium_until: item.expiryTime, purchase_token, updated_at: new Date().toISOString(),
    });
    if (grantError) return json(500, { error: "grant_failed" }); // the app retries on the next start
    if (s.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
      await fetch(`${api}/subscriptions/${PREMIUM}/tokens/${tok}:acknowledge`, { method: "POST", headers: { ...auth, "Content-Type": "application/json" }, body: "{}" });
    }
    return json(200, { premium_until: item.expiryTime });
  }

  if (look || pass) {
    // Asked of Play every time (a retry too), so a purchase whose acknowledgement was lost still gets one: Play refunds
    // unacknowledged purchases after 3 days. redeem_cosmetic and redeem_handle_pass are idempotent ('already' for the
    // same person).
    const r = await fetch(`${api}/products/${product_id}/tokens/${tok}`, { headers: auth });
    if (!r.ok) return json(402, { error: `play_${r.status}` });
    const p = await r.json();
    if (p.purchaseState !== 0) return json(402, { error: "not_purchased" });
    if (p.obfuscatedExternalAccountId !== user.id) return json(403, { error: "wrong_account" });
    const { data: result, error } = pass
      ? await admin.rpc(product_id === MEGAPHONE ? "redeem_megaphone" : "redeem_handle_pass", { p_user: user.id, p_token: purchase_token })
      : gift_to
        ? await admin.rpc("redeem_gift", { p_from: user.id, p_to: gift_to, p_item: item, p_token: purchase_token })
        : await admin.rpc("redeem_cosmetic", { p_user: user.id, p_item: product_id, p_token: purchase_token });
    if (error) return json(error.message?.includes("bad_request") ? 400 : 500, { error: error.message?.includes("bad_request") ? "unknown_item" : "grant_failed" });
    if (result === "other_account") return json(409, { error: "token_used_by_other_account" });
    // Not acknowledged, so Play refunds it within 3 days: the friend can't get it (no longer friends, or has it already).
    if (result === "not_friends" || result === "owned" || result === "bad_item") return json(409, { error: `gift_${result}` });
    if (result === "revoked") return json(402, { error: "refunded" });
    if (p.acknowledgementState === 0) {
      await fetch(`${api}/products/${product_id}/tokens/${tok}:acknowledge`, { method: "POST", headers: { ...auth, "Content-Type": "application/json" }, body: "{}" });
    }
    // A pass or a gift is consumed by the app after this 200 so it can be bought again; a look is owned for good.
    return json(200, { item, already_redeemed: result === "already" });
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
  const { error: grantError } = await admin.from("subscriptions").upsert({ user_id: user.id, gender_until: genderUntil, updated_at: new Date().toISOString() });
  if (grantError) {
    // Undo the redemption so a retry grants it; the app only consumes the pass after a 200.
    await admin.from("purchases").delete().eq("token", purchase_token).eq("user_id", user.id);
    return json(500, { error: "grant_failed" });
  }
  return json(200, { gender_until: genderUntil });
});
