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
import concurrent.futures
import datetime as dt
import html
import json
import os
import re
import urllib.parse
from typing import Callable

from . import http

SITES = {  # name -> home page (found with jobs-probe: none of these answer GitHub's US runners)
    # Railways: every regional RRB posts the same CEN notices. www.rrbcdg.gov.in's certificate does
    # not name the www host and rrbapply.gov.in is the application portal (404 on /), 29-10-2026.
    "rrb-chennai": "https://www.rrbchennai.gov.in/",
    "rrb-mumbai": "https://rrbmumbai.gov.in/",
    "rrb-secunderabad": "https://rrbsecunderabad.gov.in/",
    "rrb-indianrailways": "https://rrb.indianrailways.gov.in/",
    # The lists behind those home pages (29-10-2026 captures): written collectors read these.
    "rrb-news": "https://rrb.indianrailways.gov.in/offrings/vacancy/news-updates",
    "rrb-cen-list": "https://rrb.indianrailways.gov.in/cencall/cen-list",
    "rrb-secunderabad-notices": "https://rrbsecunderabad.gov.in/notices",
    "rrb-secunderabad-cen": "https://rrbsecunderabad.gov.in/employment-notice",
    "ibps-recruitment": "https://www.ibps.in/index.php/recruitment",
    "rpsc-ads": "https://rpsc.rajasthan.gov.in/viewcommon?Slice=RA",
    "dsssb-vacancies": "https://dsssb.delhi.gov.in/dsssb-vacancies",
    "ibps": "https://www.ibps.in/",
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
    "mpsc": "https://mpsc.gov.in/",  # the www name is not on its certificate
    "tnpsc": "https://www.tnpsc.gov.in/",
    "wbpsc": "https://wbpsc.gov.in/",
    "jssc": "https://jssc.jharkhand.gov.in/",
    "kerala-psc": "https://keralapsc.gov.in/",
    "india-post-gds": "https://indiapostgdsonline.gov.in/",
    "army": "https://joinindianarmy.nic.in/",
    "ctet": "https://ctet.nic.in/",
    # Next in demand (29-10-2026). upsc.gov.in refuses automated clients (403), so UPSC is read from
    # its own application portal, which lists the exams with e-admit cards.
    "upsc-online": "https://upsconline.gov.in/",
    "upsc-eadmit": "https://upsconline.gov.in/eadmitcard/index.php",
    "upsc-ora-admit": "https://upsconline.gov.in/oraadmitcard/",
    "jssc-notices": "https://jssc.jharkhand.gov.in/notices",
    "jssc-whats-new": "https://jssc.jharkhand.gov.in/whats-new",
    "jssc-other-notices": "https://jssc.jharkhand.gov.in/notices/other-notices",
    "ibps-crp-updates": "https://www.ibps.in/index.php/crp-updates/",
    "bpsc-whats-new": "https://bpsc.bihar.gov.in/whats-new/",
    "bpsc-advertisement": "https://bpsc.bihar.gov.in/advertisement/",
    "ppsc-open-ads": "https://ppsc.gov.in/index.aspx?page=27&tabid=48&tablinkid=27&tablinkyes=1",
    "rbi-announcements": "https://opportunities.rbi.org.in/Scripts/resultsnew.aspx",
    "rbi-vacancies": "https://opportunities.rbi.org.in/Scripts/Vacancies.aspx",
    "uppsc": "https://uppsc.up.nic.in/",
    "bssc": "https://bssc.bihar.gov.in/",
    "kpsc-karnataka": "https://kpsc.karnataka.gov.in/",
    "appsc": "https://psc.ap.gov.in/",
    "tgpsc": "https://www.tgpsc.gov.in/",
    "gpsc": "https://gpsc.gujarat.gov.in/",
    "gsssb": "https://gsssb.gujarat.gov.in/",
    "hppsc": "https://hppsc.hp.gov.in/",
    "ukpsc": "https://psc.uk.gov.in/",
    "uksssc": "https://sssc.uk.gov.in/",
    "cgpsc": "https://psc.cg.gov.in/",
    "cg-vyapam": "https://vyapam.cgstate.gov.in/",
    "osssc": "https://www.osssc.gov.in/",
    "apsc-assam": "https://apsc.nic.in/",
    "wbssc": "https://wbssc.gov.in/",
    "wb-police": "https://prb.wb.gov.in/",
    "tnusrb": "https://tnusrb.tn.gov.in/",
    "tn-trb": "https://www.trb.tn.gov.in/",
    "kvs": "https://kvsangathan.nic.in/",
    "nvs": "https://navodaya.gov.in/",
    "nta": "https://nta.ac.in/",
    "navy": "https://www.joinindiannavy.gov.in/",
    "airforce-agniveer": "https://agnipathvayu.cdac.in/",
    "afcat": "https://afcat.cdac.in/",
    "crpf": "https://rect.crpf.gov.in/",
    "cisf": "https://cisfrectt.cisf.gov.in/",
    "itbp": "https://recruitment.itbpolice.nic.in/",
    "ssb": "https://ssbrectt.gov.in/",
    "drdo": "https://www.drdo.gov.in/drdo/careers",
    "bel": "https://bel-india.in/",
    "nabard": "https://www.nabard.org/",
    "epfo": "https://www.epfindia.gov.in/",
    "india-post": "https://www.indiapost.gov.in/",
}
WORKERS = 8  # sites are fetched side by side (each from its own host): a slow one doesn't hold up the rest
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


def _other_spelling(url: str) -> str:
    p = urllib.parse.urlsplit(url)
    host = p.netloc[4:] if p.netloc.startswith("www.") else "www." + p.netloc
    return urllib.parse.urlunsplit(p._replace(netloc=host))


def capture(out: str, get_bytes: Callable[[str], bytes], today: dt.date, sites: dict = SITES) -> dict:
    index_path = os.path.join(out, "index.json")
    try:
        with open(index_path, encoding="utf-8") as f:
            index = json.load(f)
    except FileNotFoundError:
        index = {}

    def fetch(url: str) -> tuple[str, bytes | Exception]:
        try:
            try:
                return url, get_bytes(url)
            except Exception as e:  # noqa: BLE001
                # A certificate (or DNS name) for the other spelling of the name, with or without www.
                if "Hostname mismatch" not in str(e) and "Name or service not known" not in str(e):
                    raise
                url = _other_spelling(url)
                return url, get_bytes(url)
        except Exception as e:  # noqa: BLE001 - one site down must not stop the others
            return url, e

    with concurrent.futures.ThreadPoolExecutor(WORKERS) as pool:
        fetched = dict(zip(sites, pool.map(fetch, sites.values())))
    for name, (url, body) in fetched.items():
        entry = {"url": url, "date": today.isoformat()}
        try:
            if isinstance(body, Exception):
                raise body
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
    index = {k: v for k, v in index.items() if k in sites}  # sites dropped from SITES leave the index
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1, sort_keys=True)
    return index


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)
    # Two tries of 20 s, 8 sites at a time: 70 sites stay under 6 minutes even if every one hangs
    # (run.sh also calls this, inside the 25-minute limit of sarkari.service).
    index = capture(a.out, lambda u: http.get_bytes(u, retries=2, timeout=20, max_bytes=MAX_BYTES, headers=http.PAGE_HEADERS),
                    dt.date.today())
    ok = sum(1 for e in index.values() if e.get("ok"))
    print(f"capture: {ok}/{len(index)} sites saved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
