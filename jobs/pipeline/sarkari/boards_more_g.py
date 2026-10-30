"""Four state commissions' notice boards (same Board model as listboards.py), read from the India server.

Each board below was written against its page(s) of 30-10-2026 (tests/fixtures/<source>.html; bssc.html is the notice table cut
unchanged from the 452 KB page, the others are whole):

  bssc   Bihar Staff Selection Commission              bssc.bihar.gov.in   NoticeBoard.htm table: date | advt no | <a href="Advertisement/x.pdf">headline</a> (+ forms)
  cgpsc  Chhattisgarh Public Service Commission        psc.cg.gov.in       Notifications.php, Advertisement.php, Result.php: <li><a href='PDFs/../X (16-10-2026).pdf'>
                                                                           X (16-10-2026)</a>; model answers from the home page's per-exam lists
  hppsc  Himachal Pradesh Public Service Commission    hppsc.hp.gov.in     "What's New" fragment (HomeContent/WhatsNewPV): ten links, no dates (undated)
  appsc  Andhra Pradesh Public Service Commission      portal-psc.ap.gov.in home page "Announcements": "ATTENTION: headline - (Published on 27/10/2026) - Click Here",
                                                                           files on the commission's main site psc.ap.gov.in

The rules of listboards.py apply: a day, no time; links that leave the board's own site (exam agencies' application portals such
as bsscsteno.cbrt.co.in, online.ecgpsconline.in, applications-psc.ap.gov.in) are left out; a broken page raises FormatChanged.
Left out as no recruitment news: the offices' tenders and empanelments (BSSC); serving officers' departmental tests, half-yearly
examinations, computer proficiency tests and promotion tests, the RIMC (a school) entrance test and RTI returns (APPSC).
"""
from __future__ import annotations

import datetime as dt
import html
import re
import urllib.parse
from dataclasses import dataclass
from typing import Iterator

from .boards_more_a import PagesBoard
from .listboards import _TAG, Board, _dmy, kind_of

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _first(*kinds: tuple[str, re.Pattern]):
    def kind(title: str) -> str:
        return next((k for k, pattern in kinds if pattern.search(title)), None) or kind_of(title)
    return kind


# BSSC (NoticeBoard.htm): one table, newest first, back to 2016; a row is date | advertisement no. | notice, the notice cell
# holding the notice's link and, after it, the forms it needs:
#   <tr> <td align="center" class="Uline" bgcolor="#EAFFEA" nowrap>16-10-2026</td> <td align="center" class="Uline" ...>03/25</td>
#        <td class="Uline" ...><img ... src="Advertisement/new.gif" ...> <a class="wbg" target="_blank" href="Advertisement/4593_notice.pdf">
#        Important Notice Regarding Advt. No.- 03/25, Post- Field Assistant (Main Exam)</a><br><a ... href="Advertisement/03_25_main_advt.pdf">
#        Click Here To Download Advertisement and Syllabus</a></td> </tr>
# The hand-written table is uneven: a cell may miss its </td> (closed by </tr>), a row its <tr> (two rows in one), a cell its link
# (the notice is then the cell's text, linking the board). So each row's "Uline" cells are read three by three.
# The office's own purchases sit in the same table (advertisement column "Tender", "NIT/RFP for Selection of Agency ...") and are
# left out, as is the empanelment of advocates.
_BSSC_ROW = re.compile(r"<tr\b[^>]*>")
_BSSC_CELL = re.compile(r"""<td\b[^>]*\bclass="Uline"[^>]*>(.*?)(?=</td>|</tr>|$)""", re.S | re.I)
_BSSC_LINK = re.compile(r"""<a\b[^>]*?\bhref\s*=\s*"([^"]*)"[^>]*>(.*?)</a>""", re.S | re.I)
_BSSC_SKIP = re.compile(r"\b(tenders?|NIT|RFP|quotations?|empanelment|advocate panel|selection of agency)\b", re.I)


def _bssc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for row in _BSSC_ROW.split(page):
        cells = _BSSC_CELL.findall(row)
        for i in range(0, len(cells), 3):
            day, advt, notice = (cells[i:i + 3] + ["", ""])[:3]  # a short row: its entry is malformed
            link = _BSSC_LINK.search(notice)
            href, headline = (link.group(1), link.group(2)) if link else ("", notice)
            if _text(advt).lower() == "tender" or _BSSC_SKIP.search(_text(headline)):
                continue
            yield _WS.sub("", _text(day)), href, headline  # 2016-17 rows write "21 -07-2017", "01-12-201 6"


_BSSC_KIND = _first(
    ("amendment", re.compile(r"\b(corrigendum|date extension|extension of)\b", re.I)),
    ("answer_key", re.compile(r"\b(model answer|answer key)\b|आदर्श उत्तर|उत्तर\s*कुंजी", re.I)),
    ("result", re.compile(r"\b(final result|result of|p\.?\s?t\.? result)\b|परिणाम|परीक्षाफल", re.I)),
    ("admit_card", re.compile(r"\badmit ?card\b|प्रवेश\s*पत्र", re.I)),
)


