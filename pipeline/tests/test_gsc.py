from lottery_pipeline import gsc


def row(**k):
    return dict(dict(clicks=0, impressions=5, ctr=0.0, position=12.0), **k)


def test_summary_lists_striking_distance_and_target_queries():
    rep = {"generated": "2026-10-29T10:00+00:00", "start": "2026-10-01", "end": "2026-10-29",
           "countries": [row(country="ind", impressions=214)], "days": [row(date="2026-10-27"), row(date="2026-10-25")],
           "queries_india": [row(query="dear6pm", position=6.5, impressions=12), row(query="lottery result today", position=45.0),
                             row(query="sun lotto", position=2.0, impressions=1)],
           "pages": [row(page="https://lottodost.com/")], "query_pages_india": [],
           "inspections": [{"url": "https://lottodost.com/", "verdict": "PASS", "coverage": "Submitted and indexed"}]}
    md = gsc.summarize(rep)
    near = md.split("## India: striking distance")[1].split("##")[0]
    assert "| dear6pm |" in near and "lottery result today" not in near and "sun lotto" not in near
    target = md.split("## India: target queries")[1].split("##")[0]
    assert "| lottery result today |" in target and "| dear6pm |" in target
    daily = md.split("## Daily")[1].split("##")[0]
    assert daily.index("2026-10-25") < daily.index("2026-10-27")
    assert "Submitted and indexed" in md


def test_without_the_key_it_does_nothing(tmp_path, monkeypatch, capsys):
    monkeypatch.delenv("GSC_SERVICE_ACCOUNT_JSON", raising=False)
    monkeypatch.delenv("GSC_ACCESS_TOKEN", raising=False)
    monkeypatch.setattr("sys.argv", ["gsc", "--out", str(tmp_path / "gsc")])
    assert gsc.main() == 0 and not (tmp_path / "gsc").exists()


def test_a_token_is_sent_as_bearer():
    s = gsc.session(token="ya29.test")
    assert s.headers["Authorization"] == "Bearer ya29.test"
