"""Boards of boards_more_j.py against their real pages (tests/fixtures/<source>.html), all whole and unchanged.

ossc.html   www.ossc.gov.in/Public/OSSC/Default.aspx (where www.ossc.gov.in/ redirects), read from here 30-10-2026.
opsc.html   www.opsc.gov.in/Public/OPSC/Default.aspx (where www.opsc.gov.in/ redirects), read from here 30-10-2026.
tgprb.html  www.tgprb.in/, read from here 30-10-2026: an empty page that loads /assets/index-C1RGemmW.js;
tgprb.js    that script (275 KB), whole: it holds the press-release list.
cisf.html   cisfrectt.cisf.gov.in/, the India server's capture of 30-10-2026 (captures branch, cisf/2026-10-30.html).
wbprb.html  prb.wb.gov.in/, the India server's capture of 30-10-2026 (captures branch, wb-police/2026-10-30.html).
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_j as bj
from sarkari import classify
from sarkari.ssc import FormatChanged

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)
CUTOFF = dt.date(2025, 10, 21)
FILES = {"tgprb": "tgprb.js"}  # the list is in the script the home page loads


def page(source):
    with open(os.path.join(HERE, "fixtures", FILES.get(source, f"{source}.html")), encoding="utf-8") as f:
        return f.read()


def parse(board, cutoff=CUTOFF, today=TODAY, seen=None):
    warnings = []
    items = board.parse(page(board.SOURCE), today, cutoff, warn=warnings.append, seen=seen, record={})
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


def by_title(items):
    return {i.title: i for i in items}


# (board, since 21-10-2025, last 120 days, today, entries on the page); undated boards: with an empty record (all new today)
@pytest.mark.parametrize("board,count,window,today,entries", [
    (bj.OSSC, 10, 10, 0, 10), (bj.OPSC, 10, 10, 0, 10), (bj.TGPRB, 28, 9, 0, 36), (bj.CISF, 8, 8, 8, 8), (bj.WBPRB, 3, 3, 3, 3)])
def test_real_pages_give_only_the_boards_own_links(board, count, window, today, entries):
    seen = {} if board.undated else None
    assert len(list(board.entries(page(board.SOURCE)))) == entries
    items = parse(board, seen=seen)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, cutoff=TODAY - dt.timedelta(days=120), seen=seen)) == window
    assert len(parse(board, cutoff=TODAY, seen=seen)) == today
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(url(i).startswith(board.BASE + "/") for i in items)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert all(i.published.date() <= TODAY for i in items)


def test_ossc_whats_new():
    items = parse(bj.OSSC)
    assert {url(i) for i in items} == {"https://www.ossc.gov.in/Public/OSSC/Default.aspx"}  # files open by postback only
    titles = by_title(items)
    first = items[0]
    assert first.title == ("Notice regarding Fourth (Phase-IV) certificate verification of Combined Graduate Level Recruitment "
                           "Examination-2025. Advt No.-5751/OSSC dated.12.12.2025.")
    assert (first.published.date(), first.kind) == (dt.date(2026, 10, 29), "schedule")
    admit = titles["Download the Admission Letter for Preliminary Examination of Combined Technical Services Recruitment Examination "
                   "(CTSRE)-2025. Advt No.5925/OSSC Dated: 30.12.2025"]
    assert (admit.published.date(), admit.kind) == (dt.date(2026, 10, 16), "admit_card")
    pet = titles["List of(75 nos) Candidates Provisionally shortlisted for Physical Measurement & Physical Efficiency Test for the "
                 "Post of Combined Recruitment Examination of different posts having Physical Measurement and Physical Efficiency "
                 "Test-2025 (Advertisement No. 980/OSSC dated 19.03.2026)."]
    assert (pet.published.date(), pet.kind) == (dt.date(2026, 10, 12), "result")
    assert Counter(i.kind for i in items) == {"schedule": 4, "admit_card": 3, "notice": 2, "result": 1}


def test_opsc_whats_new():
    items = parse(bj.OPSC)
    assert {url(i) for i in items} == {"https://www.opsc.gov.in/Public/OPSC/Default.aspx"}
    titles = by_title(items)
    vas = titles["Veterinary Assistant Surgeon/ Additional Veterinary Assistant Surgeon (Advt. No. 04 of 2026-27) - Programme for "
                 "Written Examination"]
    assert (vas.published.date(), vas.kind) == (dt.date(2026, 10, 29), "schedule")
    rec = titles["Assistant Professor (Stage-I) (Physics) (Advt. No. 27 of 2023-24) - Recommendation Notice"]
    assert (rec.published.date(), rec.kind) == (dt.date(2026, 10, 28), "result")
    rej = titles["Assistant Professor (Stage-I) (Chemistry) (Advt. No. 27 of 2023-24) - Rejection Notice"]
    assert rej.kind == "notice"
    assert Counter(i.kind for i in items) == {"result": 6, "notice": 3, "schedule": 1}


def test_tgprb_press_releases_from_the_script_the_home_page_loads():
    script = "https://www.tgprb.in/assets/index-C1RGemmW.js"
    with open(os.path.join(HERE, "fixtures", "tgprb.html"), encoding="utf-8") as f:
        home = f.read()
    assert bj.tgprb_script(home) == script
    pages = {bj.TGPRB_PAGE: home, script: page("tgprb")}
    now = dt.datetime(2026, 10, 30, 12, tzinfo=dt.timezone(dt.timedelta(hours=5, minutes=30)))
    fetched = bj.TGPRB.fetch(pages.__getitem__, now, 120, warn=print)
    assert [i.id for i in fetched] == [i.id for i in parse(bj.TGPRB, cutoff=TODAY - dt.timedelta(days=120))]
    with pytest.raises(FormatChanged):
        bj.tgprb_script("<html><body><div id=\"root\"></div></body></html>")

    items = parse(bj.TGPRB)
    titles = by_title(items)
    app = items[0]
    assert app.title == "APP: Press Note for APP-25 Selection dated 17 October 2026"
    assert (app.published.date(), app.kind) == (dt.date(2026, 10, 17), "result")
    assert url(app) == "https://www.tgprb.in/APP_PDF/Press%20Note%20for%20APP-25%20Selection%20dated%2017%20October%202026.pdf"
    note = titles["Police Recruitment: Notification 2026 Press Note dated 29-07-2026"]
    assert (note.published.date(), note.kind) == (dt.date(2026, 7, 29), "recruitment")
    assert url(note) == "https://www.tgprb.in/SI_PC_2026/Notification%202026%20Press%20Note%20dated%2029-07-2026.pdf"
    dates = titles["Police Recruitment: Application Dates 2026 Press Note dated 29-07-2026"]
    assert (dates.published.date(), dates.kind) == (dt.date(2026, 8, 1), "schedule")  # the list's own day
    second = titles["Police Recruitment: Press Note Second Supplementary Notification 2026 dated 10 Sep 2026"]
    assert (second.published.date(), second.kind) == (dt.date(2026, 9, 10), "amendment")
    assert Counter(i.kind for i in items) == {"notice": 11, "recruitment": 10, "result": 3, "amendment": 2, "schedule": 2}
    assert len(parse(bj.TGPRB, cutoff=dt.date(2025, 1, 1))) == 36  # the list starts 15-08-2025


def test_cisf_notice_board_is_dated_when_first_seen():
    items = parse(bj.CISF, seen={})
    assert {url(i) for i in items} == {bj.CISF_PAGE}  # its file links carry per-visit tokens
    assert {i.published.date() for i in items} == {TODAY}
    titles = by_title(items)
    assert titles["Notification for recruitment of Paramedical Staff in CISF - 2026 (English)"].kind == "recruitment"
    assert titles["Corrigendum dated 09.06.2026 related to Hindi version of Notification for Paramedical Staff in CISF - 2026"].kind \
        == "amendment"
    assert titles["Conduct of CBE for the Posts of CT (Fire), CT (TM) and CT (Driver & DCPO) -2024 (English)"].kind == "schedule"
    # the first run only records what is on the page
    record = {}
    assert bj.CISF.parse(page("cisf"), TODAY, CUTOFF, warn=print, seen=None, record=record) == []
    assert len(record) == 8 and set(record.values()) == {""}
    assert bj.CISF.parse(page("cisf"), TODAY, CUTOFF, warn=print, seen=record, record={}) == []
    # a new token for the same notice is the same notice
    assert bj.CISF.parse(page("cisf").replace("fnm=VfC3JI4", "fnm=Zz9"), TODAY, CUTOFF, warn=print, seen=record, record={}) == []


def test_wbprb_recruitment_notifications():
    items = parse(bj.WBPRB, seen={})
    titles = by_title(items)
    admit = titles["Download Admit Card for PMT & PET for Recruitment to the posts of Constables/ Lady Constables in Kolkata Police - 2024"]
    assert (admit.kind, url(admit)) == ("admit_card", "https://prb.wb.gov.in/recruitments/6e50c07d61e0194f")
    exam = titles["Notice in c/w Tentative Date of Final Written Examination for Recruitment to the posts of Constables/ Lady "
                  "Constables in Kolkata Police - 2024"]
    assert (exam.kind, url(exam)) == ("schedule", "https://prb.wb.gov.in/recruitments/6e50c07d61e0194f")
    window = titles["Notice for the Special Window to modify or update the category for Recruitment to the post of Sub-Inspector "
                    "(Unarmed & Armed Branch) in West Bengal Police - 2024"]
    assert (window.kind, url(window)) == ("amendment", "https://prb.wb.gov.in/recruitments/9b380303ea68fda3")
    record = {}
    assert bj.WBPRB.parse(page("wbprb"), TODAY, CUTOFF, warn=print, seen=None, record=record) == []
    assert len(record) == 3


def test_broken_pages_raise():
    for board in bj.ALL:
        with pytest.raises(FormatChanged):
            board.parse("<html><body>maintenance</body></html>", TODAY, CUTOFF, warn=print, seen={}, record={})
