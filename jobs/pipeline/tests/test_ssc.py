import datetime as dt

import pytest

from fake_ssc import FakeSSC, load
from sarkari import ssc

NOW = dt.datetime(2026, 10, 20, 17, 0, tzinfo=ssc.IST)
EXAMS = ssc.exam_index(load("all_exams.json"))


def test_attachment_url():
    assert ssc.attachment_url("uploads\\masterData\\NoticeBoards\\Important Notice 2026_cgle_2026_12092026.pdf") == \
        "https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/Important%20Notice%202026_cgle_2026_12092026.pdf"
    # A real SSC file name (notice board, 2026): the double dot is not a traversal.
    assert ssc.attachment_url("uploads\\masterData\\NoticeBoards\\Write_up_Final_Answer_Keys_CBE_min_2025..pdf") \
        .endswith("/NoticeBoards/Write_up_Final_Answer_Keys_CBE_min_2025..pdf")


@pytest.mark.parametrize("path", [
    "uploads\\masterData\\NoticeBoards\\..\\..\\secret.pdf",
    "/etc/passwd",
    "https://evil.example/x.pdf",
    "uploads\\masterData\\NoticeBoards\\run.exe",
])
def test_attachment_url_rejects_anything_else(path):
    with pytest.raises(ssc.RecordError):
        ssc.attachment_url(path)


def test_parse_time_converts_to_ist_and_rejects_nonsense():
    assert ssc.parse_time("2026-10-12T13:17:07.360Z", NOW).isoformat() == "2026-10-12T18:47:07.360000+05:30"
    for bad in ("2027-01-01T00:00:00Z", "2010-01-01T00:00:00Z", "2026-10-12T13:17:07", "12-10-2026", None):
        with pytest.raises(ssc.RecordError):
            ssc.parse_time(bad, NOW)


def test_parse_real_record():
    raw = next(r for r in load("notice_boards_english_p1.json")["data"]
               if r["headline"].startswith("Notice of Combined Higher Secondary"))
    item = ssc.parse_record(raw, None, EXAMS, NOW).to_json()
    assert item["kind"] == "recruitment" and item["exam"] == "CHSL" and item["examYear"] == 2026
    assert item["lang"] == "en" and item["publishedAt"].endswith("+05:30")
    assert item["files"][0]["url"] == \
        "https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/Notice_of_adv_chsle_2026.pdf"


def test_exam_id_beats_headline():
    raw = {"id": "abcdefgh12", "headline": "Important Notice", "createdAt": "2026-10-01T10:00:00Z",
           "examId": "w2sxxhjkshdcgl26", "attachments": []}
    item = ssc.parse_record(raw, None, EXAMS, NOW)
    assert (item.exam, item.exam_year) == ("CGL", 2026)


def test_only_government_links_are_kept():
    base = {"id": "abcdefgh12", "headline": "x", "createdAt": "2026-10-01T10:00:00Z", "attachments": []}
    assert ssc.parse_record({**base, "redirectUrl": "https://ssc.gov.in/home/apply"}, None, {}, NOW).link
    assert ssc.parse_record({**base, "redirectUrl": "https://dopt.gov.in/x"}, None, {}, NOW).link
    for link in ("http://ssc.gov.in/x", "https://ssc.gov.in.evil.com/", "https://bit.ly/x", "javascript:alert(1)"):
        assert ssc.parse_record({**base, "redirectUrl": link}, None, {}, NOW).link is None, link


def test_fetch_stream_pages_until_cutoff():
    fake = FakeSSC()
    cutoff = NOW - dt.timedelta(days=120)
    items, returned = ssc.fetch_stream(fake, "results", None, "result", EXAMS, NOW, cutoff, max_pages=4,
                                       warn=lambda m: None)
    assert returned == 100
    assert items and all(i.published >= cutoff for i in items)
    assert items == sorted(items, key=lambda i: (i.published, i.id), reverse=True)
    # Page 1 already reaches past the cutoff, so page 2 is never requested.
    assert len([u for u in fake.urls if "contentType=results" in u]) == 1
    # The 4 results of 14-10-2026 that the stale cached page 1 was missing are here.
    assert sum(i.published.date() == dt.date(2026, 10, 14) for i in items) == 4


def test_mixed_cached_and_live_pages_are_rejected():
    """Regression for the stale cache measured on 20-10-2026: page 1 (6/page, 382 records)
    with page 2 (100/page, 386 records) silently skips records 7-100."""
    fake = FakeSSC()
    fake.overrides[("results", None, 1)] = load("results_cached_p1.json")
    with pytest.raises(ssc.FormatChanged, match="cached pages mixed"):
        ssc.fetch_stream(fake, "results", None, "result", EXAMS, NOW, NOW - dt.timedelta(days=900),
                         max_pages=2, warn=lambda m: None)


def test_many_bad_records_mean_a_format_change():
    fake = FakeSSC()
    page = load("answer_key_all_p1.json")
    for r in page["data"][:30]:
        r["createdAt"] = "yesterday"
    fake.overrides[("answer-key", None, 1)] = page
    with pytest.raises(ssc.FormatChanged, match="failed validation"):
        ssc.fetch_stream(fake, "answer-key", None, "answer_key", EXAMS, NOW, NOW - dt.timedelta(days=900),
                         max_pages=1, warn=lambda m: None)


def test_merge_keeps_the_copy_with_more_files():
    t = dt.datetime(2026, 10, 12, 18, 0, tzinfo=ssc.IST)
    a = ssc.Item("ssc-a", "result", "en", "Final Result", "CHSL", 2025, t, (("w.pdf", "u1", 1),))
    b = ssc.Item("ssc-b", "result", "en", "final result", "CHSL", 2025, t, (("w.pdf", "u1", 1), ("l.pdf", "u2", 2)))
    c = ssc.Item("ssc-c", "notice", "en", "Other", None, None, t - dt.timedelta(hours=1), ())
    assert [i.id for i in ssc.merge([[a, c], [b]])] == ["ssc-b", "ssc-c"]


def test_calendar():
    cal = ssc.parse_calendar(load("calendar_en.json"), EXAMS)
    assert len(cal) == 13 and all(e["tentative"] for e in cal)
    cgl = next(e for e in cal if e["exam"] == "CGL")
    assert (cgl["applyFrom"], cgl["applyTo"], cgl["examYear"]) == ("2026-03-31", "2026-04-30", 2026)
    assert cgl["examWindow"] == "May – June 2026"


def test_exam_index():
    assert EXAMS["w2sxxhjkshdcgl26"] == ("CGL", 2026)
    assert EXAMS["dpce00ab6eu9si1c"] == ("DP", 2025)
    assert EXAMS["yafd7c3qloz8ixl"] == (None, None)
    with pytest.raises(ssc.FormatChanged):
        ssc.exam_index({"data": []})
