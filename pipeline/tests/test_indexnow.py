import datetime as dt

from lottery_pipeline import indexnow


def test_fresh_urls_cover_languages_days_and_draws():
    feed = {"draws": [
        {"id": "2026-10-19_NL1PM", "slot": "NL1PM", "date": "2026-10-19", "tiers": [{}]},
        {"id": "2026-10-19_NLLAXMI", "slot": "NLLAXMI", "date": "2026-10-19", "status": "PDF_ONLY"},
        {"id": "2026-10-17_NL8PM", "slot": "NL8PM", "date": "2026-10-17", "tiers": [{}]},
    ]}
    urls = indexnow.fresh_urls("https://example.in", feed, dt.date(2026, 10, 19))
    assert "https://example.in/" in urls and "https://example.in/bn/" in urls
    assert "https://example.in/hi/result/19-10-2026/1pm/" in urls
    assert not any("laxmi" in u or "17-10-2026" in u for u in urls)  # PDF-only and old draws are not new pages
    assert "https://example.in/morning-1pm/" in urls and "https://example.in/hi/yesterday/" in urls  # pages that changed
    assert len(urls) == len(set(urls)) == 15


def test_kerala_draw_page_url_matches_the_site():
    feed = {"draws": [{"id": "2026-10-19_KL", "slot": "KL", "date": "2026-10-19", "tiers": [{}]}]}
    urls = indexnow.fresh_urls("https://example.in", feed, dt.date(2026, 10, 19))
    assert "https://example.in/result/19-10-2026/kerala/" in urls and "https://example.in/kerala/" in urls


def test_body_points_to_key_file_on_the_same_host():
    b = indexnow.body("https://entrepreneur123.github.io/india", ["https://entrepreneur123.github.io/india/"])
    assert b["host"] == "entrepreneur123.github.io"
    assert b["keyLocation"] == f"https://entrepreneur123.github.io/india/{indexnow.KEY}.txt"


def test_maharashtra_pages_are_announced():
    days = [{"draws": [{"date": "2026-10-27", "name": "Maharashtra Gajlaxmi Ravi", "status": "OK"},
                       {"date": "2026-10-27", "name": "Maharashtra Padmini", "status": "PDF"},
                       {"date": "2026-10-20", "name": "Maharashtra Sagarlaxmi", "status": "OK"}]}]
    urls = indexnow.mh_urls("https://lottodost.com", days, dt.date(2026, 10, 27))
    assert urls[:3] == ["https://lottodost.com/maharashtra/", "https://lottodost.com/bn/maharashtra/",
                        "https://lottodost.com/hi/maharashtra/"]
    assert "https://lottodost.com/result/27-10-2026/maharashtra-gajlaxmi-ravi/" in urls      # numbers: its own page
    assert "https://lottodost.com/hi/maharashtra/padmini/" in urls                            # the game page, sheet only
    assert not any("sagarlaxmi" in u for u in urls)                                            # a week old
    assert urls[-1] == "https://lottodost.com/mr/maharashtra/" and len(urls) == 3 * 4 + 1
    assert indexnow.mh_urls("https://lottodost.com", [{"draws": days[0]["draws"][2:]}], dt.date(2026, 10, 27)) == []
