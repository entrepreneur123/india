"""Recruiters whose notice board is one server-rendered list of (date, link, headline).

Each board below was written against its live page (21-10-2026; tests/fixtures/<source>.html):

  csbc   Central Selection Board of Constable, Bihar     csbc.bihar.gov.in   table rows: date | link
  bpssc  Bihar Police Sub-ordinate Service Commission    bpssc.bihar.gov.in  table rows: date | link
  isro   Indian Space Research Organisation (careers)    www.isro.gov.in     "Advt. No. X dated DD.MM.YYYY - ..."
  ongc   Oil and Natural Gas Corporation (recruitment)   ongcindia.com       list items: link, then "16 Oct, 2026"

Read from the India server only (29-10-2026; tests/fixtures/<source>.html from its captures branch):

  mppsc  Madhya Pradesh Public Service Commission        mppsc.mp.gov.in     home page links, "..._Dated_28_10_2026.pdf"
  ctet   Central Teacher Eligibility Test (CBSE)         ctet.nic.in         public notices, files on the NIC CDN
  rrb    Railway Recruitment Boards (unified site)       rrbchennai.gov.in   "(CEN 08/2025) Exam Results (08-10-2026)"

Rules shared with upprpb.py: the board gives a day, no time (dateOnly); links that leave the
board's own site (exam agencies' portals such as apply-csbc.com) are left out, because such
domains can't be told apart from look-alikes; a page with too few or too many broken entries
raises FormatChanged instead of publishing a guess.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import html
import re
import urllib.parse
from dataclasses import dataclass
from typing import Callable, Iterator

from . import classify
from .ssc import IST, FormatChanged, Item, RecordError

_TAG = re.compile(r"<[^>]+>")
_FIRST_VALID = dt.date(2015, 1, 1)
MAX_REJECT_SHARE = 0.2

# Headline words SSC doesn't use (classify knows SSC's). First match wins, so a postponed
# admit card is a change and "Last date ... extended" is not a new recruitment.
_RULES: list[tuple[str, re.Pattern]] = [
    ("amendment", re.compile(r"\b(postpone|reschedul|change in (the )?(date|exam)|new date|revised|extended|extension|cancell?ation|corrigendum|addendum)", re.I)),
    ("answer_key", re.compile(r"\banswer[- ]?keys?\b", re.I)),
    ("admit_card", re.compile(r"\b(e-?admit[- ]?cards?|admit[- ]?cards?|call letters?)\b", re.I)),
    ("result", re.compile(r"(^results?\b|\bresults? of\b|\bfinal selection|\bselection list|\bmerit list|\bshortlisted|\bscreened[- ]in|\bselected candidates|\blist of (successful|provisionally|qualified))", re.I)),
    ("recruitment", re.compile(r"(^advt\.?\s*no\.?[^:]*:\s*for selection|\brecruitment (of|to)\b|^recruitment advertisement\b|\binviting (online )?applications|^notification for|^advertisement for|^engagement of|^hiring of|walk[- ]*in|call for applications)", re.I)),
    ("schedule", re.compile(r"\b(schedule of|date of (conduct|conducting))", re.I)),
]


def kind_of(title: str) -> str:
    for kind, pattern in _RULES:
        if pattern.search(title):
            return kind
    return classify.kind_of(title)


@dataclass(frozen=True)
class Board:
    SOURCE: str
    BASE: str  # scheme + host; only links on this host are kept
    NOTICE_PAGE: str
    family: str
    entries: Callable[[str], Iterator[tuple[str, str, str]]]  # page -> (raw date, href, headline html)
    parse_date: Callable[[str, str], dt.date]  # (raw date, headline text) -> day
    min_items: int = 5
    kind: Callable[[str], str] | None = None  # headline -> kind, when the board's own words say it better than kind_of
    dated_ids: bool = False  # the same headline comes back on new days (RRB): each day is a new update

    def url(self, href: str) -> str:
        href = html.unescape(href.strip())
        url = urllib.parse.urljoin(self.NOTICE_PAGE, href)
        p = urllib.parse.urlsplit(url)
        if p.scheme != "https" or f"https://{p.netloc}" != self.BASE:
            raise _Outside(url)
        return urllib.parse.urlunsplit((p.scheme, p.netloc, urllib.parse.quote(urllib.parse.unquote(p.path), safe="/"), p.query, ""))

    def parse_entry(self, raw_date: str, href: str, headline: str, today: dt.date) -> Item | None:
        try:
            url = self.url(href)
        except _Outside:
            return None  # an outside portal (or, below, an archived entry): counted, not shown
        title = classify.normalize(html.unescape(_TAG.sub(" ", headline)))
        if not title or len(title) > 1000:
            raise RecordError(f"{url}: headline empty or too long")
        try:
            day = self.parse_date(raw_date, title)
        except ValueError:
            raise RecordError(f"{url}: bad date {raw_date!r}") from None
        if day > today + dt.timedelta(days=1):
            raise RecordError(f"{url}: date {day} is in the future")
        if day < _FIRST_VALID:
            return None  # ONGC's page goes back to 2013: archive, not news
        name = urllib.parse.unquote(urllib.parse.urlsplit(url).path.rsplit("/", 1)[-1]) or url
        m = re.search(r"\b(?:advt\.?|advertisement)\s*no\.?[^0-9]{0,20}\d+\s*/\s*(20\d{2})\b", title, re.I)
        key = url + "\n" + title + (f"\n{day}" if self.dated_ids else "")
        return Item(
            id=f"{self.SOURCE}-" + hashlib.sha1(key.encode()).hexdigest()[:12],
            kind=(self.kind or kind_of)(title), lang="hi" if classify.is_hindi(title) else "en", title=title,
            exam=self.family, exam_year=int(m.group(1)) if m else None,
            published=dt.datetime.combine(day, dt.time(), IST),
            files=((name, url, None),) if re.search(r"\.(pdf|jpe?g|png)$", url, re.I) else (),
            link=None if re.search(r"\.(pdf|jpe?g|png)$", url, re.I) else url,
            source=self.SOURCE, date_only=True,
        )

    def parse(self, page: str, today: dt.date, cutoff: dt.date, warn: Callable[[str], None] = print) -> list[Item]:
        entries = list(self.entries(page))
        items: dict[str, Item] = {}
        rejected = outside = 0
        for raw_date, href, headline in entries:
            try:
                item = self.parse_entry(raw_date, href, headline, today)
            except RecordError as e:
                rejected += 1
                warn(f"::warning::{self.SOURCE}: skipped entry: {e}")
                continue
            if item is None:
                outside += 1
            elif item.published.date() >= cutoff:
                items.setdefault(item.id, item)
        if len(entries) < self.min_items or rejected > len(entries) * MAX_REJECT_SHARE:
            raise FormatChanged(f"{self.SOURCE}: {len(entries)} entries, {rejected} malformed")
        print(f"{self.SOURCE}: {len(entries)} entries, {outside} outside portals or archived entries left out")
        return sorted(items.values(), key=lambda i: (i.published, i.id), reverse=True)

    def fetch(self, get_text: Callable[[str], str], now: dt.datetime, window_days: int,
              warn: Callable[[str], None] = print) -> list[Item]:
        return self.parse(get_text(self.NOTICE_PAGE), now.date(), (now - dt.timedelta(days=window_days)).date(), warn)


class _Outside(Exception):
    pass


def _dmy(raw: str, _title: str = "") -> dt.date:
    m = re.fullmatch(r"\s*(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})\s*", raw)
    if not m:
        raise ValueError(raw)  # parse_entry turns it into a RecordError
    return dt.date(int(m.group(3)), int(m.group(2)), int(m.group(1)))


# Bihar boards: <tr><td ...>17-10-2026</td><td ...><a ... href="Advt/x.pdf">headline</a> ...</td></tr>
_ROW = re.compile(r"<tr>\s*<td[^>]*>\s*(\d{1,2}[-/]\d{1,2}[-/]\d{4})\s*</td>\s*<td[^>]*>\s*<a\b([^>]*)>(.*?)</a>(.*?)</td>", re.S | re.I)
_ADVT_TAIL = re.compile(r"\(\s*Advt\.?\s*No\.?\s*[-:]?\s*\d+\s*/\s*20\d{2}\s*\)", re.I)
_HREF = re.compile(r"""href\s*=\s*(["'])(.*?)\1""", re.S | re.I)


def _table_rows(page: str) -> Iterator[tuple[str, str, str]]:
    for date, attrs, inner, tail in _ROW.findall(page):
        h = _HREF.search(attrs)
        advt = _ADVT_TAIL.search(_TAG.sub(" ", tail))  # CSBC writes "(Advt. No. 01/2026)" after the link
        yield date, h.group(2) if h else "", inner + (" " + advt.group(0) if advt else "")


# ISRO: <td class="news"><a href="SDSCRecruitment26.html" title=" ADVT NO. ... Dated 19.10.2026 ...">
_ISRO = re.compile(r"""<td class="news">\s*<a\s+href="([^"]+)"\s+title="([^"]*)\"""", re.S)
_ISRO_DATE = re.compile(r"\bdated\s*:?\s*(\d{1,2}[-./]\d{1,2}[-./]\d{4})", re.I)


def _isro_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, title in _ISRO.findall(page):
        m = _ISRO_DATE.search(html.unescape(title))
        yield (m.group(1) if m else ""), href, title


def _isro_date(raw: str, title: str) -> dt.date:
    """The advertisement's own date (ISRO lists no posting date); updates keep the advert's date."""
    if not raw:
        raise ValueError("no 'dated' in headline")
    return _dmy(raw)


# ONGC: <li class="list-group-item"> <a ... href=/documents/...> <span|p class="list-group-title">T</..></a>
#        <p class="list-group-subtitle text-muted">16 Oct, 2026 </p>
_ONGC = re.compile(r"""<li class="list-group-item">\s*<a\b([^>]*)>(.*?)</a>\s*<p class="list-group-subtitle[^"]*">\s*([^<]+?)\s*</p>""", re.S)
_ONGC_HREF = re.compile(r"""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", re.I)


def _ongc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for attrs, inner, date in _ONGC.findall(page):
        h = _ONGC_HREF.search(attrs)
        yield date, next((g for g in h.groups() if g), "") if h else "", inner


def _ongc_date(raw: str, _title: str) -> dt.date:
    return dt.datetime.strptime(re.sub(r"\s+", " ", raw.strip()), "%d %b, %Y").date()


# MPPSC: its home page links each new file, the date in the name:
#   <a title="..." target="_blank" href="[https://mppsc.mp.gov.in/]uploads/advertisement/..._Dated_08_10_2026.pdf">headline</a>
# The office's own purchase notices (tenders) sit in the same list and are left out.
_MPPSC = re.compile(r"""<a\b([^>]*?)href="((?:https://mppsc\.mp\.gov\.in/)?uploads/(?:advertisement|files)/[^"]+)"[^>]*>(.*?)</a>""", re.S | re.I)
_TITLE_ATTR = re.compile(r'title="([^"]*)"', re.I)
_MPPSC_DATE = re.compile(r"Dated_(\d{1,2})_(\d{1,2})_(\d{4})", re.I)
_MPPSC_TEXT_DATE = re.compile(r"\bdated\s*:?\s*(\d{1,2}[-./]\d{1,2}[-./]\d{4})", re.I)
_TENDER = re.compile(r"(_Computer_Dated_|\btender|\bquotation|निविदा|आवश\u094d?\u200d?यकता)", re.I)


