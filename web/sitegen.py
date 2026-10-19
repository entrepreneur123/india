"""Static, search-friendly results site generated from the v2 feeds.

Every draw gets its own pre-rendered page (readable without JavaScript, fast on 2G), every day
has a page, and the home page shows today. English at the root, Bengali under /bn/ and Hindi
under /hi/, linked with hreflang. Wording comes from the Android app's strings.xml plus the
page-level texts below, so app and site say the same thing.

    python web/sitegen.py --data public --out public --base-url https://example.in
Optional environment: ADSENSE_CLIENT (ca-pub-…) adds the AdSense script (Auto ads) and ads.txt, and
ADSENSE_SLOT (a display ad unit id) adds in-page units; PLAY_URL links the app once it is on Google Play;
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
import re
import urllib.parse
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
        "wd": "Mon Tue Wed Thu Fri Sat Sun".split(), "copy": "Copy link", "copied": "Link copied", "date": "Date",
        "open_page": "Open the full result page",
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
        "wd": "সোম মঙ্গল বুধ বৃহস্পতি শুক্র শনি রবি".split(), "copy": "লিংক কপি", "copied": "লিংক কপি হয়েছে", "date": "তারিখ",
        "open_page": "পুরো রেজাল্টের পেজ খুলুন",
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
        "wd": "सोम मंगल बुध गुरु शुक्र शनि रवि".split(), "copy": "लिंक कॉपी करें", "copied": "लिंक कॉपी हो गया", "date": "तारीख़",
        "open_page": "पूरे रिज़ल्ट का पेज खोलें",
    },
}

APP_KEYS = """first_prize tier_1 tier_cons tier_2 tier_3 tier_4 tier_5 tier_n match_last4 match_last5 match_cons
state_lottery state_nagaland state_sikkim state_punjab tag_morning tag_day tag_night verified verified_long
pdf_only open_official_pdf open_sheet special_tag check_title check_hint ticket_number ticket_invalid check_button
no_win_title no_win_body need_full_number you_won maybe_first also_matches disclaimer_body safety_title safety_body
claim_info claim_other_state source_note info_schedule_note no_draw_day upcoming_special amount_crore amount_lakh
result_coming today more_dates draw_in live result_delayed tap_full_result draw_meta draw_meta_special find_in_result
share_title share_more share_short tab_results tab_check results_subtitle""".split()


def _unescape(s: str) -> str:
    return s.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").strip()


def app_strings(folder: str) -> dict[str, str]:
    out = {}
    for e in ET.parse(RES / folder / "strings.xml").getroot():
        name = e.get("name")
        if e.tag == "string":
            out[name] = _unescape("".join(e.itertext()))
        elif e.tag == "plurals":
            for item in e:  # "other" is the plain key; "one" is kept as key_one for counts of 1
                q = item.get("quantity")
                if q in ("one", "other"):
                    out[name if q == "other" else name + "_one"] = _unescape("".join(item.itertext()))
    return {k: v for k, v in out.items() if k in APP_KEYS or k.removesuffix("_one") in APP_KEYS}


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


def verification(value: str) -> str:
    """Accepts the bare code or the whole <meta ... content="code"> tag people copy from the console."""
    m = re.search(r'content\s*=\s*["\']([^"\']+)', value)
    return (m.group(1) if m else value).strip()


def beacon_token(value: str) -> str:
    """Accepts the bare token or the whole Cloudflare snippet (… data-cf-beacon='{"token": "…"}' …)."""
    m = re.search(r'"token"\s*:\s*"([^"]+)"', value)
    return (m.group(1) if m else value).strip()


def draw_slug(d: dict) -> str:
    return SLUG.get(d["slot"], d["slot"].lower().replace("_", "-"))


class Site:
    def __init__(self, data: pathlib.Path, base_url: str, now: dt.datetime | None = None,
                 link_prefix: str | None = None):
        self.data = data
        self.base = base_url.rstrip("/")
        if link_prefix is None:  # served where base_url points
            link_prefix = "/" + self.base.split("/", 3)[3] if self.base.count("/") >= 3 else ""
        # Links follow where this copy is served (a mirror at a host root uses ""); canonical URLs stay on base_url.
        self.path_prefix = link_prefix.rstrip("/")
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
        self.play_url = os.environ.get("PLAY_URL", "").strip()
        # A manual unit needs its slot id (AdSense → Ads → By ad unit); without one, Auto ads alone place ads.
        self.ad_slot = os.environ.get("ADSENSE_SLOT", "").strip()
        self.beacon = beacon_token(os.environ.get("CF_BEACON_TOKEN", ""))
        self.verify = {"google-site-verification": verification(os.environ.get("GOOGLE_SITE_VERIFICATION", "")),
                       "msvalidate.01": verification(os.environ.get("BING_SITE_VERIFICATION", ""))}
        self.pages: list[tuple[str, dict[str, str]]] = []  # (key, {lang: path}) for the sitemap
        self._ads = 0
        self._keys: set[str] = set()

    # ---- URLs ----
    def url(self, lang: str, path: str) -> str:
        """Site-absolute path ("/india/bn/result/…/") for links."""
        return f"{self.path_prefix}{LANGS[lang][1]}{path}"

    def abs(self, lang: str, path: str) -> str:
        return f"{self.base}{LANGS[lang][1]}{path}"

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
             data: list[dict] | None = None, live: bool = False, crumbs: list[tuple[str, str]] | None = None,
             tab: str = "") -> str:
        w, s = WEB[lang], self.s[lang]
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
        nav_lang = lang if alternates else "en"
        langs_nav = "".join(
            f'<a href="{esc(self.url(l, path))}" hreflang="{l}" lang="{l}"{" aria-current=\"true\"" if l == lang else ""}>'
            f'{ {"en": "EN", "bn": "বাংলা", "hi": "हिन्दी"}[l] }</a>' for l in LANGS) if alternates else ""
        check_href = "#check" if 'id="check"' in body else self.url(nav_lang, "/") + "#check"
        tabs = [("results", s["tab_results"], self.url(nav_lang, "/")), ("check", s["tab_check"], check_href),
                ("old", w["old_h1"], self.url(nav_lang, "/old-results/"))]
        tabs_html = "".join(f'<a href="{esc(h)}"{" aria-current=\"page\"" if k == tab else ""}>{esc(n)}</a>' for k, n, h in tabs)
        data_json = json.dumps([{k: d[k] for k in ("id", "date", "time", "name", "format", "tiers") if k in d} for d in data or []],
                               ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
        live_attr = f' data-live="{esc(data[0]["date"])}"' if live and data else (f' data-live="{self.today}"' if live else "")
        self._ads = 0  # the body is built: the next page starts its own ad count
        i18n = dict(self.s[lang], copied=w["copied"])
        foot = (f'<p class="links"><a href="{self.url("en", "/about/")}">{esc(w["about"])}</a><a href="{self.url("en", "/contact/")}">{esc(w["contact"])}</a>'
                f'<a href="{self.url("en", "/disclaimer/")}">{esc(w["disclaimer"])}</a><a href="{self.path_prefix}/privacy.html">{esc(w["privacy"])}</a></p>'
                f'<p>{esc(s["disclaimer_body"])}</p><p>{esc(s["source_note"])}</p>')
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
<header class="top"><div class="wrap bar"><a class="brand" href="{self.url(nav_lang, '/')}">LottoDost</a>
<nav class="langs" aria-label="{esc(w['lang'])}">{langs_nav}</nav></div>
<nav class="wrap tabs">{tabs_html}</nav></header>
<main class="wrap">
{body}
</main>
<footer class="foot"><div class="wrap">{foot}</div></footer>
<script type="application/json" id="draws">{data_json}</script>
<script type="application/json" id="i18n">{json.dumps(i18n, ensure_ascii=False).replace("</", "<\\/")}</script>
<script src="{self.path_prefix}/assets/site.js" defer></script>
{beacon}
</body>
</html>
"""

    def ad(self) -> str:
        # At most 3 units per page and never inside the numbers: people come for the numbers.
        if not self.adsense or not self.ad_slot or self._ads >= 3:
            return ""
        self._ads += 1
        return (f'<div class="ad"><ins class="adsbygoogle" style="display:block" data-ad-client="{esc(self.adsense)}" '
                f'data-ad-slot="{esc(self.ad_slot)}" data-ad-format="auto" data-full-width-responsive="true"></ins>'
                f'<script>(adsbygoogle=window.adsbygoogle||[]).push({{}});</script></div>')

    # ---- blocks ----
    def stub(self, lang: str, d: dict) -> str:
        s, tag = self.s[lang], TAG.get(d["slot"])
        sub = s[tag] if tag else (s["special_tag"] if d.get("special") else "")
        return f'<div class="stub"><b>{esc(short_time(d["time"]))}</b>' + (f'<span>{esc(sub)}</span>' if sub else "") + "</div>"

    def sources(self, d: dict) -> dict[str, str]:
        src = {x["kind"]: x["url"] for x in d.get("sources", [])}
        if "lotterysambad" in src and d["date"] == self.today:
            # Lottery Sambad serves the current day's sheet only from fetchtoday.php (checked 18-10-2026).
            src["lotterysambad"] = src["lotterysambad"].replace("lotteryoldresultsdown.php", "fetchtoday.php")
        return src

    def source_buttons(self, lang: str, d: dict) -> str:
        s, src = self.s[lang], self.sources(d)
        b = [f'<a class="btn" rel="nofollow" href="{esc(src[k])}">{esc(s[t])}</a>'
             for k, t in (("lotterysambad", "open_sheet"), ("official", "open_official_pdf")) if k in src]
        return f'<p class="btns">{"".join(b)}</p>' if b else ""

    def verified(self, lang: str, d: dict) -> str:
        s = self.s[lang]
        return f' <em class="ok" title="{esc(s["verified_long"])}">✓ {esc(s["verified"])}</em>' if d.get("verified") else ""

    def tiers(self, lang: str, d: dict, ads: bool = False) -> str:
        out = []
        for i, t in enumerate(d["tiers"][1:]):
            hint = self.match_hint(lang, t["match"])
            width = max((len(n) for n in t["numbers"]), default=4)
            cls = "n4" if width <= 4 else "n5" if width <= 5 else "n6"
            nums = "".join(f"<li>{esc(n)}</li>" for n in t["numbers"])
            out.append(f'<div class="tier"><div class="th"><h3>{esc(self.tier_title(lang, t["rank"]))}</h3>'
                       f'<b>{esc(rupees(t["prize"]))}</b></div>'
                       + (f'<p class="hint">{esc(hint)}</p>' if hint else "") + f'<ul class="nums {cls}">{nums}</ul></div>')
            if ads and i == 1:
                out.append(self.ad())
        return "".join(out)

    def slip(self, lang: str, d: dict) -> str:
        """A result as a ticket: time stub, game, the 1st prize, and every other prize one tap away."""
        s = self.s[lang]
        page = self.url(lang, self.draw_path(d))
        name = (f'<a href="{page}">{esc(game(d["name"]))}</a>' if d.get("tiers")  # PDF-only draws get no page of their own
                else esc(game(d["name"])))
        head = (f'{self.stub(lang, d)}<div class="sb"><h2>{name}</h2>'
                f'<p class="meta">{esc(fmt(s["state_lottery"], self.state(lang, d.get("state", ""))))}{self.verified(lang, d)}</p>')
        if not d.get("tiers"):
            return f'<section class="slip">{head}<p>{esc(s["pdf_only"])}</p>{self.source_buttons(lang, d)}</div></section>'
        first = d["tiers"][0]
        return (f'<section class="slip" id="{esc(draw_slug(d))}">{head}'
                f'<p class="eyebrow">{esc(s["first_prize"])} · {esc(self.prize_short(lang, first["prize"]))}</p>'
                f'<p class="first">{esc(first["numbers"][0])}</p></div>'
                f'<details class="all"><summary>{esc(s["tap_full_result"])}</summary><div class="in">{self.tiers(lang, d)}'
                f'{self.source_buttons(lang, d)}<p><a class="go" href="{page}">{esc(WEB[lang]["open_page"])} →</a></p></div></details>'
                f'</section>')

    def waiting(self, lang: str, slot: str, time: str, date: str) -> str:
        s = self.s[lang]
        st = "Sikkim" if slot == "SK6PM" else "Nagaland"
        at = f"{date}T{time}:00+05:30"
        return (f'<section class="slip wait">{self.stub(lang, {"slot": slot, "time": time})}<div class="sb">'
                f'<h2>Dear {esc(short_time(time))}</h2><p class="meta">{esc(fmt(s["state_lottery"], self.state(lang, st)))}</p>'
                f'<p class="when" data-at="{at}">{esc(s["result_coming"])}</p></div></section>')

    def sheet(self, lang: str, d: dict) -> str:
        """The draw page's header, shaped like the printed result sheet."""
        s = self.s[lang]
        first = d["tiers"][0]
        st = self.state(lang, d.get("state", ""))
        if d.get("price") and d.get("drawNo") and not d.get("special"):
            meta = fmt(s["draw_meta"], st, d["drawNo"], d["price"])
        elif d.get("price"):
            meta = fmt(s["draw_meta_special"], st, d["price"])
        else:
            meta = fmt(s["state_lottery"], st)
        y, m, dd = d["date"].split("-")
        when = f'{WEB[lang]["wd"][dt.date(int(y), int(m), int(dd)).weekday()]}, {ddmmyyyy(d["date"])} · {time12(d["time"])}'
        return (f'<article class="sheet"><div class="band"><h2>{esc(d["name"])}</h2><p>{esc(meta)}</p></div>'
                f'<div class="hero"><p class="eyebrow">{esc(s["first_prize"])} · {esc(rupees(first["prize"]))}</p>'
                f'<p class="first">{esc(first["numbers"][0])}</p><p class="meta">{esc(when)}{self.verified(lang, d)}</p></div></article>')

    def share(self, lang: str, d: dict) -> str:
        s = self.s[lang]
        url = self.abs(lang, self.draw_path(d))
        text = fmt(s["share_short"], game(d["name"]), f'{ddmmyyyy(d["date"])} {short_time(d["time"])}', d["tiers"][0]["numbers"][0], url)
        q = urllib.parse.quote
        links = [("wa", "WhatsApp", f"https://wa.me/?text={q(text)}"),
                 ("tg", "Telegram", f"https://t.me/share/url?url={q(url)}&text={q(text.rsplit(chr(10), 1)[0])}"),
                 ("fb", "Facebook", f"https://www.facebook.com/sharer/sharer.php?u={q(url)}"),
                 ("x", "X", f"https://x.com/intent/tweet?text={q(text.rsplit(chr(10), 1)[0])}&url={q(url)}")]
        a = "".join(f'<a class="{c}" rel="nofollow noopener" target="_blank" href="{esc(h)}">{n}</a>' for c, n, h in links)
        return (f'<section class="share" aria-label="{esc(s["share_title"])}"><p class="eyebrow">{esc(s["share_title"])}</p>'
                f'<div class="row">{a}<button type="button" class="cp" data-url="{esc(url)}">{esc(WEB[lang]["copy"])}</button>'
                f'<button type="button" class="more" hidden data-url="{esc(url)}" data-text="{esc(text)}">{esc(s["share_more"])}</button></div></section>')

    def checker(self, lang: str, title: str | None = None) -> str:
        s = self.s[lang]
        return (f'<section class="check" id="check"><h2>{esc(title or s["check_title"])}</h2><p class="hint">{esc(WEB[lang]["check_hint"])}</p>'
                f'<form id="checkForm" class="row"><input id="ticket" aria-label="{esc(s["ticket_number"])}" placeholder="39C 06366" '
                f'autocomplete="off" autocapitalize="characters" spellcheck="false" inputmode="text"><button>{esc(s["check_button"])}</button></form>'
                f'<div id="checkRes" aria-live="polite"></div></section>')

    def faq(self, lang: str) -> str:
        s, w = self.s[lang], WEB[lang]
        qa = [(w["q_time"], [w["a_time"]]), (w["q_check"], [w["check_hint"]]),
              (w["q_claim"], [s["claim_info"], s["claim_other_state"]]), (s["safety_title"], [s["safety_body"]])]
        items = "".join(f'<details><summary>{esc(q)}</summary>' + "".join(f"<p>{esc(a)}</p>" for a in ans) + "</details>"
                        for q, ans in qa)
        return f'<section class="faq"><h2>{esc(w["faq"])}</h2>{items}<p class="hint">{esc(w["search_as"])}</p></section>'

    def chips(self, lang: str, current: str) -> str:
        """Recent dates as one row of chips, like the app's date picker."""
        s, w = self.s[lang], WEB[lang]
        out = []
        for x in self.dates[:7]:
            y, m, d = x.split("-")
            label = s["today"] if x == self.today else w["wd"][dt.date(int(y), int(m), int(d)).weekday()]
            cur = ' aria-current="page"' if x == current else ""
            out.append(f'<a class="chip"{cur} href="{self.url(lang, self.day_path(x))}"><b>{esc(label)}</b>{d}.{m}</a>')
        out.append(f'<a class="chip" href="{self.url(lang, "/old-results/")}"><b>→</b>{esc(s["more_dates"])}</a>')
        return f'<nav class="chips" aria-label="{esc(w["older"])}">{"".join(out)}</nav>'

    def ordered(self, draws: list[dict]) -> list[dict]:
        order = {"NL1PM": 0, "SK6PM": 1, "NL8PM": 2, "NLLAXMI": 3}
        return sorted(draws, key=lambda d: (d["time"], order.get(d["slot"], 9)))

    def day_cards(self, lang: str, date: str) -> str:
        draws = self.ordered(self.days.get(date, []))
        if date in self.index.get("noDraw", []):
            return f'<p class="banner">{esc(self.s[lang]["no_draw_day"])}</p>'
        parts = [(d["time"], self.slip(lang, d)) for d in draws]
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
            body = (f'<div class="intro"><h1>{esc(w["home_h1"])} <span class="d">{esc(d0)}</span></h1>'
                    f'<p class="hint">{esc(s["results_subtitle"])} · {esc(w["updated"].format(t=self.updated.strftime("%d.%m %H:%M")))}</p></div>'
                    + self.chips(lang, latest)
                    + (f'<p class="banner">{esc(fmt(s["upcoming_special"], up["name"], ddmmyyyy(up["date"])))}</p>' if up else "")
                    + self.day_cards(lang, latest) + self.ad() + self.checker(lang) + self.app_promo(lang) + self.faq(lang))
            self.write(out, lang, "/", self.page(lang, "/", w["home_title"].format(d=d0), w["home_desc"].format(d=d0, list=summary),
                                                 body, data=list(got.values()), live=latest == self.today,
                                                 crumbs=[(w["home"], "/")], tab="results"), key="home")
            # one page per day and per draw
            for i, date in enumerate(self.dates):
                dd = ddmmyyyy(date)
                nav = []
                if i + 1 < len(self.dates):
                    nav.append(f'<a class="btn" href="{self.url(lang, self.day_path(self.dates[i + 1]))}">← {esc(w["prev"])}</a>')
                if i > 0:
                    nav.append(f'<a class="btn" href="{self.url(lang, self.day_path(self.dates[i - 1]))}">{esc(w["next"])} →</a>')
                draws = [d for d in self.ordered(self.days[date]) if d.get("tiers")]
                body = (f'<div class="intro"><h1>{esc(w["day_h1"].format(d=dd))}</h1></div>' + self.chips(lang, date)
                        + self.day_cards(lang, date) + self.ad() + f'<p class="pn">{"".join(nav)}</p>' + self.checker(lang))
                self.write(out, lang, self.day_path(date),
                           self.page(lang, self.day_path(date), w["day_title"].format(d=dd),
                                     w["home_desc"].format(d=dd, list=", ".join(f'{short_time(d["time"])} {d["tiers"][0]["numbers"][0]}'
                                                                                  for d in draws)) if draws else w["day_title"].format(d=dd),
                                     body, data=draws, live=date == self.today,
                                     crumbs=[(w["home"], "/"), (dd, self.day_path(date))], tab="results"), key=f"day:{date}")
                for d in draws:  # PDF-only draws have nothing to read: the day page links the PDF
                    t, g, first = short_time(d["time"]), game(d["name"]), d["tiers"][0]["numbers"][0]
                    title = w["draw_title"].format(s=self.state(lang, d.get("state", "")), d=dd, t=t, g=g)
                    sibs = "".join(f'<a class="chip"{" aria-current=\"page\"" if x is d else ""} href="{self.url(lang, self.draw_path(x))}">'
                                   f'<b>{esc(short_time(x["time"]))}</b>{esc(game(x["name"]).replace("Dear ", ""))}</a>' for x in draws)
                    body = (f'<div class="intro"><h1 class="sm">{esc(title)}</h1></div>' + self.sheet(lang, d) + self.share(lang, d)
                            + self.checker(lang, s["find_in_result"])
                            + f'<section class="card">{self.tiers(lang, d, ads=True)}</section>' + self.source_buttons(lang, d)
                            + f'<nav class="chips" aria-label="{esc(w["all_of_day"].format(d=dd))}">{sibs}</nav>'
                            + f'<p class="pn"><a class="btn" href="{self.url(lang, self.day_path(date))}">{esc(w["all_of_day"].format(d=dd))}</a></p>'
                            + self.app_promo(lang))
                    self.write(out, lang, self.draw_path(d),
                               self.page(lang, self.draw_path(d), title, w["draw_desc"].format(g=g, d=dd, t=t, n=first), body,
                                         data=[d], crumbs=[(w["home"], "/"), (dd, self.day_path(date)), (t, self.draw_path(d))],
                                         tab="results"),
                               key=f"draw:{d['id']}")
            # archive: every date with its three 1st prizes, so the right day is easy to spot
            self.write(out, lang, "/old-results/", self.page(lang, "/old-results/", w["old_title"], w["old_title"],
                                                             f'<div class="intro"><h1>{esc(w["old_h1"])}</h1></div>{self.archive(lang)}',
                                                             crumbs=[(w["home"], "/"), (w["old_h1"], "/old-results/")], tab="old"),
                       key="old")
        # English-only trust pages (required by ad networks; also what visitors look for)
        w = WEB["en"]
        for path, title, text in (("/about/", w["about"], w["about_body"]),
                                  ("/contact/", w["contact"], w["contact_body"].format(e=CONTACT)),
                                  ("/disclaimer/", w["disclaimer"], self.s["en"]["disclaimer_body"] + " " + self.s["en"]["safety_body"])):
            self.write(out, "en", path, self.page("en", path, f"{title} – LottoDost", text,
                                                  f'<div class="intro"><h1>{esc(title)}</h1></div><section class="card prose"><p>{esc(text)}</p></section>',
                                                  alternates=False), key=f"page:{path}")
        self.write(out, "en", "/404", self.page("en", "/404/", w["notfound"], w["notfound"],
                                               f'<div class="intro"><h1>{esc(w["notfound"])}</h1></div>'
                                               f'<p class="pn"><a class="btn" href="{self.url("en", "/")}">{esc(self.s["en"]["tab_results"])} →</a></p>',
                                               alternates=False))
        self.assets(out)
        return len(self.pages)

    def app_promo(self, lang: str) -> str:
        # Only once the app is really on Google Play: never point people to something they cannot get.
        if not self.play_url:
            return ""
        return f'<p class="app"><a href="{esc(self.play_url)}">{esc(WEB[lang]["app"])}</a></p>'

    def archive(self, lang: str) -> str:
        s, w = self.s[lang], WEB[lang]
        head = "".join(f"<th>{esc(short_time(t))}</th>" for _, t, _ in WEEKLY)
        rows = []
        for x in self.dates:
            y, m, d = x.split("-")
            by_slot = {dr["slot"]: dr for dr in self.days[x] if dr.get("tiers")}
            cells = "".join(f'<td><a href="{self.url(lang, self.draw_path(by_slot[sl]))}">{esc(by_slot[sl]["tiers"][0]["numbers"][0])}</a></td>'
                            if sl in by_slot else "<td>–</td>" for sl, _, _ in WEEKLY)
            rows.append(f'<tr><th scope="row"><a href="{self.url(lang, self.day_path(x))}">{d}.{m}</a>'
                        f'<small>{esc(w["wd"][dt.date(int(y), int(m), int(d)).weekday()])} {y}</small></th>{cells}</tr>')
        return (f'<div class="tbl"><table><thead><tr><th>{esc(w["date"])}</th>{head}</tr></thead>'
                f'<tbody>{"".join(rows)}</tbody></table></div><p class="hint">{esc(s["first_prize"])}</p>')

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


