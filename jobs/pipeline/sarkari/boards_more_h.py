"""TNPSC, ITBP, ESIC and NABARD notice boards (same Board model as listboards.py).

Each board below was written against its page of 30-10-2026 as saved by the India server (tests/fixtures/<source>.html;
nabard.html is cut unchanged from the 646 KB page, see tests/test_boards_more_h.py):

  tnpsc   Tamil Nadu Public Service Commission    tnpsc.gov.in                 home page "Whats new": <div class="event-date" title="Oct 29, 2026">, headline "(Certificate Verification)"
  itbp    Indo-Tibetan Border Police recruitment  recruitment.itbpolice.nic.in "NEWS" and "RESULTS" tables: SL NO | title | /noticeboards/downloadpdf/N.pdf | 03-10-2026
  esic    Employees' State Insurance Corporation  www.esic.gov.in              recruitments table: office | subject (PDFs) | publish date 2026-10-29 | Result / Last Date / Interview
  nabard  NABARD careers notices (Hindi page)     www.nabard.org               one block per recruitment, its files below it, no dates: dated the day first seen (undated)

Not collectable: EPFO (www.epfo.gov.in/recruitments/ says "No jobs found."; its archive's newest entry is of 21-07-2025
and most of its files are on another host, pmvbry-cdn.epfindia.gov.in).

The rules of listboards.py apply: a day, no time; links leaving the board's own host (exam agencies' portals such as
ibpsreg.ibps.in or apply.tnpscexams.in) are left out; a broken page raises FormatChanged.
"""
from __future__ import annotations

import datetime as dt
import html
import re
from typing import Iterator

from .boards_more_a import PagesBoard
from .listboards import _TAG, Board, _dmy, kind_of

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _kinds(rules: list[tuple[str, re.Pattern]], fallback: str | None = None):
    def kind(title: str) -> str:
        return next((k for k, p in rules if p.search(title)), None) or fallback or kind_of(title)
    return kind


# TNPSC: the home page's "Whats new" scroller, newest first (Aug-Oct 2026 on 30-10-2026); two old entries sit in HTML
# comments and are not shown:
#   <li class="event m-has-date"> <div class="event-inner"> <div class="event-date" title="Oct 29, 2026">
#   <span class="event-month">Oct</span> <span class="event-day">29</span> </div>
#   <h4 class="event-title"><a href="../english/latest_results.aspx?c=4593" target=_blank> COMBINED TECHNICAL SERVICES
#   EXAMINATION (DIPLOMA/ITI LEVEL )  (Certificate Verification)</a></h4> </div> </li>
# Each headline links the commission's section page (results, press releases, counselling), not a file, and the same
# headline comes back on new days (a new certificate-verification list, a new counselling round): each day is a new
# update (dated_ids). The office's tenders and the serving officers' departmental examinations are left out.
_COMMENT = re.compile(r"<!--.*?-->", re.S)
_TNPSC = re.compile(r"""<li class="event m-has-date">\s*<div class="event-inner">\s*<div class="event-date" title="([^"]*)">"""
                    r""".*?</div>\s*<h4 class="event-title">\s*<a\b[^>]*?href="([^"]*)"[^>]*>(.*?)</a>\s*</h4>""", re.S)
_TNPSC_SKIP = re.compile(r"(tender\.aspx|DResultView\.aspx|\btenders?\b|\bdepartmental examinations?\b)", re.I)


def _tnpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for day, href, headline in _TNPSC.findall(_COMMENT.sub("", page)):
        if _TNPSC_SKIP.search(href + " " + _text(headline)):
            continue
        yield day, href, headline


def _mon_day_year(raw: str, _title: str = "") -> dt.date:
    """'Oct 29, 2026'."""
    return dt.datetime.strptime(_WS.sub(" ", raw.strip()), "%b %d, %Y").date()


# The headline ends with the step in brackets; a press release says what it is in its own words.
_TNPSC_KINDS = [
    ("recruitment", re.compile(r"\(Notification\)$")),
    ("admit_card", re.compile(r"\(Oral Test Memo\)$")),
    ("result", re.compile(r"\((Final Result|Results?|Oral Test Mark|Oral Test|Certificate Verification)\)$")),
    ("schedule", re.compile(r"\(Counselling\)$")),
    ("amendment", re.compile(r"\b(postpone|reschedul|corrigendum|addendum to the notification)", re.I)),
    ("answer_key", re.compile(r"\b(tentative )?answer keys?\b", re.I)),
    ("result", re.compile(r"\b(provisionally admitted|marks and rank|list of ocv|zone of consideration|shortlisted)", re.I)),
    ("schedule", re.compile(r"^date of\b", re.I)),
]
_tnpsc_kind = _kinds(_TNPSC_KINDS, "notice")


