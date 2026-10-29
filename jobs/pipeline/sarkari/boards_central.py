"""Central recruiters (bank exam agency, PSUs) whose notice board is one server-rendered list.

Each board below was written against its page of 29-10-2026 (tests/fixtures/<source>.html):

  ibps   Institute of Banking Personnel Selection       www.ibps.in    "CRP updates" (View All) list: a link around "29 Oct 26"
                                                                       and the headline (read from the India server; ibps.in
                                                                       doesn't answer GitHub)
  aai    Airports Authority of India (recruitment)      www.aai.aero   exam table: one row per exam, cells "Updated On 28-09-2026"
                                                                       for advertisement, press note, syllabus and result
  iocl   Indian Oil Corporation (latest job openings)   iocl.com       cards: heading, then file links, some ending "[14/08/2026]";
                                                                       the rest carry no date (dated the day first seen)
  becil  Broadcast Engineering Consultants India        www.becil.com  cards: "Advt No. 563: ...", "Upload Date : 19-Oct-2026", PDF

Not collectable from what these sites serve (29-10-2026):
  rbi    opportunities.rbi.org.in answers GitHub with a JavaScript bot challenge; the India server gets the
         real lists: boards_more_a.py.
  lic    www.licindia.in/careers lists 7 openings with no dates, most behind script-only links.

The rules of listboards.py apply: a day, no time; links leaving the board's own host (registration
portals such as ibpsreg.ibps.in or ioclapply.com) are left out; a broken page raises FormatChanged.
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


# IBPS "CRP updates" (www.ibps.in/index.php/crp-updates/, the home page's "View All": every update, newest first):
#   <a href="https://www.ibps.in/wp-content/uploads/....pdf" target="_blank"> <div class="detail-section">
#   <div class="detail-list"> <div class="detail-first-heading">29 Oct 26</div>
#   <div class="detail-second-heading">Advertisement for Direct Recruitment of ...</div> </div> </div> </a>
# (The table's own header row is "detail-first-heading fh": not matched.)
_IBPS = re.compile(r"""<a href="([^"]*)"[^>]*>\s*<div class="detail-section">\s*<div class="detail-list">\s*"""
                   r"""<div class="detail-first-heading">\s*([^<]*?)\s*</div>\s*<div class="detail-second-heading">(.*?)</div>""", re.S)
# IBPS's own notices about the institute, not about any exam or post
_IBPS_SKIP = re.compile(r"\btrade ?mark\b|\bproprietary rights\b|\bISO \d{4,5}\b", re.I)


def _ibps_kind(title: str) -> str:
    """'Scores of Online Preliminary Examination': each candidate's marks, i.e. the result."""
    return "result" if re.search(r"\bscores? of\b", title, re.I) else kind_of(title)


def _ibps_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, day, headline in _IBPS.findall(page):
        if not _IBPS_SKIP.search(_text(headline)):
            yield day, href, headline


def _ibps_date(raw: str, _title: str = "") -> dt.date:
    """'29 Oct 26' (two-digit year)."""
    m = re.fullmatch(r"(\d{1,2}) ([A-Za-z]{3}) (\d{2})", raw.strip())
    if not m:
        raise ValueError(raw)
    return dt.datetime.strptime(f"{m.group(1)} {m.group(2).title()} 20{m.group(3)}", "%d %b %Y").date()


def _day_mon_year(raw: str, _title: str = "") -> dt.date:
    """'19-Oct-2026' (BECIL)."""
    m = re.fullmatch(r"\s*(\d{1,2})[ -]([A-Za-z]{3}),?[ -](\d{4})\s*", raw)
    if not m:
        raise ValueError(raw)
    return dt.datetime.strptime(f"{m.group(1)} {m.group(2).title()} {m.group(3)}", "%d %b %Y").date()


# AAI: <tr class="odd|even ..."> <td class="views-field views-field-title">EXAM NAME</td> ...
#   <td class="views-field views-field-field-job-post-date"><span ...>28-09-2026</span></td>
#   <td ...field-exam-release-date"><a href="/en/recruitment/release/748316" ...><div>Updated On <span ...>28-09-2026</span>...
#   press note / syllabus / result cells: <a href="https://www.aai.aero/en/recruitment/<cell>/<id>" ...><div>Updated On <span>DD-MM-YYYY
# One update per filled cell. The advertisement is dated by its Job Post Date; a press note, syllabus or
# result page by its "Updated On" day, and (dated_ids) a later day on the same page is a new update.
_AAI_ROW = re.compile(r"""<tr class="(?:odd|even)[^"]*">(.*?)</tr>""", re.S)
_AAI_TITLE = re.compile(r"""<td class="views-field views-field-title"\s*>(.*?)</td>""", re.S)
_AAI_POSTED = re.compile(r"""views-field-field-job-post-date"\s*>\s*<span[^>]*>\s*([^<]*?)\s*</span>""", re.S)
_AAI_CELL = re.compile(r"""<a href="((?:https://www\.aai\.aero)?/en/recruitment/(release|press-note|syllabus|result)/\d+)"[^>]*>"""
                       r"""\s*<div>\s*Updated On\s*<span[^>]*>\s*([^<]*?)\s*</span>""", re.S)
