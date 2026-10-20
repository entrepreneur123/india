"""Static, search-friendly page per exam: jobs/exam/<code>/index.html, plus jobs/sitemap.xml.

People reach sites like this from Google with queries such as "ssc cgl exam date 2026",
"ssc chsl notification 2026", "ssc gd result 2026", "ssc cgl age limit", "ssc mts salary"
(jobs/research/NEEDS.md). The app itself is drawn by JavaScript, which search engines index
poorly, so each exam also gets a plain HTML page with those facts written out: dates, fee, age
limit, pay, vacancies and the latest official notices. Every fact comes from the same
published JSON as the app; every link goes to ssc.gov.in. All text is HTML-escaped.
"""
from __future__ import annotations

import datetime as dt
import html
import os

from . import eligibility

E = html.escape
EXEMPT = {"female": "women", "PwBD": "persons with benchmark disabilities", "ESM": "ex-servicemen"}
QUAL = {"10": "Class 10", "12": "Class 12", "diploma": "Diploma", "graduate": "Bachelor's degree", "postgraduate": "Master's degree"}


def inr(n: int) -> str:
    """Indian digit grouping, as on SSC's own pages: 151100 -> 1,51,100."""
    s = str(n)
    if len(s) <= 3:
        return s
    head, tail = s[:-3], s[-3:]
    parts = []
    while len(head) > 2:
        parts.insert(0, head[-2:])
        head = head[:-2]
    return ",".join([head] + parts + [tail]) if head else ",".join(parts + [tail])


def _day(iso: str) -> str:
    return dt.date.fromisoformat(iso[:10]).strftime("%d %b %Y").lstrip("0")


def _status(rules: dict, today: dt.date) -> str:
    s = eligibility.application_status(rules, today)
    d = rules["dates"]
    return {"open": f"Applications open till {_day(d['applyTo'])}", "upcoming": f"Applications open on {_day(d['applyFrom'])}",
            "closed": f"Applications closed on {_day(d['applyTo'])}"}[s]


# Who publishes an exam family's notices (exam_families.json "source"; default ssc).
SOURCES = {
    "ssc": ("SSC", "Staff Selection Commission", "https://ssc.gov.in"),
    "upprpb": ("UPPRPB", "UP Police Recruitment & Promotion Board", "https://uppbpb.gov.in"),
}


def _gov(url: str) -> str | None:
    return url if any(url.startswith(home + "/") for _, _, home in SOURCES.values()) else None


def _faq(short: str, fam: dict, r: dict) -> str:
    """The questions people type into Google, answered from the rules file only."""
    d, fee = r["dates"], r["fee"]
    qa = [(f"What is the last date to apply for {short} {r['examYear']}?",
           f"{_day(d['applyTo'])}. Fee payment closes on {_day(d['feeTo'])}."),
          (f"What is the age limit for {short} {r['examYear']}?",
           "; ".join(f"{b} years: born {_day(x['bornFrom'])} to {_day(x['bornTo'])}" for b, x in r["age"]["bands"].items())
           + f" (as on {_day(r['age']['asOn'])}), plus category relaxation."),
          (f"What is the {short} application fee?",
           f"₹{fee['amount']}; no fee for {', '.join(EXEMPT.get(x, x) for x in fee['exempt'])}.")]
    if r.get("payScales"):
        qa.append((f"What is the {short} salary?", "; ".join(
            f"Level {k}: ₹{inr(v[0])} to ₹{inr(v[1])} basic pay a month" for k, v in r["payScales"].items() if k != "ref")
            + ". Allowances are extra."))
    if r.get("examSchedule"):
        qa.append((f"When is the {short} {r['examYear']} exam?", "; ".join(
            f"{x['stage']}: {_day(x['from'])} to {_day(x['to'])} (tentative)" if x.get("from") else f"{x['stage']}: {x['when']}"
            for x in r["examSchedule"])))
    if fam.get("qualifications"):
        qa.append((f"What qualification is needed for {short}?", " / ".join(QUAL.get(q, q) for q in fam["qualifications"]) + "."))
    return "<h2>Frequently asked questions</h2>" + "".join(f"<h3>{E(q)}</h3><p>{E(a)}</p>" for q, a in qa)


