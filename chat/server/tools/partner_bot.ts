// Test partner for trying the live app with one phone: two QA accounts take turns waiting in the
// match queue (never both at once, so they don't match each other) and reply to every message.
// Run: YAARI_QA_PASSWORD=... deno run --allow-net --allow-env server/tools/partner_bot.ts [minutes]
import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";

const URL = "https://dgynsckrqgnpyyvqekzi.supabase.co";
const ANON = Deno.env.get("SUPABASE_ANON_KEY")!;
const PASSWORD = Deno.env.get("YAARI_QA_PASSWORD")!;
const minutes = Number(Deno.args[0] ?? 60);
const stopAt = Date.now() + minutes * 60_000;
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const log = (who: string, msg: string) => console.log(`${new Date().toISOString().slice(11, 19)} [${who}] ${msg}`);

let searcher: string | null = null; // only one bot sits in the queue at a time

async function bot(email: string, name: string) {
  const db: SupabaseClient = createClient(URL, ANON, { auth: { persistSession: false } });
  const { error } = await db.auth.signInWithPassword({ email, password: PASSWORD });
  if (error) throw new Error(`${name} sign-in: ${error.message}`);
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
  await db.from("messages").insert({ match_id: matchId, body: `Hi! I'm ${name}, a test bot. Send me anything.` });
  let lastId = 0;
  while (Date.now() < stopAt) {
    const { data: m } = await db.from("matches").select("ended_at").eq("id", matchId).maybeSingle();
    if (!m || m.ended_at) { log(name, "partner left"); return; }
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