def _mppsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for attrs, href, headline in _MPPSC.findall(page):
        if not _TAG.sub("", headline).strip():  # a link with only an icon: its title attribute says what it is
            t = _TITLE_ATTR.search(attrs)
            headline = t.group(1) if t else ""
        text = html.unescape(_TAG.sub(" ", headline))
        if _TENDER.search(href + " " + text):
            continue
        m = _MPPSC_DATE.search(href)
        t = _MPPSC_TEXT_DATE.search(text)
        yield (f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if m else t.group(1) if t else ""), href, headline


# CTET: <a download href="https://cdnbbsr.s3waas.gov.in/<ctet's folder>/uploads/2026/09/20260914325514446.pdf">headline</a>
# The file name starts with the upload day (YYYYMMDD), inside that year/month folder.
CTET_FILES = "https://cdnbbsr.s3waas.gov.in/s3443dec3062d0286986e21dc0631734c9/"
_CTET = re.compile(r"""<a\b[^>]*?href="(""" + re.escape(CTET_FILES) + r"""uploads/(\d{4})/(\d{2})/(\d{8})\d*\.pdf)"[^>]*>(.*?)</a>""", re.S | re.I)


def _ctet_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, year, month, day, headline in _CTET.findall(page):
        same_folder = day[:6] == year + month
        yield (f"{day[6:]}-{day[4:6]}-{day[:4]}" if same_folder else ""), href, headline


