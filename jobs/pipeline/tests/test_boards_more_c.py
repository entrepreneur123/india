"""Boards of boards_more_c.py against their real pages of 29-10-2026, read from the India server (tests/fixtures/<source>.html).

crpf.html  rect.crpf.gov.in home page, whole.
nta.html   nta.ac.in home page is 717 KB: the fixture is an unchanged slice of its bytes, from the "Latest @ NTA" box
           up to the end of its entry for Notice_20250806173333.pdf (06-08-2025); the rest of the box is older.
"""
import datetime as dt
import os
from collections import Counter

import pytest

from sarkari import boards_more_c as bm
from sarkari import classify

HERE = os.path.dirname(__file__)
TODAY = dt.date(2026, 10, 29)


def page(source):
    return open(os.path.join(HERE, "fixtures", f"{source}.html"), encoding="utf-8").read()


def parse(board, cutoff=dt.date(2025, 10, 21), today=TODAY):
    warnings = []
    items = board.parse(page(board.SOURCE), today, cutoff, warn=warnings.append, seen=None, record={})
    assert warnings == []
    return items


def url(item):
    return item.files[0][1] if item.files else item.link


@pytest.mark.parametrize("board,count,window,today", [(bm.CRPF, 16, 8, 0), (bm.NTA, 69, 28, 0)])
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


def test_crpf_tables():
    items = parse(bm.CRPF)
    gdmo = items[0]
    assert gdmo.title == ("WINDOW ADVERTISEMENT FOR CONTRACTUAL APPOINTMENT OF 15 GENERAL DUTY MEDICAL OFFICERS IN VARIOUS "
                          "UNITS/INSTITUTION OF CRPF & UNITS OF NDRF")
    assert (gdmo.published.date(), gdmo.kind) == (dt.date(2026, 10, 19), "recruitment")
    assert url(gdmo) == "https://rect.crpf.gov.in/Upload/Recruitment/Recruitment_Advertisement_333102026-941.pdf"
    # a bare "CORRIGENDUM/ADDENDUM" row carries its table's heading
    corr = items[1]
    assert corr.title == ("Recruitment of Meritorious Sportspersons for the post of HC/GD and CT/GD under Sports Quota : "
                          "Publication of Advertisement/Notification: CORRIGENDUM/ADDENDUM")
    assert (corr.published.date(), corr.kind) == (dt.date(2026, 10, 13), "amendment")
    # file names with spaces and "&" are quoted
    walkin = items[-1]
    assert url(walkin) == "https://rect.crpf.gov.in/Upload/Recruitment/Annexure%201%20%26%202-976.pdf"
    assert (walkin.published.date(), walkin.kind) == (dt.date(2025, 12, 12), "recruitment")
    amended = [i for i in items if "Amendment dated 17/04/2026" in i.title]
    assert [(i.lang, i.kind) for i in amended] == [("en", "amendment"), ("hi", "amendment")]
    # compassionate-ground appointment lists (the first table, 14 rows) are left out
    assert not any("ompassionate" in i.title for i in parse(bm.CRPF, cutoff=dt.date(2015, 1, 1)))
    assert len(list(bm.CRPF.entries(page("crpf")))) == 52
    assert Counter(i.kind for i in items) == {"recruitment": 8, "amendment": 3, "result": 3, "admit_card": 1, "notice": 1}


def test_nta_keeps_only_job_related_exams():
    items = parse(bm.NTA)
    by = {url(i): i for i in items}
    ext = by["https://nta.ac.in/Download/Notice/Notice_20261027235155.pdf"]
    assert ext.title == "Extension of Last Date for Submission of Online Application Form for UGC-NET December 2026 Examination – Reg."
    assert (ext.published.date(), ext.kind) == (dt.date(2026, 10, 27), "amendment")
    lib = by["https://nta.ac.in/Download/Notice/Notice_20261007204855.pdf"]
    assert lib.title == "Vacancy Notification for Librarian - Administration"
    assert (lib.published.date(), lib.kind) == (dt.date(2026, 10, 7), "recruitment")
    csir = by["https://nta.ac.in/Download/Notice/Notice_20261008083141.pdf"]
    assert (csir.title, csir.kind) == ("Inviting Online Application for Joint CSIR UGC-NET December 2026", "recruitment")
    # admission tests are left out: JEE (Main) 2027 advisory of 28-10-2026, NIFTEE, ICAR AIEEA, "Post Graduate" (AIAPGET)
    assert "https://nta.ac.in/Download/Notice/Notice_20261028220547.pdf" not in by
    assert not any(w in i.title for i in items for w in ("JEE", "NEET", "CUET", "NIFTEE", "AIAPGET", "NITTT"))
    # the UGC-NET corrigendum on the NIC CDN is an outside link
    assert not any("cdnbbsr" in url(i) for i in items)
    assert len(list(bm.NTA.entries(page("nta")))) == 75  # 179 on the whole page
    older = parse(bm.NTA, cutoff=dt.date(2025, 8, 1))
    assert older[-1].title == "INVITATION OF APPLICATIONS FOR VARIOUS POSTS ON CONTRACTUAL BASIS"
    assert (older[-1].published.date(), older[-1].kind) == (dt.date(2025, 8, 6), "recruitment")
