"""Boards of boards_more_k.py against their real pages (tests/fixtures/<source>.html), all whole and unchanged: the India server's
captures of 30-10-2026.

wbpsc.html    psc.wb.gov.in/ (WBPSC's new host; wbpsc.gov.in timed out).
upessc.html   upessc.up.gov.in/
apslprb.html  slprb.ap.gov.in/
gprb.html     gprb.gujarat.gov.in/
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_k as bk
from sarkari import classify

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)
CUTOFF = dt.date(2025, 10, 21)


def page(source):
    with open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8") as f:
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


# (board, since 21-10-2025, last 120 days, today, entries on the page, since 21-10-2025 on the first run);
# undated boards: with an empty record (all undated entries new today), and with no record (the first run: undated left out)
@pytest.mark.parametrize("board,count,window,today,entries,first", [
    (bk.WBPSC, 150, 33, 3, 151, 147), (bk.UPESSC, 3, 3, 0, 3, 3), (bk.APSLPRB, 41, 38, 36, 61, 5), (bk.GPRB, 37, 20, 4, 38, 33)])
def test_real_pages_give_only_the_boards_own_links(board, count, window, today, entries, first):
    seen = {} if board.undated else None
    assert len(list(board.entries(page(board.SOURCE)))) == entries
    items = parse(board, seen=seen)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, cutoff=TODAY - dt.timedelta(days=120), seen=seen)) == window
    assert len(parse(board, cutoff=TODAY, seen=seen)) == today
    assert len(parse(board)) == first
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(url(i).startswith(board.BASE + "/") for i in items)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert all(i.published.date() <= TODAY for i in items)


def test_wbpsc_whats_new_dated_by_the_files_names():
    items = parse(bk.WBPSC)  # no record: the three files without a day in their names are left out
    titles = by_title(items)
    clerk = items[0]
    assert clerk.title == "IMPORTANT ANNOUNCEMENT REGARDING EXTENSION OF CLOSING DATE FOR THE POST OF CLERKSHIP EXAMINATION, 2024 [ADVT. NO. 12/2024]"
    assert (clerk.published.date(), clerk.kind) == (dt.date(2026, 10, 25), "amendment")
    # "&param2" is kept as written, not read as the entity "&para"
    assert url(clerk) == ("https://psc.wb.gov.in/Download?param1=An_20261025234341_Important_Announcement_extension_Clerkship.pdf"
                          "&param2=advertisement")
    survey = titles["DETAILED ADVERTISEMENT TO THE POST OF SUB-SURVEYOR OF THE DIRECTORATE OF LAND RECORDS & SURVEYS AND DEPARTMENT OF "
                    "LAND & LAND REFORMS AND REFUGEE RELIEF & REHABILITATION, WB [ADVT. NO. 22/2024]"]
    assert (survey.published.date(), survey.kind, survey.exam_year) == (dt.date(2026, 10, 20), "recruitment", 2024)
    judge = titles["INDICATIVE ADVERTISEMENT FOR RECRUITMENT TO THE POST OF CIVIL JUDGE (JR. DIVN.) IN WEST BENGAL JUDICIAL SERVICE "
                   "EXAMINATION, 2025. ADVT. NO. 02/2025"]
    assert (judge.published.date(), judge.kind) == (dt.date(2025, 11, 22), "recruitment")
    assert url(judge) == "https://psc.wb.gov.in/Download?param1=An_20251122123405_Indicative_advt_judicial_2025.pdf&param2=advertisement"
    first = items[-1]
    assert first.title.startswith("IMPORTANT ANNOUNCEMENT REGARDING ASSISTANT ENGINEER (ELECTRICAL)")
    assert first.published.date() == dt.date(2025, 10, 31)
    # serving officers' departmental examinations and purchase notices are not recruitment news
    assert not [i for i in items if "HALF" in i.title or "QUOTATION" in i.title or "PROFESSIONAL EXAMINATION" in i.title]
    assert Counter(i.kind for i in items) == {"notice": 45, "result": 45, "recruitment": 26, "schedule": 14, "amendment": 8,
                                              "answer_key": 7, "admit_card": 2}


def test_wbpsc_files_without_a_day_are_dated_when_first_seen():
    record = {}
    first = bk.WBPSC.parse(page("wbpsc"), TODAY, CUTOFF, warn=print, seen=None, record=record)
    assert sorted(v for v in record.values()) == ["", "", ""]
    later = bk.WBPSC.parse(page("wbpsc"), TODAY + dt.timedelta(days=1), CUTOFF, warn=print, seen=record, record={})
    assert [i.id for i in later] == [i.id for i in first]  # still never published
    new = {i.title: i for i in parse(bk.WBPSC, seen={})}
    clerkship = new["DETAILED ADVERTISEMENT TO THE POST OF CLERKSHIP EXAMINATION, 2024 Advt. No. 12/2024"]
    assert (clerkship.published.date(), clerkship.kind) == (TODAY, "recruitment")
    assert url(clerkship) == "https://psc.wb.gov.in/Download?param1=CLERKSHIP2024.pdf&param2=advertisement"


def test_upessc_press_release():
    items = parse(bk.UPESSC)
    assert {i.published.date() for i in items} == {dt.date(2026, 9, 16)}
    titles = by_title(items)
    advt = titles["विज्ञापन संख्या- 05/2026 सहायक अध्यापक सम्बद्ध प्राइमरी (माध्यमिक शिक्षा विभाग) तथा सहायक अध्यापक, शहरी (URBAN) "
                  "(बेसिक शिक्षा विभाग) आवेदन फॉर्म की (विस्तृत विज्ञापन)"]
    assert (advt.kind, advt.lang) == ("recruitment", "hi")
    assert url(advt) == "https://upessc.up.gov.in/Notice/4f85-4bbd-4ae9-d8e5-869d.pdf"
    tet = titles["वि० सं० 02/2026 UPTET (शिक्षक पात्रता परीक्षा) के घोषित परिणाम के संबंध में प्राप्त प्रत्यावेदनों पर आयोग द्वारा लिये "
                 "गये निर्णय के संबंध में (विज्ञप्ति)"]
    assert tet.kind == "notice"
    assert url(tet) == "https://upessc.up.gov.in/Notice/945b-376d-4700-9db7-8225.pdf"
    assert Counter(i.kind for i in items) == {"notice": 2, "recruitment": 1}


def test_apslprb_days_only_from_the_headlines():
    items = parse(bk.APSLPRB)  # no record: only headlines that state their day
    assert [(i.published.date(), i.kind) for i in items] == [
        (dt.date(2026, 9, 17), "recruitment"), (dt.date(2026, 9, 16), "recruitment"), (dt.date(2025, 12, 23), "result"),
        (dt.date(2025, 12, 15), "schedule"), (dt.date(2025, 12, 3), "result")]
    sct = items[1]
    assert sct.title == ("Press Note dtd 16.09.2026 regarding Recruitment for SCT SIs,Scientific Assistants, PCs, Firemen & Warders – "
                         "Notification Issued")
    assert url(sct) == "https://slprb.ap.gov.in/2026_PDFS/PressNote_dated_16092026.pdf"
    assert url(items[3]) == "https://slprb.ap.gov.in/APPPDFS/APP%20Interview%20Press%20Note.PDF"

    new = by_title(parse(bk.APSLPRB, seen={}))
    pc = new["SCT PC (CIVIL) (MEN & WOMEN),PC (AR) (MEN & WOMEN),FIREMEN IN APSDRF,WARDER (MEN & WOMEN) IN PRISONS AND CORRECTIONAL "
             "DEPARTMENT"]
    assert (pc.published.date(), pc.kind, url(pc)) == (TODAY, "recruitment", "https://slprb.ap.gov.in/UI/Recruitments.aspx")
    # "on 25-07-2025" names the day of the test, not of the notice: dated when first seen
    call = new['Download of "CALL LETTER" for women candidates For Attending the PMT/PET on 25-07-2025']
    assert (call.published.date(), call.kind) == (TODAY, "admit_card")
    old = by_title(parse(bk.APSLPRB, cutoff=dt.date(2023, 1, 1)))
    amend = old["Amendment dtd- 30-07-2025 to the Notification Rc. No. 161-SLPRB-Rect.2-2022, dtd- 28.11.2022"]
    assert (amend.published.date(), amend.kind) == (dt.date(2025, 7, 30), "amendment")
    assert not [i for i in new.values() if "slprbpc" in url(i)]  # the application portal is another host


def test_gprb_years_from_the_lists_order():
    entries = list(bk.GPRB.entries(page("gprb")))
    assert [e[0] for e in entries[:2]] == ["22 Oct", "20 Oct"]
    dated = list(bk._with_years(entries, TODAY))
    assert [d for d, _, _ in dated[28:34]] == ["09-01-2026", "31-12-2025", "03-12-2025", "03-12-2025", "02-12-2025", "06-08-2025"]
    assert [d for d, _, _ in dated[34:]] == ["", "", "", ""]  # after 06 Aug comes 21 Aug: the order is lost, so are the years

    items = parse(bk.GPRB)
    first = items[0]
    assert first.title.startswith("Police Technical Cadre (Wireless and Motor Transport)ની દસ્તાવેજ ચકાસણી")
    assert (first.published.date(), first.kind) == (dt.date(2026, 10, 22), "notice")
    assert url(first) == "https://gprb.gujarat.gov.in/ViewDocument.aspx?CDID=7fa83d1acb15268fdc748596a7217443"
    titles = by_title(items)
    key = titles["જાહેરાત ક્રમાંકઃ GPRB/202526/3 & GPRB/202526/4 અન્વયે તા.૨૩.૦૮.૨૦૨૬ના રોજ લેવામાં આવેલ પોલીસ સબ ઇન્સ્પેકટર (મોટર "
                 "ટ્રાન્સ્પોર્ટ) તથા હેડ કોન્સ્ટેબલ ડ્રાયવર મિકેનિક (ગ્રેડ-૧)ની લેખિત પરીક્ષાની Final Answer Keyની વિગતો જોવા માટે અહીં ક્લીક કરો..."]
    assert (key.published.date(), key.kind) == (dt.date(2026, 10, 9), "answer_key")
    advt = titles["જાહેરાત ક્રમાંકઃ GPRB/202526/1 અન્વયે પો.સ.ઇ. કેડર અને લોકરક્ષક કેડરની ભરતી અંગે વિગતવારની તમામ સુચનાઓ (જાહેરાત) "
                  "જોવા માટે અહી કલીક કરો..."]
    assert (advt.published.date(), advt.kind) == (dt.date(2025, 12, 3), "recruitment")
    assert url(advt) == "https://gprb.gujarat.gov.in/ViewDocument.aspx?CDID=07ac4c5df1362e54b13260bfc1b36951"
    assert Counter(i.kind for i in items) == {"result": 12, "notice": 10, "answer_key": 6, "recruitment": 5}
    # seen on 2 January: 22 Oct is last year's
    assert bk._with_years([("22 Oct", "", ""), ("31 Dec", "", "")], dt.date(2027, 1, 2)).__next__()[0] == "22-10-2026"
