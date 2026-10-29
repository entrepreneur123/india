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
import json
import os

from . import eligibility, sources

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


# Who publishes an exam family's notices (exam_families.json "source"; default ssc): sources.py.
SOURCES = {k: (s["short"], s["name"], s["site"]) for k, s in sources.SOURCES.items()}


def _gov(url: str) -> str | None:
    return url if sources.official(url) else None


def _link(item: dict) -> str | None:
    """The item's official file, else its official page (ISRO and ONGC post pages, not PDFs)."""
    return next((_gov(f["url"]) for f in item["files"] if _gov(f["url"])), None) or (_gov(item["link"]) if item.get("link") else None)


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


NAME = "Sarkari Job Alerts"  # working name (jobs/LAUNCH.md step 0); also web/i18n.js appName
# The app's colours (web/index.html) so a visitor from Google sees the same site when they tap through.
STYLE = ("body{margin:0 auto;max-width:760px;padding:0 16px 16px;font:16px/1.55 system-ui,sans-serif;color:#172033;background:#f6f8fb}"
         "@media(prefers-color-scheme:dark){body{color:#e5e7eb;background:#111827}a{color:#5d9bff}.bar{background:#182234!important}}"
         ".bar{display:flex;align-items:center;gap:10px;margin:0 -16px 8px;padding:12px 16px;background:#fff;border-bottom:1px solid #8883}"
         ".bar a{font-weight:700;text-decoration:none;color:inherit}.crumbs{font-size:.9rem}.crumbs a{color:inherit}"
         "h1{font-size:1.4rem}h2{font-size:1.1rem;margin-top:24px}table{border-collapse:collapse;width:100%}"
         "th,td{text-align:left;padding:6px;border-bottom:1px solid #8884;vertical-align:top}.note,.hi{color:#5f6b7e;font-size:.9rem}"
         "li{margin:6px 0}a.cta{display:inline-block;padding:12px 16px;border-radius:12px;background:#1769e0;color:#fff;text-decoration:none;font-weight:700}"
         "footer{margin-top:32px;padding-top:12px;border-top:1px solid #8884}footer nav a{display:inline-block;padding:8px 12px 8px 0}")
INFO = [("about", "About"), ("privacy", "Privacy policy"), ("terms", "Terms of use"), ("contact", "Contact")]


def _frame(title: str, desc: str, body: list[str], today: dt.date, site_url: str | None, path: str,
           crumbs: tuple[tuple[str, str | None], ...] = (), contact: str | None = None, noindex: bool = False) -> str:
    """The same frame round every static page: name bar, breadcrumbs, footer links to the about,
    privacy and terms pages, and a link preview (WhatsApp shows og:image only from a full address).
    path is the page's place on the site, "jobs/exam/cgl/"; links go up from there to jobs/."""
    base = site_url.rstrip("/") if site_url else None
    if path.startswith("jobs/") and path.endswith("/"):
        root = "../" * (path.count("/") - 1)
    else:  # jobs/404.html: shown at any address, so only full links work
        root = f"{base}/jobs/" if base else "/jobs/"
    head = ['<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">',
            f"<title>{E(title)}</title>", f'<meta name="description" content="{E(desc)}">',
            '<meta name="theme-color" content="#1769e0">', f'<meta property="og:site_name" content="{E(NAME)}">',
            f'<meta property="og:title" content="{E(title)}">', f'<meta property="og:description" content="{E(desc)}">']
    if noindex:
        head.append('<meta name="robots" content="noindex">')
    elif base:
        url = f"{base}/{path}"
        head += [f'<link rel="canonical" href="{E(url)}">', f'<meta property="og:url" content="{E(url)}">',
                 '<meta property="og:type" content="website">', f'<meta property="og:image" content="{E(base)}/jobs/icon-512.png">']
        trail = [("Home", ""), *crumbs]
        ld = {"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": [
            {"@type": "ListItem", "position": n + 1, "name": name, **({"item": f"{base}/jobs/{p}"} if p is not None else {})}
            for n, (name, p) in enumerate(trail)]}
        head.append('<script type="application/ld+json">' + json.dumps(ld, ensure_ascii=False).replace("</", "<\\/") + "</script>")
    nav = [f'<a href="{E(root)}">Home</a>'] + [f'<a href="{E(root + p)}">{E(n)}</a>' if p is not None else E(n) for n, p in crumbs]
    links = [(s, n) for s, n in INFO if s != "contact" or contact]
    foot = ('<footer><nav>' + "".join(f'<a href="{E(root + s)}/">{E(n)}</a>' for s, n in links) + "</nav>"
            f'<p class="note">{E(NAME)} is not a government website and is not linked to any recruiting body. '
            "Always check the official notice before you apply.</p></footer>")
    return ("<!doctype html><html lang=\"en\"><head>" + "".join(head) + f"<style>{STYLE}</style></head><body>"
            + f'<header class="bar"><a href="{E(root)}">{E(NAME)}</a></header>'
            + (f'<nav class="crumbs" aria-label="Breadcrumb">{" › ".join(nav)}</nav>' if crumbs else "")
            + "\n".join(body) + foot + f"<p class=\"note\">Updated {today.isoformat()}.</p></body></html>\n")


