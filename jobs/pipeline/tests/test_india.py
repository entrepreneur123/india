import datetime as dt
import json
import os

from sarkari import india, listboards, ssc

HERE = os.path.dirname(__file__)
NOW = dt.datetime(2026, 10, 29, 12, tzinfo=ssc.IST)


def page(url):
    for board in india.BOARDS:
        if url == board.NOTICE_PAGE:
            return open(os.path.join(HERE, "fixtures", f"{board.SOURCE}.html"), encoding="utf-8").read()
    raise AssertionError(url)


def read(out):
    return json.load(open(os.path.join(out, "jobs", "v1", "india.json"), encoding="utf-8"))


def test_reads_only_the_india_only_boards_into_its_own_file(tmp_path):
    assert {b.SOURCE for b in india.BOARDS} == listboards.INDIA_ONLY == {"mppsc", "ctet"}
    assert india.collect(str(tmp_path), page, NOW, warn=lambda _: None)
    doc = read(str(tmp_path))
    assert doc["boards"] == {"mppsc": {"ok": True}, "ctet": {"ok": True}}
    assert {i["source"] for i in doc["items"]} == {"mppsc", "ctet"} and len(doc["items"]) == 17  # 120 days back
    assert min(i["publishedAt"] for i in doc["items"]) >= "2026-07-01"
    assert [i["publishedAt"] for i in doc["items"]] == sorted((i["publishedAt"] for i in doc["items"]), reverse=True)
    assert not india.collect(str(tmp_path), page, NOW + dt.timedelta(minutes=30), warn=lambda _: None)  # no news: no commit


def test_a_board_that_fails_keeps_its_items_and_says_why(tmp_path):
    india.collect(str(tmp_path), page, NOW, warn=lambda _: None)
    before = sum(i["source"] == "mppsc" for i in read(str(tmp_path))["items"])

    def mppsc_down(url):
        if "mppsc" in url:
            raise OSError("timed out")
        return page(url)
    warnings = []
    assert india.collect(str(tmp_path), mppsc_down, NOW, warn=warnings.append)  # the board status changed
    doc = read(str(tmp_path))
    assert doc["boards"]["mppsc"] == {"ok": False, "error": "OSError: timed out"} and doc["boards"]["ctet"] == {"ok": True}
    assert sum(i["source"] == "mppsc" for i in doc["items"]) == before > 0 and warnings