# RRB: the unified site (since 08-2026 every Railway Recruitment Board's site is a page of it) lists
# each update as CEN number, category and day, linking that category's page for the CEN:
#   <li><a href="/getdata?loc=chennai&cenum=08/2025&category=Exam Results"><Strong>(08/2025) &nbsp;&nbsp;
#   </Strong>Exam Results <span class="pub_date">(08-10-2026)</span></a></li>
# CENs (Centralised Employment Notices) are common to all RRBs, so one board's page has them all.
_RRB = re.compile(r"""<li>\s*<a\s+href="(/getdata\?[^"]+)">\s*<Strong>\s*\((\d{2}/\d{4})\)\s*(?:&nbsp;\s*)*</Strong>\s*([^<]+?)\s*"""
                  r"""<span class="pub_date">\s*\((\d{2}-\d{2}-\d{4})\)\s*</span>\s*</a>\s*</li>""", re.S | re.I)
RRB_KINDS = {"Notification": "notice", "Indicative Notice": "notice", "Application (Special Notice)": "notice",
             "Special Notice on Examination": "notice", "Medical Examination": "notice",
             "Exam Results": "result", "Selection List": "result", "Document Verification": "result",
             "E-Call Letters": "admit_card", "City Intimation (Exam)": "admit_card",
             "Exam Schedule": "schedule", "Objection Tracker": "answer_key"}


