"""Boards of boards_more_g.py against their real pages of 30-10-2026, saved by the India server (tests/fixtures/).

bssc.html                 bssc.bihar.gov.in/NoticeBoard.htm: only its notice table down to the last row of 2025 (bytes 3992 to
                          103290 of the 452 369-byte page, unchanged; the rest is the page head and rows of 2016-2024).
cgpsc.html                psc.cg.gov.in/Notifications.php, whole.
cgpsc-advertisement.html  psc.cg.gov.in/Advertisement.php, whole.
cgpsc-result.html         psc.cg.gov.in/Result.php, whole.
cgpsc-home.html           psc.cg.gov.in/Home_Welcome.php, whole (read for its model answers only).
hppsc.html                hppsc.hp.gov.in/HomeContent/WhatsNewPV, whole (the fragment the home page loads).
appsc.html                portal-psc.ap.gov.in/Default.aspx, whole.
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_g as bg
from sarkari import classify
from sarkari.ssc import FormatChanged

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)
NOW = dt.datetime(2026, 10, 30, 12, 0)
CUTOFF = dt.date(2025, 10, 21)
WINDOW = TODAY - dt.timedelta(days=120)  # 2026-07-02
PAGES = {bg.BSSC.NOTICE_PAGE: "bssc", bg.HPPSC.NOTICE_PAGE: "hppsc", bg.APPSC.NOTICE_PAGE: "appsc",
         "https://psc.cg.gov.in/Notifications.php": "cgpsc", "https://psc.cg.gov.in/Advertisement.php": "cgpsc-advertisement",
         "https://psc.cg.gov.in/Result.php": "cgpsc-result", "https://psc.cg.gov.in/Home_Welcome.php": "cgpsc-home"}


def page(name):
    return open(os.path.join(HERE, "fixtures", f"{name}.html"), encoding="utf-8", errors="replace").read()


def get_text(url):
    return page(PAGES[url])


def fetch(board, cutoff=CUTOFF, **kw):
    warnings = []
    kw.setdefault("seen", None)  # first run: undated entries are recorded, never published
    kw.setdefault("record", {})
    items = board.fetch(get_text, NOW, (TODAY - cutoff).days, warnings.append, **kw)
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


def by_url(items):
    return {url(i): i for i in items}


# since 2025-10-21, in the 120-day window, published today; first run (no record of undated entries yet)
@pytest.mark.parametrize("board,since,window,today", [(bg.BSSC, 49, 10, 0), (bg.CGPSC, 200, 43, 0), (bg.HPPSC, 0, 0, 0),
                                                      (bg.APPSC, 375, 163, 0)])
def test_real_pages_give_only_the_boards_own_links(board, since, window, today):
    items = fetch(board)
    assert len(items) == since and len({i.id for i in items}) == since
    assert len(fetch(board, WINDOW)) == window
    assert len(fetch(board, TODAY)) == today
    hosts = (board.BASE, *getattr(board, "also", ()))
    assert all(url(i).startswith(tuple(h + "/" for h in hosts)) for i in items)
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert all(i.published.date() <= TODAY for i in items)
    assert board in bg.ALL


@pytest.mark.parametrize("board,since,window,today", [(bg.CGPSC, 207, 50, 7), (bg.HPPSC, 10, 10, 10)])
def test_undated_entries_are_dated_the_day_first_seen(board, since, window, today):
    # a record exists and holds none of the page's undated entries: they are new today
    assert [len(fetch(board, c, seen={})) for c in (CUTOFF, WINDOW, TODAY)] == [since, window, today]


def test_bssc_rows_date_advertisement_notice():
    items = fetch(bg.BSSC)
    by = by_url(items)
    # <td ...>16-10-2026</td> <td ...>03/25</td> <td ...><img ...> <a ... href="Advertisement/4593_notice.pdf">Important Notice
    # Regarding Advt. No.- 03/25, Post- Field Assistant (Main Exam)</a><br><a ... href="Advertisement/03_25_main_advt.pdf">Click Here ...
    field = by["https://bssc.bihar.gov.in/Advertisement/4593_notice.pdf"]
    assert (field.published.date(), field.kind, field.lang, field.title) == (
        dt.date(2026, 10, 16), "notice", "en", "Important Notice Regarding Advt. No.- 03/25, Post- Field Assistant (Main Exam)")
    assert "https://bssc.bihar.gov.in/Advertisement/03_25_main_advt.pdf" not in by  # the notice's attachment, not a notice
    result = by["https://bssc.bihar.gov.in/Advertisement/3363_022022_final_result.pdf"]
    assert (result.published.date(), result.kind, result.lang) == (dt.date(2026, 8, 1), "result", "hi")
    assert result.title.startswith("विज्ञान, प्रावैधिकी एवं तकनीकी शिक्षा विभाग द्वारा प्रकाशित विज्ञापन संख्या - 02/2022")
    final = by["https://bssc.bihar.gov.in/Advertisement/0225_Final_Result.pdf"]
    assert (final.published.date(), final.kind) == (dt.date(2026, 4, 2), "result")
    key = by["https://bssc.bihar.gov.in/Advertisement/2425_02_2022_main_obj_notice.pdf"]  # "... आदर्श उत्तर सम्बन्धी ..."
    assert (key.published.date(), key.kind) == (dt.date(2026, 6, 4), "answer_key")
    # the 25-11-2025 row's notice cell has no </td> (the row ends with </tr>)
    ext = by["https://bssc.bihar.gov.in/Advertisement/4804_25-11-2025.pdf"]
    assert (ext.published.date(), ext.kind) == (dt.date(2025, 11, 25), "amendment")
    assert items[0].published.date() == dt.date(2026, 10, 27) and items[-1].published.date() == dt.date(2025, 10, 31)
    assert Counter(i.kind for i in items) == {"notice": 29, "amendment": 14, "result": 5, "answer_key": 1}


def test_bssc_leaves_out_tenders_and_application_portals():
    text = page("bssc")
    assert text.count("Tender") > 20 and "bsscsteno.cbrt.co.in" in text and "Advocate Panel" in text
    items = fetch(bg.BSSC)
    assert not any(w in i.title for i in items for w in ("Tender", "NIT ", "RFP", "Agency", "Advocate"))
    assert not any("cbrt.co.in" in url(i) or "onlinebssc" in url(i) for i in items)


def test_bssc_uneven_rows_are_read_three_cells_at_a_time():
    cell = '<td align="center" class="Uline" bgcolor="#EAFFEA" nowrap>{}</td>'
    rows = "".join(
        f"<tr>{cell.format(f'0{n}-10-2026')}{cell.format('01/25')}"
        f'<td class="Uline"><a class="wbg" href="Advertisement/{n}.pdf">Important Notice {n}</a></td></tr>' for n in range(1, 5))
    # two rows under one <tr>, and a row whose notice is text without a link
    rows += (f"<tr>{cell.format('05-10-2026')}{cell.format('01/25')}<td class=\"Uline\"><a href=\"Advertisement/5.pdf\">Notice 5</a></td>"
             f"{cell.format('06-10-2026')}{cell.format('01/25')}<td class=\"Uline\"><a href=\"Advertisement/6.pdf\">Notice 6</a></td></tr>"
             f"<tr>{cell.format('07-10-2026')}{cell.format('-')}<td class=\"Uline\">Notice 7 without a link</tr>")
    warnings = []
    items = bg.BSSC.parse(rows, TODAY, CUTOFF, warn=warnings.append)
    assert warnings == [] and len(items) == 7
    assert url(items[0]) == "https://bssc.bihar.gov.in/NoticeBoard.htm" and items[0].title == "Notice 7 without a link"
    assert [i.published.day for i in items] == [7, 6, 5, 4, 3, 2, 1]
    with pytest.raises(FormatChanged):
        bg.BSSC.parse(rows.replace('class="Uline"', 'class="x"'), TODAY, CUTOFF)


def test_cgpsc_reads_three_lists_and_the_home_pages_model_answers():
    items = fetch(bg.CGPSC)
    by = by_url(items)
    # <li><a href='PDFs/Notifications/MICRO_BIOLOGIST-2026_CAVEAT_SUCHANA (16-10-2026).pdf' target='_blank'>MICRO_BIOLOGIST-2026_CAVEAT_SUCHANA (16-10-2026)</a></li>
    caveat = by["https://psc.cg.gov.in/PDFs/Notifications/MICRO_BIOLOGIST-2026_CAVEAT_SUCHANA%20%2816-10-2026%29.pdf"]
    assert (caveat.published.date(), caveat.kind, caveat.title) == (dt.date(2026, 10, 16), "notice",
                                                                   "MICRO BIOLOGIST-2026 CAVEAT SUCHANA (16-10-2026)")
    advert = by["https://psc.cg.gov.in/PDFs/advertisement/MICRO_BIOLOGIST-2026_ADVERTISEMENT%20%2808-09-2026%29.pdf"]
    assert (advert.published.date(), advert.kind) == (dt.date(2026, 9, 8), "recruitment")
    result = by["https://psc.cg.gov.in/PDFs/Result/STATE_ENGINEERING_SERVICE_EXAM-2026_WRITTEN_EXAM_RESULT%20%2803-09-2026%29.pdf"]
    assert (result.published.date(), result.kind) == (dt.date(2026, 9, 3), "result")
    # home page, per-exam list: <a href='PDFs/model_answer/SAHAYAK_..._MODEL_ANSWER (28-09-2026).pdf' ...>[MODEL_ANSWER] SAHAYAK_...</a>
    answer = by["https://psc.cg.gov.in/PDFs/model_answer/SAHAYAK_JILA_LOK_ABHIYOJAN_ADHIKARI-2026_MODEL_ANSWER%20%2828-09-2026%29.pdf"]
    assert (answer.published.date(), answer.kind, answer.title) == (
        dt.date(2026, 9, 28), "answer_key", "SAHAYAK JILA LOK ABHIYOJAN ADHIKARI-2026 MODEL ANSWER (28-09-2026)")
    folders = Counter(url(i).split("/")[4] for i in items)
    assert folders == {"Result": 95, "Notifications": 78, "model_answer": 14, "advertisement": 13}  # What's New is not read
    assert Counter(i.kind for i in items) == {"result": 95, "notice": 38, "schedule": 23, "answer_key": 14, "admit_card": 13,
                                              "recruitment": 9, "amendment": 8}
    assert not any("ecgpsconline" in url(i) or "cgpolice" in url(i) for i in items)
    assert len(bg.CGPSC.MORE_PAGES) == 3


def test_cgpsc_undated_files_are_recorded_not_guessed():
    record = {}
    fetch(bg.CGPSC, record=record)
    assert sorted(v for v in record.values()) == [""] * 7  # e.g. "COURT_MANAGER_EXAM-2025_PRELIMS_FEE_REFUND_NOTIFICATION.pdf"
    again = {}
    assert len(fetch(bg.CGPSC, seen=record, record=again)) == 200 and again == record


def test_hppsc_has_no_dates_so_entries_are_dated_when_first_seen():
    record = {}
    assert fetch(bg.HPPSC, record=record) == [] and len(record) == 10 and set(record.values()) == {""}
    assert fetch(bg.HPPSC, seen=record, record={}) == []
    items = fetch(bg.HPPSC, seen={})
    by = {i.title: i for i in items}
    # <a ... data-linktypecd="F" target="_blank" href=/CommonControls/ViewCMSFile?qs=KI3g...%3D>Advertisement Number : 66/10-2026 ...
    advert = by["Advertisement Number : 66/10-2026 (Assistant Director) in the Department of Home, H.P"]
    assert advert.kind == "recruitment" and advert.published.date() == TODAY and advert.link.startswith(
        "https://hppsc.hp.gov.in/CommonControls/ViewCMSFile?qs=KI3gZ53zz1wSGo29lXLTBfw1DHezN8KfEg%2BrikZhi%2FLLqiZME0%2BQRfdkGlH8WWht8SZ1ey5nxWRbIQpWQ15HVgWs0PtJUPX3fNEqq5gRepw%3D")
    # type "H" links are absolute: https://hppsc.hp.gov.in/CommonControls/CMSFileView?qs=...
    calls = next(i for i in items if i.title.startswith("Press Note:- Regarding e-call letters"))
    assert calls.kind == "admit_card" and calls.link.startswith("https://hppsc.hp.gov.in/CommonControls/CMSFileView?qs=oeMK915q2Hkd")
    assert Counter(i.kind for i in items) == {"recruitment": 4, "schedule": 2, "amendment": 2, "admit_card": 1, "result": 1}
    assert bg.HPPSC.title_ids  # the qs values are encrypted ids: an entry is known by its headline


def test_appsc_announcements_and_the_second_host():
    items = fetch(bg.APPSC)
    by = by_url(items)
    # ATTENTION: Web note on Errata for Forest Section Officer (17/2026) and Forest Beat Officer (18/2026) - (Published on 23/10/2026) -
    #   <a href="https://psc.ap.gov.in/Documents/NotificationDocuments/Errata_FSO_FBO.pdf" target="_blank"> ... Click Here ...
    errata = by["https://psc.ap.gov.in/Documents/NotificationDocuments/Errata_FSO_FBO.pdf"]
    assert (errata.published.date(), errata.kind, errata.title) == (
        dt.date(2026, 10, 23), "amendment",
        "Web note on Errata for Forest Section Officer (17/2026) and Forest Beat Officer (18/2026) - (Published on 23/10/2026)")
    web = by["https://psc.ap.gov.in/Documents/NotificationDocuments/Webnote_172023_27102026.pdf"]
    assert (web.published.date(), web.kind) == (dt.date(2026, 10, 27), "result")
    beat = [i for i in items if i.title.startswith("Detailed Notification to the Post of Forest Beat Officer")]
    assert [(i.published.date(), i.kind, i.link) for i in beat] == [
        (dt.date(2026, 10, 16), "recruitment", "https://portal-psc.ap.gov.in/HomePages/RecruitmentNotifications.aspx")]
    # "... vide Notification No.24/2025 - (Published on 25/06/2026 -": the bracket is never closed
    warden = [i for i in items if "(Published on 25/06/2026" in i.title and i.title.startswith("Marks for Selected Candidates")]
    assert [(i.published.date(), i.kind) for i in warden] == [(dt.date(2026, 6, 25), "result")]
    assert Counter(url(i).split("/")[2] for i in items) == {"psc.ap.gov.in": 201, "portal-psc.ap.gov.in": 174}
    assert items[0].published.date() == dt.date(2026, 10, 27)


def test_appsc_leaves_out_staff_tests_and_other_sites():
    text = page("appsc")
    assert "Departmental Tests" in text and "Half Yearly" in text and "applications-psc.ap.gov.in" in text
    items = fetch(bg.APPSC)
    assert not any(w in i.title for i in items for w in ("Departmental", "Half Yearly", "Half-Yearly", "RTI Act", "RIMC",
                                                       "April-2026 Session", "Appointment by Promotion"))
    assert not any("applications-psc" in url(i) or "javascript" in url(i) for i in items)
    cpt = [i for i in items if "CPT" in i.title or "Computer Proficiency" in i.title]
    assert cpt and all("post" in i.title.lower() for i in cpt)  # a CPT for a recruited post stays
