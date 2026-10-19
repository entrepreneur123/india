"""Static, search-friendly results site generated from the v2 feeds.

Every draw gets its own pre-rendered page (readable without JavaScript, fast on 2G), every day
has a page, and the home page shows today. English at the root, Bengali under /bn/ and Hindi
under /hi/, linked with hreflang. Wording comes from the Android app's strings.xml plus the
page-level texts below, so app and site say the same thing.

    python web/sitegen.py --data public --out public --base-url https://example.in
Optional environment: ADSENSE_CLIENT (ca-pub-…) adds ad slots and ads.txt;
CF_BEACON_TOKEN adds Cloudflare Web Analytics (cookie-free); GOOGLE_SITE_VERIFICATION and
BING_SITE_VERIFICATION add the ownership meta tags for Search Console / Bing Webmaster Tools.
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "android/app/src/main/res"
LANGS = {"en": ("values", ""), "bn": ("values-bn", "/bn"), "hi": ("values-hi", "/hi")}
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
WEEKLY = [("NL1PM", "13:00", "1pm"), ("SK6PM", "18:00", "6pm"), ("NL8PM", "20:00", "8pm")]
SLUG = {"NL1PM": "1pm", "SK6PM": "6pm", "NL8PM": "8pm", "NLLAXMI": "laxmi-8pm"}
TAG = {"NL1PM": "tag_morning", "SK6PM": "tag_day", "NL8PM": "tag_night"}
CONTACT = "yechan750745@gmail.com"

# Page-level texts. {d} = 19.10.2026, {t} = 1 PM, {g} = game name, {s} = state, {n} = 1st prize.
WEB = {
    "en": {
        "home_title": "Nagaland State Lottery Result Today {d} – 1 PM, 6 PM, 8 PM Dear Lottery",
        "home_h1": "Nagaland State Lottery Result Today",
        "home_desc": "Dear lottery result {d}: {list}. Full prize list, ticket checker and official result links.",
        "day_title": "Nagaland State Lottery Result {d} – 1 PM, 6 PM, 8 PM Dear Lottery",
        "day_h1": "Dear Lottery Result {d}",
        "draw_title": "{s} State Lottery Result {d} {t} – {g}",
        "draw_desc": "{g} result {d} {t}: 1st prize {n}. All prize numbers and a ticket checker.",
        "awaited": "awaited", "result_of": "Result of {d}", "all_of_day": "All results of {d}",
        "old_title": "Old Dear Lottery Results – All Dates", "old_h1": "Old results",
        "prev": "Previous day", "next": "Next day", "older": "Older results",
        "app": "Get the result the moment it is out: free LottoDost app (alerts, ticket scan, number history).",
        "faq": "Common questions", "q_time": "When are the results declared?",
        "a_time": "Dear lottery draws are at 1 PM (Nagaland), 6 PM (Sikkim) and 8 PM (Nagaland) IST every day. The result sheet usually appears 7–10 minutes after each draw, and this page updates by itself.",
        "check_hint": "Type the full number (e.g. 39C 06366, bumper A 801021) or just the last 4–6 digits.",
        "q_check": "How do I check my ticket?", "q_claim": "How do I claim a prize?",
        "updated": "Updated {t} IST", "home": "Today", "lang": "Language",
        "about": "About", "contact": "Contact", "disclaimer": "Disclaimer", "privacy": "Privacy policy",
        "about_body": "LottoDost publishes Dear lottery results within minutes of each draw. Numbers are read from the published result sheet, checked for completeness and compared with the official Nagaland gazette when it is released. We are not a government website and we do not sell tickets.",
        "contact_body": "Questions, corrections or removal requests: {e}. We usually reply within two working days.",
        "notfound": "Page not found", "search_as": "Also searched as Lottery Sambad, Dear Lottery Result, Nagaland Lottery Sambad.",
    },
    "bn": {
        "home_title": "নাগাল্যান্ড স্টেট লটারি রেজাল্ট আজ {d} – দুপুর 1টা, সন্ধ্যা 6টা, রাত 8টা ডিয়ার লটারি",
        "home_h1": "নাগাল্যান্ড স্টেট লটারি আজকের রেজাল্ট",
        "home_desc": "ডিয়ার লটারি রেজাল্ট {d}: {list}। পুরো পুরস্কারের তালিকা, টিকিট চেক আর সরকারি রেজাল্টের লিংক।",
        "day_title": "নাগাল্যান্ড স্টেট লটারি রেজাল্ট {d} – দুপুর 1টা, সন্ধ্যা 6টা, রাত 8টা",
        "day_h1": "ডিয়ার লটারি রেজাল্ট {d}",
        "draw_title": "{s} স্টেট লটারি রেজাল্ট {d} {t} – {g}",
        "draw_desc": "{g} রেজাল্ট {d} {t}: প্রথম পুরস্কার {n}। সব পুরস্কারের নম্বর আর টিকিট চেক।",
        "awaited": "রেজাল্টের অপেক্ষা", "result_of": "{d}-এর রেজাল্ট", "all_of_day": "{d}-এর সব রেজাল্ট",
        "old_title": "পুরোনো ডিয়ার লটারি রেজাল্ট – সব তারিখ", "old_h1": "পুরোনো রেজাল্ট",
        "prev": "আগের দিন", "next": "পরের দিন", "older": "পুরোনো রেজাল্ট",
        "app": "রেজাল্ট বেরোনোর সঙ্গে সঙ্গে জানতে ফ্রি LottoDost অ্যাপ নিন (অ্যালার্ট, টিকিট স্ক্যান, নম্বরের ইতিহাস)।",
        "faq": "সাধারণ প্রশ্ন", "q_time": "রেজাল্ট কখন বেরোয়?",
        "a_time": "ডিয়ার লটারির ড্র রোজ দুপুর 1টা (নাগাল্যান্ড), সন্ধ্যা 6টা (সিকিম) আর রাত 8টায় (নাগাল্যান্ড) হয়। প্রতিটি ড্রয়ের সাধারণত 7–10 মিনিট পরে রেজাল্ট শিট বেরোয়, আর এই পেজ নিজে থেকেই আপডেট হয়।",
        "check_hint": "পুরো নম্বর লিখুন (যেমন 39C 06366, বাম্পার A 801021) বা শুধু শেষ 4–6টি সংখ্যা।",
        "q_check": "টিকিট কীভাবে মেলাব?", "q_claim": "পুরস্কার কীভাবে নেব?",
        "updated": "আপডেট {t} IST", "home": "আজ", "lang": "ভাষা",
        "about": "আমাদের কথা", "contact": "যোগাযোগ", "disclaimer": "দায়মুক্তি", "privacy": "প্রাইভেসি পলিসি",
        "about_body": "LottoDost প্রতিটি ড্রয়ের কয়েক মিনিটের মধ্যে ডিয়ার লটারির রেজাল্ট দেয়। নম্বরগুলো প্রকাশিত রেজাল্ট শিট থেকে পড়া হয়, সব ঠিক আছে কি না দেখা হয়, আর নাগাল্যান্ডের সরকারি গেজেট বেরোলে তার সঙ্গে মেলানো হয়। এটি সরকারি ওয়েবসাইট নয়, আর আমরা টিকিট বিক্রি করি না।",
        "contact_body": "প্রশ্ন, সংশোধন বা কিছু সরানোর অনুরোধ: {e}। সাধারণত দুই কাজের দিনের মধ্যে উত্তর দিই।",
        "notfound": "পেজটি পাওয়া যায়নি", "search_as": "লটারি সংবাদ, ডিয়ার লটারি রেজাল্ট, নাগাল্যান্ড লটারি সংবাদ নামেও খোঁজা হয়।",
    },
    "hi": {
        "home_title": "नागालैंड स्टेट लॉटरी रिज़ल्ट आज {d} – 1 PM, 6 PM, 8 PM डियर लॉटरी",
        "home_h1": "नागालैंड स्टेट लॉटरी आज का रिज़ल्ट",
        "home_desc": "डियर लॉटरी रिज़ल्ट {d}: {list}। पूरी इनाम लिस्ट, टिकट चेक और सरकारी रिज़ल्ट के लिंक।",
        "day_title": "नागालैंड स्टेट लॉटरी रिज़ल्ट {d} – 1 PM, 6 PM, 8 PM डियर लॉटरी",
        "day_h1": "डियर लॉटरी रिज़ल्ट {d}",
        "draw_title": "{s} स्टेट लॉटरी रिज़ल्ट {d} {t} – {g}",
        "draw_desc": "{g} रिज़ल्ट {d} {t}: पहला इनाम {n}। सारे इनाम के नंबर और टिकट चेक।",
        "awaited": "रिज़ल्ट का इंतज़ार", "result_of": "{d} का रिज़ल्ट", "all_of_day": "{d} के सारे रिज़ल्ट",
        "old_title": "पुराने डियर लॉटरी रिज़ल्ट – सभी तारीख़ें", "old_h1": "पुराने रिज़ल्ट",
        "prev": "पिछला दिन", "next": "अगला दिन", "older": "पुराने रिज़ल्ट",
        "app": "रिज़ल्ट आते ही जानने के लिए फ़्री LottoDost ऐप लें (अलर्ट, टिकट स्कैन, नंबर हिस्ट्री)।",
        "faq": "आम सवाल", "q_time": "रिज़ल्ट कब आता है?",
        "a_time": "डियर लॉटरी का ड्रॉ रोज़ 1 PM (नागालैंड), 6 PM (सिक्किम) और 8 PM (नागालैंड) IST पर होता है। हर ड्रॉ के आमतौर पर 7–10 मिनट बाद रिज़ल्ट शीट आती है, और यह पेज अपने-आप अपडेट होता है।",
        "check_hint": "पूरा नंबर लिखें (जैसे 39C 06366, बंपर A 801021) या सिर्फ़ आख़िरी 4–6 अंक।",
        "q_check": "अपना टिकट कैसे चेक करें?", "q_claim": "इनाम कैसे लें?",
        "updated": "अपडेट {t} IST", "home": "आज", "lang": "भाषा",
        "about": "हमारे बारे में", "contact": "संपर्क", "disclaimer": "अस्वीकरण", "privacy": "प्राइवेसी पॉलिसी",
        "about_body": "LottoDost हर ड्रॉ के कुछ ही मिनट में डियर लॉटरी का रिज़ल्ट देता है। नंबर प्रकाशित रिज़ल्ट शीट से पढ़े जाते हैं, पूरे हैं या नहीं यह जाँचा जाता है, और नागालैंड का सरकारी गज़ट आने पर उससे मिलाया जाता है। यह सरकारी वेबसाइट नहीं है और हम टिकट नहीं बेचते।",
        "contact_body": "सवाल, सुधार या कुछ हटवाने के लिए: {e}। आमतौर पर दो कामकाजी दिनों में जवाब देते हैं।",
        "notfound": "पेज नहीं मिला", "search_as": "लॉटरी संबाद, डियर लॉटरी रिज़ल्ट, नागालैंड लॉटरी संबाद नाम से भी खोजा जाता है।",
    },
}

APP_KEYS = """first_prize tier_1 tier_cons tier_2 tier_3 tier_4 tier_5 tier_n match_last4 match_last5 match_cons
state_lottery state_nagaland state_sikkim state_punjab tag_morning tag_day tag_night verified verified_long
pdf_only open_official_pdf open_sheet special_tag check_title check_hint ticket_number ticket_invalid check_button
no_win_title no_win_body need_full_number you_won maybe_first also_matches disclaimer_body safety_title safety_body
claim_info claim_other_state source_note info_schedule_note no_draw_day upcoming_special amount_crore amount_lakh
result_coming""".split()


def _unescape(s: str) -> str:
    return s.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").strip()


def app_strings(folder: str) -> dict[str, str]:
    out = {}
    for e in ET.parse(RES / folder / "strings.xml").getroot():
        name = e.get("name")
        if e.tag == "string":
            out[name] = _unescape("".join(e.itertext()))
        elif e.tag == "plurals":
            other = next((i for i in e if i.get("quantity") == "other"), None)
            if other is not None:
                out[name] = _unescape("".join(other.itertext()))
    return {k: v for k, v in out.items() if k in APP_KEYS}


def fmt(s: str, *args) -> str:
    """Android-style %1$s / %1$d placeholders."""
    for i, a in enumerate(args, 1):
        s = s.replace(f"%{i}$s", str(a)).replace(f"%{i}$d", str(a))
    return s


def esc(s) -> str:
    return html.escape(str(s), quote=True)


def rupees(n: int) -> str:
    s = str(n)
    if len(s) <= 3:
        return "₹" + s
    head, tail = s[:-3], s[-3:]
    groups = []
    while len(head) > 2:
        groups.insert(0, head[-2:])
        head = head[:-2]
    if head:
        groups.insert(0, head)
    return "₹" + ",".join(groups) + "," + tail


def time12(hhmm: str) -> str:
    h, m = (int(x) for x in hhmm.split(":"))
    return f"{h % 12 or 12}:{m:02d} {'PM' if h >= 12 else 'AM'}"


def short_time(hhmm: str) -> str:  # "1 PM" as people search it
    h, m = (int(x) for x in hhmm.split(":"))
    return f"{h % 12 or 12}{'' if m == 0 else f':{m:02d}'} {'PM' if h >= 12 else 'AM'}"


def ddmmyyyy(iso: str, sep: str = ".") -> str:
    y, m, d = iso.split("-")
    return f"{d}{sep}{m}{sep}{y}"


def game(name: str) -> str:
    """'DEAR RISE MONDAY WEEKLY LOTTERY' -> 'Dear Rise Monday' (titles read better than all caps)."""
    return name.removesuffix(" WEEKLY LOTTERY").strip().title()


def draw_slug(d: dict) -> str:
    return SLUG.get(d["slot"], d["slot"].lower().replace("_", "-"))


class Site:
    def __init__(self, data: pathlib.Path, base_url: str, now: dt.datetime | None = None):
        self.data = data
        self.base = base_url.rstrip("/")
        self.path_prefix = "/" + self.base.split("/", 3)[3] if self.base.count("/") >= 3 else ""
        self.now = now or dt.datetime.now(IST)
        self.today = self.now.date().isoformat()
        self.s = {code: app_strings(folder) for code, (folder, _) in LANGS.items()}
        self.days: dict[str, list[dict]] = {}
        for f in sorted((data / "v2/days").glob("*.json")):
            day = json.loads(f.read_text())
            self.days[day["date"]] = day["draws"]
        today_feed = data / "v2/today.json"
        if today_feed.exists():  # today.json is the freshest copy of the last two days
            for d in json.loads(today_feed.read_text())["draws"]:
                lst = self.days.setdefault(d["date"], [])
                lst[:] = [x for x in lst if x["id"] != d["id"]] + [d]
        self.updated = self.now
        if today_feed.exists():
            gen = json.loads(today_feed.read_text()).get("generatedAt")
            if gen:
                self.updated = dt.datetime.fromisoformat(gen).astimezone(IST)
        self.index = json.loads((data / "v2/index.json").read_text()) if (data / "v2/index.json").exists() else {}
        self.dates = sorted(self.days, reverse=True)
        self.adsense = os.environ.get("ADSENSE_CLIENT", "").strip()
        self.beacon = os.environ.get("CF_BEACON_TOKEN", "").strip()
        self.verify = {"google-site-verification": os.environ.get("GOOGLE_SITE_VERIFICATION", "").strip(),
                       "msvalidate.01": os.environ.get("BING_SITE_VERIFICATION", "").strip()}
        self.pages: list[tuple[str, dict[str, str]]] = []  # (key, {lang: path}) for the sitemap
        self._ads = 0
        self._keys: set[str] = set()

    # ---- URLs ----
    def url(self, lang: str, path: str) -> str:
        """Site-absolute path ("/india/bn/result/…/") for links."""
        return f"{self.path_prefix}{LANGS[lang][1]}{path}"

    def abs(self, lang: str, path: str) -> str:
        origin = self.base[: len(self.base) - len(self.path_prefix)] if self.path_prefix else self.base
        return origin + self.url(lang, path)

    @staticmethod
    def day_path(date: str) -> str:
        return f"/result/{ddmmyyyy(date, '-')}/"

    def draw_path(self, d: dict) -> str:
        return f"{self.day_path(d['date'])}{draw_slug(d)}/"

    # ---- text helpers ----
    def state(self, lang: str, st: str) -> str:
        s = self.s[lang]
        return {"Nagaland": s["state_nagaland"], "Sikkim": s["state_sikkim"], "Punjab": s["state_punjab"]}.get(st, st)

    def tier_title(self, lang: str, rank: str) -> str:
        s = self.s[lang]
        key = {"1": "tier_1", "CONS": "tier_cons", "2": "tier_2", "3": "tier_3", "4": "tier_4", "5": "tier_5"}.get(rank)
        return s[key] if key else fmt(s["tier_n"], rank)

    def match_hint(self, lang: str, m: str) -> str:
        return {"LAST4": self.s[lang]["match_last4"], "LAST5": self.s[lang]["match_last5"],
                "CONS": self.s[lang]["match_cons"]}.get(m, "")

    def prize_short(self, lang: str, n: int) -> str:
        s = self.s[lang]
        if n >= 10_000_000 and n % 10_000_000 == 0:
            return fmt(s["amount_crore"], n // 10_000_000)
        if n >= 100_000 and n % 100_000 == 0:
            return fmt(s["amount_lakh"], n // 100_000)
        return rupees(n)

    # ---- page shell ----
    def page(self, lang: str, path: str, title: str, desc: str, body: str, alternates: bool = True,
             data: list[dict] | None = None, live: bool = False, crumbs: list[tuple[str, str]] | None = None) -> str:
        w = WEB[lang]
        alt = ""
        if alternates:
            alt = "".join(f'<link rel="alternate" hreflang="{l}" href="{esc(self.abs(l, path))}">' for l in LANGS)
            alt += f'<link rel="alternate" hreflang="x-default" href="{esc(self.abs("en", path))}">'
        ads_head = (f'<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client={esc(self.adsense)}" '
                    f'crossorigin="anonymous"></script>') if self.adsense else ""
        beacon = (f'<script defer src="https://static.cloudflareinsights.com/beacon.min.js" '
                  f'data-cf-beacon=\'{{"token": "{esc(self.beacon)}"}}\'></script>') if self.beacon else ""
        ld = []
        if crumbs:
            ld.append({"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": [
                {"@type": "ListItem", "position": i + 1, "name": n, "item": self.abs(lang, p)} for i, (n, p) in enumerate(crumbs)]})
        ld_html = "".join(f'<script type="application/ld+json">{json.dumps(x, ensure_ascii=False)}</script>' for x in ld)
        langs_nav = " · ".join(
            f'<a href="{esc(self.url(l, path))}" hreflang="{l}" lang="{l}"{" aria-current=\"true\"" if l == lang else ""}>'
            f'{ {"en": "English", "bn": "বাংলা", "hi": "हिन्दी"}[l] }</a>' for l in LANGS) if alternates else ""
        data_json = json.dumps([{k: d[k] for k in ("id", "date", "time", "name", "format", "tiers") if k in d} for d in data or []],
                               ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
        live_attr = f' data-live="{esc(data[0]["date"])}"' if live and data else (f' data-live="{self.today}"' if live else "")
        self._ads = 0  # the body is built: the next page starts its own ad count
        foot = (f'<p>{esc(self.s[lang]["disclaimer_body"])}</p><p>{esc(self.s[lang]["source_note"])}</p>'
                f'<p><a href="{self.url("en", "/about/")}">{esc(w["about"])}</a> · <a href="{self.url("en", "/contact/")}">{esc(w["contact"])}</a> · '
                f'<a href="{self.url("en", "/disclaimer/")}">{esc(w["disclaimer"])}</a> · <a href="{self.path_prefix}/privacy.html">{esc(w["privacy"])}</a></p>')
        return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)}</title>
<meta name="description" content="{esc(desc)}">
<link rel="canonical" href="{esc(self.abs(lang, path))}">
{alt}
<meta property="og:title" content="{esc(title)}"><meta property="og:description" content="{esc(desc)}">
<meta property="og:type" content="website"><meta property="og:url" content="{esc(self.abs(lang, path))}">
<meta name="theme-color" content="#b3261e">
{"".join(f'<meta name="{k}" content="{esc(v)}">' for k, v in self.verify.items() if v)}
<style>{CSS}</style>
{ads_head}{ld_html}
</head>
<body{live_attr}>
<header class="top"><div class="wrap"><a class="brand" href="{self.url(lang, '/')}">LottoDost</a>
<nav aria-label="{esc(w['lang'])}">{langs_nav}</nav></div></header>
<main class="wrap">
{body}
</main>
<footer class="wrap foot">{foot}</footer>
<script type="application/json" id="draws">{data_json}</script>
<script type="application/json" id="i18n">{json.dumps(self.s[lang], ensure_ascii=False).replace("</", "<\\/")}</script>
<script src="{self.path_prefix}/assets/site.js" defer></script>
{beacon}
</body>
</html>
"""

    def ad(self) -> str:
        # At most 3 units per page and never inside the numbers: people come for the numbers.
        if not self.adsense or self._ads >= 3:
            return ""
        self._ads += 1
        return (f'<div class="ad"><ins class="adsbygoogle" style="display:block" data-ad-client="{esc(self.adsense)}" '
                f'data-ad-format="auto" data-full-width-responsive="true"></ins>'
                f'<script>(adsbygoogle=window.adsbygoogle||[]).push({{}});</script></div>')

    # ---- blocks ----
    def card(self, lang: str, d: dict, full: bool) -> str:
        s, w = self.s[lang], WEB[lang]
        tag = TAG.get(d["slot"])
        stub = f'<b>{esc(short_time(d["time"]))}</b>' + (f'<span>{esc(s[tag])}</span>' if tag else
                                                          (f'<span>{esc(s["special_tag"])}</span>' if d.get("special") else ""))
        head = (f'<div class="slip-h"><div class="stub">{stub}</div><div class="nm">'
                f'<h2><a href="{self.url(lang, self.draw_path(d))}">{esc(game(d["name"]))}</a></h2>'
                f'<small>{esc(fmt(s["state_lottery"], self.state(lang, d.get("state", ""))))}</small>'
                + (f'<em class="ok" title="{esc(s["verified_long"])}">✓ {esc(s["verified"])}</em>' if d.get("verified") else "")
                + "</div></div>")
        sources = {x["kind"]: x["url"] for x in d.get("sources", [])}
        if "lotterysambad" in sources and d["date"] == self.today:
            # Lottery Sambad serves the current day's sheet only from fetchtoday.php (checked 18-10-2026).
            sources["lotterysambad"] = sources["lotterysambad"].replace("lotteryoldresultsdown.php", "fetchtoday.php")
        if not d.get("tiers"):
            body = f'<p>{esc(s["pdf_only"])}</p>'
            if "official" in sources:
                body += f'<p><a rel="nofollow" href="{esc(sources["official"])}">{esc(s["open_official_pdf"])}</a></p>'
            if "lotterysambad" in sources:
                body += f'<p><a rel="nofollow" href="{esc(sources["lotterysambad"])}">{esc(s["open_sheet"])}</a></p>'
            return f'<section class="slip">{head}<div class="slip-b">{body}</div></section>'
        first = d["tiers"][0]
        out = [f'<div class="slip-b"><p class="eyebrow">{esc(s["first_prize"])} · {esc(self.prize_short(lang, first["prize"]))}</p>'
               f'<p class="first">{esc(first["numbers"][0])}</p>']
        if full:
            for i, t in enumerate(d["tiers"][1:]):
                hint = self.match_hint(lang, t["match"])
                nums = " ".join(f"<span>{esc(n)}</span>" for n in t["numbers"])
                out.append(f'<div class="tier"><div class="tier-h"><h3>{esc(self.tier_title(lang, t["rank"]))}</h3>'
                           f'<b>{esc(rupees(t["prize"]))}</b></div>'
                           + (f'<p class="hint">{esc(hint)}</p>' if hint else "") + f'<p class="nums">{nums}</p></div>')
                if i == 1:
                    out.append(self.ad())
            links = []
            if "lotterysambad" in sources:
                links.append(f'<a rel="nofollow" href="{esc(sources["lotterysambad"])}">{esc(s["open_sheet"])}</a>')
            if "official" in sources:
                links.append(f'<a rel="nofollow" href="{esc(sources["official"])}">{esc(s["open_official_pdf"])}</a>')
            if links:
                out.append('<p class="links">' + " · ".join(links) + "</p>")
        else:
            out.append(f'<p><a href="{self.url(lang, self.draw_path(d))}">{esc(WEB[lang]["result_of"].format(d=ddmmyyyy(d["date"])))} '
                       f'{esc(short_time(d["time"]))} →</a></p>')
        out.append("</div>")
        return f'<section class="slip">{head}{"".join(out)}</section>'

    def waiting(self, lang: str, slot: str, time: str, date: str) -> str:
        s, w = self.s[lang], WEB[lang]
        st = "Sikkim" if slot == "SK6PM" else "Nagaland"
        return (f'<section class="slip"><div class="slip-h"><div class="stub"><b>{esc(short_time(time))}</b>'
                f'<span>{esc(s[TAG[slot]])}</span></div><div class="nm"><h2>{esc(w["awaited"])}</h2>'
                f'<small>{esc(fmt(s["state_lottery"], self.state(lang, st)))}</small></div></div>'
                f'<div class="slip-b"><p>{esc(s["result_coming"] if date == self.today else w["awaited"])}</p></div></section>')

    def checker(self, lang: str) -> str:
        s = self.s[lang]
        return (f'<section class="check" id="check"><h2>{esc(s["check_title"])}</h2><p class="hint">{esc(WEB[lang]["check_hint"])}</p>'
                f'<form id="checkForm" class="row"><input id="ticket" aria-label="{esc(s["ticket_number"])}" placeholder="39C 06366" '
                f'autocomplete="off" autocapitalize="characters" spellcheck="false"><button>{esc(s["check_button"])}</button></form>'
                f'<div id="checkRes" aria-live="polite"></div></section>')

    def faq(self, lang: str) -> str:
        s, w = self.s[lang], WEB[lang]
        return (f'<section class="faq"><h2>{esc(w["faq"])}</h2>'
                f'<h3>{esc(w["q_time"])}</h3><p>{esc(w["a_time"])}</p>'
                f'<h3>{esc(w["q_check"])}</h3><p>{esc(w["check_hint"])}</p>'
                f'<h3>{esc(w["q_claim"])}</h3><p>{esc(s["claim_info"])}</p><p>{esc(s["claim_other_state"])}</p>'
                f'<h3>{esc(s["safety_title"])}</h3><p>{esc(s["safety_body"])}</p>'
                f'<p class="hint">{esc(w["search_as"])}</p></section>')

    def ordered(self, draws: list[dict]) -> list[dict]:
        order = {"NL1PM": 0, "SK6PM": 1, "NL8PM": 2, "NLLAXMI": 3}
        return sorted(draws, key=lambda d: (d["time"], order.get(d["slot"], 9)))

    def day_cards(self, lang: str, date: str, full: bool) -> str:
        draws = self.ordered(self.days.get(date, []))
        if date in self.index.get("noDraw", []):
            return f'<p class="banner">{esc(self.s[lang]["no_draw_day"])}</p>'
        parts = [(d["time"], self.card(lang, d, full)) for d in draws]
        have = {d["slot"] for d in draws}
        for slot, time, _ in WEEKLY:  # show the empty slots of today so people know a result is coming
            if date == self.today and slot not in have:
                parts.append((time, self.waiting(lang, slot, time, date)))
        return "".join(h for _, h in sorted(parts, key=lambda p: p[0]))

    def write(self, out: pathlib.Path, lang: str, path: str, content: str, key: str | None = None):
        f = out / (LANGS[lang][1].lstrip("/")) / path.strip("/") / "index.html" if path != "/404" else out / "404.html"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(content, encoding="utf-8")
        if key and key not in self._keys:  # one sitemap entry per page, listing all its languages
            self._keys.add(key)
            self.pages.append((key, {l: self.abs(l, path) for l in LANGS}))

    # ---- pages ----
    def build(self, out: pathlib.Path) -> int:
        latest = self.today if self.today in self.days or not self.dates else self.dates[0]
        for lang in LANGS:
            w, s = WEB[lang], self.s[lang]
            # home: today's results, refreshed by the page itself while a draw is due
            d0 = ddmmyyyy(latest)
            got = {d["slot"]: d for d in self.days.get(latest, []) if d.get("tiers")}
            summary = ", ".join(f'{short_time(t)} {got[sl]["tiers"][0]["numbers"][0] if sl in got else w["awaited"]}'
                                for sl, t, _ in WEEKLY)
            up = next((u for u in self.index.get("upcoming", []) if u["date"] >= self.today), None)
            body = (f'<h1>{esc(w["home_h1"])} <span class="d">{esc(d0)}</span></h1>'
                    f'<p class="hint">{esc(w["updated"].format(t=self.updated.strftime("%d.%m %H:%M")))}</p>'
                    + (f'<p class="banner">{esc(fmt(s["upcoming_special"], up["name"], ddmmyyyy(up["date"])))}</p>' if up else "")
                    + self.day_cards(lang, latest, full=True) + self.ad() + self.checker(lang)
                    + f'<p class="app">{esc(w["app"])}</p>' + self.recent(lang, skip=latest) + self.faq(lang))
            self.write(out, lang, "/", self.page(lang, "/", w["home_title"].format(d=d0), w["home_desc"].format(d=d0, list=summary),
                                                 body, data=list(got.values()), live=latest == self.today,
                                                 crumbs=[(w["home"], "/")]), key="home")
            # one page per day and per draw
            for i, date in enumerate(self.dates):
                dd = ddmmyyyy(date)
                nav = []
                if i + 1 < len(self.dates):
                    nav.append(f'<a href="{self.url(lang, self.day_path(self.dates[i + 1]))}">← {esc(w["prev"])}</a>')
                if i > 0:
                    nav.append(f'<a href="{self.url(lang, self.day_path(self.dates[i - 1]))}">{esc(w["next"])} →</a>')
                draws = [d for d in self.days[date] if d.get("tiers")]
                body = (f'<h1>{esc(w["day_h1"].format(d=dd))}</h1>' + self.day_cards(lang, date, full=True)
                        + f'<p class="nav">{" · ".join(nav)}</p>' + self.checker(lang))
                self.write(out, lang, self.day_path(date),
                           self.page(lang, self.day_path(date), w["day_title"].format(d=dd),
                                     w["home_desc"].format(d=dd, list=", ".join(f'{short_time(d["time"])} {d["tiers"][0]["numbers"][0]}'
                                                                                  for d in self.ordered(draws))) if draws else w["day_title"].format(d=dd),
                                     body, data=draws, live=date == self.today,
                                     crumbs=[(w["home"], "/"), (dd, self.day_path(date))]), key=f"day:{date}")
                for d in self.days[date]:
                    if not d.get("tiers"):
                        continue  # nothing to read on a PDF-only draw: the day page links the PDF
                    t, g, first = short_time(d["time"]), game(d["name"]), d["tiers"][0]["numbers"][0]
                    title = w["draw_title"].format(s=self.state(lang, d.get("state", "")), d=dd, t=t, g=g)
                    body = (f'<h1>{esc(title)}</h1>' + self.card(lang, d, full=True) + self.checker(lang)
                            + f'<p class="nav"><a href="{self.url(lang, self.day_path(date))}">{esc(w["all_of_day"].format(d=dd))}</a></p>'
                            + f'<p class="app">{esc(w["app"])}</p>')
                    self.write(out, lang, self.draw_path(d),
                               self.page(lang, self.draw_path(d), title, w["draw_desc"].format(g=g, d=dd, t=t, n=first), body,
                                         data=[d], crumbs=[(w["home"], "/"), (dd, self.day_path(date)), (t, self.draw_path(d))]),
                               key=f"draw:{d['id']}")
            # archive
            rows = "".join(f'<li><a href="{self.url(lang, self.day_path(x))}">{esc(ddmmyyyy(x))}</a></li>' for x in self.dates)
            self.write(out, lang, "/old-results/", self.page(lang, "/old-results/", w["old_title"], w["old_title"],
                                                             f'<h1>{esc(w["old_h1"])}</h1><ul class="dates">{rows}</ul>',
                                                             crumbs=[(w["home"], "/"), (w["old_h1"], "/old-results/")]), key="old")
        # English-only trust pages (required by ad networks; also what visitors look for)
        w = WEB["en"]
        for path, title, text in (("/about/", w["about"], w["about_body"]),
                                  ("/contact/", w["contact"], w["contact_body"].format(e=CONTACT)),
                                  ("/disclaimer/", w["disclaimer"], self.s["en"]["disclaimer_body"] + " " + self.s["en"]["safety_body"])):
            self.write(out, "en", path, self.page("en", path, f"{title} – LottoDost", text, f"<h1>{esc(title)}</h1><p>{esc(text)}</p>",
                                                  alternates=False), key=f"page:{path}")
        self.write(out, "en", "/404", self.page("en", "/404/", w["notfound"], w["notfound"],
                                               f'<h1>{esc(w["notfound"])}</h1><p><a href="{self.url("en", "/")}">LottoDost</a></p>',
                                               alternates=False))
        self.assets(out)
        return len(self.pages)

    def recent(self, lang: str, skip: str) -> str:
        w = WEB[lang]
        items = "".join(f'<li><a href="{self.url(lang, self.day_path(x))}">{esc(w["result_of"].format(d=ddmmyyyy(x)))}</a></li>'
                        for x in [x for x in self.dates if x != skip][:7])
        return (f'<section><h2>{esc(w["older"])}</h2><ul class="dates">{items}</ul>'
                f'<p><a href="{self.url(lang, "/old-results/")}">{esc(w["old_h1"])} →</a></p></section>')

    def assets(self, out: pathlib.Path):
        (out / "assets").mkdir(parents=True, exist_ok=True)
        (out / "assets/site.js").write_text(JS, encoding="utf-8")
        urls = []
        for key, alts in self.pages:
            for lang, loc in alts.items():
                if key.startswith("page:") and lang != "en":
                    continue
                links = "" if key.startswith("page:") else "".join(
                    f'<xhtml:link rel="alternate" hreflang="{l}" href="{esc(u)}"/>' for l, u in alts.items())
                urls.append(f"<url><loc>{esc(loc)}</loc>{links}</url>")
        (out / "sitemap.xml").write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
            'xmlns:xhtml="http://www.w3.org/1999/xhtml">' + "".join(urls) + "</urlset>\n", encoding="utf-8")
        (out / "robots.txt").write_text(f"User-agent: *\nAllow: /\nSitemap: {self.abs('en', '/sitemap.xml').replace(LANGS['en'][1], '')}\n")
        ads = out / "ads.txt"
        if self.adsense:
            ads.write_text(f"google.com, {self.adsense.replace('ca-pub-', 'pub-')}, DIRECT, f08c47fec0942fa0\n")
        elif ads.exists():
            ads.unlink()