def page(code: str, fam: dict, rules: dict | None, items: list[dict], today: dt.date, site_url: str | None,
         contact: str | None = None) -> str:
    short = fam.get("short") or fam["name"]
    year = rules["examYear"] if rules else max((i["examYear"] or 0 for i in items), default=0) or None
    # Only what the page really has: age limit and salary come from a rules file (SSC exams).
    topics = ("Exam Date, Notification, Age Limit, Salary, Admit Card, Result" if rules
              else "Latest Notices, Admit Card, Answer Key, Result")
    title = f"{short}{' ' + str(year) if year else ''}: {topics}"
    org, org_name, home = SOURCES[fam.get("source", "ssc")]
    host = home.split("//", 1)[1]
    what = "exam dates, application dates, fee, age limit, pay and results" if rules else "admit cards, answer keys and results"
    desc = (f"{fam['name']}{' ' + str(year) if year else ''}: latest {org} notices, {what}, "
            f"with links to the official PDFs on {host}. Not a government website.")
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
            link = _link(i)
            t = E(i["title"])
            b.append(f'<li><time datetime="{E(i["publishedAt"])}">{_day(i["publishedAt"])}</time> · '
                     + (f'<a href="{E(link)}" rel="noopener" lang="{E(i["lang"])}">{t}</a>' if link else t) + "</li>")
        b.append("</ul>")
    b.append(f'<p><a class="cta" href="../../#exam/{E(code)}">Open {E(short)} in the app: alerts, result check by roll number →</a></p>')
    return _frame(title, desc, b, today, site_url, f"jobs/exam/{code.lower()}/", ((short, None),), contact)


# Topic pages for what people search on Google India (research/google_suggest_IN_2026-10-2*.json:
# "latest govt jobs", "sarkari result", "police vacancy 2026", "bihar police vacancy 2026",
# "up govt jobs 2026", "isro recruitment 2026", ...). Each lists only notices we really have.
KIND_WORDS = {"recruitment": "New job", "result": "Result", "admit_card": "Admit card", "answer_key": "Answer key",
              "amendment": "Change", "vacancy": "Vacancy", "schedule": "Exam dates", "notice": "Notice"}
