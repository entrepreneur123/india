import datetime as dt
import json
import os
import re

from sarkari import eligibility, sitegen

HERE = os.path.dirname(__file__)
FAMILIES = json.load(open(os.path.join(HERE, "..", "exam_families.json"), encoding="utf-8"))["families"]
RULES = eligibility.load_all()
TODAY = dt.date(2026, 10, 20)
ITEMS = [
    {"id": "ssc-1", "kind": "admit_card", "lang": "en", "exam": "CGL", "examYear": 2026, "publishedAt": "2026-09-21T10:00:00+05:30",
     "title": 'City of examination <script>alert(1)</script> & "admit card"',
     "files": [{"name": "a.pdf", "url": "https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/a.pdf", "size": 1}]},
    {"id": "ssc-2", "kind": "result", "lang": "en", "exam": "CTGD", "examYear": 2026, "publishedAt": "2026-10-12T18:47:07+05:30",
     "title": "Constable (GD) 2026 - shortlisting", "files": [{"name": "x.pdf", "url": "https://evil.example/x.pdf", "size": 1}]},
]


def test_pages_for_exams_with_rules_or_notices_and_a_sitemap(tmp_path):
    paths = sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, "https://example.in/india")
    assert set(paths) == {"jobs/exam/cgl/", "jobs/exam/chsl/", "jobs/exam/steno/", "jobs/exam/capf/", "jobs/exam/ctgd/",
                          *(f"jobs/topic/{h[0]}/" for h in sitegen.HUBS), "jobs/about/", "jobs/privacy/", "jobs/terms/"}
    sitemap = open(os.path.join(str(tmp_path), "jobs", "sitemap.xml")).read()
    assert "<loc>https://example.in/india/jobs/exam/cgl/</loc>" in sitemap and "<loc>https://example.in/india/jobs/</loc>" in sitemap


def test_cgl_page_says_what_people_search_for_from_the_notice(tmp_path):
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, "https://example.in/india")
    page = open(os.path.join(str(tmp_path), "jobs", "exam", "cgl", "index.html"), encoding="utf-8").read()
    assert "<title>SSC CGL 2026: Exam Date, Notification, Age Limit, Salary, Admit Card, Result</title>" in page
    assert '<link rel="canonical" href="https://example.in/india/jobs/exam/cgl/">' in page
    assert "30 Sep 2026 to 30 Oct 2026 (tentative)" in page            # Tier-I, SSC notice of 12-09-2026
    assert "Applications closed on 25 Jun 2026" in page
    assert "born 2 Aug 1999 to 1 Aug 2008" in page                      # 18-27 band, Notice para 5.1
    assert "Level 8: basic pay ₹47,600 to ₹1,51,100 a month" in page    # Notice para 2.1, Indian grouping
    assert "About 12,256 (tentative)" in page
    assert "No fee for: women, SC, ST, persons with benchmark disabilities, ex-servicemen." in page
    assert 'href="../../#check/ssc-cgl-2026"' in page
    assert "<strong>Qualification:</strong> Bachelor&#x27;s degree (CGL 2026 Notice para 8)" in page
    assert "<h3>What is the last date to apply for SSC CGL 2026?</h3><p>25 Jun 2026. Fee payment closes on 26 Jun 2026.</p>" in page
    assert "<h3>When is the SSC CGL 2026 exam?</h3>" in page


def test_untrusted_text_is_escaped_and_only_ssc_links_are_kept(tmp_path):
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, None)
    cgl = open(os.path.join(str(tmp_path), "jobs", "exam", "cgl", "index.html"), encoding="utf-8").read()
    assert "<script>" not in cgl and "&lt;script&gt;" in cgl and "&quot;admit card&quot;" in cgl
    ctgd = open(os.path.join(str(tmp_path), "jobs", "exam", "ctgd", "index.html"), encoding="utf-8").read()
    assert "evil.example" not in ctgd and "SSC GD Constable 2026" in ctgd
    hrefs = re.findall(r'href="([^"]+)"', cgl + ctgd)
    # Outside links only to ssc.gov.in; the rest stay on this site (frame: home, about, privacy, terms).
    assert all(h.startswith("https://ssc.gov.in") or (h.startswith("../../") and "//" not in h[6:]) for h in hrefs), hrefs
    assert not os.path.exists(os.path.join(str(tmp_path), "jobs", "sitemap.xml"))  # no address, no sitemap


def test_a_new_day_alone_does_not_rewrite_the_page(tmp_path):
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, None)
    path = os.path.join(str(tmp_path), "jobs", "exam", "ctgd", "index.html")
    before = open(path).read()
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY + dt.timedelta(days=1), None)
    assert open(path).read() == before


def test_indian_grouping():
    assert [sitegen.inr(n) for n in (5, 999, 1000, 12256, 151100, 181570, 12345678)] == \
        ["5", "999", "1,000", "12,256", "1,51,100", "1,81,570", "1,23,45,678"]


