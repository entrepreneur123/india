import datetime as dt
import os

import pytest

from sarkari import boards_more_b as mb
from sarkari import classify

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 29)  # every page was read from the India server on this day
CUTOFF = dt.date(2025, 10, 21)
WINDOW = TODAY - dt.timedelta(days=120)  # 2026-07-01


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=CUTOFF, **kw):
    warnings = []
    items = board.parse(page(board.SOURCE), TODAY, cutoff, warn=warnings.append, **kw)
    assert warnings == []
    return items


def _by_url(items):
    return {(i.files[0][1] if i.files else i.link): i for i in items}


def _own(board, url):
    return any(url.startswith(base + "/") for base in (board.BASE,) + getattr(board, "also", ()))


# KPSC's first run: its pages without an upload stamp are recorded, not published (seen=None).
@pytest.mark.parametrize("board,count,in_window", [(mb.GPSC, 54, 34), (mb.UKPSC, 22, 22), (mb.UKSSSC, 19, 19),
                                                   (mb.OSSSC, 10, 10), (mb.KPSC, 4, 4), (mb.TNTRB, 4, 4)])
def test_real_pages_give_only_the_boards_own_links(board, count, in_window):
    kw = {"seen": None, "record": {}} if board.undated else {}
    items = parse(board, **kw)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, WINDOW, **kw)) == in_window
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(_own(board, u) for i in items for u in [f[1] for f in i.files] + [i.link] if u)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert max(i.published.date() for i in items) <= TODAY
    assert len(mb.ALL) == 6 and board in mb.ALL


def test_gpsc_news_dated_by_its_time_tag_and_linking_the_attachment():
    items = parse(mb.GPSC)
    got = _by_url(items)
    first = got["https://gpsc.gujarat.gov.in/Documents/AdvertismentDocument/LECI-10-202526c.pdf"]
    assert first.title == ("Eligibility List (Interview) - 10/2025-26 (Assistant Engineer (Electrical), Class-2, "
                           "Road and Building Department)")
    assert (first.published.date(), first.kind) == (TODAY, "result")
    call = got["https://gpsc.gujarat.gov.in/Documents/AdvertismentDocument/INIC-11-82-202425.pdf"]
    assert call.kind == "admit_card"  # "Interview Schedule - ... Important Notice To Download Interview Call letter"
    keys = [i for i in items if i.files and i.files[0][0] == "FAK-110-128-25-2-2-2026-27.pdf"]
    assert len(keys) == 3 and {i.kind for i in keys} == {"answer_key"}  # one final key for three advertisements
    assert {i.published.date() for i in keys} == {dt.date(2026, 10, 28)}
    final = got["https://gpsc.gujarat.gov.in/Documents/AdvertismentDocument/FR-106-202526.pdf"]
    assert (final.kind, final.title) == ("result", "Final Stage - 106/2025-26 (Chief Scientific Officer, Class-1 (GWSSB))")
    assert got["https://gpsc.gujarat.gov.in/Documents/AdvertismentDocument/DIP-113-115-202526.pdf"].kind == "schedule"
    assert got["https://gpsc.gujarat.gov.in/Documents/AdvertismentDocument/NA-30012026F.pdf"].kind == "recruitment"
    assert got["https://gpsc.gujarat.gov.in/Documents/AdvertismentDocument/INCD-110326.pdf"].kind == "notice"
    assert min(i.published.date() for i in items) == dt.date(2025, 11, 7)
    # 02-05-2025: the one entry without an attachment links its news page
    older = _by_url(parse(mb.GPSC, dt.date(2025, 5, 1)))
    assert older["https://gpsc.gujarat.gov.in/newseventdetail?NewsID=24776"].published.date() == dt.date(2025, 5, 2)
    assert len(parse(mb.GPSC, dt.date(2015, 1, 1))) == 68


def test_ukpsc_announcements_keep_their_section_and_leave_out_tenders():
    items = parse(mb.UKPSC)
    first = items[0]
    assert first.link == "https://psc.uk.gov.in/candidate-corner/recruitment"
    assert first.published.date() == TODAY
    assert first.title == ("Review Officer/Assistant Review Officer in Advocate General’s Office, Nainital Examination 2024-- "
                           "Notification, Disqualified Candidates List and Candidature Cancelled List (Recruitment)")
    by_title = {i.title.split("--")[0]: i for i in items}
    pa = by_title["Personal Assistant (Establishment, Hon. High Court) Exam-2025"]
    assert (pa.published.date(), pa.kind, pa.link) == (dt.date(2026, 10, 27), "result",
                                                       "https://psc.uk.gov.in/candidate-corner/results")
    aps = [i for i in items if i.title.startswith("Additional Private Secretary Exam-2024 -- Notification, Selection Result")]
    assert len(aps) == 1 and aps[0].kind == "result"  # "... Final Answer Key ..." under Results
    geo = [i for i in items if i.title.startswith("Directorate of Geology and Mining")]
    assert len(geo) == 1 and (geo[0].kind, geo[0].published.date()) == ("recruitment", dt.date(2026, 10, 7))
    assert not any("Tenders" in i.title or "Quotation" in i.title for i in items)
    assert not any("Link for Download Online Application" in i.title for i in items)  # on pscuk.net.in


