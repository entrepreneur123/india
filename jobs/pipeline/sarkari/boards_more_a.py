"""More recruiters whose notice board is one server-rendered list (read from the India server).

Each board below was written against its page of 29-10-2026 (tests/fixtures/<source>.html):

  uppsc  Uttar Pradesh Public Service Commission        uppsc.up.nic.in            home page "What's New" marquee: <li> N. <a title=
                                                                                   'FULL HEADLINE' href=Open_PDF_DB.aspx?...>; no posting
                                                                                   date (only "Visible upto", an expiry), so undated
  rbi    Reserve Bank of India (recruitment)            opportunities.rbi.org.in   Vacancies and Results pages: a table, a date row
                                                                                   "Oct 26, 2026", then that day's link rows

Not collectable from what was saved on 29-10-2026:
  nabard  www.nabard.org was saved as its Hindi home page: news and publications, no career notices
          (those sit on ftrcontent.aspx?id=660, not saved).
  epfo    www.epfindia.gov.in's home page has no recruitment notices, only a link to www.epfo.gov.in/recruitments/
          (not saved).

The rules of listboards.py apply: a day, no time; links leaving the board's own host are left out;
a broken page raises FormatChanged.
"""
from __future__ import annotations

import datetime as dt
import html
import re
from dataclasses import dataclass, field
from typing import Callable, Iterator

from .listboards import _TAG, Board, kind_of

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


# UPPSC home page. "What's New" (the full list; the Results / Press Release / Forthcoming tabs repeat
# its files under other encrypted links, so they are not read):
#   <div id="ctl00_MainContent_Div_Whats_New" ...><marquee ...><li> 1. <a title='LIST OF ... MATH'
#   href=Open_PDF_DB.aspx?I4PnQ0tBagnfpuRH59X6AZU6OQK2HlGI  target=_Blank>LIST OF ... TEACH..</a>
#   <span ...> Visible upto : 28/11/2026</span> ...</li> ... </marquee>
# The link text is cut ("TEACH.."); the title attribute holds the whole headline. The only date is the
# day the notice leaves the page, so entries are dated the day they are first seen.
# "Recruitment Dashboard" marquee: <b>1.</b><span ...> Live Advertisement :- <a ... title='CLICK HERE TO
#   APPLY FOR ADVT.NO. A-4/E-1/2026,...' href=CandidatePages/Notifications.aspx ...>: an open
# advertisement (its other rows, exam district slips and document verification, repeat What's New notices).
_UPPSC_NEW = re.compile(r"""<div id="ctl00_MainContent_Div_Whats_New"[^>]*>(.*?)</marquee>""", re.S)
_UPPSC_ITEM = re.compile(r"""<li>\s*\d+\.\s*<a\s+title='([^']*)'\s+href=([^\s>]+)""", re.S)
_UPPSC_DASH = re.compile(r"""<div id="ctl00_MainContent_RecruitmentDash"[^>]*>(.*?)</marquee>""", re.S)
_UPPSC_LIVE = re.compile(r"""Live Advertisement\s*:-\s*<a\b[^>]*?\btitle='([^']*)'\s+href=([^\s>]+)""", re.S)
UPPSC_LIVE = "Live advertisement: "


def _uppsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    new = _UPPSC_NEW.search(page)
    for title, href in _UPPSC_ITEM.findall(new.group(1) if new else ""):
        yield "", href, title
    dash = _UPPSC_DASH.search(page)
    for title, href in _UPPSC_LIVE.findall(dash.group(1) if dash else ""):
        yield "", href, UPPSC_LIVE + re.sub(r"^\s*click here to\s+", "", title, flags=re.I)


def _uppsc_kind(title: str) -> str:
    if title.lower().startswith(UPPSC_LIVE.lower()):
        return "recruitment"
    if re.search(r"\bdocument verification\b", title, re.I):
        return "notice"
    return kind_of(title)


def _no_date(raw: str, _title: str) -> dt.date:
    raise ValueError(raw)  # UPPSC shows no posting date: undated, dated the day first seen


