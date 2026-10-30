"""Boards of boards_more_h.py against their real pages of 30-10-2026, saved by the India server (read as UTF-8,
errors replaced, as the server reads pages).

tnpsc.html         tnpsc.gov.in/home.aspx, whole.
itbp.html          recruitment.itbpolice.nic.in/statics/news, whole.
itbp-results.html  recruitment.itbpolice.nic.in/statics/results, whole.
esic.html          www.esic.gov.in/recruitments (first page of the list), whole.
nabard.html        www.nabard.org/Hindi/careers-notices1.aspx?cid=693&id=26 is 646 KB: the unchanged slice from
                   <div id="ContentPlaceHolder1_UpdatePanel1"> (line 423) to the end of the notice list (line 4518),
                   432 KB, all 129 notice blocks.
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_h as bh
from sarkari import classify
from sarkari.ssc import FormatChanged

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)
CUTOFF = dt.date(2025, 10, 21)
NOW = dt.datetime(2026, 10, 30, 12, 0)
PAGES = {"https://tnpsc.gov.in/home.aspx": "tnpsc",
         "https://recruitment.itbpolice.nic.in/statics/news": "itbp",
         "https://recruitment.itbpolice.nic.in/statics/results": "itbp-results",
         "https://www.esic.gov.in/recruitments": "esic",
         "https://www.nabard.org/Hindi/careers-notices1.aspx?cid=693&id=26": "nabard"}


def get_text(url):
    return open(os.path.join(HERE, "fixtures", f"{PAGES[url]}.html"), encoding="utf-8", errors="replace").read()


def fetch(board, cutoff=CUTOFF, seen=None):
    warnings = []
    items = board.fetch(get_text, NOW, (TODAY - cutoff).days, warnings.append, seen=seen, record={})
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


def by_url(items):
    out = {}
    for i in items:
        out.setdefault(url(i), []).append(i)
    return out


# (board, since 21-10-2025, last 120 days, today) on a first run (no record of NABARD's undated files yet)
@pytest.mark.parametrize("board,count,window,today", [
    (bh.TNPSC, 89, 89, 0), (bh.ITBP, 21, 7, 0), (bh.ESIC, 12, 12, 0), (bh.NABARD, 0, 0, 0)])
def test_real_pages_give_only_the_boards_own_links(board, count, window, today):
    items = fetch(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(fetch(board, cutoff=TODAY - dt.timedelta(days=120))) == window
    assert len(fetch(board, cutoff=TODAY)) == today
    later = fetch(board, seen={})  # with a record, undated entries not in it are new today; dated ones keep their day
    assert len(later) == (184 if board.undated else count)
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in later)
    assert all(url(i).startswith(board.BASE + "/") for i in later)
    assert all(bool(i.files) != bool(i.link) for i in later)
    assert all(i.kind in classify.KINDS for i in later)
    assert all("�" not in i.title for i in later)
    assert [i.published for i in later] == sorted((i.published for i in later), reverse=True)
    assert all(i.published.date() <= TODAY for i in later)


def test_tnpsc_whats_new():
    entries = list(bh.TNPSC.entries(get_text(bh.TNPSC.NOTICE_PAGE)))
    assert len(entries) == 89  # of 96 shown: 6 tenders and the departmental examinations' result are left out
    assert not any("TENDER" in h.upper() or "DEPARTMENTAL" in h.upper() for _, _, h in entries)
    items = fetch(bh.TNPSC)
    assert items[0].published.date() == dt.date(2026, 10, 29) and items[-1].published.date() == dt.date(2026, 8, 3)
    links = by_url(items)
    # the same headline on two days is two updates (a new certificate-verification list each time)
    cv = [i for i in links["https://tnpsc.gov.in/english/latest_results.aspx?c=4593"]
          if i.title == "COMBINED TECHNICAL SERVICES EXAMINATION (DIPLOMA/ITI LEVEL ) (Certificate Verification)"]
    assert sorted(i.published.date() for i in cv) == [dt.date(2026, 8, 11), dt.date(2026, 8, 20), dt.date(2026, 10, 22),
                                                      dt.date(2026, 10, 29)]
    assert {i.kind for i in cv} == {"result"} and all(i.link and not i.files for i in cv)
    group4 = next(i for i in items if i.title == "COMBINED CIVIL SERVICES EXAMINATION - IV (GROUP IV SERVICES) (Notification)")
    assert (group4.published.date(), group4.kind, url(group4)) == (
        dt.date(2026, 10, 6), "recruitment", "https://tnpsc.gov.in/examdashboard/web/index.aspx?key=ksojs2efre3WFDsw5")
    key = next(i for i in items if "TENTATIVE ANSWER KEYS" in i.title)
    assert (key.published.date(), key.kind, url(key)) == (dt.date(2026, 10, 7), "answer_key",
                                                          "https://tnpsc.gov.in/english/press_releases.aspx")
    memo = next(i for i in items if i.title.endswith("(Oral Test Memo)"))
    assert memo.kind == "admit_card"
    assert Counter(i.kind for i in items) == {"result": 42, "schedule": 23, "notice": 17, "recruitment": 3,
                                              "admit_card": 3, "answer_key": 1}


def test_itbp_news_and_results():
    entries = list(bh.ITBP.entries(get_text(bh.ITBP.NOTICE_PAGE) + "\n" + get_text(bh.ITBP.MORE_PAGES[0])))
    assert len(entries) == 125  # 86 news rows, 39 results rows
    items = fetch(bh.ITBP)
    links = by_url(items)
    sports = links["https://recruitment.itbpolice.nic.in/noticeboards/downloadpdf/361.pdf"][0]
    assert sports.title == ("DETAILED ADVERTISEMENT FOR RECRUITMENT OF MERITORIOUS SPORTSPERSONS TO THE POST OF CONSTABLE "
                            "(GENERAL DUTY) UNDER...")  # the site cuts long titles
    assert (sports.published.date(), sports.kind, sports.files[0][0]) == (dt.date(2026, 10, 3), "recruitment", "361.pdf")
    corr = links["https://recruitment.itbpolice.nic.in/noticeboards/downloadpdf/362.pdf"][0]
    assert (corr.published.date(), corr.kind) == (dt.date(2026, 10, 2), "amendment")
    # a row with no file links the page it is on
    nofile = links["https://recruitment.itbpolice.nic.in/statics/news"]
    assert sorted(i.published.date() for i in nofile) == [dt.date(2025, 11, 6), dt.date(2026, 6, 25), dt.date(2026, 8, 12)]
    # the results page
    short = links["https://recruitment.itbpolice.nic.in/noticeboards/downloadpdf/343.pdf"][0]
    assert (short.title, short.published.date(), short.kind) == (
        "Shortlisted Candidate of CT/GD (Sportsperson)Recruitment-2024 in ITBP", dt.date(2025, 11, 13), "result")
    assert items[-1].published.date() == dt.date(2025, 11, 3)
    assert Counter(i.kind for i in items) == {"recruitment": 7, "result": 7, "amendment": 3, "notice": 3, "admit_card": 1}


def test_esic_recruitments():
    items = fetch(bh.ESIC)
    assert len(list(bh.ESIC.entries(get_text(bh.ESIC.NOTICE_PAGE)))) == 12  # 10 rows, two with two files
    links = by_url(items)
    gulbarga = links["https://www.esic.gov.in/attachments/recruitmentfile/Declaration_of_Result_for_the_post_of_Teaching_Faculty_and_"
                     "Senior_Resident_based_on_for_Interview_held_on_27_10_2026_1793273627.pdf"][0]
    assert gulbarga.title == ("ESIC Medical College and Hospital Gulbarga, Kalaburagi: Declaration of Result for the post of "
                              "Teaching Faculty and Senior Resident based on for Interview held on 27.10.2026")
    assert (gulbarga.published.date(), gulbarga.kind) == (dt.date(2026, 10, 29), "result")
    kolkata = [i for i in items if i.title.startswith("ESI-PGIMSR, ESIC Medical College & ESIC Hospital and ODC (EZ), Joka, Kolkata: ")]
    assert sorted(i.title.split(": ", 1)[1] for i in kolkata) == ["Selection list Teaching Faculty (Broad Specialty)",
                                                                  "Selection list Teaching Faculty (Super Specialty)"]
    gwalior = links["https://www.esic.gov.in/attachments/recruitmentfile/WALK_IN_INTERVIEW_FOR_RECRUITMENT_TO_THE_POST_OF_SENIOR_"
                    "RESIDENTS_AND_SPECIALIST_ON_CONTRACT_BASIS_1793187250.pdf"][0]
    assert (gwalior.published.date(), gwalior.kind) == (dt.date(2026, 10, 28), "recruitment")
    form = next(i for i in items if i.title.endswith(": Application 07 of 2026"))
    assert form.kind == "notice"
    assert items[-1].published.date() == dt.date(2026, 10, 27)
    assert Counter(i.kind for i in items) == {"result": 7, "recruitment": 4, "notice": 1}


def test_nabard_files_are_dated_the_day_first_seen():
    page = get_text(bh.NABARD.NOTICE_PAGE)
    entries = list(bh.NABARD.entries(page))
    assert len(entries) == 209 and all(day == "" for day, _, _ in entries)
    assert not any(h.endswith("/CareerNotices/") or not h for _, h, _ in entries)  # hidden empty slots
    assert sum("ibps" in h for _, h, _ in entries) == 25  # IBPS portals: left out
    record = {}
    assert bh.NABARD.parse(page, TODAY, CUTOFF, seen=None, record=record) == []  # first run: recorded, not published
    assert len(record) == 184 and set(record.values()) == {""}
    assert bh.NABARD.parse(page, TODAY, CUTOFF, seen=record, record={}) == []
    items = fetch(bh.NABARD, seen={})
    links = by_url(items)
    rmd = links["https://www.nabard.org/Hindi/auth/writereaddata/CareerNotices/"
                "1929883319List%20of%20Candidates%20Shortlisted%20for%20Interview%20-%20RMD.pdf"][0]
    assert rmd.title.endswith(": List of Candidates Shortlisted for Interview - RMD")
    assert rmd.title.startswith("विशेषज्ञ की भर्ती (संविदा पर) – 2025 - 26 - आरएमडी")
    assert (rmd.kind, rmd.lang, rmd.published.date()) == ("result", "hi", TODAY)
    advt = links["https://www.nabard.org/Hindi/auth/writereaddata/CareerNotices/"
                 "1557815802FINAL%20%20Advtertisement%20-%2006%20Posts%20-%20SPPID%2C%20DCAS%20%26%20FD%20June%202026.pdf"][0]
    assert advt.title == "अनुबंध पर विशेषज्ञों की भर्ती - 2026-27 - एसपीपीआईडी, डीसीएएस एवं एफडी: Advtertisement - SPPID, DCAS & FD"
    assert advt.kind == "recruitment"
    # the page's own score-card link on the same host is kept
    assert len(links["https://www.nabard.org/hrmd-new/prelimresult.aspx"]) == 2
    assert Counter(i.kind for i in items) == {"result": 96, "notice": 57, "recruitment": 23, "admit_card": 7, "amendment": 1}


def test_changed_pages_raise_instead_of_publishing_nothing():
    for board, marker, other in [(bh.TNPSC, 'class="event-date"', 'class="date"'),
                                 (bh.ESIC, "<td>2026-10-", "<td>29.10.2026-"),
                                 (bh.NABARD, 'class="career_row"', 'class="row"')]:
        page = get_text(board.NOTICE_PAGE).replace(marker, other)
        with pytest.raises(FormatChanged):
            board.parse(page, TODAY, CUTOFF, warn=lambda _: None, seen={}, record={})
    page = (get_text(bh.ITBP.NOTICE_PAGE) + get_text(bh.ITBP.MORE_PAGES[0])).replace("<h4>NEWS</h4>", "<h4>LATEST</h4>")
    page = page.replace("<h4>RESULTS</h4>", "<h4>LATEST</h4>")
    with pytest.raises(FormatChanged):
        bh.ITBP.parse(page, TODAY, CUTOFF)


def test_a_bad_date_is_rejected_not_guessed():
    page = get_text(bh.TNPSC.NOTICE_PAGE).replace('title="Oct 29, 2026"', 'title="Oct 32, 2026"', 1)
    warnings = []
    items = bh.TNPSC.parse(page, TODAY, CUTOFF, warn=warnings.append)
    assert len(items) == 88 and len(warnings) == 1 and "bad date" in warnings[0]
