"""Roll-number lookup for SSC result lists, so a candidate can check a 26 MB list without downloading it.

SSC publishes results as PDFs of every shortlisted candidate, one row per candidate:
  "Record# ROLL NAME FATHERSNAM MOTHERSNAM [CATEGORY]", e.g. "     1 126280019129 VANSH ..."
Only the roll numbers are kept. Names and parents' names are never stored or republished
(India's DPDP Act 2023; a roll number is all the lookup needs).

A list is published only when its rows number 1, 2, 3 ... with no gap and no roll number
repeats; anything else (another layout, a broken PDF) is skipped and remembered,
because a missing roll number would tell a qualified candidate they failed.

Output under jobs/v1/results/:
  index.json                         the lists, newest first
  <list id>/<last two digits>.json   sorted roll numbers ending in those digits (100 small files)
  skipped.json                       files already tried and not indexable, with the reason
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
from typing import Callable

MAX_PDF_BYTES = 60_000_000
MAX_NEW_PER_RUN = 4  # each list is a big download; the rest wait for the next run
_ROW = re.compile(r"^\s*(\d{1,7})\s+(\d{8,14})\s")
_TITLE = re.compile(r"\bLIST[- ]?[IVX0-9]+\s*[:\-–]\s*(.+)$", re.I)


class NotIndexable(ValueError):
    pass


def parse(text: str) -> tuple[str, list[str]]:
    """(list title or "" when the PDF has no "LIST-n:" line, roll numbers); raises NotIndexable."""
    title = None
    rolls = []
    expect = 1
    for line in text.splitlines():
        if title is None:
            m = _TITLE.search(line)
            if m:
                title = re.sub(r"\s+", " ", m.group(0)).strip()
        m = _ROW.match(line)
        if not m:
            continue
        record, roll = int(m.group(1)), m.group(2)
        if record != expect:
            raise NotIndexable(f"record {record} where {expect} was expected")
        expect += 1
        rolls.append(roll)
    if not rolls:
        raise NotIndexable("no 'Record# ROLL ...' rows")
    # Lengths may differ within one list: the CT(GD) 2026 female list has 14,653 twelve-digit and
    # 1,125 eleven-digit roll numbers, numbered consecutively, so both are real.
    if len(set(rolls)) != len(rolls):
        raise NotIndexable("duplicate roll numbers")
    return title or "", rolls


def shards(rolls: list[str]) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {f"{i:02d}": [] for i in range(100)}
    for r in rolls:
        out[r[-2:]].append(r)
    return {k: sorted(v) for k, v in out.items()}


def pdf_text(data: bytes) -> str:
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "list.pdf")
        with open(src, "wb") as f:
            f.write(data)
        res = subprocess.run(["pdftotext", "-layout", src, "-"], capture_output=True, timeout=300)
        if res.returncode != 0:
            raise NotIndexable(f"pdftotext failed: {res.stderr[:200]!r}")
        return res.stdout.decode("utf-8", "replace")


def _list_files(item: dict) -> list[dict]:
    return [f for f in item["files"]
            if f["url"].lower().endswith(".pdf") and not f["name"].lower().startswith(("writeup", "write_up", "write-up"))
            and (f.get("size") or 0) <= MAX_PDF_BYTES]


def list_id(url: str) -> str:
    return hashlib.sha1(url.encode()).hexdigest()[:12]


def _write(path: str, obj) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path + ".tmp", "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(path + ".tmp", path)


def update(out: str, feed_items: list[dict], get_bytes: Callable[[str], bytes],
           to_text: Callable[[bytes], str] = pdf_text, warn: Callable[[str], None] = print) -> int:
    """Index result lists of feed items not done yet; drop lists whose notice left the feed.
    Never raises for one bad list. Returns the number of lists added."""
    root = os.path.join(out, "jobs", "v1", "results")
    index_path = os.path.join(root, "index.json")
    skipped_path = os.path.join(root, "skipped.json")
    try:
        with open(index_path, encoding="utf-8") as f:
            lists = json.load(f)["lists"]
    except FileNotFoundError:
        lists = []
    try:
        with open(skipped_path, encoding="utf-8") as f:
            skipped = json.load(f)
    except FileNotFoundError:
        skipped = {}

    live = {i["id"] for i in feed_items}
    for old in [x for x in lists if x["item"] not in live]:
        shutil.rmtree(os.path.join(root, old["id"]), ignore_errors=True)
    lists = [x for x in lists if x["item"] in live]
    done = {x["pdf"] for x in lists}

    added = 0
    for item in (i for i in feed_items if i["kind"] == "result" and i["lang"] == "en" and i.get("source", "ssc") == "ssc"):
        for f in _list_files(item):
            if f["url"] in done or f["url"] in skipped:
                continue
            if added >= MAX_NEW_PER_RUN:
                break
            try:
                title, rolls = parse(to_text(get_bytes(f["url"])))
            except NotIndexable as e:
                skipped[f["url"]] = str(e)
                warn(f"results: not indexed {f['name']}: {e}")
                continue
            except Exception as e:  # noqa: BLE001 - network trouble: try again next run
                warn(f"::warning::results: could not fetch {f['url']}: {e}")
                continue
            lid = list_id(f["url"])
            for key, part in shards(rolls).items():
                _write(os.path.join(root, lid, f"{key}.json"), part)
            lists.append({"id": lid, "item": item["id"], "exam": item["exam"], "examYear": item["examYear"],
                          "title": title or item["title"], "notice": item["title"], "publishedAt": item["publishedAt"],
                          "pdf": f["url"], "count": len(rolls), "rollLengths": sorted({len(r) for r in rolls})})
            done.add(f["url"])
            added += 1
    lists.sort(key=lambda x: (x["publishedAt"], x["id"]), reverse=True)
    _write(index_path, {"schema": 1, "lists": lists})
    urls = {f["url"] for i in feed_items for f in i["files"]}
    _write(skipped_path, {u: r for u, r in skipped.items() if u in urls})
    return added
