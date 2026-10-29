"""Reads our Google Search Console numbers so the site can be tuned to what people really search.

Uses a service account that the owner added to the Search Console property (read-only is enough).
The workflow signs in without a key (Workload Identity Federation) and passes a short-lived token
as GSC_ACCESS_TOKEN; a JSON key in GSC_SERVICE_ACCOUNT_JSON also works. Without either this does nothing. Writes
latest.json, latest.md and history/<date>.json into --out (the workflow commits them to the `seo` branch).

    python -m lottery_pipeline.gsc --site sc-domain:lottodost.com --out ../seo/gsc
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import sys
import urllib.parse

API = "https://searchconsole.googleapis.com"
SCOPE = "https://www.googleapis.com/auth/webmasters.readonly"
TARGET = ("lottery result", "lottery sambad", "kerala lottery", "maharashtra lottery", "dear")
# Pages whose index state is checked every day (URL Inspection API, 2,000 calls a day are allowed).
INSPECT = ("/", "/bn/", "/hi/", "/kerala/", "/maharashtra/", "/night-8pm/", "/day-6pm/", "/morning-1pm/", "/yesterday/")


def session(key_json: str = "", token: str = ""):
    if token:  # minted by google-github-actions/auth for this run, valid for an hour
        import requests
        s = requests.Session()
        s.headers["Authorization"] = f"Bearer {token}"
        return s
    from google.oauth2 import service_account
    from google.auth.transport.requests import AuthorizedSession
    creds = service_account.Credentials.from_service_account_info(json.loads(key_json), scopes=[SCOPE])
    return AuthorizedSession(creds)


def query(s, site: str, start: dt.date, end: dt.date, dims: list[str], country: str = "", limit: int = 1000) -> list[dict]:
    body: dict = {"startDate": start.isoformat(), "endDate": end.isoformat(), "dimensions": dims,
                  "rowLimit": limit, "dataState": "all"}
    if country:
        body["dimensionFilterGroups"] = [{"filters": [{"dimension": "country", "operator": "equals", "expression": country}]}]
    r = s.post(f"{API}/webmasters/v3/sites/{urllib.parse.quote(site, safe='')}/searchAnalytics/query", json=body, timeout=60)
    r.raise_for_status()
    return [dict(zip(dims, row["keys"]), clicks=row["clicks"], impressions=row["impressions"],
                 ctr=round(row["ctr"], 4), position=round(row["position"], 1)) for row in r.json().get("rows", [])]


def inspect(s, site: str, url: str) -> dict:
    r = s.post(f"{API}/v1/urlInspection/index:inspect", json={"inspectionUrl": url, "siteUrl": site}, timeout=60)
    if r.status_code != 200:
        return {"url": url, "error": r.status_code}
    res = r.json().get("inspectionResult", {}).get("indexStatusResult", {})
    return {"url": url, "verdict": res.get("verdict"), "coverage": res.get("coverageState"),
            "lastCrawl": res.get("lastCrawlTime"), "canonical": res.get("googleCanonical")}


def summarize(rep: dict) -> str:
    """The report a person (or the weekly tuning session) reads first."""
    def table(rows: list[dict], key: str, n: int) -> list[str]:
        out = [f"| {key} | clicks | impressions | CTR | position |", "|---|---|---|---|---|"]
        out += [f"| {r[key]} | {r['clicks']} | {r['impressions']} | {r['ctr']:.1%} | {r['position']} |" for r in rows[:n]]
        return out
    q = sorted(rep["queries_india"], key=lambda r: -r["impressions"])
    near = [r for r in q if 4 <= r["position"] <= 20 and r["impressions"] >= 3]   # one push from page 1 / the top 3
    target = [r for r in q if any(t in r["query"] for t in TARGET)]
    lines = [f"# Search Console {rep['start']} – {rep['end']} (generated {rep['generated']})", "",
             "## Countries", *table(sorted(rep["countries"], key=lambda r: -r["impressions"]), "country", 10), "",
             "## Daily (all countries)", *table(sorted(rep["days"], key=lambda r: r["date"]), "date", 40), "",
             "## India: top queries by impressions", *table(q, "query", 60), "",
             "## India: striking distance (position 4–20, ≥3 impressions)", *table(near, "query", 40), "",
             "## India: target queries (lottery result / sambad / kerala / maharashtra / dear)", *table(target, "query", 40), "",
             "## Pages (all countries)", *table(sorted(rep["pages"], key=lambda r: -r["impressions"]), "page", 40), "",
             "## Index state", "| url | verdict | coverage | last crawl |", "|---|---|---|---|"]
    lines += [f"| {i['url']} | {i.get('verdict', i.get('error'))} | {i.get('coverage', '')} | {i.get('lastCrawl', '')} |"
              for i in rep["inspections"]]
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--site", default="sc-domain:lottodost.com")
    ap.add_argument("--base-url", default="https://lottodost.com")
    ap.add_argument("--days", type=int, default=28)
    ap.add_argument("--out", type=pathlib.Path, required=True)
    a = ap.parse_args()
    key = os.environ.get("GSC_SERVICE_ACCOUNT_JSON", "").strip()
    token = os.environ.get("GSC_ACCESS_TOKEN", "").strip()
    if not key and not token:
        print("No Search Console credentials (GSC_ACCESS_TOKEN or GSC_SERVICE_ACCOUNT_JSON): nothing to read")
        return 0
    s = session(key, token)
    end = dt.date.today()
    start = end - dt.timedelta(days=a.days)
    rep = {"generated": dt.datetime.now(dt.timezone.utc).isoformat(timespec="minutes"), "site": a.site,
           "start": start.isoformat(), "end": end.isoformat(),
           "countries": query(s, a.site, start, end, ["country"]),
           "days": query(s, a.site, start, end, ["date"]),
           "queries_india": query(s, a.site, start, end, ["query"], country="ind"),
           "pages": query(s, a.site, start, end, ["page"]),
           "query_pages_india": query(s, a.site, start, end, ["query", "page"], country="ind"),
           "inspections": [inspect(s, a.site, a.base_url.rstrip("/") + p) for p in INSPECT]}
    a.out.mkdir(parents=True, exist_ok=True)
    (a.out / "history").mkdir(exist_ok=True)
    text = json.dumps(rep, ensure_ascii=False, indent=1)
    (a.out / "latest.json").write_text(text, encoding="utf-8")
    (a.out / "history" / f"{end.isoformat()}.json").write_text(text, encoding="utf-8")
    (a.out / "latest.md").write_text(summarize(rep), encoding="utf-8")
    print(f"{len(rep['queries_india'])} India queries, {len(rep['pages'])} pages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
