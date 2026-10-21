"""Saves copies of recruiters' pages that only answer from India, so collectors can be written
from real pages instead of guesses (the way ssc.py, upprpb.py and hssc.py were).

Runs weekly on the India server (deploy/india/capture.sh) and keeps, per site:
  captures/<site>/<date>.html         the page as served
  captures/<site>/<date>.links.json   its links [{"href", "text"}], easier to read than the HTML
  captures/index.json                 per site: HTTP status, bytes, error, date

    python -m sarkari.capture --out <dir>
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import re
import urllib.parse
from typing import Callable

from . import http

SITES = {  # name -> home page (found with jobs-probe: none of these answer GitHub's US runners)
    "rrb-cdg": "https://www.rrbcdg.gov.in/",
    "rrb-apply": "https://www.rrbapply.gov.in/",
    "ibps": "https://www.ibps.in/",
    "upsc": "https://upsc.gov.in/",
    "upsssc": "https://upsssc.gov.in/",
    "bpsc": "https://bpsc.bihar.gov.in/",
    "rpsc": "https://rpsc.rajasthan.gov.in/",
    "rssb": "https://rssb.rajasthan.gov.in/",
    "mppsc": "https://mppsc.mp.gov.in/",
    "mp-esb": "https://esb.mp.gov.in/",
    "hpsc": "https://hpsc.gov.in/",
    "dsssb": "https://dsssb.delhi.gov.in/",
    "ppsc": "https://ppsc.gov.in/",
    "punjab-sssb": "https://sssb.punjab.gov.in/",
    "mpsc": "https://www.mpsc.gov.in/",
    "tnpsc": "https://www.tnpsc.gov.in/",
    "wbpsc": "https://wbpsc.gov.in/",
    "jssc": "https://jssc.jharkhand.gov.in/",
    "kerala-psc": "https://keralapsc.gov.in/",
    "india-post-gds": "https://indiapostgdsonline.gov.in/",
    "army": "https://joinindianarmy.nic.in/",
    "ctet": "https://ctet.nic.in/",
}
MAX_BYTES = 5_000_000
_A = re.compile(r"""<a\b[^>]*?href\s*=\s*(["'])(.*?)\1[^>]*>(.*?)</a>""", re.S | re.I)


def links(page: str, base: str) -> list[dict]:
    out, seen = [], set()
    for _, href, text in _A.findall(page):
        href = urllib.parse.urljoin(base, html.unescape(href.strip()))
        if not href.startswith(("http://", "https://")) or href in seen:
            continue
        seen.add(href)
        out.append({"href": href, "text": re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", text))).strip()[:300]})
    return out


def capture(out: str, get_bytes: Callable[[str], bytes], today: dt.date, sites: dict = SITES) -> dict:
    index_path = os.path.join(out, "index.json")
    try:
        with open(index_path, encoding="utf-8") as f:
            index = json.load(f)
    except FileNotFoundError:
        index = {}
    for name, url in sites.items():
        entry = {"url": url, "date": today.isoformat()}
        try:
            body = get_bytes(url)
            page = body.decode("utf-8", errors="replace")
            os.makedirs(os.path.join(out, name), exist_ok=True)
            with open(os.path.join(out, name, f"{today}.html"), "wb") as f:
                f.write(body)
            with open(os.path.join(out, name, f"{today}.links.json"), "w", encoding="utf-8") as f:
                json.dump(links(page, url), f, ensure_ascii=False, indent=0)
            entry.update(ok=True, bytes=len(body))
        except Exception as e:  # noqa: BLE001 - one site down must not stop the others
            entry.update(ok=False, error=str(e)[:300])
        index[name] = entry
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1, sort_keys=True)
    return index


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)
    # One try each: 22 sites x 3 tries x 40 s could outlast the 30-minute service limit.
    index = capture(a.out, lambda u: http.get_bytes(u, retries=1, timeout=30, max_bytes=MAX_BYTES), dt.date.today())
    ok = sum(1 for e in index.values() if e.get("ok"))
    print(f"capture: {ok}/{len(index)} sites saved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
