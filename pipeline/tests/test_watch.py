import datetime as dt
import json

from lottery_pipeline import watch

UTC = dt.timezone.utc


def feed(draws, gen="2026-10-30T12:50:00+00:00"):
    return {"generatedAt": gen, "draws": draws}


def draw(date, slot, tiers=None):
    return {"id": f"{date}_{slot}", "date": date, "slot": slot,
            "tiers": tiers if tiers is not None else [{"rank": "1", "numbers": ["68C 85552"]}, {"rank": "2", "numbers": ["03798", "08875"]}]}


def run(now, draws, home="<title>Lottery Result Today 30.10.2026</title>", mh=None, runs=None, gen=None):
    files = {"/v2/today.json": feed(draws, gen or now.isoformat()), "/v2/index.json": {"noDraw": []}}
    files.update(mh or {})

    def fetch(url):
        path = url.removeprefix("https://x.test")
        if path not in files:
            raise OSError("404")
        return files[path]
    return watch.checks("https://x.test", now, fetch=fetch, fetch_text=lambda u: home, runs=runs)


KL_TIERS = [{"rank": "1", "numbers": ["PA 402332"]}, {"rank": "5", "numbers": ["0137", "2264"]}]
ALL_29 = [draw("2026-10-29", s) for s in ("NL1PM", "SK6PM", "NL8PM")] + [draw("2026-10-29", "KL", KL_TIERS)]


def test_a_late_draw_opens_an_incident_and_a_missing_one_of_yesterday_too():
    now = dt.datetime(2026, 10, 30, 8, 5, tzinfo=UTC)          # 13:35 IST: the 1 PM result is 35 min old
    got = run(now, [d for d in ALL_29 if d["slot"] != "KL"])
    assert "late:2026-10-30:NL1PM" in got and "late:2026-10-29:KL" in got
    assert "late:2026-10-30:SK6PM" not in got
    assert run(now, ALL_29 + [draw("2026-10-30", "NL1PM")]) == {}


def test_impossible_numbers_wrong_home_date_stale_feed_and_failed_runs():
    now = dt.datetime(2026, 10, 30, 7, 20, tzinfo=UTC)          # 12:50 IST
    bad = draw("2026-10-29", "NL1PM", [{"rank": "2", "numbers": ["03798", "03798", "8O875"]}])
    got = run(now, [bad] + ALL_29[1:], home="<title>Lottery Result Today 29.10.2026</title>",
              runs=[{"databaseId": 7, "conclusion": "failure"}, {"databaseId": 8, "conclusion": "success"}],
              gen="2026-10-28T00:00:00+00:00")
    assert {"bad:2026-10-29_NL1PM:dup", "bad:2026-10-29_NL1PM:format", "home:2026-10-30:date", "run:7", "feed:stale"} <= set(got)
    assert "run:8" not in got


def test_maharashtra_unread_sheet_and_marathi_in_the_english_hub_title():
    now = dt.datetime(2026, 10, 30, 12, 0, tzinfo=UTC)
    mh = {"/v2/mh/days/2026-10-28.json": {"draws": [{"id": "x", "date": "2026-10-28", "name": "Maharashtra Akshay", "status": "PDF"}]}}
    got = run(now, ALL_29 + [draw("2026-10-30", "NL1PM"), draw("2026-10-30", "KL", KL_TIERS)], mh=mh,
              home="<title>Maharashtra महाराष्ट्र आकर्षक Lottery Result 30.10.2026</title>")
    assert "mh:2026-10-28:Maharashtra Akshay" in got and "mh:hub-title" in got


def test_incidents_stay_open_until_the_check_passes(tmp_path):
    t0 = dt.datetime(2026, 10, 30, 8, 0, tzinfo=UTC)
    opened, resolved = watch.update(tmp_path, {"late:a": {"kind": "late", "severity": "high", "detail": "x"}}, t0)
    assert opened == ["late:a"] and resolved == []
    opened, resolved = watch.update(tmp_path, {"late:a": {"kind": "late", "severity": "high", "detail": "x"}}, t0 + dt.timedelta(minutes=30))
    assert opened == []
    opened, resolved = watch.update(tmp_path, {}, t0 + dt.timedelta(hours=1))
    state = json.loads((tmp_path / "incidents.json").read_text())
    assert resolved == ["late:a"] and state["open"] == {} and state["resolved"]["late:a"]["first_seen"] == "2026-10-30T08:00+00:00"
    assert len((tmp_path / "log.jsonl").read_text().splitlines()) == 2