AAI_LABELS = {"release": "Advertisement", "press-note": "Press note", "syllabus": "Syllabus", "result": "Result"}
AAI_KINDS = {"Advertisement": "recruitment", "Press note": "notice", "Syllabus": "notice", "Result": "result"}


def _aai_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for row in _AAI_ROW.findall(page):
        title = _AAI_TITLE.search(row)
        posted = _AAI_POSTED.search(row)
        for href, cell, updated in _AAI_CELL.findall(row):
            day = (posted.group(1) if posted else "") if cell == "release" else updated
            yield day, href, f"{AAI_LABELS[cell]}: {title.group(1) if title else ''}"


def _aai_kind(title: str) -> str:
    label, _, exam = title.partition(": ")
    if re.search(r"\bempanelment\b", exam, re.I):
        return "notice"  # arbitrators' panel, not a job
    return AAI_KINDS.get(label, "notice")


# IOCL: <div class="divscreeen1"> ... <li class="liscreen"><span><a [href="FILE"] ...><strong ...>HEADING</strong>...</a></span>
#   <ul class="job-list"><li class="liscreen"><a href="FILE" ...>Click Here to Read the Detailed Advertisement - [03/09/2026] <img ...></a></li> ...
# A link whose text ends "[DD/MM/YYYY]" carries its day; every other file is dated the day it is first seen.
# The forms every advertisement repeats (caste certificate formats, undertakings, fitness guidelines) are left out.
_IOCL_CARD = '<div class="divscreeen1">'
_IOCL_HEAD = re.compile(r"""<li class="liscreen">\s*<span>\s*<a\b([^>]*)>\s*<strong[^>]*>(.*?)</strong>.*?</a>\s*</span>""", re.S)
_IOCL_LINK = re.compile(r"""<a\b([^>]*)>(.*?)</a>""", re.S)
_IOCL_HREF = re.compile(r'href="([^"]*)"')
_IOCL_DAY = re.compile(r"\[\s*(\d{1,2}/\d{1,2}/\d{4})\s*\]\s*$")
_IOCL_FORMS = re.compile(r"(?<![a-z])(formats?|undertakings?|guidelines|handout|checklist|mandate|affidavit|caste certificate|"
                         r"disability certificate|certificate regarding|certificate for employed|application form|"
                         r"application for|terms ?& ?conditions?)(?![a-z])", re.I)  # "PwBD_Certificate ...": no \b after "_"


def _iocl_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for card in page.split(_IOCL_CARD)[1:]:
        head = _IOCL_HEAD.search(card)
        if not head:
            continue
        heading = _text(head.group(2))
        h = _IOCL_HREF.search(head.group(1))
        if h:
            yield "", h.group(1), heading
        for attrs, inner in _IOCL_LINK.findall(card[head.end():]):
            h = _IOCL_HREF.search(attrs)
            text = _text(inner)
            if not h or not text or _IOCL_FORMS.search(text):
                continue
            d = _IOCL_DAY.search(text)
            yield (d.group(1) if d else ""), h.group(1), f"{heading}: {text}"


def _iocl_kind(title: str) -> str:
    """The link's own words first (a corrigendum, a shortlist), then the card's heading."""
    own = kind_of(title.rsplit(": ", 1)[-1])
    return own if own != "notice" else kind_of(title)


# BECIL: <article> ... <h3 class="card-title title-text">Advt No. 563: ...</h3> ...
#   <h5 class="custom-field-title">Upload Date :</h5> <div class="custom-field-value">19-Oct-2026</div> ...
#   <a href="https://www.becil.com/uploads/topics/17924098715995.pdf" ...>Download Details ...</a> </article>
_BECIL_CARD = re.compile(r"<article>(.*?)</article>", re.S)
_BECIL = re.compile(r"""<h3 class="card-title title-text">(.*?)</h3>.*?Upload Date :</h5>\s*"""
                    r"""<div class="custom-field-value">\s*([^<]*?)\s*</div>.*?<a href="([^"]+)\"""", re.S)


def _becil_kind(title: str) -> str:
    """'Advt No. 563: Applications are invited ...' is a new advertisement."""
    kind = kind_of(title)
    if kind == "notice" and re.match(r"advt\.?\s*no\.?\s*\d+\s*:.*\bapplications are invited\b", title, re.I):
        return "recruitment"
    return kind


def _becil_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for card in _BECIL_CARD.findall(page):
        m = _BECIL.search(card)
        if m:
            yield m.group(2), m.group(3), m.group(1)
        else:
            yield "", "", card  # a card without its date or file: counted as malformed


IBPS = Board("ibps", "https://www.ibps.in", "https://www.ibps.in/index.php/crp-updates/", "IBPS", _ibps_entries, _ibps_date,
             kind=_ibps_kind)
AAI = Board("aai", "https://www.aai.aero", "https://www.aai.aero/en/careers/recruitment", "AAI", _aai_entries, _dmy,
            kind=_aai_kind, dated_ids=True)
IOCL = Board("iocl", "https://iocl.com", "https://iocl.com/latest-job-opening", "IOCL", _iocl_entries, _dmy,
             kind=_iocl_kind, undated=True)
BECIL = Board("becil", "https://www.becil.com", "https://www.becil.com/vacancies", "BECIL", _becil_entries, _day_mon_year,
              kind=_becil_kind)
ALL = (IBPS, AAI, IOCL, BECIL)