def test_up_police_page_names_its_own_board(tmp_path):
    item = {"id": "upprpb-1", "kind": "result", "lang": "hi", "exam": "UPPC", "examYear": 2025, "source": "upprpb",
            "dateOnly": True, "publishedAt": "2026-10-14T00:00:00+05:30", "title": "आरक्षी भर्ती-2025 परिणाम",
            "files": [{"name": "a.pdf", "url": "https://uppbpb.gov.in/FilesUploaded/Notice/a.pdf", "size": None}]}
    sitegen.build(str(tmp_path), FAMILIES, RULES, [item], TODAY, None)
    page = open(os.path.join(str(tmp_path), "jobs", "exam", "uppc", "index.html"), encoding="utf-8").read()
    assert "UP Police Constable 2025" in page and "uppbpb.gov.in" in page
    assert "SSC" not in page and 'href="https://uppbpb.gov.in/FilesUploaded/Notice/a.pdf"' in page


def test_topic_pages_list_only_matching_notices_with_official_links(tmp_path):
    items = ITEMS + [
        {"id": "bpssc-1", "kind": "recruitment", "lang": "en", "exam": "BPSSC", "examYear": 2026, "source": "bpssc",
         "publishedAt": "2026-10-01T00:00:00+05:30", "title": "Advt. No.-09/2026: For Selection of Police Sub-Inspector", "dateOnly": True,
         "files": [{"name": "a.pdf", "url": "https://bpssc.bihar.gov.in/Notices/a.pdf", "size": None}]},
        {"id": "ongc-1", "kind": "recruitment", "lang": "en", "exam": "ONGC", "examYear": None, "source": "ongc",
         "publishedAt": "2026-10-16T00:00:00+05:30", "title": "Advertisement for the posts of Consultants", "dateOnly": True,
         "files": [], "link": "https://ongcindia.com/web/eng/detail?assetEntry=1"},
    ]
    sitegen.build(str(tmp_path), FAMILIES, RULES, items, TODAY, "https://example.in/india")
    read = lambda slug: open(os.path.join(str(tmp_path), "jobs", "topic", slug, "index.html"), encoding="utf-8").read()  # noqa: E731
    bihar, police, jobs, psu = read("bihar-govt-jobs"), read("police-jobs"), read("latest-govt-jobs"), read("psu-jobs")
    assert "<title>Bihar Govt Jobs 2026: Bihar Police constable and SI vacancy</title>" in bihar
    assert 'href="https://bpssc.bihar.gov.in/Notices/a.pdf"' in bihar and "ONGC" not in bihar.split("<h2>More</h2>")[0]
    assert "Police Sub-Inspector" in police and "Constable (GD) 2026 - shortlisting" in police  # CTGD is SSC + police
    assert "evil.example" not in police
    assert "Police Sub-Inspector" in jobs and "Consultants" in jobs and "shortlisting" not in jobs  # new jobs only
    assert 'href="https://ongcindia.com/web/eng/detail?assetEntry=1"' in psu
    assert '<link rel="canonical" href="https://example.in/india/jobs/topic/psu-jobs/">' in psu
    sitemap = open(os.path.join(str(tmp_path), "jobs", "sitemap.xml")).read()
    assert "<loc>https://example.in/india/jobs/topic/police-jobs/</loc>" in sitemap


def test_every_page_has_the_basic_site_pages_and_a_link_preview(tmp_path):
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, "https://example.in/india", "help@example.in")
    read = lambda p: open(os.path.join(str(tmp_path), p), encoding="utf-8").read()  # noqa: E731
    cgl = read("jobs/exam/cgl/index.html")
    for slug in ("about", "privacy", "terms", "contact"):
        assert f'href="../../{slug}/"' in cgl
    assert '<meta property="og:image" content="https://example.in/india/jobs/icon-512.png">' in cgl
    ld = json.loads(re.search(r'<script type="application/ld\+json">(.*?)</script>', cgl).group(1))
    assert [x["name"] for x in ld["itemListElement"]] == ["Home", "Exams", "SSC CGL"]
    assert ld["itemListElement"][0]["item"] == "https://example.in/india/jobs/"
    contact, privacy = read("jobs/contact/index.html"), read("jobs/privacy/index.html")
    assert 'href="mailto:help@example.in"' in contact and '<link rel="canonical" href="https://example.in/india/jobs/contact/">' in contact
    assert "No account" in privacy and "last two digits" in privacy and 'href="../"' in privacy
    about = read("jobs/about/index.html")
    assert "uppbpb.gov.in" in about and "isro.gov.in" in about  # every recruiter we read, from sources.py
    missing = read("404.html")
    assert '<meta name="robots" content="noindex">' in missing and "canonical" not in missing
    assert 'href="https://example.in/india/jobs/topic/latest-govt-jobs/"' in missing  # full links: shown at any address
    sitemap = read("jobs/sitemap.xml")
    assert "<loc>https://example.in/india/jobs/privacy/</loc>" in sitemap and "404" not in sitemap


def test_no_contact_address_means_no_contact_page_or_link(tmp_path):
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, None, "help@example.in")
    assert os.path.exists(os.path.join(str(tmp_path), "jobs", "contact", "index.html"))
    sitegen.build(str(tmp_path), FAMILIES, RULES, ITEMS, TODAY, None)
    assert not os.path.exists(os.path.join(str(tmp_path), "jobs", "contact", "index.html"))  # taken away again
    terms = open(os.path.join(str(tmp_path), "jobs", "terms", "index.html"), encoding="utf-8").read()
    assert "contact/" not in terms and "mailto:" not in terms and "cybercrime.gov.in" in terms
    assert 'href="/jobs/topic/' in open(os.path.join(str(tmp_path), "404.html"), encoding="utf-8").read()
