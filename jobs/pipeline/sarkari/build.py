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
  jobs/v1/india.json            written by the India server (sarkari/india.py), read here
  jobs/v1/latest.json           the newest LATEST items of feed.json, for the home page
  jobs/v1/new.json              items that were not in the previous feed (for push alerts)
  jobs/v1/results/              roll-number lookup for result lists (results_index.py; needs pdftotext)
  jobs/v1/results/cutoffs.json  pages of the result write-ups that give the cut-off marks (cutoffs.py)
  jobs/exam/<code>/index.html   one static page per exam for search engines (sitegen.py), + jobs/sitemap.xml
  jobs/v1/site.json             public links of this site (Telegram channel, push server, if set)
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

from . import boards, classify, cutoffs, eligibility, hssc, http, results_index, sitegen, sources, ssc, upprpb, whatsapp_post

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


def _write_text(path: str, text: str) -> bool:
    try:
        with open(path, encoding="utf-8") as f:
            if f.read() == text:
                return False
    except FileNotFoundError:
        pass
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path + ".tmp", "w", encoding="utf-8") as f:
        f.write(text)
    os.replace(path + ".tmp", path)
    return True


FAMILIES = os.path.join(os.path.dirname(__file__), "..", "exam_families.json")
GUIDE = os.path.join(os.path.dirname(__file__), "..", "ssc_guide.json")
EARN = os.path.join(os.path.dirname(__file__), "..", "earn.json")


def load_earn() -> dict:
    """Ways to earn besides government jobs (earn.json); checked so a bad edit stops the run."""
    e = _read_json(EARN, None)
    ids = [o["id"] for g in (e or {}).get("groups", []) for o in g["options"]]
    if (not e or not ids or len(ids) != len(set(ids)) or not e.get("warning", {}).get("signs")
            or any(not o["url"].startswith("https://") or o["role"] not in sitegen.ROLE_EN
                   for g in e["groups"] for o in g["options"])):
        raise ssc.FormatChanged("earn.json is incomplete")
    return e


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
            # The home screen lists exam dates from this alone, without fetching every rules file.
            "examSchedule": rules.get("examSchedule", []),
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


BOARDS = (upprpb, hssc, *boards.ALL)  # besides SSC; each has SOURCE, NOTICE_PAGE and fetch(get_text, now, days, warn)


def other_boards(get_text, now: dt.datetime, window_days: int, old_feed: list[dict],
                 warn=print, india: dict | None = None) -> tuple[list[ssc.Item], list[dict], list[str]]:
    """(fresh items, previous items kept for a board that failed or wasn't asked, failed boards).
    One board failing never stops the others or SSC. Boards that answer only from India
    (boards.INDIA_ONLY) are read by the India server (sarkari/india.py): their items come
    from its india.json, or stay as they were while there is none."""
    cutoff = (now - dt.timedelta(days=window_days)).isoformat(timespec="seconds")
    fresh, kept, failed = [], [], []
    for board in BOARDS:
        previous = [i for i in old_feed if i.get("source") == board.SOURCE and i["publishedAt"] >= cutoff]
        if board.SOURCE in boards.INDIA_ONLY:
            kept += [i for i in india["items"] if i.get("source") == board.SOURCE and i["publishedAt"] >= cutoff] \
                if india else previous
            continue
        if get_text is None:
            kept += previous
            continue
        try:
            fresh += board.fetch(get_text, now, window_days, warn)
        except Exception as e:  # noqa: BLE001 - whatever breaks in one board, SSC and the others still publish
            warn(f"::warning::{board.SOURCE}: {type(e).__name__}: {e}; keeping its previous items")
            kept += previous
            failed.append(board.SOURCE)
    return fresh, kept, failed


_CHANNEL = re.compile(r"^@([A-Za-z][A-Za-z0-9_]{4,31})$")  # a public Telegram username


_PUSH_URL = re.compile(r"^https://[a-z0-9.-]+(:\d{2,5})?/push$")
_PUSH_KEY = re.compile(r"^[A-Za-z0-9_-]{87}$")  # 65-byte P-256 public key, base64url without padding


