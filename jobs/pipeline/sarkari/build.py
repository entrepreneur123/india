"""Fetch SSC notices and publish static JSON for the job-alert app and website.

  python -m sarkari.build --out ../public [--window-days 120] [--max-pages 4] [--force]

Output (static files, served from any CDN), schema 1:
  jobs/v1/feed.json             notices/results/admit cards/answer keys, newest first
  jobs/v1/calendar.json         SSC's tentative calendar (application windows, exam months)
  jobs/v1/resources.json        links to SSC's syllabus, vacancy, previous-paper, certificate PDFs
  jobs/v1/exams.json            exam families: names (en/hi), qualification from the latest Notice
  jobs/v1/guide.json            how to apply, documents, helplines, skill-test standards (ssc_guide.json)
  jobs/v1/rules/index.json      exams with an eligibility checker
  jobs/v1/rules/<id>.json       copied from rules/ after validation
  jobs/v1/new.json              items that were not in the previous feed (for push alerts)
  jobs/v1/results/              roll-number lookup for result lists (results_index.py; needs pdftotext)
  jobs/v1/results/cutoffs.json  pages of the result write-ups that give the cut-off marks (cutoffs.py)
  jobs/exam/<code>/index.html   one static page per exam for search engines (sitegen.py), + jobs/sitemap.xml
  jobs/v1/site.json             public links of this site (the Telegram channel, if one is set)
  jobs/v1/status.json           last full check and SSC's lastUpdates stamp (changes on full checks only)

Publishing rules (a wrong date or a missing notice is worse than a late one):
  * Every listing is fetched in full before anything is written. Any failure exits 1 and
    the previous files stay as they are.
  * A listing that had items before and is empty now, or whose records stop validating,
    is treated as an API change, not as "SSC deleted everything".
  * Unchanged content is not rewritten, so the data branch only gets a commit when
    something real changed.
  * If SSC's lastUpdates stamp is unchanged and the last full check is under
    FULL_CHECK_HOURS old, the run stops after one small request (other boards are then
    checked on the next full check, at most FULL_CHECK_HOURS later).
  * Other boards (upprpb.py, hssc.py) are isolated: if one fails, its items from the previous feed
    are kept, a warning is printed and status.json names it; SSC is still published.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import sys

from . import classify, cutoffs, eligibility, hssc, http, results_index, sitegen, ssc, upprpb

SCHEMA = 1
FEED_MAX_ITEMS = 400
NEW_MAX_ITEMS = 20  # a first run or a long outage must not fire 400 push notifications
# The API's page size changes without notice; a bigger page brings older records into the
# window. Those are not news, so only recent items can be "new".
NEW_MAX_AGE = dt.timedelta(days=3)
# lastUpdates follows the notice board; results and answer keys may be posted without
# touching it, so a full check still runs every few hours.
FULL_CHECK_HOURS = 3


def _v1(out: str, *parts: str) -> str:
    return os.path.join(out, "jobs", "v1", *parts)


def _read_json(path: str, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def _write_json(path: str, obj: dict, volatile: tuple[str, ...] = ("generatedAt",)) -> bool:
    """Atomic write; skipped when nothing but `volatile` keys changed. True if written."""
    old = _read_json(path, None)
    if old is not None and {k: v for k, v in old.items() if k not in volatile} == \
            {k: v for k, v in obj.items() if k not in volatile}:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)
    return True


FAMILIES = os.path.join(os.path.dirname(__file__), "..", "exam_families.json")
GUIDE = os.path.join(os.path.dirname(__file__), "..", "ssc_guide.json")


def load_guide() -> dict:
    """How to apply / documents / helplines / skill tests; checked so a bad edit stops the run."""
    g = _read_json(GUIDE, None)
    if not g or not g.get("steps") or not g.get("documents") or not g.get("helplines", {}).get("list"):
        raise ssc.FormatChanged("ssc_guide.json is missing steps, documents or helplines")
    for entry in g["steps"] + g["documents"]:
        if not (entry.get("en") and entry.get("hi") and entry.get("ref")):
            raise ssc.FormatChanged(f"ssc_guide.json entry without en/hi/ref: {entry}")
    for t in g["skillTests"]["tests"]:
        if not (t["minutes"] > 0 and t["kdph"] > 0):
            raise ssc.FormatChanged(f"ssc_guide.json skill test {t.get('id')}")
    return g


def collect(get: ssc.Getter, now: dt.datetime, window_days: int, max_pages: int,
            previous_kinds: dict[str, int]) -> tuple[list[ssc.Item], list[dict], dict[str, int], dict]:
    exams = ssc.exam_index(get(ssc.EXAMS))
    cutoff = now - dt.timedelta(days=window_days)
    streams = []
    for content_type, language, kind in ssc.STREAMS:
        items, returned = ssc.fetch_stream(get, content_type, language, kind, exams, now, cutoff, max_pages)
        key = f"{content_type}/{language or 'all'}"
        # A listing can lose all items to the time window (no answer key for 4 months); that is
        # fine. The API returning nothing at all for a listing that had records is not.
        if not returned and previous_kinds.get(key, 0) > 0:
            raise ssc.FormatChanged(f"{key}: API returned no records, {previous_kinds[key]} before")
        streams.append((key, items, returned))
    calendar = ssc.parse_calendar(get(ssc.calendar_url()), exams)
    merged = ssc.merge([s for _, s, _ in streams])[:FEED_MAX_ITEMS]
    if not merged:
        raise ssc.FormatChanged("no items at all")
    resources = ssc.fetch_resources(get, exams, now)
    return merged, calendar, {k: n for k, _, n in streams}, resources


def publish_rules(out: str, all_rules: list[dict], today: dt.date) -> list[dict]:
    index = []
    for rules in all_rules:
        _write_json(_v1(out, "rules", f"{rules['id']}.json"), rules)
        index.append({
            "id": rules["id"], "exam": rules["exam"], "examYear": rules["examYear"], "title": rules["title"],
            "applyFrom": rules["dates"]["applyFrom"], "applyTo": rules["dates"]["applyTo"],
            "application": eligibility.application_status(rules, today),
        })
    _write_json(_v1(out, "rules", "index.json"), {"schema": SCHEMA, "exams": index})
    return index


def new_items(old: list[dict], items: list[ssc.Item], now: dt.datetime) -> list[dict]:
    """Items to announce: not in the previous feed (by id, or by title and day, since the
    merged copy of a notice can switch between listings), and published recently.
    A first run (no previous feed) announces nothing."""
    if not old:
        return []
    seen_ids = {i["id"] for i in old}
    seen_keys = {(i["title"].casefold(), i["publishedAt"][:10]) for i in old}
    out = []
    for item in items:
        key = (item.title.casefold(), item.published.date().isoformat())
        if item.id not in seen_ids and key not in seen_keys and now - item.published <= NEW_MAX_AGE:
            out.append(item.to_json())
    return out[:NEW_MAX_ITEMS]


BOARDS = (upprpb, hssc)  # besides SSC; each has SOURCE, NOTICE_PAGE and fetch(get_text, now, days, warn)


def other_boards(get_text, now: dt.datetime, window_days: int, old_feed: list[dict],
                 warn=print) -> tuple[list[ssc.Item], list[dict], list[str]]:
    """(fresh items, previous items kept for a board that failed or wasn't asked, failed boards).
    One board failing never stops the others or SSC."""
    cutoff = (now - dt.timedelta(days=window_days)).isoformat(timespec="seconds")
    fresh, kept, failed = [], [], []
    for board in BOARDS:
        previous = [i for i in old_feed if i.get("source") == board.SOURCE and i["publishedAt"] >= cutoff]
        if get_text is None:
            kept += previous
            continue
        try:
            fresh += board.fetch(get_text, now, window_days, warn)
        except (http.FetchError, ssc.FormatChanged, UnicodeDecodeError) as e:
            warn(f"::warning::{board.SOURCE}: {e}; keeping its previous items")
            kept += previous
            failed.append(board.SOURCE)
    return fresh, kept, failed


_CHANNEL = re.compile(r"^@([A-Za-z][A-Za-z0-9_]{4,31})$")  # a public Telegram username


def site_info(channel: str | None) -> dict:
    """Only a public @channel becomes a link; a numeric chat id (a private chat) is never shown."""
    m = _CHANNEL.match((channel or "").strip())
    return {"schema": SCHEMA, **({"telegram": f"https://t.me/{m.group(1)}"} if m else {})}


def run(out: str, get: ssc.Getter, now: dt.datetime, window_days: int = 120, max_pages: int = 4,
        force: bool = False, get_bytes=None, site_url: str | None = None, get_text=None,
        channel: str | None = None) -> int:
    status_path = _v1(out, "status.json")
    status = _read_json(status_path, {})
    stamp = ssc.last_update(get(ssc.LAST_UPDATES))
    last_full = status.get("fullCheckAt")
    if not force and stamp == status.get("sourceUpdatedAt") and last_full and \
            now - dt.datetime.fromisoformat(last_full) < dt.timedelta(hours=FULL_CHECK_HOURS):
        # Nothing is written: a file changed on every run would mean a commit and a Pages build
        # every 30 minutes for no news.
        print(f"ssc: unchanged since {stamp}; last full check {last_full}")
        return 0

    all_rules = eligibility.load_all()  # a broken rules file stops the run before anything is written
    guide = load_guide()
    old_feed = _read_json(_v1(out, "feed.json"), {"items": []})
    families = _read_json(FAMILIES, None)
    if not families or set(families["families"]) != set(classify.EXAM_FAMILIES):
        raise ssc.FormatChanged("exam_families.json does not list exactly classify.EXAM_FAMILIES")
    items, calendar, counts, resources = collect(get, now, window_days, max_pages, status.get("streamCounts", {}))
    others, kept, failed = other_boards(get_text, now, window_days, old_feed["items"])
    items = sorted(items + others, key=lambda i: (i.published, i.id), reverse=True)
    feed = sorted([i.to_json() for i in items] + kept, key=lambda i: (i["publishedAt"], i["id"]), reverse=True)
    new = new_items(old_feed["items"], items, now)

    stamp_now = now.isoformat(timespec="seconds")
    changed = _write_json(_v1(out, "feed.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "source": "ssc",
                                                  "sourcePage": ssc.NOTICE_BOARD_PAGE,
                                                  "sources": {"ssc": ssc.NOTICE_BOARD_PAGE,
                                                              **{x.SOURCE: x.NOTICE_PAGE for x in BOARDS}},
                                                  "items": feed})
    _write_json(_v1(out, "calendar.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "source": "ssc",
                                            "entries": calendar})
    _write_json(_v1(out, "new.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "items": new})
    _write_json(_v1(out, "resources.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "source": "ssc",
                                             "sections": resources})
    _write_json(_v1(out, "exams.json"), {"schema": SCHEMA, "families": families["families"]})
    _write_json(_v1(out, "site.json"), site_info(channel))
    _write_json(_v1(out, "guide.json"), {"schema": SCHEMA, **{k: v for k, v in guide.items() if k != "_comment"}})
    publish_rules(out, all_rules, now.date())
    sitegen.build(out, families["families"], all_rules, feed, now.date(), site_url)
    if get_bytes is not None and not shutil.which("pdftotext"):
        print("::warning::pdftotext (poppler-utils) is not installed: result lists and cut-offs skipped")
    elif get_bytes is not None:
        added = results_index.update(out, feed, get_bytes)
        print(f"results: {added} new roll-number lists")
        print(f"cut-offs: {cutoffs.update(out, feed, get_bytes)} write-ups read")
    _write_json(status_path, {"schema": SCHEMA, "fullCheckAt": stamp_now,
                              "sourceUpdatedAt": stamp, "streamCounts": counts, "failedSources": failed}, volatile=())
    print(f"feed: {len(feed)} items ({len(new)} new, {len(others) + len(kept)} from other boards), {len(calendar)} calendar entries"
          f"{'' if changed else ', feed unchanged'}")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--window-days", type=int, default=120)
    ap.add_argument("--max-pages", type=int, default=4)
    ap.add_argument("--force", action="store_true", help="full check even if lastUpdates is unchanged")
    ap.add_argument("--site-url", help="public address, for canonical links and jobs/sitemap.xml")
    ap.add_argument("--no-results", action="store_true", help="skip roll-number indexing of result PDFs")
    args = ap.parse_args(argv)
    try:
        return run(args.out, http.get_json, dt.datetime.now(ssc.IST), args.window_days, args.max_pages, args.force,
                   get_bytes=None if args.no_results else http.get_bytes, site_url=args.site_url,
                   get_text=lambda url: http.get_bytes(url, timeout=40, max_bytes=5_000_000).decode("utf-8"),
                   channel=os.environ.get("JOBS_TELEGRAM_CHAT_ID"))
    except (http.FetchError, ssc.FormatChanged, eligibility.RulesError) as e:
        print(f"::error::{e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
