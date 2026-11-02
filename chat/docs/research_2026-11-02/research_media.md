# Yaari media-server and noise-suppression research (2026-11-02)

Labels: **[C: url]** = confirmed by fetching that page/source today. **[U]** = unverified or my inference.
LiveKit docs moved to `docs.livekit.io/transport/...`. Appending `.md` gives plain text, and the quotes below were taken from those `.md` files.

---

## Part A: Media servers

### A.1 LiveKit OSS (self-hosted)
- **License, language**: "LiveKit server is licensed under Apache License v2.0." "LiveKit's server is written in Go, using the awesome Pion WebRTC implementation." [C: https://github.com/livekit/livekit (README.md)]
- **What it is**: a complete server (single binary, Docker, or K8s) with JWT auth and SDKs for every platform. [C: README]
- **Features** [C: README]: speaker detection, simulcast, selective subscription, moderation APIs, E2EE, "SVC codecs (VP9, AV1)", data tracks, SIP telephony, webhooks, "distributed and multi-region".
  - Audio RED: "LiveKit recommends enabling this feature … If your use case prioritizes bandwidth and can tolerate audio glitches, you can disable RED." The client option is `red: false`, so RED is on by default [U: the default is inferred from the wording]. [C: https://docs.livekit.io/transport/media/advanced.md]
  - FlexFEC: `config-sample.yaml` has "FlexFEC-03 forward error correction, disabled by default. Accepts FEC from publishers and uses it to repair lost packets before forwarding." [C: https://github.com/livekit/livekit/blob/master/config-sample.yaml]. Opus in-band FEC is negotiated end-to-end between clients, so it passes through any SFU [U].
  - Embedded TURN: "LiveKit includes an embedded TURN server. It's a secure TURN implementation that has integrated authentication with the rest of LiveKit. The authentication layer ensures that only clients that have already established a signal connection could connect to our TURN server." [C: https://docs.livekit.io/transport/self-hosting/deployment.md]
  - Recording: Egress is "Full support, deployed as a separate service" when self-hosted. [C: https://docs.livekit.io/transport/self-hosting.md]
- **Scaling** [C: https://docs.livekit.io/transport/self-hosting/distributed.md]:
  - "When Redis is configured, LiveKit automatically switches to a distributed setup by using Redis for room data as well as a message bus."
  - "there are limits to the number of participants in a room since, for now, a room must fit on a single node."
  - The signal connection can land on any node. "That instance then acts as a signaling bridge, proxying messages between the node where the room is hosted and the client."
  - Multi-region: there is a "region-aware, load aware node selector", used together with geo DNS. Selection rules: "node must have lower utilization than `sysload_limit`", "nodes are in the region closest to the signaling instance", "chosen at random".
  - Implication: in self-hosted multi-region, a room lives in one region, and users from other regions connect to that one node's media. There is no inter-node media relay, which matches "Single-home SFU" in LiveKit's own comparison table. [U, inference from docs]
  - Draining: on SIGTERM a node keeps active rooms running and rejects new rooms. The Helm chart sets `terminationGracePeriodSeconds` to 5 hours. [C: distributed.md, kubernetes.md]
- **Benchmark** [C: https://docs.livekit.io/transport/self-hosting/benchmark.md]:
  - Hardware: "All benchmarks were ran with the server running on a 16-core, compute optimized instance on Google Cloud. ( `c2-standard-16`)". They measure "max number of participants supported in a single room."
  - Audio only: "It uses an average audio bitrate of 3kbps. … We use 10 as the approximate number of speakers here."
    `| Large audio rooms | 10 | 3000 | 7.3 kBps / 23 MBps | 305 / 959,156 | 80% |` (Pubs | Subs | Bytes/s in/out | Packets/s in/out | CPU)
    - Caveat: the command shown for this row uses `--subscribers 1000`, not 3000. The page is internally inconsistent. The load tester's audio "sends blank packets that aren't audible", and 3 kbps is far below real Opus voice bitrates, so do not treat this as real-world capacity [U].
  - Video, 720p, simulcast: "Large meeting | 150 | 150 | 50 MBps / 93 MBps | … | 85%" and "Livestreaming | 1 | 3000 | 233 kBps / 531 MBps | … | 92%".
  - The comparison table gives self-hosted "Max users per room: Up to ~3,000". [C: self-hosting.md]
- **Deployment requirements**:
  - Ports [C: https://docs.livekit.io/transport/self-hosting/ports-firewall.md]: 7880 API/WS (behind an SSL-terminating LB); ICE/UDP 50000-60000 ("each participant in the room will use two ports"); ICE/TCP 7881; optional UDP mux 7882; TURN/TLS 5349 ("If not using LB, this port needs to be set to 443."); TURN/UDP 3478 (also serves as STUN).
  - Domain and cert: "you will need a domain as well as a SSL certificate … self-signed certs do not work here." "If you are using TURN, then a separate TURN domain and SSL cert will be needed, as well." For TURN/TLS "LiveKit will perform TLS termination". TURN/UDP on 443 is possible with `udp_port: 443`. [C: deployment.md]
  - Resources: "bound by CPU and bandwidth. We recommend running production setups on 10Gbps ethernet or faster." "compute-optimized instance types". "If running in a Dockerized environment, host networking should be used". [C: deployment.md]
  - Kubernetes: there is an official Helm chart (`helm repo add livekit https://helm.livekit.io`) supporting GKE, EKS and DOKS. "LiveKit pods requires direct access to the network with host networking … limited to one LiveKit pod per node." "LiveKit does not support deployment to serverless and/or private clusters." Prerequisites: Redis, plus SSL certs for the main domain and TURN/TLS. [C: https://docs.livekit.io/transport/self-hosting/kubernetes.md]

### A.2 LiveKit Cloud vs OSS: official comparison
Source [C: https://docs.livekit.io/intro/cloud.md and https://docs.livekit.io/transport/self-hosting.md, identical tables]:

| | Self-hosted | LiveKit Cloud |
|---|---|---|
| Noise cancellation (AI agents row) | "Client-side WebRTC cancellation; ai-coustics supported with your own license key." | "Client-side WebRTC cancellation, plus enhanced models (Krisp, ai-coustics)." |
| Telephony noise cancellation | "N/A" | Krisp at the SIP trunk |
| Architecture | "Single-home SFU" | "Global mesh SFU" |
| Connection model | "Single server per room" | "Each user connects to the nearest edge" |
| Max users per room | "Up to ~3,000" | "No limit" |
| Regions | "Regions you deploy to" | Global edge plus region pinning |
| Analytics | "Custom / external" | Dashboard, plus Analytics API on Scale |
| Access token revocation | "N/A" | Included |
| Uptime target | "N/A" | "99.99%" |
| Egress/Ingress | separate service you deploy | included |
| Agent hosting, Agent Builder, built-in inference | N/A | Included |

The Cloud page also says: "LiveKit Cloud runs the same open-source LiveKit server available on GitHub … What does differ is everything around the server: agent tooling, hosting, inference, telephony, global scaling, and observability". It lists "Elastic, unlimited scale: Support for rooms with unlimited participants using LiveKit's global mesh architecture."

### A.3 Jitsi Videobridge (JVB)
- **License**: Apache 2.0 [C: https://github.com/jitsi/jitsi-videobridge LICENSE]. **Language**: Java/Kotlin. The README runs `org.jitsi.videobridge.MainKt` via maven [C: README]. The GitHub languages API was blocked, so the exact split is [U].
- **What it is**: "a WebRTC-compatible Selective Forwarding Unit (SFU) … one of the backend components in the Jitsi Meet stack" [C: README]. It needs Jicofo (conference focus) and Prosody (XMPP) for signaling, so in practice you adopt the whole Jitsi stack [C: https://github.com/jitsi/handbook docs/devops-guide/scalable.md, diagram with nginx/prosody/jicofo + JVBs].
- **Features**: simulcast (`disableSimulcast` option) and Opus RED ("Enables support for opus-red (redundancy for Opus). // enableOpusRed: false") are both configurable [C: https://github.com/jitsi/jitsi-meet/blob/master/config.js]. Recording is via Jibri, which "works by launching a Chrome instance rendered in a virtual framebuffer" [C: https://github.com/jitsi/jibri]. TURN is not embedded in JVB. The quickstart uses coturn: "5349 TCP => … served by coturn. Required" [C: handbook quickstart.md].
  - **Noise suppression in Jitsi Meet**: "By default rnnoise is used. Optionally Krisp can be used by enabling it below, but the Krisp JS SDK files must be supplied in your installation." [C: jitsi-meet config.js]. This is a real-world example of a self-hosted stack using Krisp's own JS SDK.
- **Scaling**: "It is easy to scale the video bridges horizontally by adding as many as needed." The sizing guidance is "4 or 8 CPU with 8 GB RAM" per JVB [C: handbook scalable.md]. Cascading uses "Relays (aka secure octo) use ICE and DTLS/SRTP between each pair of bridges", with Jicofo `RegionBasedBridgeSelectionStrategy` [C: https://github.com/jitsi/jitsi-videobridge/blob/master/doc/relay.md]. Unlike LiveKit OSS, one conference can span bridges in different regions [C: relay.md describes selecting a bridge in each client's region].
- **Ports**: "443 TCP … Required", "10000 UDP … Required", 3478 UDP (coturn STUN, optional), 5349 TCP (coturn, Required) [C: handbook quickstart.md].
- **Benchmark** (old, undated; it uses a quad-core Xeon E5-1620 v2): "for about 20% CPU you will be able to run 1000+ video streams using an average of 550 Mbps!" Table: 1056 streams = 550.4 Mbps at 20.3% CPU [C: https://jitsi.org/jitsi-videobridge-performance-evaluation]. The post is likely about 2014 and does not reflect the current JVB [U].

### A.4 mediasoup
- **License**: ISC [C: https://github.com/versatica/mediasoup README "License: ISC License"]. **Language**: Node.js module or Rust crate on the server side, with a C++ worker subprocess on libuv [C: README].
- **What it is**: a library, not a server. Design goals include "Be minimalist: just handle the media layer", "Be signaling agnostic", "Be super low level API" [C: README]. You write signaling, rooms, auth, TURN, recording and scaling yourself.
- **Features**: "Simulcast and SVC support", congestion control, BWE, DataChannels [C: README]. No `audio/red` appears in `supportedRtpCapabilities.ts`, which lists opus, multiopus, PCMU/PCMA, G722, etc. [C: https://github.com/versatica/mediasoup/blob/v3/node/src/supportedRtpCapabilities.ts]. So RED is not a supported codec [U: inferred from that list]. Opus FEC passes end-to-end [U]. No embedded TURN and no built-in recording; recording goes through PlainTransport plus FFmpeg/GStreamer [U].
- **Capacity** [C: https://mediasoup.org/documentation/v3/scalability/]: "a Worker represents a mediasoup C++ subprocess that runs in a single CPU core". "a mediasoup C++ subprocess can typically handle over ~500 consumers in total" (depends on host CPU). Workers should be "no more than the number of CPU cores in the host". For broadcasting, "If there are more than 200-300 viewers (so 400-600 consumers)" one router may not be enough.
- **Scaling**: `router.pipeToRouter()` connects routers within a host. Across hosts, mediasoup gives no signaling, so the app must replicate the pipe mechanism itself [C: scalability page].

### A.5 Janus WebRTC Server
- **License**: GPLv3 [C: https://github.com/meetecho/janus-gateway README badge and COPYING]. Using it as a network service does not trigger GPL source distribution, because GPLv3 is not AGPL. Modifying it and distributing binaries would [U, legal inference]. **Language**: C [U: inferred from the dependency list GLib, libnice, libsrtp, Jansson].
- **What it is**: a "general purpose, WebRTC server" with plugins (VideoRoom SFU, AudioBridge mixer, Streaming, and others). You still write the app and signaling against its JSON API [C: README / docs].
- **Features**: VideoRoom has simulcast (substream/temporal), recording ("record = true|false", "recording will work with all codecs except iSAC"), and `opus_fec` (default true). RED is not mentioned [C: https://janus.conf.meetecho.com/docs/videoroom]. AudioBridge is "an audio conference bridge for Janus, specifically mixing Opus streams" and has **server-side RNNoise**: "denoise = true|false (whether denoising via RNNoise should be performed for each participant by default)" [C: https://janus.conf.meetecho.com/docs/audiobridge]. Server-side NS is cheap here only because AudioBridge already decodes everything to mix it.
- **Scaling**: no built-in cluster. VideoRoom has "remote publishers" to cascade rooms across instances, but you make and maintain the API calls yourself [C: videoroom docs]. TURN is not embedded [U].

### A.6 Pion
- **License**: MIT. "A pure Go implementation of the WebRTC API" [C: https://github.com/pion/webrtc].
- **What it is**: a library. There is no SFU logic. You build rooms, forwarding, simulcast layer selection, BWE policy, and so on. It supports simulcast and SVC packetization, NACK, TWCC, and TURN as a client (UDP/TCP/DTLS/TLS) [C: README features list]. Pion TURN is "A toolkit for building TURN clients and servers in Go" [C: https://github.com/pion/turn]. LiveKit itself is built on Pion [C: LiveKit README].

### A.7 coturn
- **License**: BSD 3-clause (Citrix copyright text) [C: https://github.com/coturn/coturn LICENSE]. **Language**: C on libevent2 [C: README].
- **What it is**: a TURN/STUN server only, not an SFU. It supports TLS (incl. 1.3), DTLS, and Redis/SQL user DBs [C: README].
- **Capacity**: "can handle thousands simultaneous calls per CPU (when TURN protocol is used) or tens of thousands calls when only STUN protocol is used." Load balancing is via DNS SRV or "built-in 300 ALTERNATE-SERVER mechanism" [C: README].
- **Ports**: the Docker example uses 3478 and 5349 TCP/UDP plus a 49152-65535/udp relay range [C: README].
- **Relevance**: Yaari's P2P friend calls need TURN anyway. If you leave LiveKit Cloud, you need either coturn or LiveKit's embedded TURN for those calls [U].

### A.8 Writing your own SFU [all U, inference]
On top of Pion or mediasoup you must build: signaling and auth, ICE/TURN, per-subscriber simulcast and SVC layer switching, BWE and congestion response, NACK/PLI handling, RED and FEC policy, speaker detection, reconnection and ICE restart, multi-node routing, observability, and mobile SDK integration. LiveKit OSS already provides all of this under Apache-2.0, so a custom SFU is only justified for a very specific need. For a single-developer team (the owner is a student), it is not advisable.

---

## Part B: Krisp

### B.1 Is LiveKit's Krisp Cloud-only?
**Yes, per LiveKit's own documents.**
- The comparison table's noise cancellation row reads, self-hosted: "Client-side WebRTC cancellation; ai-coustics supported with your own license key." Cloud: "Client-side WebRTC cancellation, plus enhanced models (Krisp, ai-coustics)." [C: https://docs.livekit.io/intro/cloud.md, https://docs.livekit.io/transport/self-hosting.md]
- "**LiveKit Cloud** includes access to advanced noise cancellation models (Krisp and ai-coustics)". "**LiveKit SDKs** support WebRTC noise and echo cancellation … in any deployment." [C: https://docs.livekit.io/transport/media/noise-cancellation.md]
- Frontend support table [C: same page]: Web `@livekit/krisp-noise-filter` (NC + BVC); Swift, Android (`io.livekit:krisp-noise-filter`), Flutter and React Native are NC only. "The BVC model is available in the JavaScript frontend; other frontend SDKs support the NC model only." "Not all browsers support the underlying Krisp SDK (including Safari < 17.4)."
- The pricing page lists, under Media transport, "Enhanced noise cancellation — Automatic background noise reduction for voice streams with Krisp NC" [C: https://livekit.com/pricing]. Separately, agent-side "Voice isolation" (Krisp VIVA) has 100/1,000/10,000 minutes included, then $0.0012/min [C: same page; the per-plan column mapping is from flattened text, so it is U].
- Only ai-coustics has documented self-hosted auth: "If you self-host your SFU … authenticate directly against ai-coustics by passing your own license key … Usage is then billed by ai-coustics, not LiveKit." The docs give no equivalent for Krisp [C: noise-cancellation.md]. ai-coustics "is not supported on clients for video conferencing" [C: same page].

### B.2 Can it run against self-hosted LiveKit? License or server check?
- **License**: npm `@livekit/krisp-noise-filter@0.4.5` (published 2026-09-17) has license "SEE LICENSE IN https://livekit.io/legal/terms-of-service" [C: https://registry.npmjs.org/@livekit/krisp-noise-filter]. The Android Maven POM `io.livekit:krisp-noise-filter` (latest 0.0.15) license is "LiveKit Cloud Terms of Service" [C: https://repo1.maven.org/maven2/io/livekit/krisp-noise-filter/0.0.15/krisp-noise-filter-0.0.15.pom].
- **ToS** (Last Updated 10/06/2026) [C: https://livekit.io/legal/terms-of-service]:
  - "The 'Services' means the LiveKit's cloud platform and related tools made available at https://cloud.livekit.io …"
  - "Any LiveKit SDKs or plugins are licensed under the Apache License 2.0 unless otherwise expressly specified by LiveKit…". The Krisp packages expressly point to the ToS instead.
  - 3.1 License grant: "…to access and use the Services to develop, test, create, operate, and commercialize Customer's [Application Content] … **when integrated with the Services**; provided that Customer may not distribute, resell, or make available the Services (including any Third-Party App) to any third party or on a standalone basis."
  - 3.2 Restrictions: no reverse engineering or "otherwise attempt to extract the source code of the Services or any Third-Party App"; "(b) attempt to disable, bypass, or circumvent any security mechanisms used by the Services"; "(e) circumvent the Services to avoid incurring Fees".
  - **Meaning [U, legal inference, not legal advice]**: the license covers the Krisp filter only while it is integrated with LiveKit Cloud. Patching out the `/settings` check, or self-hosting the models, to use it with a self-hosted SFU would likely breach 3.1, 3.2(a), 3.2(b) and 3.2(e).
- **Technical check**: the 0.4.5 bundle is obfuscated. I confirmed only that it contains `VITE_MODEL_PATH: "https://integrations.livekit.io/enc/v2"` and a settings URL built as `{ws→http}//{host}/settings` with `authorization: "Bearer " + token` [C: tarball dist/index.js]. In the OSS server (`pkg/service/server.go`), the registered routes are twirp services, `/rtc*`, WHIP, `/agent`, and `defaultHandler`, which returns `http.NotFound` for any path other than `/` [C: https://github.com/livekit/livekit/blob/master/pkg/service/server.go, rtcservice.go]. So against self-hosted OSS, the `/settings` call should return 404 and the filter should refuse to enable [U: inferred from code, not tested at runtime].
- The Android, iOS and Flutter Krisp packages may run a similar server check, but I did not verify this: Maven Central rate-limited the AAR download [U].

### B.3 Krisp's own SDK
- "Krisp SDK is a commercial product. Developers will need to obtain a commercial license from Krisp Technologies, Inc to integrate the SDK into their products. You can apply for a license on Krisp developer website." [C: https://sdk-docs.krisp.ai/docs/licensing-information.md]
- Platforms: "available for Windows, Mac, Linux, Web (JS/WASM), iOS and Android platforms." [C: https://krisp.ai/developers/]. There is a Web Browser SDK (WASM) with NC, BVC and VAD, and a Mobile SDK, which is a C library: "can also be integrated directly into WebRTC or other communication frameworks". Krisp publishes a WebRTC Android Krisp Module (github.com/krispai/webrtc-android-krisp-module), a bridge built on WebRTC v126 [C: https://sdk-docs.krisp.ai/docs/mobile-background-voice-cancellation.md, webrtc-android.md]. So it works with any WebRTC stack, which also means a self-hosted LiveKit, if licensed directly from Krisp [U for LiveKit-SDK compatibility specifically. Jitsi Meet does exactly this: C, jitsi-meet config.js].
- **Pricing**: not published. Access is via "Request SDK Access" or "Book a demo" [C: krisp.ai/developers]. A third-party listing claims "$5,000/year starting", which is unverified and not from Krisp [U].
- **BVC caveats** [C: https://sdk-docs.krisp.ai/docs/krisp-rtc-bvc-nc.md]: "BVC … removes other people's voices around the user". It works "above 8 kHz". It "works best with wired USB headsets with a boom microphone". Built-in mics "might demonstrate acceptable performance" only on high-quality devices such as MacBooks. CPU varies "within the range of 5-13%". NC latency is "with 10 ms frames at 16 kHz it is about 25 ms". Place AEC **before** Krisp NC on capture. The BVC device allow-list matters for phones [U for phone speaker-mode quality].
- Note: on the krisp.ai/developers marketing page, the BVC and "Outbound Noise Cancellation" descriptions appear swapped compared with the SDK docs. Trust the SDK docs.

### B.4 Open-source alternatives

| Tool | Solves | License | Browser | Android | Server | Notes / numbers |
|---|---|---|---|---|---|---|
| Browser built-in WebRTC APM (`echoCancellation`, `noiseSuppression`, `autoGainControl`) | AEC (AEC3), NS, AGC (agc/agc2), VAD | BSD (libwebrtc) | yes, native | yes (libwebrtc in LiveKit/WebRTC Android SDK) | n/a | WebRTC source has `aec3/`, `ns/`, `agc/`, `agc2/`, `vad/` [C: https://webrtc.googlesource.com/src/+/refs/heads/main/modules/audio_processing/]. LiveKit: "Leaving these WebRTC settings on is strongly recommended when you are not using enhanced noise cancellation" [C: noise-cancellation.md]. The NS is a classical (non-DNN) suppressor, weaker on non-stationary noise [U]. Chrome's `voiceIsolation` constraint shipped for **ChromeOS** (M123) [C: https://groups.google.com/a/chromium.org/g/blink-dev/c/hPMvCu-3iPA]. Other platforms are [U]. |
| RNNoise | NS only (no AEC/AGC; VAD probability output) | BSD-3 (Xiph/Mozilla/Amazon) [C: https://github.com/xiph/rnnoise COPYING] | yes, WASM: `@jitsi/rnnoise-wasm`, `@sapphi-red/web-noise-suppressor` (MIT, AudioWorklet; also bundles Speex and GTCRN nodes) [C: npm] | yes (C) | yes (C; Janus AudioBridge uses it [C]) | "runs about 60x faster than real-time on an x86 CPU", "about 7x faster than real-time on a Raspberry Pi 3", 10 ms lookahead, ~85 kB model [C: https://jmvalin.ca/demo/rnnoise/]. Default NS in Jitsi Meet [C]. |
| SpeexDSP | NS, AGC, VAD, AEC (MDF echo canceller + residual echo suppress) | BSD-style (Xiph) [C: COPYING] | yes, WASM (`SpeexWorkletNode`) [C: npm readme] | yes (C) | yes | API `SPEEX_PREPROCESS_SET_DENOISE/AGC/VAD/ECHO_STATE` [C: https://github.com/xiph/speexdsp/blob/master/include/speex/speex_preprocess.h]. Old DSP; weaker than RNNoise [U]. |
| webrtc-audio-processing (freedesktop) | standalone libwebrtc APM: AEC3, NS, AGC2 | BSD-3 (Google) [C] | not packaged for WASM [U] | yes (C++, but Android WebRTC already includes it) | yes | "a more Linux packaging friendly copy of the AudioProcessing module from the WebRTC project". v2.0 = "code from WebRTC M131" [C: https://gitlab.freedesktop.org/pulseaudio/webrtc-audio-processing]. |
| DeepFilterNet (v3) | NS (full-band 48 kHz), de-noise only | MIT OR Apache-2.0 [C: https://github.com/Rikorose/DeepFilterNet] | no official WASM [U]; Rust `libDF` with tract and a C API (`capi` feature) [C: libDF/Cargo.toml] | possible via the C API [U] | yes (Rust/LADSPA) | "single-threaded real-time factor of 0.19 on a notebook i5-8250U CPU" [C: arXiv 2305.08227]. LADSPA plugin "minimum latency is 20 ms" [C: ladspa/README.md]. Heavier than RNNoise (~5-6x per the RTFs) [U, computed]. |
| GTCRN (bonus) | NS | MIT [C] | community WASM demo (sherpa-onnx) [C: README links] | via ONNX [U] | yes | "48.2 K parameters and 33.0 MMACs per second", streaming "RTF of 0.07 on … i5-12400" [C: https://github.com/Xiaobin-Rong/gtcrn]. 16 kHz model [U]. |

**Published objective comparisons** (same test sets, numbers verbatim):
- DeepFilterNet2 paper, Table 1, VoiceBank+DEMAND, "RTFs … measured on a notebook Core i5-8250U": Noisy PESQ 1.97 · **RNNoise PESQ 2.33** (RTF 0.03, single-threaded, 0.06M params) · NSNet2 2.47 · PercepNet 2.73 · DeepFilterNet 2.81 · DeepFilterNet2 3.08 (RTF 0.04). Table 2, DNSMOS P.835 on DNS4 blind (SIG/BAK/OVL): Noisy 4.14/2.94/3.29 · **RNNoise 3.88/3.69/3.38** · NSNet2 3.87/4.21/3.59 · DeepFilterNet2 4.20/4.43/3.88 [C: https://arxiv.org/abs/2205.05474]
- DeepFilterNet3 paper: PESQ **3.17**, STOI 0.944 on VoiceBank+DEMAND [C: https://arxiv.org/abs/2305.08227]
- GTCRN README: VCTK-DEMAND PESQ RNNoise 2.29, DeepFilterNet 2.81, GTCRN 2.87. DNS3 blind DNSMOS OVRL: RNNoise 2.53 vs GTCRN 2.70 [C: GTCRN README]
- Krisp vs OSS: no independent objective benchmark found. LiveKit's own WER sample is a single clip (Original 117.6% to Krisp VIVA 11.8%) and is agent-side voice isolation, not frontend NC [C: noise-cancellation.md].

**Background voice cancellation in OSS**: none of RNNoise, SpeexDSP, WebRTC APM, DeepFilterNet or GTCRN removes competing speakers; they are trained to keep speech [U, inference from model descriptions]. Research target-speaker-extraction code exists (ModelScope ClearerVoice-Studio, OpenSpeakerBeam reimplementations), but I found nothing production-ready, real-time, or browser-ready [U: based on one search].

**Server-side (SFU) processing cost [U]**: an SFU forwards encrypted Opus without decoding. Server-side NS would need SRTP decrypt, Opus decode, NS, Opus re-encode, and re-encrypt per publisher. That adds CPU, ~10-30 ms latency, a second lossy encode, and breaks E2EE. LiveKit's Cloud Krisp for agents runs in the agent process, not in the SFU [C: docs list it under "Agents"]. Janus AudioBridge does RNNoise only because it already decodes to mix [C].

**Placement rule**: AEC before NS on capture (Krisp docs). Do not stack two DNN suppressors: "do not enable noise cancellation models in the frontend" when using them in the agent [C: noise-cancellation.md].

---

## Unverified points (summary)
1. The LiveKit audio benchmark's real-world relevance (3 kbps blank packets; the command says 1000 subs while the table says 3000).
2. Whether the Krisp filter fails against self-hosted OSS. This is inferred from code (`/settings` returns 404 in OSS routes) and was not runtime-tested.
3. The legal reading that using the LiveKit Krisp packages with a self-hosted SFU breaches the ToS. This is an inference, not legal advice.
4. Whether the Android/iOS/Flutter LiveKit Krisp packages have the same server check (the AAR could not be downloaded; HTTP 429).
5. Krisp SDK pricing: not published. The $5k/yr figure is third-party only.
6. mediasoup RED: absent from supported codecs, so likely not supported. Recording and TURN are not built in.
7. The Jitsi perf post's date and its relevance to current JVB. The Janus language and its TURN situation.
8. DeepFilterNet WASM availability, and GTCRN's sample rate and Android path.
9. That no OSS does BVC (based on a limited search).
10. The per-plan mapping of LiveKit pricing rows (from flattened page text).
