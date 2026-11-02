# Yaari infra cost model — official price research (fetched 2026-11-02)

Method: every number below was read today from the official page listed (WebFetch and/or raw `curl` + grep of the page HTML / official price-list JSON).
"Verified Y" = number seen on the official page today. "N" = not published / not found / ambiguous — do not use without checking.
All prices exclude tax/VAT/GST unless noted.

## 1. Supabase

| Item | Price | Unit | URL | Quote / note | Verified |
|---|---|---|---|---|---|
| Free plan | $0 | /mo | https://supabase.com/pricing | 50,000 MAU; "500 MB database size per project"; 5 GB egress + 5 GB cached egress; Realtime 200 concurrent, 2M msgs/mo. "Free projects are paused after 1 week of inactivity. Limit of 2 active projects." | Y |
| Pro plan | from $25 | /mo | same | "100,000 monthly active users then $0.00325 per MAU"; "8 GB disk size per project then $0.125 per GB"; "250 GB egress then $0.09 per GB"; "250 GB cached egress then $0.03 per GB" | Y |
| Team plan | from $599 | /mo | same | Same usage quotas/overage as Pro (100k MAU, 8 GB, 250 GB egress, Realtime 500 conn / 5M msgs) | Y |
| Realtime concurrent peak connections (Pro/Team) | 500 incl., then $10 | per 1,000 | same | "500 included then $10 per 1000" | Y |
| Realtime messages (Pro/Team) | 5M incl., then $2.50 | per 1M | same | "5 Million included then $2.50 per Million" | Y |
| Compute credits | $10 | /mo | same | Pro & Team include "$10/month in compute credits, which covers one Micro instance" | Y |
| Spend cap | — | — | https://supabase.com/docs/guides/platform/cost-control | Pro only, on by default. "After exceeding the quota for a usage item, further usage of that item is disallowed until the next billing cycle." Covers Disk Size, Egress, Edge Fn invocations, Logs, MAU, Realtime Messages, Realtime Peak Connections, Storage… NOT covered: Compute, Branching/Read-replica compute, Custom Domain, extra IOPS/throughput, IPv4, Log drains, MFA Phone, PITR | Y |

Compute add-ons — https://supabase.com/docs/guides/platform/compute-and-disk (Verified Y)

| Size | $/hr | ~$/mo | CPU | RAM | Max DB conn | Max pooler clients |
|---|---|---|---|---|---|---|
| Nano (free) | 0 | 0 | Shared | up to 0.5 GB | 60 | 200 |
| Micro | 0.01344 | ~10 | Shared | 1 GB | 60 | 200 |
| Small | 0.0206 | ~15 | Shared | 2 GB | 90 | 400 |
| Medium | 0.0822 | ~60 | Shared | 4 GB | 120 | 600 |
| Large | 0.1517 | ~110 | Dedicated 2 vCPU | 8 GB | 160 | 800 |
| XL | 0.2877 | ~210 | Dedicated 4 vCPU | 16 GB | 240 | 1,000 |
| 2XL | 0.562 | ~410 | Dedicated 8 vCPU | 32 GB | 380 | 1,500 |
| 4XL | 1.32 | ~960 | Dedicated 16 vCPU | 64 GB | 480 | 3,000 |
| 8XL | 2.562 | ~1,870 | Dedicated 32 vCPU | 128 GB | 490 | 6,000 |
| 12XL | 3.836 | ~2,800 | Dedicated 48 vCPU | 192 GB | 500 | 9,000 |
| 16XL | 5.12 | ~3,730 | Dedicated 64 vCPU | 256 GB | 500 | 12,000 |

Realtime limits — https://supabase.com/docs/guides/realtime/limits (Verified Y)