# Same palette as the app (Material 3 from the brand red), light and dark.
CSS = """:root{--bg:#f7f2f0;--fg:#221a19;--mut:#6b5552;--card:#fff;--line:#eaddd9;--soft:#f5ece9;--brand:#b3261e;--red:#b3261e;--gold:#ffe08a;--ongold:#3d2e00;--pink:#ffdad5;--onpink:#410001;--ok:#1b7f3b}
@media (prefers-color-scheme:dark){:root{--bg:#151110;--fg:#efe0dd;--mut:#c2aca8;--card:#201b1a;--line:#3b3230;--soft:#2b2422;--brand:#8c1d18;--red:#ffb4a9;--gold:#5c4300;--ongold:#ffe08a;--pink:#5c1a14;--onpink:#ffdad5;--ok:#7fd18f}}
*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans","Noto Sans Bengali","Noto Sans Devanagari",sans-serif}
.wrap{max-width:42rem;margin:0 auto;padding:0 16px}a{color:var(--red)}h1,h2,h3{margin:0;line-height:1.25}p{margin:0}
.top{background:var(--brand);color:#fff}.bar{display:flex;align-items:center;justify-content:space-between;gap:12px;height:56px}
.brand{color:#fff;text-decoration:none;font-weight:800;font-size:1.25rem;letter-spacing:-.01em}
.langs{display:flex;background:rgba(255,255,255,.16);border-radius:999px;padding:3px}.langs a{color:#fff;text-decoration:none;font-size:.85rem;padding:4px 11px;border-radius:999px}
.langs a[aria-current]{background:#fff;color:#8c1d18;font-weight:700}
.tabs{display:flex}.tabs a{flex:1;text-align:center;color:rgba(255,255,255,.82);text-decoration:none;font-weight:600;font-size:.95rem;padding:10px 4px 9px;border-bottom:3px solid transparent}
.tabs a[aria-current]{color:#fff;border-color:var(--gold)}
.intro{margin:20px 0 4px}h1{font-size:1.45rem;font-weight:800;letter-spacing:-.01em}h1 .d{white-space:nowrap;color:var(--red)}h1.sm{font-size:1.15rem}
.hint{color:var(--mut);font-size:.88rem;margin-top:4px}.eyebrow{color:var(--mut);font-size:.85rem;font-weight:600}
.chips{display:flex;gap:8px;overflow-x:auto;margin:14px -16px 4px;padding:2px 16px 6px;scrollbar-width:none}.chips::-webkit-scrollbar{display:none}
.chip{flex:none;min-width:64px;text-align:center;background:var(--card);border:1px solid var(--line);border-radius:12px;padding:6px 12px;color:var(--fg);text-decoration:none;font-weight:600;font-size:.9rem;line-height:1.25}
.chip b{display:block;font-size:.72rem;color:var(--mut);font-weight:600}.chip[aria-current]{background:var(--gold);border-color:var(--gold);color:var(--ongold)}.chip[aria-current] b{color:var(--ongold)}
.banner{background:var(--pink);color:var(--onpink);border-radius:14px;padding:12px 16px;margin:12px 0;font-weight:600}
.slip{display:grid;grid-template-columns:86px 1fr;background:var(--card);border:1px solid var(--line);border-radius:18px;margin:12px 0;overflow:hidden}
.stub{display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:2px;padding:14px 6px;color:#fff;
background:radial-gradient(circle at 100% 50%,var(--card) 2.5px,transparent 3px) right/8px 11px repeat-y,var(--brand)}
.stub b{font-size:1.05rem;white-space:nowrap}.stub span{font-size:.78rem;opacity:.92}
.sb{padding:14px 16px;min-width:0}.sb h2{font-size:1rem;font-weight:700;text-transform:uppercase;letter-spacing:.01em}.sb h2 a{color:var(--fg);text-decoration:none}
.meta{color:var(--mut);font-size:.85rem;margin:2px 0 10px}.sb>p:not(.meta):not(.eyebrow):not(.first){margin-top:6px}
.ok{color:var(--ok);font-style:normal;font-weight:700;white-space:nowrap}
.first{font-weight:800;font-size:clamp(1.7rem,9vw,2.7rem);line-height:1.1;color:var(--red);word-spacing:.28em;white-space:nowrap;font-variant-numeric:tabular-nums;margin-top:2px}
.all{grid-column:1/-1;border-top:1px dashed var(--line)}
.all>summary,.faq summary{list-style:none;cursor:pointer;display:flex;justify-content:space-between;align-items:center;gap:8px}
.all>summary{padding:12px 16px;color:var(--red);font-weight:700;font-size:.95rem}
.all>summary::-webkit-details-marker,.faq summary::-webkit-details-marker{display:none}
.all>summary::after,.faq summary::after{content:"";width:8px;height:8px;border:solid currentColor;border-width:0 2px 2px 0;transform:rotate(45deg);transition:transform .15s;flex:none;margin-right:4px}
.all[open]>summary::after,.faq details[open] summary::after{transform:rotate(-135deg)}
.all .in{padding:0 16px 14px}.go{font-weight:700;text-decoration:none}
.wait .when{color:var(--mut);font-weight:600}.live{display:inline-block;background:var(--red);color:var(--card);border-radius:6px;padding:0 6px;font-size:.72rem;font-weight:800;margin-right:6px;vertical-align:1px}
.tier{padding:14px 0;border-top:1px solid var(--line)}.tier:first-child{border-top:0}.th{display:flex;justify-content:space-between;align-items:baseline;gap:8px}
.th h3{font-size:1rem}.th b{color:var(--red);white-space:nowrap}
.nums{list-style:none;padding:0;margin:10px 0 0;display:grid;gap:5px;grid-template-columns:repeat(auto-fill,minmax(3.4rem,1fr))}
.nums.n5{grid-template-columns:repeat(auto-fill,minmax(4.1rem,1fr))}.nums.n6{grid-template-columns:repeat(auto-fill,minmax(6.2rem,1fr))}
.nums li{background:var(--soft);border-radius:7px;text-align:center;padding:5px 0;font-weight:600;font-variant-numeric:tabular-nums;letter-spacing:.02em}
.nums li.hit{background:var(--gold);color:var(--ongold);box-shadow:inset 0 0 0 2px #d9a400}
.sheet{background:var(--card);border:1px solid var(--line);border-radius:18px;overflow:hidden;margin:14px 0}.band{background:var(--brand);color:#fff;padding:14px 16px}
.band h2{font-size:1.02rem;text-transform:uppercase;letter-spacing:.01em}.band p{opacity:.9;font-size:.88rem;margin-top:2px}.hero{padding:14px 16px 16px}.hero .meta{margin:4px 0 0}
.card{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:2px 16px;margin:12px 0}.prose{padding:16px}
.share{margin:14px 0}.share .row{margin-top:8px;display:grid;grid-template-columns:repeat(auto-fit,minmax(6.5rem,1fr))}.share a,.share button{display:inline-flex;align-items:center;justify-content:center;min-height:42px;padding:8px 14px;border-radius:999px;border:0;font:inherit;font-weight:700;font-size:.9rem;color:#fff;text-decoration:none;cursor:pointer}
.wa{background:#1b9e4f}.tg{background:#2481cc}.fb{background:#1877f2}.x{background:#14171a}.share .cp,.share .more{background:var(--card);color:var(--fg);box-shadow:inset 0 0 0 1px var(--line)}
.row{display:flex;gap:8px;flex-wrap:wrap}
.check{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:16px;margin:16px 0}.check h2{font-size:1.1rem}
.check .row{margin-top:12px}.check input{flex:1 1 11rem;min-width:0;font-family:inherit;font-weight:700;font-size:1.25rem;line-height:1.2;letter-spacing:.04em;padding:12px 14px;border:1.5px solid var(--line);border-radius:12px;background:var(--bg);color:var(--fg);text-transform:uppercase;font-variant-numeric:tabular-nums}
.check input:focus{outline:none;border-color:var(--red)}
.check button{font:inherit;font-weight:700;padding:12px 26px;border:0;border-radius:999px;background:var(--brand);color:#fff;cursor:pointer}
.res{margin-top:12px;border:1px solid var(--line);border-radius:14px;overflow:hidden}.res .rh{background:var(--ok);color:#fff;padding:10px 14px;font-weight:700}
@media (prefers-color-scheme:dark){.res .rh{color:#0b2912}}
.res .rb{padding:12px 14px}.res .rb strong{display:block;font-size:1.3rem}.res.no .rh{background:var(--soft);color:var(--fg)}
.btns{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.btn{display:inline-flex;align-items:center;min-height:42px;padding:8px 16px;border:1px solid var(--line);border-radius:999px;background:var(--card);color:var(--fg);text-decoration:none;font-weight:600;font-size:.92rem}
.pn{display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;margin:14px 0}
.app{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px 16px;margin:12px 0;font-weight:600}
.faq{margin:24px 0 8px}.faq h2{font-size:1.1rem;margin-bottom:8px}.faq details{background:var(--card);border:1px solid var(--line);border-radius:14px;margin:8px 0}
.faq summary{padding:12px 16px;font-weight:600}.faq details p{padding:0 16px 12px}
.tbl{overflow-x:auto;background:var(--card);border:1px solid var(--line);border-radius:18px;margin:14px 0}table{width:100%;border-collapse:collapse;font-size:.92rem}
th,td{padding:10px 6px;border-top:1px solid var(--line);text-align:left;white-space:nowrap}th:first-child{padding-left:14px}td:last-child{padding-right:12px}thead th{border-top:0;background:var(--soft);color:var(--mut);font-size:.8rem}
tbody th a{color:var(--fg);font-weight:700;text-decoration:none}tbody th small{display:block;color:var(--mut);font-weight:400}
td a{color:var(--red);font-weight:700;text-decoration:none;font-variant-numeric:tabular-nums;font-size:.86rem}tbody th small{font-size:.75rem}
.foot{color:var(--mut);font-size:.8rem;padding:28px 0 36px;margin-top:24px;border-top:1px solid var(--line)}.foot p{margin:0 0 8px}
.foot .links{display:flex;flex-wrap:wrap;gap:4px 16px;font-size:.88rem}.ad{margin:16px 0;min-height:100px}
@media (min-width:720px){.intro{margin-top:28px}h1{font-size:1.7rem}.slip{grid-template-columns:104px 1fr}}
@media (max-width:380px){th,td{padding-left:4px;padding-right:4px}td a{font-size:.78rem}.tbl{margin-inline:-8px}}
@media (prefers-reduced-motion:reduce){*{transition:none!important}}
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
function hm(s){var p=s.split(":"),h=+p[0];return(h%12||12)+(p[1]!=="00"?":"+p[1]:"")+(h>=12?" PM":" AM")}
function box(head,cls){var b=el("div","res"+(cls?" "+cls:""));b.appendChild(el("div","rh",head));var c=el("div","rb");b.appendChild(c);return[b,c]}
var f=document.getElementById("checkForm");
if(f)f.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("checkRes");out.textContent="";
var v=document.getElementById("ticket").value;if(!v.trim())return;var k=parse(v);if(!k){var e=box(t("ticket_invalid"),"no");out.appendChild(e[0]);return}
var cs=D.map(function(d){return check(k,d)}).filter(Boolean),won=cs.filter(function(c){return c.w.length});
won.forEach(function(c){var x=c.w[0],e=box(x.nf?t("maybe_first"):t("you_won"));e[1].appendChild(el("strong",null,tier(x.T.rank)+" · "+rup(x.T.prize)));
e[1].appendChild(el("div","hint",c.d.name.replace(/ WEEKLY LOTTERY$/,"")+" · "+c.d.date.split("-").reverse().join(".")+" "+hm(c.d.time)));
if(x.nf)e[1].appendChild(el("div","hint",t("need_full_number")));out.appendChild(e[0])});
if(!won.length){var e=box(t("no_win_title",show(k)),"no");e[1].appendChild(el("div","hint",t(cs.length===1&&S.no_win_body_one?"no_win_body_one":"no_win_body",cs.length)));out.appendChild(e[0])}
var dg=k.dg;document.querySelectorAll(".nums li").forEach(function(s){var x=s.textContent,hit=dg.length>=x.length&&dg.slice(-x.length)===x;
s.classList.toggle("hit",hit);if(hit){var dt=s.closest("details");if(dt)dt.open=true}});
out.scrollIntoView({block:"nearest",behavior:"smooth"})});
// Share: copy the link, or the phone's own share sheet where the browser has one.
document.querySelectorAll(".share .cp").forEach(function(b){b.addEventListener("click",function(){var u=b.dataset.url,l=b.textContent;
function done(){b.textContent=t("copied");setTimeout(function(){b.textContent=l},1800)}
if(navigator.clipboard)navigator.clipboard.writeText(u).then(done,function(){prompt("",u)});else prompt("",u)})});
if(navigator.share)document.querySelectorAll(".share .more").forEach(function(b){b.hidden=false;b.addEventListener("click",function(){
navigator.share({text:b.dataset.text}).catch(function(){})})});
// Draws still to come: countdown, then LIVE, then the "taking longer" note, as in the app.
var W=document.querySelectorAll(".when[data-at]");function tick(){var now=Date.now();W.forEach(function(e){var at=Date.parse(e.dataset.at),m=Math.floor((at-now)/60000);
if(m>=0){e.textContent=t("draw_in",Math.floor(m/60),m%60)}else if(m>-15){e.textContent="";e.appendChild(el("span","live",t("live")));e.appendChild(document.createTextNode(t("result_coming")))}
else e.textContent=t("result_delayed")})}if(W.length){tick();setInterval(tick,30000)}
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
    ap.add_argument("--link-prefix", default=None,
                    help='path the copy is served under, if not base-url\'s ("" for a mirror at a host root)')
    a = ap.parse_args(argv)
    n = Site(a.data, a.base_url, link_prefix=a.link_prefix).build(a.out)
    print(f"site: {n} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
