"""Watches the live site for misses: a result that is late or missing, a stale feed, a number that
cannot be right, a page that shows the wrong thing, a failed run. Every miss becomes an incident
that stays open until the check passes again; the incident loop (CLAUDE.md) then finds its cause,
fixes it and adds a check or test so the same miss cannot happen silently twice.

    python -m lottery_pipeline.watch --site https://lottodost.com --out ../ops --runs runs.json

Writes incidents.json (open and recently resolved) and log.jsonl (every opening and resolution).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import urllib.request

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
# Draw time and how long after it a result may take before it counts as late (Dear sheets land
# 7-13 min after the draw; Kerala's copies ~1 h after its 3 PM draw).
DUE = {"NL1PM": ("13:00", 30), "SK6PM": ("18:00", 30), "NL8PM": ("20:00", 30), "KL": ("15:00", 150)}
FLIP = dt.time(12, 30)          # web/sitegen.py DAY_FLIP: the home page shows today's date from then on
MH_READ_HOURS = 30              # a Maharashtra sheet still a PDF link this long after its draw day began
_NUM = {"NL1PM": re.compile(r"^(\d{2}[A-L] )?\d{4,5}$"), "SK6PM": re.compile(r"^(\d{2}[A-L] )?\d{4,5}$"),
        "NL8PM": re.compile(r"^(\d{2}[A-L] )?\d{4,5}$"), "KL": re.compile(r"^([A-Z]{2} )?\d{4,6}$"),
        "MH": re.compile(r"^([A-Z]{2}-\d{2} )?\d{4}$")}


def get(url: str, timeout: int = 40) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def get_json(url: str):
    return json.loads(get(url))


def checks(site: str, now: dt.datetime, fetch=get_json, fetch_text=lambda u: get(u).decode("utf-8", "replace"),
           runs: list[dict] | None = None) -> dict[str, dict]:
    """Open problems right now: {incident id: {kind, severity, detail}}."""
    out: dict[str, dict] = {}

    def add(key, kind, severity, detail):
        out[key] = {"kind": kind, "severity": severity, "detail": detail}

    today = now.astimezone(IST).date()
    local = now.astimezone(IST)
    try:
        feed = fetch(f"{site}/v2/today.json")
        index = fetch(f"{site}/v2/index.json")
    except Exception as e:  # noqa: BLE001
        add("feed:unreachable", "unreachable", "high", f"today.json/index.json: {type(e).__name__}: {e}")
        return out
    gen = dt.datetime.fromisoformat(feed["generatedAt"])
    if now - gen > dt.timedelta(hours=26):
        add("feed:stale", "stale", "high", f"today.json last changed {feed['generatedAt']}")
    no_draw = set(index.get("noDraw", []))
    have = {(d["date"], d["slot"]): d for d in feed.get("draws", [])}
    for day in (today - dt.timedelta(days=1), today):   # yesterday too: a draw that never came (Kerala's Thursdays)
        for slot, (at, grace) in DUE.items():
            due = dt.datetime.combine(day, dt.time.fromisoformat(at), IST) + dt.timedelta(minutes=grace)
            if local < due or day.isoformat() in no_draw:
                continue
            d = have.get((day.isoformat(), slot))
            if d is None or not d.get("tiers"):
                late = int((local - due).total_seconds() // 60) + grace
                add(f"late:{day}:{slot}", "late", "high", f"{slot} {day}: not on the site {late} min after its draw")
    # numbers that cannot be right
    for d in feed.get("draws", []):
        pat = _NUM.get(d["slot"])
        for t in d.get("tiers") or []:
            nums = t.get("numbers", [])
            if len(set(nums)) != len(nums):
                add(f"bad:{d['id']}:dup", "wrong", "high", f"{d['id']} tier {t.get('rank')}: a number twice")
            odd = [n for n in nums if pat and not pat.match(n)]
            if odd:
                add(f"bad:{d['id']}:format", "wrong", "high", f"{d['id']} tier {t.get('rank')}: odd numbers {odd[:3]}")
    # the home page turns to today's date at 12:30 IST
    try:
        home = fetch_text(f"{site}/")
        title = re.search(r"<title>(.*?)</title>", home, re.S)
        want = today.strftime("%d.%m.%Y")
        if local.time() >= dt.time(12, 45) and (not title or want not in title.group(1)):
            add(f"home:{today}:date", "wrong", "medium", f"home title {title.group(1) if title else None!r} lacks {want}")
    except Exception as e:  # noqa: BLE001
        add("home:unreachable", "unreachable", "high", f"home page: {type(e).__name__}: {e}")
    # Maharashtra: sheets left as PDF links, and the hub's English title
    for back in (1, 2):
        day = today - dt.timedelta(days=back)
        try:
            mh = fetch(f"{site}/v2/mh/days/{day}.json")
        except Exception:  # noqa: BLE001 - no sheets that day
            continue
        if local - dt.datetime.combine(day, dt.time(0), IST) < dt.timedelta(hours=MH_READ_HOURS):
            continue
        for d in mh.get("draws", []):
            game = d["name"].removeprefix("Maharashtra ")
            if not re.fullmatch(r"[A-Z][a-z]+( [A-Z][a-z]+){0,3}", game):   # a circular, or a sheet with no English name
                add(f"mh-name:{d['id']}", "wrong", "medium", f"Maharashtra entry {d['date']} does not look like a game: {game[:60]!r}")
            if d.get("status") != "OK":
                add(f"mh:{d['date']}:{d['name']}", "unread", "low",
                    f"{d['name']} {d['date']}: still a PDF link (OCR tries {d.get('ocrTries', 0)})")
            pat = _NUM["MH"]
            for t in d.get("tiers") or []:
                if any(not pat.match(n) for n in t.get("numbers", [])) or len(set(t["numbers"])) != len(t["numbers"]):
                    add(f"bad:{d['id']}", "wrong", "high", f"{d['id']} tier {t.get('rank')}: odd or repeated number")
    try:
        hub = fetch_text(f"{site}/maharashtra/")
        t = re.search(r"<title>(.*?)</title>", hub, re.S)
        if t and re.search(r"[ऀ-ॿ]", t.group(1)):
            add("mh:hub-title", "wrong", "medium", f"/maharashtra/ English title has Marathi text: {t.group(1)[:80]!r}")
    except Exception:  # noqa: BLE001
        pass
    # failed results runs (from `gh run list --json`)
    for r in runs or []:
        if r.get("conclusion") == "failure":
            add(f"run:{r['databaseId']}", "failed-run", "high", f"results run {r['databaseId']} failed ({r.get('createdAt')})")
    return out


def update(out_dir: pathlib.Path, found: dict[str, dict], now: dt.datetime) -> tuple[list[str], list[str]]:
    """Merge into incidents.json; returns (newly opened, resolved) ids."""
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / "incidents.json"
    state = json.loads(path.read_text()) if path.exists() else {"open": {}, "resolved": {}}
    stamp = now.isoformat(timespec="minutes")
    opened, resolved = [], []
    before = json.dumps(state, sort_keys=True)
    for k, v in found.items():
        if k not in state["open"]:   # an open one is left as first seen: the file changes only when something does
            state["open"][k] = dict(v, first_seen=stamp)
            opened.append(k)
    for k in [k for k in state["open"] if k not in found]:
        inc = state["open"].pop(k)
        inc["resolved"] = stamp
        state["resolved"][k] = inc
        resolved.append(k)
    cutoff = (now - dt.timedelta(days=14)).isoformat()
    state["resolved"] = {k: v for k, v in state["resolved"].items() if v["resolved"] >= cutoff}
    if json.dumps(state, sort_keys=True) != before:
        path.write_text(json.dumps(state, ensure_ascii=False, indent=1))
    with (out_dir / "log.jsonl").open("a") as f:
        for k in opened:
            f.write(json.dumps({"at": stamp, "event": "open", "id": k, **found[k]}, ensure_ascii=False) + "\n")
        for k in resolved:
            f.write(json.dumps({"at": stamp, "event": "resolved", "id": k}, ensure_ascii=False) + "\n")
    return opened, resolved


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--site", default="https://lottodost.com")
    ap.add_argument("--out", type=pathlib.Path, required=True)
    ap.add_argument("--runs", type=pathlib.Path, help="gh run list --json databaseId,conclusion,createdAt output")
    a = ap.parse_args()
    runs = json.loads(a.runs.read_text()) if a.runs and a.runs.exists() else []
    now = dt.datetime.now(dt.timezone.utc)
    found = checks(a.site.rstrip("/"), now, runs=runs)
    opened, resolved = update(a.out, found, now)
    for k in opened:
        print(f"::warning::incident opened: {k}: {found[k]['detail']}")
    print(f"{len(found)} open ({len(opened)} new), {len(resolved)} resolved")
    return 0


if __name__ == "__main__":
    sys.exit(main())