| Limit | Free | Pro | Pro (no spend cap) | Team | Enterprise |
|---|---|---|---|---|---|
| Concurrent connections | 200 | 500 | 10,000 | 10,000 | 10,000+ |
| Messages/sec | 100 | 500 | 2,500 | 2,500 | 2,500+ |
| Channel joins/sec | 100 | 500 | 2,500 | 2,500 | 2,500+ |
| Channels per connection | 100 | 100 | 100 | 100 | 100+ |
| Presence keys/object | 10 | 10 | 10 | 10 | 10+ |
| Presence msgs/sec | 20 | 50 | 1,000 | 1,000 | 1,000+ |
| Broadcast payload | 256 KB | 3,000 KB | 3,000 KB | 3,000 KB | 3,000+ KB |
| Postgres changes payload | 1,024 KB | 1,024 KB | 1,024 KB | 1,024 KB | 1,024+ KB |

## 2. LiveKit Cloud — https://livekit.com/pricing (livekit.io redirects here)

| Item | Build | Ship | Scale | Enterprise | Verified |
|---|---|---|---|---|---|
| Price | $0/mo | "STARTING AT $50/mo" | "STARTING AT $500/mo" | Custom | Y |
| WebRTC participant minutes | 5,000 incl. | 150,000 incl., "then $0.0005 per min" | 1.5M incl., "then $0.0004 per min" | Custom | Y |
| Concurrent connections ("end users and agents") | 100 | 1,000 | 5,000 | Custom | Y |
| Downstream data transfer | 50 GB incl. | 250 GB, "then $0.12 per GB" | 3 TB, "then $0.10 per GB" | Custom | Y |
| Agent session minutes | 1,000 | 5,000 then $0.01/min | 50,000 then $0.01/min | Custom | Y |
| Enhanced noise cancellation (Krisp NC) | included | included | included | included | Y (plan JSON value:true for all 4; docs: "Background noise suppression models are included with LiveKit Cloud") |
| Voice isolation (Krisp VIVA / ai-coustics) | 100 min | 1,000 min then $0.0012/min | 10,000 min then $0.0012/min | Custom | Y ("All voice isolation models incur an additional cost") |
| TURN separate price | not listed | not listed | not listed | — | N (no separate TURN line on pricing page; billing docs https://docs.livekit.io/home/cloud/billing/ do not mention TURN) |
| Build overage | not listed (no overage price published for Build) | | | | N |

Noise-cancellation doc: https://docs.livekit.io/home/cloud/noise-cancellation/ . Billing doc: WebRTC "metered on a combination of time and data transfer", 10-second minimum per session, 0.01 GB minimum increment.

## 3. Vercel

| Item | Price | Unit | URL | Quote / note | Verified |
|---|---|---|---|---|---|
| Hobby | $0 | /mo | https://vercel.com/pricing | "Our Hobby plan is for personal, non-commercial use." Fast Data Transfer "100 GB / month included"; Hobby users "are limited to the usage caps and cannot purchase additional usage." | Y |
| Hobby commercial restriction | — | — | https://vercel.com/docs/limits/fair-use-guidelines | "Hobby teams are restricted to non-commercial personal use only. All commercial usage of the platform requires either a Pro or Enterprise plan." Commercial includes "Any method of requesting or processing payment from visitors", "The inclusion of advertisements…". (page last_updated 2026-09-14) | Y |
| Pro | $20 | /mo (per plan; includes "$20 included credit") | https://vercel.com/pricing | "1TB / month included; then starting at $0.15 per GB" | Y |
| Pro Flat Rate CDN | incl. / $20 / $100 / $300 | /mo | https://vercel.com/changelog/flat-rate-cdn-is-now-ga-for-pro-teams (2026-09-08) | Table: Included with Pro 1M req, 1 TB; $20/mo 10M req, 50 TB; $100/mo 50M, 50 TB; $300/mo 150M, 50 TB. "Traffic above your tier's capacity is served normally and not billed" (fair use). New Pro teams have it on by default. | Y (raw HTML checked) |
| Fast Data Transfer usage-based range | $0.15–$0.35 | /GB | https://vercel.com/docs/pricing/regional-pricing | "1 GB for $0.15 - $0.35" | Y |
| FDT Mumbai (bom1) | $0.20 | /GB | https://vercel.com/docs/pricing/regional-pricing/bom1 | "$0.20 per 1 GB"; Fast Origin Transfer $0.25/GB; Function invocations $0.60/1M (fair-use page) | Y |

## 4. Cloudflare

| Item | Price | Unit | URL | Quote / note | Verified |
|---|---|---|---|---|---|
| Pages Free limits | 500 builds/mo, 1 concurrent build, 20,000 files, 25 MiB/file, 100 custom domains/project | — | https://developers.cloudflare.com/pages/platform/limits/ | "Requests to Pages functions count towards your quota for Workers plans" | Y |
| Static asset requests | free | — | https://developers.cloudflare.com/workers/static-assets/billing-and-limitations/ | "Requests to static assets are free and unlimited." | Y |
| Pages Free commercial use | — | — | — | No explicit restriction found on fetched official pages; only community-forum posts. | N |
| Workers Free | $0 | — | https://developers.cloudflare.com/workers/platform/pricing/ | 100,000 requests/day, 10 ms CPU per invocation | Y |
| Workers Paid | $5 min | /mo per account | same | 10M req/mo incl., then $0.30/M; 30M CPU-ms incl., then $0.02/M CPU-ms | Y |
| Durable Objects requests (Paid) | 1M incl., then $0.15 | per 1M | same | Includes HTTP, RPC, WebSocket messages, alarms. Free: 100k/day | Y |
| DO duration (Paid) | 400,000 GB-s incl., then $12.50 | per 1M GB-s | same | Free: 13,000 GB-s/day | Y |
| DO WebSocket billing | 20:1 | — | same | Incoming WS messages billed at 20:1 (100 msgs = 5 requests); outgoing msgs and protocol pings free; hibernation-eligible idle objects not billed duration; `accept()` (non-hibernation) bills duration for whole connection | Y |
| DO SQLite storage (Paid) | 25B rows read incl. then $0.001/M; 50M rows written incl. then $1.00/M; 5 GB-mo incl. then $0.20/GB-mo | — | same | Billing started Jan 2026 | Y |
| Workers KV (Paid) | 10M reads incl. then $0.50/M; 1M writes/deletes/lists then $5.00/M; 1 GB then $0.50/GB-mo | — | same | | Y |
| R2 Standard storage | $0.015 | /GB-mo | https://developers.cloudflare.com/r2/pricing/ | Class A $4.50/M, Class B $0.36/M; free 10 GB-mo, 1M A, 10M B; egress "Free" | Y |
| Realtime SFU + TURN | $0.05 | /GB egress | https://developers.cloudflare.com/realtime/pricing/ | "The first 1,000 GB each month is free." shared SFU+TURN; ingress free; TURN "free of charge when used together with the Realtime SFU" (https://developers.cloudflare.com/realtime/turn/) | Y |
| Plans | Free $0; Pro $20/mo annual ($25 monthly); Business $200/mo annual ($250 monthly) | — | https://www.cloudflare.com/plans/network-cdn.md | "Unmetered DDoS Protection" included on Free, Pro, Business, Contract | Y |
| DDoS | — | — | https://developers.cloudflare.com/ddos-protection/ | "Standard, unmetered DDoS protection (layers 3-7)" Free = Yes; "Available on all plans" | Y |
| Spectrum | not on Free | — | https://developers.cloudflare.com/spectrum/ | "Available on Paid plans"; "Custom TCP/UDP applications require an Enterprise plan with Spectrum as a paid add-on." No price published | Y (price N) |

## 5. Bare-metal / VPS / cloud

### Hetzner Cloud (raw HTML of https://www.hetzner.com/cloud/general-purpose and /cloud/regular-performance) — prices EUR/mo excl. VAT

CCX (dedicated vCPU, AMD) — Verified Y

| Plan | vCPU/RAM/NVMe | EU (NBG1/HEL1) price / traffic | US (ASH1/HIL1) | Singapore (SIN1) |
|---|---|---|---|---|
| CCX13 | 2/8 GB/80 GB | €43.49 / 20 TB | €43.99 / 1 TB | €54.49 / 1 TB |
| CCX23 | 4/16/160 | €86.49 / 20 TB | €87.99 / 2 TB | €108.99 / 2 TB |
| CCX33 | 8/32/240 | €138.99 / 30 TB | €141.49 / 3 TB | €174.99 / 3 TB |
| CCX43 | 16/64/360 | €276.49 / 40 TB | €279.99 / 4 TB | €341.49 / 4 TB |
| CCX53 | 32/128/600 | €533.99 / 50 TB | €538.99 / 6 TB | €652.49 / 6 TB |
| CCX63 | 48/192/960 | €853.99 / 60 TB | €860.49 / 8 TB | €1,036.99 / 8 TB |

CPX (shared, AMD) — Verified Y. EU/SIN line: CPX02 1/1GB €6.49 (EU, 20 TB) / €7.99 (SIN, 0.5 TB); CPX12 1/2GB €11.99 / €15.99 (0.5 TB); CPX22 2/4GB €19.99 / €26.99 (1 TB); CPX32 4/8GB €35.99 / €49.49 (2 TB); CPX42 8/16GB €69.99 / €93.99 (3 TB); CPX52 12/24GB €100.99 / €134.99 (4 TB); CPX62 16/32GB €130.49 / €172.49 (5 TB). US line: CPX11 2/2GB €17.99 (1 TB); CPX21 3/4GB €32.49 (2 TB); CPX31 4/8GB €62.99 (3 TB); CPX41 8/16GB €120.99 (4 TB); CPX51 16/32GB €238.49 (5 TB).

Traffic overage (verbatim, page text says "per hour" — evidently a template typo for per TB): "For each additional TB at EU-Central locations we charge € 1.00 per hour"; "at U.S. locations we charge € 1.00 per hour"; "at AP locations we charge € 7.40 per hour". Billing FAQ (https://docs.hetzner.com/cloud/billing/faq/): over-usage billed "in blocks of 100MB". Note: page HTML also contains "This product is currently unavailable. Please check back later." next to each plan (could be a hidden template; availability not verified).

### Hetzner dedicated (EUR excl. VAT) — Verified Y
- AX (https://www.hetzner.com/dedicated-rootserver/matrix-ax): AX41 Ryzen 5 3600/64 GB from €59.00, setup €0; AX42 Ryzen 7 PRO 8700GE/64 GB from €79.00 (max €99), setup €49; AX102 Ryzen 9 7950X3D/128 GB from €159.00 (max €259), setup €129; AX162 EPYC 9454P 48c/128 GB from €319.00 (max €614), setup €304. 1 GBit/s port, "Guaranteed bandwidth 1 GBit/s". "Traffic usage is unlimited and free of charge. Please note that our unlimited traffic policy does not apply to servers that have the 10G uplink addon."
- EX (https://www.hetzner.com/dedicated-rootserver/matrix-ex): EX44 i5-13500/64 GB €59.00, setup €0; EX63 Core Ultra 7 265/64 GB €99–149, setup €74; EX131 Xeon Gold 6731P/128 GB €269–559, setup €279; same unlimited-traffic wording. (CPU↔model mapping inferred from list order.)
- Locations: page names Nuremberg, Falkenstein, Helsinki (no Asia/US dedicated). Not explicitly tied per model → N.

### OVHcloud — Verified Y unless noted
- Mumbai exists: location list on https://www.ovhcloud.com/en-in/bare-metal/dedicated-server-singapore/ HTML includes "India - Mumbai" (AP-SOUTH-MUM), plus Singapore, Sydney.
- Bandwidth policy (https://www.ovhcloud.com/en-in/bare-metal/ FAQ): "unlimited and unmetered traffic is enabled by default, except in our Asia-Pacific datacentres."
- Singapore dedicated (https://www.ovhcloud.com/en-sg/lp/dedicated-server-solutions/): "minimum public outgoing bandwidth of 500 Mbit/s and 10TB monthly traffic quota which can be upgraded to up to 3Gbps unmetered"; "Typically for servers hosted in Asia Pacific, they come with a minimum of 10TB quota with unmetered upgrade options." Upgrade price: N.
- Advance (https://www.ovhcloud.com/en-in/bare-metal/advance/): ADVANCE-1 (2024, EPYC 4244P) ₹11,700/mo; ADVANCE-2 ₹15,750; ADVANCE-1 (2026, EPYC 4245P) ₹16,250; ADVANCE-3 ₹20,300; "3Gbps unmetered and guaranteed public bandwidth" (generic — APAC exception above applies). Install fee ₹11,700 (Singapore page). Mumbai-specific Advance price: N.
- VPS (https://www.ovhcloud.com/en-in/vps/): VPS-1 2 vCore/4 GB ₹420; VPS-2 4/8 ₹810; VPS-3 6/12 ₹1,160; VPS-4 8/24 ₹2,240 (ex GST). Footnote: "Our VPS solutions in the Asia-Pacific region (Mumbai, Singapore, Sydney) come with a set monthly quota: 500 GB for VPS-1, 1 TB for VPS-2 and VPS-3, 3 TB for VPS-4. Beyond this, the bandwidth is capped at 10 Mbps."

### AWS (official price-list JSON; EC2 file published 2026-10-29, DataTransfer file 2026-09-16) — Verified Y
- EC2 Linux on-demand, ap-south-1 Mumbai (https://b0.p.awsstatic.com/pricing/2.0/meteredUnitMaps/ec2/USD/current/ec2-ondemand-without-sec-sel/Asia%20Pacific%20(Mumbai)/Linux/index.json): c7g.large $0.0491/hr; c7g.xlarge $0.0982; c7g.2xlarge $0.1963; c6g.xlarge $0.0852; c6i.large $0.085; c6i.xlarge $0.17; c6i.2xlarge $0.34; c7i.xlarge $0.1785.
- Comparison: US East c7g.xlarge $0.145, c6i.xlarge $0.17; Singapore c7g.xlarge $0.1666, c6i.xlarge $0.196; UAE (me-central-1) c6g.xlarge $0.1605 (c7g/c6i not returned for UAE).
- Data transfer out to internet (https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AWSDataTransfer/current/<region>/index.json): global free 100 GB/mo. Mumbai: $0.1093/GB first 10 TB, $0.085 next 40 TB, $0.082 next 100 TB, $0.080 >150 TB. Singapore: $0.12 / 0.085 / 0.082 / 0.080. US East: $0.09 / 0.085 / 0.07 / 0.05. Bahrain (me-south-1): $0.117 / 0.1105 / 0.091 / 0.065. UAE (me-central-1): pricePerUnit $0.11 / 0.085 / 0.077 / 0.055 (but description text in the same file says USD0.1170/0.1105/0.0910/0.0650 — inconsistent in AWS's own file → treat UAE as N).
- Region existence: me-central-1 (UAE) and me-south-1 (Bahrain) both have price-list files → exist. Y.

### GCP / Azure
- GCP internet egress from asia-south1: https://cloud.google.com/vpc/network-pricing — table rendered dynamically; could not extract. N.
- Azure (https://azure.microsoft.com/en-us/pricing/details/bandwidth/), source Zone 2 "Asia, Oceana, and Middle East" (incl. Central India): first 100 GB free; next 10 TB $0.12 (Microsoft network) / $0.11 (routing preference transit ISP); next 40 TB $0.085 / $0.075; next 100 TB $0.082 / $0.07; next 350 TB $0.08 / $0.06. Y (via WebFetch summary).

### E2E Networks (India) — https://www.e2enetworks.com/pricing (USD)
- C3 (vCPU/RAM, hourly, monthly): C3.8GB 4/8 $0.049 $35.64; C3.16GB 8/16 $0.096 $70.13; C3.32GB 16/32 $0.194 $141.42; C3.64GB 20/64 $0.386 $281.69. SDC3 dedicated: SDC3-4.30GB 4/30 $0.099 $72.43; SDC3-8.60GB 8/60 $0.189 $137.97; SDC3-16-120GB 16/120 $0.378 $275.94. Y
- Bandwidth: Policy FAQ (https://www.e2enetworks.com/policies/policy-faq): "No, we currently don't charge for data-transfer to & from your machine instances." Port speed / fair-use cap: N. (Object storage egress ₹3/GB or $0.045/GB — separate product.)

## 6. SMS / OTP / WhatsApp

| Item | Price | Unit | URL | Quote / note | Verified |
|---|---|---|---|---|---|
| Twilio Verify | $0.05 | per successful verification + channel fee | https://www.twilio.com/en-us/verify/pricing | "$0.05 per successful verification plus standard channel fees". Page lists US WhatsApp channel fee $0.0034 in one place and $0.0147 in its FAQ (conflict) | Y (WA fee N) |
| Twilio SMS India | $0.0832 | per segment | https://www.twilio.com/en-us/sms/pricing/in | International numbers & Alphanumeric Sender IDs both $0.0832 | Y |
| Twilio SMS UAE | $0.1176 | per segment | https://www.twilio.com/en-us/sms/pricing/ae | | Y |
| Twilio SMS Saudi Arabia | $0.1949 | per segment | https://www.twilio.com/en-us/sms/pricing/sa | | Y |
| Twilio SMS USA | $0.0083 | per segment (long code / toll-free) + carrier fees | https://www.twilio.com/en-us/sms/pricing/us | carrier pass-through & 10DLC fees extra | Y |
| Twilio SMS Canada | $0.0083 | per segment (long code / toll-free) | https://www.twilio.com/en-us/sms/pricing/ca | | Y |
| Twilio SMS Indonesia | $0.4414 | per segment | https://www.twilio.com/en-us/sms/pricing/id | | Y |
| Twilio SMS Philippines | $0.241 | per segment | https://www.twilio.com/en-us/sms/pricing/ph | | Y |
| Twilio SMS Vietnam | $0.2852 | per segment | https://www.twilio.com/en-us/sms/pricing/vn | | Y |
| Twilio SMS pumping protection | $0.025 | (unit as listed on page) | country pages | optional add-on | Y |
| India DLT | — | — | https://www.twilio.com/en-us/guidelines/in/sms | International: "No DLT registration is required." Alphanumeric sender "will be overwritten with a random short number between five and nine digits long." Domestic: "Completion of company and Sender ID registration within the mobile operators' DLT portal is necessary". International traffic bypasses DND. | Y |
| Meta WhatsApp authentication, India (USD) | $0.0014 list (0–750k/mo); $0.0013 (750,001–15M); $0.0012 (15M–20M); $0.0011 (20M–100M); $0.001 (>100M) | per delivered msg | official calculator API https://whatsappbusiness.com/wp-json/wab/v1/pricing?market=IN&currency=USD&category=Authentication (used by https://whatsappbusiness.com/products/platform-pricing/) ; rate cards effective Oct 1 2026 per https://developers.facebook.com/docs/whatsapp/pricing | | Y |
| Meta WhatsApp authentication, India (INR) | ₹0.115 list; ₹0.1081; ₹0.1012; ₹0.0943; ₹0.0874; ₹0.0805 (same tiers) | per msg | same API, currency=INR | | Y |
| Meta auth-international rate India | not shown | — | https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing/authentication-international-rates | applies to non-India businesses sending >750K msgs/30 days; India rate raised Apr 1 2026 ("Higher"), amount only in rate-card CSV | N |
| Twilio WhatsApp fee | $0.005 | per message (on top of Meta fee) | https://www.twilio.com/en-us/whatsapp/pricing | "Twilio's message handling fee: $0.005 per message"; failed msg fee $0.001 | Y |
| MSG91 OTP India | ₹0.25 (5k) → ₹0.22 (15k) → ₹0.20 (27k) → ₹0.19 (53k–402k) → ₹0.18 (855k) | per SMS + 18% GST | https://msg91.com/in/pricing/otp | "27000SMS for ₹5,400 +18% GST at ₹0.2per SMS" | Y |

## 7. KYC

| Item | Price | URL | Note | Verified |
|---|---|---|---|---|
| Didit free tier | 500 free full KYC verifications/mo | https://didit.me/pricing | "500 free verifications every month, forever." | Y |
| Didit per check | ID $0.15; passive liveness $0.10; active liveness $0.15; face match $0.05; age estimation $0.10; AML $0.20; email $0.03; phone from $0.03; Full KYC bundle $0.33 | same | | Y |
| Veriff | Essential $0.80/verif ($49/mo min); Plus $1.39 ($99 min); Premium $1.89 ($209 min); trial 50 | https://veriff.com/pricing | | Y |
| Sumsub | Basic $1.35/verif ($149/mo min); Compliance $1.85 ($299 min); 14-day trial, 50 checks | https://sumsub.com/pricing/ | | Y |
| Persona | not fetched | — | | N |

## 8. Payments / app stores

| Item | Price | URL | Quote / note | Verified |
|---|---|---|---|---|
| Stripe US | 2.9% + 30¢ | https://stripe.com/pricing | domestic cards; +1.5% international cards; +1% currency conversion; Billing 0.7% of billing volume | Y |
| Stripe India | 2% (Indian Visa/MC); 3% foreign cards (+2% FX); Amex 3.5%; 4.3% non-INR presentment | https://stripe.com/in/pricing | Standard plan CTA is "Request an invite" → appears invite-only; new-account availability not explicitly stated. UPI not listed | Y (availability N) |
| Razorpay | 2% domestic cards / net banking / wallets; international "Up to 3%"; setup ₹0, AMC ₹0; 18% GST on fee | https://razorpay.com/pricing/ | UPI: page data says "Free (0% MDR for most transactions)" while rendered table reads "Zero MDR — 2% platform fee applies" → ambiguous | Y (UPI N) |
| Google Play (remaining markets incl. India) | 15% first $1M/yr; 30% above; auto-renew subscriptions 15% | https://support.google.com/googleplay/android-developer/answer/112622 | India not in new-structure list (inference); alternative billing in India: fee "reduced by 4%" | Y |
| Google Play (US/EEA/UK from 2026-06-30; AU/JP from 2026-09-30) | subscriptions 10% + 5% billing fee; other new installs 20% + 5%; existing installs 25% + 5% (20% for external web links) | same | applies by new vs existing install; $1M-tier mapping unclear in page | Y (tier mapping N) |
| Apple standard | 30% (complement of "you receive 70%") | https://developer.apple.com/app-store/subscriptions/ | "During a subscriber's first year of service, you receive 70%… After … one year of paid service, your net revenue increases to 85%" | Y |
| Apple Small Business Program | 15% | https://developer.apple.com/app-store/small-business-program/ | "reduced commission rate of 15%"; eligible if "up to 1 million USD in proceeds in the prior calendar year"; SBP subscriptions get 85% regardless of year | Y |

## 9. Monitoring

| Item | Price | URL | Note | Verified |
|---|---|---|---|---|
| Sentry Developer | Free | https://sentry.io/pricing/ | 5k errors, 5M spans, 50 replays, 1 user, 30-day lookback | Y |
| Sentry Team | $29/mo monthly, $26/mo annual | same | 50k errors, 5M spans, 50 replays, unlimited users, up to 90-day | Y |
| Sentry Business | $89/mo monthly, $80 annual | same | | Y |
| Grafana Cloud Free | $0 | https://grafana.com/pricing/ | 10k active series, 50 GB logs, 50 GB traces, 50 GB profiles (14-day retention), 3 users, 500 k6 VUh, 50k frontend sessions | Y |
| Grafana Cloud Pro | $19/mo + usage | same | metrics from $6.50/1k series; logs $0.05/GB processed + $0.40/GB written + $0.10/GB retained | Y |

## Not verified / not published (summary)
- LiveKit: separate TURN price (none listed); Build plan overage (none).
- Cloudflare: explicit "commercial use allowed" statement for Pages Free; Spectrum price.
- Hetzner: real-time availability (page contains "currently unavailable" text); dedicated server locations per model.
- OVHcloud: Mumbai-specific Advance price and unmetered-upgrade price; exact APAC dedicated quota for Mumbai (Singapore = 10 TB).
- AWS UAE egress (internal inconsistency in AWS file); c7g/c6i in UAE not found.
- GCP Mumbai internet egress (dynamic table not extractable).
- E2E Networks port speed / fair-use limits.
- Twilio Verify WhatsApp channel fee ($0.0034 vs $0.0147 on same page); Meta auth-international rate for India.
- Razorpay UPI fee (0% MDR vs 2% platform fee wording conflict).
- Stripe India new-account availability (only "Request an invite" CTA seen).
- Persona pricing (not fetched).
