import datetime as dt
import pathlib

import pytest

from lottery_pipeline import build, official, sambad
from lottery_pipeline.model import ValidationError

FIX = pathlib.Path(__file__).parent / "fixtures"
OCT17 = dt.date(2026, 10, 17)
OCT18 = dt.date(2026, 10, 18)


def tier(draw, rank):
    return next(t for t in draw.tiers if t.rank == rank)


def test_sambad_1pm_sunday():
    d = sambad.parse((FIX / "sambad_MN181026.txt").read_text(), "NL1PM", OCT18)
    assert d.name == "DEAR WISH SUNDAY WEEKLY LOTTERY"
    assert d.draw_no == 50
    assert tier(d, "1").numbers == ["74D 70698"]
    assert tier(d, "CONS").numbers == ["70698"]
    assert tier(d, "2").numbers[:3] == ["15172", "15525", "15647"]
    assert tier(d, "3").numbers == ["0889", "1873", "1964", "4182", "4788",
                                    "4957", "6981", "6995", "7733", "9783"]
    assert tier(d, "4").numbers[0] == "0114"
    assert len(tier(d, "5").numbers) == 100
    assert tier(d, "1").prize == 1_00_00_000 and tier(d, "5").prize == 120


def test_sambad_6pm_is_sikkim_slot():
    d = sambad.parse((FIX / "sambad_DN181026.txt").read_text(), "SK6PM", OCT18)
    assert d.name == "DEAR EMPIRE SUNDAY WEEKLY LOTTERY"
    assert d.to_json()["state"] == "Sikkim"
    assert tier(d, "1").numbers == ["93G 38150"]
    assert tier(d, "4").numbers[-1] == "9443"


def test_sambad_rejects_wrong_date():
    with pytest.raises(ValidationError):
        sambad.parse((FIX / "sambad_MN181026.txt").read_text(), "NL1PM", OCT17)


def test_sambad_rejects_truncated_sheet():
    text = (FIX / "sambad_MN181026.txt").read_text()
    lines = text.splitlines()
    cut = "\n".join(l for l in lines if not l.strip().startswith("0056"))
    with pytest.raises(ValidationError):
        sambad.parse(cut, "NL1PM", OCT18)


def test_official_raw_ocr_with_misreads_is_rejected():
    # Real tesseract output: "72587", "124Z7" etc. must never be published.
    text = (FIX / "official_MN171026_ocr_psm4.txt").read_text()
    with pytest.raises(ValidationError):
        official.parse_d5(text, "NL1PM", OCT17, "X", 50)


def test_official_clean_ocr_parses():
    text = (FIX / "official_MN171026_corrected.txt").read_text()
    d = official.parse_d5(text, "NL1PM", OCT17, "DEAR VISION SATURDAY WEEKLY LOTTERY", 50)
    assert tier(d, "1").numbers == ["39C 06366"]
    assert tier(d, "4").numbers == sorted(["7587", "3867", "1031", "9229", "5142",
                                           "2806", "5742", "9612", "6052", "7789"])
    assert "7536" in tier(d, "5").numbers


def _sambad_draw():
    d = sambad.parse((FIX / "sambad_MN181026.txt").read_text(), "NL1PM", OCT18)
    d.sources.append({"kind": "lotterysambad", "url": "s"})
    return d


def test_merge_verifies_when_gazette_agrees():
    fast = _sambad_draw()
    first = build.merge(None, fast, None, None, print)
    assert first["status"] == "OK" and not first["verified"]
    second = build.merge(first, None, _sambad_draw(), "https://x/old_results/MN181026.PDF", print)
    assert second["verified"] is True
    assert [s["kind"] for s in second["sources"]] == ["lotterysambad", "official"]


def test_merge_keeps_sambad_and_warns_on_disagreement():
    first = build.merge(None, _sambad_draw(), None, None, print)
    bad = _sambad_draw()
    bad.tiers[5].numbers[0] = "0001"
    warnings = []
    out = build.merge(first, None, bad, "u", warnings.append)
    assert out["verified"] is False and warnings
    assert out["tiers"][5]["numbers"][0] == first["tiers"][5]["numbers"][0]


def test_merge_gazette_only_without_consensus_gives_none():
    assert build.merge(None, None, None, "u", print) is None
