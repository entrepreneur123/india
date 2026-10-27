"""Maharashtra State Lottery: the official results list (real page of 27-10-2026) and the rules that
decide whether OCR readings of a sheet are published. OCR itself is not run here (slow, needs
tesseract and OpenCV); see MH_OCR_TEST below."""
import datetime as dt
import json
import os
import pathlib

import pytest

from lottery_pipeline import maharashtra as mh

F = pathlib.Path(__file__).parent / "fixtures"


def test_results_list_gives_date_name_and_pdf():
    rows = mh.list_sheets((F / "mh_results_list.html").read_text())
    assert len(rows) == 10
    date, local, url = rows[0]
    assert date == dt.date(2026, 10, 27) and local == "महाराष्ट्र तेजस्विनी मासिक सोडत"
    assert url.startswith("https://lottery.maharashtra.gov.in/UploadFiles/") and url.endswith(".pdf")
    assert " " not in url and "%20" in url
    assert [mh.english_name(u) for _, _, u in rows[:5]] == ["Tejaswini", "Padmini", "Sahyadri Mahalaxmi",
                                                             "Gajlaxmi Mangal", "Ganeshlaxmi Shubh"]
    assert rows[-1][0] == dt.date(2026, 10, 25) and mh.english_name(rows[-1][2]) == "Gajlaxmi Ravi"


