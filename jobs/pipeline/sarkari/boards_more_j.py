"""State police and service boards and a central armed police force whose notices are one list (same Board model as listboards.py).

Each board below was written against its real page (tests/fixtures/<source>.html; see tests/test_boards_more_j.py):

  ossc   Odisha Staff Selection Commission         www.ossc.gov.in      home "What's New": day box, headline, advt line, "View Pdf" postback
  opsc   Odisha Public Service Commission          www.opsc.gov.in      the same site software: home "What's New", 10 latest notices
  tgprb  Telangana Police Recruitment Board        www.tgprb.in         its press-release list (ISO day, category, file), in the site's script
  cisf   Central Industrial Security Force (recruitment)   cisfrectt.cisf.gov.in   "NOTICE BOARD": headlines, no dates: dated the day first seen
  wbprb  West Bengal Police Recruitment Board      prb.wb.gov.in        home "Recruitment Notification": headlines, no dates: dated the day first seen

ossc, opsc and tgprb were read from here (30-10-2026); cisf and wbprb from the India server's captures of 30-10-2026.

The rules of listboards.py apply: a day, no time; a link that leaves the board's own host is not linked (the item links the board's
page, which states the news); a broken page raises FormatChanged. Odisha's two commissions open each file through an ASP.NET postback
(javascript:__doPostBack), and CISF's file links carry per-visit tokens: their items link the board's page.
"""
from __future__ import annotations

import datetime as dt
import html
import re
import urllib.parse
from dataclasses import dataclass
from typing import Callable, Iterator

from .listboards import _TAG, Board, _dmy, kind_of
from .ssc import FormatChanged

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _kinds(rules: list[tuple[str, re.Pattern]], fallback: str | None = None):
    def kind(title: str) -> str:
        return next((k for k, p in rules if p.search(title)), None) or fallback or kind_of(title)
    return kind


# OSSC and OPSC (one site software, Odisha): the home page's "What's New", newest first, the 10 latest notices:
#   <li><div class='listing_date newflash'><div class='datebox'><h2><span class='month'>29</span class='month'></h2>
#   <span class='Date'>Oct</span class='Date'><span> - </span><span class='year'>2026</span class='year'></div ...></div ...>
#   <div class='listing_content'><span class='content_title'><span>Notice regarding Fourth (Phase-IV) certificate verification of
#   Combined Graduate Level Recruitment Examination-2025.</span></span class='content_title'><span class='content_extra'>
#   <span class="newflash">Advt No.-5751/OSSC dated.12.12.2025.</span></span class='content_extra'></div class='listing_content'>
#   <div class='listing_action'><a ... href="javascript:__doPostBack(&#39;ctl00$generic_masterpage1$ctl16&#39;,&#39;&#39;)">View Pdf</a>
# The span named "month" holds the day, "Date" the month. Files open only through the postback: each item links the home page.
_OD_ITEM = re.compile(r"<li><div class='listing_date[^']*'>(.*?)</li>", re.S)
_OD_DAY = re.compile(r"<span class='month'>\s*(\d{1,2})\s*</span[^>]*>.*?<span class='Date'>\s*([A-Za-z]{3})\s*</span[^>]*>.*?"
                     r"<span class='year'>\s*(\d{4})\s*</span", re.S)
_OD_TITLE = re.compile(r"<span class='content_title'>(.*?)</span class='content_title'>", re.S)
_OD_EXTRA = re.compile(r"<span class='content_extra'>(.*?)</span class='content_extra'>", re.S)


def _odisha_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for body in _OD_ITEM.findall(page):
        day, title, extra = _OD_DAY.search(body), _OD_TITLE.search(body), _OD_EXTRA.search(body)
        headline = (_text(title.group(1)) if title else "") + (" " + _text(extra.group(1)) if extra else "")
        yield (" ".join(day.groups()) if day else ""), "", html.escape(headline.strip(), quote=False)


def _odisha_date(raw: str, _title: str = "") -> dt.date:
    """'29 Oct 2026'."""
    return dt.datetime.strptime(raw.strip(), "%d %b %Y").date()