_WHATSAPP = re.compile(r"^https://(www\.)?whatsapp\.com/channel/[A-Za-z0-9]{10,40}$")  # a WhatsApp Channel's invite link
LATEST = 40
# Home's "most searched" groups, in the order of how many people apply (research/ROUTES_2026-10.md and the 30-10-2026
# update: RRB NTPC 1.22 crore and Group D 1.08 crore applicants, then SSC GD/CGL/CHSL, state police, CTET and teacher
# exams, bank clerk/PO, Agniveer and CAPF, state PSCs, India Post and PSUs). Each shows its newest important updates.
TOP_SECTORS = ("railway", "ssc", "police", "teaching", "bank", "defence", "state", "psu")
TOP_PER_SECTOR = 3
TOP_DAYS = 60
TOP_KINDS = ("recruitment", "admit_card", "result", "answer_key")  # what people come for; the rest only fills gaps
# A board whose family is in several sectors (DSSSB: state and teaching) counts for a group other than its first
# sector only when the headline says so ("TGT", "Assistant Teacher"), so the teacher group shows teacher notices.
TOP_WORDS = {"teaching": re.compile(r"\b(teachers?|tgt|pgt|prt|lecturers?|professors?|[a-z]*tet|principal)\b", re.I),
             "police": re.compile(r"\b(police|constable|sub[- ]inspector|si|warder|fireman)\b", re.I),
             "defence": re.compile(r"\b(agniveer|army|navy|air force|rifleman|capf|bsf|crpf|cisf|itbp|ssb)\b", re.I)}


def _exam_head(title: str) -> str:
    """The exam a headline is about, to show one update per exam: "SI in Delhi Police ...: list 1/2/3" and
    "TAMIL NADU TET ... – Notification" / "TAMILNADU TET ... - Certificates" each give one key. A short part
    before the colon ("Result notice: Staff Nurse", "RRB CEN 08/2025: E-Call Letters") is not an exam name."""
    key = lambda text: re.sub(r"[^a-z0-9]", "", text.lower())
    head = key(re.split(r"[:—–]|\s-\s", title, maxsplit=1)[0])
    return (head if len(head) >= 20 else key(title))[:60]


def top_groups(feed: list[dict], families: dict, now: dt.datetime) -> list[dict]:
    """[{"sector", "items"}] for the home page: per sector, its newest recruitment/admit card/result/answer key
    notices of the last TOP_DAYS days (others only if too few), each notice in one group only."""
    since = (now - dt.timedelta(days=TOP_DAYS)).isoformat(timespec="seconds")
    taken: set[str] = set()
    seen_heads: set[str] = set()  # across groups too: SSC's SI list is not shown again under police
    out = []
    for sector in TOP_SECTORS:
        def belongs(i):
            secs = families.get(i.get("exam") or "", {}).get("sectors") or (["ssc"] if (i.get("source") or "ssc") == "ssc" else [])
            if sector not in secs:
                return False
            return secs[0] == sector or sector not in TOP_WORDS or bool(TOP_WORDS[sector].search(i["title"]))
        pool = [i for i in feed if i["publishedAt"] >= since and i.get("exam") != "LDCE" and i["id"] not in taken
                and ((i.get("source") or "ssc") != "ssc" or i.get("lang") == "en")  # SSC posts each notice twice (en/hi)
                and belongs(i)]
        pick = []
        for i in [i for i in pool if i["kind"] in TOP_KINDS] + [i for i in pool if i["kind"] not in TOP_KINDS]:
            head = _exam_head(i["title"])
            if head not in seen_heads and len(pick) < TOP_PER_SECTOR:
                seen_heads.add(head)
                pick.append(i)
        pick.sort(key=lambda i: (i["publishedAt"], i["id"]), reverse=True)
        taken.update(i["id"] for i in pick)
        if pick:
            out.append({"sector": sector, "items": pick})
    return out
_EMAIL = re.compile(r"^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(\.[A-Za-z0-9-]{1,63})*\.[A-Za-z]{2,24}$")


def contact_email(value: str | None) -> str | None:
    """JOBS_CONTACT_EMAIL, shown on the contact page and in the app; anything else is left out."""
    v = (value or "").strip()
    return v if _EMAIL.match(v) else None