# RBI (opportunities.rbi.org.in, Vacancies.aspx and resultsnew.aspx):
#   <table ... class="tablebg"> <tr><td class="tableheader" align="left"><b>Oct 26, 2026</b></td></tr>
#   <tr><td class="link2"><a href="bs_viewcontent.aspx?Id=5194" class="link2">Extension of last date ...</a></td></tr>
#   (one date row, then one or more link rows). One results row nests a second link inside the first:
#   <td><a href="bs_viewcontent.aspx?Id=5197" class="link2"><a href="https://www.rbi.org.in/..." class="link2"> Scorecard ...</a></a>
#   A browser shows only the inner link, so the last link of a row is the one read (here off-host: left out).
_RBI_TABLE = re.compile(r"""<table\b[^>]*class="tablebg"[^>]*>(.*?)</table>""", re.S)
_RBI_CELL = re.compile(r"""<tr>\s*<td\b([^>]*)>(.*?)</td>\s*</tr>""", re.S)
_RBI_DAY = re.compile(r"""\s*<b>\s*([A-Z][a-z]{2} \d{1,2}, \d{4})\s*</b>\s*""")
_RBI_HREF = re.compile(r"""<a\s+href="([^"]*)\"""")


def _rbi_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for table in _RBI_TABLE.findall(page):
        day = ""
        for attrs, cell in _RBI_CELL.findall(table):
            if 'class="tableheader"' in attrs:
                m = _RBI_DAY.fullmatch(cell)
                day = m.group(1) if m else "?"  # a broken date row: its links are counted as malformed
                continue
            hrefs = _RBI_HREF.findall(cell)
            yield day, hrefs[-1] if hrefs else "", cell


def _rbi_date(raw: str, _title: str = "") -> dt.date:
    """'Oct 26, 2026'."""
    if not re.fullmatch(r"[A-Z][a-z]{2} \d{1,2}, \d{4}", raw):
        raise ValueError(raw)
    return dt.datetime.strptime(raw, "%b %d, %Y").date()


_RBI_KINDS = [("amendment", re.compile(r"\bextension of last date|\bamendment|\bcorrigendum", re.I)),
              ("result", re.compile(r"\bresult|\bdisplay of roll numbers?|\bscorecard|\bmarksheet|\bselected", re.I)),
              ("admit_card", re.compile(r"\bcall letters?\b|\badmit card", re.I)),
              ("recruitment", re.compile(r"^(application for |advertisement for |inviting applications)?engagement of|"
                                         r"\binviting applications\b|^advertisement\b|^lateral recruitment\b", re.I))]


def _rbi_kind(title: str) -> str:
    return next((kind for kind, pattern in _RBI_KINDS if pattern.search(title)), None) or kind_of(title)


@dataclass(frozen=True)
class PagesBoard(Board):
    """A board whose list is split over pages of one site (RBI: vacancies, results): read as one page."""
    MORE_PAGES: tuple[str, ...] = field(default=())

    def fetch(self, get_text: Callable[[str], str], now: dt.datetime, window_days: int,
              warn: Callable[[str], None] = print, seen: dict[str, str] | None = None,
              record: dict[str, str] | None = None):
        page = "\n".join(get_text(u) for u in (self.NOTICE_PAGE, *self.MORE_PAGES))
        return self.parse(page, now.date(), (now - dt.timedelta(days=window_days)).date(), warn, seen, record)


UPPSC = Board("uppsc", "https://uppsc.up.nic.in", "https://uppsc.up.nic.in/", "UPPSC", _uppsc_entries, _no_date,
              kind=_uppsc_kind, undated=True, title_ids=True)  # its links are encrypted strings: maybe new each visit
RBI = PagesBoard("rbi", "https://opportunities.rbi.org.in", "https://opportunities.rbi.org.in/Scripts/Vacancies.aspx", "RBI",
                 _rbi_entries, _rbi_date, kind=_rbi_kind,
                 MORE_PAGES=("https://opportunities.rbi.org.in/Scripts/resultsnew.aspx",))
ALL = (UPPSC, RBI)