# "Notice regarding Third (Phase-III) certificate verification ...", "Download the Admission letter for ...", "List of(75 nos)
# Candidates Provisionally shortlisted ...", "... (Advt. No. 27 of 2023-24) - Recommendation Notice", "... - Programme for Written
# Examination", "... - Rejection Notice", "Debarment Order of ..."
_ODISHA_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|postpone|reschedul|extension of|extended|cancell?ation|cancell?ed|revised)", re.I)),
    ("admit_card", re.compile(r"\b(admission (letter|certificate)s?|admit ?cards?|call letters?)\b", re.I)),
    ("answer_key", re.compile(r"\b(answer ?keys?|model answers?)\b", re.I)),
    ("result", re.compile(r"(\bresults?\b|\bshortlisted\b|\brecommendation notice\b|\bselect(ed|ion) list\b|\bmerit list\b|"
                          r"\bprovisionally (selected|qualified)\b)", re.I)),
    ("schedule", re.compile(r"(\bprogramme for\b|\bschedule\b|\bdate for\b|\bto be held on\b|\bcertificate verification\b|"
                            r"\binterview\b)", re.I)),
    ("recruitment", re.compile(r"(\badvertisement for\b|\brecruitment (of|to)\b|\binviting (online )?applications\b)", re.I)),
]
_odisha_kind = _kinds(_ODISHA_KINDS, "notice")


# TGPRB: www.tgprb.in is a script-drawn page (React); its press releases are a list inside the site's script, which the home page
# names (<script type="module" crossorigin src="/assets/index-C1RGemmW.js">; the name changes with each release of the site):
#   var qn={police:`Police Recruitment`,driver:`Driver & Shramik`,supervisor:`Supervisor Trainees`,fsl:`FSL`,app:`APP`}, ...
#   Yn=[{href:`https://www.tgprb.in/APP_PDF/Press Note for APP-25 Selection dated 17 October 2026.pdf`,category:`app`,
#   kind:`Press Note`,date:`2026-10-17`}, ... {href:`https://www.tgprb.in/SI_PC_2026/Press Note dated 17-09-2026.pdf`,
#   category:`police`,kind:`Press Release`,date:`2026-09-17`,isNew:!0}, ...]
# Its "Press Releases" page shows each as "<category> - <kind> dated <day>"; the headline here is the category and the file's name.
TGPRB_PAGE = "https://www.tgprb.in/"
_TG_BUNDLE = re.compile(r'<script\s+type="module"\s+crossorigin\s+src="(/assets/index-[A-Za-z0-9_-]+\.js)"')
_TG_LABELS = re.compile(r"\{(police:`[^`]*`(?:,\w+:`[^`]*`)*)\}")
_TG_LABEL = re.compile(r"(\w+):`([^`]*)`")
_TG_ITEM = re.compile(r"\{href:`([^`]+)`,category:`(\w+)`,kind:`([^`]+)`,date:`([^`]*)`(?:,isNew:!0)?\}")


def tgprb_script(home: str) -> str:
    """The script the home page loads, which holds the list."""
    m = _TG_BUNDLE.search(home)
    if not m:
        raise FormatChanged("tgprb: the home page names no script")
    return urllib.parse.urljoin(TGPRB_PAGE, m.group(1))


def _tgprb_entries(script: str) -> Iterator[tuple[str, str, str]]:
    labels = _TG_LABELS.search(script)
    names = dict(_TG_LABEL.findall(labels.group(1))) if labels else {}
    for href, category, _kind, day in _TG_ITEM.findall(script):
        stem = urllib.parse.unquote(href.rsplit("/", 1)[-1])
        stem = stem[:-4] if stem.lower().endswith(".pdf") else stem
        yield day, href, html.escape(f"{names.get(category, category)}: {stem}", quote=False)


def _iso(raw: str, _title: str = "") -> dt.date:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", raw.strip()):
        raise ValueError(raw)
    return dt.date.fromisoformat(raw.strip())


# "APP: Press Note for APP-25 Result dtd 12-10-2026", "Police Recruitment: Application Dates 2026 Press Note dated 29-07-2026",
# "Police Recruitment: Press Note Second Supplementary Notification 2026 dated 10 Sep 2026", "Driver & Shramik: Selection Press Note ..."
_TGPRB_KINDS = [
    ("amendment", re.compile(r"\b(supplementary|corrigendum|addendum|postpone|reschedul)", re.I)),
    ("result", re.compile(r"\b(results?|selection|selected|cut-?offs?)\b", re.I)),
    ("schedule", re.compile(r"(\bdates?\b|\bschedule\b)", re.I)),  # "dated" is not "date"
    ("recruitment", re.compile(r"\bnotification\b", re.I)),
]
_tgprb_kind = _kinds(_TGPRB_KINDS, "notice")


@dataclass(frozen=True)
class _ScriptBoard(Board):
    """A board whose list is in the script its home page loads: the home page is read for the script's (changing) name."""
    script: Callable[[str], str] = tgprb_script

    def fetch(self, get_text: Callable[[str], str], now: dt.datetime, window_days: int,
              warn: Callable[[str], None] = print, seen: dict[str, str] | None = None,
              record: dict[str, str] | None = None):
        page = get_text(self.script(get_text(self.NOTICE_PAGE)))
        return self.parse(page, now.date(), (now - dt.timedelta(days=window_days)).date(), warn, seen, record)