def site_info(channel: str | None, push_url: str | None = None, push_key: str | None = None,
              whatsapp: str | None = None, contact: str | None = None) -> dict:
    """Only a public @channel becomes a link; a numeric chat id (a private chat) is never shown.
    The push server (India server, sarkari.push) is announced only when both address and key look right."""
    m = _CHANNEL.match((channel or "").strip())
    out = {"schema": SCHEMA, **({"telegram": f"https://t.me/{m.group(1)}"} if m else {})}
    if whatsapp and _WHATSAPP.match(whatsapp.strip()):
        out["whatsapp"] = whatsapp.strip()
    if contact_email(contact):
        out["contact"] = contact_email(contact)
    if push_url and push_key and _PUSH_URL.match(push_url.strip()) and _PUSH_KEY.match(push_key.strip()):
        out["push"] = {"url": push_url.strip(), "key": push_key.strip()}
    return out


def needs_full_check(status: dict, stamp: str, now: dt.datetime, force: bool = False, india_at: str | None = None) -> bool:
    """india_at: generatedAt of the India server's india.json, which changes only with its content,
    so new notices from India reach the site on the next run, not at the next 3-hour check."""
    last_full = status.get("fullCheckAt")
    return force or stamp != status.get("sourceUpdatedAt") or not last_full or india_at != status.get("indiaAt") or \
        now - dt.datetime.fromisoformat(last_full) >= dt.timedelta(hours=FULL_CHECK_HOURS)


def rules_needed(feed: list[dict], all_rules: list[dict]) -> list[dict]:
    """New SSC recruitment notices with no rules file yet: the eligibility check can't cover them
    until someone copies the Notice into pipeline/rules/ (the slow, human step)."""
    have = {(r["exam"], r["examYear"]) for r in all_rules}
    out, seen = [], set()
    for i in feed:
        key = (i.get("exam"), i.get("examYear"))
        if i["kind"] == "recruitment" and i.get("source", "ssc") == "ssc" and i["lang"] == "en" and \
                key[0] and key[0] != "LDCE" and key[1] and key not in have and key not in seen:
            seen.add(key)
            out.append({"exam": key[0], "examYear": key[1], "title": i["title"], "publishedAt": i["publishedAt"],
                        "notice": next((f["url"] for f in i["files"]), None)})
    return out


