"""Staff Selection Commission (ssc.gov.in): notices, results, admit cards, answer keys, calendar.

The site is an Angular app over a public JSON API (found in its main.js, 20-10-2026):

  GET /api/general-website/portal/records?contentType=<type>&page=N&...   paged listings
  GET /api/general-website/portal/ssc-calendar?...                         tentative calendar
  GET /api/general-website/portal/lastUpdates                              newest createdAt
  GET /api/admin/5.1/allExams                                              examId -> examCode
  GET /api/attachment/<path with "/" for "\\">                               the PDF itself

Quirks measured on the live API (20-10-2026):
  * Without pageType=filter, page 1 is served from a stale server cache with its own page
    size: results page 1 said 382 records in pages of 6 while page 2 said 386 in pages of
    100, so the 4 newest results were missing and records 7-100 were skipped entirely.
    pageType=filter (what the site's own Results page sends) returned the live list in
    pages of 100 on every listing, so it is always sent, and a run whose pages disagree
    on page size or total is rejected instead of published with a hole in it.
  * notice-boards without a `language` filter returns a 3-per-page list of 854 records.
    language=english and language=hindi are fetched separately.
  * A bad query is HTTP 200 with statusCode "203" (handled in http.get_json).
  * createdAt is UTC ("...Z"). Indian candidates read dates in IST, so they are converted.
"""
from __future__ import annotations

import dataclasses
import datetime as dt
import re
import urllib.parse
from typing import Callable

from . import classify

BASE = "https://ssc.gov.in"
RECORDS = BASE + "/api/general-website/portal/records"
CALENDAR = BASE + "/api/general-website/portal/ssc-calendar"
LAST_UPDATES = BASE + "/api/general-website/portal/lastUpdates"
EXAMS = BASE + "/api/admin/5.1/allExams"
ATTACHMENT = BASE + "/api/attachment/"
NOTICE_BOARD_PAGE = BASE + "/home/notice-board"

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
_ATTRS = "id,headline,examId,examYear,contentType,redirectUrl,startDate,endDate,language,createdAt"
_CAL_ATTRS = "id,headline,examId,examYear,desc,content,contentType,startDate,endDate,language,createdAt"

# (contentType, language filter, kind when the listing itself says what it is)
STREAMS: tuple[tuple[str, str | None, str | None], ...] = (
    ("notice-boards", "english", None),
    ("notice-boards", "hindi", None),
    ("results", None, "result"),
    ("admit-card", None, "admit_card"),
    ("answer-key", None, "answer_key"),
)

_ID = re.compile(r"^[a-z0-9]{8,40}$")
_PATH = re.compile(r"^uploads[\\/]masterData[\\/][A-Za-z]+[\\/][^\\/]+\.(pdf|xlsx?|docx?|zip|jpe?g|png)$", re.I)
_TRUSTED_LINK = re.compile(r"^https://([a-z0-9-]+\.)*(gov\.in|nic\.in)(/|$)", re.I)
_FIRST_VALID = dt.datetime(2015, 1, 1, tzinfo=dt.timezone.utc)
# A stream whose records fail validation this often has changed format: stop, don't publish.
MAX_REJECT_SHARE = 0.2

Getter = Callable[[str], dict]


class RecordError(ValueError):
    pass


class FormatChanged(RuntimeError):
    pass


@dataclasses.dataclass(frozen=True)
class Item:
    id: str
    kind: str
    lang: str
    title: str
    exam: str | None
    exam_year: int | None
    published: dt.datetime  # IST
    files: tuple[tuple[str, str, int | None], ...]  # (name, url, size in bytes)
    link: str | None = None  # an official page the record points to instead of / besides a file

    def to_json(self) -> dict:
        out = {
            "id": self.id,
            "kind": self.kind,
            "lang": self.lang,
            "title": self.title,
            "exam": self.exam,
            "examYear": self.exam_year,
            "publishedAt": self.published.isoformat(timespec="seconds"),
            "files": [{"name": n, "url": u, "size": s} for n, u, s in self.files],
        }
        if self.link:
            out["link"] = self.link
        return out