def page(code: str, fam: dict, rules: dict | None, items: list[dict], today: dt.date, site_url: str | None) -> str:
    short = fam.get("short") or fam["name"]
    year = rules["examYear"] if rules else max((i["examYear"] or 0 for i in items), default=0) or today.year
    topics = "Exam Date, Notification, Age Limit, Salary, Admit Card, Result"
    title = f"{short} {year}: {topics}"
    org, org_name, home = SOURCES[fam.get("source", "ssc")]
    host = home.split("//", 1)[1]
    desc = (f"{fam['name']} {year}: latest {org} notices, exam dates, application dates, fee, age limit, pay and results, "
            f"with links to the official PDFs on {host}. Not a government website.")
    canonical = f"{site_url.rstrip('/')}/jobs/exam/{code.lower()}/" if site_url else None
    b = []
    b.append(f"<h1>{E(title)}</h1>")
    b.append(f'<p class="hi" lang="hi">{E(fam["hi"])}</p>')
    b.append(f'<p class="note">Unofficial: not affiliated with the {E(org_name)} or any government. Source: '
             f'<a href="{E(home)}" rel="noopener">{E(host)}</a>. Always read the official Notice.</p>')
    if fam.get("qualifications"):
        b.append(f"<p><strong>Qualification:</strong> {E(' / '.join(QUAL.get(q, q) for q in fam['qualifications']))} ({E(fam['ref'])})</p>")
    if rules:
        d = rules["dates"]
        rows = [("Application", f"{_day(d['applyFrom'])} to {_day(d['applyTo'])}"), ("Fee payment last date", _day(d["feeTo"]))]
        if d.get("correction"):
            rows.append(("Form correction", f"{_day(d['correction'][0])} to {_day(d['correction'][1])}"))
        for s in rules.get("examSchedule", []):
            rows.append((s["stage"] + " exam date", f"{_day(s['from'])} to {_day(s['to'])} (tentative)" if s.get("from") else s["when"]))
        b.append(f"<h2>{E(rules['title'])}: important dates</h2><p><strong>{E(_status(rules, today))}</strong></p>")
        b.append("<table>" + "".join(f"<tr><th>{E(k)}</th><td>{E(v)}</td></tr>" for k, v in rows) + "</table>")
        fee = rules["fee"]
        b.append(f"<h2>Application fee</h2><p>₹{fee['amount']} for General / OBC / EWS. No fee for: "
                 f"{E(', '.join(EXEMPT.get(x, x) for x in fee['exempt']))}." + (f" Correction charges ₹{fee['correction'][0]} / ₹{fee['correction'][1]}." if fee.get("correction") else "") + "</p>")
        age = rules["age"]
        b.append(f"<h2>Age limit (as on {_day(age['asOn'])})</h2><ul>" + "".join(
            f"<li>{E(name)} years: born {_day(x['bornFrom'])} to {_day(x['bornTo'])}</li>" for name, x in age["bands"].items())
            + "</ul><p>Upper age relaxation: " + E(", ".join(f"{k.replace('_', ' ')} {v} years" for k, v in age["relaxation"].items())) + ".</p>")
        if rules.get("payScales"):
            b.append("<h2>Salary (pay level)</h2><ul>" + "".join(
                f"<li>Level {E(k)}: basic pay ₹{inr(v[0])} to ₹{inr(v[1])} a month</li>" for k, v in rules["payScales"].items() if k != "ref")
                + "</ul><p>Basic pay from the Notice; allowances are extra.</p>")
        if rules.get("vacancies"):
            b.append(f"<h2>Vacancies</h2><p>About {inr(rules['vacancies']['count'])} (tentative).</p>")
        b.append(f"<h2>Posts</h2><ul>" + "".join(f"<li>{E(p['name'])}, {E(p['org'])}</li>" for p in rules["posts"]) + "</ul>")
        notice = _gov(rules["source"]["notice"])
        if notice:
            b.append(f'<p><a href="{E(notice)}" rel="noopener">Official Notice (PDF)</a></p>')
        b.append(f'<p><a class="cta" href="../../#check/{E(rules["id"])}">Check your eligibility for each post →</a></p>')
        b.append(_faq(short, fam, rules))
    latest = sorted(items, key=lambda i: i["publishedAt"], reverse=True)[:25]
    if latest:
        b.append(f"<h2>Latest {E(short)} notices: results, admit cards, answer keys</h2><ul>")
        for i in latest:
            link = next((_gov(f["url"]) for f in i["files"] if _gov(f["url"])), None)
            t = E(i["title"])
            b.append(f'<li><time datetime="{E(i["publishedAt"])}">{_day(i["publishedAt"])}</time> · '
                     + (f'<a href="{E(link)}" rel="noopener" lang="{E(i["lang"])}">{t}</a>' if link else t) + "</li>")
        b.append("</ul>")
    b.append(f'<p><a class="cta" href="../../#exam/{E(code)}">Open {E(short)} in the app: alerts, result check by roll number →</a></p>')
    head = [
        '<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">',
        f"<title>{E(title)}</title>", f'<meta name="description" content="{E(desc)}">',
    ]
    if canonical:
        head.append(f'<link rel="canonical" href="{E(canonical)}">')
    style = ("body{margin:0 auto;max-width:760px;padding:16px;font:16px/1.55 system-ui,sans-serif;color:#111827;background:#fff}"
             "@media(prefers-color-scheme:dark){body{color:#e5e7eb;background:#0b1220}a{color:#60a5fa}}"
             "h1{font-size:1.4rem}h2{font-size:1.1rem;margin-top:24px}table{border-collapse:collapse;width:100%}"
             "th,td{text-align:left;padding:6px;border-bottom:1px solid #8884;vertical-align:top}.note,.hi{color:#6b7280;font-size:.9rem}"
             "a.cta{display:inline-block;padding:12px 16px;border-radius:12px;background:#1d4ed8;color:#fff;text-decoration:none;font-weight:700}")
    return ("<!doctype html><html lang=\"en\"><head>" + "".join(head) + f"<style>{style}</style></head><body>"
            + "\n".join(b) + f"<p class=\"note\">Updated {today.isoformat()}.</p></body></html>\n")


def build(out: str, families: dict, all_rules: list[dict], items: list[dict], today: dt.date, site_url: str | None) -> list[str]:
    paths = []
    for code, fam in families.items():
        if code == "LDCE":
            continue
        rules = max((r for r in all_rules if r["exam"] == code), key=lambda r: r["examYear"], default=None)
        mine = [i for i in items if i["exam"] == code]
        if not rules and not mine:
            continue
        path = os.path.join(out, "jobs", "exam", code.lower(), "index.html")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        text = page(code, fam, rules, mine, today, site_url)
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        # "Updated <date>" alone must not cause a commit every day.
        if old is None or old.split('<p class="note">Updated')[0] != text.split('<p class="note">Updated')[0]:
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
        paths.append(f"jobs/exam/{code.lower()}/")
    if site_url:
        base = site_url.rstrip("/")
        urls = [f"{base}/jobs/"] + [f"{base}/{p}" for p in paths]
        xml = ('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
               + "".join(f"  <url><loc>{E(u)}</loc></url>\n" for u in urls) + "</urlset>\n")
        with open(os.path.join(out, "jobs", "sitemap.xml"), "w", encoding="utf-8") as f:
            f.write(xml)
    return paths