# CISF recruitment: the home page's "NOTICE BOARD" (a marquee), English and Hindi files listed apart, no dates:
#   <a href="file_open.php?fnm=VfC3JI4MmwUYpdAh808k..." target="_blank"><font color="red">Conduct of CBE for the Posts of CT (Fire),
#   CT (TM) and CT (Driver & DCPO) -2024 (English)</font> <img src="blinking_new.gif" /></a>
# The fnm tokens differ on every visit (the India server's captures of 29 and 30-10-2026 differ only in them): each item links the
# board's page, is known by its headline and dated the day it is first seen.
CISF_PAGE = "https://cisfrectt.cisf.gov.in/"
_CISF_BOARD = re.compile(r'<marquee\b[^>]*class="marquee-scroll"(.*?)</marquee>', re.S)
_CISF_ITEM = re.compile(r'<a\s+href="file_open\.php\?fnm=[^"]*"[^>]*>\s*<font\b[^>]*>(.*?)</font>', re.S)


def _cisf_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for board in _CISF_BOARD.findall(page):
        for title in _CISF_ITEM.findall(board):
            yield "", "", title


_CISF_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|postpone|reschedul|extension|extended|revised)", re.I)),
    ("admit_card", re.compile(r"\b(admit ?cards?|call letters?)\b", re.I)),
    ("result", re.compile(r"\b(results?|merit list|selected|shortlisted|qualified)\b", re.I)),
    ("schedule", re.compile(r"(^conduct of\b|\bschedule\b)", re.I)),
    ("recruitment", re.compile(r"\bnotification for recruitment\b", re.I)),
]
_cisf_kind = _kinds(_CISF_KINDS, "notice")


# WB Police Recruitment Board: the home page's "Recruitment Notification" box (a marquee), no dates; each links its recruitment's page:
#   <a style="color:#fff !important;" href="https://prb.wb.gov.in/recruitments/6e50c07d61e0194f" > <p style="color:#fff !important;">
#   Download Admit Card for PMT &amp; PET for Recruitment to the posts of Constables/ Lady Constables in Kolkata Police - 2024
#   <img class="newImage" src="https://prb.wb.gov.in/assets/newNews.gif"> <a onclick="speak(`...`)"> ...</p>
# Two notices can link the same recruitment page: an item is known by its link and headline, dated the day it is first seen.
WBPRB_PAGE = "https://prb.wb.gov.in/"
_WB_BOARD = re.compile(r"&nbsp;Recruitment Notification:.*?<marquee\b[^>]*>(.*?)</marquee>", re.S)
_WB_ITEM = re.compile(r'<a\s+style="[^"]*"\s+href="([^"]+)"\s*>\s*<p\b[^>]*>(.*?)(?=<img\b|<a\b|</p>)', re.S)


def _wbprb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for board in _WB_BOARD.findall(page):
        for href, title in _WB_ITEM.findall(board):
            yield "", href, title


_WBPRB_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|postpone|reschedul|extension|extended|revised|special window)", re.I)),
    ("admit_card", re.compile(r"\b(admit ?cards?|call letters?)\b", re.I)),
    ("answer_key", re.compile(r"\banswer ?keys?\b", re.I)),
    ("result", re.compile(r"\b(results?|merit list|selected|shortlisted|qualified)\b", re.I)),
    ("schedule", re.compile(r"(\b(tentative )?date of\b|\bschedule\b)", re.I)),
    ("recruitment", re.compile(r"(^recruitment (of|to)\b|\bnotification for recruitment\b|\bapply online\b)", re.I)),
]
_wbprb_kind = _kinds(_WBPRB_KINDS, "notice")


OSSC = Board("ossc", "https://www.ossc.gov.in", "https://www.ossc.gov.in/Public/OSSC/Default.aspx", "OSSC", _odisha_entries,
             _odisha_date, kind=_odisha_kind)
OPSC = Board("opsc", "https://www.opsc.gov.in", "https://www.opsc.gov.in/Public/OPSC/Default.aspx", "OPSC", _odisha_entries,
             _odisha_date, kind=_odisha_kind)
TGPRB = _ScriptBoard("tgprb", "https://www.tgprb.in", TGPRB_PAGE, "TGPRB", _tgprb_entries, _iso, kind=_tgprb_kind)
CISF = Board("cisf", "https://cisfrectt.cisf.gov.in", CISF_PAGE, "CISF", _cisf_entries, _dmy, min_items=3, kind=_cisf_kind,
             undated=True, title_ids=True)
WBPRB = Board("wbprb", "https://prb.wb.gov.in", WBPRB_PAGE, "WBPRB", _wbprb_entries, _dmy, min_items=2, kind=_wbprb_kind,
              undated=True)
ALL = (OSSC, OPSC, TGPRB, CISF, WBPRB)