def attachment_url(path: str) -> str:
    """'uploads\\masterData\\NoticeBoards\\a b.pdf' -> https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/a%20b.pdf"""
    # SSC's own file names contain "..pdf"; traversal is impossible anyway because the
    # pattern allows no separator in the file name.
    if not _PATH.match(path):
        raise RecordError(f"unexpected attachment path {path!r}")
    return ATTACHMENT + urllib.parse.quote(path.replace("\\", "/"), safe="/")


def parse_time(value: object, now: dt.datetime) -> dt.datetime:
    if not isinstance(value, str):
        raise RecordError(f"createdAt is {value!r}")
    try:
        t = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as e:
        raise RecordError(f"createdAt {value!r}: {e}") from None
    if t.tzinfo is None:
        raise RecordError(f"createdAt {value!r} has no time zone")
    if not _FIRST_VALID <= t <= now + dt.timedelta(days=1):
        raise RecordError(f"createdAt {value!r} out of range")
    return t.astimezone(IST)


def exam_index(doc: dict) -> dict[str, tuple[str | None, int | None]]:
    """examId -> (exam family, year in the exam's name)."""
    data = doc.get("data")
    if not isinstance(data, list) or not data:
        raise FormatChanged("allExams: no data")
    out = {}
    for e in data:
        if isinstance(e, dict) and isinstance(e.get("id"), str) and isinstance(e.get("examCode"), str):
            out[e["id"]] = (classify.family_of(e["examCode"]), classify.year_of(str(e.get("examName", ""))))
    if len(out) < len(data) * (1 - MAX_REJECT_SHARE):
        raise FormatChanged("allExams: entries lack id/examCode")
    return out


def parse_record(raw: dict, stream_kind: str | None, exams: dict, now: dt.datetime) -> Item:
    rid = raw.get("id")
    if not isinstance(rid, str) or not _ID.match(rid):
        raise RecordError(f"id {rid!r}")
    title = classify.normalize(str(raw.get("headline") or ""))
    if not title or len(title) > 1000:
        raise RecordError(f"{rid}: headline empty or too long")
    published = parse_time(raw.get("createdAt"), now)

    files = []
    for a in raw.get("attachments") or []:
        if not isinstance(a, dict) or not isinstance(a.get("path"), str):
            raise RecordError(f"{rid}: attachment {a!r}")
        size = a.get("size")
        name = classify.normalize(str(a.get("fileName") or a["path"].replace("\\", "/").rsplit("/", 1)[-1]))
        files.append((name, attachment_url(a["path"]), size if isinstance(size, int) and size >= 0 else None))
    link = raw.get("redirectUrl")
    link = link.strip() if isinstance(link, str) and _TRUSTED_LINK.match(link.strip()) else None

    lang = raw.get("language")
    lang = {"english": "en", "hindi": "hi"}.get(lang) if isinstance(lang, str) else None
    lang = lang or ("hi" if classify.is_hindi(title) else "en")

    exam, exam_year = None, None
    if isinstance(raw.get("examId"), str) and raw["examId"] in exams:
        exam, exam_year = exams[raw["examId"]]
    exam = exam or classify.exam_of(title)
    ey = raw.get("examYear")
    if isinstance(ey, str) and re.fullmatch(r"20\d{2}", ey.strip()):
        exam_year = int(ey)
    exam_year = classify.year_of(title) or exam_year

    kind = classify.kind_of(title)
    if stream_kind and kind in ("notice", "schedule", "vacancy"):
        kind = stream_kind  # the listing is more reliable than a headline with no keyword
    return Item(f"ssc-{rid}", kind, lang, title, exam, exam_year, published, tuple(files), link)


def records_url(content_type: str, language: str | None, page: int) -> str:
    q = {
        "contentType": content_type, "page": page, "limit": 100, "key": "createdAt", "order": "DESC",
        "isAttachment": "true", "attributes": _ATTRS, "pageType": "filter",
    }
    if language:
        q["language"] = language
    return RECORDS + "?" + urllib.parse.urlencode(q, safe=",")


