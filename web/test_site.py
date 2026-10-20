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
    assert 'hreflang="bn" href="https://example.in/bn/result/18-10-2026/1pm/"' in page
    assert "adsbygoogle" not in page                        # no ad code until an AdSense id is configured


def test_languages_and_home(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    bn = (out / "bn/index.html").read_text()
    assert '<html lang="bn">' in bn and "নাগাল্যান্ড" in bn and "37K 90889" in bn
    hi = (out / "hi/result/18-10-2026/index.html").read_text()
    assert "डियर लॉटरी रिज़ल्ट 18.10.2026" in hi


def test_sitemap_lists_every_page_once(tmp_path, monkeypatch):
    out = build(tmp_path, monkeypatch)
    locs = re.findall(r"<loc>([^<]+)</loc>", (out / "sitemap.xml").read_text())
    assert len(locs) == len(set(locs))
    assert "https://example.in/bn/result/18-10-2026/8pm/" in locs
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
    assert "<title>Kerala Lottery Result Today 17.10.2026 – Karunya KR-772 3 PM</title>" in hub
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
    assert "লটারি সংবাদ মর্নিং রেজাল্ট আজ 18.10.2026" in bn
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
