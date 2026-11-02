# Public architecture benchmark: Azar/Hyperconnect, OmeTV, Discord, Jitsi, Signal, LiveKit
Research date: 2026-11-02. Every claim below is tagged:
- `[confirmed: URL]`: read in a primary or official source fetched for this research (blog, docs, source code, press release).
- `[secondary: URL]`: Korean press or a third-party article quoting the company. Not the company's own engineering text.
- `[inference]`: my reasoning, not something the company stated.
- "Not public": I searched and found no primary source.

---

## 1. Azar / Hyperconnect (Hakuna Live is also Hyperconnect; owned by Match Group)

**Company and scale**
- Match Group bought Hyperconnect for US$1.725B (50% cash, 50% stock). The deal was announced 2021-02-09 and closed 2021-06-17. At that time Azar had 540M+ downloads since 2014, Hakuna Live (launched 2019) had 23M+ downloads, 2020 revenue was >$200M, the company had about 400 staff in Seoul, and >75% of usage came from Asia. [confirmed: https://mtch.com/?p=1768]
- The same release credits Hyperconnect with deploying mobile WebRTC in 2014, "scalable, low-cost video technology" and "award-winning on-device AI". [confirmed: https://mtch.com/?p=1768]
- Cloudflare's case study cites 100B+ Azar matches. [confirmed: https://www.cloudflare.com/case-studies/hyperconnect/]
- Korean press reported up to 85 million WebRTC calls per day and about 23 billion video calls in 2019. [secondary: https://m.ddaily.co.kr/page/view/2020052909071948897]
- "HyperRTC" is the company's own name for its mobile-optimised video technology. Its tech page has no internals. [confirmed: https://hyperconnect.com/en/tech/webrtc/]
- Hyperconnect forks the libwebrtc release branch (m78 as of 2020), cherry-picks upstream fixes, and builds its own AAR and iOS libraries. [confirmed: https://hyperconnect.github.io/2020/08/13/webrtc-library.html]

**Media transport**
- **1:1 random video (Azar):** The blog shows a 1:1 call as a direct PeerConnection between two users, with a signaling server exchanging offer and answer. [confirmed: https://hyperconnect.github.io/2022/12/30/introduction-to-media-server.html]
  - Press reports group video of up to 8 mobile devices "without server involvement" (P2P). [secondary: ddaily link above]
  - **TURN usage, TURN share and TURN vendor are not public.** No source mentions them. That Azar falls back to its own or rented TURN is [inference].
- **Live (Hakuna Live, Azar Live) and group calls:** These run on an in-house WebRTC SFU-style media server with an **Origin-Edge** split. [confirmed: https://hyperconnect.github.io/2022/12/30/introduction-to-media-server.html]
  - Origin servers accept only hosts. Edge servers accept only viewers.
  - A "Media Server Broker" handles load balancing and routing.
  - When an Edge has no viewers yet for a stream, it relays that stream from the Origin.
  - Hosts send simulcast.
  - Viewers connect to their local region. When the host is in another region, the viewer's local Edge pulls from the remote Origin.
  - Reported results for Azar Live: connection time about 25% lower, RTT about 30% lower (up to 1/30 in some regions), packet loss up to 80% lower.
- **Group calls (Pairs, 2022):** Each participant opens two PeerConnections, one to an Origin for sending and one to an Edge for receiving. Rooms hold at most 50 people. The blog says load grows with the square of the participant count (N²). [confirmed: https://hyperconnect.github.io/2024/09/25/introduction-to-groupcall-media-server.html]
- **Signaling:** The blog names WebSocket, STOMP or gRPC as options. [confirmed: 2022 media-server post] A separate SRE post confirms a STOMP-over-TLS component in production. [confirmed: https://hyperconnect.github.io/2024/06/24/global-network-troubleshooting.html]
- **Not public:** the SFU's implementation language, and whether it is built on Janus, mediasoup or similar.

**IP privacy for strangers**
- Not public. If 1:1 calls are direct P2P (the blog's diagram), each peer can see the other's IP unless TURN is forced. [inference]

**Matching**
- **Earlier approach:** Heuristic rules (shared language, age group, recent reports) grew to "hundreds of thousands of lines". [confirmed: https://hyperconnect.github.io/2024/11/19/azar-recommendation-model.html]
- **Current model:** A two-tower model (user and peer embeddings, dot product predicts chat time) running over a matching pool.
  - Expensive embeddings are computed O(N) times. Only the cheap dot products run O(N²).
  - Session embeddings come from a causal transformer, are computed asynchronously and are cached.
  - p90 latency fell from 236 ms to 48 ms and p99 from 290 ms to 70 ms.
  - The model is multi-task: chat time, reports, friend requests, swipes.
  - Dot products run on CPU with BLAS. Embeddings run on GPU/NPU.
  - [confirmed: same URL]
- **Event pipeline:** Kafka plus Flink stream joins (match request → conversation done or cancelled). Redis `SET NX` gives exactly-once dedup, which cut dedup time from about 300 ms to under 3 ms. Deployment is blue-green through Spinnaker. [confirmed: https://hyperconnect.github.io/2025/06/11/azar-flink-real-time-stream-join-service.html]

**Global deployment**
- AWS VPC is in the request path, and Global Accelerator was tested. [confirmed: global-network-troubleshooting post]
- Cloudflare provides Argo Smart Routing, CDN, Workers (image resizing) and DDoS/app security. [confirmed: https://www.cloudflare.com/case-studies/hyperconnect/]
- The media upload service covers 230+ countries. Global Accelerator and CloudFront both showed long-tail latency, so they moved to Cloudflare edge. Upload p99 fell from 180 s to 1.5 s. [confirmed: https://hyperconnect.github.io/2021/12/06/global-media-management-system.html]
- Not public: the list of media-server regions.

**DB and storage**
- **ScyllaDB** is the company-wide NoSQL store, run on EKS on i4i.4xlarge nodes with local NVMe. "Super Disk" mirrors local NVMe and EBS with mdadm RAID-1 using write-mostly, an idea copied from Discord. Node recovery went from 3–12 h to about 1 h. [confirmed: https://hyperconnect.github.io/2025/05/15/Building-Resilient-High-Performance-ScyllaDB-Clusters-with-Super-Disk.html]
- **ML serving stores:** MySQL, MongoDB, Cassandra/ScyllaDB and Redis, each chosen per access pattern. [confirmed: https://hyperconnect.github.io/2022/07/11/data-stores-for-ml-apps.html]
- **Media files:** S3. Media history goes to ScyllaDB, then CDC to Kafka, then OpenSearch. [confirmed: GMMS post]
- **Other posts:** PostgreSQL, Redis distributed locks, CDC/CQRS, BigQuery. [confirmed: post titles on https://hyperconnect.github.io/]

**Scaling patterns**
- Kubernetes (EKS) with Istio canary releases and traffic mirroring. Half of workloads moved to ARM64. [confirmed: post titles on https://hyperconnect.github.io/]
- A media-server load tester was built in-house (2022-03-04 post title). [confirmed: blog index]

**Moderation and user protection**
- **On-device video moderation (press release dated 2020-12-10):**
  - First built in 2017.
  - Runs on the phone with no server. The release says personal video is never sent to a server.
  - Detects violating video **before transmission** and blocks it.
  - Reported speeds: 0.004 s on iPhone XR, 0.006 s on Galaxy S10.
  - Rolled out to Slide and Hakuna. The release does not explicitly say it is deployed in Azar.
  - [confirmed: https://hyperconnect.com/ko/company/news/5fd1802710a2830039d3dcd8/]
- **Policy research:** WSDM 2023 paper "Reliable Decision from Multiple Subtasks through Threshold Optimization: Content Moderation in the Wild" (TruSThresh). Models learn separate subtask scores (for example minor exposure, inappropriate behaviour), and per-policy thresholds are optimised so models need no retraining when a policy changes. [confirmed: https://career.hyperconnect.com/post/63c9dda97f12a9003d685116/]
- **Human review:** The CEO told TechCrunch that automated tools flag image, audio and text first, then human moderators review. [secondary: TechCrunch quote via search result; not fetched directly]
- **Store listing claims:** In-match blur, and automatic cancellation of matches that fall below safety guidelines. [secondary]
- **Recommendation signal:** The matching model's multi-task heads include reports, so expected abuse probability lowers match ranking. [confirmed: recsys post] Using it to down-rank abusers specifically is [inference].
- **ML serving cost:** Moving to AWS Inferentia cut ML serving cost by about 75–80%+. The post does not name moderation models. [confirmed: https://hyperconnect.github.io/2022/12/13/infra-cost-optimization-with-aws-inferentia.html]

**Media cost handling**
- Running 1:1 calls P2P avoids server bandwidth for the bulk of traffic. [inference from the P2P diagram]
- Origin-Edge relays a stream once per region instead of once per viewer. [confirmed: 2022 media post]

**Own technology vs vendors**
- **Built in-house:** the libwebrtc fork, the media server, the moderation models, the matching ML.
- **Bought or rented:** AWS (EKS, S3, Inferentia), Cloudflare, ScyllaDB (OSS/enterprise not stated), Kafka/Flink.
- [confirmed across the posts above]

---

## 2. OmeTV

- **Operator:** "Ome.TV, Bad Kitty's Dad, LDA", Corroios, Portugal. Hosting is in Germany, and Cloudflare sits in front. [confirmed: https://ome.tv/privacy/]
- **Transport:**
  - The privacy policy says video chat "may require a direct connection between users' devices". In P2P connections the IP address is made available to the other device, though it is not shown in the UI.
  - The policy says the video/audio stream is "processed in real time; not stored".
  - [confirmed: https://ome.tv/privacy/]
- **Moderation:**
  - The policy lists a "Picture" data field under video chat and ban records, including text-chat messages kept only for behaviour-related bans. It does not say how or when pictures are taken.
  - The policy also mentions age signals from Google Play and the App Store, and third-party age verification.
  - [confirmed: https://ome.tv/privacy/]
- **Not public:** media-server, TURN, matching, DB and scaling details. No engineering blog or conference talk exists. Third-party "how OmeTV works" articles are SEO content and were excluded.

---

## 3. Discord

**Voice and media** (source: https://discord.com/blog/how-discord-handles-two-and-half-million-concurrent-voice-users-using-webrtc)
- **Topology:** All media goes client → server (SFU). There is no P2P, even for 1:1 calls. [confirmed]
  - Reasons given: P2P is "prohibitively expensive" as participants grow, a server **hides user IPs** (DDoS protection), and a server enables moderation such as server-mute (the SFU drops packets).
- **Client stacks:**
  - Native apps use a custom C++ media engine on libwebrtc and the low-level `webrtc::Call` API. They skip SDP, exchange about 1000 bytes at join, and encrypt with **Salsa20** instead of DTLS/SRTP.
  - Browsers get under 1200 bytes from the server and synthesize SDP locally. Full SDP would be close to 10 KB round trip.
  - [confirmed]
- **No ICE needed:** Every client connects to a public relay, and periodic pings keep NAT bindings open. [confirmed]
- **Silence:** Nothing is sent during silence, which saves bandwidth and CPU. The SFU rewrites sequence numbers to cover the gaps. [confirmed]
- **Language split:** Signaling is in Elixir and the SFU in C++. Gateway and Guilds services run on GCP. Voice servers run on physical servers from "a handful of providers". [confirmed]
- **Server allocation:**
  - Voice servers report health and load to **etcd** service discovery.
  - The Guilds service assigns the **least-utilised voice server in the guild's region**.
  - All voice channels in one guild share one server.
  - [confirmed]
- **Failover:**
  - If the SFU crashes, it restarts and signaling rebuilds its state.
  - If a server dies, it fails its ping checks and leaves discovery. Clients notice the dropped WebSocket and ask for reassignment through the gateway.
  - A server under **DDoS** is pulled from discovery and its guilds move elsewhere.
  - [confirmed]
- **Numbers (2018):**
  - 850+ voice servers, 13 regions, 30+ data centres.
  - 2.6M+ concurrent voice users.
  - 220 Gbps egress and 120 Mpps.
  - [confirmed]
- **Codec:** The post does not mention Opus. That Discord uses Opus is [inference / common knowledge, not confirmed here].
- **DAVE (2024):** End-to-end encryption for DMs, group DMs, voice channels and Go Live. [confirmed: https://discord.com/blog/meet-dave-e2ee-for-audio-video]
  - Uses WebRTC encoded transforms for per-frame encryption and **MLS** for group keys, with the voice gateway acting as MLS delivery service.
  - The SFU still forwards packets but cannot decrypt them.
  - Open source: github.com/discord/libdave. Audited by Trail of Bits.
  - Text messages are *not* end-to-end encrypted and remain moderated.

**Gateway, presence and fan-out**
- **Elixir design** (https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users):
  - One GenServer per user session and one per guild.
  - **Manifold** batches sends per remote node, so a guild makes one `send/2` per node.
  - **FastGlobal** stores the hash ring.
  - **Semaphore** throttles reconnect storms.
  - Numbers: "nearly five million concurrent users", up to 500,000 sessions per VM, guilds up to 30,000 concurrent users, average user in 5 guilds.
  - [confirmed]
- **Rust NIF:** A Rust NIF (via Rustler) implements the member-list SortedSet, with 0.61–3.68 µs per operation on sets up to 1M items. It runs on every guild, up to about 200k members. [confirmed: https://discord.com/blog/using-rust-to-scale-elixir-for-11-million-concurrent-users]
- **Public bot gateway sharding:** `shard_id = (guild_id >> 22) % num_shards`, at most 2500 guilds per shard, 120 gateway events per 60 s per connection, `zlib-stream` or `zstd-stream` compression, and heartbeat with jitter plus resume. [confirmed: https://docs.discord.com/developers/events/gateway]

**Database**
- **Cassandra → ScyllaDB:** 12 Cassandra nodes in 2017, 177 nodes early 2022, 72 ScyllaDB nodes after the move. [confirmed: https://discord.com/blog/how-discord-stores-trillions-of-messages]
  - Each ScyllaDB node has 9 TB versus an average of 4 TB per Cassandra node.
  - p99 history reads: 40–125 ms → 15 ms. p99 inserts: 5–70 ms → 5 ms.
  - Messages are partitioned by (channel_id, time bucket). IDs are Snowflakes. Replication factor is 3.
- **Rust data services:** A layer between the API and the database with request **coalescing** and **consistent-hash routing by channel_id**. The migrator was rewritten in Rust, cutting the estimate from 3 months to 9 days at up to 3.2M rows/s. [confirmed: same URL]
- **Super-disk:** Local SSDs in RAID0, mirrored (RAID1, write-mostly) with a GCP Persistent Disk. Coverage cites 4 billion messages per day. [secondary: https://www.theregister.com/2022/08/16/discord_gcp/; original at https://discord.com/blog/how-discord-supercharges-network-disks-for-extreme-low-latency, not fetched]

**Moderation:** No public detail on voice moderation technology beyond server-mute at the SFU. [confirmed absence in the voice post] Not public otherwise in the sources fetched.

**Own technology vs vendors:**
- **Built in-house:** the C++ SFU, the Elixir and Rust services, the native media engine.
- **Rented or third-party:** GCP for core services, bare-metal providers for voice, ScyllaDB.
- [confirmed]

---

## 4. Jitsi (Videobridge / meet.jit.si / 8x8)

- **1:1 calls:** With `p2p.enabled`, "when there are only 2 participants in the conference an attempt to make direct connection will be made". If that succeeds, the conference stops sending through the JVB. When a 3rd participant joins, the call moves back to the bridge. `backToP2PDelay` sets how long to wait before returning to P2P after the 3rd person leaves. [confirmed: https://jitsi.github.io/lib-jitsi-meet/classes/JitsiConference.JitsiConference.html]
- **Basic scaling:** One signaling box (nginx + Prosody + Jicofo; 4 CPU / 8 GB) plus N JVBs (4–8 CPU / 8 GB). JVBs register with Prosody on 5222, and Jicofo picks a JVB per conference. Media uses UDP 10000. [confirmed: https://jitsi.github.io/handbook/docs/devops-guide/devops-guide-scalable/]
- **Cascading (Octo → secure-octo, meet.jit.si):** [confirmed: https://jitsi.org/blog/bridge-cascading-is-back/]
  - Geo-cascading first shipped in 2018. It was disabled in 2020 under pandemic load: interconnecting every bridge broke at **50+ shards and 2000+ JVBs**.
  - The new design separates signaling-only shards (a few per region) from **regional auto-scaling JVB pools** plus "remote" pools serving other regions.
  - Bridges pair over ICE/DTLS/SRTP ("secure-octo"), so no VPN is needed between them.
  - "Region groups" (for example Frankfurt+London) keep two near-identical regions from cascading with each other.
  - Next-hop RTT across continents fell 29% (223 → 158 ms).
  - Colibri2 (late 2021) replaced COLIBRI.
- **Shared code:** Signal's SFU reuses Jitsi's dominant-speaker identification algorithm. [confirmed: https://github.com/signalapp/Signal-Calling-Service]
- **Not covered:** moderation and DB. Jitsi is a meeting product, so these were not researched.

---

## 5. Signal

- **1:1 calls (2017 post):** Signal "can attempt to establish a peer-to-peer connection".
  - P2P is attempted only when you place the call or the caller is **in your contacts**. Calls from **non-contacts are relayed** through Signal's servers so they cannot learn your IP or rough location.
  - An optional setting relays **all** calls.
  - [confirmed: https://signal.org/blog/signal-video-calls/]
  - The setting's current name, "Always relay calls", and where it sits in the menus are [secondary: privacy forums]. The support page returned 403.
- **Group calls:** [confirmed: https://signal.org/blog/how-to-build-encrypted-group-calls/]
  - Signal chose an SFU because mesh does not scale and mixing breaks end-to-end encryption.
  - A modified third-party SFU could not scale past 8 participants, so Signal wrote its own in **Rust**.
  - Group size grew from 5 to 40 participants.
  - Congestion control is googcc with transport-cc, reimplemented in-house.
  - Senders use **VP8 simulcast**, and the SFU rewrites SSRC, sequence numbers, timestamps and PictureID into one stream.
  - Frames are encrypted end-to-end in RingRTC (SFrame-like). Keys rotate on every join or leave and take effect after 3 s. The SFU sees only rewritten headers.
- **Current limit:** Group calls support up to 75 people as of 2026, per the update note on the call-links post. [confirmed by search snippet of https://signal.org/blog/call-links/; page not opened directly]
- **Signal-Calling-Service source code (AGPL-3.0)** [confirmed: https://github.com/signalapp/Signal-Calling-Service, read locally]
  - **frontend** is a stateless signaling and routing service on GCP.
    - It keeps the room → backend IP and region mapping in **DynamoDB**, along with call-link records that carry TTL deletion.
    - It redirects clients to `https://sfu.<region>.voip.signal.org`.
    - It finds backends by listing a GCP regional instance group.
  - **Load balancing:** The frontend health-checks every backend, which reports `cpu_idle_pct`. It then does weighted-random "urn" selection, so idle backends get more new calls (`frontend/src/load_balancer.rs`).
  - **backend** is the SFU.
  - **Metrics:** Datadog StatsD.
- **RingRTC:** A Rust middleware library on top of WebRTC (via the `webrtc-sys` binding), with Android and iOS builds. [confirmed: https://github.com/signalapp/ringrtc]
- **TURN:** Signal's own TURN servers are not documented in the fetched sources. [Not public in sources fetched]
- **Moderation:** None by design, because the service is end-to-end encrypted.

---

## 6. LiveKit (open-source SFU and LiveKit Cloud)

- **Open-source server:** Written in Go on **Pion WebRTC**, Apache-2.0. Features include simulcast, SVC (VP9, AV1), speaker detection and "distributed and multi-region". [confirmed: https://github.com/livekit/livekit]
- **Self-hosted distribution:** [confirmed: https://docs.livekit.io/home/self-hosting/distributed/]
  - **Redis** is both the shared store and the message bus. Nodes report load to Redis.
  - Clients hold a WebSocket to any node, which proxies to the node hosting the room.
  - **A room must fit on a single node.**
  - Node selection keeps nodes under `sysload_limit` (0.5 in the sample), prefers the closest region, and picks randomly among those.
  - Graceful **draining** on SIGTERM.
  - Multi-region uses geo-DNS in front of regional load balancers.
- **Deployment:** [confirmed: https://docs.livekit.io/home/self-hosting/deployment/]
  - **Embedded TURN** that only clients with an established signal connection can use: TURN/TLS on 5349 or 443, TURN/UDP on 443.
  - RTC UDP ports 50000–60000 and TCP 7881.
  - Use host networking and 10 Gbps NICs.
- **Benchmarks on one GCP c2-standard-16:** [confirmed: https://docs.livekit.io/home/self-hosting/benchmark/]
  - Audio: 10 publishers / 3000 subscribers at 80% CPU.
  - 720p meeting: 150 publishers / 150 subscribers at 85% CPU.
  - Livestream: 1 publisher / 3000 subscribers, 531 MBps out, 92% CPU.
- **LiveKit Cloud mesh:** [confirmed: https://livekit.com/blog/scaling-webrtc-with-distributed-mesh/]
  - A session spans many servers, and each client uses the nearest PoP (target <100 ms).
  - Servers relay RTP to each other over a **custom FlatBuffers protocol, not WebRTC**.
  - Each server discovers and chooses its own upstream sources, with no central coordinator.
  - Participant state is owned by the participant's own server and replicated over a **pub-sub bus** (not a database; about 100 bytes per participant, eventually consistent). Postgres, Spanner and CockroachDB were rejected.
  - Live migration uses **ICE restart**, with under 1 s of disruption versus up to 5 s for a full reconnect.
  - The network is an encrypted multi-cloud overlay. Target availability is 99.99%.
  - Which pieces are proprietary is [inference]: the mesh, relay and migration layers appear Cloud-only.
- **Moderation, DB and matching:** Not applicable. LiveKit is infrastructure only.

---

## 7. Cross-company summary: how 1:1 stranger video is carried

| Company | 1:1 media path | Stranger IP hidden? | Source |
|---|---|---|---|
| Azar | P2P PeerConnection (TURN not public) | Not public; likely exposed if P2P [inference] | hyperconnect 2022 media post |
| OmeTV | May be direct P2P; IP made available to peer device | No, when P2P | ome.tv/privacy |
| Discord | Always via SFU (no P2P) | Yes, by design | Discord 2.5M voice post |
| Jitsi | P2P when exactly 2 people, JVB when 3+ | No when P2P | lib-jitsi-meet docs |
| Signal | P2P only with contacts; non-contacts relayed; option to always relay | Yes for non-contacts | signal.org/blog/signal-video-calls |
| LiveKit | Always SFU (+ embedded TURN) | Yes (peers only see the SFU) [inference from SFU model] | LiveKit docs |

---

## 8. Techniques these companies have described publicly that a 1–2 person team can actually apply

1. **Relay calls between strangers; allow P2P only after mutual trust.**
   - **How:** Force TURN relay (`iceTransportPolicy: "relay"`) for random matches. Allow P2P only after both sides "add friend".
   - **Source:** Signal relays non-contacts by default (https://signal.org/blog/signal-video-calls/). Discord routes everything through servers to hide IPs and blunt DDoS (Discord voice post).
2. **Use P2P when exactly two people are in the call, and move to an SFU when a third joins.**
   - **How:** Keep P2P for 1:1 to save bandwidth. Switch to the SFU at 3+ participants, with a delay before switching back to stop flapping.
   - **Source:** Jitsi `p2p.enabled` and `backToP2PDelay` (lib-jitsi-meet docs).
3. **Pick the least-loaded media server per region, using health-reported load.**
   - **How:** Each server reports its load. A small registry picks the least-loaded server per region, or does weighted-random by idle CPU.
   - **Source:** Discord uses etcd and least-utilised selection. Signal uses `cpu_idle_pct` with urn-weighted random (Signal-Calling-Service `frontend/src/load_balancer.rs`). LiveKit uses `sysload_limit` with random choice among qualifying nodes.
   - **Small-team note:** a plain Redis hash replaces etcd. [inference]
4. **Pull an attacked or dead server out of rotation and let clients rejoin.**
   - **How:** The client treats a dropped WebSocket as a cue to ask for a new server. The server side just removes the node from discovery.
   - **Source:** Discord voice post. LiveKit adds graceful SIGTERM draining (distributed docs), which helps zero-downtime deploys.
5. **Keep the signaling payload small; skip trickle-ICE when the server has a public IP.**
   - **Source:** Discord's ~1 KB join and client-side SDP synthesis.
   - **Small-team note:** This is mostly relevant if you control native clients. For a web or app MVP, standard SDP is fine. [inference]
6. **Use the media server's built-in TURN on 443 (TLS and UDP).**
   - **How:** Saves running a separate coturn fleet. TURN credentials are issued only to authenticated signaling sessions.
   - **Source:** LiveKit deployment docs.
7. **Make the SFU tier stateless behind a tiny room → server table.**
   - **Source:** Signal keeps room → backend IP and region in DynamoDB, with TTL cleanup of ended calls (Signal-Calling-Service `storage.rs`). LiveKit does the same with Redis.
8. **Send regional viewers to a regional Edge that pulls from the host's Origin once.**
   - **Use:** Only if 1:N live streaming is added.
   - **Source:** Hyperconnect Origin-Edge post (25% faster connect, about 30% lower RTT, up to 80% less loss).
9. **Run moderation ML on the device and block before transmission.**
   - **How:** A TFLite/CoreML NSFW classifier on sampled local frames. Blur or stop sending locally, and send only a flag (no video) to the server.
   - **Source:** Hyperconnect 2020 press release (0.004–0.006 s per frame, video never sent to a server).
   - **Small-team note:** Feasible with an open NSFW model. [inference]
10. **Learn separate subtask scores and tune thresholds per policy.**
    - **How:** Store per-category scores (nudity, minor, violence). Change policy thresholds in config without retraining.
    - **Source:** Hyperconnect WSDM 2023 (TruSThresh).
11. **Feed report probability into matching.**
    - **How:** A small team can start with a heuristic score: shared language, region, recent reports. Add an embedding model only at scale.
    - **Source:** Azar's multi-task matching includes a "reports" head and began as heuristic rules (Azar recsys post).
12. **Write per-sender state once and fan it out by node, not by recipient.**
    - **Use:** For presence and fan-out, though only once there are thousands of concurrent users.
    - **Source:** Discord Manifold (Elixir post). LiveKit owner-writes state over pub-sub.
    - **Small-team note:** With Supabase Realtime or a single Node or Elixir process, simple per-room channels suffice at small scale. [inference]
13. **Coalesce requests for hot rows and route by key.**
    - **How:** Coalesce identical concurrent reads, and use consistent-hash routing by room or channel ID.
    - **Source:** Discord's Rust data services.
    - **Small-team note:** Applicable in an API layer even with Postgres. [inference]
14. **Rely on transport-cc/googcc plus simulcast in the SFU instead of custom bitrate logic.**
    - **Source:** Signal group-calls post. Off-the-shelf SFUs (LiveKit/Pion) already do this. [inference]
15. **Use a CDN or edge for uploads and API in regions with poor ISPs, and monitor errors per country and carrier.**
    - **Source:** Hyperconnect GMMS (Cloudflare edge, upload p99 from 180 s to 1.5 s) and the carrier/SNI troubleshooting post (always send SNI in TLS).
16. **Pin the libwebrtc version and backport fixes, or use the platform SDK, rather than tracking upstream.**
    - **Source:** Hyperconnect webrtc-library post.
    - **Small-team note:** Use stock SDKs; this is only relevant if forking. [inference]

---

## 9. Unverified / not public (do not cite as fact)

- **Azar 1:1:** TURN usage share, TURN vendor, whether stranger IPs are hidden, SFU implementation language, region list.
- **Azar on-device moderation:** whether it currently runs in Azar 1:1. The press release names Slide and Hakuna. The store listing mentions "in-match blur" (secondary).
- **"85M WebRTC calls/day" and "8-device serverless group call":** Korean press (ddaily 2020) only, not Hyperconnect engineering text.
- **TechCrunch quote** from Azar's CEO about AI first, then human moderators: seen only in a search snippet.
- **OmeTV:** everything beyond the privacy policy (media servers, TURN, matching, moderation tech, scaling).
- **Discord:** Opus settings (not in the voice post); super-disk details (only The Register's summary was read; Discord's own post was not fetched); current 2026 voice server, region and CCU numbers (the 2018 figures are the latest fetched).
- **Signal:** the exact current menu name of "Always relay calls" (support page returned 403); TURN infrastructure details; the 75-participant figure (search snippet of the call-links post).
- **LiveKit Cloud:** which mesh components are closed source (the article does not say explicitly).
