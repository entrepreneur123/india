"""Boards of boards_more_e.py against their real pages (tests/fixtures/<source>.html).

tgpsc.html  websitenew.tgpsc.gov.in home page of 30-10-2026, whole, read live with the jobs UA. The site sends
            ISO-8859-1; its one non-ASCII byte (a no-break space after the vigilance cell's number) is outside the
            list, and the page is read as the India server reads pages (UTF-8, errors replaced).
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_e as be
from sarkari import classify
from sarkari.ssc import FormatChanged

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8", errors="replace").read()


def parse(board, cutoff=dt.date(2025, 10, 21), today=TODAY, text=None):
    warnings = []
    items = board.parse(text if text is not None else page(board.SOURCE), today, cutoff, warn=warnings.append,
                        seen=None, record={})
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


@pytest.mark.parametrize("board,count,window,today", [(be.TGPSC, 13, 13, 0)])
def test_real_pages_give_only_the_boards_own_links(board, count, window, today):
    items = parse(board)
    assert len(items) == count and len({i.id for i in items}) == count
    assert len(parse(board, cutoff=TODAY - dt.timedelta(days=120))) == window
    assert len(parse(board, cutoff=TODAY)) == today
    assert all(i.source == board.SOURCE and i.exam == board.family and i.date_only for i in items)
    assert all(url(i).startswith(board.BASE + "/") for i in items)
    assert all(bool(i.files) != bool(i.link) for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert all(i.published.date() <= TODAY for i in items)


def test_tgpsc_dates_come_from_the_upload_stamp_in_the_link():
    items = parse(be.TGPSC)
    by = {url(i): i for i in items}
    # base64 of "PRESSNOTE/WEBNOTE-07G-MPSO20261028221504.pdfaccesstime=2026-11-30" + the fixed tail; the padding "="
    # stays as written (the server answers 500 to "%3D")
    note = by["https://websitenew.tgpsc.gov.in/preview/"
              "UFJFU1NOT1RFL1dFQk5PVEUtMDdHLU1QU08yMDI2MTAyODIyMTUwNC5wZGZhY2Nlc3N0aW1lPTIwMjYtMTEtMzA=r95v17a0y2d8i13v"]
    assert note.title == ("GENERAL RECRUITMENT FOR THE POST OF MANDAL PLANNING AND STATISTICAL OFFICERS / ASSISTANT "
                          "STATISTICAL OFFICERS IN DIRECTORATE OF ECONOMICS & STATISTICS NOTIFICATION NO. 07/G/ES/2026, "
                          "DATED: 28/10/2026 - WEB NOTE")
    assert (note.published.date(), note.kind, note.lang, note.files) == (dt.date(2026, 10, 28), "recruitment", "en", ())
    # "...WEBNOTE-KEY-01OGPC202620261027192635.pdf": the last 14 digits are the upload time
    key = next(i for i in items if i.title.startswith("HOSTING OF KEY"))
    assert (key.published.date(), key.kind) == (dt.date(2026, 10, 27), "answer_key")
    hall = [i for i in items if "HALL TICKETS" in i.title]
    assert [(i.published.date(), i.kind) for i in hall] == [(dt.date(2026, 10, 19), "admit_card"),
                                                            (dt.date(2026, 10, 17), "admit_card")]
    cancel = next(i for i in items if "CANCELLATION OF BRIEF NOTIFICATION" in i.title)
    assert (cancel.published.date(), cancel.kind) == (dt.date(2026, 9, 11), "amendment")
    assert items[-1].published.date() == dt.date(2026, 9, 11)
    assert Counter(i.kind for i in items) == {"recruitment": 4, "notice": 3, "schedule": 2, "admit_card": 2,
                                              "answer_key": 1, "amendment": 1}


def test_tgpsc_leaves_out_departmental_tests_and_other_sites():
    entries = list(be.TGPSC.entries(page("tgpsc")))
    assert len(entries) == 13  # of 26 rows: 12 departmental/half-yearly test notices and the tests' portal link
    assert not any("DEPARTMENTAL" in h.upper() or "HALF YEARLY" in h.upper() for _, _, h in entries)
    assert not any("tspscdeptltest" in href for _, href, _ in entries)
    assert all(day for day, _, _ in entries)


def test_tgpsc_link_without_a_stamp_is_rejected_not_guessed():
    row = ('<h3 class="h4 panel-title">What\'s New</h3><table><tbody>'
           '<tr><td><a target="_blank" href = "preview/{}r95v17a0y2d8i13v">NOTICE {}</a></td></tr></tbody></table>')
    import base64
    good = base64.b64encode(b"PRESSNOTE/A20261001101010.pdfaccesstime=2026-12-31").decode()
    bad = base64.b64encode(b"PRESSNOTE/no-stamp.pdfaccesstime=2026-12-31").decode()
    rows = "".join(row.format(good if n % 5 else bad, n).split("<tbody>")[1].split("</tbody>")[0] for n in range(1, 6))
    text = '<h3 class="h4 panel-title">What\'s New</h3><table><tbody>' + rows + "</tbody></table>"
    warnings = []
    items = be.TGPSC.parse(text, TODAY, dt.date(2025, 10, 21), warn=warnings.append)
    assert len(items) == 4 and all(i.published.date() == dt.date(2026, 10, 1) for i in items)
    assert len(warnings) == 1 and "bad date" in warnings[0]
    with pytest.raises(FormatChanged):
        be.TGPSC.parse(page("tgpsc").replace("What's New</h3>", "Latest</h3>"), TODAY, dt.date(2025, 10, 21))
