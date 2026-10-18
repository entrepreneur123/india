"""Measure HTTPS reachability of result hosts from Indian ISP networks (Globalping probes).

  python loadsim/reachability.py https://entrepreneur123.github.io/india/v2/today.json [more URLs]

Free Globalping API (no key): ~250 tests/hour. Probes are mostly home broadband, so this
approximates fixed-line ISPs; mobile networks are not covered.
Results from 18-10-2026 are recorded in docs/REVIEW.md.
"""
from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.globalping.io/v1/measurements"
ISPS = {55836: "Jio", 24560: "Airtel", 9498: "Airtel-backbone", 38266: "Vi", 9829: "BSNL",
        24309: "ACT", 17488: "Hathway", 45609: "Airtel-mobile", 133982: "Excitel"}


def measure(url: str, per_isp: int = 2) -> list[dict]:
    u = urllib.parse.urlsplit(url)
    body = {"type": "http", "target": u.hostname,
            "locations": [{"country": "IN", "asn": a, "limit": per_isp} for a in ISPS],
            "measurementOptions": {"protocol": "HTTPS",
                                   "request": {"method": "GET", "path": u.path or "/", "query": u.query or None}}}
    if body["measurementOptions"]["request"]["query"] is None:
        del body["measurementOptions"]["request"]["query"]
    req = urllib.request.Request(API, data=json.dumps(body).encode(), headers={"content-type": "application/json"})
    mid = json.load(urllib.request.urlopen(req, timeout=30))["id"]
    for _ in range(40):
        j = json.load(urllib.request.urlopen(f"{API}/{mid}", timeout=30))
        if j["status"] != "in-progress":
            break
        time.sleep(2)
    rows = []
    for r in j["results"]:
        p, res = r["probe"], r["result"]
        rows.append({"isp": ISPS.get(p["asn"], p["network"]), "city": p["city"], "ok": res.get("status") == "finished",
                     "code": res.get("statusCode"), "ms": (res.get("timings") or {}).get("total"),
                     "error": None if res.get("status") == "finished" else (res.get("rawOutput") or "")[:60]})
    return rows


def main(urls: list[str]) -> None:
    for url in urls:
        rows = measure(url)
        ok = [r for r in rows if r["ok"]]
        print(f"\n{url}\n  reachable {len(ok)}/{len(rows)} probes")
        for r in rows:
            print(f"  {r['isp']:16} {r['city']:18} {'OK ' + str(r['code']) if r['ok'] else 'FAIL'}"
                  f" {r['ms'] or '':>6} {r['error'] or ''}")
        time.sleep(1)


if __name__ == "__main__":
    main(sys.argv[1:] or ["https://cdn.jsdelivr.net/gh/entrepreneur123/india@data/v2/today.json"])