def run(out: str, get: ssc.Getter, now: dt.datetime, window_days: int = 120, max_pages: int = 4,
        force: bool = False, get_bytes=None, site_url: str | None = None, get_text=None,
        channel: str | None = None, push: tuple[str | None, str | None] = (None, None),
        whatsapp: str | None = None, contact: str | None = None) -> int:
    status_path = _v1(out, "status.json")
    status = _read_json(status_path, {})
    stamp = ssc.last_update(get(ssc.LAST_UPDATES))
    india = _read_json(_v1(out, "india.json"), None)
    india_at = india.get("generatedAt") if india else None
    if not needs_full_check(status, stamp, now, force, india_at):
        # Nothing is written: a file changed on every run would mean a commit and a Pages build
        # every 30 minutes for no news.
        print(f"ssc: unchanged since {stamp}; last full check {status.get('fullCheckAt')}")
        return 0

    all_rules = eligibility.load_all()  # a broken rules file stops the run before anything is written
    guide = load_guide()
    old_feed = _read_json(_v1(out, "feed.json"), {"items": []})
    families = _read_json(FAMILIES, None)
    if not families or set(families["families"]) != set(classify.EXAM_FAMILIES):
        raise ssc.FormatChanged("exam_families.json does not list exactly classify.EXAM_FAMILIES")
    items, calendar, counts, resources = collect(get, now, window_days, max_pages, status.get("streamCounts", {}))
    others, kept, failed = other_boards(get_text, now, window_days, old_feed["items"], india=india)
    items = sorted(items + others, key=lambda i: (i.published, i.id), reverse=True)
    feed = sorted([i.to_json() for i in items] + kept, key=lambda i: (i["publishedAt"], i["id"]), reverse=True)
    new = new_items(old_feed["items"], items, now)

    stamp_now = now.isoformat(timespec="seconds")
    changed = _write_json(_v1(out, "feed.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "source": "ssc",
                                                  "sourcePage": ssc.NOTICE_BOARD_PAGE,
                                                  "sources": {"ssc": ssc.NOTICE_BOARD_PAGE,
                                                              **{x.SOURCE: x.NOTICE_PAGE for x in BOARDS}},
                                                  "items": feed})
    # The home page shows the newest 8 after dropping duplicates; 40 leave room. A tenth of feed.json on a slow line.
    _write_json(_v1(out, "latest.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "items": feed[:LATEST],
                                          "top": top_groups(feed, families["families"], now)})
    _write_json(_v1(out, "calendar.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "source": "ssc",
                                            "entries": calendar})
    _write_json(_v1(out, "new.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "items": new})
    _write_json(_v1(out, "resources.json"), {"schema": SCHEMA, "generatedAt": stamp_now, "source": "ssc",
                                             "sections": resources})
    _write_json(_v1(out, "exams.json"), {"schema": SCHEMA, "families": families["families"], "sources": sources.SOURCES})
    _write_json(_v1(out, "site.json"), site_info(channel, *push, whatsapp=whatsapp, contact=contact))
    _write_text(_v1(out, "whatsapp.txt"), whatsapp_post.digest(feed, families["families"], now, site_url))
    _write_json(_v1(out, "guide.json"), {"schema": SCHEMA, **{k: v for k, v in guide.items() if k != "_comment"}})
    earn = load_earn()
    _write_json(_v1(out, "earn.json"), {"schema": SCHEMA, **{k: v for k, v in earn.items() if k != "_about"}})
    publish_rules(out, all_rules, now.date())
    sitegen.build(out, families["families"], all_rules, feed, now.date(), site_url, contact_email(contact), earn)
    if get_bytes is not None and not shutil.which("pdftotext"):
        print("::warning::pdftotext (poppler-utils) is not installed: result lists and cut-offs skipped")
    elif get_bytes is not None:
        added = results_index.update(out, feed, get_bytes)
        print(f"results: {added} new roll-number lists")
        print(f"cut-offs: {cutoffs.update(out, feed, get_bytes)} write-ups read")
    _write_json(status_path, {"schema": SCHEMA, "fullCheckAt": stamp_now,
                              "sourceUpdatedAt": stamp, "indiaAt": india_at, "streamCounts": counts, "failedSources": failed,
                              "rulesNeeded": rules_needed(feed, all_rules)}, volatile=())
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
    ap.add_argument("--plan", action="store_true",
                    help="only print true/false: would this run do a full check (so the workflow installs pdftotext only then)")
    args = ap.parse_args(argv)
    if args.plan:
        try:
            stamp = ssc.last_update(http.get_json(ssc.LAST_UPDATES))
        except (http.FetchError, ssc.FormatChanged):
            print("true")  # the build will report the error properly
            return 0
        status = _read_json(_v1(args.out, "status.json"), {})
        india_at = (_read_json(_v1(args.out, "india.json"), None) or {}).get("generatedAt")
        print("true" if needs_full_check(status, stamp, dt.datetime.now(ssc.IST), args.force, india_at) else "false")
        return 0
    try:
        return run(args.out, http.get_json, dt.datetime.now(ssc.IST), args.window_days, args.max_pages, args.force,
                   get_bytes=None if args.no_results else http.get_bytes, site_url=args.site_url,
                   get_text=lambda url: http.get_bytes(url, timeout=40, max_bytes=5_000_000).decode("utf-8"),
                   channel=os.environ.get("JOBS_TELEGRAM_CHAT_ID"),
                   push=(os.environ.get("JOBS_PUSH_URL"), os.environ.get("JOBS_PUSH_KEY")),
                   whatsapp=os.environ.get("JOBS_WHATSAPP_CHANNEL"), contact=os.environ.get("JOBS_CONTACT_EMAIL"))
    except (http.FetchError, ssc.FormatChanged, eligibility.RulesError) as e:
        print(f"::error::{e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
