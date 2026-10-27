"""Builds the site from the app's test feed (real 18-10-2026 results) and checks what search engines see."""
import datetime as dt
import json
import pathlib
import re

import sitegen  # web/sitegen.py

FEED = pathlib.Path(__file__).resolve().parent.parent / "android/app/src/test/resources/feed_latest.json"


def build(tmp, monkeypatch, ads="", base="https://example.in", link_prefix=None, slot="", og=False):
    monkeypatch.setenv("SITE_OG", "1" if og else "0")  # share images are slow to draw; one test covers them
    monkeypatch.setenv("ADSENSE_CLIENT", ads)
    monkeypatch.setenv("ADSENSE_SLOT", slot)
    data = tmp / "data"
    (data / "v2/days").mkdir(parents=True)
    feed = json.loads(FEED.read_text())
    for date in sorted({d["date"] for d in feed["draws"]}):
        (data / f"v2/days/{date}.json").write_text(json.dumps({"schema": 2, "date": date,
                                                                "draws": [d for d in feed["draws"] if d["date"] == date]}))
    (data / "v2/index.json").write_text(json.dumps({"schema": 2, "dates": [], "noDraw": [], "upcoming": []}))
    now = dt.datetime(2026, 10, 18, 21, 0, tzinfo=sitegen.IST)
    out = tmp / "out"
    s = sitegen.Site(data, base, now=now, link_prefix=link_prefix)
    s.build(out)
    return out