def fetch_stream(get: Getter, content_type: str, language: str | None, stream_kind: str | None,
                 exams: dict, now: dt.datetime, cutoff: dt.datetime, max_pages: int,
                 warn: Callable[[str], None] = print) -> tuple[list[Item], int]:
    """(records newer than `cutoff` newest first, number of records the API returned).
    Raises FormatChanged instead of guessing."""
    items: dict[str, Item] = {}
    rejected = seen = 0
    shape = None
    for page in range(1, max_pages + 1):
        doc = get(records_url(content_type, language, page))
        data, pag = doc.get("data"), doc.get("paginate")
        if not isinstance(data, list) or not isinstance(pag, dict):
            raise FormatChanged(f"{content_type}/{language}: no data/paginate")
        if data:
            this = (str(pag.get("totalPerpage")), pag.get("totalRecords"))
            if shape is not None and this != shape:
                raise FormatChanged(f"{content_type}/{language}: page {page} is {this}, page 1 was {shape} "
                                    "(cached pages mixed with live ones)")
            shape = this
        oldest = None
        for raw in data:
            seen += 1
            try:
                item = parse_record(raw, stream_kind, exams, now)
            except RecordError as e:
                rejected += 1
                warn(f"::warning::ssc {content_type}: skipped record: {e}")
                continue
            oldest = item.published if oldest is None else min(oldest, item.published)
            if item.published >= cutoff:
                items.setdefault(item.id, item)
        if not data or not pag.get("nextPage") or (oldest is not None and oldest < cutoff):
            break
    if seen and rejected > seen * MAX_REJECT_SHARE:
        raise FormatChanged(f"{content_type}/{language}: {rejected}/{seen} records failed validation")
    return sorted(items.values(), key=lambda i: (i.published, i.id), reverse=True), seen


def merge(streams: list[list[Item]]) -> list[Item]:
    """One list, newest first. The same notice posted to two listings is shown once.

    SSC often posts a result both on the notice board and under Results, as separate records
    with the same headline. The copy with more files (the lists, not just the write-up) wins.
    """
    best: dict[tuple, Item] = {}
    for item in (i for s in streams for i in s):
        key = (item.title.casefold(), item.published.date())
        old = best.get(key)
        if old is None or len(item.files) > len(old.files):
            best[key] = item
    return sorted(best.values(), key=lambda i: (i.published, i.id), reverse=True)


def calendar_url() -> str:
    q = {
        "page": 1, "limit": 100, "contentType": "ssc-calendar", "key": "startDate", "order": "ASC",
        "isAttachment": "true", "isPaginationRequired": "false", "language": "english", "attributes": _CAL_ATTRS,
    }
    return CALENDAR + "?" + urllib.parse.urlencode(q, safe=",")


def parse_calendar(doc: dict, exams: dict) -> list[dict]:
    """The Commission's tentative calendar: application window and exam month per exam.

    These dates are plans, not the notice. The app must label them "tentative" and
    take the real dates from the exam's Notice once it is out.
    """
    data = doc.get("data")
    if not isinstance(data, list):
        raise FormatChanged("calendar: no data")
    out = []
    for r in data:
        try:
            start = dt.date.fromisoformat(str(r.get("startDate"))[:10])
            end = dt.date.fromisoformat(str(r.get("endDate"))[:10])
        except ValueError:
            continue
        title = classify.normalize(str(r.get("headline") or ""))
        if not title or end < start:
            continue
        exam, year = exams.get(r.get("examId"), (None, None))
        out.append({
            "exam": exam or classify.exam_of(title),
            "examYear": classify.year_of(title) or year,
            "title": title,
            "stage": classify.normalize(str(r.get("desc") or "")) or None,
            "applyFrom": start.isoformat(),
            "applyTo": end.isoformat(),
            "examWindow": classify.normalize(str(r.get("content") or "")) or None,
            "tentative": True,
        })
    if data and len(out) < len(data) * (1 - MAX_REJECT_SHARE):
        raise FormatChanged("calendar: entries lack dates/headline")
    return sorted(out, key=lambda e: (e["applyFrom"], e["title"]))


def last_update(doc: dict) -> str:
    value = (doc.get("data") or {}).get("createdAt")
    if not isinstance(value, str) or not value:
        raise FormatChanged("lastUpdates: no createdAt")
    return value
