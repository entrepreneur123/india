"""Uttar Pradesh Police Recruitment & Promotion Board (uppbpb.gov.in): notice board.

  GET https://uppbpb.gov.in/Home/Notice     server-rendered HTML, <ul id="noticeList">

Each entry (checked on the live page, 20-10-2026; tests/fixtures/upprpb_notice.html):

  <li> <a href='/FilesUploaded/Notice/<name>.pdf' ...> HINDI HEADLINE<span class='whatsnew'>
       [ Notice Board ]</span></a> <p class="date">Date : 14-10-2026</p> </li>

  * Headlines are Hindi only. The board gives a date, no time: items carry dateOnly.
  * About a third of the entries link to an exam agency's portal (upcons2025.com,
    hgupexam2025.com, iroams.com, ...) instead of a PDF on uppbpb.gov.in. Those domains can't
    be told apart from look-alike sites, so they are not shown; the board posts the same
    news as its own PDF on the same day (e.g. "final result published" + "link to see it").
  * Promotion exams (पदोन्नति) are for serving police staff only: grouped with the other
    departmental exams (LDCE), which the app hides unless asked.
  * Families: UPPC constable, UPSI sub-inspector, UPHG home guards, UPP everything else
    (head constable motor transport, ...). UPP mixes recruitments, so it gets no timeline.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import html
import re
import urllib.parse
from typing import Callable

from . import classify
from .ssc import IST, FormatChanged, Item, RecordError

BASE = "https://uppbpb.gov.in"
NOTICE_PAGE = BASE + "/Home/Notice"
SOURCE = "upprpb"
MIN_ITEMS = 5  # the board keeps ~40 entries; a handful means the page came back broken
MAX_REJECT_SHARE = 0.2

_LIST = re.compile(r'<ul id="noticeList">(.*?)</ul>', re.S)
_LI = re.compile(r"<li\b[^>]*>(.*?)</li>", re.S)
_A = re.compile(r"""<a\s+href=(['"])(.*?)\1[^>]*>(.*?)</a>""", re.S)
_DATE = re.compile(r'<p class="date">\s*Date\s*:\s*(\d{2})-(\d{2})-(\d{4})\s*</p>')
_PATH = re.compile(r"^/FilesUploaded/Notice/[^/\\?#]+\.(pdf|jpe?g|png)$", re.I)
_GUID = re.compile(r"_?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=\.\w+$)", re.I)
_TAG = re.compile(r"<[^>]+>")
_FIRST_VALID = dt.date(2015, 1, 1)

# Words the board uses that SSC's rules (classify) don't know; checked first. Order matters:
# "PET date changed" is a change, "admit card for the PET" is an admit card.
_RULES: list[tuple[str, re.Pattern]] = [
    ("amendment", re.compile(r"(स्थगित|परिवर्तन|परिवर्तित|संशोधित|रद्द|निरस्त)")),
    ("answer_key", re.compile(r"उत्तर\s*कुंज[ीि]")),
    ("admit_card", re.compile(r"प्रवेश\s*पत्र")),
    ("result", re.compile(r"(परिणाम|प्रसामान्यीकृत\s*अंक|चयन\s*सूची|अर्ह\s*अभ्यर्थि)")),
    ("schedule", re.compile(r"तिथि")),
]
# Exam family by post. First match wins: "मुख्य आरक्षी" (head constable) is not "आरक्षी".
_FAMILIES: list[tuple[str, re.Pattern]] = [
    ("LDCE", re.compile(r"(पदोन्नति|प्रोन्नति)")),
    ("UPHG", re.compile(r"होम\s*गार्ड")),
    ("UPP", re.compile(r"मुख्य\s*आरक्षी")),
    ("UPSI", re.compile(r"उप\s*-?\s*निरीक्षक")),
    ("UPPC", re.compile(r"आरक्षी")),
]
FAMILIES = ("UPPC", "UPSI", "UPHG", "UPP")
_YEAR = re.compile(r"(?:भर्ती|एनरोलमेंट)\s*[-—–]?\s*(20\d{2})\b")


def kind_of(title: str) -> str:
    for kind, pattern in _RULES:
        if pattern.search(title):
            return kind
    return classify.kind_of(title)


def file_url(href: str) -> str:
    href = html.unescape(href.strip())
    if href.startswith(BASE + "/"):
        href = href[len(BASE):]
    if not _PATH.match(href):
        raise RecordError(f"not a file on {BASE}: {href[:120]!r}")
    return BASE + urllib.parse.quote(href, safe="/")


def file_name(url: str) -> str:
    """'.../const 14 Oct 2026c0201978-...-e162a881cb82.pdf' -> 'const 14 Oct 2026.pdf'"""
    name = urllib.parse.unquote(url.rsplit("/", 1)[1])
    return _GUID.sub("", name).strip() or name


def parse_entry(li: str, today: dt.date) -> Item | None:
    """One <li>. None for a link to an outside portal; RecordError for anything malformed."""
    a, d = _A.search(li), _DATE.search(li)
    if not a or not d:
        raise RecordError(f"entry without link or date: {_TAG.sub(' ', li)[:120]!r}")
    href = a.group(2).strip()
    if re.match(r"^https?://", href, re.I) and not href.startswith(BASE + "/"):
        return None
    url = file_url(href)
    title = classify.normalize(html.unescape(_TAG.sub(" ", a.group(3).split("<span class='whatsnew'")[0])))
    title = re.sub(r"\s*[।|]$", "", title)  # the board ends headlines with a danda or a "|"
    if not title or len(title) > 1000:
        raise RecordError(f"{url}: headline empty or too long")
    try:
        day = dt.date(int(d.group(3)), int(d.group(2)), int(d.group(1)))
    except ValueError:
        raise RecordError(f"{url}: bad date {d.group(0)!r}") from None
    if not _FIRST_VALID <= day <= today + dt.timedelta(days=1):
        raise RecordError(f"{url}: date {day} out of range")
    exam = next((code for code, p in _FAMILIES if p.search(title)), "UPP")
    m = _YEAR.search(title)
    return Item(
        id="upprpb-" + hashlib.sha1(url.encode()).hexdigest()[:12],
        kind=kind_of(title), lang="hi" if classify.is_hindi(title) else "en", title=title,
        exam=exam, exam_year=int(m.group(1)) if m else None,
        published=dt.datetime.combine(day, dt.time(), IST),
        files=((file_name(url), url, None),), source=SOURCE, date_only=True,
    )


def parse(page: str, today: dt.date, cutoff: dt.date, warn: Callable[[str], None] = print) -> list[Item]:
    """Notices from cutoff on, newest first. Raises FormatChanged instead of guessing."""
    m = _LIST.search(page)
    if not m:
        raise FormatChanged("upprpb: no <ul id=\"noticeList\">")
    entries = _LI.findall(m.group(1))
    items: dict[str, Item] = {}
    rejected = outside = 0
    for li in entries:
        try:
            item = parse_entry(li, today)
        except RecordError as e:
            rejected += 1
            warn(f"::warning::upprpb: skipped entry: {e}")
            continue
        if item is None:
            outside += 1
        elif item.published.date() >= cutoff:
            items.setdefault(item.id, item)
    if len(entries) < MIN_ITEMS or rejected > len(entries) * MAX_REJECT_SHARE:
        raise FormatChanged(f"upprpb: {len(entries)} entries, {rejected} malformed")
    print(f"upprpb: {len(entries)} entries, {outside} links to outside portals left out")
    return sorted(items.values(), key=lambda i: (i.published, i.id), reverse=True)


def fetch(get_text: Callable[[str], str], now: dt.datetime, window_days: int,
          warn: Callable[[str], None] = print) -> list[Item]:
    return parse(get_text(NOTICE_PAGE), now.date(), (now - dt.timedelta(days=window_days)).date(), warn)

