"""State recruiters' notice boards (same Board model as listboards.py).

Each board below was written against its page as read from the India server (29-10-2026;
tests/fixtures/<source>.html):

  bpsc   Bihar Public Service Commission                bpsc.bihar.gov.in   home page "important notices": <li> <a href=".../BPSC-20260720-x.pdf">
  hpsc   Haryana Public Service Commission              hpsc.gov.in         "What's New": <li><a href="/Portals/0/x.pdf">... dated 22.10.2026 ...</a>
  ppsc   Punjab Public Service Commission               ppsc.gov.in         announcements: <a title=.. href="/index.aspx?page=N&tabid=10..">... uploaded on 14-10-2026</a>
  psssb  Subordinate Services Selection Board, Punjab   sssb.punjab.gov.in  "What's New": <li><a href=".../uploads/2026/10/x.pdf"> headline</a> (no dates)

Not collectable from the home page: JSSC (jssc.jharkhand.gov.in shows five undated headlines;
its lists are at /whats-new and /notices/other-notices).
"""
from __future__ import annotations

import html
import re
from typing import Iterator

from .listboards import _TAG, Board, _dmy, kind_of

# These commissions' headlines put the stage first and "recruitment to N posts" after it
# ("FINAL RESULT FOR RECRUITMENT TO 101 POSTS ...", "TIME SLOT FOR COMPETITIVE EXAMINATION RECRUITMENT TO ...",
# "PUBLIC NOTICE REGARDING RECRUITMENT TO 75 POSTS ..."), which kind_of would call a new recruitment.
_CANCELLED = re.compile(r"\bcancell?ed\b", re.I)
_RESULT = re.compile(r"\bresults?\b", re.I)
_SCHEDULE = re.compile(r"\b(time slot|interview schedule|schedule (of|for) (the )?(interview|competitive exam))", re.I)
_ADVERT = re.compile(r"^advertisement no\.?\s*\d+", re.I)
_ABOUT = re.compile(r"^(public notice|time slot)\b", re.I)


def _kind(title: str) -> str:
    kind = kind_of(title)
    if kind in ("amendment", "answer_key", "admit_card"):
        return kind
    if _CANCELLED.search(title):  # "Advertisement ... has been cancelled"
        return "amendment"
    if _RESULT.search(title):
        return "result"
    if _SCHEDULE.search(title):
        return "schedule"
    if _ADVERT.search(title):  # PSSSB: "Advertisement No. 13 of 2026 for the Posts of ..."
        return "recruitment"
    if kind == "recruitment" and _ABOUT.search(title):
        return "notice"  # a notice about a recruitment already advertised
    return kind

# BPSC: the home page's server-rendered lists (two marquees and "What's New") repeat a handful of
# "important notices"; its full notice table is filled by JavaScript (admin-ajax) and is not in the page.
#   <li> <a href="https://bpsc.bihar.gov.in/wp-content/uploads/BPSC_content/Notices/Regarding_postpone_72nd_BPSC_BPSC-20260720-u768lx.pdf"
#        target="_blank"><b>Important Notice :- </b> Regarding ...</a></li>
# The upload system stamps each file "_BPSC-YYYYMMDD-<random>"; older files are named "NB-YYYY-MM-DD-NN".
# Repeats of one link (same headline) collapse into one item.
_BPSC = re.compile(r"""<li>\s*<a\s+href="(https://bpsc\.bihar\.gov\.in/wp-content/uploads/[^"]+)"[^>]*>(.*?)</a>\s*</li>""", re.S | re.I)
_BPSC_STAMP = re.compile(r"_BPSC-(20\d{2})(\d{2})(\d{2})-[0-9a-z]+\.pdf$", re.I)
_BPSC_NB = re.compile(r"/NB-(20\d{2})-(\d{2})-(\d{2})-\d+[^/]*\.pdf$", re.I)


def _bpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, headline in _BPSC.findall(page):
        m = _BPSC_STAMP.search(href) or _BPSC_NB.search(href)
        yield (f"{m.group(3)}-{m.group(2)}-{m.group(1)}" if m else ""), href, headline


