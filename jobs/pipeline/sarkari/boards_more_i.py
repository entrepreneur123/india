"""PSUs, an insurer and AIIMS whose recruitment page is one server-rendered list (same Board model as listboards.py).

Each board below was written against its page of 30-10-2026 as the India server saved it (tests/fixtures/<source>.html; ntpc and
niacl are cut unchanged from larger pages, see tests/test_boards_more_i.py):

  ntpc   NTPC Limited (careers)                careers.ntpc.co.in  "Notice Board": headline, files, "Posting date : 2026-10-29"
  niacl  The New India Assurance Co. Ltd.      www.newindia.co.in  one accordion per recruitment, its files, no dates: dated the day first seen
  aiims  All India Institute of Medical Sciences, New Delhi   www.aiims.edu   table: category | title | date of publishing | closing

Not collectable from what was saved (30-10-2026): FCI (fci.gov.in/recruitments.php draws its list in the browser; the page
holds no notice).

The rules of listboards.py apply: a day, no time; links leaving the board's own host (exam agencies' admit card and score portals,
IBPS's application and call letter pages) are not linked: the item links the board's page, which states the news; a broken page
raises FormatChanged.
"""
from __future__ import annotations

import datetime as dt
import html
import re
import urllib.parse
from typing import Iterator

from .listboards import _TAG, Board, _dmy, kind_of

_WS = re.compile(r"\s+")
_A = re.compile(r'<a\b[^>]*?href="([^"]*)"[^>]*>(.*?)</a>', re.S)


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _on_host(base: str, page: str, href: str) -> bool:
    url = urllib.parse.urljoin(page, html.unescape(href.strip()))
    return url.startswith(base + "/")


def _kinds(rules: list[tuple[str, re.Pattern]], fallback: str | None = None):
    def kind(title: str) -> str:
        return next((k for k, p in rules if p.search(title)), None) or fallback or kind_of(title)
    return kind


# NTPC: the careers home page's "Notice Board", newest first (back to 2022), one <li> per notice:
#   <li> ... <div class="job-post-info"> <h5>Special Recruitment Drive for OBC Candidates against  Advt.14/26. Online application
#   portal will remain open from 16.10.2026 to 30.10.2026. ...</h5> ... <a href="pdf_viewer.php?token=MjF4...%3D%3D" ...>Adv English</a>
#   ... <a href="https://ibtexamination.com/secuAdmitcard/AdmitCard04_NTPC" ...>Download Admit Card</a> ...
#   <span><b>Posting date : </b>2026-10-29</span>
# A notice links its first file on NTPC's site (pdf_viewer.php?token=...); one with only outside links (admit card and score portals)
# or none links this page. The tokens differ for the same file within one page (per-visit), so a notice is known by its headline.
NTPC_PAGE = "https://careers.ntpc.co.in/recruitment/"
_NTPC_BOARD = '<h2 class="m-b5">Notice Board</h2>'
_NTPC = re.compile(r'<li>\s*<div class="d-flex m-b20">(.*?)<b>Posting date : </b>\s*([^<]*?)\s*</span>', re.S)
_NTPC_HEAD = re.compile(r"<h5>(.*?)</h5>", re.S)


def _ntpc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    start = page.find(_NTPC_BOARD)
    if start < 0:
        return
    for body, day in _NTPC.findall(page, start):
        head = _NTPC_HEAD.search(body)
        href = next((h for h, _t in _A.findall(body) if _on_host("https://careers.ntpc.co.in", NTPC_PAGE, h)), "")
        yield day, href, head.group(1) if head else ""


def _ntpc_date(raw: str, _title: str = "") -> dt.date:
    """'2026-10-29'."""
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", raw.strip()):
        raise ValueError(raw)
    return dt.date.fromisoformat(raw.strip())


# Its headlines are sentences: "Result for the post of GDMO against Advt. No- 04/26", "Online interviews for the post of ... will be
# conducted ...", "The Computer Based Test ... is scheduled on 01.11.2026. Link to download admit card ...", "Recruitment of ...,
# Advt.12/26. Online application portal will remain open from ...", "Reference is made to Advt. No. 3/26 ... has been closed".
_NTPC_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|stands closed|has been (closed|cancelled)|cancel+ed)\b", re.I)),
    ("admit_card", re.compile(r"\badmit ?cards?\b", re.I)),
    ("result", re.compile(r"(^results?\b|\bscores?\b|\bcut[- ]?off\b|^list of (candidates|shortlisted)|^shortlisting\b|"
                          r"\boffer of appointment\b)", re.I)),
    ("schedule", re.compile(r"(\binterviews?\b|\bcomputer based test\b|\bCBT\b.*\b(scheduled|planned)\b|\bsyllabus\b)", re.I)),
    ("recruitment", re.compile(r"(\brecruitment\b|\bonline application\b|\bapplication window\b|\bdates? of (application|applying)\b)",
                               re.I)),
]
_ntpc_kind = _kinds(_NTPC_KINDS, "notice")