def grid(values, rows):
    """Numbers printed column after column, `rows` per column, as page fractions (x, y)."""
    out, it = [], iter(values)
    cols = -(-len(values) // rows)
    full_rows = len(values) - (cols - 1) * rows  # rows that reach the last column
    for c in range(cols):
        for r in range(rows):
            if c < cols - 1 or r < full_rows:
                out.append((str(next(it)), 0.1 + c * 0.04, 0.6 + r * 0.017, 0.035))
    return out


def test_random_tier_order_check():
    assert mh._column_order_ok(grid(list(range(1050, 1150)), 5))            # 100 numbers, 5 rows of 20
    assert mh._column_order_ok(grid(list(range(1000, 1350)), 18))           # 350 = 8 rows of 20 + 10 of 19
    nums = grid(list(range(1050, 1150)), 5)
    nums[37] = ("1094",) + nums[37][1:]                                     # one digit misread
    assert not mh._column_order_ok(nums)
    assert not mh._column_order_ok(grid(list(range(1050, 1150)), 5)[:-1] + [("9999", 0.5, 0.6, 0.035)])


def reading(first=("2142", "GL-00"), fourth=("2013", "4179"), heads=None):
    """A fake OCR reading of a small sheet: 1st prize, a 2nd prize and a two-number 4th... tier."""
    nums = [(first[0], 0.6, 0.25, 0.08, first[1], False), ("2273", 0.8, 0.32, 0.08, None, False)]
    nums += [(n, 0.1 + i * 0.09, 0.45, 0.08, None, False) for i, n in enumerate(fourth)]
    return {"head": {"name": ["GAJLAXMI", "RAVI"], "kind": "weekly", "date": "2026-10-25", "drawNo": 43, "time": "16:45",
                     "series": [f"GL-0{i}" for i in range(10)]},
            "headings": heads or [{"rank": "1", "top": 0.23, "prize": 10000, "count": None},
                                  {"rank": "2", "top": 0.30, "prize": 5000, "count": 1},
                                  {"rank": "3", "top": 0.40, "prize": 2000, "count": 2}],
            "numbers": nums}


def test_readings_must_agree_number_by_number():
    got = mh._agree([reading()] * 3 + [reading(first=("9142", "GL-00"))])   # one misread 1st prize: outvoted 3:1
    assert got["tiers"] == [{"rank": "1", "prize": 10000, "numbers": ["GL-00 2142"]},
                            {"rank": "2", "prize": 5000, "numbers": ["2273"]},
                            {"rank": "3", "prize": 2000, "numbers": ["2013", "4179"]}]
    with pytest.raises(mh.MaharashtraError):  # 2:1 is too close for a number with no printed order to check
        mh._agree([reading(), reading(), reading(first=("9142", "GL-00"))])
    with pytest.raises(mh.MaharashtraError):  # 4:2 as well
        mh._agree([reading()] * 4 + [reading(first=("9142", "GL-00"))] * 2)
    with pytest.raises(mh.MaharashtraError):  # a number the readings missed: the printed count does not match
        mh._agree([reading(fourth=("2013",))] * 3)
    with pytest.raises(mh.MaharashtraError):  # the 1st prize must name a series printed on the sheet
        mh._agree([reading(first=("2142", "XX-00"))] * 3)


def test_a_split_place_in_a_random_tier_is_settled_by_the_printed_order():
    vals = list(range(1050, 1150))
    def run(misread):
        r = reading(heads=[{"rank": "1", "top": 0.23, "prize": 10000, "count": None},
                           {"rank": "2", "top": 0.55, "prize": 250, "count": 100}])
        r["numbers"] = [r["numbers"][0]] + [
            (("4" + n[1:]) if misread and n == "1063" else n, x, y, w, None, False) for n, x, y, w in grid(vals, 5)]
        return r
    got = mh._agree([run(True)] * 3 + [run(False)] * 2)    # 4063 x3, 1063 x2: only 1063 fits the order
    assert got["tiers"][1]["numbers"] == [str(v) for v in vals]
    with pytest.raises(mh.MaharashtraError):              # read once: not enough
        mh._agree([run(True)] * 3 + [run(False)])


def test_update_links_the_pdf_when_a_sheet_does_not_read(tmp_path, monkeypatch):
    page = (F / "mh_results_list.html").read_text()
    good = mh._agree([reading()] * 3)

    def fake_read(pdf, warn=print):
        if pdf == b"good":
            return good
        raise mh.MaharashtraError("no two readings agree")

    monkeypatch.setattr(mh, "read", fake_read)
    pdfs = lambda url: b"good" if "Gajlaxmi%20Ravi" in url else b"bad"
    n = mh.update(tmp_path, dt.date(2026, 10, 27), warn=lambda s: None, fetch_list=lambda: page, fetch_pdf=pdfs)
    assert n == 10
    day = json.loads((tmp_path / "v2/mh/days/2026-10-25.json").read_text())
    (d,) = day["draws"]
    assert d["id"] == "2026-10-25_MH_GAJLAXMI-RAVI" and d["name"] == "Maharashtra Gajlaxmi Ravi" and d["status"] == "OK"
    assert d["tiers"][0] == {"rank": "1", "prize": 10000, "match": "EXACT", "numbers": ["GL-00 2142"]}
    assert d["tiers"][1]["match"] == "LAST4" and d["time"] == "16:45" and d["nameLocal"].startswith("महाराष्ट्र")
    pending = json.loads((tmp_path / "v2/mh/days/2026-10-27.json").read_text())["draws"]
    assert {x["status"] for x in pending} == {"PDF"} and all(x["tiers"] == [] for x in pending)
    assert json.loads((tmp_path / "v2/mh/index.json").read_text())["dates"] == ["2026-10-27", "2026-10-26", "2026-10-25"]
    # Next run: published draws are not fetched again; the others are retried.
    asked = []
    mh.update(tmp_path, dt.date(2026, 10, 27), warn=lambda s: None, fetch_list=lambda: page,
              fetch_pdf=lambda u: asked.append(u) or b"bad")
    assert len(asked) == 9 and not any("Gajlaxmi%20Ravi" in u for u in asked)


def test_a_sheet_for_another_date_is_not_published(tmp_path, monkeypatch):
    other = mh._agree([reading()] * 3) | {}
    other = {"head": dict(other["head"], date="2026-10-18"), "tiers": other["tiers"]}
    monkeypatch.setattr(mh, "read", lambda pdf, warn=print: other)
    page = (F / "mh_results_list.html").read_text()
    mh.update(tmp_path, dt.date(2026, 10, 27), warn=lambda s: None, fetch_list=lambda: page, fetch_pdf=lambda u: b"x")
    d = json.loads((tmp_path / "v2/mh/days/2026-10-25.json").read_text())["draws"][0]
    assert d["status"] == "PDF"


@pytest.mark.skipif(not os.environ.get("MH_OCR_TEST"), reason="set MH_OCR_TEST=/path/to/sheet.pdf:/path/to/gold.json")
def test_real_sheet_reads_exactly():
    pdf, gold = os.environ["MH_OCR_TEST"].split(":")
    got = mh.read(pathlib.Path(pdf).read_bytes(), warn=print)
    assert {t["rank"]: t["numbers"] for t in got["tiers"]} == json.loads(pathlib.Path(gold).read_text())


def test_without_ocr_every_draw_is_listed_with_its_official_pdf(tmp_path):
    page = (F / "mh_results_list.html").read_text()
    n = mh.update(tmp_path, dt.date(2026, 10, 27), warn=lambda s: None, fetch_list=lambda: page,
                  fetch_pdf=lambda u: pytest.fail("no download without OCR"), ocr=False)
    assert n == 10
    draws = json.loads((tmp_path / "v2/mh/days/2026-10-27.json").read_text())["draws"]
    assert sorted(d["name"] for d in draws) == ["Maharashtra Gajlaxmi Mangal", "Maharashtra Ganeshlaxmi Shubh",
                                                "Maharashtra Padmini", "Maharashtra Sahyadri Mahalaxmi", "Maharashtra Tejaswini"]
    assert {d["status"] for d in draws} == {"PDF"} and all(d["sources"][0]["url"].endswith(".pdf") for d in draws)
    assert next(d for d in draws if d["name"] == "Maharashtra Tejaswini")["kind"] == "monthly"
    assert mh.update(tmp_path, dt.date(2026, 10, 27), warn=lambda s: None, fetch_list=lambda: page, ocr=False) == 0
