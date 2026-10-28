"""Fetch, cross-check and publish result JSON.

  python -m lottery_pipeline.build --out ../public [--backfill-days 30] [--deep]

Output (static files, served from any CDN), schema 2:
  v2/today.json             today's and yesterday's draws: small, polled around draw times
  v2/latest.json            the last 7 days, newest first (first launch / weekly view)
  v2/days/YYYY-MM-DD.json   one file per day (history; immutable once complete)
  v2/index.json             dates with results, dates without a draw, upcoming special draws
  v2/config.json            remote config (mirrors, min app version, live-draw link, notice)

Publishing rules (wrong numbers are worse than late numbers):
  * Numbers come from the text-based Sambad PDF whenever it exists.
  * The official gazette is OCR'd only to verify; agreement sets verified=true.
    Disagreement keeps the Sambad numbers, verified=false, and prints a
    ::warning:: so the GitHub Actions run is flagged for a human.
  * A draw only the gazette carries (Dear Laxmi) is published from OCR only
    when every OCR pass agrees, with ocr=true; otherwise as status PDF_ONLY.
  * Bumper/monthly draws are parsed only with a prize table in specials.json;
    without one they are published as PDF_ONLY with a link to the sheet.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
import time

from . import http, kerala, official, sambad
from .model import SLOTS, SPECIALS, WEEKLY, Draw, ValidationError

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
SCHEMA = 2
HISTORY_DAYS = 90  # number-history search: fetched only when the user opens it
LATEST_DAYS = 7
CONFIG_TEMPLATE = os.path.join(os.path.dirname(__file__), "..", "app_config.json")


def _v2(out: str, *parts: str) -> str:
    return os.path.join(out, "v2", *parts)


def _day_path(out: str, date: dt.date) -> str:
    return _v2(out, "days", f"{date.isoformat()}.json")


def _read_json(path: str, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def load_day(out: str, date: dt.date) -> dict[str, dict]:
    return {d["id"]: d for d in _read_json(_day_path(out, date), {"draws": []})["draws"]}


def _write_json(path: str, obj: dict) -> None:
    """Atomic write that skips files whose content (ignoring generatedAt) did not change.

    Keeps the data branch quiet: a commit (and a GitHub Pages build, soft-limited to
    10/hour) only happens when results actually change, and generatedAt means
    "data last changed", which clients use to reject stale mirror copies.
    """
    old = _read_json(path, None)
    if old is not None and {k: v for k, v in old.items() if k != "generatedAt"} == \
            {k: v for k, v in obj.items() if k != "generatedAt"}:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def _official_pdf(entry: dict) -> str | None:
    return next((s["url"] for s in entry.get("sources", []) if s["kind"] == "official"), None)


def merge(existing: dict | None, fast: Draw | None, gazette: Draw | None,
          gazette_url: str | None, warn, gazette_texts: list[str] | None = None) -> dict | None:
    """Combine what we already published with this run's reads. Pure function."""
    base: dict | None = existing
    if fast is not None:
        if base is not None and base.get("status") == "OK" and not base.get("ocr"):
            if Draw.from_json(base).numbers_key() != fast.numbers_key():
                warn(f"{fast.id}: Sambad file changed since last publish; using new file")
        prev_sources = [s for s in (base or {}).get("sources", []) if s["kind"] == "official"]
        was_verified = bool((existing or {}).get("verified")) and (existing or {}).get("status") == "OK" \
            and Draw.from_json(existing).numbers_key() == fast.numbers_key()
        prev_check = (existing or {}).get("officialCheck")
        base = fast.to_json() | {"status": "OK", "ocr": False}
        base["sources"] += prev_sources
        base["verified"] = was_verified
        if was_verified and prev_check:
            base["officialCheck"] = prev_check

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
        # No full OCR consensus (the usual case): confirm the fast numbers against the OCR text instead.
        if gazette is None and not base.get("verified") and gazette_texts and base.get("status") == "OK" \
                and not base.get("ocr") and base.get("format") == "D5":
            check = official.verify(Draw.from_json(base), gazette_texts)
            base["verified"] = check["top"]
            base["officialCheck"] = check
            if not check["firstPrize"] or check["ratio"] < 0.9:
                warn(f"{base['id']}: gazette OCR confirms only {check['ratio']:.0%} of numbers "
                     f"(1st prize {'ok' if check['firstPrize'] else 'NOT found'}); please compare by eye")
        if not any(s["kind"] == "official" for s in base["sources"]):
            base["sources"].append({"kind": "official", "url": gazette_url, "ocr": gazette is not None})
    return base


def pdf_only(slot_id: str, date: dt.date, url: str, kind: str = "official",
             name: str | None = None, special: bool = False) -> dict:
    slot = SLOTS.get(slot_id)
    return {"id": f"{date.isoformat()}_{slot_id}", "slot": slot_id, "date": date.isoformat(),
            "time": slot.time if slot else "18:00", "state": slot.state if slot else "",
            "name": name or (slot.label.upper() if slot else slot_id), "drawNo": None,
            "format": slot.scheme.ticket_format if slot else "B6",
            "price": slot.scheme.price if slot else 0, "special": special, "verified": False,
            "status": "PDF_ONLY", "ocr": False, "tiers": [],
            "sources": [{"kind": kind, "url": url, "ocr": False}]}