CSS = """:root{--bg:#f7f3f1;--fg:#231918;--mut:#6b5552;--card:#fff;--line:#e2d4d0;--red:#b3261e;--onred:#fff;--gold:#ffe08a;--ok:#1b7f3b}
@media (prefers-color-scheme:dark){:root{--bg:#191514;--fg:#f1dfdc;--mut:#c9b2ae;--card:#231e1d;--line:#433835;--red:#ffb4a9;--onred:#410001;--gold:#5c4300;--ok:#9bd5a5}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,"Noto Sans","Noto Sans Bengali","Noto Sans Devanagari",sans-serif}
.wrap{max-width:40rem;margin:0 auto;padding:0 16px}a{color:var(--red)}h1{font-size:1.4rem;line-height:1.25;margin:16px 0 4px}h1 .d{white-space:nowrap}
h2{font-size:1.05rem;margin:0}h3{font-size:1rem;margin:0}.top{background:var(--red)}.top .wrap{display:flex;gap:12px;align-items:center;justify-content:space-between;padding:10px 16px;flex-wrap:wrap}
.top a{color:var(--onred);text-decoration:none}.brand{font-weight:800;font-size:1.15rem}.top nav{font-size:.9rem}.top nav [aria-current]{text-decoration:underline}
.hint{color:var(--mut);font-size:.9rem;margin:4px 0}.banner{background:var(--gold);border-radius:10px;padding:10px 12px}
.slip{background:var(--card);border:1px solid var(--line);border-radius:10px;margin:12px 0;overflow:hidden}
.slip-h{display:flex;border-bottom:1px dashed var(--line)}.stub{background:var(--red);color:var(--onred);min-width:76px;padding:12px 8px;text-align:center;display:flex;flex-direction:column;justify-content:center}
.stub b{font-size:1rem}.stub span{font-size:.8rem}.nm{padding:10px 14px;min-width:0}.nm h2 a{color:var(--fg);text-decoration:none}.nm small{color:var(--mut)}.ok{color:var(--ok);font-style:normal;font-weight:700;font-size:.85rem;display:block}
.slip-b{padding:10px 14px}.eyebrow{color:var(--mut);font-size:.85rem;margin:0}.first{font:800 clamp(1.9rem,9vw,2.5rem)/1.15 ui-monospace,"Roboto Mono",monospace;color:var(--red);margin:2px 0 6px;white-space:nowrap}
.tier{border-top:1px solid var(--line);padding:8px 0}.tier-h{display:flex;justify-content:space-between;gap:8px}.tier-h b{color:var(--red);white-space:nowrap}
.nums{display:flex;flex-wrap:wrap;gap:2px 12px;font:600 1.02rem/1.6 ui-monospace,"Roboto Mono",monospace;margin:4px 0}.nums .hit{background:var(--gold);border-radius:3px}
.links,.nav{font-size:.95rem}.check{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px;margin:16px 0}
.row{display:flex;gap:8px;flex-wrap:wrap}.row input{flex:1 1 11rem;min-width:0;font:700 1.15rem ui-monospace,monospace;padding:10px;border:1px solid var(--line);border-radius:8px;background:var(--bg);color:var(--fg);text-transform:uppercase}
.row button{font:inherit;font-weight:700;padding:10px 20px;border:0;border-radius:999px;background:var(--red);color:var(--onred)}
#checkRes>div{margin-top:8px;padding:10px;border-radius:8px;background:var(--bg)}#checkRes .won{border-left:4px solid var(--ok)}
.app{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:10px 12px}.dates{columns:2;padding-left:18px}
.faq p{margin:4px 0 10px}.foot{color:var(--mut);font-size:.8rem;padding:24px 16px}.ad{margin:12px 0;min-height:90px}
"""

