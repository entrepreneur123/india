import datetime as dt
import json
import os

from sarkari import boards, india, ssc

HERE = os.path.dirname(__file__)
NOW = dt.datetime(2026, 10, 29, 12, tzinfo=ssc.IST)


EXTRA = {"https://opportunities.rbi.org.in/Scripts/resultsnew.aspx": "rbi-results"}  # boards read from two pages


def page(url):
    if url in EXTRA:
        return open(os.path.join(HERE, "fixtures", f"{EXTRA[url]}.html"), encoding="utf-8").read()
    for board in india.BOARDS:
        if url == board.NOTICE_PAGE:
            return open(os.path.join(HERE, "fixtures", f"{board.SOURCE}.html"), encoding="utf-8").read()
    raise AssertionError(url)


def read(out):
    return json.load(open(os.path.join(out, "jobs", "v1", "india.json"), encoding="utf-8"))


def test_reads_only_the_india_only_boards_into_its_own_file(tmp_path):
    assert {b.SOURCE for b in india.BOARDS} == boards.INDIA_ONLY
    assert {"mppsc", "ctet", "rrb", "sbi", "ibps", "rpsc", "bpsc"} <= boards.INDIA_ONLY and "isro" not in boards.INDIA_ONLY
    assert india.collect(str(tmp_path), page, NOW, warn=lambda _: None)
    doc = read(str(tmp_path))
    assert doc["boards"] == {b.SOURCE: {"ok": True} for b in india.BOARDS}
    assert {i["source"] for i in doc["items"]} <= boards.INDIA_ONLY
    assert sum(i["source"] in ("mppsc", "ctet") for i in doc["items"]) == 17  # 120 days back
    assert min(i["publishedAt"] for i in doc["items"]) >= "2026-07-01"
    assert [i["publishedAt"] for i in doc["items"]] == sorted((i["publishedAt"] for i in doc["items"]), reverse=True)
    # Undated boards record their page on the first run and publish none of its undated entries.
    assert set(doc["seen"]) == {b.SOURCE for b in india.BOARDS if b.undated} == {"sbi", "psssb", "iocl", "uppsc"}
    assert all(day == "" for days in doc["seen"].values() for day in days.values()) and len(doc["seen"]["sbi"]) == 82
    assert not any(i["source"] == "psssb" for i in doc["items"])
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
    assert doc["boards"]["rrb"] == {"ok": True}
    assert sum(i["source"] == "mppsc" for i in doc["items"]) == before > 0 and warnings


def test_pages_are_fetched_side_by_side_and_a_failure_stays_with_its_board(tmp_path):
    import threading
    running, peak, lock = [0], [0], threading.Lock()

    def slow(url):
        with lock:
            running[0] += 1
            peak[0] = max(peak[0], running[0])
        threading.Event().wait(0.05)
        with lock:
            running[0] -= 1
        if "mppsc" in url:
            raise OSError("timed out")
        return page(url)
    get = india.prefetch(slow, [b.NOTICE_PAGE for b in india.BOARDS])
    assert peak[0] > 1
    warnings = []
    india.collect(str(tmp_path), get, NOW, warn=warnings.append)
    doc = read(str(tmp_path))
    assert doc["boards"]["mppsc"] == {"ok": False, "error": "OSError: timed out"}
    assert all(v == {"ok": True} for k, v in doc["boards"].items() if k != "mppsc")
