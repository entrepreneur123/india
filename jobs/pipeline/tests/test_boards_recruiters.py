import datetime as dt
import os

import pytest

from sarkari import boards_recruiters as br
from sarkari import classify, ssc

HERE = os.path.dirname(__file__)
CAPTURED = dt.date(2026, 10, 29)  # every fixture was read from the India server on this day


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=dt.date(2025, 10, 21), today=CAPTURED):
    warnings = []
    items = board.parse(page(board.SOURCE), today, cutoff, warn=warnings.append)
    assert warnings == []
    return items


def _by_url(items):
    return {(i.files[0][1] if i.files else i.link): i for i in items}


@pytest.mark.parametrize("board,year,today", [(br.RPSC, 258, 4), (br.RSSB, 10, 3), (br.DSSSB, 30, 2), (br.KERALAPSC, 13, 0)])
def test_real_pages_give_only_the_boards_own_links(board, year, today):
    items = parse(board)
    assert len(items) == year and len({i.id for i in items}) == year
    assert len(parse(board, cutoff=CAPTURED)) == today
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(u.startswith(board.BASE + "/") for i in items for u in [f[1] for f in i.files] + [i.link] if u)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert max(i.published.date() for i in items) <= CAPTURED


def test_rpsc_news_box():
    items = parse(br.RPSC)
    got = _by_url(items)
    first = got["https://rpsc.rajasthan.gov.in/Static/Result/C9F3F4CC860845C997938B802C4FAC0F.pdf"]
    assert (first.title, first.published.date(), first.kind) == (
        "Result Preamble for Sr. Teacher (Sec. Edu.) Comp. Exam - 2025 (Gujarati)", dt.date(2026, 10, 29), "result")
    corr = got["https://rpsc.rajasthan.gov.in/Static/RecruitmentAdvertisements/12E5561DA6634F6A9548306FFA667C12.pdf"]
    assert (corr.title, corr.published.date(), corr.kind) == (
        "Corrigendum No. 15/2026-27 for Sub Inspector Comb. Comp. Exam - 2021", dt.date(2026, 9, 9), "amendment")
    note = got["https://rpsc.rajasthan.gov.in/Static/PressNotes/096672EF-8065-4F8B-9CD7-8836F9403CA6.pdf"]
    assert (note.title, note.published.date()) == (
        "Press Note Regarding Interview Dates for Sub Inspector Comb. Comp. Exam - 2025 (Third Phase)", dt.date(2026, 10, 23))
    letter = next(i for i in items if i.title.startswith("Interview Letter for the post of Assistant Professor- Chemistry"))
    assert (letter.link, letter.published.date(), letter.kind) == (
        "https://rpsc.rajasthan.gov.in/viewcommon?Slice=EL&LinkType=I", dt.date(2026, 10, 28), "admit_card")
    assert not any("~" in (i.link or i.files[0][1]) for i in items)  # '~/Static/News/...' never resolves in a browser
    assert sum(i.kind == "answer_key" for i in items) == 37


def test_rpsc_advertisements_are_recruitments():
    item = br.RPSC.parse_entry("21/07/2026", "/Static/RecruitmentAdvertisements/C80D8376B256408692F17AAA61B754DE.pdf",
                               " Advt. No. 05/2026-27 for Physiotherapist - 2026", CAPTURED)
    assert (item.kind, item.exam_year) == ("recruitment", 2026)


def test_rssb_latest_news():
    got = _by_url(parse(br.RSSB))
    dv = got["https://rssb.rajasthan.gov.in/storage/news_item/1793273784.pdf"]
    assert (dv.title, dv.published.date(), dv.kind) == (
        "Driver 2024 : List of Provisionally Selected Candidate's for Document Verification after Trade Test", dt.date(2026, 10, 29), "result")
    amended = got["https://rssb.rajasthan.gov.in/storage/news_item/1793188157.pdf"]
    assert (amended.title, amended.published.date(), amended.kind) == (
        "Basic and Senior Computer Instructor 2026 : Ammended Advertisement", dt.date(2026, 10, 28), "amendment")
    assert got["https://rssb.rajasthan.gov.in/storage/news_item/1792066984.pdf"].published.date() == dt.date(2026, 10, 15)
    assert not any("Right when the page is loaded" in i.title for i in got.values())  # the commented-out sample rows


def test_dsssb_notifications_and_results_without_tenders():
    items = parse(br.DSSSB)
    got = _by_url(items)
    advt = got["https://dsssb.delhi.gov.in/dsssb/vacancy-notice-advertisement-no-052026-0"]
    assert (advt.title, advt.published.date(), advt.kind, advt.exam_year) == (
        "VACANCY NOTICE ADVERTISEMENT NO. 05/2026", dt.date(2026, 10, 9), "recruitment", 2026)
    result = got["https://dsssb.delhi.gov.in/sites/default/files/DSSSB/circulars-orders/result_notice_384_96-23.pdf"]
    assert (result.title, result.published.date(), result.kind) == (
        "RESULT NOTICE No: 384 For Post code: 96/23, Computer Lab/IT Assistant in New Delhi Municipal Council", dt.date(2026, 10, 29), "result")
    keys = got["https://dsssb.delhi.gov.in/dsssb/notice-display-final-answer-keys-online-computer-based-examination-various-examinations-1"]
    assert (keys.published.date(), keys.kind) == (dt.date(2026, 10, 28), "answer_key")
    assert not any("/tender/" in (i.link or "") for i in items)
    assert sum(i.kind == "recruitment" for i in items) == 2  # advertisements 04/2026 and 05/2026


def test_kerala_psc_gazettes():
    items = parse(br.KERALAPSC)
    first = items[0]
    assert first.link == "https://www.keralapsc.gov.in/extra-ordinary-gazette-date-30092026"
    assert first.title == "Kerala PSC notifications: EXTRA ORDINARY GAZETTE DATE 30/09/2026 (CAT.NO.151/2026 TO CAT.NO.161/2026), last date 04-11-2026"
    assert (first.published.date(), first.kind) == (dt.date(2026, 9, 30), "recruitment")
    assert [i.published.date() for i in parse(br.KERALAPSC, cutoff=CAPTURED - dt.timedelta(days=120))] == [
        dt.date(2026, 9, 30), dt.date(2026, 8, 31), dt.date(2026, 7, 30)]


@pytest.mark.parametrize("board", br.ALL)
def test_a_changed_page_stops_instead_of_guessing(board):
    with pytest.raises(ssc.FormatChanged):
        board.parse("<html>maintenance</html>", CAPTURED, dt.date(2026, 1, 1))


def test_look_alike_hosts_are_dropped():
    assert br.RSSB.parse_entry("29-10-2026", "https://rssb.rajasthan.gov.in.evil.example/a.pdf", "Results: x", CAPTURED) is None
    assert br.DSSSB.parse_entry("29-10-2026", "http://dsssb.delhi.gov.in/a.pdf", "Result Notice x", CAPTURED) is None
