"""Peak-traffic model for the result feed around one Dear draw (e.g. 1 PM IST).

Not a benchmark of a server: the feed is static files on a CDN. This answers
  * how many requests/second hit the CDN edge and the origin at the worst second,
  * how much bandwidth that is, per day and per month (free-tier limits),
  * how much the client design choices (jitter, polling interval, ETag) matter.

Behaviour model (assumptions, tune with real analytics after launch):
  - share  of daily active users who check this draw:        CHECK_SHARE
  - result published PUBLISH_MIN after the draw (Sambad: 7-10 min observed)
  - 25% "waiters" open the app between T-10 and the publish time and keep it open;
    the rest open after publish, delay ~ lognormal (median 12 min, long tail to 2 h)
  - waiters poll while the card says "Result coming soon"
  - background check for saved tickets / notifications on a share of installs
Run:  python loadsim/simulate.py
"""
from __future__ import annotations

import numpy as np

RNG = np.random.default_rng(7)
PUBLISH_MIN = 9          # sheet appears ~7-10 min after the draw
FEED_200 = 3_100         # bytes: v2/today.json gzipped (measured 18-10-2026, 8 draws)
FEED_304 = 250           # bytes: 304 Not Modified incl. headers
HORIZON_S = 3 * 3600     # simulate T-10 min .. ~T+2h50
T0 = -600                # seconds relative to draw time


def simulate(dau: int, check_share: float, poll_s: float | None, poll_jitter: bool,
             bg_share: float, bg_jitter_s: float, edge_ttl_s: float, pull_spam: bool):
    users = int(dau * check_share)
    n_wait = int(users * 0.25)
    n_late = users - n_wait
    secs = np.zeros(HORIZON_S, dtype=np.int64)   # requests per second, index 0 == T0
    bytes_ = np.zeros(HORIZON_S, dtype=np.float64)
    pub = PUBLISH_MIN * 60 - T0                  # publish second index

    def add(t, size):
        t = t[(t >= 0) & (t < HORIZON_S)].astype(np.int64)
        np.add.at(secs, t, 1)
        np.add.at(bytes_, t, size)

    # Waiters: open uniformly between T-10 and publish, first request on open (200: they need data).
    open_w = RNG.uniform(0, pub, n_wait)
    add(open_w, FEED_200)
    if poll_s:
        # Poll until the result is seen. Old-style "pull spam": users drag to refresh every ~8 s.
        interval = 8.0 if pull_spam else poll_s
        k = np.ceil((pub - open_w) / interval).astype(int)
        kmax = int(k.max()) if len(k) else 0
        phase = RNG.uniform(0, interval, n_wait) if poll_jitter else np.zeros(n_wait)
        for i in range(1, kmax + 2):
            alive = k >= i - 1
            t = open_w[alive] + i * interval + (phase[alive] if poll_jitter else 0)
            seen = t >= pub
            add(t[~seen], FEED_304)
            add(t[seen], FEED_200)   # the poll that sees the new result downloads it
            k = np.where(alive & (open_w + i * interval + phase >= pub), -10, k)
    # Late openers: one request on open.
    delay = RNG.lognormal(mean=np.log(12 * 60), sigma=0.9, size=n_late)
    add(pub + delay, FEED_200)
    # Background workers (saved tickets): scheduled at draw + 10 min, optionally jittered.
    n_bg = int(dau * bg_share)
    t_bg = (10 * 60 - T0) + (RNG.uniform(0, bg_jitter_s, n_bg) if bg_jitter_s else np.zeros(n_bg))
    add(t_bg, FEED_200)

    peak_rps = int(secs.max())
    peak_mbps = bytes_.max() * 8 / 1e6
    total_gb = bytes_.sum() / 1e9
    # CDN: each edge PoP asks the origin at most once per TTL (and coalesces concurrent misses).
    pops = 12  # Cloudflare/Fastly PoPs in India serving most traffic (order of magnitude)
    origin_rps = pops / max(edge_ttl_s, 1)
    return peak_rps, peak_mbps, total_gb, origin_rps, secs


def main():
    print("Per-draw peak, 1 draw. GB/day multiplies by ~3 draws (1 PM, 6 PM, 8 PM).\n")
    header = f"{'scenario':58} {'DAU':>9} {'peak req/s':>11} {'peak Mbit/s':>12} {'GB/draw':>8} {'origin req/s':>12}"
    print(header)
    print("-" * len(header))
    for dau in (100_000, 1_000_000, 3_000_000):
        rows = [
            ("v1 design: pull-to-refresh spam, BG at exact T+10", dict(poll_s=8, poll_jitter=False, bg_share=0.3, bg_jitter_s=0, edge_ttl_s=300, pull_spam=True)),
            ("v2: auto-poll 30 s + jitter, BG T+10..+16 min", dict(poll_s=30, poll_jitter=True, bg_share=0.3, bg_jitter_s=360, edge_ttl_s=30, pull_spam=False)),
            ("v2 + FCM push instead of polling (future)", dict(poll_s=None, poll_jitter=True, bg_share=0.0, bg_jitter_s=0, edge_ttl_s=30, pull_spam=False)),
        ]
        for name, kw in rows:
            peak, mbps, gb, origin, _ = simulate(dau, 0.6, **kw)
            print(f"{name:58} {dau:>9,} {peak:>11,} {mbps:>12.1f} {gb:>8.2f} {origin:>12.2f}")
        print()
    # Monthly bandwidth vs free tiers, v2 design, 3 draws/day.
    for dau in (100_000, 1_000_000):
        _, _, gb, _, _ = simulate(dau, 0.6, 30, True, 0.3, 360, 30, False)
        print(f"v2 monthly transfer at {dau:,} DAU: ~{gb * 3 * 30:,.0f} GB "
              f"(GitHub Pages soft limit 100 GB/month; Cloudflare static: no bandwidth charge)")


if __name__ == "__main__":
    main()