# ITBP: two pages of one table each, newest first, back to 2023 (NEWS) and 2022 (RESULTS):
#   <div class="panel-heading"><h4>NEWS</h4></div> ... <tbody> <tr align="left"> <td class="bg-success">1</td>
#   <td class="bg-success">DETAILED ADVERTISEMENT FOR RECRUITMENT OF ... UNDER...</td>
#   <td class="bg-success"> <i class='glyphicon glyphicon-download-alt'> <a href="/noticeboards/downloadpdf/361.pdf">DETAILED...</a></i>
#   </td> <td class="bg-success">03-10-2026</td> <br>
# (rows have no </tr>). The site itself cuts long titles with "..."; a few rows have no file and link their page.
_ITBP_PANEL = re.compile(r"""<div class="panel-heading"><h4>(NEWS|RESULTS)</h4></div>(.*?)</table>""", re.S)
_ITBP_ROW = re.compile(r"""<tr align="left">\s*<td class="bg-success">\s*\d+\s*</td>\s*<td class="bg-success">(.*?)</td>\s*"""
                       r"""<td class="bg-success">(.*?)</td>\s*<td class="bg-success">\s*([^<]*?)\s*</td>""", re.S)
_ITBP_FILE = re.compile(r"""<a href="(/noticeboards/downloadpdf/\d+\.pdf)">""")
_ITBP_PAGES = {"NEWS": "/statics/news", "RESULTS": "/statics/results"}


def _itbp_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for panel, table in _ITBP_PANEL.findall(page):
        for headline, cell, day in _ITBP_ROW.findall(table):
            f = _ITBP_FILE.search(cell)
            yield day, f.group(1) if f else _ITBP_PAGES[panel], headline


_ITBP_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|amendment|change of (centre|date)|additional vacancies)", re.I)),
    ("admit_card", re.compile(r"\badmit card\b", re.I)),
    ("result", re.compile(r"\b(result|shortlisted|selected candidates|finally selected|force allocation|select list|"
                          r"supplementary list|roll (numbers?|no)\b)", re.I)),
    ("recruitment", re.compile(r"^(detailed )?(advertisement|advertisment|recruitment)\b", re.I)),
]
_itbp_kind = _kinds(_ITBP_KINDS)


# ESIC: the recruitments table, newest first (ten rows a page; the list's further pages are older):
#   <tr> <td>1</td> <td>ESIC Medical College and Hospital Gulbarga, Kalaburagi</td> <td>
#   <a href="/attachments/recruitmentfile/Declaration_of_Result_..._1793273627.pdf" target = "_blank" class="red-txt" ...>
#   Declaration of Result ... - PDF <img .../> size:(663.14 KB) .</a> </a> </br> ... </td> <td>2026-10-29</td>
#   <td>Result</br></td> <td>12696/2026</td> </tr>
# The third column holds the notice's files (a row can have two: the selection lists of two departments, or an
# advertisement and its application form): each file is an item, headed "office: subject".
_ESIC_ROW = re.compile(r"""<tr>\s*<td>\s*\d+\s*</td>\s*<td>(.*?)</td>\s*<td>(.*?)</td>\s*<td>\s*(\d{4}-\d{2}-\d{2})\s*</td>""", re.S)
_ESIC_FILE = re.compile(r"""<a href="(/attachments/recruitmentfile/[^"]+)"[^>]*>(.*?)</a>""", re.S)
_ESIC_SIZE = re.compile(r"\s*-?\s*PDF\s+size:\s*\([^)]*\)\s*\.?\s*$", re.I)


def _esic_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for office, cell, day in _ESIC_ROW.findall(page):
        for href, subject in _ESIC_FILE.findall(cell):
            yield day, href, f"{_text(office)}: {_ESIC_SIZE.sub('', _text(subject))}"


def _iso(raw: str, _title: str = "") -> dt.date:
    return dt.date.fromisoformat(raw.strip())


_ESIC_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|postpone|extension|cancell?ation)", re.I)),
    ("result", re.compile(r"\b(result|selection list|selected|successful candidates|merit list|shortlisted)", re.I)),
    ("notice", re.compile(r":\s*(application|annexure|format)\b", re.I)),  # a form filed beside its advertisement
    ("recruitment", re.compile(r"\b(walk[- ]*in|recruitment|engagement|application|advertisement|vacanc)", re.I)),
]
_esic_kind = _kinds(_ESIC_KINDS)