# New India Assurance: "Recruitment" is an Angular page rendered on the server; one accordion item per recruitment, its files below:
#   <h2 _ngcontent-sc464="" id="heading_RECRUITMENT_OF_ADMINISTRATIVE_OFFICERS_2025" class="accordion-header"><button ...>
#   RECRUITMENT OF ADMINISTRATIVE OFFICERS 2025 </button></h2> ... <li class="mt-2"><a href="assets/docs/recruitment/RECRUITMENT OF
#   ADMINISTRATIVE OFFICERS 2025/Notice - AO DR RE 2025.pdf" target="_blank">NOTICE - RECRUITMENT OF ...</a></li>
#   ... <a href="https://ibpsonline.ibps.in/niacljul25/intcla_jan26/login.php?appid=..." ...>CLICK HERE TO DOWNLOAD INTERVIEW
#   CALL-LETTER ( PHP , English )</a>
# No file carries a date, so each is dated the day it is first seen. The page declares <base href="/">: its relative links are
# resolved from the site's root (www.newindia.co.in/assets/docs/...), not from /recruitment/. A file's headline leads with its
# recruitment's; IBPS's (outside) links stay as items linking this page. The 4 MB rest of the page (translations) is not read.
NIACL_PAGE = "https://www.newindia.co.in/recruitment/list"
_NIACL_LIST = re.compile(r'<div[^>]*\bid="accordionExample" class="recruitment">(.*?)(?:</app-recruitment>|$)', re.S)
_NIACL_SECTION = re.compile(r'<h2\b[^>]*\bid="heading_[^"]*"[^>]*>\s*<button\b[^>]*>(.*?)</button>\s*</h2>(.*?)(?=<h2\b[^>]*\bid="heading_|$)',
                            re.S)
_BASE = re.compile(r'<base\s+href="([^"]*)"', re.I)
_NIACL_TAIL = re.compile(r"\s*\(\s*(?:PHP|FILE|PDF)\s*,\s*[A-Za-z]+\s*\)\s*$", re.I)


def _niacl_entries(page: str) -> Iterator[tuple[str, str, str]]:
    base_tag = _BASE.search(page[:5000])
    base = urllib.parse.urljoin(NIACL_PAGE, html.unescape(base_tag.group(1))) if base_tag else NIACL_PAGE
    host = "https://www.newindia.co.in"
    for block in _NIACL_LIST.findall(page):
        for name, body in _NIACL_SECTION.findall(block):
            section = _text(name)
            for href, inner in _A.findall(body):
                title = _NIACL_TAIL.sub("", _text(inner))
                if not title:
                    continue
                url = urllib.parse.urljoin(base, html.unescape(href.strip()))
                yield "", url if _on_host(host, base, href) else "", html.escape(f"{section}: {title}", quote=False)


_NIACL_KINDS = [
    ("amendment", re.compile(r":[^:]*\b(addendum|corrigendum|extension|extended)\b", re.I)),
    ("admit_card", re.compile(r":[^:]*\bcall[- ]?letter", re.I)),
    ("schedule", re.compile(r":\s*schedule of\b", re.I)),  # "SCHEDULE OF INTERVIEW FOR PROVISIONALLY SELECTED CANDIDATES ..."
    ("result", re.compile(r"(:[^:]*\b(shortlisted|selected|results?|cut-off|mark-?sheets?|qualified)\b|^RECRUITMENT : MARKSHEET:)", re.I)),
    ("schedule", re.compile(r":[^:]*\bschedule\b", re.I)),
    ("recruitment", re.compile(r":[^:]*(\bdetailed advertisement\b|\bapply on-? ?line\b|विस्तृत विज्ञप्ति)", re.I)),
]
_niacl_kind = _kinds(_NIACL_KINDS, "notice")


# AIIMS New Delhi: "AIIMS Recruitment" lists the advertisements open now (closed ones move to its archive):
#   <tr> <td> 1. </td> <td> Advertisement for Recruitment </td> <td> <a href="/index.php/en/notices/recruitment/aiims-recruitment?id=24933">
#   डीबीटी वित्त पोषित ... /Advertisement for the post of Scientific Administrative Asstt/Field work in DBT funded at Dept. of
#   Biochemistry, AIIMS</a> </td> <td> 19-10-2026 </td> <td> 30-10-2026 </td> </tr>
# The day is the date of publishing; each link opens the advertisement's own page on the site.
_AIIMS = re.compile(r"<tr>\s*<td>\s*\d+\.?\s*</td>\s*<td>\s*([^<]*?)\s*</td>\s*<td>\s*<a\s+href=\"([^\"]+)\"[^>]*>(.*?)</a>\s*</td>"
                    r"\s*<td>\s*([^<]*?)\s*</td>\s*<td>\s*[^<]*?\s*</td>\s*</tr>", re.S)


def _aiims_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for _category, href, title, published in _AIIMS.findall(page):
        yield published, href, title


_AIIMS_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|extension|extended|cancell?ation|cancell?ed)\b|शुद्धिपत्र", re.I)),
    ("result", re.compile(r"\b(result|selected candidates|shortlisted|merit list)\b|परिणाम", re.I)),
    ("schedule", re.compile(r"\b(interview schedule|schedule of interview)\b", re.I)),
]
_aiims_kind = _kinds(_AIIMS_KINDS, "recruitment")  # the page lists advertisements


NTPC = Board("ntpc", "https://careers.ntpc.co.in", NTPC_PAGE, "NTPC", _ntpc_entries, _ntpc_date, kind=_ntpc_kind, title_ids=True)
NIACL = Board("niacl", "https://www.newindia.co.in", NIACL_PAGE, "NIACL", _niacl_entries, _dmy, kind=_niacl_kind, undated=True)
AIIMS = Board("aiims", "https://www.aiims.edu", "https://www.aiims.edu/index.php/en/notices/recruitment/aiims-recruitment", "AIIMS",
              _aiims_entries, _dmy, min_items=3, kind=_aiims_kind)
ALL = (NTPC, NIACL, AIIMS)
