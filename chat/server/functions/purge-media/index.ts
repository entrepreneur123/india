// Nightly clean-up of files nobody uses any more: old profile photos, photos of deleted chats and posts.
// Called by pg_cron (job yaari-purge-media) with a key that lives only in the database vault; anyone else
// gets 403. Up to 5 rounds of 1000 files per run, so a backlog clears over a few nights.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: ok } = await admin.rpc("purge_key_ok", { p_key: req.headers.get("x-purge-key") ?? "" });
  if (ok !== true) return json(403, { error: "forbidden" });

  let removed = 0;
  for (let round = 0; round < 5; round++) {
    const { data: names, error } = await admin.rpc("stale_media", { p_limit: 1000 });
    if (error) return json(500, { error: "list_failed", removed });
    if (!names?.length) break;
    const { error: rmError } = await admin.storage.from("media").remove(names as string[]);
    if (rmError) return json(500, { error: "remove_failed", removed });
    removed += names.length;
    if (names.length < 1000) break;
  }
  return json(200, { removed });
});
