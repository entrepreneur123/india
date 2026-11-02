// GoTrue Send SMS Hook (supabase.com/docs/guides/auth/auth-hooks/send-sms-hook): delivers the code GoTrue made, by
// SMS or WhatsApp per country (OTP_ROUTES, _shared/otp.ts), through Twilio's Messages API. Off until the owner enables
// the hook in the dashboard (Authentication → Hooks → Send SMS → this function's URL) and sets the secrets:
//   SEND_SMS_HOOK_SECRET (from that screen), TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN,
//   SMS: TWILIO_MESSAGING_SERVICE_SID or TWILIO_SMS_FROM;  WhatsApp: TWILIO_WHATSAPP_FROM + TWILIO_WHATSAPP_CONTENT_SID
//   (an approved authentication template with the code as {{1}});  OTP_ROUTES (default "*:sms").
// Deployed without JWT verification: GoTrue signs the call (Standard Webhooks), checked below.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { Webhook } from "npm:standardwebhooks@1.1.1";
import { type Channel, deliver, parseRoutes, routeFor } from "../_shared/otp.ts";

const env = (k: string) => Deno.env.get(k) ?? "";
const fail = (status: number, message: string) =>
  new Response(JSON.stringify({ error: { http_code: status, message } }), { status, headers: { "Content-Type": "application/json" } });

async function twilio(fields: Record<string, string>) {
  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${env("TWILIO_ACCOUNT_SID")}/Messages.json`, {
    method: "POST",
    headers: { Authorization: "Basic " + btoa(`${env("TWILIO_ACCOUNT_SID")}:${env("TWILIO_AUTH_TOKEN")}`) },
    body: new URLSearchParams(fields),
  });
  if (!res.ok) throw new Error(`twilio ${res.status}: ${(await res.text()).slice(0, 300)}`);
}

const send: Record<Channel, (phone: string, otp: string) => Promise<void>> = {
  sms: (phone, otp) => {
    const from: Record<string, string> = env("TWILIO_MESSAGING_SERVICE_SID")
      ? { MessagingServiceSid: env("TWILIO_MESSAGING_SERVICE_SID") }
      : { From: env("TWILIO_SMS_FROM") };
    if (!Object.values(from)[0]) throw new Error("sms not configured");
    return twilio({ ...from, To: `+${phone}`, Body: `${otp} is your Yaari code. Don't share it.` });
  },
  whatsapp: (phone, otp) => {
    if (!env("TWILIO_WHATSAPP_FROM") || !env("TWILIO_WHATSAPP_CONTENT_SID")) throw new Error("whatsapp not configured");
    return twilio({
      From: `whatsapp:+${env("TWILIO_WHATSAPP_FROM").replace(/\D/g, "")}`,
      To: `whatsapp:+${phone}`,
      ContentSid: env("TWILIO_WHATSAPP_CONTENT_SID"),
      ContentVariables: JSON.stringify({ 1: otp }),
    });
  },
};

Deno.serve(async (req) => {
  if (!env("SEND_SMS_HOOK_SECRET") || !env("TWILIO_ACCOUNT_SID") || !env("TWILIO_AUTH_TOKEN")) return fail(503, "sms_not_configured");
  const body = await req.text();
  let event: { user?: { phone?: string }; sms?: { otp?: string } };
  try {
    event = new Webhook(env("SEND_SMS_HOOK_SECRET").replace("v1,whsec_", "")).verify(body, Object.fromEntries(req.headers)) as typeof event;
  } catch {
    return fail(401, "bad_signature");
  }
  const phone = (event.user?.phone ?? "").replace(/\D/g, ""), otp = event.sms?.otp ?? "";
  if (!phone || !otp) return fail(400, "bad_request");

  let route: Channel[];
  try {
    route = routeFor(phone, parseRoutes(env("OTP_ROUTES") || undefined));
  } catch (e) {
    console.error("send-sms:", (e as Error).message);
    return fail(500, "sms_unavailable");
  }
  if (!route.length) return fail(400, "sms_unavailable"); // a country we don't send to

  const admin = createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"));
  // At most 5 codes an hour per number (SMS-pumping guard on top of GoTrue's own limits), counted in the database (121).
  const { data: underCap } = await admin.rpc("rate_ok", { p_key: "otp:cap:" + phone, p_limit: 5, p_window: "1 hour" });
  if (underCap === false) return fail(429, "sms_unavailable");
  // The first code in 10 minutes goes by the first channel, a resend by the next one.
  const { data: first } = route.length > 1
    ? await admin.rpc("rate_ok", { p_key: "otp:try:" + phone, p_limit: 1, p_window: "10 minutes" })
    : { data: true };
  try {
    await deliver(phone, otp, route, first === false ? 1 : 0, send);
    return new Response("{}", { headers: { "Content-Type": "application/json" } });
  } catch {
    return fail(500, "sms_unavailable");
  }
});
