"""Central recruiters (armed police, the national exam agency) whose notice board is one server-rendered list.

Each board below was written against its page of 29-10-2026, read from the India server (tests/fixtures/<source>.html):

  crpf   Central Reserve Police Force (recruitment)       rect.crpf.gov.in   one table per recruitment: document | "22-Oct-2026" | PDF
  nta    National Testing Agency ("Latest @ NTA")         nta.ac.in          headline, then "/Download/Notice/Notice_20261027235155.pdf";
                                                                             only UGC-NET, Joint CSIR-UGC NET, NTET and NTA's own vacancies
                                                                             or recruitment exams; admission tests (JEE, NEET, CUET, ...) are left out

Not collectable from what these sites serve (29-10-2026):
  kvs        kvsangathan.nic.in lands on kvs.gov.in's Hindi home page: its "अद्यतनीकरण" box holds 8 items that mix office
             orders and audit circulars with staff-only departmental exam (LDCE) panels; the recruitment page is not on it.
  indiapost  www.indiapost.gov.in's home page has no recruitment list (only a link to /vacancies).
  navy       www.joinindiannavy.gov.in lists 6 news links with no date, most marked "(Closed)".
  itbp       recruitment.itbpolice.nic.in shows 2 notice-board PDFs with no date (sports quota 2025).

The rules of listboards.py apply: a day, no time; links leaving the board's own host (NTA's exam portals,
the NIC CDN, CRPF's online application site) are left out; a broken page raises FormatChanged.
"""
from __future__ import annotations

import datetime as dt
import html
import re
from typing import Iterator

from .listboards import _TAG, Board, _dmy, kind_of

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


# CRPF: <h3 class="heading-3"> 2 ) CONTRACTUAL APPOINTMENT OF 15 GENERAL DUTY MEDICAL OFFICERS ... </h3> then a table:
#   <tr> <td> 1</td> <td><a href="/Upload/Recruitment/Recruitment_Advertisement_333102026-941.pdf" ...><p>WINDOW ADVERTISEMENT ...</p>
#   </a> </td> <td style="width:130px;"> 19-Oct-2026 </td> <td> <a href="..." class="view-pdf-btn" ...>
# A row's own text can be a bare "CORRIGENDUM/ADDENDUM" or "Click here to apply": a short one is prefixed with the table's heading.
# The first table (lists of dependants appointed on compassionate grounds) is no open recruitment and is left out.
_CRPF = re.compile(r"""<h3 class="heading-3">(.*?)</h3>|<tr>\s*<td>\s*\d+\s*</td>\s*<td>\s*<a href="([^"]*)"[^>]*>(.*?)</a>\s*</td>\s*"""
                   r"""<td[^>]*>\s*([^<]*?)\s*</td>""", re.S)
_CRPF_SKIP = re.compile(r"\bcompassionate\b", re.I)
_CRPF_SHORT = 8  # words: fewer than this and the row needs its heading to make sense


def _crpf_entries(page: str) -> Iterator[tuple[str, str, str]]:
    heading = ""
    for head, href, inner, day in _CRPF.findall(page):
        if head:
            heading = re.sub(r"^\d+\s*\)\s*", "", _text(head))
            continue
        if _CRPF_SKIP.search(heading):
            continue
        text = _text(inner)
        short = len(text.split()) < _CRPF_SHORT and text.lower() not in heading.lower()
        yield day, href, (f"{heading}: {text}" if short and heading else text)


def _crpf_date(raw: str, _title: str = "") -> dt.date:
    """'19-Oct-2026'."""
    return dt.datetime.strptime(raw.strip(), "%d-%b-%Y").date()


