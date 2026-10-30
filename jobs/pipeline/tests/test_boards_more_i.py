"""Boards of boards_more_i.py against their real pages of 30-10-2026, as the India server saved them (tests/fixtures/<source>.html).

ntpc.html   careers.ntpc.co.in/recruitment/ is 1 MB: an unchanged slice of its bytes from <h2 class="m-b5">Notice Board</h2> up to
            the <li> of the first notice posted before 2025 (2024-12-27); the 221 older notices (back to 2022) and the rest of the
            page (the "Advertisement Details" panel, scripts) are left out. Its notices are newest first throughout.
niacl.html  www.newindia.co.in/recruitment/list is 4.3 MB: its first 165 KB unchanged, from the start (with <base href="/">) to the
            end of </app-recruitment>; the 4 MB left out is the page's translation table (serverApp-state) and footer.
aiims.html  www.aiims.edu/index.php/en/notices/recruitment/aiims-recruitment, whole.
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_i as bi
from sarkari import classify

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)
CUTOFF = dt.date(2025, 10, 21)


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=CUTOFF, today=TODAY, seen=None):
    warnings = []
    items = board.parse(page(board.SOURCE), today, cutoff, warn=warnings.append, seen=seen, record={})
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


def by_title(items):
    return {i.title: i for i in items}


# (board, since 21-10-2025, last 120 days, today, entries on the page) on a first run (no record of undated entries yet)
@pytest.mark.parametrize("board,count,window,today,entries", [
    (bi.NTPC, 112, 33, 0, 169), (bi.NIACL, 0, 0, 0, 237), (bi.AIIMS, 11, 11, 0, 11)])
def test_real_pages_give_only_the_boards_own_links(board, count, window, today, entries):
    assert len(list(board.entries(page(board.SOURCE)))) == entries
    items = parse(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, cutoff=TODAY - dt.timedelta(days=120))) == window
    assert len(parse(board, cutoff=TODAY)) == today
    # with a record, undated entries not in it are new today; dated ones keep their day
    later = parse(board, seen={})
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in later)
    assert all(url(i).startswith(board.BASE + "/") for i in later)
    assert all(bool(i.files) != bool(i.link) for i in later)
    assert all(i.kind in classify.KINDS for i in later)
    assert [i.published for i in later] == sorted((i.published for i in later), reverse=True)
    assert all(i.published.date() <= TODAY for i in later)


def test_ntpc_notice_board():
    items = parse(bi.NTPC)
    titles = by_title(items)
    first = items[0]
    assert first.title.startswith("Recruitment of experienced professionals on Fixed Term Basis for Gas Engine based power project at "
                                  "Great Nicobar Island (GNI), Advt.15/26. Online application portal will remain open from 30-10-2026 "
                                  "to 13-11-2026.")
    assert (first.published.date(), first.kind, url(first)) == (dt.date(2026, 10, 29), "recruitment", bi.NTPC_PAGE)  # no file
    gdmo = titles["Result for the post of GDMO against Advt. No- 04/26"]
    assert (gdmo.published.date(), gdmo.kind) == (dt.date(2026, 9, 22), "result")
    assert url(gdmo) == "https://careers.ntpc.co.in/recruitment/"  # its file tokens change per visit: the board page is linked
    # the admit card is on an exam agency's portal: the item links NTPC's own page
    admit = [i for i in items if i.published.date() == dt.date(2026, 10, 21)]
    assert [(i.kind, url(i)) for i in admit] == [("admit_card", bi.NTPC_PAGE)]
    assert admit[0].title.startswith("The Computer Based Test (CBT) for the post of Assistant Officer (Corporate Communication) against "
                                     "Advt. 12/26 is scheduled on 01.11.2026.")
    closed = [i for i in items if i.title.startswith("Reference is made to Advt. No. 3/26")]
    assert [(i.published.date(), i.kind) for i in closed] == [(dt.date(2026, 4, 29), "amendment")]
    assert Counter(i.kind for i in items) == {"result": 47, "schedule": 29, "recruitment": 23, "admit_card": 10, "amendment": 3}
    # its file links carry per-visit tokens: a notice is known by its headline alone
    assert bi.NTPC.title_ids
    since_2025 = parse(bi.NTPC, cutoff=dt.date(2025, 1, 1))
    assert len(since_2025) == 169 and since_2025[-1].published.date() == dt.date(2025, 1, 8)  # the slice's oldest notice


def test_niacl_files_resolve_from_the_site_root_and_are_dated_when_first_seen():
    items = parse(bi.NIACL, seen={})
    assert len(items) == 236  # one call letter link is listed twice in the 2016 exercise
    assert {i.published.date() for i in items} == {TODAY}
    titles = by_title(items)
    notice = titles["RECRUITMENT OF ADMINISTRATIVE OFFICERS 2025: NOTICE - RECRUITMENT OF ADMINISTRATIVE OFFICE (GENERALISTS & "
                    "SPECIALISTS) (SCALE - I) - 2025"]
    assert url(notice) == ("https://www.newindia.co.in/assets/docs/recruitment/RECRUITMENT%20OF%20ADMINISTRATIVE%20OFFICERS%202025/"
                           "Notice%20-%20AO%20DR%20RE%202025.pdf")
    advert = titles["RECRUITMENT OF ADMINISTRATIVE OFFICERS 2025: DETAILED ADVERTISEMENT- RECRUITMENT OF 550 ADMINISTRATIVE OFFICERS "
                    "(GENERALISTS & SPECIALISTS) (SCALE-I) 2025"]
    assert advert.kind == "recruitment"
    # IBPS's call letter page is outside: the item links this page, which lists it
    call = titles["RECRUITMENT OF ADMINISTRATIVE OFFICERS 2025: CLICK HERE TO DOWNLOAD INTERVIEW CALL-LETTER"]
    assert (call.kind, url(call)) == ("admit_card", bi.NIACL_PAGE)
    marks = titles["RECRUITMENT : MARKSHEET: RECRUITMENT 2025- ADMINISTRATIVE OFFICERS (SCALE-I) GENERALISTS & SPECIALISTS"]
    assert (marks.kind, url(marks)) == (
        "result", "https://www.newindia.co.in/recruitment/mark-sheets/2025-administrative-officers-scale-1-generalists-specialists")
    interview = titles["RECRUITMENT OF ADMINISTRATIVE OFFICERS 2025: SCHEDULE OF INTERVIEW FOR PROVISIONALLY SELECTED CANDIDATES FOR "
                       "RECRUITMENT EXERCISE FOR THE POST OF ADMINISTRATIVE OFFICERS (SCALE - I) GENERALISTS & SPECIALISTS, 2025"]
    assert interview.kind == "schedule"
    # the first run only records what is on the page; those entries never come out, a new one does
    record = {}
    assert bi.NIACL.parse(page("niacl"), TODAY, CUTOFF, warn=print, seen=None, record=record) == []
    assert len(record) == 236 and set(record.values()) == {""}
    assert bi.NIACL.parse(page("niacl"), TODAY, CUTOFF, warn=print, seen=record, record={}) == []


def test_aiims_open_advertisements():
    items = parse(bi.AIIMS)
    assert {i.kind for i in items} == {"recruitment"}
    titles = by_title(items)
    dbt = next(i for i in items if i.title.endswith("Advertisement for the post of Scientific Administrative Asstt/Field work in DBT "
                                                     "funded at Dept. of Biochemistry, AIIMS"))
    assert (dbt.published.date(), url(dbt)) == (
        dt.date(2026, 10, 19), "https://www.aiims.edu/index.php/en/notices/recruitment/aiims-recruitment?id=24933")
    assert [i.published.date() for i in items[:2]] == [dt.date(2026, 10, 29)] * 2
    assert {url(i).rsplit("=", 1)[1] for i in items if i.published.date() == dt.date(2026, 10, 16)} == {"24920", "24910", "24912"}
    assert len(titles) == 11
