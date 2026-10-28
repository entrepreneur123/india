"""When the results workflow runs and what each run does.

GitHub starts scheduled runs late, and on busy days very late: on 27-10-2026 the 07:17 UTC run
started at 13:15 and the 12:47 and 14:17 runs had not started by 15:10, so the 8 PM result went
out 43 minutes late (by hand). Two defences:

* A late run does what is due *now*, not what its schedule was for (the 07:17 run starting at
  13:15 waits for the 8 PM draw instead of re-checking the 1 PM one).
* A relay: every results run ends by starting `relay.yml`, which sleeps until 15 minutes before
  the next draw and then starts the results run itself. Runs started that way (workflow_dispatch)
  begin within seconds; the cron schedules stay as a backup.

    python -m lottery_pipeline.schedule mode --event schedule --cron "17 7 * * *"
    python -m lottery_pipeline.schedule relay
Both print `key=value` lines for $GITHUB_OUTPUT. Times are UTC (IST = UTC + 5:30).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os

# (slot, polling starts, minutes to keep polling): the draw at 07:30, 10:30 (Kerala 3 PM: copies
# appear ~10:40), 12:30 and 14:30 UTC.
DRAWS = [("NL1PM", "07:35", 90), ("KL", "10:35", 90), ("SK6PM", "12:35", 90), ("NL8PM", "14:35", 90)]
# Each draw's run starts ~1 h 45 min early (and a backup 15 min before) and waits in the job.
CRON_SLOT = {"47 5 * * *": "NL1PM", "17 7 * * *": "NL1PM", "17 9 * * *": "KL", "47 10 * * *": "SK6PM",
             "17 12 * * *": "SK6PM", "47 12 * * *": "NL8PM", "17 14 * * *": "NL8PM"}
DEEP = {"10 15 * * *", "40 15 * * *", "10 16 * * *", "0 3 * * *"}   # gazette OCR / overnight re-check
WAKE_BEFORE = dt.timedelta(minutes=15)
# The site shows yesterday's results until 12:30 IST, then today's date with the draws to come
# (web/sitegen.py DAY_FLIP). The 1 PM run publishes that page at FLIP, so the relay starts it earlier.
FLIP = "07:00"
# The 8 PM run also reads the day's Maharashtra sheets (draws ~4-5 PM IST, ~2 min a sheet) before its
# draw is due, so it starts at 19:10 IST.
WAKE = {"NL1PM": "06:45", "NL8PM": "13:40"}
MAX_SLEEP = dt.timedelta(hours=5, minutes=40)                         # a job may run 6 h
LATE = dt.timedelta(minutes=45)                                       # a cron run later than this is "late"


def _at(day: dt.date, hhmm: str) -> dt.datetime:
    h, m = map(int, hhmm.split(":"))
    return dt.datetime(day.year, day.month, day.day, h, m, tzinfo=dt.timezone.utc)


def _draw(slot: str) -> dict:
    s, start, watch = next(d for d in DRAWS if d[0] == slot)
    return {"require": s, "from": start, "watch": watch, "deep": False, "ocr": False, "backfill": 1,
            "mh": s == "NL8PM",   # Maharashtra in the 8 PM run's idle wait
            "flip": FLIP if s == "NL1PM" else ""}


def _deep(backfill: int = 1) -> dict:
    return {"require": "", "from": "", "watch": 0, "deep": True, "ocr": True, "backfill": backfill, "mh": True, "flip": ""}


def due(now: dt.datetime, done: set[str] = frozenset()) -> dict:
    """What is due at `now`: the first draw not published yet whose polling window has not ended,
    else the evening gazette check."""
    for slot, start, watch in DRAWS:
        if slot not in done and now < _at(now.date(), start) + dt.timedelta(minutes=watch):
            return _draw(slot)
    return _deep()


def mode(now: dt.datetime, event: str, cron: str = "", target: str = "", done: set[str] = frozenset()) -> dict:
    """`done`: the slots already published today (from today.json)."""
    if target:                                   # started by the relay for this draw
        return _draw(target)
    if event == "workflow_dispatch":             # by hand: catalog refresh, all recent days
        return _deep()
    if event == "schedule" and cron:
        minute, hour = (int(x) for x in cron.split()[:2])
        planned = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
        if planned > now:                        # e.g. the 23:xx run of a schedule for 0 3: yesterday's
            planned -= dt.timedelta(days=1)
        if now - planned <= LATE:                # on time: do what it was scheduled for
            if cron in DEEP:
                return _deep(2 if cron == "0 3 * * *" else 1)
            if cron in CRON_SLOT:
                return _draw(CRON_SLOT[cron])
    return due(now, done)                        # late: do what is due now


def relay(now: dt.datetime) -> dict:
    """When to start the next results run, and for which draw. Longer waits than one job may last
    are split: the relay then starts another relay instead (`next=relay`)."""
    day = now.date()
    for d in (day, day + dt.timedelta(days=1)):
        for slot, start, _ in DRAWS:
            wake = _at(d, WAKE[slot]) if slot in WAKE else _at(d, start) - dt.timedelta(minutes=5) - WAKE_BEFORE
            if wake > now + dt.timedelta(minutes=2):
                if wake - now > MAX_SLEEP:
                    return {"sleep": int(MAX_SLEEP.total_seconds()), "next": "relay", "target": ""}
                return {"sleep": int((wake - now).total_seconds()), "next": "results", "target": slot}
    raise AssertionError("unreachable")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("what", choices=["mode", "relay"])
    ap.add_argument("--event", default="")
    ap.add_argument("--cron", default="")
    ap.add_argument("--target", default="")
    ap.add_argument("--today-feed", default="", help="v2/today.json: which of today's draws are out")
    a = ap.parse_args(argv)
    now = dt.datetime.now(dt.timezone.utc)
    done: set[str] = set()
    if a.today_feed and os.path.exists(a.today_feed):
        ist_day = (now + dt.timedelta(hours=5, minutes=30)).date().isoformat()
        feed = json.load(open(a.today_feed))
        done = {d["slot"] for d in feed.get("draws", []) if d.get("date") == ist_day and d.get("tiers")}
    out = mode(now, a.event, a.cron, a.target, done) if a.what == "mode" else relay(now)
    for k, v in out.items():
        print(f"{k}={str(v).lower() if isinstance(v, bool) else v}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
