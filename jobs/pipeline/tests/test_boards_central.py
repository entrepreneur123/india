"""Boards of boards_central.py against their real pages of 29-10-2026 (tests/fixtures/<source>.html).

ibps.html  www.ibps.in home page, captured from the India server.
aai.html   www.aai.aero/en/careers/recruitment, page 1 of the exam table (sorted by last update).
iocl.html  iocl.com/latest-job-opening is 1.2 MB: the fixture is its first 39 cards, an unchanged slice
           of the page's bytes from '<section class="content-section"' up to the 40th card.
becil.html www.becil.com/vacancies, page 1 (10 newest cards).
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_central as bc
from sarkari import ssc

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 29)


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=dt.date(2025, 10, 21), today=TODAY, seen=None):
    warnings = []
    items = board.parse(page(board.SOURCE), today, cutoff, warn=warnings.append, seen=seen, record={})
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


@pytest.mark.parametrize("board,count,window", [(bc.IBPS, 2, 2), (bc.AAI, 69, 35), (bc.IOCL, 7, 7), (bc.BECIL, 10, 10)])
def test_real_pages_give_only_the_boards_own_links(board, count, window):
    items = parse(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, cutoff=TODAY - dt.timedelta(days=120))) == window
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(url(i).startswith(board.BASE + "/") for i in items)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert all(i.published.date() <= TODAY for i in items)


def test_ibps_home_page_cards():
    items = parse(bc.IBPS)
    ad, scores = items
    assert ad.title == ("IBPS Advertisement: Advertisement for Direct Recruitment of Server Administrator, "
                        "Assistant & Programming Assistant on Regular Basis")
    assert (ad.published.date(), ad.kind) == (dt.date(2026, 10, 29), "recruitment")
    assert url(ad) == ("https://www.ibps.in/wp-content/uploads/Advertisement-for-Server-Administrator-Programming-"
                       "Assistant-and-Assistant-Oct._Nov.-2026.pdf")
    assert scores.title == "CRP-SPL-XVI: Scores of Online Preliminary Examination for CRP-SPL-XVI"
    assert (scores.published.date(), scores.kind) == (dt.date(2026, 10, 22), "result")
    assert scores.link == "https://www.ibps.in/index.php/specialist-officers-xvi/"
    # the other two cards link the registration portal (ibpsreg.ibps.in): counted, left out
    assert len(list(bc.IBPS.entries(page("ibps")))) == 4
    assert not any("ibpsreg" in url(i) for i in items)


def test_aai_one_update_per_filled_cell():
    items = parse(bc.AAI)
    by = {(url(i), i.published.date()): i for i in items}
    press = by["https://www.aai.aero/en/recruitment/press-note/748316", dt.date(2026, 10, 29)]
    assert press.title == ("Press note: ENGAGEMENT OF MEDICAL CONSULTANT (NON-SPECIALIST DOCTOR) ON CONTRACT BASIS AT "
                           "AIRPORTS AUTHORITY OF INDIA, REGIONAL HEAD QUARTERS, SOUTHERN REGION, CHENNAI")
    assert press.kind == "notice" and items[0] is press
    result = by["https://www.aai.aero/en/recruitment/result/559022", dt.date(2026, 10, 19)]
    assert result.kind == "result" and result.title.startswith("Result: Recruitment Notification (Advt. 01/2025/NR)")
    # an advertisement is dated by the row's Job Post Date (27-10-2026), not by later edits
    member = by["https://www.aai.aero/en/recruitment/release/756700", dt.date(2026, 10, 27)]
    assert member.kind == "recruitment" and member.title.startswith("Advertisement: Selection for The Post of Member (Operations)")
    sscos = [i for i in items if url(i).endswith("/release/744118")]
    assert [i.published.date() for i in sscos] == [dt.date(2026, 9, 16)]  # Job Post Date; the cell says "Updated On 21-10-2026"
    assert next(i for i in items if url(i).endswith("/release/741728")).kind == "notice"  # empanelment of arbitrators
    assert Counter(i.kind for i in parse(bc.AAI, cutoff=TODAY - dt.timedelta(days=120))) == {"recruitment": 21, "notice": 7, "result": 7}


def test_aai_a_new_day_on_the_same_page_is_a_new_update():
    row = ('<tr class="odd"><td class="views-field views-field-title" >X exam</td>'
           '<td class="views-field views-field-field-job-post-date" ><span>01-10-2026</span></td>'
           '<td><a href="https://www.aai.aero/en/recruitment/press-note/1" target="_blank"><div>Updated On <span>{}</span></div></a></td></tr>')
    a, b = (bc.AAI.parse_entry(*next(bc._aai_entries(row.format(d))), TODAY) for d in ("20-10-2026", "28-10-2026"))
    assert a.link == b.link and a.id != b.id


def test_iocl_dated_links_and_first_seen_files():
    items = parse(bc.IOCL)  # no record yet: only links whose text carries a day
    first = items[0]
    assert first.title == ("Recruitment of Experienced Professionals (Poly Butadiene Rubber) in IndianOil: "
                           "Click Here to Read the Detailed Advertisement - [03/09/2026]")
    assert first.published.date() == dt.date(2026, 9, 3) and first.kind == "recruitment"
    assert url(first) == "https://iocl.com/admin/img/UploadedFiles/LatestJobOpening/Files/1d167fc3d4b14cb394ac728aff0d32e9.pdf"
    ad = next(i for i in items if url(i).endswith("/DetailedAd14082026.pdf"))
    assert ad.published.date() == dt.date(2026, 8, 14)
    assert ad.title.startswith("Recruitment of Executives in Indian Oil Corporation Limited through Computer Based Test (CBT) - 2026: ")
    assert not any("ioclapply" in url(i) or "ibps" in url(i) for i in items)  # scorecards on the exam agency's portal

    seen = parse(bc.IOCL, seen={})  # a record exists: files not in it are new today
    assert len(seen) == 90
    new = [i for i in seen if i.published.date() == TODAY]
    assert len(new) == 83
    corr = next(i for i in new if url(i).endswith("/62bf6ab4ec6d4c4685cb3f05d02aa702.pdf"))
    assert (corr.title, corr.kind) == ("EOI for Hiring of Two Nos. LA Staff for Panipat-Rewari OFC Laying Work.: Corrigendum", "amendment")
    assert not any(bc._IOCL_FORMS.search(i.title) for i in seen)  # caste certificate formats and the like
    shortlist = next(i for i in new if url(i).endswith("/498180e48e714ca2ab52133edb438eff.pdf"))
    assert shortlist.kind == "result"


def test_becil_cards():
    items = parse(bc.BECIL)
    first = items[0]
    assert first.title == ("Advt No. 563: Applications are invited from highly experienced and qualified Contract "
                           "[Retainer-ship) basis, as Advisor.")
    assert (first.published.date(), first.kind) == (dt.date(2026, 10, 19), "recruitment")
    assert url(first) == "https://www.becil.com/uploads/topics/17924098715995.pdf"
    corr = next(i for i in items if i.title == "Corrigendum for Vacancy Advt No. 557")
    assert (corr.published.date(), corr.kind, url(corr)) == (dt.date(2026, 9, 18), "amendment",
                                                             "https://www.becil.com/uploads/topics/17907679861135.pdf")
    assert Counter(i.kind for i in items) == {"recruitment": 7, "result": 2, "amendment": 1}


def test_dates():
    assert bc._day_mon_year("22 Oct, 2026") == dt.date(2026, 10, 22)
    assert bc._day_mon_year("19-Oct-2026") == dt.date(2026, 10, 19)
    with pytest.raises(ValueError):
        bc._day_mon_year("2026-10-19")


@pytest.mark.parametrize("board", bc.ALL)
def test_a_changed_page_stops_instead_of_guessing(board):
    with pytest.raises(ssc.FormatChanged):
        board.parse("<html>maintenance</html>", TODAY, dt.date(2026, 1, 1))
