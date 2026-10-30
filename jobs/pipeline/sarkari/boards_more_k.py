"""A state commission, a teachers' commission and two police boards whose notices are one list (same Board model as listboards.py).

Each board below was written against the India server's capture of its page of 30-10-2026 (tests/fixtures/<source>.html; see
tests/test_boards_more_k.py):

  wbpsc    West Bengal Public Service Commission           psc.wb.gov.in     home "What's New": headlines; the day is in each file's name
  upessc   UP Education Service Selection Commission       upessc.up.gov.in  home "PRESS RELEASE": the latest notices, each file with its day
  apslprb  AP State Level Police Recruitment Board         slprb.ap.gov.in   home "Latest Jobs" and "Latest News": the day only in some headlines
  gprb     Gujarat Police Recruitment Board                gprb.gujarat.gov.in  home "Latest Update": day and month, no year

The rules of listboards.py apply: a day, no time; a link that leaves the board's own host is left out; a broken page raises
FormatChanged. A headline without a day of its own is dated the day it is first seen (undated boards), and never published from the
page as it was when the record began.
"""
from __future__ import annotations

import dataclasses
import datetime as dt
import html
import re
from typing import Iterable, Iterator

from .listboards import _TAG, Board, _dmy, kind_of

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _kinds(rules: list[tuple[str, re.Pattern]], fallback: str | None = None):
    def kind(title: str) -> str:
        return next((k for k, p in rules if p.search(title)), None) or fallback or kind_of(title)
    return kind


# WBPSC (new host psc.wb.gov.in; wbpsc.gov.in no longer answers): the home page's "What's New" box, oldest first, a year long:
#   <a href="Download?param1=20251031172549_ScheduleAsstEnggELec.pdf&param2=advertisement" target="_blank" style="..."><i class="fa
#   fa-angle-double-right" aria-hidden="true"></i> &nbsp;<span class="text-uppercase">SCHEDULE OF INTERVIEW FOR RECRUITMENT TO THE POST
#   OF ASSISTANT ENGINEER (ELECTRICAL) ... ADVT. NO. 21/2020&nbsp;</span><sup><img alt="new" src="image/newSymbol.gif"></sup></a>
# The box shows no dates. Each file's name starts with its upload time, YYYYMMDDhhmm[ss], after an optional An_ / Ad_ / Cur_
# (announcement, advertisement, current advertisement): that day dates the item. A few names carry none (CLERKSHIP2024.pdf): those
# are dated the day first seen. Half-yearly departmental examinations of serving officers and the office's purchase notices sit in
# the same box and are left out: they are not recruitment news.
_WB_BOX = re.compile(r"&nbsp;What's New :(.*?)<a href=\"whats_new\.jsp\"", re.S)
_WB_ITEM = re.compile(r'<a\s+href="(Download\?param1=[^"]+)"\s+target="_blank"\s+style="[^"]*">\s*<i\b[^>]*></i>\s*(?:&nbsp;)?\s*'
                      r'<span\s+class="text-uppercase">(.*?)</span>', re.S)
_WB_STAMP = re.compile(r"^Download\?param1=(?:(?:An|Ad|Cur)_)?(20\d{2})(\d{2})(\d{2})\d{4}(?:\d{2})?_")
_WB_SKIP = re.compile(r"(HALF[- ]?YEARLY\b|\bDEPARTMENTAL EXAM|\bPROFICIENCY TEST\b|\bPROFESSIONAL EXAMINATION\b|\bQUOTATION\b|"
                      r"\bTENDER\b)", re.I)  # "RESULTS OF THEHALF-YEARLY ...": no word boundary before HALF


def _wbpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for box in _WB_BOX.findall(page):
        for href, headline in _WB_ITEM.findall(box):
            if _WB_SKIP.search(_text(headline)):
                continue
            m = _WB_STAMP.search(href)
            # the page writes "&param2" bare, which html.unescape would read as "¶m2": escape it as the page meant it
            yield (f"{m.group(3)}-{m.group(2)}-{m.group(1)}" if m else ""), html.escape(href, quote=False), headline