def _is_complete(entry: dict | None) -> bool:
    return entry is not None and entry.get("status") == "OK" and not entry.get("ocr")


# Seconds between requests for dates older than 3 days (a long backfill); 0 in tests.
PACE = float(os.environ.get("PIPELINE_PACE", "0"))


def run(out: str, today: dt.date, backfill_days: int, use_official: bool, deep: bool,
        now_ist: dt.datetime | None = None, ocr: bool = True, use_kerala: bool = False) -> int:
    warnings: list[str] = []
    now_ist = now_ist or dt.datetime.now(IST)

    def not_drawn_yet(date: dt.date, hhmm: str) -> bool:
        """No sheet can exist until a few minutes after the draw: don't hit the sources early."""
        h, m = map(int, hhmm.split(":"))
        return now_ist < dt.datetime.combine(date, dt.time(h, m), IST) + dt.timedelta(minutes=5)

    def warn(msg: str) -> None:
        msg = " ".join(str(msg).split())  # one line: text from source sites must not inject ::commands::
        warnings.append(msg)
        print(f"::warning::{msg}")

    index = _read_json(_v2(out, "index.json"), {})
    no_draw: set[str] = set(index.get("noDraw", []))
    upcoming = index.get("upcoming", [])

    # Special (bumper/monthly) draws we can see: today's listing, plus the catalog on deep runs.
    specials_seen: dict[tuple[str, str], dict] = {}
    try:
        for stem, title in sambad.listed_today():
            if stem[:2] not in {s.file_code for s in WEEKLY.values()}:
                specials_seen[(stem, today.isoformat())] = {"stem": stem, "name": title.upper(),
                                                            "date": today.isoformat()}
        if deep:
            for e in sambad.special_catalog():
                specials_seen[(e["stem"], e["date"])] = e
            upcoming = sambad.upcoming_specials(today)
    except RuntimeError as e:
        warn(f"Sambad listing unavailable: {e}")

    dates = [today - dt.timedelta(days=i) for i in range(backfill_days + 1)]
    for date in dates:
        day = load_day(out, date)
        codes = None
        if use_official:
            try:
                codes = official.list_codes(date)
            except RuntimeError as e:
                warn(f"official listing for {date} unavailable: {e}")
        for slot_id, slot in WEEKLY.items():
            key = f"{date.isoformat()}_{slot_id}"
            existing = day.get(key)
            if existing is not None and existing.get("status") == "PDF_ONLY" and not_drawn_yet(date, slot.time):
                del day[key]  # published too early by an older run (listed file, not yet uploaded)
                existing = None
            fast = None
            if slot.scheme.ticket_format == "D5" and not _is_complete(existing) \
                    and not not_drawn_yet(date, slot.time):
                try:
                    fast = sambad.fetch(slot_id, date, today)
                except ValidationError as e:
                    warn(f"{key}: Sambad file rejected: {e}")
                except Exception as e:  # network, pdftotext, a corrupt file…: skip this slot, keep publishing
                    warn(f"{key}: Sambad unavailable: {type(e).__name__}: {e}")
                if (today - date).days > 3:
                    time.sleep(PACE)   # filling in old dates: be gentle with the source
            gazette, gazette_url, gazette_texts = None, None, []
            already_checked = existing is not None and _official_pdf(existing) is not None
            # The official calendar lists the day's files before they are uploaded: never before the draw.
            if codes and slot.file_code in codes and not already_checked and not not_drawn_yet(date, slot.time):
                if ocr:
                    try:
                        gazette, gazette_url, gazette_texts = official.fetch(slot_id, date)
                    except Exception as e:  # incl. OCR tool errors and image decoder limits
                        warn(f"{key}: official fetch failed: {type(e).__name__}: {e}")
                else:  # link it without downloading, once the file is really there (listed != uploaded)
                    url = official.PDF_URL.format(code=slot.file_code, ddmmyy=date.strftime("%d%m%y"))
                    gazette_url = url if http.is_pdf(url) else None
            if existing is None and fast is None and gazette is None and gazette_url:
                day[key] = pdf_only(slot_id, date, gazette_url)
                continue
            merged = merge(existing, fast, gazette, gazette_url, warn, gazette_texts)
            if merged is not None:
                day[key] = merged

        # Kerala (3 PM; copies appear ~16:10 IST): re-read until every tier is confirmed, for two days at most.
        key = f"{date.isoformat()}_KL"
        existing = day.get(key)
        settled = existing is not None and all(t.get("confirmed", True) for t in existing.get("tiers", []))
        # Published but not fully confirmed: re-read once per run (deep pass), not on every minute's poll.
        if use_kerala and not settled and (existing is None or deep) and not not_drawn_yet(date, "16:00") \
                and (today - date).days <= 2:
            try:
                got = kerala.fetch(date, warn)
            except Exception as e:
                warn(f"{key}: Kerala read failed: {type(e).__name__}: {e}")
                got = None
            if got is not None:
                day[key] = got

        for (stem, d), sp in specials_seen.items():
            if d != date.isoformat():
                continue
            slot = next((s for s in SPECIALS.values() if s.file_code == stem), None)
            key = f"{d}_{slot.id if slot else stem}"
            if _is_complete(day.get(key)):
                continue
            draw = None
            if slot is not None:
                try:
                    draw = sambad.fetch(slot.id, date, today)
                except Exception as e:
                    warn(f"{key}: special sheet rejected: {type(e).__name__}: {e}")
            if draw is not None:
                day[key] = draw.to_json() | {"status": "OK", "ocr": False}
            else:
                if slot is None:
                    warn(f"{stem} ({sp['name']}) has no prize table in specials.json; published as sheet link only")
                day[key] = pdf_only(slot.id if slot else stem, date, sambad.OLD_URL.format(stem=stem),
                                    kind="lotterysambad", name=sp["name"], special=True)

        if date < today and codes == [] and not day:
            no_draw.add(date.isoformat())   # e.g. 02-10-2026: no draw on the official calendar
        if day:
            no_draw.discard(date.isoformat())
            order = {s: i for i, s in enumerate(SLOTS)}
            draws = sorted(day.values(), key=lambda x: (x["time"], order.get(x["slot"], 99)))
            _write_json(_day_path(out, date), {"schema": SCHEMA, "date": date.isoformat(), "draws": draws})

    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")

    def window(days: int) -> list[dict]:
        out_draws = []
        for i in range(days):
            d = load_day(out, today - dt.timedelta(days=i))
            out_draws += sorted(d.values(), key=lambda x: x["time"], reverse=True)
        return out_draws

    _write_json(_v2(out, "today.json"), {"schema": SCHEMA, "generatedAt": now,
                                          "date": today.isoformat(), "draws": window(2)})
    _write_json(_v2(out, "latest.json"), {"schema": SCHEMA, "generatedAt": now,
                                           "date": today.isoformat(), "draws": window(LATEST_DAYS)})
    # Number history: every published number of the last HISTORY_DAYS days, without sources.
    keep = ("id", "slot", "lottery", "date", "time", "state", "name", "drawNo", "format", "price", "special", "tiers")
    history = [{k: d[k] for k in keep if k in d} for d in window(HISTORY_DAYS) if d.get("status") == "OK" and d.get("tiers")]
    _write_json(_v2(out, "history.json"), {"schema": SCHEMA, "generatedAt": now, "days": HISTORY_DAYS,
                                            "draws": history})
    days_dir = _v2(out, "days")
    dates_with_data = sorted((f[:-5] for f in os.listdir(days_dir) if f.endswith(".json")), reverse=True) \
        if os.path.isdir(days_dir) else []
    _write_json(_v2(out, "index.json"), {"schema": SCHEMA, "generatedAt": now, "dates": dates_with_data,
                                          "noDraw": sorted(no_draw, reverse=True), "upcoming": upcoming})
    config = _read_json(CONFIG_TEMPLATE, {})
    config = {k: v for k, v in config.items() if not k.startswith("_")}
    _write_json(_v2(out, "config.json"), {"schema": SCHEMA, "generatedAt": now, **config})
    print(f"published {len(window(LATEST_DAYS))} draws in 7 days, {len(dates_with_data)} days archived, "
          f"{len(warnings)} warnings")
    return 0