HUBS = [  # slug, title ({y}: year), Hindi line, what it lists, filter
    ("latest-govt-jobs", "Latest Govt Jobs {y}: new recruitment notifications", "सरकारी नौकरी {y}: नई भर्तियाँ", ("kind", "recruitment")),
    ("sarkari-result", "Sarkari Result {y}: latest results from official websites", "सरकारी रिजल्ट {y}", ("kind", "result")),
    ("admit-card", "Admit Card {y}: latest admit card notices", "एडमिट कार्ड {y}", ("kind", "admit_card")),
    ("answer-key", "Answer Key {y}: latest answer keys", "आंसर की {y}", ("kind", "answer_key")),
    ("police-jobs", "Police Vacancy {y} (Police Bharti): UP, Bihar, Haryana, CAPF, Delhi Police", "पुलिस भर्ती {y}", ("sector", "police")),
    ("railway-jobs", "Railway Jobs {y} (RRB): CEN notices, results, admit cards", "रेलवे भर्ती {y}: आरआरबी", ("sector", "railway")),
    ("ssc-jobs", "SSC {y}: CGL, CHSL, MTS, GD, CPO notices and results", "एसएससी {y}", ("sector", "ssc")),
    ("psu-jobs", "PSU Jobs {y}: ONGC, Indian Oil, AAI recruitment notices", "सरकारी कंपनी (PSU) भर्ती {y}", ("sector", "psu")),
    ("isro-recruitment", "ISRO Recruitment {y}: notifications and updates", "इसरो भर्ती {y}", ("sector", "science")),
    ("up-govt-jobs", "UP Govt Jobs {y} (Sarkari Naukri UP): UPPSC, UP Police and more", "यूपी सरकारी नौकरी {y}", ("state", "UP")),
    ("bihar-govt-jobs", "Bihar Govt Jobs {y}: BPSC, Bihar Police constable and SI vacancy", "बिहार सरकारी नौकरी {y}: बीपीएससी, बिहार पुलिस", ("state", "BR")),
    ("haryana-govt-jobs", "Haryana Govt Jobs {y}: HSSC and HPSC notices", "हरियाणा सरकारी नौकरी {y}", ("state", "HR")),
    ("mp-govt-jobs", "MP Govt Jobs {y} (MPPSC): recruitment, results, answer keys", "एमपी सरकारी नौकरी {y}: एमपीपीएससी", ("state", "MP")),
    ("rajasthan-govt-jobs", "Rajasthan Govt Jobs {y}: RPSC and RSSB notices, results, answer keys", "राजस्थान सरकारी नौकरी {y}: आरपीएससी, आरएसएसबी", ("state", "RJ")),
    ("punjab-govt-jobs", "Punjab Govt Jobs {y}: PPSC and PSSSB notices", "पंजाब सरकारी नौकरी {y}", ("state", "PB")),
    ("delhi-govt-jobs", "Delhi Govt Jobs {y}: DSSSB notices and results", "दिल्ली सरकारी नौकरी {y}: डीएसएसएसबी", ("state", "DL")),
    ("kerala-govt-jobs", "Kerala Govt Jobs {y}: Kerala PSC notifications", "केरल सरकारी नौकरी {y}", ("state", "KL")),
    ("bank-jobs", "Bank Jobs {y}: SBI, IBPS and RBI notices, results, admit cards", "बैंक भर्ती {y}: एसबीआई, आईबीपीएस, आरबीआई", ("sector", "bank")),
    ("teacher-jobs", "Teacher Jobs {y}: CTET, UGC-NET and DSSSB teacher notices, exam dates and results", "शिक्षक भर्ती {y}: सीटेट", ("sector", "teaching")),
]


def _matches(item: dict, families: dict, how: tuple[str, str]) -> bool:
    field, value = how
    fam = families.get(item.get("exam") or "", {})
    source = item.get("source", "ssc")
    if field == "kind":
        return item["kind"] == value
    if field == "sector":
        return value in (fam.get("sectors") or (["ssc"] if source == "ssc" else []))
    return sources.SOURCES.get(source, {}).get("state") == value


def hub_page(slug: str, title: str, hindi: str, items: list[dict], families: dict, today: dt.date, site_url: str | None,
             contact: str | None = None) -> str:
    who = sorted({sources.SOURCES.get(i.get("source", "ssc"), {}).get("short", "") for i in items} - {""})
    desc = (f"{title}. {len(items)} updates from {', '.join(who) or 'official websites'}, each with the official link. "
            "Free, unofficial, updated several times a day.")
    b = [f"<h1>{E(title)}</h1>", f'<p class="hi" lang="hi">{E(hindi)}</p>',
         '<p class="note">Unofficial: not a government website. Every update below links to the recruiter\'s official file or page.</p>']
    if items:
        b.append("<ul>")
        for i in items[:60]:
            fam = families.get(i.get("exam") or "", {})
            name = fam.get("short") or sources.SOURCES.get(i.get("source", "ssc"), {}).get("short", "")
            link = _link(i)
            exam = (f' · <a href="../../exam/{E(i["exam"].lower())}/">{E(name)}</a>' if i.get("exam") and i["exam"] != "LDCE" else f" · {E(name)}")
            b.append(f'<li><time datetime="{E(i["publishedAt"][:10])}">{_day(i["publishedAt"])}</time> · '
                     f'{KIND_WORDS.get(i["kind"], "Notice")}{exam}: '
                     + (f'<a href="{E(link)}" rel="noopener" lang="{E(i["lang"])}">{E(i["title"])}</a>' if link else E(i["title"])) + "</li>")
        b.append("</ul>")
    else:
        b.append("<p>No updates in the last 120 days from the recruiters we follow.</p>")
    b.append("<h2>More</h2><ul>" + "".join(f'<li><a href="../{E(s)}/">{E(t.format(y=today.year))}</a></li>' for s, t, _, _ in HUBS if s != slug) + "</ul>")
    b.append('<p><a class="cta" href="../../#home">Open the app: alerts, eligibility check, result by roll number →</a></p>')
    return _frame(title, desc, b, today, site_url, f"jobs/topic/{slug}/", ((title.split(":")[0], None),), contact)


