"""Central recruiters' notice boards (same Board model as listboards.py), read from the India server.

Written against its page of 29-10-2026 (tests/fixtures/<source>.html, whole):

  indiapost  India Post (Department of Posts), vacancies   www.indiapost.gov.in   /vacancies: a table "Title | Size | Format |
                                                                                   Published Date (22-10-2026)", 10 rows a page;
                                                                                   the page's data holds every row (13)

Not collectable from what was saved on 29-10-2026 (and not reachable from outside India to check):
  epfo    www.epfo.gov.in/recruitments/ said "No jobs found." (its archive, /archive-recruitments, was not saved),
          so there was no entry to write a reader against.
  kvs     kvs.gov.in's "भर्ती" page is fixed text (reservation, syllabus, rules, process), no notices; the home page's
          "अद्यतनीकरण" list had only internal items (limited departmental exams, promotion panels, office orders).
  nabard  ftrcontent.aspx?id=660 is a warning about fake recruitment letters that links the real list,
          careers-notices1.aspx?cid=693&id=26 (not saved).

The rules of listboards.py apply: a day, no time; links leaving the board's own host are left out;
a broken page raises FormatChanged.
"""
from __future__ import annotations

import html
import json
import re
from typing import Iterator

from .listboards import Board, _dmy, kind_of

# India Post (Next.js). The table shows the 10 newest rows of a sorted, paged list:
#   <td data-column-key="title" ...><a ... href="/api/documents/file/103pgjk0...">Notification for filling up of Drivers
#   (Ordinary Grade) ...</a> ... <td data-column-key="date" ...><svg ...>...</svg>01-08-2026</div>
# The page's own data (self.__next_f.push([1,"..."]) strings, JSON once joined) carries the whole list:
#   "recruitmentData":[{"id":"...","slug":"...","title":"...","date":"01-08-2026","size":"1 MB",
#   "version":"Hindi Version","type":"PDF","url":"/api/documents/file/103pgjk0..."}, ...]
# so the reader takes that. A notice often comes as two files with one title, an English and a Hindi one;
# the Hindi file's headline says so. Empanelment of artists (stamp design) is a contract call, not a job.
# The links are opaque file tokens (the same in the table, the data and the home page, but only one day was
# seen), so an entry is known by its headline (title_ids) rather than risk republishing on a new token.
_NEXT_F = re.compile(r'self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)', re.S)
_INDIAPOST_DATA = '"recruitmentData":'
_INDIAPOST_SKIP = re.compile(r"\bempanelment\b|\btenders?\b", re.I)


def _indiapost_entries(page: str) -> Iterator[tuple[str, str, str]]:
    data = "".join(json.loads(s) for s in _NEXT_F.findall(page))
    at = data.find(_INDIAPOST_DATA)
    if at < 0:
        return
    rows, _ = json.JSONDecoder().raw_decode(data, at + len(_INDIAPOST_DATA))
    for row in rows if isinstance(rows, list) else ():
        if not isinstance(row, dict):
            continue
        title = str(row.get("title") or "")
        if _INDIAPOST_SKIP.search(title):
            continue
        if str(row.get("version") or "").strip().lower() == "hindi version":
            title += " (Hindi Version)"
        yield str(row.get("date") or ""), str(row.get("url") or ""), html.escape(title, quote=False)


# Its headlines: "Notification for filling up of ...", "Filling up of vacancies of ...", "Direct Recruitment for the
# post of ...", "Corrigendum for ...", "Declaration of (Pending) Results - ...", "First Provisional Merit list ...",
# "... - 11th list of Tainted candidates".
_INDIAPOST_KINDS = [
    ("amendment", re.compile(r"\bcorrigendum\b|\baddendum\b|\bextension\b|\bpostpone", re.I)),
    ("result", re.compile(r"\bresults?\b|\bmerit list\b|\bselect(ion|ed)? list\b", re.I)),
    ("notice", re.compile(r"\btainted\b", re.I)),
    ("recruitment", re.compile(r"^(notification for )?filling up of\b|^direct recruitment for\b", re.I)),
]


def _indiapost_kind(title: str) -> str:
    return next((kind for kind, pattern in _INDIAPOST_KINDS if pattern.search(title)), None) or kind_of(title)


INDIAPOST = Board("indiapost", "https://www.indiapost.gov.in", "https://www.indiapost.gov.in/vacancies", "INDIAPOST",
                  _indiapost_entries, _dmy, kind=_indiapost_kind, title_ids=True)
ALL = (INDIAPOST,)
