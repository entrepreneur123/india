"""The India server's share of the collection: boards that answer only from India.

GitHub's runners (US) can't reach these boards, and the India server (Oracle, Mumbai) can't reach
ssc.gov.in (it times out there, 29-10-2026). So the work is split: the server reads only
listboards.INDIA_ONLY and writes jobs/v1/india.json; GitHub's build (build.py) reads SSC and the
other boards and takes these items from india.json. The two never write the same file, so their
pushes to the data branch never conflict.

    python -m sarkari.india --out <data branch checkout>

india.json: {"schema", "generatedAt", "boards": {source: {"ok": bool, "error"?: str}}, "items": [...]}
A board that fails keeps its previous items (inside the window), like build.other_boards.
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import sys

from . import http, listboards, ssc
from .build import SCHEMA, _read_json, _v1, _write_json

BOARDS = tuple(b for b in listboards.ALL if b.SOURCE in listboards.INDIA_ONLY)


def collect(out: str, get_text, now: dt.datetime, window_days: int = 120, warn=print) -> bool:
    """Writes india.json; True if anything but its time changed (then it needs a commit)."""
    path = _v1(out, "india.json")
    old = _read_json(path, {"items": []})
    cutoff = (now - dt.timedelta(days=window_days)).isoformat(timespec="seconds")
    items, boards = [], {}
    for board in BOARDS:
        try:
            items += [i.to_json() for i in board.fetch(get_text, now, window_days, warn)]
            boards[board.SOURCE] = {"ok": True}
        except Exception as e:  # noqa: BLE001 - one board failing never stops the others
            warn(f"::warning::{board.SOURCE}: {type(e).__name__}: {e}; keeping its previous items")
            items += [i for i in old["items"] if i.get("source") == board.SOURCE and i["publishedAt"] >= cutoff]
            boards[board.SOURCE] = {"ok": False, "error": f"{type(e).__name__}: {e}"[:300]}
    items.sort(key=lambda i: (i["publishedAt"], i["id"]), reverse=True)
    return _write_json(path, {"schema": SCHEMA, "generatedAt": now.isoformat(timespec="seconds"),
                              "boards": boards, "items": items})


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--window-days", type=int, default=120)
    a = ap.parse_args(argv)
    changed = collect(a.out, lambda url: http.get_bytes(url, timeout=40, max_bytes=5_000_000).decode("utf-8"),
                      dt.datetime.now(ssc.IST), a.window_days)
    n = len(_read_json(_v1(a.out, "india.json"), {"items": []})["items"])
    print(f"india: {n} items{'' if changed else ', unchanged'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