def missing_slots(out: str, today: dt.date, required: list[str]) -> list[str]:
    """Required weekly slots that do not yet have numbers for today (used by the watch loop)."""
    day = load_day(out, today)
    return [s for s in required if day.get(f"{today.isoformat()}_{s}", {}).get("status") != "OK"]


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--out", required=True)
    p.add_argument("--date", help="IST date yyyy-mm-dd (default: today in IST)")
    p.add_argument("--backfill-days", type=int, default=1)
    p.add_argument("--no-official", action="store_true")
    p.add_argument("--no-ocr", action="store_true", help="link official PDFs without OCR verification")
    p.add_argument("--deep", action="store_true", help="also refresh special-draw catalog and upcoming list")
    p.add_argument("--no-kerala", action="store_true", help="skip Kerala State Lottery")
    p.add_argument("--require", default="", help="comma-separated slots; exit 3 while any has no result today")
    a = p.parse_args(argv)
    today = dt.date.fromisoformat(a.date) if a.date else dt.datetime.now(IST).date()
    now_ist = dt.datetime.now(IST)
    if a.date:  # replaying a past day: treat every draw of that day as done
        now_ist = dt.datetime.combine(today, dt.time(23, 59), IST)
    run(a.out, today, a.backfill_days, not a.no_official, a.deep, now_ist, ocr=not a.no_ocr, use_kerala=not a.no_kerala)
    missing = missing_slots(a.out, today, [s for s in a.require.split(",") if s])
    if missing:
        print(f"waiting for: {', '.join(missing)}")
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