def test_draw_page_is_readable_without_javascript(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    page = (out / "result/18-10-2026/1pm/index.html").read_text()
    assert "<title>Lottery Sambad Morning 1 PM Result 18.10.2026 – Dear Wish Sunday</title>" in page
    assert "74D 70698" in page and "15172" in page          # 1st prize and a 2nd-prize number in the HTML itself
    assert '<link rel="canonical" href="https://example.in/result/18-10-2026/1pm/">' in page
    assert 'hreflang="bn-IN" href="https://example.in/bn/result/18-10-2026/1pm/"' in page   # for readers in India
    assert '<meta property="og:locale" content="en_IN">' in page
    assert "adsbygoogle" not in page                        # no ad code until an AdSense id is configured


def test_languages_and_home(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    bn = (out / "bn/index.html").read_text()
    assert '<html lang="bn-IN">' in bn and "নাগাল্যান্ড" in bn and "37K 90889" in bn
    hi = (out / "hi/result/18-10-2026/index.html").read_text()
    assert "लॉटरी संबाद रिज़ल्ट 18.10.2026 (18/10/26)" in hi   # dates as people type them too


def test_ticket_checker_comes_before_the_results(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    for f in ("index.html", "result/18-10-2026/index.html", "morning-1pm/index.html", "kerala/index.html"):
        page = (out / f).read_text()
        assert 0 < page.index('id="check"') < page.index('class="slip'), f
    draw = (out / "result/18-10-2026/1pm/index.html").read_text()
    assert draw.index('class="sheet"') < draw.index('id="check"') < draw.index('class="share"') < draw.index('class="card"')
    assert "t.me/" not in (out / "index.html").read_text()   # no Telegram channel link (sharing a result stays)


def test_home_names_the_site_for_google(tmp_path, monkeypatch):
    home = (build(tmp_path, monkeypatch) / "index.html").read_text()
    site = next(json.loads(x) for x in re.findall(r'<script type="application/ld\+json">(.*?)</script>', home)
                if '"WebSite"' in x)
    assert site["name"] == "LottoDost" and site["url"] == "https://example.in/" and site["inLanguage"] == "en-IN"


def test_yesterday_page_has_a_fixed_address(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    page = (out / "yesterday/index.html").read_text()
    assert "<title>Lottery Sambad Yesterday Result 17.10.2026 – Dear 1 PM, 6 PM, 8 PM and Kerala</title>" in page
    assert 'class="slip' in page and 'id="check"' in page and "/result/17-10-2026/" in page
    assert 'href="/yesterday/"' in (out / "index.html").read_text()        # linked from the home page
    assert "https://example.in/hi/yesterday/" in (out / "sitemap.xml").read_text()


def test_site_icon_for_search_results(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch, link_prefix="/india")
    home = (out / "index.html").read_text()
    assert '<link rel="icon" href="/india/favicon.ico" sizes="48x48">' in home and 'href="/india/apple-touch-icon.png"' in home
    for f in ("favicon.ico", "favicon.svg", "apple-touch-icon.png", "icon-192.png", "icon-512.png"):
        assert (out / f).stat().st_size > 0, f
    assert '"logo": "https://example.in/icon-512.png"' in home
    assert 'href="/india/manifest.webmanifest"' in home and 'content="max-image-preview:large"' in home
    m = json.loads((out / "manifest.webmanifest").read_text())   # "Add to Home screen" in Chrome / Samsung Internet
    assert m["start_url"] == "./" and {i["sizes"] for i in m["icons"]} == {"192x192", "512x512"}


def test_sitemap_lists_every_page_once(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    locs = re.findall(r"<loc>([^<]+)</loc>", (out / "sitemap.xml").read_text())
    assert len(locs) == len(set(locs))
    assert "https://example.in/bn/result/18-10-2026/8pm/" in locs
    assert 'hreflang="hi-IN" href="https://example.in/hi/"' in (out / "sitemap.xml").read_text()
    assert (out / "robots.txt").read_text().endswith("Sitemap: https://example.in/sitemap.xml\n")
    assert not (out / "ads.txt").exists()


def test_ads_when_configured(tmp_path, monkeypatch):
    auto = (build(tmp_path / "auto", monkeypatch, ads="ca-pub-1234567890") / "result/18-10-2026/index.html").read_text()
    assert "adsbygoogle.js?client=ca-pub-1234567890" in auto and 'class="adsbygoogle"' not in auto  # Auto ads only
    out = build(tmp_path, monkeypatch, ads="ca-pub-1234567890", slot="1234567")
    page = (out / "result/18-10-2026/index.html").read_text()
    assert 0 < page.count('class="adsbygoogle"') <= 3 and 'data-ad-slot="1234567"' in page
    assert (out / "ads.txt").read_text() == "google.com, pub-1234567890, DIRECT, f08c47fec0942fa0\n"


def test_html_tags_are_balanced(tmp_path, monkeypatch):
    from html.parser import HTMLParser
    out = build(tmp_path, monkeypatch)
    void = {"meta", "link", "input", "br", "img"}

    class P(HTMLParser):
        def __init__(self):
            super().__init__(); self.stack = []
        def handle_starttag(self, tag, a):
            if tag not in void: self.stack.append(tag)
        def handle_endtag(self, tag):
            assert self.stack and self.stack[-1] == tag, (tag, self.stack[-5:])
            self.stack.pop()

    for f in out.rglob("*.html"):
        p = P(); p.feed(f.read_text()); assert p.stack == [], f


def test_search_console_verification_tag(tmp_path, monkeypatch):
    monkeypatch.setenv("GOOGLE_SITE_VERIFICATION", "abc123")
    out = build(tmp_path, monkeypatch)
    assert '<meta name="google-site-verification" content="abc123">' in (out / "index.html").read_text()
    assert "msvalidate.01" not in (out / "index.html").read_text()


def test_verification_accepts_the_whole_meta_tag():
    tag = '<meta name="google-site-verification" content="kaGs6GCkJCjF95K6r6FLMmRcG76VAjaoHo8bTWcLL9Q" />'
    assert sitegen.verification(tag) == "kaGs6GCkJCjF95K6r6FLMmRcG76VAjaoHo8bTWcLL9Q"
    assert sitegen.verification(" abc123 ") == "abc123"


def test_beacon_accepts_the_whole_snippet():
    snippet = ("<!-- Cloudflare Web Analytics --><script defer src='https://static.cloudflareinsights.com/beacon.min.js' "
               "data-cf-beacon='{\"token\": \"0123abcd\"}'></script><!-- End Cloudflare Web Analytics -->")
    assert sitegen.beacon_token(snippet) == "0123abcd"
    assert sitegen.beacon_token("0123abcd") == "0123abcd"


def test_mirror_at_host_root_links_without_the_project_path(tmp_path, monkeypatch):
    """GitHub serves the site under /india/, Cloudflare at the root: links must follow the copy, canonicals must not."""
    gh = (build(tmp_path / "gh", monkeypatch, base="https://example.github.io/india") / "bn/index.html").read_text()
    assert 'href="/india/bn/result/17-10-2026/"' in gh and 'src="/india/assets/site.js"' in gh
    cf = (build(tmp_path / "cf", monkeypatch, base="https://example.github.io/india", link_prefix="") / "bn/index.html").read_text()
    assert "/india/" not in cf.replace("https://example.github.io/india/", "")
    assert 'href="/bn/result/17-10-2026/"' in cf and 'src="/assets/site.js"' in cf and 'href="/privacy.html"' in cf
    assert '<link rel="canonical" href="https://example.github.io/india/bn/">' in cf


def test_every_internal_link_resolves(tmp_path, monkeypatch):
    """A PDF-only draw (no numbers) has no page of its own, so nothing may link to one."""
    data = tmp_path / "pdfonly"
    out = build(data, monkeypatch, base="https://example.github.io/india", link_prefix="")
    feed = json.loads(FEED.read_text())
    assert any(not d.get("tiers") for d in feed["draws"])  # the fixture really has a PDF-only draw
    for page in out.rglob("*.html"):
        for link in set(re.findall(r'(?:href|src)="(/[^"]*)"', page.read_text())) - {"/privacy.html"}:  # copied by the workflow
            target = out / link.split("#")[0].lstrip("/")
            assert (target / "index.html").exists() or target.is_file(), f"{page.relative_to(out)} -> {link}"


def test_share_image_and_freshness(tmp_path, monkeypatch):
    pytest = __import__("pytest")
    pytest.importorskip("PIL")
    out = build(tmp_path, monkeypatch, og=True)
    page = (out / "result/18-10-2026/1pm/index.html").read_text()
    assert '<meta property="og:image" content="https://example.in/og/2026-10-18_NL1PM.png">' in page
    png = out / "og/2026-10-18_NL1PM.png"
    assert png.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n" and png.stat().st_size < 40_000
    home = (out / "index.html").read_text()
    assert '"dateModified": "2026-10-18T21:00+05:30"' in home
    shared = re.search(r'<meta property="og:image" content="https://example.in/(og/2026-10-18_\w+\.png)">', home)
    assert shared and (out / shared[1]).exists()             # WhatsApp/Discover preview of the most shared page
    assert shared[1] != "og/2026-10-18_NL1PM.png"            # the newest result of the day, not the first
    assert 'href="/icon-192.png" type="image/png" sizes="192x192"' in home


def test_number_history_and_date_search_are_on_the_page(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    assert 'id="histForm"' in (out / "index.html").read_text()
    old = (out / "old-results/index.html").read_text()
    assert 'id="dateForm"' in old and 'min="2026-10-12" max="2026-10-18"' in old


def test_kerala_hub_and_draw_page(tmp_path, monkeypatch):
    kl = {"id": "2026-10-17_KL", "slot": "KL", "lottery": "kerala", "date": "2026-10-17", "time": "15:00", "state": "Kerala",
          "name": "KARUNYA KR-772", "drawNo": 772, "format": "B6", "price": 0, "special": False, "verified": False, "status": "OK",
          "tiers": [{"rank": "1", "prize": 10000000, "match": "EXACT", "numbers": ["KG 709965"]},
                    {"rank": "CONS", "prize": 5000, "match": "EXACT", "numbers": ["KA 709965"]},
                    {"rank": "9", "prize": 100, "match": "LAST4", "numbers": ["9865"], "confirmed": False}],
          "sources": [{"kind": "copy", "url": "https://g.test/a.pdf"}, {"kind": "officialSite", "url": "https://statelottery.kerala.gov.in/"}]}
    orig = sitegen.Site.__init__

    def init(self, *a, **k):
        orig(self, *a, **k)
        self.days.setdefault("2026-10-17", []).append(kl)
        self.dates = sorted(self.days, reverse=True)
    monkeypatch.setattr(sitegen.Site, "__init__", init)
    out = build(tmp_path, monkeypatch)
    hub = (out / "kerala/index.html").read_text()
    assert "<title>Kerala Lottery Result Today 17.10.2026 – Karunya KR-772 3 PM, Result Chart</title>" in hub
    assert "KG 709965" in hub and 'placeholder="MM 289707"' in hub
    page = (out / "result/17-10-2026/kerala/index.html").read_text()
    assert "<title>Kerala Lottery Result 17.10.2026 – Karunya KR-772</title>" in page
    assert "Not yet confirmed by two sources" in page and "statelottery.kerala.gov.in" in page
    assert "https://example.in/kerala/" in (out / "sitemap.xml").read_text()
    day = (out / "result/17-10-2026/index.html").read_text()
    assert day.index("<b>1 PM</b>") < day.index("<b>3 PM</b>") < day.index("<b>6 PM</b>")   # in draw-time order


def test_draw_time_pages_rank_before_the_result_exists(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    night = (out / "night-8pm/index.html").read_text()   # 18-10 at 9 PM: the 8 PM result is in
    assert "<title>Lottery Sambad Night Result Today 18.10.2026 – Dear 8 PM</title>" in night
    assert '<link rel="canonical" href="https://example.in/night-8pm/">' in night
    assert "data-wait" not in night and night.count("<tr>") >= 2   # the result and its last days
    bn = (out / "bn/morning-1pm/index.html").read_text()
    assert "লটারি সংবাদ আজকের মর্নিং রেজাল্ট 18.10.2026" in bn
    home = (out / "index.html").read_text()
    for p in ("/morning-1pm/", "/day-6pm/", "/night-8pm/", "/kerala/"):
        assert f'href="{p}"' in home
    draw = (out / "result/18-10-2026/8pm/index.html").read_text()
    assert 'href="/night-8pm/"' in draw
    sitemap = (out / "sitemap.xml").read_text()
    assert "https://example.in/day-6pm/" in sitemap and "https://example.in/hi/day-6pm/" in sitemap
    assert "<url><loc>https://example.in/result/17-10-2026/</loc><lastmod>2026-10-17</lastmod>" in sitemap
    assert "<url><loc>https://example.in/night-8pm/</loc><lastmod>2026-10-18T21:00+05:30</lastmod>" in sitemap


def test_draw_time_page_waits_for_todays_result(tmp_path, monkeypatch):
    monkeypatch.setenv("SITE_OG", "0")
    data = tmp_path / "data"
    (data / "v2/days").mkdir(parents=True)
    feed = json.loads(FEED.read_text())
    (data / "v2/days/2026-10-18.json").write_text(json.dumps({"schema": 2, "date": "2026-10-18",
                                                              "draws": [d for d in feed["draws"] if d["date"] == "2026-10-18"]}))
    now = dt.datetime(2026, 10, 19, 12, 50, tzinfo=sitegen.IST)   # before the 1 PM draw of the next day
    sitegen.Site(data, "https://example.in", now=now).build(tmp_path / "out")
    page = (tmp_path / "out/morning-1pm/index.html").read_text()
    assert 'data-wait="2026-10-19_NL1PM"' in page
    assert "Lottery Sambad Morning Result Today 19.10.2026" in page
    assert 'data-at="2026-10-19T13:00:00+05:30"' in page    # the countdown
    assert "74D 70698" in page                              # and yesterday's result below it


def test_month_archive_and_date_search_reach_every_date(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    month = (out / "old-results/2026-10/index.html").read_text()
    assert "<title>Lottery Sambad Old Result October 2026 – Dear Lottery 1 PM, 6 PM, 8 PM</title>" in month
    assert 'href="/result/17-10-2026/"' in month and "74D 70698" in month
    assert (out / "bn/old-results/2026-10/index.html").exists()
    old = (out / "old-results/index.html").read_text()
    assert 'href="/old-results/2026-10/"' in old
    assert re.search(r'data-days="[0-9 ]*20261017[0-9 ]*"', old) and 'data-base="/result/"' in old
    assert "https://example.in/old-results/2026-10/" in (out / "sitemap.xml").read_text()


def ref_tax(p):
    """Income-tax Act 2025 s.194/s.393(3) and Finance Bill 2026 (new regime), prize as the only income."""
    tds = round(p * 0.3) if p > 10000 else 0
    t = p * 0.3
    for lim, r, rp in ((20_000_000, 0.25, 0.15), (10_000_000, 0.15, 0.10), (5_000_000, 0.10, 0.0)):
        if p > lim:
            t = min(t * (1 + r), lim * 0.3 * (1 + rp) + (p - lim))   # marginal relief
            break
    total = round(t * 1.04)
    return {"tds": tds, "paid": p - tds, "more": total - tds, "total": total, "keep": p - total}


def test_guides_are_built_with_sources_and_the_calculator_matches_the_law(tmp_path, monkeypatch):
    import shutil
    import subprocess
    out = build(tmp_path, monkeypatch)
    for lang in ("", "bn/", "hi/"):
        for p in ("lottery-tax", "claim-prize", "lottery-scams"):
            page = (out / f"{lang}{p}/index.html").read_text()
            assert '<ul class="src"><li><a rel="noopener" href="https://' in page
    tax = (out / "lottery-tax/index.html").read_text()
    assert "<title>Lottery Tax in India 2026 – Tax on Lottery Prize Calculator</title>" in tax and 'id="taxForm"' in tax
    assert 'href="/claim-prize/"' in (out / "index.html").read_text()
    assert 'href="/hi/lottery-tax/"' in (out / "hi/result/18-10-2026/1pm/index.html").read_text()
    assert ref_tax(10_000_000) == {"tds": 3_000_000, "paid": 7_000_000, "more": 432_000, "total": 3_432_000, "keep": 6_568_000}
    node = shutil.which("node")
    if not node:
        return
    amounts = [5000, 10000, 10001, 500000, 5_000_000, 5_000_001, 5_200_000, 10_000_000, 10_100_000, 20_000_000, 20_500_000, 120_000_000]
    js = ("var window={},navigator={},stub={textContent:'[]',dataset:{},addEventListener:function(){}};"
          "var document={getElementById:function(){return stub},querySelector:function(){return null},"
          "querySelectorAll:function(){return[]},body:{dataset:{}}};"
          + (out / "assets/site.js").read_text()
          + f";console.log(JSON.stringify({amounts}.map(function(p){{return window.lotteryTax(p)}})))")
    got = json.loads(subprocess.run([node, "-e", js], capture_output=True, text=True, check=True).stdout)
    assert got == [ref_tax(p) for p in amounts]


def test_github_copy_redirects_every_page_to_the_real_site(tmp_path, monkeypatch):
    import redirect_stubs  # web/redirect_stubs.py
    out = build(tmp_path, monkeypatch, base="https://lottodost.com", link_prefix="/india")
    (out / "privacy.html").write_text("<html>policy</html>")
    (out / "v2").mkdir()
    (out / "v2/today.json").write_text("{}")
    pages = sorted(out.rglob("*.html"))
    assert redirect_stubs.stub_tree(out, "https://lottodost.com/", "/india") == len(pages)
    home = (out / "index.html").read_text()
    assert '<meta http-equiv="refresh" content="0; url=https://lottodost.com/">' in home
    assert '<link rel="canonical" href="https://lottodost.com/">' in home
    draw = (out / "hi/result/18-10-2026/1pm/index.html").read_text()
    assert 'url=https://lottodost.com/hi/result/18-10-2026/1pm/"' in draw and "Lottery Sambad" not in draw
    assert 'url=https://lottodost.com/privacy.html"' in (out / "privacy.html").read_text()
    nf = (out / "404.html").read_text()
    assert 'content="noindex"' in nf and 'p.indexOf("/india/")===0' in nf and "http-equiv" not in nf
    assert (out / "v2/today.json").read_text() == "{}"          # the app's JSON mirror is untouched
    # The Cloudflare copy starts from this tree: whatever the site build does not rewrite is removed, never served.
    (out / "result/18-10-2026/1pm/index.html").write_text("<html>real page</html>")
    assert redirect_stubs.purge(out) == len(pages) - 1
    assert sorted(out.rglob("*.html")) == [out / "result/18-10-2026/1pm/index.html"]


def test_share_kit_has_ready_posts_and_stays_out_of_search(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    kit = (out / "share/index.html").read_text()
    assert '<meta name="robots" content="noindex,nofollow">' in kit
    assert "https://example.in/share/" not in (out / "sitemap.xml").read_text()
    assert "🏆 1st Prize ₹1 Crore: 74D 70698" in kit                     # the 18-10-2026 1 PM result
    assert "https://example.in/result/18-10-2026/1pm/" in kit and "https://wa.me/?text=" in kit
    first = kit.index("<section>")
    assert "1 PM" not in kit[first:kit.index("</section>")]                # newest draw of the day first


MH_DRAW = {"id": "2026-10-18_MH_GAJLAXMI-RAVI", "slot": "MH", "lottery": "maharashtra", "date": "2026-10-18", "time": "16:45",
           "state": "Maharashtra", "name": "Maharashtra Gajlaxmi Ravi", "kind": "weekly", "nameLocal": "महाराष्ट्र गजलक्ष्मी रवि साप्ताहिक सोडत",
           "format": "MH4", "ocr": True, "status": "OK", "drawNo": 43,
           "tiers": [{"rank": "1", "prize": 10000, "match": "EXACT", "numbers": ["GL-00 2142"]},
                     {"rank": "2", "prize": 5000, "match": "LAST4", "numbers": ["2273"]},
                     {"rank": "4", "prize": 1000, "match": "LAST4", "numbers": ["2013", "4179"]}],
           "sources": [{"kind": "officialPdf", "url": "https://lottery.maharashtra.gov.in/UploadFiles/x.pdf"},
                       {"kind": "officialSite", "url": "https://lottery.maharashtra.gov.in/mhlotteryresults.aspx"}]}


def test_maharashtra_has_its_own_section_and_pages(tmp_path, monkeypatch):
    import shutil
    import subprocess
    data = tmp_path / "data" / "v2/mh/days"
    data.mkdir(parents=True)
    pending = dict(MH_DRAW, id="2026-10-18_MH_PADMINI", name="Maharashtra Padmini", status="PDF", time="", tiers=[], drawNo=None)
    (data / "2026-10-18.json").write_text(json.dumps({"schema": 2, "date": "2026-10-18", "draws": [MH_DRAW, pending]}))
    out = build(tmp_path, monkeypatch)
    home = (out / "index.html").read_text()
    assert '<h2 class="sub">Other state lotteries</h2>' in home and 'href="/maharashtra/"' in home
    assert "MH_GAJLAXMI" not in home and "Gajlaxmi" not in home   # Sambad's day cards stay Sambad's
    hub = (out / "maharashtra/index.html").read_text()
    assert "<title>Maharashtra Lottery Result Today 18.10.2026 – Gajlaxmi Ravi Weekly and All Draws</title>" in hub
    assert "GL-00 2142" in hub and "Padmini" in hub and "https://lottery.maharashtra.gov.in/UploadFiles/x.pdf" in hub
    page = (out / "result/18-10-2026/maharashtra-gajlaxmi-ravi/index.html").read_text()
    assert "<title>Maharashtra Gajlaxmi Ravi Weekly Lottery Result 18.10.2026 (Draw 43)</title>" in page
    assert 'lang="mr">महाराष्ट्र गजलक्ष्मी रवि साप्ताहिक सोडत' in page
    assert not (out / "result/18-10-2026/maharashtra-padmini").exists()   # no numbers, no page
    assert (out / "hi/maharashtra/index.html").exists() and (out / "bn/maharashtra/index.html").exists()
    sm = (out / "sitemap.xml").read_text()
    assert "https://example.in/maharashtra/" in sm and "https://example.in/result/18-10-2026/maharashtra-gajlaxmi-ravi/" in sm
    node = shutil.which("node")
    if not node:
        return
    # The page's own checker: series + number for the 1st prize, any series for the others.
    js = (out / "assets/site.js").read_text()
    m = re.search(r'<script type="application/json" id="draws">(.*?)</script>', page, re.S)
    harness = ("var res=[];var els={};function E(id){return els[id]||(els[id]={value:'',textContent:'',appendChild:function(c){res.push(c)},"
               "addEventListener:function(ev,f){this.f=f},scrollIntoView:function(){},dataset:{}})}"
               "var document={getElementById:function(id){if(id==='draws')return{textContent:" + json.dumps(m[1]) + "};"
               "if(id==='i18n')return{textContent:'{}'};return E(id)},querySelector:function(){return null},"
               "querySelectorAll:function(){return[]},createElement:function(t){return{appendChild:function(c){(this.k=this.k||[]).push(c)},"
               "classList:{toggle:function(){}},set textContent(v){this.t=v},get textContent(){return this.t||''}}},body:{dataset:{}}};"
               "var window={},navigator={},location={hash:''};")
    out_js = []
    for ticket in ("GL-00 2142", "GL-05 2142", "gl00 2273", "2142", "9999"):
        run = (harness + js + f";E('ticket').value={json.dumps(ticket)};E('checkForm').f({{preventDefault:function(){{}}}});"
               "console.log(JSON.stringify(res.map(function(b){return b.className+':'+b.k[0].t})))")
        out_js.append(json.loads(subprocess.run([node, "-e", run], capture_output=True, text=True, check=True).stdout))
    assert out_js[0][0].startswith("res:") and "no" not in out_js[0][0].split(":")[0]
    assert out_js[1][0].startswith("res no:")             # 1st prize is for series GL-00 only
    assert not out_js[2][0].startswith("res no:")         # 2nd prize: any series
    assert not out_js[3][0].startswith("res no:")         # 4 digits alone: "same digits as a top prize"
    assert out_js[4][0].startswith("res no:")


def test_maharashtra_without_numbers_promises_only_the_official_sheets(tmp_path, monkeypatch):
    data = tmp_path / "data" / "v2/mh/days"
    data.mkdir(parents=True)
    pdf = dict(MH_DRAW, status="PDF", time="", tiers=[], drawNo=None)
    (data / "2026-10-18.json").write_text(json.dumps({"schema": 2, "date": "2026-10-18", "draws": [pdf]}))
    out = build(tmp_path, monkeypatch)
    hub = (out / "maharashtra/index.html").read_text()
    assert "<title>Maharashtra Lottery Result Today 18.10.2026 – Gajlaxmi Ravi Weekly, Official Result Sheets</title>" in hub
    assert 'id="checkForm"' not in hub and "Full prize list" not in hub and "read by computer" not in hub
    assert "https://lottery.maharashtra.gov.in/UploadFiles/x.pdf" in hub
    assert "https://example.in/maharashtra/" in (out / "sitemap.xml").read_text()


def test_kerala_in_malayalam(tmp_path, monkeypatch):
    kl = {"id": "2026-10-18_KL", "slot": "KL", "lottery": "kerala", "date": "2026-10-18", "time": "15:00", "state": "Kerala",
          "name": "SAMRUDHI SM-77", "drawNo": 77, "format": "B6", "price": 0, "special": False, "status": "OK",
          "tiers": [{"rank": "1", "prize": 10000000, "match": "EXACT", "numbers": ["MM 289707"]},
                    {"rank": "9", "prize": 100, "match": "LAST4", "numbers": ["9865"], "confirmed": False}],
          "sources": [{"kind": "officialSite", "url": "https://statelottery.kerala.gov.in/"}]}
    orig = sitegen.Site.__init__

    def init(self, *a, **k):
        orig(self, *a, **k)
        self.days.setdefault("2026-10-18", []).append(kl)
        self.dates = sorted(self.days, reverse=True)
    monkeypatch.setattr(sitegen.Site, "__init__", init)
    out = build(tmp_path, monkeypatch)
    page = (out / "ml/kerala/index.html").read_text()
    assert '<html lang="ml-IN">' in page and '<meta property="og:locale" content="ml_IN">' in page
    assert re.search(r"<title>കേരള ലോട്ടറി ഫലം ഇന്ന് \d\d\.\d\d\.2026 – .+ \| Kerala Lottery Result</title>", page)
    assert '<link rel="canonical" href="https://example.in/ml/kerala/">' in page
    assert 'hreflang="en-IN" href="https://example.in/kerala/"' in page and 'hreflang="ml-IN" href="https://example.in/ml/kerala/"' in page
    assert "ഒന്നാം സമ്മാനം" in page and "MM 289707" in page and "സമൃദ്ധി SM-77" in page
    assert "ഒമ്പതാം സമ്മാനം" in page and "ഈ നമ്പറുകൾ ഇനിയും സ്ഥിരീകരിച്ചിട്ടില്ല." in page
    hub = (out / "kerala/index.html").read_text()
    assert 'hreflang="ml-IN" href="https://example.in/ml/kerala/"' in hub and 'href="/ml/kerala/"' in hub
    sm = (out / "sitemap.xml").read_text()
    assert "<loc>https://example.in/ml/kerala/</loc>" in sm and "/bn/ml/" not in sm