def _write_page(path: str, text: str) -> None:
    old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
    # "Updated <date>" alone must not cause a commit every day.
    if old is None or old.split('<p class="note">Updated')[0] != text.split('<p class="note">Updated')[0]:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)


POLICY_DATE = "28 Oct 2026"  # change when the privacy policy or terms change


def info_pages(today: dt.date, site_url: str | None, contact: str | None) -> dict[str, str]:
    """The pages every website needs (and Google Play and AdSense ask for): about, privacy policy,
    terms of use, contact, and the not-found page. They say only what the code really does."""
    mail = f'<a href="mailto:{E(contact)}">{E(contact)}</a>' if contact else None
    reach = f"<p>Questions or a wrong notice to report: {mail}.</p>" if mail else ""
    who = "".join(f'<li>{E(s["name"])} ({E(s["short"])}): <a href="{E(s["site"])}" rel="noopener">{E(s["site"].split("//", 1)[1].rstrip("/"))}</a></li>'
                  for s in sources.SOURCES.values())
    pages = {
        "about": ("About", f"About {NAME}", "हमारे बारे में",
                  f"{NAME} collects government job notices in India from the recruiters' official websites: new jobs, admit cards, answer keys, results and exam dates.",
                  [f"<p>{E(NAME)} is a free website that puts the latest government job notices in India in one place: new recruitments, "
                   "admit cards, answer keys, results and exam dates. It also checks your eligibility for SSC posts from the official "
                   "notice and finds your roll number in published result lists. It works in English and 12 Indian languages.</p>",
                   "<h2>Where the information comes from</h2><p>Only from the recruiters' own websites:</p><ul>" + who + "</ul>",
                   "<h2>How it is kept up to date</h2><p>A program reads these notice boards several times a day. Every update links "
                   "to the recruiter's official file or page, and titles are shown as the recruiter published them.</p>",
                   "<h2>Not a government website</h2><p>We are not part of, or linked to, any government or recruiting body. "
                   "If anything here differs from the official notice, the official notice is right.</p>", reach]),
        "privacy": ("Privacy policy", f"Privacy policy: {NAME}", "गोपनीयता नीति",
                    f"What {NAME} stores and what it does not: no sign-up, no name, phone number or e-mail.",
                    [f"<p>Last updated {POLICY_DATE}.</p>",
                     "<h2>No account</h2><p>There is no sign-up. We never ask for your name, phone number, e-mail or documents.</p>",
                     "<h2>What stays on your phone</h2><p>Your eligibility answers (date of birth, category, qualification), saved notices, "
                     "followed exams, roll numbers you check, language and theme are kept in your browser's storage on this device only. "
                     "They are not sent to us. Clearing the site's data in your browser deletes them.</p>",
                     "<h2>Result check by roll number</h2><p>The app downloads the part of the published result list that could contain "
                     "your number (picked by its last two digits) and looks for the full number on your phone. The full number is not sent.</p>",
                     "<h2>Alerts</h2><p>Only if you turn on alerts: your browser's push service gives an address for your browser. We keep "
                     "that address, its keys, the exam codes you follow, your language and when you last changed them on our server to send "
                     "the alerts, and nothing else. Turning alerts off deletes them; if you are offline at that moment, they are deleted "
                     "the next time an alert cannot be delivered.</p>",
                     "<h2>Channels and links</h2><p>Our WhatsApp and Telegram channels are run inside those apps, under their own privacy "
                     "policies. Links to recruiters' websites take you to sites with their own policies.</p>",
                     "<h2>Server logs, cookies, ads</h2><p>Like any website, the servers that deliver these pages may keep technical logs "
                     "(such as IP address and browser type) for security. This site uses no cookies, no analytics and no advertising at "
                     "present. If that changes, this page will say so first.</p>", reach]),
        "terms": ("Terms of use", f"Terms of use: {NAME}", "नियम और शर्तें",
                  f"Terms of use for {NAME}: information only, the official notice always comes first, no fees ever.",
                  [f"<p>Last updated {POLICY_DATE}.</p>",
                   "<h2>Information only</h2><p>We collect notices from official websites and try to show them correctly and quickly, "
                   "but we can make mistakes or be late. Before you apply, pay or travel, read the official notice: it always comes first.</p>",
                   "<h2>Eligibility check and result search</h2><p>These are aids worked out from the official notice and result lists. "
                   "They are not official decisions; the recruiter decides who is eligible and who has passed.</p>",
                   "<h2>We never ask for money</h2><p>This site is free. We never ask for fees, bank details or documents, and we never "
                   "promise a job. Anyone who asks for money to get you a government job is committing fraud: report it to the police "
                   "or at cybercrime.gov.in.</p>",
                   "<h2>Other websites</h2><p>Notices and files belong to the recruiters that published them. We are not responsible for "
                   "other websites' content or availability.</p>",
                   "<h2>Changes</h2><p>We may update these terms; the date above shows the latest version.</p>", reach]),
    }
    if mail:
        pages["contact"] = ("Contact", f"Contact: {NAME}", "संपर्क करें", f"How to reach {NAME}.",
                            [f"<p>E-mail: {mail}</p><p>To report a wrong, late or missing notice, send the link to the official notice. "
                             "We do not answer questions about individual applications: for those, contact the recruiter.</p>"])
    out = {f"jobs/{slug}/": _frame(h1, desc, [f"<h1>{E(h1)}</h1>", f'<p class="hi" lang="hi">{E(hi)}</p>', *body],
                                   today, site_url, f"jobs/{slug}/", ((crumb, None),), contact)
           for slug, (crumb, h1, hi, desc, body) in pages.items()}
    # Under jobs/: the data branch's root (and its 404.html) belongs to the lottery site. Caddy serves this one.
    out["jobs/404.html"] = _frame(f"Page not found: {NAME}", "This page does not exist.",
                             ["<h1>Page not found</h1>", '<p class="hi" lang="hi">यह पेज नहीं मिला</p>',
                              "<p>The page may have moved. Start again from the latest updates:</p><ul>"
                              + "".join(f'<li><a href="{E((site_url.rstrip("/") if site_url else "") + "/jobs/topic/" + s)}/">{E(t.format(y=today.year))}</a></li>'
                                        for s, t, _, _ in HUBS[:4]) + "</ul>"],
                             today, site_url, "jobs/404.html", contact=contact, noindex=True)
    return out