# CGPSC (psc.cg.gov.in). Notifications.php, Advertisement.php and Result.php each list one folder's files, newest first:
#   <div class="inner-content"> <section> <div> <ul> <li><a href='PDFs/Notifications/MICRO_BIOLOGIST-2026_CAVEAT_SUCHANA (16-10-2026).pdf'
#   target='_blank'>MICRO_BIOLOGIST-2026_CAVEAT_SUCHANA (16-10-2026)</a></li> ...
# The day in brackets ends each headline (also "_(13-01-2026)", "ADVERTISEMENT(17-07-2026)"); a headline without one (an old
# cut-off list, a judgement) is dated the day first seen. Model answers have no list page: the home page's per-exam lists
# ("Integrated_Information") give them, each marked "[MODEL_ANSWER] ". The home page's "What's New" repeats the three lists'
# newest files (under lower-case folder names) and is not read.
# Headlines are file names: "_" is shown as a space.
CGPSC_PAGES = ("https://psc.cg.gov.in/Notifications.php", "https://psc.cg.gov.in/Advertisement.php",
               "https://psc.cg.gov.in/Result.php", "https://psc.cg.gov.in/Home_Welcome.php")
_CGPSC_LIST = re.compile(r"""<div class="inner-content">(.*?)</section>""", re.S)
_CGPSC_ITEM = re.compile(r"""<li>\s*<a\s+href='([^']*)'[^>]*>(.*?)</a>\s*</li>""", re.S)
_CGPSC_ANSWER = re.compile(r"""<a\s+href='(PDFs/model_answer/[^']*)'[^>]*>\s*\[MODEL_ANSWER\]\s*(.*?)</a>""", re.S)
_CGPSC_DAY = re.compile(r"\(\s*(\d{2}-\d{2}-\d{4})\s*\)")


def _cgpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    found = [m for block in _CGPSC_LIST.findall(page) for m in _CGPSC_ITEM.findall(block)]
    found += _CGPSC_ANSWER.findall(page)
    for href, headline in found:
        title = _text(headline).replace("_", " ")
        days = _CGPSC_DAY.findall(title)
        yield (days[-1] if days else ""), href, html.escape(title, quote=False)


_CGPSC_KIND = _first(
    ("answer_key", re.compile(r"\bmodel answer\b", re.I)),
    ("notice", re.compile(r"\b(judgement|khandan)\b", re.I)),  # a court ruling; a rebuttal ("khandan") of a rumour
    ("amendment", re.compile(r"\b(corrigendum|postponement|extension|amendment)\b", re.I)),
    ("admit_card", re.compile(r"\badmit card\b|\bcall letter\b", re.I)),
    ("result", re.compile(r"\b(result|selection list|statement of marks|cut off|cout off|merit list)\b", re.I)),
    ("schedule", re.compile(r"\bschedule\b", re.I)),
    ("recruitment", re.compile(r"\badvertisement\b", re.I)),
)


# HPPSC: the home page loads "What's New" from HomeContent/WhatsNewPV, a fragment of ten links without dates:
#   <li class="mb-3 lh-1"> <a style="..." data-linktypecd="F" target="_blank" href=/CommonControls/ViewCMSFile?qs=KI3g...%3D>Press Note - ...</a>
# (links of type "H" are absolute: https://hppsc.hp.gov.in/CommonControls/CMSFileView?qs=...). The qs values are encrypted ids:
# an entry is known by its headline (title_ids) and dated the day it is first seen (undated).
_HPPSC_ITEM = re.compile(r"""<li class="mb-3 lh-1">\s*<a\b[^>]*?\bhref=("[^"]*"|'[^']*'|[^\s>]+)[^>]*>(.*?)</a>\s*</li>""", re.S)


def _hppsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, headline in _HPPSC_ITEM.findall(page):
        yield "", href.strip("\"'"), headline


def _no_date(raw: str, _title: str) -> dt.date:
    raise ValueError(raw)  # HPPSC shows no date: undated, dated the day first seen


_HPPSC_KIND = _first(
    ("amendment", re.compile(r"\b(addendum|corrigendum)\b", re.I)),
    ("admit_card", re.compile(r"\be-?call letters?\b|\badmit card", re.I)),
    ("result", re.compile(r"\bresults?\b", re.I)),
    ("schedule", re.compile(r"\bschedule\b", re.I)),
    ("recruitment", re.compile(r"^advertisement (number|no\.?)\s*:", re.I)),
)


