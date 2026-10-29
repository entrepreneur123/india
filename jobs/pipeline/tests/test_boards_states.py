import datetime as dt
import os

import pytest

from sarkari import boards_states as states

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 29)  # all four pages were read from the India server on this day
CUTOFF = dt.date(2025, 10, 21)
WINDOW = TODAY - dt.timedelta(days=120)


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=CUTOFF, **kw):
    warnings = []
    if board.undated:
        kw.setdefault("seen", {})
        kw.setdefault("record", {})
    items = board.parse(page(board.SOURCE), TODAY, cutoff, warn=warnings.append, **kw)
    assert warnings == []
    return items


def _by_file(items):
    return {(i.files[0][0] if i.files else i.link.rsplit("/", 1)[1]): i for i in items}


@pytest.mark.parametrize("board,count,in_window", [(states.BPSC, 6, 3), (states.HPSC, 25, 25), (states.PPSC, 121, 37),
                                                   (states.PSSSB, 41, 41)])
def test_real_pages_give_only_the_boards_own_links(board, count, in_window):
    items = parse(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, WINDOW)) == in_window
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(u.startswith(board.BASE + "/") for i in items for u in [f[1] for f in i.files] + [i.link] if u)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert {i.kind for i in items} <= {"notice", "result", "schedule", "amendment", "answer_key", "admit_card", "recruitment"}
    assert all(i.published.date() <= TODAY for i in items)
    assert len(states.ALL) == 4 and board in states.ALL


def test_bpsc_dates_come_from_the_upload_stamp_and_repeats_collapse():
    got = _by_file(parse(states.BPSC))
    cancelled = got["Important-Notice-04-282025-Advertisement-cancelled_BPSC-20261029-wh9ep8.pdf"]
    assert (cancelled.published.date(), cancelled.kind) == (dt.date(2026, 10, 29), "amendment")
    assert cancelled.title.startswith("Important Notice:- Advertisement for the Posts of Assistant Professor")
    postponed = got["Regarding_postpone_72nd_BPSC_BPSC-20260720-u768lx.pdf"]
    assert (postponed.published.date(), postponed.kind) == (dt.date(2026, 7, 20), "amendment")
    assert postponed.files[0][1] == ("https://bpsc.bihar.gov.in/wp-content/uploads/BPSC_content/Notices/"
                                     "Regarding_postpone_72nd_BPSC_BPSC-20260720-u768lx.pdf")
    steno = got["Important-Notice-Date-of-012026-Stenographer-BPSC-Exam_BPSC-20260420-zj3eyx.pdf"]  # three times on the page
    assert (steno.published.date(), steno.exam_year) == (dt.date(2026, 4, 20), 2026)
    assert "NB-2025-06-26-02_BPSC-20250626-vmnwcx.pdf" not in got  # 26-06-2025: before the cutoff
    assert "NB-2025-06-26-02_BPSC-20250626-vmnwcx.pdf" in _by_file(parse(states.BPSC, dt.date(2025, 6, 1)))
    assert "NB-2025-06-10-01.pdf" in _by_file(parse(states.BPSC, dt.date(2025, 6, 1)))  # the older "NB-YYYY-MM-DD" name


def test_hpsc_reads_the_headlines_own_date():
    got = _by_file(parse(states.HPSC))
    first = got["Final_Result_ADA_22_10_2026.pdf"]
    assert (first.published.date(), first.kind, first.exam_year) == (dt.date(2026, 10, 22), "result", 2025)
    assert first.files[0][1] == "https://hpsc.gov.in/Portals/0/Final_Result_ADA_22_10_2026.pdf"
    assert got["HCS_MARKS.pdf"].published.date() == dt.date(2026, 10, 15)  # no date in the file name
    assert got["HCS_ALLOCATION_17_10_26.pdf"].published.date() == dt.date(2026, 10, 17)
    assert got["Result_II_ProjectOfficer_09_10_2026.pdf"].published.date() == dt.date(2026, 10, 9)  # not "result dated 08.08.2023"
    assert got["Ann_Int_Sch_HCS_06_10_2026.pdf"].kind == "schedule"
    assert got["Ann_Sch_Int_Rollwise_HCS_21_09_2026.pdf"].published.date() == dt.date(2026, 9, 21)
    absolute = '<li><a href="http://hpsc.gov.in/Portals/0/A_01_10_2026.pdf">Result dated 01.10.2026 x</a></li>'
    assert list(states._hpsc_entries(absolute)) == [("01.10.2026", "https://hpsc.gov.in/Portals/0/A_01_10_2026.pdf",
                                                     "Result dated 01.10.2026 x")]
    assert list(states._hpsc_entries('<li><a href="https://evil.example/Portals/0/A_01_10_2026.pdf">x</a></li>')) == []


def test_ppsc_announcements_dated_by_their_upload_note():
    items = parse(states.PPSC)
    first = items[0]
    assert first.link == "https://ppsc.gov.in/index.aspx?page=2805&tabid=10&tablinkid=6844"
    assert first.title == ("public notice regarding the punjab state civil services combined competitive examination "
                           "pscscce 2025 mains")
    assert (first.published.date(), first.kind) == (dt.date(2026, 10, 14), "notice")
    got = {i.link.split("page=")[1].split("&")[0]: i for i in items}
    assert (got["2802"].kind, got["2802"].published.date()) == ("result", dt.date(2026, 9, 28))  # "FINAL RESULT FOR RECRUITMENT TO ..."
    assert got["2800"].kind == "schedule"  # "TIME SLOT FOR COMPETITIVE EXAMINATION RECRUITMENT TO ..."
    assert got["2804"].kind == "amendment"  # reschedule
    assert got["2794"].published.date() == dt.date(2026, 8, 28)  # "...GOVERNMENT OF PUNJABUPLOADED ON 28-08-2026"
    assert "2761" not in got and "2760" not in got  # "NEW RECRUITMENT ... YEAR 2026": no date given
    assert not any("TENDER" in i.title.upper() or "QUOTATION" in i.title.upper() for i in items)
    assert not any("uploaded on" in i.title.lower() for i in items)


def test_psssb_has_no_dates_so_entries_are_dated_when_first_seen():
    assert parse(states.PSSSB, seen=None) == []  # no record yet: nothing is dated by guess
    record = {}
    items = parse(states.PSSSB, record=record)
    assert {i.published.date() for i in items} == {TODAY} and len(record) == 41
    earlier = {items[0].id: "2026-10-20"}
    again = {i.id: i for i in parse(states.PSSSB, seen=earlier, record={})}
    assert again[items[0].id].published.date() == dt.date(2026, 10, 20)
    got = _by_file(items)
    assert got["Advt.-No.-13-of-2026.pdf"].kind == "recruitment"
    assert got["Advt.-No.-13-of-2026.pdf"].files[0][1] == "https://sssb.punjab.gov.in/wp-content/uploads/2026/09/Advt.-No.-13-of-2026.pdf"
    assert got["Clerk-IT-11-2025-Final-Result.pdf"].kind == "result"
    assert got["Provisional-Key-Set-A.pdf"].kind == "answer_key"
    assert got["Corrigendum.pdf"].kind == "amendment"
    assert not any(i.link for i in items)  # exam agencies' portals (eservices, gndu.ac.in, ...) and empty links are left out
