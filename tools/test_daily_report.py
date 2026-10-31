"""python3 -m pytest -q tools/"""
import datetime as dt
import json
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import daily_report as r  # noqa: E402

MD = """# Search Console
## Countries
| country | clicks |
|---|---|
| ind | 5 |
| bgd | 0 |

## Daily (all countries)
| date | clicks |
|---|---|
| a | 1 |
| b | 2 |
| c | 3 |
"""


def test_sections_by_heading_prefix():
    assert r.section(MD, "Daily", 2) == ["| date | clicks |", "|---|---|", "| b | 2 |", "| c | 3 |"]
    assert r.section(MD, "Countries", 1)[-1] == "| ind | 5 |"


def test_a_draw_is_on_time_from_its_first_commit_with_numbers(monkeypatch):
    day = lambda nums: json.dumps({"draws": [{"slot": "NL1PM", "tiers": [{"numbers": nums}]}]})
    log = "s1 2026-10-30T07:35:00+00:00\ns2 2026-10-30T07:40:00+00:00\n"
    monkeypatch.setattr(r, "git", lambda *a: log)
    monkeypatch.setattr(r, "show", lambda ref: {"s1:v2/days/2026-10-30.json": day([]),
                                                 "s2:v2/days/2026-10-30.json": day(["38G 03421"])}.get(ref, ""))
    rows = r.timeliness("2026-10-30")
    assert "| NL1PM | 13:00 | 13:10 | 10 min | yes |" in rows
    assert any(row.startswith("| SK6PM") and "NO" in row for row in rows)