# "SCHEDULE OF INTERVIEW FOR ...", "LIST OF CANDIDATES RECOMMENDED FOR APPOINTMENT ...", "RECOMMENDATION FOR APPOINTMENT ...",
# "FINAL ANSWER KEYS ...", "INDICATIVE ADVERTISEMENT FOR ...", "DETAILED ADVERTISEMENT TO THE POST OF ...", "CORRIGENDUM NOTICE ...",
# "IMPORTANT ANNOUNCEMENT REGARDING EXTENSION OF CLOSING DATE ...", "ROLL NUMBERS OF CANDIDATES WHO HAVE BEEN QUALIFIED ..."
_WBPSC_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|postpone|reschedul|extension|extention|extended|edit (window|option))", re.I)),
    ("answer_key", re.compile(r"\banswer ?keys?\b", re.I)),
    ("admit_card", re.compile(r"\badmit[- ]?cards?\b", re.I)),
    ("result", re.compile(r"(\bresults?\b|\brecommend(ed|ation)\b|\bqualified\b|\bmerit (list|positions)\b|\bmarks of\b|"
                          r"\bunsuitable candidates\b|\bsuccessful candidates\b|\bcut of(f)? marks\b)", re.I)),
    ("schedule", re.compile(r"(\bschedule of\b|\bexamination schedule\b|\bprogramme of\b|\bvenue list\b)", re.I)),
    ("recruitment", re.compile(r"(\b(indicative|detailed) advertisement\b|\badvertisement for recruitment\b)", re.I)),
]
_wbpsc_kind = _kinds(_WBPSC_KINDS, "notice")


# UPESSC: the home page's "PRESS RELEASE" card, the latest notices (the rest of its lists are drawn by script):
#   <div style="font-size: 14px; font-weight: 600; color: #222; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
#   वि० सं० 02/2026 UPTET (शिक्षक पात्रता परीक्षा) के घोषित परिणाम के संबंध में ... </div> <div style="font-size: 12px; ...">
#   <img ...> ALL DEPARTMENT | Press Vigyapti: <a href="/Notice/945b-376d-4700-9db7-8225.pdf" target="_blank"><b>विज्ञप्ति</b><br>2026-09-16</a>,
#   | News </div>
# A notice can have several files (a detailed advertisement and a press note): each file is an item, its headline the notice's
# with the file's label.
UPESSC_PAGE = "https://upessc.up.gov.in/"
_UP_CARD = re.compile(r'<div style="font-size: 14px; font-weight: 600; color: #222;\s*white-space: nowrap; overflow: hidden; '
                      r'text-overflow: ellipsis;">(.*?)</div>\s*<div style="font-size: 12px;[^"]*">(.*?)</div>', re.S)
_UP_FILE = re.compile(r'<a href="(/Notice/[^"]+)" target="_blank"><b>(.*?)</b><br>\s*(\d{4}-\d{2}-\d{2})\s*</a>', re.S)


def _upessc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for title, files in _UP_CARD.findall(page):
        for href, label, day in _UP_FILE.findall(files):
            yield day, href, html.escape(f"{_text(title)} ({_text(label)})", quote=False)


def _iso(raw: str, _title: str = "") -> dt.date:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", raw.strip()):
        raise ValueError(raw)
    return dt.date.fromisoformat(raw.strip())


# "विज्ञापन संख्या- 05/2026 सहायक अध्यापक ... आवेदन फॉर्म की (विस्तृत विज्ञापन)", "... (विज्ञप्ति)"
_UPESSC_KINDS = [
    ("recruitment", re.compile(r"\(विस्तृत विज्ञापन\)$")),
    ("amendment", re.compile(r"(संशोधन|शुद्धि[- ]?पत्र|स्थगित|corrigendum|postpone)", re.I)),
    ("admit_card", re.compile(r"(प्रवेश[- ]?पत्र|admit card)", re.I)),
    ("answer_key", re.compile(r"(उत्तर[- ]?कुंजी|answer key)", re.I)),
]
_upessc_kind = _kinds(_UPESSC_KINDS, "notice")


