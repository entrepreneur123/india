import datetime as dt
import os

import pytest

from sarkari import listboards, sources, ssc

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 21)
CAPTURED = {"mppsc": dt.date(2026, 10, 29), "ctet": dt.date(2026, 10, 29), "rrb": dt.date(2026, 10, 29),
            "sbi": dt.date(2026, 10, 29)}  # from the India server's captures branch (sbi: fetched from here)


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=dt.date(2025, 10, 21), today=TODAY):
    warnings = []
    items = board.parse(page(board.SOURCE), max(today, CAPTURED.get(board.SOURCE, today)), cutoff, warn=warnings.append)
    assert warnings == []
    return items


@pytest.mark.parametrize("board,count", [(listboards.CSBC, 41), (listboards.BPSSC, 29), (listboards.ISRO, 23), (listboards.ONGC, 31),
                                         (listboards.MPPSC, 30), (listboards.CTET, 10), (listboards.RRB, 151),
                                         (listboards.SBI, 3)])
def test_real_pages_give_only_the_boards_own_links(board, count):
    items = parse(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(u.startswith(board.BASE + "/") for i in items for u in [f[1] for f in i.files] + [i.link] if u)
    assert all(bool(i.files) != bool(i.link) for i in items)  # a PDF, or else the board's own page
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert board.SOURCE in sources.SOURCES and sources.official(board.NOTICE_PAGE)


def _by_file(items):
    return {(i.files[0][0] if i.files else i.link.rsplit("/", 1)[1]): i for i in items}


def test_bihar_constable_board():
    got = _by_file(parse(listboards.CSBC))
    final = got["Results-01-2026-Final-09-10-2026.pdf"]
    assert (final.published.date(), final.kind, final.exam_year) == (dt.date(2026, 10, 9), "result", 2026)
    assert final.title.endswith("(Advt. No. 01/2026)")  # the number printed after the link
    assert got["Notice-02-2026-Change-in-ExamDate-26-05-2026.pdf"].kind == "amendment"
    assert got["Notice-01-2026-e-AdmitCard&Exam-20-05-2026.pdf"].kind == "admit_card"
    assert not any("apply-csbc.com" in (i.link or "") for i in got.values())  # the exam agency's portal


def test_bihar_si_board():
    got = _by_file(parse(listboards.BPSSC))
    assert got["Advt. No-09-2026 Pdf.pdf"].kind == "recruitment"
    assert got["Advt_042026_PRELIMS_Result_Publish_12082026.pdf"].published.date() == dt.date(2026, 8, 12)
    assert got["Exam Cancel Pdf.pdf"].kind == "amendment"
    assert got["Exam Cancel Pdf.pdf"].files[0][1] == "https://bpssc.bihar.gov.in/Notices/Exam%20Cancel%20Pdf.pdf"


def test_isro_dates_come_from_the_advertisement():
    first = parse(listboards.ISRO)[0]
    assert first.link == "https://www.isro.gov.in/SDSCRecruitment26.html"
    assert (first.published.date(), first.kind) == (dt.date(2026, 10, 19), "recruitment")


def test_ongc_keeps_recent_notices_and_ignores_its_archive():
    items = parse(listboards.ONGC)
    assert items[0].published.date() == dt.date(2026, 10, 16) and items[0].kind == "recruitment"
    assert all(i.published.date() >= dt.date(2025, 10, 21) for i in items)
    assert sum(i.kind == "admit_card" for i in items) == 3  # call letters


def test_look_alike_and_plain_http_links_are_dropped():
    b = listboards.CSBC
    row = '<tr><td>17-10-2026</td><td><a href="{}">Results: x</a></td></tr>'
    for href in ("https://csbc.bihar.gov.in.evil.example/a.pdf", "http://csbc.bihar.gov.in/a.pdf", "//evil.example/a.pdf"):
        assert b.parse_entry("17-10-2026", href, "Results: x", TODAY) is None
    assert b.parse_entry("17-10-2026", "Advt/a b.pdf", "Results: x", TODAY).files[0][1] == "https://csbc.bihar.gov.in/Advt/a%20b.pdf"
    with pytest.raises(ssc.RecordError):
        b.parse_entry("17-10-2027", "Advt/a.pdf", "Results: x", TODAY)  # a date in the future
    with pytest.raises(ssc.FormatChanged):
        b.parse(row.format("Advt/a.pdf"), TODAY, dt.date(2026, 1, 1))  # one row: the page came back broken


def test_a_changed_page_stops_instead_of_guessing():
    with pytest.raises(ssc.FormatChanged):
        listboards.ISRO.parse("<html>maintenance</html>", TODAY, dt.date(2026, 1, 1))


def test_mppsc_reads_dates_from_file_names_and_leaves_out_its_own_tenders():
    got = {i.files[0][0]: i for i in parse(listboards.MPPSC)}
    first = got["Vigyapti_District_Public_Health_Nursing_Officer_2025_Dated_28_10_2026.pdf"]
    assert first.published.date() == dt.date(2026, 10, 28) and first.lang == "hi"  # title from the link's title attribute
    assert first.files[0][1].startswith("https://mppsc.mp.gov.in/uploads/advertisement/")
    assert got["Advt_Law_Officer_Exam_2026_Dated_08_10_2026.pdf"].kind == "recruitment"
    assert got["Final_Answer_Key_SFS_Main_Exam_2026_Dated_16_10_2026.pdf"].kind == "answer_key"
    assert not any("Computer" in name for name in got)  # photocopier toner, interactive panels: purchases, not jobs
    assert got["Vigyapti_Assistant_Professor_Yogik_Vigyan_Exam_2025_Dated_23_10_20261.pdf"].published.date() == dt.date(2026, 10, 23)


def test_ctet_files_count_only_in_ctets_own_cdn_folder():
    items = parse(listboards.CTET)
    assert items[0].published.date() == dt.date(2026, 9, 14) and items[0].title == "PUBLIC NOTICE: Exam Dates for 22nd edition of CTET"
    assert all(sources.official(i.files[0][1]) for i in items)
    assert not sources.official("https://cdnbbsr.s3waas.gov.in/s0000000000000000000000000000000/uploads/2026/09/x.pdf")
    other = '<a download href="https://cdnbbsr.s3waas.gov.in/s0000000000000000000000000000000/uploads/2026/09/20260914111.pdf">x</a>'
    moved = '<a download href="' + listboards.CTET_FILES + 'uploads/2026/08/20260914111.pdf">y</a>'
    assert list(listboards._ctet_entries(other)) == []  # another site on the same CDN
    assert list(listboards._ctet_entries(moved))[0][0] == ""  # name and folder disagree: no date, counted as malformed


def test_rrb_updates_are_one_per_cen_category_and_day():
    items = parse(listboards.RRB)
    first = items[0]
    assert (first.title, first.published.date(), first.kind) == ("RRB CEN 01/2025: Exam Schedule", dt.date(2026, 10, 29), "schedule")
    assert first.link == "https://rrbchennai.gov.in/getdata?loc=chennai&cenum=01/2025&category=Exam%20Schedule"
    keys = [(i.title, i.published.date()) for i in items]
    assert len(keys) == len(set(keys))  # repeated rows of one day collapse
    days = {i.published.date() for i in items if i.title == "RRB CEN 08/2026: Notification"}
    assert {dt.date(2026, 10, 26), dt.date(2026, 10, 27)} <= days  # a new day is a new update
    assert {i.kind for i in items if i.title.endswith("E-Call Letters")} == {"admit_card"}
    assert {i.kind for i in items if i.title.endswith("Objection Tracker")} == {"answer_key"}
    assert not any("Mock Test" in i.title for i in items)


def test_sbi_dates_a_step_the_day_it_is_first_seen():
    today = CAPTURED["sbi"]
    first = parse(listboards.SBI)  # no record yet: only open applications, which carry their own date
    assert [(i.published.date(), i.kind) for i in first] == [(dt.date(2026, 10, 19), "recruitment")] + [(dt.date(2026, 8, 7), "recruitment")] * 2
    assert first[0].title == ("ENGAGEMENT OF SPECIALIST CADRE OFFICERS ON FIXED TERM EMPLOYMENT BASIS "
                              "(Apply Online from 19.10.2026 TO 09.11.2026) — ADVERTISEMENT NO: CRPD/SCO/2026-27/30")
    assert first[0].link.startswith("https://sbi.bank.in/documents/77530/57941334/17102026_Detail_ADV_CRPD_SCO_2026-27_30.pdf/")
    seen = {}
    listboards.SBI.parse(page("sbi"), today, dt.date(2025, 10, 21), seen=None, record=seen)
    assert len(seen) == 82 and set(seen.values()) == {""}  # the first run records the page, undated
    again = {}
    assert len(listboards.SBI.parse(page("sbi"), today + dt.timedelta(days=1), dt.date(2025, 10, 21), seen=seen, record=again)) == 3
    assert again == seen  # an unchanged page publishes nothing new
    # A card's headline changes with its next step: that headline is new, the rest keep their day.
    later = today + dt.timedelta(days=3)
    changed = page("sbi").replace("(PRELIMINARY EXAMINATION CALL LETTER IS AVAILABLE FOR DOWNLOAD)", "(PRELIMINARY EXAMINATION RESULT ANNOUNCED)")
    record = {}
    items = listboards.SBI.parse(changed, later, dt.date(2025, 10, 21), seen=dict(seen), record=record)
    new = [i for i in items if i.published.date() == later]
    assert [i.kind for i in new] == ["result"] and new[0].title.startswith("RECRUITMENT OF JUNIOR ASSOCIATES (CUSTOMER SUPPORT")
    assert len(record) == 82 and [d for d in record.values() if d] == [later.isoformat()]
    kinds = {listboards._sbi_kind(e[2]) for e in listboards._sbi_entries(page("sbi"))}
    assert {"result", "admit_card", "schedule", "amendment", "recruitment"} <= kinds
