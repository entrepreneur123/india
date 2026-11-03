"""Dating app (Tinder-style swipes + random voice match) at 100k peak CCU: monthly cost per stack. (2026-11-03)
Every usage figure is an assumption (T*); prices are the official pages in COST_MODEL_100K.md §1 / research_2026-11-02.
Run: python3 chat/docs/dating_model.py"""
import sys
CCU = int(sys.argv[1]) if len(sys.argv) > 1 else 100_000
LIGHT = "light" in sys.argv                          # T10: lighter use: average 35% of peak, 8% in calls, 90% photo cache hits
MAU_PER_CCU, AVG = 20, (0.35 if LIGHT else 0.5)           # T1: monthly actives = 20 x peak concurrent; average = half of peak
SEC = 30 * 86400 * AVG                               # concurrency-seconds per peak user per month
SWIPE, CHAT, QUEUE, CALL = 0.40, 0.30, 0.10, (0.08 if LIGHT else 0.15)   # T2: share of peak users swiping, chatting, waiting for a match, in a voice call
CARD_S, PHOTO_KB, PHOTOS = 4, 40, 2                  # T3: a card every 4 s, 2 WebP thumbnails of 40 KB
CACHE_HIT = 0.9 if LIGHT else 0.8                                     # T4: CDN cache hit rate for photos
MSG_S, TYPING = 30, 0.5                              # T5: a chat message every 30 s; typing/presence = +50% of deliveries
DB_RPS = tuple(x * CCU / 100_000 for x in (5_000, 7_000))                             # T6: DB calls/s at peak with no polling (reads 3-6k incl. card batches, writes ~1.4k)
MS_CALL, UTIL, VCPU = 4, 0.6, 20                     # T7: 4 ms per call, plan to 60% CPU, $20 per dedicated vCPU-month (A15)
KBPS = (44, 106)                                     # T8: relayed voice stream: economy Opus 24k / today's 48k+RED, incl. headers
OTP_SHARE, KYC_SHARE = 0.10, (0.005, 0.02)           # T9: OTPs per MAU per month; share of MAU doing a $0.33 ID check

mau = CCU * MAU_PER_CCU
r = lambda lo, hi: f"${lo:,.0f}–{hi:,.0f}"
def db_vcpu(rps): return rps * MS_CALL / 1000 / UTIL
# photos: R2 + CDN (egress free, Class B $0.36/M on cache misses, storage $0.015/GB: 6 photos x 360 KB per user)
photo_req = CCU * SWIPE * PHOTOS / CARD_S * SEC
photos = photo_req * (1 - CACHE_HIT) * 0.36e-6 + mau * 6 * 0.36e-3 * 0.015
photo_tb = photo_req * PHOTO_KB / 1e9
# voice: every stranger/match call relayed (IP hidden); each caller receives one stream
voice_tb = [CCU * CALL * k * 1000 / 8 * SEC / 1e12 for k in KBPS]
cf_turn = [tb * 1000 * 0.05 for tb in voice_tb]
nodes = max(2, -(-CCU // 12_500)) * 4                 # 4 vCPU coturn per region slice, at least 2
own_turn = [voice_tb[0] * 1000 * 0.01 + nodes * VCPU, voice_tb[1] * 1000 * 0.08 + nodes * VCPU]
# realtime traffic
sends = CCU * CHAT / MSG_S
deliv = sends * 2 * (1 + TYPING)
supa_rt = max(0, CCU - 500) / 1000 * 10 + max(0, (sends + deliv) * SEC / 1e6 - 5) * 2.5
incoming = sends * (1 + TYPING) + CCU * QUEUE / 10          # chat + typing + a matching heartbeat every 10 s
do_rt = [5 + incoming * SEC / 20 * 0.15e-6 + incoming * SEC * ms / 1000 * 0.128 * 12.5e-6 for ms in (2, 20)]
own_rt = (max(2, -(-CCU // 25_000)) * 8 + 2 * 2) * VCPU                        # Supabase Realtime x6 nodes + Valkey pair
# database
supa_compute = lambda need: next(usd for v, usd in [(1, 15), (2, 110), (4, 210), (8, 410), (16, 960), (32, 1870), (48, 2800), (64, 3730)] if v >= need)
supa_db = [25 + supa_compute(db_vcpu(x)) + max(0, mau - 100_000) * 0.00325 + x * 2 * SEC / 1e6 * 0.09 for x in DB_RPS]
own_db = [(max(4, round(db_vcpu(x))) * 2 + max(2, round(db_vcpu(x)) // 2) + 2 * 2) * VCPU + 150 for x in DB_RPS]   # primary+standby+replica, API, backups
otp = [mau * OTP_SHARE * c for c in (0.0064, 0.083)]          # WhatsApp (Meta + Twilio fee) / international SMS
kyc = [mau * s * 0.33 for s in KYC_SHARE]
misc = 300                                                   # monitoring, Workers Paid, Pages, DNS

print(f"MAU {mau:,} | photos {photo_tb:,.0f} TB/mo served | voice {voice_tb[0]:,.0f}-{voice_tb[1]:,.0f} TB/mo | DB {DB_RPS} rps -> {db_vcpu(DB_RPS[0]):.0f}-{db_vcpu(DB_RPS[1]):.0f} vCPU")
print("photos R2+CDN", r(photos, photos), "| Supabase Storage egress would be", r(photo_tb * 1000 * 0.03, photo_tb * 1000 * 0.09))
print("realtime: Supabase", r(supa_rt, supa_rt), "| Durable Objects", r(*do_rt), "| own", r(own_rt, own_rt))
print("voice: Cloudflare TURN", r(*cf_turn), "| own coturn", r(*own_turn))
print("db: Supabase", r(*supa_db), "| own", r(*own_db), "| otp", r(*otp), "| kyc", r(*kyc))
common = [photos + otp[0] + kyc[0] + misc, photos + otp[0] + kyc[1] + misc]
stacks = {
    "A Supabase everything + Cloudflare R2/TURN": [supa_db[0] + supa_rt + cf_turn[0] + common[0], supa_db[1] + supa_rt + cf_turn[1] + common[1]],
    "B Supabase DB/Auth + Cloudflare DO realtime/matching + R2/TURN": [supa_db[0] + do_rt[0] + cf_turn[0] + common[0], supa_db[1] + do_rt[1] + cf_turn[1] + common[1]],
    "C own servers DB/Auth/realtime + Cloudflare R2/TURN": [own_db[0] + own_rt + cf_turn[0] + common[0], own_db[1] + own_rt + cf_turn[1] + common[1]],
    "D own servers incl. own TURN + R2": [own_db[0] + own_rt + own_turn[0] + common[0], own_db[1] + own_rt + own_turn[1] + common[1]],
}
for k, (lo, hi) in stacks.items():
    print(f"{k}: {r(lo, hi)} /mo  (${lo / CCU:.2f}-{hi / CCU:.2f} per peak user)")
