// Link previews for chat (migration 036): POST {url} (signed-in user) -> {url, title, description, image, site, frameable}
// "frameable" says whether the page lets other sites show it in a frame (no X-Frame-Options, no frame-ancestors
// limit), so the web app knows to show it beside the chat or offer "open" instead. The Android app shows any page
// in its own viewer. Only public https pages on the default port are fetched, at most 3 redirects, each hop re-checked,
// the first 512 KB read, 6 seconds in all: this must never become a way to reach private addresses.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json, preflight } from "../_shared/cors.ts";

const MAX_BYTES = 512 * 1024;
const UA = "Mozilla/5.0 (compatible; YaariLinkPreview/1.0; +https://yaari-web.vercel.app)";

function privateV4(ip: string): boolean {
  const p = ip.split(".").map(Number);
  if (p.length !== 4 || p.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return true;
  const [a, b] = p;
  return a === 0 || a === 10 || a === 127 || (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || (a === 198 && (b === 18 || b === 19)) || a >= 224;
}

function privateV6(ip: string): boolean {
  const x = ip.toLowerCase();
  if (x.startsWith("::ffff:")) return privateV4(x.slice(7));
  return x === "::" || x === "::1" || /^f[cd]/.test(x) || /^fe[89ab]/.test(x) || x.startsWith("ff");
}

/** A URL we may fetch: https, default port, a public host name that resolves only to public addresses. */
async function allowed(u: URL): Promise<boolean> {
  if (u.protocol !== "https:" || (u.port && u.port !== "443") || u.username || u.password) return false;
  const host = u.hostname.replace(/^\[|\]$/g, "").toLowerCase();
  if (!host.includes(".") || /\.(local|internal|localhost|lan|home|corp)$/.test(host) || host === "localhost") return false;
  if (/^[\d.]+$/.test(host)) return !privateV4(host);
  if (host.includes(":")) return !privateV6(host);
  try {
    const [a, aaaa] = await Promise.all([
      Deno.resolveDns(host, "A").catch(() => [] as string[]),
      Deno.resolveDns(host, "AAAA").catch(() => [] as string[]),
    ]);
    if (a.length + aaaa.length === 0) return false;
    return !a.some(privateV4) && !aaaa.some(privateV6);
  } catch {
    return false;
  }
}

function decode(s: string): string {
  return s.replace(/&(#x?[0-9a-f]+|amp|lt|gt|quot|apos|#39);/gi, (m, e: string) => {
    const k = e.toLowerCase();
    if (k === "amp") return "&";
    if (k === "lt") return "<";
    if (k === "gt") return ">";
    if (k === "quot") return '"';
    if (k === "apos" || k === "#39") return "'";
    const n = k.startsWith("#x") ? parseInt(k.slice(2), 16) : parseInt(k.slice(1), 10);
    return Number.isFinite(n) && n > 0 && n < 0x110000 ? String.fromCodePoint(n) : m;
  }).replace(/\s+/g, " ").trim();
}

function meta(html: string, keys: string[]): string | null {
  for (const tag of html.match(/<meta\b[^>]*>/gi) ?? []) {
    const name = /(?:property|name)\s*=\s*["']([^"']+)["']/i.exec(tag)?.[1]?.toLowerCase();
    if (!name || !keys.includes(name)) continue;
    const content = /content\s*=\s*["']([^"']*)["']/i.exec(tag)?.[1];
    if (content) return decode(content);
  }
  return null;
}

/** Whether the page's own headers let another site frame it. */
function frameable(h: Headers): boolean {
  if (h.get("x-frame-options")) return false;
  const fa = /frame-ancestors([^;]*)/i.exec(h.get("content-security-policy") ?? "")?.[1]?.trim();
  if (fa === undefined) return true;
  return fa.split(/\s+/).some((s) => s === "*" || s === "https:");
}

/**
 * At most 30 previews a minute per person (this instance's memory; a person's requests mostly reach the same warm
 * instance): a chat shows a handful, so more is a script using us to fetch pages. Answered with 429.
 */
const LIMIT = 30;
const recent = new Map<string, number[]>();
function overLimit(user: string): boolean {
  const now = Date.now();
  const hits = (recent.get(user) ?? []).filter((t) => t > now - 60_000);
  if (hits.length >= LIMIT) return true;
  hits.push(now);
  recent.delete(user); // re-inserted last: the oldest-used person is dropped first when the map is full
  recent.set(user, hits);
  if (recent.size > 10_000) recent.delete(recent.keys().next().value!);
  return false;
}

const clip = (s: string | null, n: number) => (s ? (s.length > n ? s.slice(0, n - 1) + "…" : s) : null);

Deno.serve(async (req) => {
  const pre = preflight(req);
  if (pre) return pre;
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: who } = await admin.auth.getUser((req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, ""));
  if (!who?.user) return json(401, { error: "not_signed_in" });
  if (overLimit(who.user.id)) return json(429, { error: "slow_down" });

  const body = await req.json().catch(() => ({}));
  let url: URL;
  try {
    url = new URL(String(body?.url ?? ""));
  } catch {
    return json(400, { error: "bad_request" });
  }
  if (url.href.length > 500 || !(await allowed(url))) return json(400, { error: "bad_request" });

  const stop = AbortSignal.timeout(6_000);
  try {
    let res: Response | null = null;
    for (let hop = 0; hop <= 3; hop++) {
      res = await fetch(url, { redirect: "manual", signal: stop, headers: { "User-Agent": UA, Accept: "text/html,*/*;q=0.5" } });
      const next = res.status >= 300 && res.status < 400 ? res.headers.get("location") : null;
      if (!next) break;
      await res.body?.cancel();
      url = new URL(next, url);
      if (hop === 3 || !(await allowed(url))) return json(200, { url: url.href, title: null, description: null, image: null, site: url.hostname, frameable: false });
    }
    if (!res) throw new Error("no response");
    const canFrame = res.ok && frameable(res.headers);
    const type = res.headers.get("content-type") ?? "";
    let html = "";
    if (res.ok && type.includes("html") && res.body) {
      const reader = res.body.getReader();
      const parts: Uint8Array[] = [];
      let size = 0;
      while (size < MAX_BYTES) {
        const { done, value } = await reader.read();
        if (done) break;
        parts.push(value);
        size += value.length;
      }
      await reader.cancel().catch(() => undefined);
      const all = new Uint8Array(Math.min(size, MAX_BYTES));
      let at = 0;
      for (const p of parts) {
        const take = Math.min(p.length, all.length - at);
        all.set(p.subarray(0, take), at);
        at += take;
        if (at >= all.length) break;
      }
      html = new TextDecoder().decode(all);
      const end = html.search(/<\/head>/i);
      if (end > 0) html = html.slice(0, end);
    } else {
      await res.body?.cancel();
    }
    const title = meta(html, ["og:title", "twitter:title"]) ?? (/<title[^>]*>([^<]*)<\/title>/i.exec(html)?.[1] ? decode(/<title[^>]*>([^<]*)<\/title>/i.exec(html)![1]) : null);
    let image = meta(html, ["og:image", "og:image:url", "twitter:image"]);
    try {
      image = image ? new URL(image, url).href : null;
      if (image && !image.startsWith("https://")) image = null;
    } catch {
      image = null;
    }
    return new Response(JSON.stringify({
      url: url.href,
      title: clip(title, 200),
      description: clip(meta(html, ["og:description", "description", "twitter:description"]), 300),
      image: image && image.length <= 1000 ? image : null,
      site: clip(meta(html, ["og:site_name"]), 80) ?? url.hostname.replace(/^www\./, ""),
      frameable: canFrame,
    }), { headers: { "Access-Control-Allow-Origin": "*", "Content-Type": "application/json", "Cache-Control": "private, max-age=3600" } });
  } catch (e) {
    console.error("link-meta:", (e as Error).message);
    return json(200, { url: url.href, title: null, description: null, image: null, site: url.hostname, frameable: false });
  }
});
