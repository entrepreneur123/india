"""The India server's share of the collection: boards that answer only from India.

GitHub's runners (US) can't reach these boards, and the India server (Oracle, Mumbai) can't reach
ssc.gov.in (it times out there, 29-10-2026). So the work is split: the server reads only
boards.INDIA_ONLY and writes jobs/v1/india.json; GitHub's build (build.py) reads SSC and the
other boards and takes these items from india.json. The two never write the same file, so their
pushes to the data branch never conflict.

    python -m sarkari.india --out <data branch checkout>

india.json: {"schema", "generatedAt", "boards": {source: {"ok": bool, "error"?: str}}, "items": [...],
             "seen": {source: {id: day}}}
A board that fails keeps its previous items (inside the window), like build.other_boards.
"seen" (boards whose entries carry no date, such as SBI's): the day each entry on the page was
first seen. It covers every entry still on the page, older than the window too, so an unchanged
entry is never dated again; a board's first run records the page and publishes none of them.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import datetime as dt
import os
import sys

from . import boards, http, ssc
from .build import SCHEMA, _read_json, _v1, _write_json

BOARDS = tuple(b for b in boards.ALL if b.SOURCE in boards.INDIA_ONLY)


def collect(out: str, get_text, now: dt.datetime, window_days: int = 120, warn=print) -> bool:
    """Writes india.json; True if anything but its time changed (then it needs a commit)."""
    path = _v1(out, "india.json")
    old = _read_json(path, {"items": []})
    cutoff = (now - dt.timedelta(days=window_days)).isoformat(timespec="seconds")
    items, boards, seen = [], {}, {}
    old_seen = old.get("seen", {})
    for board in BOARDS:
        record: dict[str, str] = {}
        try:
            items += [i.to_json() for i in board.fetch(get_text, now, window_days, warn, old_seen.get(board.SOURCE), record)]
            boards[board.SOURCE] = {"ok": True}
            if board.undated:
                seen[board.SOURCE] = record
        except Exception as e:  # noqa: BLE001 - one board failing never stops the others
            warn(f"::warning::{board.SOURCE}: {type(e).__name__}: {e}; keeping its previous items")
            items += [i for i in old["items"] if i.get("source") == board.SOURCE and i["publishedAt"] >= cutoff]
            boards[board.SOURCE] = {"ok": False, "error": f"{type(e).__name__}: {e}"[:300]}
            if board.SOURCE in old_seen:
                seen[board.SOURCE] = old_seen[board.SOURCE]
    items.sort(key=lambda i: (i["publishedAt"], i["id"]), reverse=True)
    return _write_json(path, {"schema": SCHEMA, "generatedAt": now.isoformat(timespec="seconds"),
                              "boards": boards, "items": items, "seen": seen})


def page_text(url: str) -> str:
    body = http.get_bytes(url, retries=3, timeout=30, max_bytes=5_000_000, headers=http.PAGE_HEADERS)  # IBPS drops some handshakes
    return body.decode("utf-8", errors="replace")


def prefetch(get_text, urls: list[str], workers: int = 8):
    """Fetches the boards' pages side by side (each from its own site), so a hanging site costs one
    timeout, not one per board: the run stays well inside sarkari.service's 25-minute limit."""
    def one(url):
        try:
            return get_text(url)
        except Exception as e:  # noqa: BLE001 - handed to that board's fetch, which reports it
            return e
    with concurrent.futures.ThreadPoolExecutor(workers) as pool:
        pages = dict(zip(urls, pool.map(one, urls)))

    def get(url: str) -> str:
        page = pages[url] if url in pages else get_text(url)
        if isinstance(page, Exception):
            raise page
        return page
    return get


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--window-days", type=int, default=120)
    a = ap.parse_args(argv)
    changed = collect(a.out, prefetch(page_text, [u for b in BOARDS for u in (b.NOTICE_PAGE, *getattr(b, "MORE_PAGES", ()))]), dt.datetime.now(ssc.IST), a.window_days)
    n = len(_read_json(_v1(a.out, "india.json"), {"items": []})["items"])
    print(f"india: {n} items{'' if changed else ', unchanged'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