def _rrb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, cen, category, day in _RRB.findall(page):
        if category.strip() == "Mock Test Link":
            continue  # practice links, not news
        yield day, html.unescape(href).replace(" ", "%20"), f"RRB CEN {cen}: {category.strip()}"


def _rrb_kind(title: str) -> str:
    return RRB_KINDS.get(title.split(": ", 1)[-1], "notice")


CSBC = Board("csbc", "https://csbc.bihar.gov.in", "https://csbc.bihar.gov.in/", "CSBC", _table_rows, _dmy)
BPSSC = Board("bpssc", "https://bpssc.bihar.gov.in", "https://bpssc.bihar.gov.in/", "BPSSC", _table_rows, _dmy)
ISRO = Board("isro", "https://www.isro.gov.in", "https://www.isro.gov.in/Careers.html", "ISRO", _isro_entries, _isro_date)
ONGC = Board("ongc", "https://ongcindia.com", "https://ongcindia.com/web/eng/career/recruitment-notice", "ONGC",
             _ongc_entries, _ongc_date)
MPPSC = Board("mppsc", "https://mppsc.mp.gov.in", "https://mppsc.mp.gov.in/", "MPPSC", _mppsc_entries, _dmy)
CTET = Board("ctet", "https://cdnbbsr.s3waas.gov.in", "https://ctet.nic.in/", "CTET", _ctet_entries, _dmy, min_items=3)
RRB = Board("rrb", "https://rrbchennai.gov.in", "https://rrbchennai.gov.in/", "RRB", _rrb_entries, _dmy,
            kind=_rrb_kind, dated_ids=True)
ALL = (CSBC, BPSSC, ISRO, ONGC, MPPSC, CTET, RRB)
INDIA_ONLY = {"mppsc", "ctet", "rrb"}  # don't answer GitHub's US runners: only the India server reads them
