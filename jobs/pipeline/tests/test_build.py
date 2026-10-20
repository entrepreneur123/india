import datetime as dt
import json
import os

import pytest

from fake_ssc import FakeSSC, load
from sarkari import build, ssc

NOW = dt.datetime(2026, 10, 20, 17, 0, tzinfo=ssc.IST)


def _read(out, name):
    with open(os.path.join(out, "jobs", "v1", name), encoding="utf-8") as f:
        return json.load(f)


def _files(out):
    root = os.path.join(out, "jobs", "v1")
    return {os.path.relpath(os.path.join(d, f), root) for d, _, fs in os.walk(root) for f in fs}


def test_first_run_publishes_everything_and_announces_nothing(tmp_path):
    out = str(tmp_path)
    assert build.run(out, FakeSSC(), NOW) == 0
    assert _files(out) == {"feed.json", "calendar.json", "new.json", "status.json",
                           "rules/index.json", "rules/ssc-cgl-2026.json", "rules/ssc-chsl-2026.json"}
    feed = _read(out, "feed.json")
    assert feed["schema"] == 1 and len(feed["items"]) > 100
    assert len({i["id"] for i in feed["items"]}) == len(feed["items"])
    assert all(i["publishedAt"] >= "2026-06-22" for i in feed["items"])  # 120-day window
    assert _read(out, "new.json")["items"] == []
    idx = _read(out, "rules/index.json")["exams"]
    assert {e["id"]: e["application"] for e in idx} == {"ssc-cgl-2026": "closed", "ssc-chsl-2026": "closed"}


def test_unchanged_source_stops_after_one_request(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    feed_before = os.path.getmtime(os.path.join(out, "jobs", "v1", "feed.json"))
    fake = FakeSSC()
    assert build.run(out, fake, NOW + dt.timedelta(minutes=30)) == 0
    assert len(fake.urls) == 1 and fake.urls[0].endswith("/lastUpdates")
    assert os.path.getmtime(os.path.join(out, "jobs", "v1", "feed.json")) == feed_before
    # ...but a full check still runs every FULL_CHECK_HOURS, since results can skip lastUpdates.
    fake = FakeSSC()
    build.run(out, fake, NOW + dt.timedelta(hours=build.FULL_CHECK_HOURS, minutes=1))
    assert len(fake.urls) > 1


def test_a_new_notice_is_announced_once(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    fake = FakeSSC()
    page = load("notice_boards_english_p1.json")
    page["data"].insert(0, {
        "id": "zz1newnotice2026", "headline": "Notice of Multi Tasking (Non-Technical) Staff Examination, 2026",
        "examId": "gh67ytxj8tmts26", "language": "english", "createdAt": "2026-10-20T06:00:00.000Z",
        "attachments": [{"fileName": "Notice_MTS_2026.pdf", "size": 1000,
                         "path": "uploads\\masterData\\NoticeBoards\\Notice_MTS_2026.pdf"}]})
    fake.overrides[("notice-boards", "english", 1)] = page
    build.run(out, fake, NOW + dt.timedelta(hours=1), force=True)
    new = _read(out, "new.json")["items"]
    assert [(i["id"], i["kind"], i["exam"], i["examYear"]) for i in new] == \
        [("ssc-zz1newnotice2026", "recruitment", "MTS", 2026)]
    assert _read(out, "feed.json")["items"][0]["id"] == "ssc-zz1newnotice2026"
    # Next run: same content, so nothing new any more.
    build.run(out, fake, NOW + dt.timedelta(hours=2), force=True)
    assert _read(out, "new.json")["items"] == []


def test_old_records_brought_in_by_a_bigger_page_are_not_news(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW, window_days=30)
    build.run(out, FakeSSC(), NOW + dt.timedelta(hours=1), window_days=120, force=True)
    assert _read(out, "new.json")["items"] == []


def test_api_failure_leaves_previous_files_alone(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    before = _read(out, "feed.json")
    fake = FakeSSC()
    fake.overrides[("results", None, 1)] = {"statusCode": "200", "data": [],
                                            "paginate": {"totalRecords": 0, "totalPerpage": 0, "nextPage": 0}}
    with pytest.raises(ssc.FormatChanged, match="results/all: API returned no records"):
        build.run(out, fake, NOW + dt.timedelta(hours=1), force=True)
    assert _read(out, "feed.json") == before


def test_main_exits_1_on_error(tmp_path, monkeypatch):
    def broken(url):
        raise build.http.FetchError("connection reset")
    monkeypatch.setattr(build.http, "get_json", broken)
    assert build.main(["--out", str(tmp_path)]) == 1
    assert not os.path.exists(os.path.join(str(tmp_path), "jobs"))


def test_a_listing_that_ages_out_of_the_window_is_not_an_error(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    assert _read(out, "status.json")["streamCounts"]["admit-card/all"] == 2  # both from 2024
    # 400 days later every fixture record is outside the 120-day window: still a valid run.
    later = NOW + dt.timedelta(days=400)
    with pytest.raises(ssc.FormatChanged, match="no items at all"):
        build.run(out, FakeSSC(), later, force=True)  # all streams empty -> refuses, by design
    page = load("answer_key_all_p1.json")
    page["data"][0]["createdAt"] = (later - dt.timedelta(days=1)).astimezone(dt.timezone.utc).isoformat()
    fake = FakeSSC()
    fake.overrides[("answer-key", None, 1)] = page
    assert build.run(out, fake, later, force=True) == 0
    assert len(_read(out, "feed.json")["items"]) == 1
