"""Boards of boards_more_f.py against their real pages of 30-10-2026 (tests/fixtures/<source>.html, decompressed bytes).

pnb.html     www.pnb.bank.in/Recruitments.aspx, whole.
bob.html     bankofbaroda.bank.in/career/current-opportunities is 1 MB: an unchanged slice of its bytes from `const allowedList = [`
             to the end of `var glblMasterCareerDetails = [...];` (the openings' JSON).
nhpc.html    www.nhpcindia.com/welcome/job, whole.
bpcl.html    www.bharatpetroleum.in/careers/job-openings, whole.
pgcil.html   www.powergrid.in/en/job-opportunities is 658 KB: an unchanged slice from its first card (Engineer Trainees-2027) up to
             the card "Engagement of experienced personnel ... Field Engineer/ Field Supervisor" (left out: 150 KB, 88 files) of
             the contract-posts tab; the five older contract cards after it are left out too.
canara.html  www.canarabank.bank.in/pages/Recruitment is 461 KB: the unchanged slice from <h2 class="sb-heading">Recruitment</h2>
             to the end of its list.
iob.html     www.iob.bank.in/en/Careers is 912 KB: the unchanged slice from the heading "Engagement of Apprentices 2026-27" to the
             end of the last table.
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_f as bf
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


# (board, since 21-10-2025, last 120 days, today) on a first run (no record of undated entries yet)
@pytest.mark.parametrize("board,count,window,today", [
    (bf.PNB, 61, 29, 0), (bf.BOB, 40, 19, 0), (bf.NHPC, 9, 9, 0), (bf.BPCL, 26, 16, 0), (bf.PGCIL, 21, 1, 0),
    (bf.CANARA, 0, 0, 0), (bf.IOB, 0, 0, 0)])
def test_real_pages_give_only_the_boards_own_links(board, count, window, today):
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


def test_undated_boards_with_a_record():
    assert [len(parse(b, seen={})) for b in (bf.PGCIL, bf.CANARA, bf.IOB)] == [114, 32, 165]
    assert [len(list(b.entries(page(b.SOURCE)))) for b in (bf.PGCIL, bf.CANARA, bf.IOB)] == [208, 35, 165]


def test_pnb_files_and_publish_dates():
    items = by_title(parse(bf.PNB))
    advert = items["RECRUITMENT OF 30 SPECIALIST OFFICERS UNDER HRP 2026-27: DETAILED ADVERTISEMENT (ENGLISH)"]
    assert (advert.published.date(), advert.kind, url(advert)) == (dt.date(2026, 4, 20), "recruitment", bf.PNB.NOTICE_PAGE)
    call = items["RECRUITMENT OF 30 SPECIALIST OFFICERS UNDER HRP 2026-27: CLICK HERE TO DOWNLOAD CALL LETTER FOR ONLINE WRITTEN TEST "
                 "SCHEDULED ON 27.05.2026"]
    assert (call.published.date(), call.kind) == (dt.date(2026, 5, 18), "admit_card")
    corr = items["RECRUITMENT OF 750 LOCAL BANK OFFICERS UNDER HRP 2026-27: CORRIGENDUM TO THE RECRUITMENT NOTIFICATION FOR LOCAL BANK "
                 "OFFICERS"]
    assert (corr.published.date(), corr.kind) == (dt.date(2025, 11, 24), "amendment")
    assert Counter(i.kind for i in items.values()) == {"notice": 28, "schedule": 10, "result": 8, "recruitment": 7, "admit_card": 6,
                                                       "amendment": 2}


def test_bob_general_tab_and_dated_updates():
    items = parse(bf.BOB)
    titles = by_title(items)
    cat = "Recruitment of professionals on regular basis for Corporate Accounts & Taxation Planning Department - BOB/HRM/REC/ADVT/2026/15"
    link = "https://bankofbaroda.bank.in/career/current-opportunities/recruitment-of-professionals-on-regular-basis-for-corporate-accounts-taxation-planning-26-08"
    assert (titles[cat].published.date(), titles[cat].kind, url(titles[cat])) == (dt.date(2026, 8, 6), "recruitment", link)
    update = titles[cat + ": Notification dated 26.10.2026"]
    assert (update.published.date(), update.kind, url(update)) == (dt.date(2026, 10, 26), "notice", link)
    addendum = [i for i in items if i.title.endswith("Addendum dated 06.07.2026")]
    assert [(i.published.date(), i.kind) for i in addendum] == [(dt.date(2026, 7, 6), "amendment")]
    # the "Others" tab (local contracts: financial literacy counsellors, RSETI staff, BC coordinators) is left out
    assert not any(w in i.title.upper() for i in items for w in ("FINANCIAL LITERACY", "RSETI", "BC CO", "WATCHMAN"))
    assert len(list(bf.BOB.entries(page("bob")))) == 96  # of 458 openings in the JSON


def test_nhpc_list():
    items = parse(bf.NHPC)
    first = items[0]
    assert (first.title, first.published.date(), first.kind) == ("Engagement of Consultant (Safety) on contract basis.",
                                                                 dt.date(2026, 10, 27), "recruitment")
    assert url(first) == "https://www.nhpcindia.com/assests/pzi_public/job/details_1793084987_4256.pdf"
    assert not any("Arbitrators" in i.title for i in items)  # an expression of interest, no job
    te = [i for i in items if i.title.startswith("List of provisionally selected candidates for the post of TE")]
    assert {(i.published.date(), i.kind) for i in te} == {(dt.date(2026, 8, 14), "result")} and len(te) == 3


def test_bpcl_updates():
    items = parse(bf.BPCL)
    refinery = ("Recruitment to Non-Management Posts in Kochi Refinery & Mumbai Refinery (Process Technician (Chemical), "
                "Process Operator / Operator (Chemical), Technician / Operator (Mechanical), Technician (Electrical))")
    titles = by_title(items)
    advert = titles[refinery + ": Detailed Advertisement"]
    assert (advert.published.date(), advert.kind) == (dt.date(2026, 7, 29), "recruitment")
    assert url(advert) == "https://www.bharatpetroleum.in/images/files/BPCL-KR-and-MR-NON-MGMT-RECT-NOTIFICATION-july-26.pdf"
    shortlist = titles[refinery + ": Click here to view the list of candidates shortlisted for the skill / proficiency test."]
    assert (shortlist.published.date(), shortlist.kind) == (dt.date(2026, 10, 1), "result")
    assert url(shortlist) == "https://www.bharatpetroleum.in/images/files/SHORTLIST-SKILL-TEST-KOCHI-and-MUMBAI-REFINERY.pdf"
    # the call letter sits on IBPS's portal: the item links BPCL's page, which states the update
    call = titles[refinery + ": Click here to download call letter for the skill / proficiency test."]
    assert (call.published.date(), call.kind, url(call)) == (dt.date(2026, 10, 9), "admit_card", bf.BPCL_PAGE)
    assert not any("Director" in i.title for i in items)  # board posts carry no day
    assert Counter(i.kind for i in items) == {"result": 9, "notice": 4, "admit_card": 4, "schedule": 3, "amendment": 3, "recruitment": 3}


def test_pgcil_dated_files():
    items = by_title(parse(bf.PGCIL, cutoff=dt.date(2015, 1, 1)))
    et = items["Recruitment of Engineer Trainees-2027 through GATE 2027 Advt No. CC/03/2026 dated 24.09.2026: Detailed Advertisement"]
    assert (et.published.date(), et.kind) == (dt.date(2026, 9, 24), "recruitment")
    assert url(et) == ("https://www.powergrid.in/sites/default/files/job_opportunities_document/"
                       "ET2027_Detailed_Advertisement_For%20Website_1.pdf")
    otf = "Recruitment of Officer Trainee (Finance) and Officer Trainee (CS) Advt No. CC/05/2025 dated 15.10.2025.: "
    cbt = items[otf + "Notice - 02 dtd 11-11-2025 - Applications Count and CBT Schedule"]
    assert (cbt.published.date(), cbt.kind) == (dt.date(2025, 11, 11), "schedule")
    adm = items[otf + "Notice No 04 dt 10-12-25-Admit Card"]
    assert (adm.published.date(), adm.kind) == (dt.date(2025, 12, 10), "admit_card")
    n2 = items["Recruitment of Officer Trainee – 2024 Advt No. CC/11/2024 dated 04.12.2024: Notice 2 dated 30th December 2024"]
    assert n2.published.date() == dt.date(2024, 12, 30)
    # files with no day in their title wait for a record: "Notice 1_Commencement of application window" (OT Law 2025)
    assert not any("Commencement of application window" in t for t in items)
    assert any("Commencement of application window" in i.title for i in parse(bf.PGCIL, seen={}))


def test_canara_list():
    items = parse(bf.CANARA, seen={})
    titles = by_title(items)
    new = titles["Engagement of Graduate Apprentices in Canara Bank under Apprentices Act, 1961 for FY 2026-27"]  # "[NEW]" dropped
    assert (new.published.date(), new.kind) == (TODAY, "recruitment")
    assert url(new) == ("https://www.canarabank.bank.in/engagement-of-graduate-apprentices-in-canara-bank-under-apprentices-act-1961-"
                        "for-fy-2026-27")
    dmt = titles["Engagement Of Deputy Managing Trustee For Canara Financial Advisory Trust (CFAT) On Contract Basis - Extension Of "
                 "Last Date For Submission Of Application"]
    assert (dmt.kind, url(dmt)) == ("amendment", "https://www.canarabank.bank.in/documents/d/guest/website-content-new-dmt_extension")
    assert titles["IBPS-CRP-XV-Probationary Customer Service Associates (CSAs)"].kind == "notice"
    assert not any("NEW]" in t or "Fraud" in t for t in titles)


def test_iob_tables():
    items = parse(bf.IOB, seen={})
    titles = by_title(items)
    corr = titles["Recruitment of Security Guards: Corrigendum Notification dated 02.09.2026"]
    assert (corr.kind, url(corr)) == ("amendment", "https://www.iob.bank.in/documents/d/guest/corrigendum-_recruitment-of-security-guards")
    lbo = titles["Recruitment of Local Bank Officers 2026-27"]
    assert (lbo.kind, url(lbo)) == ("recruitment", "https://www.iob.bank.in/documents/d/guest/lbo-final-ad-2026-27")
    # the call letter is on IBPS's portal: the item links IOB's careers page
    call = titles["Recruitment of Local Bank Officers 2026-27: Online Examination Call Letter Download Link"]
    assert (call.kind, url(call)) == ("admit_card", bf.IOB_PAGE)
    assert not any("Direct Selling Agent" in t for t in titles)
