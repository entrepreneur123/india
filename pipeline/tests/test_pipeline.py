import datetime as dt
import pathlib

import pytest

from lottery_pipeline import build, official, sambad
from lottery_pipeline.model import SPECIALS, ValidationError

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


# --- 35 days of real sheets (18 Sep - 17 Oct 2026, no draw on 2 Oct) ---------------------------

WEEKDAY_NAMES = {
    "MN": ["RISE", "SHINE", "SPARK", "STAR", "VICTORY", "VISION", "WISH"],
    "DN": ["LEGEND", "PRESTIGE", "REGAL", "SUPREME", "CROWN", "ELITE", "EMPIRE"],
    "EN": ["CLOVER", "DESTINY", "DREAM", "FAME", "HORIZON", "LUCKY", "MAGIC"],
}
DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
SLOT_OF = {"MN": "NL1PM", "DN": "SK6PM", "EN": "NL8PM"}
HISTORY = sorted((FIX / "history").glob("*.txt"))


def test_history_fixtures_present():
    assert len(HISTORY) == 102


@pytest.mark.parametrize("path", HISTORY, ids=lambda p: p.stem)
def test_every_real_sheet_parses_with_expected_name(path):
    code, date = path.stem[:2], dt.datetime.strptime(path.stem[2:], "%d%m%y").date()
    d = sambad.parse(path.read_text(), SLOT_OF[code], date)
    wd = date.weekday()
    assert d.name == f"DEAR {WEEKDAY_NAMES[code][wd]} {DAYS[wd]} WEEKLY LOTTERY"


def test_sambad_8pm_sunday():
    d = sambad.parse((FIX / "sambad_EN181026.txt").read_text(), "NL8PM", OCT18)
    assert d.name == "DEAR MAGIC SUNDAY WEEKLY LOTTERY"
    assert tier(d, "1").numbers == ["37K 90889"]


def test_sambad_matches_official_gazette_17_oct_1pm():
    s = sambad.parse((FIX / "history" / "MN171026.txt").read_text(), "NL1PM", OCT17)
    o = official.parse_d5((FIX / "official_MN171026_corrected.txt").read_text(), "NL1PM", OCT17, "x", 50)
    assert s.numbers_key() == o.numbers_key()


# --- bumper / monthly sheets ----------------------------------------------------------------

def test_ganesh_bumper_sheet():
    d = sambad.parse_special((FIX / "sambad_GH190926.txt").read_text(), "SK_GANESH_BUMPER_2026")
    assert d.date == "2026-09-19"
    assert tier(d, "1").numbers == ["A 801021"] and tier(d, "1").prize == 3_00_00_000
    assert tier(d, "2").numbers == ["A 981936", "B 900028"]
    assert len(tier(d, "7").numbers) == 500


def test_punjab_monthly_sheet_uses_last5_tier():
    d = sambad.parse_special((FIX / "sambad_MT031026.txt").read_text(), "PB_DEAR200_MONTHLY_2026_10")
    assert tier(d, "4").match == "LAST5" and tier(d, "4").numbers[0] == "16110"
    assert tier(d, "5").numbers == ["5713", "8337"]
    assert d.to_json()["state"] == "Punjab" and d.to_json()["special"] is True


def test_special_sheet_with_extra_numbers_is_rejected():
    text = (FIX / "sambad_GH190926.txt").read_text() + "\n 1111 2222 3333\n"
    with pytest.raises(ValidationError):
        sambad.parse_special(text, "SK_GANESH_BUMPER_2026")


def test_special_sheet_with_wrong_scheme_is_rejected():
    # Ganesh sheet parsed with the Punjab table: tier sizes differ, so it must fail.
    with pytest.raises(ValidationError):
        sambad.parse_special((FIX / "sambad_GH190926.txt").read_text(), "PB_DEAR200_MONTHLY_2026_10")


def test_specials_config_loaded():
    assert {"SK_GANESH_BUMPER_2026", "PB_DEAR200_MONTHLY_2026_10"} <= set(SPECIALS)


# --- build orchestration with the network stubbed --------------------------------------------

