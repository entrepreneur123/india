"""Fetch, cross-check and publish result JSON.

  python -m lottery_pipeline.build --out ../public

Output (static files, served from any CDN):
  v1/latest.json            draws of the last 7 days, newest first
  v1/days/YYYY-MM-DD.json   one file per day (archive)

Publishing rules (wrong numbers are worse than late numbers):
  * Numbers come from the text-based Sambad PDF whenever it exists.
  * The official gazette is OCR'd only to verify; agreement sets verified=true.
    Disagreement keeps the Sambad numbers, verified=false, and prints a
    ::warning:: so the GitHub Actions run is flagged for a human.
  * A draw only the gazette carries (Dear Laxmi) is published from OCR only
    when every OCR pass agrees, with ocr=true; otherwise as status PDF_ONLY
    (no numbers, the app links the official PDF).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys

from . import official, sambad
from .model import SLOTS, Draw, ValidationError

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
SCHEMA = 1
LATEST_DAYS = 7


def _day_path(out: str, date: dt.date) -> str:
    return os.path.join(out, "v1", "days", f"{date.isoformat()}.json")


def load_day(out: str, date: dt.date) -> dict[str, dict]:
    try:
        with open(_day_path(out, date), encoding="utf-8") as f:
            return {d["id"]: d for d in json.load(f)["draws"]}
    except FileNotFoundError:
        return {}


def _write_json(path: str, obj: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def _official_pdf(entry: dict) -> str | None:
    return next((s["url"] for s in entry.get("sources", []) if s["kind"] == "official"), None)


def merge(existing: dict | None, fast: Draw | None, gazette: Draw | None,
          gazette_url: str | None, warn) -> dict | None:
    """Combine what we already published with this run's reads. Pure function."""
    base: dict | None = existing
    if fast is not None:
        if base is not None and base.get("status") == "OK" and not base.get("ocr"):
            if Draw.from_json(base).numbers_key() != fast.numbers_key():
                warn(f"{fast.id}: Sambad file changed since last publish; using new file")
        prev_sources = [s for s in (base or {}).get("sources", []) if s["kind"] == "official"]
        base = fast.to_json() | {"status": "OK", "ocr": False}
        base["sources"] += prev_sources
        base["verified"] = bool((existing or {}).get("verified")) and \
            Draw.from_json(existing).numbers_key() == fast.numbers_key()

    if gazette_url is not None:
        if base is None:
            if gazette is None:
                return None  # caller publishes a PDF_ONLY entry
            base = gazette.to_json() | {"status": "OK", "ocr": True}
        elif gazette is not None and base.get("status") == "OK" and not base.get("ocr"):
            same = Draw.from_json(base).numbers_key() == gazette.numbers_key()
            base["verified"] = same
            if not same:
                warn(f"{base['id']}: official gazette OCR disagrees with Sambad; kept Sambad, needs review")
        if not any(s["kind"] == "official" for s in base["sources"]):
            base["sources"].append({"kind": "official", "url": gazette_url, "ocr": gazette is not None})
    return base


def pdf_only(slot_id: str, date: dt.date, url: str) -> dict:
    slot = SLOTS[slot_id]
    return {"id": f"{date.isoformat()}_{slot_id}", "slot": slot_id, "date": date.isoformat(),
            "time": slot.time, "state": slot.state, "name": slot.label.upper(), "drawNo": None,
            "format": slot.scheme.ticket_format, "price": slot.scheme.price, "verified": False,
            "status": "PDF_ONLY", "ocr": False, "tiers": [],
            "sources": [{"kind": "official", "url": url, "ocr": False}]}


def run(out: str, today: dt.date, backfill_days: int, use_official: bool) -> int:
    warnings: list[str] = []

    def warn(msg: str) -> None:
        warnings.append(msg)
        print(f"::warning::{msg}")

    dates = [today - dt.timedelta(days=i) for i in range(backfill_days + 1)]
    for date in dates:
        day = load_day(out, date)
        codes = official.list_codes(date) if use_official else []
        for slot_id, slot in SLOTS.items():
            key = f"{date.isoformat()}_{slot_id}"
            existing = day.get(key)
            fast = None
            if date == today and slot.scheme.ticket_format == "D5":
                try:
                    fast = sambad.fetch(slot_id, date)
                except ValidationError as e:
                    warn(f"{key}: Sambad file rejected: {e}")
            gazette, gazette_url = None, None
            already_checked = existing is not None and _official_pdf(existing) is not None
            if use_official and slot.file_code in codes and not already_checked:
                try:
                    gazette, gazette_url = official.fetch(slot_id, date)
                except (ValidationError, RuntimeError) as e:
                    warn(f"{key}: official fetch failed: {e}")
            if existing is None and fast is None and gazette is None and gazette_url:
                day[key] = pdf_only(slot_id, date, gazette_url)
                continue
            merged = merge(existing, fast, gazette, gazette_url, warn)
            if merged is not None:
                day[key] = merged
        if day:
            order = {s: i for i, s in enumerate(SLOTS)}
            draws = sorted(day.values(), key=lambda d: order[d["slot"]])
            _write_json(_day_path(out, date), {"schema": SCHEMA, "date": date.isoformat(), "draws": draws})

    latest = []
    for i in range(LATEST_DAYS):
        d = load_day(out, today - dt.timedelta(days=i))
        latest += sorted(d.values(), key=lambda x: x["time"], reverse=True)
    _write_json(os.path.join(out, "v1", "latest.json"), {
        "schema": SCHEMA,
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "draws": latest,
    })
    print(f"published {len(latest)} draws, {len(warnings)} warnings")
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--out", required=True)
    p.add_argument("--date", help="IST date yyyy-mm-dd (default: today in IST)")
    p.add_argument("--backfill-days", type=int, default=1)
    p.add_argument("--no-official", action="store_true")
    a = p.parse_args(argv)
    today = dt.date.fromisoformat(a.date) if a.date else dt.datetime.now(IST).date()
    return run(a.out, today, a.backfill_days, not a.no_official)


if __name__ == "__main__":
    sys.exit(main())