# HPSC: <div class="sidemmenu whats_new"> <ul class="nav">
#   <li><a href="/Portals/0/Final_Result_ADA_22_10_2026.pdf" target="_blank">Final Result dated 22.10.2026 for the posts of ...</a></li>
# The headline's first "dated DD.MM.YYYY" is the notice's date ("Result-II dated 09.10.2026 (In Supersession of the
# result dated 08.08.2023)"); some files carry no date ("HCS_MARKS.pdf"), others a two-digit year ("_17_10_26").
# Without a headline date the file name's "_DD_MM_YYYY.pdf" is used.
_HPSC = re.compile(r"""<li>\s*<a\s+href="((?:https?://hpsc\.gov\.in)?/Portals/0/[^"]+\.pdf)"[^>]*>(.*?)</a>\s*</li>""", re.S | re.I)
_HPSC_DATED = re.compile(r"\bdated\s*:?\s*(\d{1,2}\.\d{1,2}\.\d{4})\b", re.I)
_HPSC_FILE_DATE = re.compile(r"_(\d{1,2})_(\d{1,2})_(\d{4})\.pdf$", re.I)


def _hpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, headline in _HPSC.findall(page):
        href = re.sub(r"^http://hpsc\.gov\.in/", "https://hpsc.gov.in/", href)  # the site itself is served over https
        t = _HPSC_DATED.search(html.unescape(_TAG.sub(" ", headline)))
        f = _HPSC_FILE_DATE.search(href)
        yield (t.group(1) if t else f"{f.group(1)}-{f.group(2)}-{f.group(3)}" if f else ""), href, headline


# PPSC: the home page's "Announcements" marquee lists every announcement since 2022, each its own page:
#   <a class="" title="..." href="/index.aspx?page=2805&tabid=10&tablinkid=6844" target="_parent ">public notice
#   regarding ... uploaded on 14-10-2026</a>
# The date is the headline's closing "uploaded on DD-MM-YYYY" / "Updated DD-MM-YYYY" (sometimes glued:
# "PUNJABUPLOADED ON", "updated at 6 P M on"). A handful of headlines have no such date ("NEW RECRUITMENT TO
# TWENTY ONE POSTS ... YEAR 2026", or only an exam day "To Be Conducted On 25-09-2022"); they are left out
# rather than dated by guess. The office's own purchases (tenders, quotations) are left out too. The headline
# is kept without its date phrase.
_PPSC = re.compile(r"""<a\s+class="[^"]*"\s+title="[^"]*"\s+href="(/index\.aspx\?page=\d+&(?:amp;)?tabid=10&(?:amp;)?tablinkid=\d+)"[^>]*>(.*?)</a>""", re.S | re.I)
_PPSC_DATE = re.compile(r"(?:uploaded|updated)(?:\s+on|\s+at\b.{0,15}?\bon)?\s*:?\s*(\d{1,2}-\d{1,2}-\d{4})\s*\.?\s*$", re.I)
_PPSC_TENDER = re.compile(r"\b(tenders?|quotations?)\b", re.I)


def _ppsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, headline in _PPSC.findall(page):
        text = re.sub(r"\s+", " ", html.unescape(_TAG.sub(" ", headline))).strip()
        m = _PPSC_DATE.search(text)
        if _PPSC_TENDER.search(text) or not m:
            continue
        yield m.group(1), href, html.escape(text[:m.start()].rstrip(" -,"), quote=False)  # headline without the date


# PSSSB: <ul class="listing"> under "What's New":
#   <li><a href="https://sssb.punjab.gov.in/wp-content/uploads/2026/10/Clerk-IT-11-2025-Final-Result.pdf" target="_blank"> Final Result/...</a></li>
# Exam agencies' portals (eservices.punjab.gov.in, gndu.ac.in, ...) sit in the same list and are left out, as
# are its empty links (href=""). The board prints no posting date (dates in headlines are exam or counselling
# days, file names mix both), so each notice is dated the day it is first seen (undated).
_PSSSB_LIST = re.compile(r"""<ul class="listing">(.*?)</ul>""", re.S | re.I)
_PSSSB = re.compile(r"""<li>\s*<a\s+href="([^"]+)"[^>]*>(.*?)</a>\s*</li>""", re.S | re.I)


def _psssb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for block in _PSSSB_LIST.findall(page):
        for href, headline in _PSSSB.findall(block):
            yield "", href, headline


BPSC = Board("bpsc", "https://bpsc.bihar.gov.in", "https://bpsc.bihar.gov.in/", "BPSC", _bpsc_entries, _dmy, kind=_kind)
HPSC = Board("hpsc", "https://hpsc.gov.in", "https://hpsc.gov.in/", "HPSC", _hpsc_entries, _dmy, kind=_kind)
PPSC = Board("ppsc", "https://ppsc.gov.in", "https://ppsc.gov.in/", "PPSC", _ppsc_entries, _dmy, kind=_kind)
PSSSB = Board("psssb", "https://sssb.punjab.gov.in", "https://sssb.punjab.gov.in/", "PSSSB", _psssb_entries, _dmy, kind=_kind,
              undated=True)
ALL = (BPSC, HPSC, PPSC, PSSSB)
