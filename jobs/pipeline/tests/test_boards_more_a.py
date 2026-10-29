"""Boards of boards_more_a.py against their real pages of 29-10-2026, saved by the India server.

uppsc.html        uppsc.up.nic.in home page.
rbi.html          opportunities.rbi.org.in/Scripts/Vacancies.aspx (current list, Aug-Oct 2026).
rbi-results.html  opportunities.rbi.org.in/Scripts/resultsnew.aspx (current list, Aug-Oct 2026).
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_a as more
from sarkari.ssc import FormatChanged

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 29)
CUTOFF = dt.date(2025, 10, 21)
WINDOW = TODAY - dt.timedelta(days=120)  # 2026-07-01
NOW = dt.datetime(2026, 10, 29, 12, 0)
PAGES = {"https://uppsc.up.nic.in/": "uppsc", "https://opportunities.rbi.org.in/Scripts/Vacancies.aspx": "rbi",
         "https://opportunities.rbi.org.in/Scripts/resultsnew.aspx": "rbi-results"}


def get_text(url):
    return open(os.path.join(HERE, "fixtures", f"{PAGES[url]}.html"), encoding="utf-8").read()


def fetch(board, cutoff=CUTOFF, **kw):
    warnings = []
    if board.undated:
        kw.setdefault("seen", {})  # a record exists: entries not in it are new today
        kw.setdefault("record", {})
    items = board.fetch(get_text, NOW, (TODAY - cutoff).days, warnings.append, **kw)
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


@pytest.mark.parametrize("board,count,in_window", [(more.UPPSC, 17, 17), (more.RBI, 34, 34)])
def test_real_pages_give_only_the_boards_own_links(board, count, in_window):
    items = fetch(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(fetch(board, WINDOW)) == in_window
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(url(i).startswith(board.BASE + "/") for i in items)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert {i.kind for i in items} <= {"notice", "result", "schedule", "amendment", "answer_key", "admit_card", "recruitment"}
    assert all(i.published.date() <= TODAY for i in items)
    assert board in more.ALL


def test_uppsc_has_no_posting_dates_so_entries_are_dated_when_first_seen():
    assert fetch(more.UPPSC, seen=None) == []  # no record yet: nothing is dated by guess ("Visible upto" is an expiry)
    record = {}
    items = fetch(more.UPPSC, record=record)
    assert {i.published.date() for i in items} == {TODAY} and len(record) == 17
    earlier = {items[0].id: "2026-10-20"}
    again = {i.id: i for i in fetch(more.UPPSC, seen=earlier, record={})}
    assert again[items[0].id].published.date() == dt.date(2026, 10, 20)
    got = {url(i): i for i in items}
    maths = got["https://uppsc.up.nic.in/Open_PDF_DB.aspx?I4PnQ0tBagnfpuRH59X6AZU6OQK2HlGI"]
    assert maths.title == ("LIST OF PROVISIONALLY SELECTED CANDIDATES IN ADVT. NO. A-5/E-1/2025, ASST. TEACHER TRAINED "
                           "GRADUATE (M./F.) EXAM 2025, MATH")  # the full title attribute, not the cut link text
    assert maths.kind == "result" and not maths.files  # an .aspx page that serves the PDF: a link
    corr = got["https://uppsc.up.nic.in/Open_PDF_DB.aspx?I4PnQ0tBagkz0o52o77EYadVpmKbwEUn"]
    assert corr.title.startswith("CORRIGENDUM NOTICE REGARDING ADVT.NO.D-6/E-1/2025, DIRECTORATE OF UNANI") and corr.kind == "amendment"
    slip = got["https://uppsc.up.nic.in/Open_PDF_DB.aspx?I4PnQ0tBagmzNJIRVKnDR0yp8H0q1cBz"]
    assert slip.kind == "admit_card"
    dv = got["https://uppsc.up.nic.in/Open_PDF_DB.aspx?I4PnQ0tBagmzNJIRVKnDR/OjdeHPS5Dn"]
    assert dv.title.startswith("NOTICE REAGARDING DOCUMENT VERIFICATION FOR ADVT. NO. A-5/E-1/2025") and dv.kind == "notice"
    live = got["https://uppsc.up.nic.in/CandidatePages/Notifications.aspx"]
    assert live.title == ("Live advertisement: APPLY FOR ADVT.NO. A-4/E-1/2026,SAMIKSHA ADHIKARI/SAHAYAK SAMIKSHA ADHIKARI "
                          "ETC.(GEN. / SPL. RECTT.) EXAM.-2026")
    assert live.kind == "recruitment"
    assert Counter(i.kind for i in items) == {"notice": 8, "result": 6, "admit_card": 1, "amendment": 1, "recruitment": 1}
    # the Results / Press Release / Forthcoming tabs repeat What's New files under other links: not read
    assert not any("Open_PDF.aspx" in url(i) for i in items)


def test_rbi_reads_vacancies_and_results_under_their_date_rows():
    items = fetch(more.RBI)
    got = {url(i).rsplit("=", 1)[1]: i for i in items}
    final = got["5196"]  # results page, row under "Oct 28, 2026"
    assert final.link == "https://opportunities.rbi.org.in/Scripts/bs_viewcontent.aspx?Id=5196"
    assert final.title == ("Final Result of Direct Recruitment for the Post of Officers in Grade ‘B’ (Direct Recruit-DR) "
                           "(On Probation-OP) – DEPR Cadre - Panel Year 2026")
    assert (final.published.date(), final.kind) == (dt.date(2026, 10, 28), "result")
    ext = got["5194"]  # vacancies page, "Oct 26, 2026"
    assert (ext.published.date(), ext.kind) == (dt.date(2026, 10, 26), "amendment")
    assert ext.title.startswith("Extension of last date for submission of Application - Engagement of Part-time Bank’s Medical")
    pharm = got["5189"]
    assert (pharm.published.date(), pharm.kind) == (dt.date(2026, 10, 13), "recruitment")
    assert pharm.title == "Engagement of Pharmacist in the Bank on contract basis with fixed hourly remuneration, Mumbai"
    # two link rows under one date row ("Sep 16, 2026") both take it
    assert got["5173"].published.date() == got["5172"].published.date() == dt.date(2026, 9, 16)
    assert got["5171"].kind == "recruitment"  # Inviting Applications for the Post of Director, IIBM, Guwahati
    assert got["5169"].kind == "result"  # "... Central Office - Final Result"
    assert got["5179"].kind == "result"  # "Display of Roll Numbers of Provisionally Shortlisted Candidates"
    assert items[-1].published.date() == dt.date(2026, 8, 3) and items[0] is final
    # "Oct 29, 2026": the scorecard row nests a www.rbi.org.in link inside its own; the shown (inner) link is
    # off-host, so it is counted and left out
    assert "5197" not in got and not any("Scorecard" in i.title for i in items)
    assert Counter(i.kind for i in items) == {"result": 16, "recruitment": 10, "amendment": 8}


def test_rbi_broken_page_raises():
    with pytest.raises(FormatChanged):
        more.RBI.parse("<html>maintenance</html>", TODAY, CUTOFF, warn=lambda _: None)
    assert more._rbi_date("Oct 06, 2026") == dt.date(2026, 10, 6)
    with pytest.raises(ValueError):
        more._rbi_date("06/10/2026")
