// Writes what a test bot says (migration 030). Called only by the database (bot_job) with a key that lives in its
// vault; anyone else gets 403. One job = one line: an answer in a random chat or a room, a greeting, a welcome.
// Secrets: ANTHROPIC_API_KEY (without it the canned lines of migration 028 are used), BOT_MODEL (default
// claude-opus-5-5). The database caps model answers per day (bot_config.llm_cap).
import Anthropic from "npm:@anthropic-ai/sdk@0.132.1";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json } from "../_shared/cors.ts";

type Card = { name: string; gender: string; age: number; city: string | null; country: string | null; interests: string[] };
type Line = { me: boolean; name?: string; text: string | null; replyTo?: string | null };
type Context = {
  kind: "chat" | "greet" | "video" | "room" | "welcome" | "idle";
  bot: Card;
  partner?: Card;
  friends?: boolean;
  room?: { title: string; category: string; lang: string | null; announcement: string | null; playing: string | null; host: string };
  people?: string[];
  to?: { id?: number; name: string; text?: string };
  history: Line[];
};

const MODEL = Deno.env.get("BOT_MODEL") || "claude-opus-5-5";
const KEY = Deno.env.get("ANTHROPIC_API_KEY");
const anthropic = KEY ? new Anthropic({ apiKey: KEY, maxRetries: 1, timeout: 20_000 }) : null;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

const who = (c: Card) =>
  `${c.name}, ${c.age}, ${c.gender}${c.city ? `, from ${c.city}` : ""}${c.interests?.length ? `; into ${c.interests.join(", ")}` : ""}`;

function system(ctx: Context) {
  const b = ctx.bot;
  const where = ctx.room
    ? `You are in the text chat of the group voice room "${ctx.room.title}" (${ctx.room.category === "music" ? "a music room" : "a hangout room"}, hosted by ${ctx.room.host})${ctx.room.playing ? `; now playing: ${ctx.room.playing}` : ""}. People here: ${ctx.people?.join(", ") || "just a few"}.`
    : `You are in a one-to-one random chat with ${ctx.partner ? who(ctx.partner) : "someone"}${ctx.friends ? " (you are friends on Yaari)" : " you were just matched with"}.`;
  return [
    `You are ${who(b)}, chatting on Yaari, an Indian random-chat and group-room app.`,
    `This account is one of Yaari's test bots, used by the team to try the app. Chat like a friendly real person, but if someone sincerely asks whether you are a bot or AI, say yes, you are Yaari's test bot.`,
    where,
    `Write like people in India text on WhatsApp: short (one sentence, at most two, under 25 words), casual, light slang is fine, an emoji only now and then. Use the same language and script as the people you talk to: English, Hinglish (Hindi in Latin letters), Hindi, Bengali, Portuguese or Vietnamese. Keep the conversation going: react to what they said and sometimes ask something back. Remember what was said before. Never repeat a line you already wrote.`,
    `You can only text. You can't do voice or video calls, send photos, share phone numbers or social handles, or meet up; say so kindly if asked.`,
    `Keep it friendly and clean: no sexual content or flirting beyond friendliness, nothing hateful or dangerous. If someone pushes for that, change the subject or say you're not into it.`,
    `Reply with only your message text, without your name or quotes.`,
  ].join("\n");
}

function prompt(ctx: Context) {
  const name = ctx.bot.name;
  const other = ctx.partner?.name ?? "them";
  const lines = ctx.history
    .filter((l) => l.text)
    .map((l) => `${l.me ? `${name} (you)` : l.name ?? other}${l.replyTo ? ` (replying to ${l.replyTo})` : ""}: ${l.text}`)
    .join("\n");
  const so = lines ? `Chat so far:\n${lines}\n\n` : "";
  switch (ctx.kind) {
    case "greet": return `You were just matched with ${other}. Send the first message.`;
    case "video": return `${so}${other} asked for a video call; the app already declined it. Tell them briefly that you can only text.`;
    case "chat": return `${so}Write ${name}'s next message to ${other}.`;
    case "room": return `${so}Answer ${ctx.to?.name}'s last message ("${ctx.to?.text}").`;
    case "welcome": return `${so}${ctx.to?.name} just joined the room. Welcome them in one short line.`;
    case "idle": return `${so}The room chat has gone quiet. Say something casual that fits the room to get people talking.`;
  }
}

async function write(ctx: Context): Promise<string | null> {
  if (!anthropic) return null;
  // The safety fallback applies to Opus/Sonnet/Fable; Haiku has none.
  const fallback = /^claude-(opus|sonnet|fable)/.test(MODEL) ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" } : {};
  const res = await anthropic.beta.messages.create({
    model: MODEL,
    max_tokens: 1024,
    system: system(ctx),
    messages: [{ role: "user", content: prompt(ctx) }],
    output_config: { effort: "low" },
    ...fallback,
  } as Anthropic.Beta.Messages.MessageCreateParamsNonStreaming);
  if (res.stop_reason === "refusal") return null;
  const text = res.content.flatMap((b) => (b.type === "text" ? [b.text] : [])).join(" ").trim();
  // Models sometimes add their own name or quotes; people don't.
  return text.replace(new RegExp(`^${ctx.bot.name}\\s*(\\(you\\))?\\s*:\\s*`, "i"), "").replace(/^"(.*)"$/s, "$1").trim() || null;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: ok } = await admin.rpc("edge_key_ok", { p_key: req.headers.get("x-edge-key") ?? "" });
  if (ok !== true) return json(403, { error: "forbidden" });

  const job = await req.json().catch(() => null);
  if (!job || typeof job.kind !== "string" || typeof job.bot !== "string") return json(400, { error: "bad_request" });
  const started = Date.now();
  // A person reads before answering, and a newer line from them should win over this one.
  await sleep(job.kind === "greet" ? 2500 : job.kind === "idle" ? 0 : 1200);

  const { data: ctx, error } = await admin.rpc("bot_context", { p_job: job });
  if (error) {
    console.error("bot-brain: context", error.message);
    return json(500, { error: "context_failed" });
  }
  if (!ctx) return json(200, { skipped: true });
  await admin.rpc("bot_typing", { p_job: job });

  let text: string | null = null;
  const { data: allowed } = anthropic ? await admin.rpc("bot_llm_take") : { data: false };
  if (allowed === true) {
    try {
      text = await write(ctx as Context);
    } catch (e) {
      console.error("bot-brain: model", (e as Error).message);
    }
  }
  if (!text) {
    const last = (ctx as Context).history.at(-1);
    const { data } = await admin.rpc("bot_canned", { p_job: { ...job, toName: (ctx as Context).to?.name ?? null }, p_text: last && !last.me ? last.text : null });
    text = typeof data === "string" ? data : null;
  }
  if (!text) return json(200, { skipped: true });

  // Typing takes a moment: about 40 ms a character, 1.5 to 6 s in all.
  const typing = Math.min(6000, 1500 + text.length * 40) - (Date.now() - started);
  if (typing > 0) await sleep(typing);
  const { data: posted } = await admin.rpc("bot_post", { p_job: job, p_text: text });
  return json(200, { posted: posted === true, model: allowed === true });
});