def test_uksssc_lists_dated_and_on_the_boards_cdn_folder():
    items = parse(mb.UKSSSC)
    got = _by_url(items)
    cdn = mb.UKSSSC_FILES + "uploads/"
    key = got[cdn + "2026/10/202610231635461748.pdf"]
    assert (key.published.date(), key.kind, key.lang) == (dt.date(2026, 10, 23), "answer_key", "hi")
    assert key.title == "पदनाम- सहायक कृषि अधिकारी वर्ग(1&3) व चारा सहायक की उत्तरकुंजी(Answer Keys) हेतु क्लिक करें"
    admit = got[cdn + "2026/10/202610251345667448.pdf"]
    assert (admit.published.date(), admit.kind) == (dt.date(2026, 10, 25), "admit_card")
    assert got[cdn + "2026/10/202610182051341189.pdf"].kind == "amendment"  # written exam postponed (स्थगन)
    assert got[cdn + "2026/10/202610141145817454.pdf"].kind == "result"  # चयन संस्तुति की सूची
    assert got[cdn + "2026/10/202610052099111360.pdf"].kind == "recruitment"  # सीधी भर्ती के विज्ञापन
    assert all(u.startswith(cdn) for u in got)  # preference.ssscuk.com is left out
    other = ('<div class="gen-list x"><h2>T</h2><ul><li><a download href="https://cdnbbsr.s3waas.gov.in/s3other/uploads/'
             '2026/10/1.pdf">x</a> <span class="date">25 Oct, 2026</span></li></ul>')
    assert list(mb._uksssc_entries(other)) == []  # another site's folder on the shared CDN


def test_osssc_grid_dated_by_its_calendar_linking_the_home_page():
    items = parse(mb.OSSSC)
    assert {i.link for i in items} == {"https://www.osssc.gov.in/"}
    first = items[0]
    assert first.title == ("Notification No. IIE-96/2026-3183/OSSSC dated - 27.10.2026 ----- Physical Test (Phase-II) for the "
                           "posts of Forest Guard & Excise Constable under Combined Recruitment Examination-2025(II).")
    assert (first.published.date(), first.kind) == (dt.date(2026, 10, 27), "schedule")
    by_no = {i.title.split(" dated")[0].split(" DATED")[0]: i for i in items}
    assert by_no["Notification No. IIE-63/2026-3116/OSSSC"].kind == "answer_key"
    assert by_no["Notification No. IIE-68/2026-316/OSSSC"].kind == "result"  # provisional screening list
    assert by_no["Notification No. IIE-60/2026-2918/OSSSC"].kind == "amendment"  # extension of dateline
    admission = by_no["Notification No. IIE-96/2026-3004/OSSSC"]
    assert (admission.kind, admission.published.date()) == ("admit_card", dt.date(2026, 10, 7))  # posted 07-10, dated 01.10


def test_kpsc_dated_by_upload_stamp_others_when_first_seen():
    record = {}
    items = parse(mb.KPSC, seen=None, record=record)
    got = _by_url(items)
    up = "https://kpsc.karnataka.gov.in/uploads/"
    assert set(got) == {up + "DE2026VivaPressNote_1792752290.pdf", up + "DE26vivavoce_1792669808.pdf",
                        up + "Secretary%20Information%20Instruction%20as%20on%2017-10-2026_1792235575.pdf",
                        up + "pressnote%20regarding%202026%20DE%20first%20session%20viva_1792238478.pdf"}
    secretary = got[up + "Secretary%20Information%20Instruction%20as%20on%2017-10-2026_1792235575.pdf"]
    assert secretary.published.date() == dt.date(2026, 10, 17)  # the stamp agrees with the name's own date
    assert got[up + "DE2026VivaPressNote_1792752290.pdf"].published.date() == dt.date(2026, 10, 23)
    assert got[up + "DE2026VivaPressNote_1792752290.pdf"].kind == "admit_card"  # ಪ್ರವೇಶ ಪತ್ರ
    assert len(record) == 6 and set(record.values()) == {""}  # the site's own pages without a stamp: recorded, not published
    later = parse(mb.KPSC, seen={k: "" for k in record}, record={})
    assert len(later) == 4
    new = parse(mb.KPSC, seen={}, record={})
    assert len(new) == 10 and sum(i.published.date() == TODAY for i in new) == 6
    assert not any("lorem" in i.title.lower() for i in new)
    assert not any("TenderforScanners" in (i.files[0][1] if i.files else "") for i in new)  # the office's tender


def test_tntrb_accepts_both_names_of_its_site():
    items = parse(mb.TNTRB)
    got = _by_url(items)
    syllabus = got["https://trb.tn.gov.in/syllabus1.php?language=LG-1&status=Active"]
    assert (syllabus.published.date(), syllabus.title) == (dt.date(2026, 10, 27),
                                                           "TAMIL NADU TEACHER ELIGIBILITY TEST - Revised Syllabus")
    note = got["https://www.trb.tn.gov.in/morenotification.php?id=02/2026&language=LG-1"]
    assert (note.published.date(), note.kind) == (dt.date(2026, 10, 26), "recruitment")
    assert note.title == "TAMIL NADU TEACHER ELIGIBILITY TEST FOR WORKING TEACHERS – OCTOBER-2026 - Notification"
    sgt = got["https://www.trb.tn.gov.in/more_notification_details.php?id=MN-1049&language=LG-1"]
    assert (sgt.published.date(), sgt.kind) == (dt.date(2026, 10, 15), "result")
    assert not any("amazonaws" in u for u in got)  # the S3 bucket's press note is left out
    with pytest.raises(Exception):
        mb.TNTRB.url("https://trb.tn.gov.in.example.com/x")
