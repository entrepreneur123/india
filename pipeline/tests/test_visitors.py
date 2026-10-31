import datetime as dt

from lottery_pipeline import visitors


def test_daily_visits_add_up_across_beacons():
    g = lambda date, tag, v, c: {"count": c, "sum": {"visits": v}, "dimensions": {"date": date, "siteTag": tag}}
    data = {"daily": [g("2026-10-30", "a", 10, 30), g("2026-10-30", "b", 2, 5), g("2026-10-31", "a", 4, 9)],
            "countries": [{"count": 40, "sum": {"visits": 15}, "dimensions": {"countryName": "IN"}}],
            "pages": [{"count": 20, "sum": {"visits": 8}, "dimensions": {"requestPath": "/"}}]}
    md = visitors.summarize(data, dt.date(2026, 10, 30), dt.date(2026, 10, 31), "now")
    assert "| 2026-10-30 | 12 | 35 |" in md and "| 2026-10-31 | 4 | 9 |" in md
    assert "| IN | 15 | 40 |" in md and "| / | 8 | 20 |" in md


def test_a_missing_permission_is_reported_not_fatal(tmp_path, monkeypatch):
    monkeypatch.delenv("CLOUDFLARE_API_TOKEN", raising=False)
    monkeypatch.setattr("sys.argv", ["visitors", "--out", str(tmp_path)])
    assert visitors.main() == 0
    assert "Not read" in (tmp_path / "latest.md").read_text()
