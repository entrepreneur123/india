// In-app account deletion (Google Play requirement). Removes the user's media, keeps only a hashed
// phone number if they are currently banned (so a ban cannot be escaped), then deletes the auth user;
// the database cascades to profile, posts, matches, messages, reports and blocks.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { json, preflight } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  const pre = preflight(req);
  if (pre) return pre;
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const userClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json(401, { error: "not_signed_in" });

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const bucket = admin.storage.from("media");
  for (const folder of ["avatar", "chat", "posts"]) {
    for (;;) {
      const { data: files, error } = await bucket.list(`${user.id}/${folder}`, { limit: 1000 });
      if (error) return json(500, { error: "storage_list_failed" });
      if (!files?.length) break;
      const { error: rmError } = await bucket.remove(files.map((f) => `${user.id}/${folder}/${f.name}`));
      if (rmError) return json(500, { error: "storage_remove_failed" });
      if (files.length < 1000) break;
    }
  }
  const { error: banError } = await admin.rpc("remember_ban", { uid: user.id });
  if (banError) return json(500, { error: "ban_check_failed" });
  const { error } = await admin.auth.admin.deleteUser(user.id);
  if (error) return json(500, { error: "delete_failed" });
  return json(200, { deleted: true });
});
