"""Haryana Staff Selection Commission (hssc.gov.in): advertisements, public notices, results.

The site's own list pages load their rows from a JSON endpoint (found in js/custom.js, 20-10-2026):

  GET https://hssc.gov.in/search?site=hssc&section=<section>&searchTxt=&page=N
      {"current_page", "per_page": 10, "total", "last_page", "next_page_url",
       "data": [{"id": uuid, "advtNo", "published_on": "YYYY-MM-DD", "title"?, "catNo"?}]}
  GET https://hssc.gov.in/file/<id>/<section>     the PDF itself (checked: application/pdf)

  * advertisements rows have no title, only "advtNo" ("Advt. No. 06/2026 (Group-C)").
  * Rows come newest first; the board gives a day, no time: items carry dateOnly.
  * Titles are English (tests/fixtures/hssc_*.json).
"""
from __future__ import annotations

import datetime as dt
import json
import re
import urllib.parse
from typing import Callable

from . import classify
from .ssc import IST, FormatChanged, Item, RecordError

BASE = "https://hssc.gov.in"
SOURCE = "hssc"
NOTICE_PAGE = BASE + "/publicNotice"
SECTIONS = ("advertisements", "publicNotice", "result")
MAX_PAGES = 4  # 10 rows a page; ~40 rows go back further than the 120-day window
MAX_REJECT_SHARE = 0.2

_ID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
_DAY = re.compile(r"^\d{4}-\d{2}-\d{2}$")
_FIRST_VALID = dt.date(2015, 1, 1)

# Checked against the real titles (a year of them, 20-10-2026); first match wins.
# "Declaration of revised final result" is a result; "Closing Date Extension" and
# "RE-SCHEDULE NOTICE ... PST" are changes, not new advertisements or notices.
_RULES: list[tuple[str, re.Pattern]] = [
    ("answer_key", re.compile(r"\banswer[- ]?keys?\b", re.I)),
    ("admit_card", re.compile(r"\b(admit card|admission certificate|roll no\. slip)", re.I)),
    ("result", re.compile(r"\b(result|waiting list|marks\b|merit list|selected|list of .*candidates|qualified)", re.I)),
    ("amendment", re.compile(r"\b(corrigendum|addendum|extension|re-?schedule|postpone|revised|cancell?ation)", re.I)),
]


def kind_of(title: str, section: str) -> str:
    for kind, pattern in _RULES:
        if pattern.search(title):
            return kind
    if section == "advertisements":
        return "recruitment"
    return classify.kind_of(title)


def page_url(section: str, page: int) -> str:
    return BASE + "/search?" + urllib.parse.urlencode(
        {"site": "hssc", "section": section, "searchTxt": "", "page": page})


def parse_row(row: dict, section: str, today: dt.date) -> Item:
    rid = row.get("id")
    if not isinstance(rid, str) or not _ID.match(rid):
        raise RecordError(f"id {rid!r}")
    day = row.get("published_on")
    if not isinstance(day, str) or not _DAY.match(day):
        raise RecordError(f"{rid}: published_on {day!r}")
    published = dt.date.fromisoformat(day)
    if not _FIRST_VALID <= published <= today + dt.timedelta(days=1):
        raise RecordError(f"{rid}: date {day} out of range")
    advt = classify.normalize(str(row.get("advtNo") or ""))
    title = classify.normalize(str(row.get("title") or ""))
    if section == "advertisements":
        if not advt:
            raise RecordError(f"{rid}: advertisement without number")
        title = (f"HSSC Advt. No. {advt}" if re.match(r"\d", advt)
                 else f"HSSC {advt}" if re.match(r"advt", advt, re.I) else f"HSSC: {advt}")
    elif not title:
        raise RecordError(f"{rid}: no title")
    elif advt and advt.lower() != "various" and advt not in title:
        # "Public notice regarding PST" means little without the advertisement it belongs to.
        cat = classify.normalize(str(row.get("catNo") or ""))
        title += f" (Advt. No. {advt}{', Cat. No. ' + cat if cat and cat.lower() != 'various' else ''})"
    if len(title) > 1000:
        raise RecordError(f"{rid}: title too long")
    url = f"{BASE}/file/{rid}/{section}"
    return Item(
        id=f"hssc-{rid}", kind=kind_of(title, section), lang="hi" if classify.is_hindi(title) else "en",
        title=title, exam="HSSC", exam_year=None, published=dt.datetime.combine(published, dt.time(), IST),
        files=((f"{section}.pdf", url, None),), source=SOURCE, date_only=True,
    )


def fetch(get_text: Callable[[str], str], now: dt.datetime, window_days: int,
          warn: Callable[[str], None] = print) -> list[Item]:
    """Rows from the window, newest first. Raises FormatChanged instead of guessing."""
    today, cutoff = now.date(), (now - dt.timedelta(days=window_days)).date()
    items: dict[str, Item] = {}
    seen = rejected = 0
    for section in SECTIONS:
        for page in range(1, MAX_PAGES + 1):
            try:
                doc = json.loads(get_text(page_url(section, page)))
            except ValueError as e:
                raise FormatChanged(f"hssc {section}: not JSON ({e})") from None
            data = doc.get("data") if isinstance(doc, dict) else None
            if not isinstance(data, list) or doc.get("current_page") != page:
                raise FormatChanged(f"hssc {section} page {page}: no data/current_page")
            if page == 1 and not data:
                raise FormatChanged(f"hssc {section}: empty list")
            oldest = None
            for row in data:
                seen += 1
                try:
                    item = parse_row(row if isinstance(row, dict) else {}, section, today)
                except RecordError as e:
                    rejected += 1
                    warn(f"::warning::hssc {section}: skipped row: {e}")
                    continue
                day = item.published.date()
                oldest = day if oldest is None else min(oldest, day)
                if day >= cutoff:
                    items.setdefault(item.id, item)
            if not doc.get("next_page_url") or (oldest is not None and oldest < cutoff):
                break
    if rejected > seen * MAX_REJECT_SHARE:
        raise FormatChanged(f"hssc: {rejected}/{seen} rows failed validation")
    print(f"hssc: {seen} rows read, {len(items)} in the window")
    return sorted(items.values(), key=lambda i: (i.published, i.id), reverse=True)