def test_build_writes_v2_feeds(tmp_path, monkeypatch):
    sheets = {"MN181026": "sambad_MN181026.txt", "DN181026": "sambad_DN181026.txt",
              "EN181026": "sambad_EN181026.txt"}

    def fake_fetch(slot_id, date, today=None):
        slot = sambad.SLOTS[slot_id]
        stem = f"{slot.file_code}{date:%d%m%y}"
        if stem not in sheets:
            return None
        d = sambad.parse((FIX / sheets[stem]).read_text(), slot_id, date)
        d.sources.append({"kind": "lotterysambad", "url": stem})
        return d

    monkeypatch.setattr(sambad, "fetch", fake_fetch)
    monkeypatch.setattr(sambad, "listed_today", lambda: [("MN181026", "DEAR 1:00 PM WEEKLY")])
    monkeypatch.setattr(official, "list_codes", lambda date: [])
    now = dt.datetime(2026, 10, 18, 21, 0, tzinfo=build.IST)
    build.run(str(tmp_path), OCT18, backfill_days=1, use_official=True, deep=False, now_ist=now)

    import json
    today = json.loads((tmp_path / "v2" / "today.json").read_text())
    assert [d["slot"] for d in today["draws"]] == ["NL8PM", "SK6PM", "NL1PM"]
    index = json.loads((tmp_path / "v2" / "index.json").read_text())
    assert index["dates"] == ["2026-10-18"]
    assert index["noDraw"] == ["2026-10-17"]  # official calendar empty in this stub
    config = json.loads((tmp_path / "v2" / "config.json").read_text())
    assert config["mirrors"] and "_comment" not in config


def test_build_skips_draws_that_cannot_exist_yet(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(sambad, "fetch", lambda slot_id, date, today=None: calls.append(slot_id))
    monkeypatch.setattr(sambad, "listed_today", lambda: [])
    now = dt.datetime(2026, 10, 18, 13, 3, tzinfo=build.IST)   # 3 minutes after the 1 PM draw
    build.run(str(tmp_path), OCT18, backfill_days=0, use_official=False, deep=False, now_ist=now)
    assert calls == []
    now = dt.datetime(2026, 10, 18, 13, 8, tzinfo=build.IST)
    build.run(str(tmp_path), OCT18, backfill_days=0, use_official=False, deep=False, now_ist=now)
    assert calls == ["NL1PM"]


def test_unchanged_feeds_are_not_rewritten(tmp_path, monkeypatch):
    monkeypatch.setattr(sambad, "fetch", lambda slot_id, date, today=None: None)
    monkeypatch.setattr(sambad, "listed_today", lambda: [])
    now = dt.datetime(2026, 10, 18, 21, 0, tzinfo=build.IST)
    build.run(str(tmp_path), OCT18, backfill_days=0, use_official=False, deep=False, now_ist=now)
    first = (tmp_path / "v2" / "today.json").read_text()
    build.run(str(tmp_path), OCT18, backfill_days=0, use_official=False, deep=False, now_ist=now)
    assert (tmp_path / "v2" / "today.json").read_text() == first   # same generatedAt: no commit


def test_verify_against_noisy_official_ocr():
    # Real tesseract output of the 17-10-2026 1 PM gazette, including its misreads (7587 -> 72587 ...).
    ocr = (FIX / "official_MN171026_ocr_psm4.txt").read_text()
    s = sambad.parse((FIX / "history" / "MN171026.txt").read_text(), "NL1PM", OCT17)
    check = official.verify(s, [ocr])
    assert check["top"] is True                 # 1st prize ticket and all ten 2nd-prize numbers confirmed
    assert 0.9 < check["ratio"] < 1.0           # the misread lower-tier numbers stay unconfirmed
    assert "7587" in check["unconfirmed"]


def test_verify_catches_a_wrong_second_prize():
    ocr = (FIX / "official_MN171026_ocr_psm4.txt").read_text()
    s = sambad.parse((FIX / "history" / "MN171026.txt").read_text(), "NL1PM", OCT17)
    s.tiers[2].numbers[0] = "11111"
    assert official.verify(s, [ocr])["top"] is False


def test_merge_marks_verified_from_ocr_text():
    s = sambad.parse((FIX / "history" / "MN171026.txt").read_text(), "NL1PM", OCT17)
    s.sources.append({"kind": "lotterysambad", "url": "x"})
    base = build.merge(None, s, None, None, print)
    out = build.merge(base, None, None, "official-url", print,
                      [(FIX / "official_MN171026_ocr_psm4.txt").read_text()])
    assert out["verified"] is True and out["officialCheck"]["top"] is True


def test_require_exit_code_drives_the_watch_loop(tmp_path, monkeypatch):
    def fake_fetch(slot_id, date, today=None):
        if slot_id != "NL1PM":
            return None
        d = sambad.parse((FIX / "sambad_MN181026.txt").read_text(), slot_id, date)
        d.sources.append({"kind": "lotterysambad", "url": "x"})
        return d
    monkeypatch.setattr(sambad, "fetch", fake_fetch)
    monkeypatch.setattr(sambad, "listed_today", lambda: [])
    args = ["--out", str(tmp_path), "--date", "2026-10-18", "--backfill-days", "0", "--no-official"]
    assert build.main(args + ["--require", "NL1PM"]) == 0
    assert build.main(args + ["--require", "NL1PM,NL8PM"]) == 3
