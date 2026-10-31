"""The owner's daily report, from what the workflows already keep (no secrets needed here).

    python3 tools/daily_report.py [--date YYYY-MM-DD]     (default: yesterday in India)

- On time?  For every draw of the day: when its numbers first reached the `data` branch (git history of
  v2/days/<date>.json) against the watcher's allowance (pipeline/lottery_pipeline/watch.py DUE). The site
  deploy follows a data commit by ~4 min. Maharashtra: sheets read as numbers vs. left as PDF links.
- Search Console (seo:gsc/latest.md, lags 1-3 days) and visitors (seo:cf/latest.md, Cloudflare Web Analytics).
- Misses: incidents opened/resolved in the last 24 h (ops:ops/log.jsonl), still open ones, failed workflow runs.
- Live: the watcher's checks against lottodost.com right now.
Revenue is not readable here: AdSense has no API access for this project (the owner sees it in AdSense).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "pipeline"))
from lottery_pipeline.watch import DUE  # noqa: E402

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))


def git(*args: str) -> str:
    p = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    return p.stdout if p.returncode == 0 else ""


def show(ref_path: str) -> str:
    return git("show", ref_path)


def first_published(date: str) -> dict[str, dt.datetime]:
    """slot -> time its numbers first appeared on the data branch."""
    path = f"v2/days/{date}.json"
    seen: dict[str, dt.datetime] = {}
    for line in git("log", "--reverse", "--format=%H %cI", "origin/data", "--", path).split("\n"):
        if not line.strip():
            continue
        sha, when = line.split()
        try:
            day = json.loads(show(f"{sha}:{path}"))
        except ValueError:
            continue
        for d in day.get("draws", []):
            if d.get("slot") not in seen and any(t.get("numbers") for t in d.get("tiers") or []):
                seen[d["slot"]] = dt.datetime.fromisoformat(when)
    return seen


def timeliness(date: str) -> list[str]:
    seen = first_published(date)
    out = ["| draw | due (IST) | numbers on data branch (IST) | after draw | ok? |", "|---|---|---|---|---|"]
    for slot, (at, allow) in DUE.items():
        draw = dt.datetime.fromisoformat(f"{date}T{at}:00").replace(tzinfo=IST)
        got = seen.get(slot)
        if got is None:
            out.append(f"| {slot} | {at} | not published | – | **NO** |")
            continue
        mins = round((got - draw).total_seconds() / 60)
        out.append(f"| {slot} | {at} | {got.astimezone(IST):%H:%M} | {mins} min | {'yes' if mins <= allow else '**LATE**'} |")
    mh = show(f"origin/data:v2/mh/days/{date}.json")
    if mh:
        draws = json.loads(mh).get("draws", [])
        read = [d["name"] for d in draws if any(t.get("numbers") for t in d.get("tiers") or [])]
        pdf = [d["name"].replace("Maharashtra ", "") for d in draws if d["name"] not in read]
        out.append(f"\nMaharashtra: {len(draws)} draws, {len(read)} read as numbers"
                   + (f"; PDF link only: {', '.join(pdf)}" if pdf else ""))
    return out


def section(md: str, title: str, rows: int) -> list[str]:
    m = re.search(rf"## {re.escape(title)}[^\n]*\n(.*?)(?:\n## |\Z)", md, re.S)
    if not m:
        return []
    lines = [l for l in m.group(1).strip().split("\n") if l.strip()]
    return lines[:2] + lines[2:][-rows:] if title == "Daily" else lines[:2 + rows]


def search_and_visitors() -> list[str]:
    git("fetch", "-q", "origin", "seo")
    gsc, cf = show("origin/seo:gsc/latest.md"), show("origin/seo:cf/latest.md")
    out = ["### Search Console (lags 1-3 days)"]
    if gsc:
        out += ([gsc.split("\n")[0].lstrip("# "), ""] + section(gsc, "Daily", 4) + [""] + section(gsc, "Countries", 1)
                + ["", "Target queries (India):"] + section(gsc, "India: target queries", 8))
    else:
        out.append("no report on the seo branch")
    out += ["", "### Visitors (Cloudflare Web Analytics)"]
    if "Not read" in cf or not cf:
        out.append("not readable: " + (re.search(r"Not read: (.*)", cf).group(1) if cf else "no report yet")
                   + " - owner action: give the Cloudflare API token \"Account Analytics: Read\"")
    else:
        out += section(cf, "Daily", 4) + [""] + section(cf, "Countries", 3)
    return out


def misses(now: dt.datetime) -> list[str]:
    git("fetch", "-q", "origin", "ops")
    since = now - dt.timedelta(hours=24)
    events = []
    for line in show("origin/ops:ops/log.jsonl").split("\n"):
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if dt.datetime.fromisoformat(e["at"]) >= since:
            events.append(f"- {e['at'][11:16]} UTC {e['event']}: {e['id']}" + (f" [{e.get('severity')}]" if e.get("severity") else ""))
    state = json.loads(show("origin/ops:ops/incidents.json") or "{}")
    still = [f"- {k} [{v['severity']}] {v['detail']}" for k, v in state.get("open", {}).items()]
    runs = subprocess.run(["gh", "run", "list", "-L", "100", "--json", "workflowName,conclusion,createdAt,url"],
                          cwd=ROOT, capture_output=True, text=True)
    failed = []
    if runs.returncode == 0:
        for r in json.loads(runs.stdout):
            if r["conclusion"] in ("failure", "timed_out") and dt.datetime.fromisoformat(r["createdAt"].replace("Z", "+00:00")) >= since:
                failed.append(f"- {r['workflowName']} {r['createdAt'][11:16]} UTC {r['url']}")
    else:
        failed.append("- (gh run list not available: " + runs.stderr.strip()[:120] + ")")
    return (["### Incidents in the last 24 h"] + (events or ["none"]) + ["", "### Open now"] + (still or ["none"])
            + ["", "### Failed workflow runs in the last 24 h"] + (failed or ["none"]))


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser()
    now = dt.datetime.now(dt.timezone.utc)
    ap.add_argument("--date", default=(now.astimezone(IST).date() - dt.timedelta(days=1)).isoformat())
    a = ap.parse_args(argv)
    git("fetch", "-q", "origin", "data")
    from harness import live  # noqa: E402  (tools/ is on sys.path when run as a script)
    found = live()
    print(f"# LottoDost daily report for {a.date} (made {now:%Y-%m-%d %H:%M} UTC)\n")
    print("## On time?\n" + "\n".join(timeliness(a.date)) + "\n")
    print("## Search and visitors\n" + "\n".join(search_and_visitors()) + "\n")
    print("## Misses\n" + "\n".join(misses(now)) + "\n")
    print("## Live check now\n" + ("\n".join(f"- {f}" for f in found) or "clean") + "\n")
    print("## Revenue\nAdSense revenue is not readable from here (no API access); see the AdSense dashboard.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
