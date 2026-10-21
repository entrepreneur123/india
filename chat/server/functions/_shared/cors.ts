// Browsers (the web version) call these functions cross-origin. Auth is a Bearer token, never a cookie,
// so allowing any origin does not let another site act as the user.
export const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

/** Answers the CORS preflight; returns null for every other request. */
export const preflight = (req: Request) => (req.method === "OPTIONS" ? new Response("ok", { headers: cors }) : null);
