"""Tells IndexNow search engines (Bing, Yandex, Seznam, Naver…) about fresh result pages at once.

Google does not use IndexNow; for Google the sitemap and Search Console do the job. The key is
public by design: it is served as a text file on the site to prove we own it.

    python -m lottery_pipeline.indexnow --site-url https://example.in --out ../public
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import urllib.request

KEY = "02718a8e3becab6dcf865cf1b74d00dd"
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
SLUG = {"NL1PM": "1pm", "SK6PM": "6pm", "NL8PM": "8pm", "NLLAXMI": "laxmi-8pm", "KL": "kerala"}  # as web/sitegen.py
# The fixed pages that show each draw's latest result (web/sitegen.py HUB and /kerala/).
HUB = {"NL1PM": "/morning-1pm/", "SK6PM": "/day-6pm/", "NL8PM": "/night-8pm/", "KL": "/kerala/"}


def fresh_urls(site_url: str, today_feed: dict, today: dt.date) -> list[str]:
    """Home, draw-time hubs and day pages plus every draw page of today and yesterday, in the three languages."""
    base = site_url.rstrip("/")
    paths = ["/"]
    for d in today_feed.get("draws", []):
        if d["date"] < (today - dt.timedelta(days=1)).isoformat() or not d.get("tiers"):
            continue
        y, m, dd = d["date"].split("-")
        day = f"/result/{dd}-{m}-{y}/"
        slug = SLUG.get(d["slot"], d["slot"].lower().replace("_", "-"))
        hubs = (HUB[d["slot"]], "/yesterday/") if d["slot"] in HUB and d["date"] == today.isoformat() else ()
        for p in (day, f"{day}{slug}/", *hubs):
            if p not in paths:
                paths.append(p)
    return [f"{base}{lang}{p}" for p in paths for lang in ("", "/bn", "/hi")]


def mh_urls(site_url: str, mh_days: list[dict], today: dt.date) -> list[str]:
    """Maharashtra (its own feed, v2/mh/), for the draws of today and yesterday: /maharashtra/, each
    draw's game page (/maharashtra/gajlaxmi-ravi/, it changes when the game draws), the draw pages
    that have numbers, and the Marathi page (one language)."""
    base = site_url.rstrip("/")
    paths, games = [], []
    for day in mh_days:
        for d in day.get("draws", []):
            if d["date"] < (today - dt.timedelta(days=1)).isoformat():
                continue
            y, m, dd = d["date"].split("-")
            slug = re.sub(r"[^a-z0-9]+", "-", d["name"].lower().removeprefix("maharashtra ")).strip("-")  # as web/sitegen.py
            games.append(f"/maharashtra/{slug}/")
            if d.get("status") == "OK":
                paths.append(f"/result/{dd}-{m}-{y}/maharashtra-{slug}/")
    if not games:
        return []
    paths = ["/maharashtra/"] + sorted(set(games)) + paths
    return [f"{base}{lang}{p}" for p in paths for lang in ("", "/bn", "/hi")] + [f"{base}/mr/maharashtra/"]


def body(site_url: str, urls: list[str]) -> dict:
    host = site_url.split("/")[2]
    return {"host": host, "key": KEY, "keyLocation": f"{site_url.rstrip('/')}/{KEY}.txt", "urlList": urls}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--site-url", required=True)
    ap.add_argument("--out", required=True, help="site folder: the key file is written there")
    ap.add_argument("--key-only", action="store_true", help="only write the key file (before publishing)")
    a = ap.parse_args(argv)
    out = pathlib.Path(a.out)
    (out / f"{KEY}.txt").write_text(KEY)
    if a.key_only:
        return 0
    feed = json.loads((out / "v2/today.json").read_text())
    today = dt.datetime.now(IST).date()
    urls = fresh_urls(a.site_url, feed, today)
    mh = [json.loads((out / f"v2/mh/days/{d.isoformat()}.json").read_text())
          for d in (today, today - dt.timedelta(days=1)) if (out / f"v2/mh/days/{d.isoformat()}.json").exists()]
    urls += mh_urls(a.site_url, mh, today)
    req = urllib.request.Request("https://api.indexnow.org/indexnow", data=json.dumps(body(a.site_url, urls)).encode(),
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            print(f"indexnow: {len(urls)} urls, HTTP {r.status}")
    except Exception as e:  # noqa: BLE001 - a ping must never fail publishing
        print(f"indexnow: not accepted ({e})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