def _crpf_kind(title: str) -> str:
    """'(Amendment dated 17/04/2026)' changes an advertisement; a correction window is a step, not a new recruitment;
    'WINDOW ADVERTISEMENT FOR ...', 'WALK-IN-INTERVIEW ...', '... की भर्ती' open applications."""
    if re.search(r"\bamendment\b", title, re.I):
        return "amendment"
    if re.search(r"\bcorrection\b", title, re.I):
        return "notice"
    kind = kind_of(title)
    if kind == "notice" and re.search(r"(\b(advertisement|walk[- ]*in|recruitment for the post|click here to apply|link to apply)\b|भर्ती)",
                                      title, re.I):
        return "recruitment"
    return kind


# NTA ("Latest @ NTA", newest first, the whole archive since 2019 in one scrolling box):
#   <p> <i class="fa fa-arrow-circle-right" aria-hidden="true"></i>
#       <content style="color:#012B55">Extension of Last Date ... UGC-NET  December 2026 Examination – Reg.&nbsp;</content>
#       <a href="/Download/Notice/Notice_20261027235155.pdf" class="orange-text" target="_blank"><strong>Read More&nbsp;</strong></a>
# A few entries are the link itself (<a href="http://cdnbbsr...">headline</a>): off the host, left out.
# The file is named after its upload time (YYYYMMDDhhmmss); that day dates the notice.
_NTA = re.compile(r"""<p>\s*<i class="fa fa-arrow-circle-right"[^>]*>\s*</i>(.*?)</p>""", re.S)
_NTA_CONTENT = re.compile(r"<content[^>]*>(.*?)</content>", re.S)
_NTA_LINK = re.compile(r"""<a\b[^>]*?href="([^"]+)"[^>]*>(.*?)</a>""", re.S)
_NTA_DAY = re.compile(r"/Notice_(20\d{2})(\d{2})(\d{2})\d{6}\.pdf$", re.I)
# Kept: exams that lead to jobs (UGC-NET: Assistant Professor / JRF; Joint CSIR-UGC NET; NTET: eligibility to teach in
# Indian System of Medicine colleges) and NTA's own posts or the recruitment exams it runs for others.
# Left out: admission tests (JEE, NEET, CUET, NCET, ICAR AIEEA/AICE-JRF for Ph.D. seats, NIFTEE, ...), NITTT (training of
# serving teachers), empanelment of experts or hotels, and NTA's general notices.
_NTA_KEEP = re.compile(r"(\bUGC\s*[-–]?\s*NET\b|\bCSIR\b|\bNTET\b|teachers?'?\s*eligibility test|\bvacanc|\brecruitment\b|"
                       r"\bposts?\b(?!\s*-?\s*graduate)|\bpost\(s\))", re.I)


def _nta_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for block in _NTA.findall(page):
        link = _NTA_LINK.search(block)
        if not link:
            continue  # a headline with no file (one, 11-2025): nothing to link
        content = _NTA_CONTENT.search(block)
        headline = content.group(1) if content else link.group(2)
        if not _NTA_KEEP.search(_text(headline)):
            continue
        day = _NTA_DAY.search(link.group(1))
        yield (f"{day.group(3)}-{day.group(2)}-{day.group(1)}" if day else ""), link.group(1), headline


def _nta_kind(title: str) -> str:
    """'Vacancy Notification for Librarian' is NTA's own job advertisement, not a vacancy table."""
    if re.match(r"vacancy notification\b", title, re.I):
        return "recruitment"
    kind = kind_of(title)
    if kind != "notice":
        return kind
    if re.search(r"\b(declaration of (the )?(results?|scores?)|declares the result|cut ?off marks)\b", title, re.I):
        return "result"
    if re.search(r"\b(inviting online applications?|invitation of applications|opening of the online registration)\b",
                 title, re.I):
        return "recruitment"
    return kind


CRPF = Board("crpf", "https://rect.crpf.gov.in", "https://rect.crpf.gov.in/", "CRPF", _crpf_entries, _crpf_date,
             kind=_crpf_kind)
NTA = Board("nta", "https://nta.ac.in", "https://nta.ac.in/", "NTA", _nta_entries, _dmy, kind=_nta_kind)
ALL = (CRPF, NTA)
