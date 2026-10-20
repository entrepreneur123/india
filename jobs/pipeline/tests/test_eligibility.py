import copy
import datetime as dt
import json
import os

import pytest

from sarkari import eligibility as el

HERE = os.path.dirname(__file__)
CASES = json.load(open(os.path.join(HERE, "eligibility_cases.json"), encoding="utf-8"))["cases"]
RULES = {r["id"]: r for r in el.load_all()}


def _post(result: dict, key: str) -> dict:
    name, _, org = key.partition("|")
    found = [p for p in result["posts"] if p["name"] == name and (not org or p["org"] == org)]
    assert len(found) == 1, f"{key!r} matches {len(found)} posts"
    return found[0]


@pytest.mark.parametrize("case", CASES, ids=[c["name"] for c in CASES])
def test_shared_cases(case):
    result = el.evaluate(RULES[case["rules"]], case["profile"], dt.date.fromisoformat(case["today"]))
    want = case["expect"]
    for key in ("application", "fee", "relaxationYears", "counts"):
        if key in want:
            assert result[key] == want[key], key
    if "posts" in want:
        assert [p["status"] for p in result["posts"]] == want["posts"]
    if "reasons0" in want:
        assert result["posts"][0]["reasons"] == want["reasons0"]
    for key, status in want.get("byName", {}).items():
        assert _post(result, key)["status"] == status, key


def test_rule_files_load_and_bands_match_the_notices():
    assert set(RULES) == {"ssc-chsl-2026", "ssc-cgl-2026", "ssc-steno-2026", "ssc-si-2026"}
    assert len(RULES["ssc-cgl-2026"]["posts"]) == 40  # Notice para 2.1-2.5: 3 + 15 + 10 + 6 + 6
    assert len(RULES["ssc-chsl-2026"]["posts"]) == 3
    assert len(RULES["ssc-steno-2026"]["posts"]) == 2
    assert len(RULES["ssc-si-2026"]["posts"]) == 4


def test_a_typo_in_a_copied_date_fails_validation():
    bad = copy.deepcopy(RULES["ssc-chsl-2026"])
    bad["age"]["bands"]["18-27"]["bornFrom"] = "1998-08-02"  # the "naive" reading of 18-27
    with pytest.raises(el.RulesError, match="formula gives 1999-08-02"):
        el.validate(bad)


@pytest.mark.parametrize("mutate", [
    lambda r: r["posts"][0].update(ageBand="21-35"),
    lambda r: r["posts"][0].update(education="phd"),
    lambda r: r["age"]["relaxation"].pop("OBC"),
    lambda r: r["dates"].update(applyTo="2026-09-01"),
    lambda r: r.update(schema=2),
])
def test_inconsistent_rules_are_rejected(mutate):
    bad = copy.deepcopy(RULES["ssc-chsl-2026"])
    mutate(bad)
    with pytest.raises(el.RulesError):
        el.validate(bad)


def test_born_window_matches_ssc_convention():
    # "18-27 as on 01-08-2026": born 02-08-1999 .. 01-08-2008 (CHSL 2026 Notice para 5.1)
    assert el.born_window(dt.date(2026, 8, 1), 18, 27) == (dt.date(1999, 8, 2), dt.date(2008, 8, 1))
    assert el.born_window(dt.date(2026, 8, 1), 18, 27, 3) == (dt.date(1996, 8, 2), dt.date(2008, 8, 1))


def test_shift_years_leap_day():
    assert el.shift_years(dt.date(2024, 2, 29), -1) == dt.date(2023, 2, 28)
    assert el.shift_years(dt.date(2024, 2, 29), -4) == dt.date(2020, 2, 29)


@pytest.mark.parametrize("profile", [
    {"dob": "2004-01-01", "category": "GEN", "education": "12"},
    {"dob": "2004-01-01", "category": "UR", "education": "diploma"},
    {"dob": "01-01-2004", "category": "UR", "education": "12"},
])
def test_bad_profiles_are_refused(profile):
    with pytest.raises(ValueError):
        el.evaluate(RULES["ssc-chsl-2026"], profile, dt.date(2026, 9, 10))


def test_every_reason_and_note_has_a_known_code():
    # The website shows a sentence for each code; an unknown code would show raw text.
    known_requires = {"state_language", "jso_maths_or_statistics", "sig2_degree_subject",
                      "driving_licence_lmv", "si_fire_qualification"}
    known_notes = {"physical_standards", "bro_male_only", "deo_science_maths_some", "stenography_skill_test"}
    for rules in RULES.values():
        for p in rules["posts"]:
            assert set(p.get("requires", [])) <= known_requires, p["name"]
            assert set(p.get("notes", [])) <= known_notes, p["name"]