def build(out: str, families: dict, all_rules: list[dict], items: list[dict], today: dt.date, site_url: str | None,
          contact: str | None = None) -> list[str]:
    paths = []
    for code, fam in families.items():
        if code == "LDCE":
            continue
        rules = max((r for r in all_rules if r["exam"] == code), key=lambda r: r["examYear"], default=None)
        mine = [i for i in items if i["exam"] == code]
        if not rules and not mine:
            continue
        _write_page(os.path.join(out, "jobs", "exam", code.lower(), "index.html"), page(code, fam, rules, mine, today, site_url, contact))
        paths.append(f"jobs/exam/{code.lower()}/")
    for slug, title, hindi, how in HUBS:
        mine = [i for i in items if i.get("exam") != "LDCE" and _matches(i, families, how)
                and (i.get("source", "ssc") != "ssc" or i.get("lang") == "en")]  # SSC's English copy only
        y = str(today.year)
        _write_page(os.path.join(out, "jobs", "topic", slug, "index.html"),
                    hub_page(slug, title.format(y=y), hindi.format(y=y), mine, families, today, site_url, contact))
        paths.append(f"jobs/topic/{slug}/")
    for path, text in info_pages(today, site_url, contact).items():
        _write_page(os.path.join(out, path, "index.html") if path.endswith("/") else os.path.join(out, path), text)
        if path.endswith("/"):
            paths.append(path)
    if not contact and os.path.exists(os.path.join(out, "jobs", "contact", "index.html")):
        os.remove(os.path.join(out, "jobs", "contact", "index.html"))  # the address was taken away
    if site_url:
        base = site_url.rstrip("/")
        urls = [f"{base}/jobs/"] + [f"{base}/{p}" for p in paths]
        xml = ('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
               + "".join(f"  <url><loc>{E(u)}</loc></url>\n" for u in urls) + "</urlset>\n")
        with open(os.path.join(out, "jobs", "sitemap.xml"), "w", encoding="utf-8") as f:
            f.write(xml)
    return paths
