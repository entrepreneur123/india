import { Webhook } from "npm:standardwebhooks@1.1.1";
import { type Channel, deliver, parseRoutes, routeFor } from "./otp.ts";

const eq = (a: unknown, b: unknown, what: string) => {
  if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${what}: ${JSON.stringify(a)} != ${JSON.stringify(b)}`);
};

Deno.test("Routes: default is SMS everywhere; longest calling code wins; no * means refused", () => {
  eq(routeFor("919876543210", parseRoutes()), ["sms"], "default");
  const r = parseRoutes(" 91: whatsapp, sms ; 1:sms; 1868:whatsapp ");
  eq(routeFor("919876543210", r), ["whatsapp", "sms"], "India");
  eq(routeFor("12025550123", r), ["sms"], "US");
  eq(routeFor("18685550123", r), ["whatsapp"], "Trinidad over +1");
  eq(routeFor("971501234567", r), [], "UAE not listed");
});

Deno.test("Routes: a bad table is refused, not half-read", () => {
  for (const bad of ["91:telegram", "x:sms", "91:", "91"]) {
    let threw = false;
    try { parseRoutes(bad); } catch { threw = true; }
    if (!threw) throw new Error(`accepted ${bad}`);
  }
});

Deno.test("Delivery: first request by the first channel, a resend by the next, failures fall through", async () => {
  const sent: string[] = [];
  const ok = (c: Channel) => (p: string, o: string) => (sent.push(`${c}:${p}:${o}`), Promise.resolve());
  const down = () => Promise.reject(new Error("down"));
  eq(await deliver("91", "123456", ["whatsapp", "sms"], 0, { whatsapp: ok("whatsapp"), sms: ok("sms") }), "whatsapp", "first");
  eq(await deliver("91", "123456", ["whatsapp", "sms"], 1, { whatsapp: ok("whatsapp"), sms: ok("sms") }), "sms", "resend");
  eq(await deliver("91", "123456", ["whatsapp", "sms"], 5, { whatsapp: ok("whatsapp"), sms: ok("sms") }), "sms", "third+ stays on last");
  eq(await deliver("91", "123456", ["whatsapp", "sms"], 0, { whatsapp: down, sms: ok("sms") }), "sms", "fallback");
  eq(sent, ["whatsapp:91:123456", "sms:91:123456", "sms:91:123456", "sms:91:123456"], "messages");
  let threw = false;
  try { await deliver("91", "1", ["whatsapp", "sms"], 0, { whatsapp: down, sms: down }); } catch { threw = true; }
  if (!threw) throw new Error("all down must throw");
});

Deno.test("Hook signature: GoTrue's Standard Webhooks signature verifies; a changed body or secret does not", () => {
  const secret = "v1,whsec_" + btoa("0123456789abcdef0123456789abcdef");
  const wh = new Webhook(secret.replace("v1,whsec_", ""));
  const body = JSON.stringify({ user: { phone: "919876543210" }, sms: { otp: "123456" } });
  const id = "msg_1", ts = new Date();
  const headers = { "webhook-id": id, "webhook-timestamp": String(Math.floor(ts.getTime() / 1000)), "webhook-signature": wh.sign(id, ts, body) };
  eq((wh.verify(body, headers) as { sms: { otp: string } }).sms.otp, "123456", "verify");
  for (const [b, w] of [[body.replace("123456", "000000"), wh], [body, new Webhook(btoa("another-secret-another-secret!!"))]] as const) {
    let threw = false;
    try { w.verify(b, headers); } catch { threw = true; }
    if (!threw) throw new Error("accepted a forged call");
  }
});
