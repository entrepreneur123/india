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
    "indiapost-vacancies": "https://www.indiapost.gov.in/vacancies",
    "nabard-careers": "https://www.nabard.org/Hindi/ftrcontent.aspx?id=660",  # "कैरियर सूचनाएँ", linked from its home
    "epfo-recruitments": "https://www.epfo.gov.in/recruitments/",
    "epfo-archive": "https://www.epfo.gov.in/archive-recruitments",  # "Archives" on the recruitments page (current list was empty)
    # Recruiters that refused or failed from outside India (30-10-2026); the URLs are their careers pages.
    "bank-of-india": "https://bankofindia.bank.in/career",
    "indian-bank": "https://indianbank.bank.in/career/",
    "uco-bank": "https://www.ucobank.bank.in/en/job-opportunities",
    "niacl": "https://www.newindia.co.in/recruitment/list",
    "esic": "https://www.esic.gov.in/recruitments",
    "coast-guard": "https://joinindiancoastguard.cdac.in/",
    "aiims-delhi": "https://www.aiims.edu/index.php/en/notices/recruitment/aiims-recruitment",
    "bhel": "https://www.bhel.com/recruitments",
    "fci": "https://fci.gov.in/recruitments.php",
    "nielit": "https://www.nielit.gov.in/recruitment",
    "ntpc": "https://careers.ntpc.co.in/recruitment/",
    # List pages behind home pages that show none (found in the saved pages' own links, 30-10-2026).
    "upsssc-registration": "https://upsssc.gov.in/AllNotifications.aspx",
    "upsssc-advt-status": "https://upsssc.gov.in/News.aspx?id=9",
    "upsssc-upcoming": "https://upsssc.gov.in/GroupNews.aspx?id=6",
    "upsssc-results": "https://upsssc.gov.in/ResultsDire.aspx",
    "bssc-noticeboard": "https://bssc.bihar.gov.in/NoticeBoard.htm",
    "mp-esb-en": "https://esb.mp.gov.in/e_default.html",
    "cgpsc-home": "https://psc.cg.gov.in/Home_Welcome.php",
    "cgpsc-notifications": "https://psc.cg.gov.in/Notifications.php",
    "cgpsc-advertisement": "https://psc.cg.gov.in/Advertisement.php",
    "cgpsc-result": "https://psc.cg.gov.in/Result.php",
    "hppsc-whatsnew": "https://hppsc.hp.gov.in/HomeContent/WhatsNewPV",
    "hppsc-notifications": "https://hppsc.hp.gov.in/HomeContent/NotificationsPV",
    "appsc-portal": "https://portal-psc.ap.gov.in/Default.aspx",
    "jssc-advertisements": "https://jssc.jharkhand.gov.in/notices/advertisements",
    "jssc-result": "https://jssc.jharkhand.gov.in/result",
    "itbp-news": "https://recruitment.itbpolice.nic.in/statics/news",
    "itbp-results": "https://recruitment.itbpolice.nic.in/statics/results",
    "wb-police-recruitments": "https://prb.wb.gov.in/recruitments",
    "wb-police-exam-notifications": "https://prb.wb.gov.in/quick/examination-notifications",
    "gsssb-news": "https://gsssb.gujarat.gov.in/News/Index",
    "gsssb-notification": "https://gsssb.gujarat.gov.in/Notification",
    "tnusrb-press": "https://tnusrb.tn.gov.in/pressreleases.php",
    "tnpsc-home": "https://tnpsc.gov.in/home.aspx",
    # Police and teacher boards that did not answer from outside India (30-10-2026); home pages, lists to be found in them.
    "mp-esb-home-frame": "https://esb.mp.gov.in/home_n.html",
    "mp-esb-leftmenu": "https://esb.mp.gov.in/leftmenu.html",
    "rajasthan-police": "https://police.rajasthan.gov.in/",
    "punjab-police": "https://www.punjabpolice.gov.in/",
    "odisha-police": "https://odishapolice.gov.in/",
    "ap-slprb": "https://slprb.ap.gov.in/",
    "gujarat-gprb": "https://gprb.gujarat.gov.in/",
    "karnataka-police": "https://ksp.karnataka.gov.in/",
    "upessc": "https://upessc.up.gov.in/",
    "rajasthan-bser": "https://rajeduboard.rajasthan.gov.in/",
    "kea": "https://kea.kar.nic.in/",
    "wbpsc-new": "https://psc.wb.gov.in/",
    "assam-police": "https://police.assam.gov.in/",
    "assam-rifles": "https://www.assamrifles.gov.in/",
    "territorial-army": "https://jointerritorialarmy.gov.in/",
    # The list pages behind MP ESB, WBPSC, UPESSC, AP SLPRB, GPRB, Punjab and Karnataka police (found in their saved pages).
    "mp-esb-notices": "https://esb.mp.gov.in/advertisement/Important_message_candidate.htm",
    "mp-esb-calendar": "https://esb.mp.gov.in/Exams_Schedule/exams_schedule.htm",
    "wbpsc-whats-new": "https://psc.wb.gov.in/whats_new.jsp",
    "wbpsc-advertisement": "https://psc.wb.gov.in/advertisement.jsp",
    "upessc-press": "https://upessc.up.gov.in/Home/Press_Release",
    "upessc-news": "https://upessc.up.gov.in/Home/news",
    "ap-slprb-press": "https://slprb.ap.gov.in/UI/pressRelease.aspx",
    "ap-slprb-recruitments": "https://slprb.ap.gov.in/UI/Recruitments.aspx",
    "gprb-latest": "https://gprb.gujarat.gov.in/latestupdate-list.htm",
    "gprb-advts": "https://gprb.gujarat.gov.in/advertisement-list.htm",
    "punjab-police-recruitment": "https://www.punjabpolice.gov.in/en/recruitments/punjab-police-recruitment/",
    "ksp-recruitment": "https://ksp.karnataka.gov.in/info-3/%E0%B2%A8%E0%B3%87%E0%B2%AE%E0%B2%95%E0%B2%BE%E0%B2%A4%E0%B2%BF/kn",
    "nabard-notices": "https://www.nabard.org/Hindi/careers-notices1.aspx?cid=693&id=26",  # the list behind the careers page
    "kvs-recruitment": "https://kvs.gov.in/%e0%a4%ad%e0%a4%b0%e0%a5%8d%e0%a4%a4%e0%a5%80/",  # "भर्ती" (recruitment), linked from KVS's home
    "uppsc": "https://uppsc.up.nic.in/",
    "bssc": "https://bssc.bihar.gov.in/",
    "kpsc-karnataka": "https://kpsc.karnataka.gov.in/",
    "appsc": "https://psc.ap.gov.in/",
    "tgpsc": "https://websitenew.tgpsc.gov.in/",  # www.tgpsc.gov.in says the site moved here (30-10-2026)
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
WORKERS = 12  # sites are fetched side by side (each from its own host): a slow one doesn't hold up the rest
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
    # Two tries of 20 s, 12 sites at a time: 142 sites stay under 9 minutes even if every one hangs
    # (run.sh also calls this, inside the 25-minute limit of sarkari.service).
    index = capture(a.out, lambda u: http.get_bytes(u, retries=2, timeout=20, max_bytes=MAX_BYTES, headers=http.PAGE_HEADERS),
                    dt.date.today())
    ok = sum(1 for e in index.values() if e.get("ok"))
    print(f"capture: {ok}/{len(index)} sites saved")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
