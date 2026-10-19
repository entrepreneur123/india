// Joins a LiveKit room from headless Chromium with a fake camera/mic (test pattern + beep).
// Used by partner_bot.ts for video calls. node server/tools/video_bot.cjs <wss-url> <token> <seconds>
const { chromium } = require("/opt/node22/lib/node_modules/playwright");
const [url, token, secs = "30"] = process.argv.slice(2);
(async () => {
  const proxy = process.env.HTTPS_PROXY ? { server: process.env.HTTPS_PROXY } : undefined;
  const browser = await chromium.launch({
    executablePath: "/opt/pw-browsers/chromium", proxy,
    args: ["--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream", "--ignore-certificate-errors"],
  });
  process.on("SIGTERM", () => browser.close().finally(() => process.exit(0))); // bot hangs up (not an error)
  const page = await browser.newPage();
  page.on("console", (m) => console.log("page:", m.text()));
  // A secure (https) origin is needed for getUserMedia; served locally, no network.
  await page.route("https://vbot.test/", (r) => r.fulfill({ contentType: "text/html", body: "<html><body></body></html>" }));
  await page.goto("https://vbot.test/");
  await page.addScriptTag({ url: "https://cdn.jsdelivr.net/npm/livekit-client@2.15.8/dist/livekit-client.umd.js" });
  const r = await page.evaluate(async ({ url, token, secs }) => {
    const { Room, RoomEvent } = LivekitClient;
    const room = new Room();
    room.on(RoomEvent.ParticipantConnected, (p) => console.log("partner joined " + p.identity));
    room.on(RoomEvent.TrackSubscribed, (t, _, p) => console.log("receiving " + t.kind + " from " + p.identity));
    room.on(RoomEvent.Disconnected, () => console.log("disconnected"));
    await room.connect(url, token);
    console.log("connected as " + room.localParticipant.identity);
    await room.localParticipant.enableCameraAndMicrophone();
    console.log("publishing: " + [...room.localParticipant.trackPublications.values()].map((p) => p.kind).join(","));
    await new Promise((res) => setTimeout(res, secs * 1000));
    const pc = room.engine.pcManager?.publisher?.getStats ? await room.engine.pcManager.publisher.getStats() : null;
    let sent = 0; pc?.forEach((s) => { if (s.type === "outbound-rtp" && s.kind === "video") sent += s.bytesSent; });
    await room.disconnect();
    return { videoBytesSent: sent };
  }, { url, token, secs: Number(secs) });
  console.log(JSON.stringify(r));
  await browser.close();
})().catch((e) => { if (!/has been closed/.test(e.message)) { console.error("FAILED", e.message); process.exit(1); } });