# AP SLPRB: the home page's two lists, hand-written (some links are never closed):
#   <a href="../UI/Recruitments.aspx" class="list-group-item">SCT PC (MECHANICS) (MEN), SCT PC (DRIVERS) (MEN) <img ...> </a>
#   <a href="https://slprb.ap.gov.in/2026_PDFS/PressNote_dated_17092026.pdf" class="list-group-item" target="_blank">
#   Press Note dtd 17.09.2026 regarding Recruitment for Driver Operator in Fire Services <img ...> </a>
# A headline that says its own day ("Press Note dtd 17.09.2026", "AMENDMENT DT. 25-08-2025", "Press Release dt: 20.12.2023",
# "Amendment dated 19-12-2023 to the Notification ..., dated: 28-11-2022": the first) is dated by it; the others (the "Latest Jobs"
# posts, keys, selection lists) the day they are first seen. A day that only names an event ("for Attending the PMT/PET on
# 25-07-2025") is not taken.
APSLPRB_PAGE = "https://slprb.ap.gov.in/"
_AP_ITEM = re.compile(r'<a href="([^"]*)" class="list-group-item"[^>]*>(.*?)(?=<a\b|</a>|</div>)', re.S | re.I)
_AP_DATE = re.compile(r"\b(?:dtd|dated|dt)\b\.?\s*[-:]?\s*(\d{1,2}[-.]\d{1,2}[-.]\d{4})", re.I)


def _apslprb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, headline in _AP_ITEM.findall(page):
        m = _AP_DATE.search(_text(headline))
        yield (m.group(1) if m else ""), href, headline


_APSLPRB_KINDS = [
    ("amendment", re.compile(r"(\bamendment\b|\bextend|\bextension\b|\bcorrigendum\b|\bdata correction\b)", re.I)),
    ("answer_key", re.compile(r"\bkey\b", re.I)),
    ("admit_card", re.compile(r"(\bhall tickets?\b|\bcall letters?\b)", re.I)),
    ("result", re.compile(r"(\bresults?\b|\bselection list\b|\bzone wise selection\b|\bcut ?offs?\b|\bcutt offs\b|\bcut off marks\b)", re.I)),
    ("schedule", re.compile(r"(\bschedule\b|\binterview\b|\bPMT\s*/\s*PET\b)", re.I)),
    ("recruitment", re.compile(r"(\bnotification\b|\brecruitment for\b|^SCT\b|^FSL Scientific Assistants\b|^Driver Operator in\b|"
                               r"^ASSISTANT PUBLIC PROSECUTORS IN\b)", re.I)),
]
_apslprb_kind = _kinds(_APSLPRB_KINDS, "notice")


# GPRB: the home page's "Latest Update" list, newest first, a day and a month but no year:
#   <a id="ContentPlaceHolder1_LatestAdvertisement1_rptLatestUpdate_lnkPdfFile_0" class="pdfIcon" href="ViewDocument.aspx?CDID=7fa8..."
#   target="_blank"> <div class="newxListBox" > <div class="newsData"> <div class="newsDate"> 22</div> <div class="newsMonth"> Oct</div>
#   </div> <div class="newsInfo"> <div class="newsTitle"> Police Technical Cadre (Wireless and Motor Transport)ની દસ્તાવેજ ચકાસણી ...
#   <span class="eventName"> ...
# The year is read from the order: the first entry is in the year that puts it on or before today; going down the list, a later
# month-and-day than the entry above starts the year before. Such a turn is believed only when it means a gap of under half a year
# (31 Dec after 09 Jan); after one that doesn't (21 Aug after 06 Aug: the list is not in order there), the rest are left undated:
# dated the day first seen, so never published from today's page.
_GP_ITEM = re.compile(r'<a id="ContentPlaceHolder1_LatestAdvertisement1_rptLatestUpdate_lnkPdfFile_\d+" class="pdfIcon" href="([^"]+)"[^>]*>'
                      r'\s*<div class="newxListBox"\s*>\s*<div class="newsData">\s*<div class="newsDate">\s*(\d{1,2})\s*</div>\s*'
                      r'<div class="newsMonth">\s*([A-Za-z]{3})\s*</div>.*?<div class="newsTitle">(.*?)<span class="eventName">', re.S)
_MAX_TURN = dt.timedelta(days=183)


