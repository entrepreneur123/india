"""Which pages of SSC's result write-ups give the cut-off marks.

"ssc gd cut off" is a common search (research/NEEDS.md). SSC publishes cut-offs only inside
the result write-up PDF, as tables that differ every time (categories, posts, states, marks,
dates of birth of the last selected candidate). Copying the numbers out would risk a wrong
figure, so only the page numbers are published and the app opens SSC's own PDF at that page.

A page counts when it mentions the cut-off, or the last selected / shortlisted candidate,
which is how SSC words it (checked 20-10-2026 on the CT(GD) 2026, CHSL 2025 final and
Steno 2025 final write-ups: each table starts on the first page found, and can run on over
the next pages without a header (CHSL 2025: pages 2-13); the MTS additional-result write-up
has no cut-off and gets none). The app therefore says "from page N".

  jobs/v1/results/cutoffs.json   {"schema": 1, "writeups": [{url, name, item, exam, examYear,
                                  title, publishedAt, pages: [2, 5]}]}  (only write-ups with pages)
  jobs/v1/results/cutoffs-done.json   url -> pages ([] when none), so nothing is fetched twice
"""
from __future__ import annotations

import json
import os
import re
from typing import Callable

from .results_index import pdf_text

MAX_PDF_BYTES = 15_000_000  # write-ups are 0.1-1.3 MB; a bigger file is not a write-up
MAX_NEW_PER_RUN = 6
_WRITEUP = re.compile(r"write[-_ ]?up", re.I)
_MENTION = re.compile(r"cut[- ]?off|last\s+(selected|shortlisted|recommended)", re.I)


def pages(text: str) -> list[int]:
    """1-based numbers of the pages (pdftotext splits pages with a form feed) that mention it."""
    return [n for n, page in enumerate(text.split("\f"), 1) if _MENTION.search(page)]


def writeups(item: dict) -> list[dict]:
    return [f for f in item["files"] if _WRITEUP.search(f["name"]) and f["url"].lower().endswith(".pdf")
            and (f.get("size") or 0) <= MAX_PDF_BYTES]


def _read(path: str, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def _write(path: str, obj) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def update(out: str, feed_items: list[dict], get_bytes: Callable[[str], bytes],
           to_text: Callable[[bytes], str] = pdf_text, warn: Callable[[str], None] = print) -> int:
    """Find cut-off pages in write-ups not looked at yet. Never raises for one bad file.
    Returns the number of write-ups looked at."""
    root = os.path.join(out, "jobs", "v1", "results")
    done = _read(os.path.join(root, "cutoffs-done.json"), {})
    results = [i for i in feed_items if i["kind"] == "result" and i["lang"] == "en" and i.get("source", "ssc") == "ssc"]
    looked, tried = 0, set()
    for item in results:
        for f in writeups(item):
            if f["url"] in done or f["url"] in tried or looked >= MAX_NEW_PER_RUN:
                continue
            tried.add(f["url"])  # the same file is attached to several notices: one attempt a run
            try:
                found = pages(to_text(get_bytes(f["url"])))
            except Exception as e:  # noqa: BLE001 - network trouble or a broken PDF: try again next run
                warn(f"::warning::cutoffs: could not read {f['url']}: {e}")
                continue
            done[f["url"]] = found
            looked += 1
    # One write-up is often attached to several notices (CT(GD) 2026: five), and SSC keeps a
    # copy under both NoticeBoards/ and Results/: it is listed once, under the newest notice.
    listed, seen = [], set()
    for item in sorted(results, key=lambda i: (i["publishedAt"], i["id"]), reverse=True):
        for f in writeups(item):
            if done.get(f["url"]) and f["name"] not in seen:
                seen.add(f["name"])
                listed.append({"url": f["url"], "name": f["name"], "item": item["id"], "exam": item["exam"],
                               "examYear": item["examYear"], "title": item["title"],
                               "publishedAt": item["publishedAt"], "pages": done[f["url"]]})
    live = {f["url"] for i in results for f in writeups(i)}
    new_index = {"schema": 1, "writeups": listed}
    if _read(os.path.join(root, "cutoffs.json"), None) != new_index:
        _write(os.path.join(root, "cutoffs.json"), new_index)
    kept = {u: p for u, p in done.items() if u in live}
    if _read(os.path.join(root, "cutoffs-done.json"), None) != kept:
        _write(os.path.join(root, "cutoffs-done.json"), kept)
    return looked