JS = r"""(function(){"use strict";
var S=JSON.parse(document.getElementById("i18n").textContent),D=JSON.parse(document.getElementById("draws").textContent);
function t(k){var s=S[k]||k,a=[].slice.call(arguments,1);return s.replace(/%(\d)\$[sd]/g,function(_,n){return a[n-1]})}
function rup(n){var s=String(n);if(s.length<=3)return"₹"+s;return"₹"+s.slice(0,-3).replace(/\B(?=(\d{2})+(?!\d))/g,",")+","+s.slice(-3)}
function tier(r){var k={"1":"tier_1",CONS:"tier_cons","2":"tier_2","3":"tier_3","4":"tier_4","5":"tier_5"}[r];return k?t(k):t("tier_n",r)}
function parse(raw){var s=raw.replace(/[०-९]/g,function(d){return d.charCodeAt(0)-0x966}).replace(/[০-৯]/g,function(d){return d.charCodeAt(0)-0x9e6});
s=s.trim().toUpperCase().replace(/[-_./,]/g," ").replace(/\s+/g," ");
function p(x){var m=/^(\d{2})\s*([A-Z])\s*(\d{5})$/.exec(x);if(m)return(+m[1]>=34&&"ABCDEGHJKL".indexOf(m[2])>=0)?{pre:m[1]+m[2],dg:m[3],f:"D5"}:false;
if((m=/^(\d{2})\s+(\d{4})$/.exec(x)))return{pre:m[1],dg:m[2],f:"D4"};if((m=/^([A-Z]{1,2})\s*(\d{6})$/.exec(x)))return{pre:m[1],dg:m[2],f:"B6"};return null}
var r=p(s);if(r)return r;if(r===false)return null;var c=s.replace(/ /g,"");if(c!==s){r=p(c);if(r)return r;if(r===false)return null}
return/^\d{4,6}$/.test(c)?{pre:null,dg:c,f:"DIGITS"}:null}
function show(k){return k.pre?k.pre+" "+k.dg:k.dg}
function compat(k,d){if(d.format==="D5")return k.f==="D5"||(k.f==="DIGITS"&&k.dg.length<=5);if(d.format==="D4")return k.f==="D4"||(k.f==="DIGITS"&&k.dg.length===4);if(d.format==="B6")return k.f==="B6"||k.f==="DIGITS";return false}
function check(k,d){if(!d.tiers||!d.tiers.length||!compat(k,d))return null;var w=[],n=k.dg.length,full=k.f!=="DIGITS",sh=show(k);
d.tiers.forEach(function(T){var N=T.numbers||[];if(T.match==="FULL"){var f=N[0];if(!f)return;if(full){if(sh===f)w.push({T:T})}else if(n===5&&k.dg===f.slice(-5))w.push({T:T,nf:1})}
else if(T.match==="CONS"){if(n<5)return;var first=full&&(d.tiers[0].numbers||[])[0]===sh;if(!first&&N.indexOf(k.dg)>=0)w.push({T:T})}
else if(T.match==="EXACT"){if(full){if(N.indexOf(sh)>=0)w.push({T:T})}else if(n===6&&N.some(function(x){return x.slice(-6)===k.dg}))w.push({T:T,nf:1})}
else if(T.match==="LAST5"){if(n>=5&&N.indexOf(k.dg.slice(-5))>=0)w.push({T:T})}else if(T.match==="LAST4"){if(N.indexOf(k.dg.slice(-4))>=0)w.push({T:T})}});
w.sort(function(a,b){return b.T.prize-a.T.prize});return{d:d,w:w}}
function el(tag,cls,txt){var e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e}
var f=document.getElementById("checkForm");
if(f)f.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("checkRes");out.textContent="";
var v=document.getElementById("ticket").value;if(!v.trim())return;var k=parse(v);if(!k){out.appendChild(el("div",null,t("ticket_invalid")));return}
var cs=D.map(function(d){return check(k,d)}).filter(Boolean),won=cs.filter(function(c){return c.w.length});
won.forEach(function(c){var b=el("div","won"),x=c.w[0];b.appendChild(el("strong",null,x.nf?t("maybe_first"):t("you_won")));
b.appendChild(el("div",null,tier(x.T.rank)+" · "+rup(x.T.prize)));b.appendChild(el("div","hint",c.d.name.replace(/ WEEKLY LOTTERY$/,"")));
if(x.nf)b.appendChild(el("div","hint",t("need_full_number")));out.appendChild(b)});
if(!won.length){var b=el("div");b.appendChild(el("strong",null,t("no_win_title",show(k))));b.appendChild(el("div","hint",t("no_win_body",cs.length)));out.appendChild(b)}
var dg=k.dg;document.querySelectorAll(".nums span").forEach(function(s){var x=s.textContent;s.classList.toggle("hit",dg.length>=x.length&&dg.slice(-x.length)===x)})});
// Today's pages refresh themselves while a result is due (static pages, so a reload is cheap).
var day=document.body.dataset.live;
if(day){var shown=D.length,url=document.querySelector('script[src$="/assets/site.js"]').getAttribute("src").replace(/assets\/site\.js$/,"v2/today.json");
setInterval(function(){if(document.hidden)return;fetch(url,{cache:"no-cache"}).then(function(r){return r.json()}).then(function(j){
var n=j.draws.filter(function(d){return d.date===day&&d.tiers&&d.tiers.length}).length;if(n>shown)location.reload()}).catch(function(){})},60000)}
})();
"""


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=pathlib.Path, required=True, help="folder containing v2/")
    ap.add_argument("--out", type=pathlib.Path, required=True)
    ap.add_argument("--base-url", default=os.environ.get("SITE_URL", "https://entrepreneur123.github.io/india"))
    a = ap.parse_args(argv)
    n = Site(a.data, a.base_url).build(a.out)
    print(f"site: {n} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
