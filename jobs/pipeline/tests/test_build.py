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
    assert _files(out) == {"feed.json", "calendar.json", "new.json", "status.json", "resources.json", "exams.json", "guide.json", "site.json",
                           "rules/index.json", "rules/ssc-cgl-2026.json", "rules/ssc-chsl-2026.json",
                           "rules/ssc-steno-2026.json", "rules/ssc-si-2026.json"}
    feed = _read(out, "feed.json")
    assert feed["schema"] == 1 and len(feed["items"]) > 100
    assert len({i["id"] for i in feed["items"]}) == len(feed["items"])
    assert all(i["publishedAt"] >= "2026-06-22" for i in feed["items"])  # 120-day window
    assert _read(out, "new.json")["items"] == []
    idx = _read(out, "rules/index.json")["exams"]
    assert {e["id"]: e["application"] for e in idx} == {
        "ssc-cgl-2026": "closed", "ssc-chsl-2026": "closed", "ssc-steno-2026": "closed", "ssc-si-2026": "closed"}


def test_unchanged_source_stops_after_one_request(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    feed_before = os.path.getmtime(os.path.join(out, "jobs", "v1", "feed.json"))
    status_before = os.path.getmtime(os.path.join(out, "jobs", "v1", "status.json"))
    fake = FakeSSC()
    assert build.run(out, fake, NOW + dt.timedelta(minutes=30)) == 0
    assert len(fake.urls) == 1 and fake.urls[0].endswith("/lastUpdates")
    assert os.path.getmtime(os.path.join(out, "jobs", "v1", "feed.json")) == feed_before
    assert os.path.getmtime(os.path.join(out, "jobs", "v1", "status.json")) == status_before  # no commit for no news
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


def test_official_resources_and_exam_families(tmp_path):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    sections = _read(out, "resources.json")["sections"]
    assert set(sections) == {"syllabus", "vacancy", "previous_papers", "certificate_formats", "instructions"}
    assert len(sections["syllabus"]) == 5 and len(sections["vacancy"]) == build.ssc.RESOURCE_MAX
    assert all(f["url"].startswith("https://ssc.gov.in/api/attachment/") for s in sections.values()
               for i in s for f in i["files"])
    fams = _read(out, "exams.json")["families"]
    assert fams["CGL"]["qualifications"] == ["graduate"] and fams["MTS"]["qualifications"] is None


def test_guide_is_published_and_checked(tmp_path, monkeypatch):
    out = str(tmp_path)
    build.run(out, FakeSSC(), NOW)
    g = _read(out, "guide.json")
    assert g["helplines"]["list"][0]["phones"] == ["1800 309 3063"]  # CHSL 2026 Notice p.1 and p.37
    assert {t["id"]: t["kdph"] for t in g["skillTests"]["tests"]}["chsl-typing-en"] == 10500  # para 13.9.13.7
    assert all(e["ref"].startswith("para ") for e in g["steps"] + g["documents"])
    bad = tmp_path / "guide.json"
    bad.write_text(json.dumps({"steps": [{"en": "x"}], "documents": [], "helplines": {}}))
    monkeypatch.setattr(build, "GUIDE", str(bad))
    with pytest.raises(build.ssc.FormatChanged, match="ssc_guide"):
        build.run(out, FakeSSC(), NOW + dt.timedelta(hours=4))


UP_PAGE = open(os.path.join(os.path.dirname(__file__), "fixtures", "upprpb_notice.html"), encoding="utf-8").read()


def boards(url):
    """The other boards' saved pages: UP Police (HTML) and HSSC (JSON)."""
    if url.startswith("https://hssc.gov.in/"):
        from test_hssc import fake
        return fake()(url)
    return UP_PAGE


def test_up_police_board_joins_the_feed_and_a_failure_keeps_its_old_items(tmp_path):
    out = str(tmp_path)
    assert build.run(out, FakeSSC(), NOW, get_text=boards) == 0
    feed = _read(out, "feed.json")["items"]
    up = [i for i in feed if i["source"] == "upprpb"]
    assert len(up) == 27 and all(i["dateOnly"] for i in up)
    assert {i["source"] for i in feed} == {"ssc", "upprpb", "hssc"}
    assert [i["publishedAt"] for i in feed] == sorted((i["publishedAt"] for i in feed), reverse=True)
    assert _read(out, "status.json")["failedSources"] == []
    assert os.path.exists(os.path.join(out, "jobs", "exam", "uppc", "index.html"))

    def down(url):
        raise build.http.FetchError("timed out")
    assert build.run(out, FakeSSC(), NOW + dt.timedelta(hours=4), get_text=down) == 0  # SSC still published
    assert [i for i in _read(out, "feed.json")["items"] if i["source"] == "upprpb"] == up
    assert _read(out, "status.json")["failedSources"] == ["upprpb", "hssc"]

    # Kept items still age out of the window.
    build.run(out, FakeSSC(), NOW + dt.timedelta(days=60), get_text=down)
    kept = [i for i in _read(out, "feed.json")["items"] if i["source"] == "upprpb"]
    assert kept and all(i["publishedAt"] >= "2026-08-20" for i in kept) and len(kept) < 27


def test_without_pdftotext_no_pdf_is_downloaded(tmp_path, monkeypatch):
    monkeypatch.setattr(build.shutil, "which", lambda name: None)

    def get_bytes(url):
        raise AssertionError(f"downloaded {url}")
    assert build.run(str(tmp_path), FakeSSC(), NOW, get_bytes=get_bytes) == 0


@pytest.mark.parametrize("channel,link", [
    ("@SarkariAlertsIN", "https://t.me/SarkariAlertsIN"),
    ("-1001234567890", None),  # a numeric chat id: private, never shown
    ("@a b", None), ("", None), (None, None),
])
def test_only_a_public_telegram_channel_is_linked(channel, link):
    assert build.site_info(channel).get("telegram") == link


def test_full_check_plan():
    status = {"fullCheckAt": NOW.isoformat(), "sourceUpdatedAt": "A"}
    assert not build.needs_full_check(status, "A", NOW + dt.timedelta(minutes=30))
    assert build.needs_full_check(status, "B", NOW + dt.timedelta(minutes=30))  # SSC posted something
    assert build.needs_full_check(status, "A", NOW + dt.timedelta(hours=build.FULL_CHECK_HOURS))
    assert build.needs_full_check({}, "A", NOW) and build.needs_full_check(status, "A", NOW, force=True)
