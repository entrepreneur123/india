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
    assert len(urls) == len(set(urls)) == 9


def test_body_points_to_key_file_on_the_same_host():
    b = indexnow.body("https://entrepreneur123.github.io/india", ["https://entrepreneur123.github.io/india/"])
    assert b["host"] == "entrepreneur123.github.io"
    assert b["keyLocation"] == f"https://entrepreneur123.github.io/india/{indexnow.KEY}.txt"
