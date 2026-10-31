"""Daily visitors from Cloudflare Web Analytics (the beacon on every page), for the daily report.

Reads the GraphQL Analytics API with CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID (the token needs
"Account Analytics: Read"; without it the report says so instead of failing the run). Writes latest.md,
latest.json and history/<date>.json into --out (the workflow keeps them on the `seo` branch, folder cf/).

    python -m lottery_pipeline.visitors --out ../seo/cf --days 14
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import urllib.request

API = "https://api.cloudflare.com/client/v4/graphql"
QUERY = """query($acct: String!, $from: Date!, $to: Date!) { viewer { accounts(filter: {accountTag: $acct}) {
  daily: rumPageloadEventsAdaptiveGroups(limit: 1000, filter: {date_geq: $from, date_leq: $to}, orderBy: [date_ASC]) {
    count sum { visits } dimensions { date siteTag } }
  countries: rumPageloadEventsAdaptiveGroups(limit: 15, filter: {date_geq: $from, date_leq: $to}, orderBy: [count_DESC]) {
    count sum { visits } dimensions { countryName } }
  pages: rumPageloadEventsAdaptiveGroups(limit: 25, filter: {date_geq: $from, date_leq: $to}, orderBy: [count_DESC]) {
    count sum { visits } dimensions { requestPath } }
} } }"""


def fetch(token: str, account: str, start: dt.date, end: dt.date) -> dict:
    body = json.dumps({"query": QUERY, "variables": {"acct": account, "from": start.isoformat(), "to": end.isoformat()}})
    req = urllib.request.Request(API, data=body.encode(), headers={"Authorization": f"Bearer {token}",
                                                                   "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        out = json.load(r)
    if out.get("errors"):
        raise RuntimeError("; ".join(e.get("message", "?") for e in out["errors"])[:500])
    accts = out["data"]["viewer"]["accounts"]
    if not accts:
        raise RuntimeError("account not visible to this token")
    return accts[0]


def summarize(data: dict, start: dt.date, end: dt.date, now: str) -> str:
    days: dict[str, list[int]] = {}
    for g in data["daily"]:
        d = days.setdefault(g["dimensions"]["date"], [0, 0])
        d[0] += g["sum"]["visits"]
        d[1] += g["count"]
    lines = [f"# Visitors {start} – {end} (Cloudflare Web Analytics, generated {now})", "",
             "Visits = sessions from outside the site; page views = every page load. Sites with ad/script blockers are not counted.",
             "", "## Daily", "| date | visits | page views |", "|---|---|---|"]
    lines += [f"| {d} | {v} | {p} |" for d, (v, p) in sorted(days.items())]
    lines += ["", "## Countries", "| country | visits | page views |", "|---|---|---|"]
    lines += [f"| {g['dimensions']['countryName']} | {g['sum']['visits']} | {g['count']} |" for g in data["countries"]]
    lines += ["", "## Pages", "| path | visits | page views |", "|---|---|---|"]
    lines += [f"| {g['dimensions']['requestPath']} | {g['sum']['visits']} | {g['count']} |" for g in data["pages"]]
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--days", type=int, default=14)
    a = ap.parse_args()
    out = pathlib.Path(a.out)
    (out / "history").mkdir(parents=True, exist_ok=True)
    now = dt.datetime.now(dt.timezone.utc)
    end = now.date()
    start = end - dt.timedelta(days=a.days - 1)
    token, account = os.environ.get("CLOUDFLARE_API_TOKEN", ""), os.environ.get("CLOUDFLARE_ACCOUNT_ID", "")
    stamp = now.strftime("%Y-%m-%dT%H:%M+00:00")
    try:
        if not token or not account:
            raise RuntimeError("CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID not set")
        data = fetch(token, account, start, end)
    except Exception as e:  # report, never fail the run: the Search Console part must still be kept
        (out / "latest.md").write_text(f"# Visitors (generated {stamp})\n\nNot read: {e}\n\n"
                                       "The Cloudflare token needs the permission \"Account Analytics: Read\" "
                                       "(Cloudflare > My Profile > API Tokens > edit the token).\n")
        print(f"visitors not read: {e}")
        return 0
    (out / "latest.json").write_text(json.dumps(data, indent=1))
    (out / "history" / f"{end}.json").write_text(json.dumps(data))
    (out / "latest.md").write_text(summarize(data, start, end, stamp))
    print((out / "latest.md").read_text())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
