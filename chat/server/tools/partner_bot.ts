// Test partner for trying the live app with one phone: two QA accounts take turns waiting in the
// match queue (never both at once, so they don't match each other) and reply to every message.
// Video calls: the bot accepts a video request and joins with a moving test pattern and beep (headless Chromium,
// see video_bot.cjs), so one phone can try the whole call flow.
// Run: YAARI_QA_PASSWORD=... deno run --allow-net --allow-env --allow-run --allow-read server/tools/partner_bot.ts [minutes]
import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = "https://dgynsckrqgnpyyvqekzi.supabase.co";
const ANON = Deno.env.get("SUPABASE_ANON_KEY")!;
const PASSWORD = Deno.env.get("YAARI_QA_PASSWORD")!;
const minutes = Number(Deno.args[0] ?? 60);
const stopAt = Date.now() + minutes * 60_000;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const log = (who: string, msg: string) => console.log(`${new Date().toISOString().slice(11, 19)} [${who}] ${msg}`);

let searcher: string | null = null; // only one bot sits in the queue at a time
const botIds = new Set<string>();    // and if two copies of this script run at once, bots still never chat with each other

async function bot(email: string, name: string) {
  const db: SupabaseClient = createClient(SUPABASE_URL, ANON, { auth: { persistSession: false } });
  const { error } = await db.auth.signInWithPassword({ email, password: PASSWORD });
  if (error) throw new Error(`${name} sign-in: ${error.message}`);
  botIds.add((await db.auth.getUser()).data.user!.id);
  log(name, "signed in");
  while (Date.now() < stopAt) {
    if (searcher && searcher !== name) { await sleep(1000); continue; }
    searcher = name;
    const { data: matchId, error: e } = await db.rpc("find_match", { p_target: "any" });
    if (e) { log(name, `find_match: ${e.message}`); await sleep(2000); continue; }
    if (!matchId) { await sleep(2000); continue; }
    searcher = null;
    log(name, `matched ${matchId}`);
    await chat(db, name, matchId as string);
  }
  if (searcher === name) await db.rpc("cancel_search");
}

async function chat(db: SupabaseClient, name: string, matchId: string) {
  const { data: { user } } = await db.auth.getUser();
  const { data: row } = await db.from("matches").select("user_a,user_b").eq("id", matchId).maybeSingle();
  const partner = row && (row.user_a === user!.id ? row.user_b : row.user_a);
  if (!partner || botIds.has(partner)) {
    await sleep(5500); // the server's exit lock
    await db.rpc("leave_match", { p_match: matchId });
    log(name, "matched the other bot, left");
    return;
  }
  await db.from("messages").insert({ match_id: matchId, body: `Hi! I'm ${name}, a test bot. Send me anything, or start a video call.` });
  let lastId = 0;
  let video: Deno.ChildProcess | null = null;
  const stopVideo = () => { try { video?.kill(); } catch { /* already exited */ } video = null; };
  while (Date.now() < stopAt) {
    const { data: m } = await db.from("matches").select("ended_at,video_status,video_requested_by").eq("id", matchId).maybeSingle();
    if (!m || m.ended_at) { stopVideo(); log(name, "partner left"); return; }
    if (m.video_status === "requested" && m.video_requested_by !== user!.id) {
      const { error } = await db.rpc("respond_video", { p_match: matchId, p_accept: true });
      log(name, `video request: ${error ? error.message : "accepted"}`);
      const { data: t, error: te } = error ? { data: null, error } : await db.functions.invoke("video-token", { body: { match_id: matchId } });
      if (t?.token) {
        video = new Deno.Command("node", { args: [new URL("./video_bot.cjs", import.meta.url).pathname, t.url, t.token, "600"] }).spawn();
        video.status.then(() => { video = null; });
      } else if (te) log(name, `video token: ${te.message}`);
    } else if (m.video_status !== "accepted" && video) { stopVideo(); log(name, "video ended"); }
    const { data: msgs } = await db.from("messages").select("id,sender,body,media_kind")
      .eq("match_id", matchId).gt("id", lastId).order("id");
    for (const x of msgs ?? []) {
      lastId = x.id;
      if (x.sender === user!.id) continue;
      const got = x.media_kind ? `your ${x.media_kind}` : `"${x.body}"`;
      log(name, `got ${got}`);
      await db.from("messages").insert({ match_id: matchId, body: `${name} got ${got} 👍` });
    }
    await sleep(1500);
  }
}

await Promise.all([bot("qa1@yaari.test", "Arjun"), bot("qa2@yaari.test", "Priya")]);
log("bot", "time is up, stopped");