def _gprb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    """(day and month as the page writes them, link, headline): the year is added by _with_years."""
    for href, day, month, headline in _GP_ITEM.findall(page):
        yield f"{int(day):02d} {month}", href, headline


def _with_years(entries: Iterable[tuple[str, str, str]], today: dt.date) -> Iterator[tuple[str, str, str]]:
    above: dt.date | None = None
    lost = False
    for raw, href, headline in entries:
        if lost:
            yield "", href, headline
            continue
        try:
            day = dt.datetime.strptime(f"{raw} {today.year}", "%d %b %Y").date()
        except ValueError:  # 29 Feb, or not a day: parse_entry reports it
            yield raw, href, headline
            continue
        if above is None:
            if day > today + dt.timedelta(days=1):
                day = day.replace(year=day.year - 1)
        else:
            day = day.replace(year=above.year)
            if day > above:
                day = day.replace(year=day.year - 1)
                if above - day > _MAX_TURN:
                    lost = True
                    yield "", href, headline
                    continue
        above = day
        yield day.strftime("%d-%m-%Y"), href, headline


# Gujarati headlines: "... ની Final Answer Key ...", "... Qualified થયેલ ઉમેદવારોની યાદી ..." (list of candidates), "... ગુણ ..." (marks),
# "... પરીણામ ..." (result), "... અભ્યાસક્રમ ..." (syllabus), "... ગેરરીતિ / ગેરલાયક ..." (malpractice / disqualified), "... ચેકલીસ્ટ ..."
# (checklist), "... ભરવા અંગેની જાહેરાત ..." (advertisement to fill posts), "... જાહેરાતની વિગતવારની સૂચનાઓ ..." (the advertisement's
# detailed instructions).
_GPRB_KINDS = [
    ("answer_key", re.compile(r"\banswer ?key", re.I)),  # "Final Answer Keyની": no word boundary before Gujarati
    ("notice", re.compile(r"(ગેરલાયક|ગેરરીતિ|અરજીઓને|ચેકલીસ્ટ|અભ્યાસક્રમ|\bsyllabus\b|\bFAQ\b|ટુંક સમયમાં)", re.I)),
    ("result", re.compile(r"(પરીણામ|પરિણામ|\bresults?\b|ગુણ|યાદી|\brechecking\b|ચકાસણી બાબત)", re.I)),
    ("recruitment", re.compile(r"(ભરવા (માટેની|અંગેની) જાહેરાત|જાહેરાતની વિગતવાર|વિગતવારની તમામ સુચનાઓ|સીધી ભરતી માટેની જાહેરાત)")),
]
_gprb_kind = _kinds(_GPRB_KINDS, "notice")


@dataclasses.dataclass(frozen=True)
class _YearlessBoard(Board):
    """A board whose list gives a day and a month: the years are read from the list's order and today."""

    def parse(self, page, today, cutoff, warn=print, seen=None, record=None):
        entries = list(_with_years(self.entries(page), today))
        plain = Board(**{f.name: getattr(self, f.name) for f in dataclasses.fields(Board)})
        return dataclasses.replace(plain, entries=lambda _page: iter(entries)).parse(page, today, cutoff, warn, seen, record)


WBPSC = Board("wbpsc", "https://psc.wb.gov.in", "https://psc.wb.gov.in/", "WBPSC", _wbpsc_entries, _dmy, kind=_wbpsc_kind,
              undated=True)
UPESSC = Board("upessc", "https://upessc.up.gov.in", UPESSC_PAGE, "UPESSC", _upessc_entries, _iso, min_items=1, kind=_upessc_kind)
APSLPRB = Board("apslprb", "https://slprb.ap.gov.in", APSLPRB_PAGE, "APSLPRB", _apslprb_entries, _dmy, kind=_apslprb_kind,
                undated=True)
GPRB = _YearlessBoard("gprb", "https://gprb.gujarat.gov.in", "https://gprb.gujarat.gov.in/", "GPRB", _gprb_entries, _dmy,
                      kind=_gprb_kind, undated=True)
# GPRB is written but not run: its home page gives day and month only, so its years would be inferred from the
# list's order. It waits for its "View All" list (captured as gprb-latest), which may state the year.
ALL = (WBPSC, UPESSC, APSLPRB)
