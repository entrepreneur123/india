// Google service-account access tokens (FCM, Play Developer API) and Google-signed ID tokens (Pub/Sub push).
import { b64url } from "./jwt.ts";

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id?: string;
}

/** Parses the secret; throws "bad_service_account" when it isn't a service-account key. */
export function serviceAccount(json: string): ServiceAccount {
  let sa: Partial<ServiceAccount>;
  try {
    sa = JSON.parse(json);
  } catch {
    throw new Error("bad_service_account");
  }
  if (typeof sa.client_email !== "string" || typeof sa.private_key !== "string") throw new Error("bad_service_account");
  return sa as ServiceAccount;
}

const tokens = new Map<string, { token: string; until: number }>();

/** OAuth access token for `scope`, reused for 50 minutes. */
export async function googleAccessToken(sa: ServiceAccount, scope: string): Promise<string> {
  const cacheKey = `${sa.client_email} ${scope}`;
  const hit = tokens.get(cacheKey);
  if (hit && hit.until > Date.now()) return hit.token;
  const now = Math.floor(Date.now() / 1000);
  const enc = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({
    iss: sa.client_email, scope, aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  })}`;
  let key: CryptoKey;
  try {
    const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
    const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  } catch {
    throw new Error("bad_service_account");
  }
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${b64url(sig)}` }),
  });
  if (!res.ok) throw new Error(`google_auth_${res.status}`);
  const token = (await res.json()).access_token as string;
  tokens.set(cacheKey, { token, until: Date.now() + 50 * 60_000 });
  return token;
}

const b64urlDecode = (s: string) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));

let certs: { keys: (JsonWebKey & { kid: string })[]; until: number } | null = null;

/**
 * Verifies a Google-signed OIDC ID token (what Pub/Sub push sends as "Authorization: Bearer ..."): RS256 with one of
 * Google's current keys, issuer accounts.google.com, the expected audience, not expired, and (when given) the
 * expected service-account email. Returns the claims, or null.
 */
export async function verifyGoogleIdToken(jwt: string, audience: string, email?: string): Promise<Record<string, unknown> | null> {
  const parts = jwt.split(".");
  if (parts.length !== 3) return null;
  let head: { alg?: string; kid?: string }, claims: Record<string, unknown>;
  try {
    head = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[1])));
  } catch {
    return null;
  }
  if (head.alg !== "RS256" || !head.kid) return null;
  if (!certs || certs.until < Date.now() || !certs.keys.some((k) => k.kid === head.kid)) {
    const res = await fetch("https://www.googleapis.com/oauth2/v3/certs");
    if (!res.ok) return null;
    certs = { keys: (await res.json()).keys ?? [], until: Date.now() + 3600_000 };
  }
  const jwk = certs.keys.find((k) => k.kid === head.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64urlDecode(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  if (!ok) return null;
  const now = Date.now() / 1000;
  if (!["https://accounts.google.com", "accounts.google.com"].includes(String(claims.iss))) return null;
  if (claims.aud !== audience || typeof claims.exp !== "number" || claims.exp < now - 60) return null;
  if (email && (claims.email !== email || claims.email_verified !== true)) return null;
  return claims;
}
