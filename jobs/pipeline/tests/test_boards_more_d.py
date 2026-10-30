"""Boards of boards_more_d.py against their real pages of 29-10-2026, read from the India server (tests/fixtures/<source>.html).

indiapost.html  www.indiapost.gov.in/vacancies, whole (147 KB).
"""
import datetime as dt
import html
import os
import re

import pytest

from sarkari import boards_more_d as bm
from sarkari import classify
from sarkari.ssc import FormatChanged

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 30)
CUTOFF = dt.date(2025, 10, 21)
FILE = "https://www.indiapost.gov.in/api/documents/file/"


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=CUTOFF, today=TODAY):
    warnings = []
    items = board.parse(page(board.SOURCE), today, cutoff, warn=warnings.append)
    assert warnings == []
    return items


def test_indiapost_counts_hosts_and_kinds():
    items = parse(bm.INDIAPOST)
    assert len(items) == 6 and len({i.id for i in items}) == 6
    assert len(parse(bm.INDIAPOST, TODAY - dt.timedelta(days=120))) == 2  # the two Drivers notices of 01-08-2026
    assert len(parse(bm.INDIAPOST, TODAY)) == 0
    assert len(parse(bm.INDIAPOST, dt.date(2015, 1, 1))) == 12  # 13 rows, less the artists' empanelment
    assert all(i.source == "indiapost" and i.exam == "INDIAPOST" and i.date_only for i in items)
    assert all(i.link and i.link.startswith(FILE) and not i.files for i in items)
    assert all(i.kind in classify.KINDS for i in items)
    assert [i.kind for i in items] == ["recruitment", "recruitment", "amendment", "recruitment", "recruitment", "recruitment"]
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    assert bm.ALL == (bm.INDIAPOST,)


def test_indiapost_rows_match_the_table_shown():
    items = parse(bm.INDIAPOST, dt.date(2015, 1, 1))
    got = {i.link: i for i in items}
    drivers_hi = got[FILE + "103pgjk0mj9n00heh09v82063nq0007d0sjth3igcr04nr70fiaj0eiq939zwj"]
    assert drivers_hi.title == ("Notification for filling up of Drivers (Ordinary Grade) through deputation /absorption "
                                "basis on 01.08.2026 (Hindi Version)")
    assert (drivers_hi.published.date(), drivers_hi.kind) == (dt.date(2026, 8, 1), "recruitment")
    drivers_en = got[FILE + "1034tw42vyvr3kbvd3e7gy369dy3beji0s5ja0e34b062aa0eiqj0gxg634ume"]
    assert drivers_en.title.endswith("basis on 01.08.2026")
    mms = got[FILE + "103eold2idj70ejh53if443eosm05ma10sj5x3hgln3a0qs048d50fgue3da11"]
    assert (mms.title, mms.published.date()) == (
        "Direct Recruitment for the post of Manger Mail Motor Service (MMS) in Department of Posts", dt.date(2026, 6, 5))
    corr = got[FILE + "1004nrp2nzm20hct53igk034uti3e7oe0sjtd0haob3kaog3e7m73k9vk38l52"]
    assert (corr.kind, corr.published.date()) == ("amendment", dt.date(2026, 6, 29))
    sports = got[FILE + "1034tap2jqpu3jtp538ljf03aiu038y60sj823juiz3bv44062h90havq0fg91"]
    assert (sports.kind, sports.published.date()) == ("result", dt.date(2025, 3, 4))
    assert not any("Empanelment" in i.title for i in items)
    # Every row of the visible table (page 1 of 2) is read with the same link and day.
    shown = re.findall(r'data-column-key="title".*?href="(/api/documents/file/[a-z0-9]+)">(.*?)</a>.*?'
                       r'data-column-key="date".*?</svg>(\d{2}-\d{2}-\d{4})</div>', page("indiapost"), re.S)
    assert len(shown) == 10
    for href, title, day in shown:
        if "Empanelment" in title:
            assert "https://www.indiapost.gov.in" + href not in got
            continue
        item = got["https://www.indiapost.gov.in" + href]
        assert item.published.date() == dt.datetime.strptime(day, "%d-%m-%Y").date()
        assert item.title.startswith(classify.normalize(html.unescape(title)))


def test_indiapost_missing_data_is_a_format_change():
    with pytest.raises(FormatChanged):
        bm.INDIAPOST.parse("<html>no data</html>", TODAY, CUTOFF)
