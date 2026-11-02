// Code delivery for GoTrue's Send SMS Hook (functions/send-sms): GoTrue makes, expires and checks the code; this picks
// the channel by the number's calling code and falls back to the next one (docs/BUILD_VS_BUY.md §6-1).
export type Channel = "sms" | "whatsapp";
export type Routes = Map<string, Channel[]>;
const CHANNELS: Channel[] = ["sms", "whatsapp"];

/**
 * OTP_ROUTES, e.g. "91:whatsapp,sms;*:sms": calling code (or * for the rest) → channels in order. A number matching no
 * entry (no *) is refused, so the table is also the list of countries we send to. Unset: SMS everywhere.
 */
export function parseRoutes(spec = "*:sms"): Routes {
  const routes: Routes = new Map();
  for (const entry of spec.split(";").map((s) => s.trim()).filter(Boolean)) {
    const [code, list = ""] = entry.split(":").map((s) => s.trim());
    const channels = list.split(",").map((s) => s.trim()) as Channel[];
    if (!/^(\*|\d{1,4})$/.test(code) || !channels.length || channels.some((c) => !CHANNELS.includes(c))) {
      throw new Error(`OTP_ROUTES: bad entry "${entry}"`);
    }
    routes.set(code, channels);
  }
  return routes;
}

/** Channels for a number (digits with the country code): the longest matching calling code, else *, else none. */
export function routeFor(phone: string, routes: Routes): Channel[] {
  for (let n = Math.min(4, phone.length - 1); n >= 1; n--) {
    const r = routes.get(phone.slice(0, n));
    if (r) return r;
  }
  return routes.get("*") ?? [];
}

/**
 * Sends [otp] by the first working channel of [route], starting at [attempt] (0 = first request, 1 = a resend: a
 * WhatsApp message is accepted even for a number without WhatsApp and fails later, so a resend must try the next one).
 * Returns the channel used; throws when every channel failed.
 */
export async function deliver(
  phone: string,
  otp: string,
  route: Channel[],
  attempt: number,
  send: Record<Channel, (phone: string, otp: string) => Promise<void>>,
): Promise<Channel> {
  const tries = route.slice(Math.min(attempt, route.length - 1));
  for (const channel of tries) {
    try {
      await send[channel](phone, otp);
      return channel;
    } catch (e) {
      console.error(`otp: ${channel} failed`, (e as Error).message);
    }
  }
  throw new Error("otp_undeliverable");
}
