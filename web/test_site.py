"""Builds the site from the app's test feed (real 18-10-2026 results) and checks what search engines see."""
import datetime as dt
import json
import pathlib
import re

import sitegen  # web/sitegen.py

FEED = pathlib.Path(__file__).resolve().parent.parent / "android/app/src/test/resources/feed_latest.json"


def build(tmp, monkeypatch, ads="", base="https://example.in", link_prefix=None):
    monkeypatch.setenv("ADSENSE_CLIENT", ads)
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
    assert "<title>Nagaland State Lottery Result 18.10.2026 1 PM – Dear Wish Sunday</title>" in page
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
    out = build(tmp_path, monkeypatch, ads="ca-pub-1234567890")
    page = (out / "result/18-10-2026/index.html").read_text()
    assert 0 < page.count('class="adsbygoogle"') <= 3
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
            target = out / link.lstrip("/")
            assert (target / "index.html").exists() or target.is_file(), f"{page.relative_to(out)} -> {link}"
