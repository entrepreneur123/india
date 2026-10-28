import datetime as dt
import os

import pytest

from sarkari import ssc, upprpb

HERE = os.path.dirname(__file__)
PAGE = open(os.path.join(HERE, "fixtures", "upprpb_notice.html"), encoding="utf-8").read()
TODAY = dt.date(2026, 10, 20)


def _parse(page=PAGE, cutoff=dt.date(2026, 1, 1)):
    return upprpb.parse(page, TODAY, cutoff, warn=lambda m: None)


def test_real_page_gives_the_boards_own_pdfs_only():
    items = _parse()
    assert len(items) == 27  # 41 entries, 14 point to exam agencies' portals
    assert all(i.files[0][1].startswith("https://uppbpb.gov.in/FilesUploaded/Notice/") for i in items)
    assert all(i.source == "upprpb" and i.date_only and i.lang == "hi" for i in items)
    assert len({i.id for i in items}) == len(items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    first = items[0]
    assert first.published == dt.datetime(2026, 10, 14, tzinfo=ssc.IST)
    assert first.files[0][0] == "const 14 Oct 2026.pdf"  # the board's upload GUID is dropped from the name
    assert first.files[0][1].endswith("/const%2014%20Oct%202026c0201978-124f-4557-9c66-e162a881cb82.pdf")
    assert not first.title.endswith(("।", "|")) and "Notice Board" not in first.title


def test_kind_family_and_year_from_the_real_headlines():
    got = {(i.published.date().isoformat(), i.files[0][0]): (i.kind, i.exam, i.exam_year) for i in _parse()}
    assert got[("2026-10-14", "const 14 Oct 2026.pdf")] == ("result", "UPPC", 2025)  # final selection result
    assert got[("2026-10-12", "vig1_12102026.pdf")] == ("notice", "LDCE", None)  # promotion: serving staff
    assert got[("2026-10-05", "CONST 25 PET DATE CHANGE 05-10-2026.pdf")] == ("amendment", "UPPC", 2025)
    assert got[("2026-10-03", "HG_03-10-2026.pdf")] == ("result", "UPHG", 2025)
    assert got[("2026-09-10", "img20260910_18171661.pdf")] == ("amendment", "UPHG", 2025)  # postponed
    assert got[("2026-09-24", "720061.pdf")] == ("admit_card", "UPPC", 2025)
    assert got[("2026-09-24", "720060.pdf")] == ("schedule", "UPPC", 2025)
    assert got[("2026-08-07", "Notification HCMT Result 07-08-2026.pdf")] == ("result", "UPP", None)  # head constable
    assert got[("2026-07-31", "vig2_31072026.pdf")] == ("answer_key", "UPPC", 2025)
    assert got[("2026-07-31", "vig1_31072026.pdf")] == ("result", "UPPC", 2025)  # qualified for DV/PST
    assert got[("2026-07-21", "25SI_normalised_marks_notice.pdf")] == ("result", "UPSI", 2025)


def test_window_and_future_dates():
    assert all(i.published.date() >= dt.date(2026, 9, 1) for i in _parse(cutoff=dt.date(2026, 9, 1)))
    future = PAGE.replace("14-10-2026", "14-10-2027", 1)
    assert len(_parse(future)) == 26  # a date in the future is a malformed entry


LI = ("<li><a href='{href}' title='x' target='_blank'> उत्तर प्रदेश पुलिस में आरक्षी भर्ती-2025 का परिणाम"
      "<span class='whatsnew'> [ Notice Board ]</span></a><p class=\"date\">Date : 14-10-2026</p></li>")


@pytest.mark.parametrize("href", [
    "/FilesUploaded/Notice/../../web.config",
    "/FilesUploaded/Other/a.pdf",
    "javascript:alert(1)",
    "//evil.example/a.pdf",
    "/FilesUploaded/Notice/a.exe",
])
def test_odd_links_are_rejected(href):
    with pytest.raises(ssc.RecordError):
        upprpb.parse_entry(LI.format(href=href), TODAY)


def test_outside_portals_are_left_out_but_the_boards_absolute_links_work():
    assert upprpb.parse_entry(LI.format(href="https://upcons2025.com/x/loginpage.aspx"), TODAY) is None
    item = upprpb.parse_entry(LI.format(href="https://uppbpb.gov.in/FilesUploaded/Notice/a.pdf"), TODAY)
    assert item.files[0][1] == "https://uppbpb.gov.in/FilesUploaded/Notice/a.pdf"


@pytest.mark.parametrize("page,reason", [
    ("<html>maintenance</html>", "noticeList"),
    ('<ul id="noticeList">' + LI.format(href="/FilesUploaded/Notice/a.pdf") + "</ul>", "1 entries"),
    ('<ul id="noticeList">' + "<li>broken</li>" * 10 + "</ul>", "10 malformed"),
])
def test_a_changed_page_is_not_published(page, reason):
    with pytest.raises(ssc.FormatChanged, match=reason):
        _parse(page)