# APPSC (portal-psc.ap.gov.in/Default.aspx): "Announcements", newest first, back to 2025; new ones carry class="blinkNew":
#   <div class="row marquee_div" style="height: auto;"> <div class="col-xs-12 col-sm-12"> <p ...>
#   <span style="font-size: 15px; color: #00436f;" class="blinkNew">ATTENTION: Web note on Errata for Forest Section Officer (17/2026)
#   and Forest Beat Officer (18/2026) - (Published on 23/10/2026) - <a href="https://psc.ap.gov.in/Documents/NotificationDocuments/
#   Errata_FSO_FBO.pdf" target="_blank"> <span style="color: #fff">Click Here</span></a></span> </p> </div> </div>
# The files sit on the commission's main site, psc.ap.gov.in (also its online application page); links to its applications portal
# (applications-psc.ap.gov.in) and ASP.NET postbacks are left out. Serving officers' departmental tests, half-yearly examinations
# and computer proficiency test sessions, promotion tests, the RIMC (a school) entrance test and RTI returns are no recruitment
# news and are left out (a CPT "for the post of" a recruitment is kept).
_APPSC_ITEM = re.compile(r"""<div class="row marquee_div"[^>]*>\s*<div class="col-xs-12 col-sm-12">\s*<p\b[^>]*>\s*"""
                         r"""<span style="font-size: 15px; color: #00436f;"(?: class="blinkNew")?>(.*?)<a\b([^>]*)>.*?</a>\s*</span>""", re.S)
_APPSC_HREF = re.compile(r"""\bhref\s*=\s*"([^"]*)\"""")
_APPSC_DAY = re.compile(r"\bPublished(?:\s+date)?\s+on\s*:?\s*(\d{1,2}/\d{1,2}/\d{4})", re.I)
_APPSC_STAFF = re.compile(r"\b(departmental|half[- ]?yearly|appointment by promotion|RTI Act|RIMC|Rashtriya Indian Military College)\b", re.I)
_APPSC_CPT = re.compile(r"\b(CPT|computer proficiency|proficiency in office automation)\b", re.I)


def _appsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for text, attrs in _APPSC_ITEM.findall(page):
        title = re.sub(r"^ATTENTION\s*:\s*|\s*-\s*$", "", _text(text))
        if _APPSC_STAFF.search(title) or (_APPSC_CPT.search(title) and not re.search(r"\bposts?\b", title, re.I)):
            continue
        days = _APPSC_DAY.findall(title)
        h = _APPSC_HREF.search(attrs)
        yield (days[-1] if days else ""), h.group(1) if h else "", html.escape(title, quote=False)


_APPSC_KIND = _first(
    ("amendment", re.compile(r"\b(extension of last date|errata|addendum|corrigendum|postpone|correction application)\b", re.I)),
    ("answer_key", re.compile(r"\b(final keys?|initial keys?)\b", re.I)),
    ("admit_card", re.compile(r"\bhall ?tickets?\b", re.I)),
    ("result", re.compile(r"\b(result|selection notification|selected candidates|marks|marks ?list)\b", re.I)),
    ("schedule", re.compile(r"\b(schedule|time ?table)\b", re.I)),
    ("recruitment", re.compile(r"^(detailed notification|brief notification|direct recruitment|online application submission)\b", re.I)),
)


@dataclass(frozen=True)
class _TwoHostBoard(Board):
    also: tuple[str, ...] = ()  # the board's own files on a second host of its own (APPSC's psc.ap.gov.in)

    def url(self, href: str) -> str:
        url = urllib.parse.urljoin(self.NOTICE_PAGE, html.unescape(href.strip()))
        p = urllib.parse.urlsplit(url)
        if p.scheme == "https" and f"https://{p.netloc}" in self.also:
            return urllib.parse.urlunsplit((p.scheme, p.netloc, urllib.parse.quote(urllib.parse.unquote(p.path), safe="/"),
                                            p.query, ""))
        return super().url(href)


BSSC = Board("bssc", "https://bssc.bihar.gov.in", "https://bssc.bihar.gov.in/NoticeBoard.htm", "BSSC", _bssc_entries, _dmy,
             kind=_BSSC_KIND)
CGPSC = PagesBoard("cgpsc", "https://psc.cg.gov.in", CGPSC_PAGES[0], "CGPSC", _cgpsc_entries, _dmy, kind=_CGPSC_KIND,
                   undated=True, MORE_PAGES=CGPSC_PAGES[1:])
HPPSC = Board("hppsc", "https://hppsc.hp.gov.in", "https://hppsc.hp.gov.in/HomeContent/WhatsNewPV", "HPPSC", _hppsc_entries,
              _no_date, min_items=3, kind=_HPPSC_KIND, undated=True, title_ids=True)
APPSC = _TwoHostBoard("appsc", "https://portal-psc.ap.gov.in", "https://portal-psc.ap.gov.in/Default.aspx", "APPSC",
                      _appsc_entries, _dmy, kind=_APPSC_KIND, also=("https://psc.ap.gov.in",))
ALL = (BSSC, CGPSC, HPPSC, APPSC)