# NABARD (Hindi careers notices; the English page is not read): one block per recruitment, newest first, then its files:
#   <div class="career_row"> <div class="career_header"> <i class="icofont-ui-file"></i>
#   <div class="career_title">संविदा आधार पर विशेषज्ञ की भर्ती (वर्ष 2026-27) – ... के लिए परिणाम</div> </div> ...
#   <div class="download_file"> <div class="download_pdf" style='display:inline;'> <a class="pdf-link"
#   href="auth/writereaddata/CareerNotices/792581022Result - Specialist – Derivatives & Treasury Management.pdf" ...>Result - ...</a>
#   ... <div class="ext_link" style='display:none;'> <a href="" ...>...</a> </div>
# A file's slot left empty ("CareerNotices/") or an empty link is hidden on the page and skipped; links to IBPS's
# registration portal are left out as everywhere else. The page gives no dates (the numbers file names start with
# stopped being dates in 2026), so a file is dated the day it is first seen.
_NABARD_ROW = re.compile(r"""<div class="career_row">(.*?)(?=<div class="career_row">|$)""", re.S)
_NABARD_TITLE = re.compile(r"""<div class="career_title">(.*?)</div>""", re.S)
_GENERIC = re.compile(r"^(click here|(यूआरएल )?लिंक|यहा[ँं] डाउनलोड करें|)$", re.I)  # labels that say nothing: the block's title
_NABARD_LINK = re.compile(r"""<a class="pdf-link" href="([^"]*)"[^>]*>(.*?)</a>|"""
                          r"""<div class="ext_link"[^>]*>\s*<a href="([^"]*)"[^>]*>(.*?)</a>""", re.S)


def _nabard_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for row in _NABARD_ROW.findall(page):
        t = _NABARD_TITLE.search(row)
        title = _text(t.group(1)) if t else ""
        for pdf, pdf_text, ext, ext_text in _NABARD_LINK.findall(row):
            href, text = (pdf, pdf_text) if pdf or pdf_text else (ext, ext_text)
            href = href.strip()
            if not href or href.endswith("/CareerNotices/"):
                continue
            text = _text(text)
            yield "", href, title if text == title or _GENERIC.match(text) else f"{title}: {text}"


def _no_date(raw: str, _title: str = "") -> dt.date:
    raise ValueError("NABARD gives no dates")


_NABARD_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|extension)\b|शुद्धिपत्र|परिशिष्ट", re.I)),
    ("answer_key", re.compile(r"\banswer key\b|उत्तर कुंजी", re.I)),
    ("admit_card", re.compile(r"\b(call letter|admit card)\b|प्रवेश पत्र|बुलावा पत्र|आमंत्रण पत्र|कॉल लेटर", re.I)),
    ("result", re.compile(r"\b(result|shortlisted|select list|wait list|cut[- ]?off|marks|score ?card)\b|परिणाम|"
                          r"चयनित|प्रतीक्षा सूची|कट[- –]*ऑफ|प्राप्तांक|स्कोरकार्ड|शॉर्टलिस्ट", re.I)),
    ("recruitment", re.compile(r"\b(advertisement|advtertisement|advertisment|notification|apply)\b|विज्ञापन|अप्लाई|आवेदन करें", re.I)),
]
_nabard_kind = _kinds(_NABARD_KINDS, "notice")


TNPSC = Board("tnpsc", "https://tnpsc.gov.in", "https://tnpsc.gov.in/home.aspx", "TNPSC", _tnpsc_entries, _mon_day_year,
              kind=_tnpsc_kind, dated_ids=True)
ITBP = PagesBoard("itbp", "https://recruitment.itbpolice.nic.in", "https://recruitment.itbpolice.nic.in/statics/news", "ITBP",
                  _itbp_entries, _dmy, kind=_itbp_kind,
                  MORE_PAGES=("https://recruitment.itbpolice.nic.in/statics/results",))
ESIC = Board("esic", "https://www.esic.gov.in", "https://www.esic.gov.in/recruitments", "ESIC", _esic_entries, _iso,
             kind=_esic_kind)
NABARD = Board("nabard", "https://www.nabard.org", "https://www.nabard.org/Hindi/careers-notices1.aspx?cid=693&id=26", "NABARD",
               _nabard_entries, _no_date, kind=_nabard_kind, undated=True)
ALL = (TNPSC, ITBP, ESIC, NABARD)
