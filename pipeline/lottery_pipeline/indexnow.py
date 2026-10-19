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
import urllib.request

KEY = "02718a8e3becab6dcf865cf1b74d00dd"
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
SLUG = {"NL1PM": "1pm", "SK6PM": "6pm", "NL8PM": "8pm", "NLLAXMI": "laxmi-8pm"}


def fresh_urls(site_url: str, today_feed: dict, today: dt.date) -> list[str]:
    """Home and day pages plus every draw page of today and yesterday, in the three languages."""
    base = site_url.rstrip("/")
    paths = ["/"]
    for d in today_feed.get("draws", []):
        if d["date"] < (today - dt.timedelta(days=1)).isoformat() or not d.get("tiers"):
            continue
        y, m, dd = d["date"].split("-")
        day = f"/result/{dd}-{m}-{y}/"
        slug = SLUG.get(d["slot"], d["slot"].lower().replace("_", "-"))
        for p in (day, f"{day}{slug}/"):
            if p not in paths:
                paths.append(p)
    return [f"{base}{lang}{p}" for p in paths for lang in ("", "/bn", "/hi")]


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
    urls = fresh_urls(a.site_url, feed, dt.datetime.now(IST).date())
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
