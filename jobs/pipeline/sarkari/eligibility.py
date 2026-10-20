"""Who can apply: age, category relaxation, education, fee — read from rules/<exam>.json.

Every number in a rules file is copied from the exam's official Notice, with the paragraph
and page in "ref". This module never decides anything the Notice does not say:
anything it cannot compute (ex-servicemen service deduction, a post that needs a
particular degree subject) comes back as "check", with the reason, never as "eligible".

Age (SSC's own convention, verified against the CHSL and CGL 2026 Notices):
an age band "18-27 as on 01-08-2026" means born not earlier than 02-08-1999 and not
later than 01-08-2008, i.e. a candidate who has turned 27 on the crucial date is OUT.
That is (asOn - max years + 1 day) .. (asOn - min years). A relaxation of N years moves
only the first date back by N years. load() checks every band in a rules file against this
formula, so a typo in a copied date fails the build instead of reaching a user.

web/eligibility.js is a line-by-line port; tests/eligibility_cases.json runs against both.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import re

RULES_DIR = os.path.join(os.path.dirname(__file__), "..", "rules")
CATEGORIES = ("UR", "EWS", "OBC", "SC", "ST")
EDUCATION_LEVELS = ("10", "12", "graduate")
STATUSES = ("eligible", "check", "ineligible")  # best to worst
GENDERS = ("male", "female", "other")
RELAXATION_KEYS = ("SC", "ST", "OBC", "PwBD", "PwBD_OBC", "PwBD_SC", "PwBD_ST")


class RulesError(ValueError):
    pass


def shift_years(d: dt.date, years: int) -> dt.date:
    """Same day and month `years` later (negative: earlier). 29 Feb -> 28 Feb."""
    try:
        return d.replace(year=d.year + years)
    except ValueError:
        return d.replace(year=d.year + years, day=28)


def born_window(as_on: dt.date, min_age: int, max_age: int, relax_years: int = 0) -> tuple[dt.date, dt.date]:
    """(earliest, latest) date of birth allowed, both inclusive."""
    earliest = shift_years(as_on, -(max_age + relax_years)) + dt.timedelta(days=1)
    latest = shift_years(as_on, -min_age)
    return earliest, latest


_ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def _date(value: object, what: str) -> dt.date:
    # Strictly YYYY-MM-DD: Python 3.11+ fromisoformat also takes "20260801", which the JS port does not.
    try:
        if not _ISO_DATE.match(str(value)):
            raise ValueError
        return dt.date.fromisoformat(str(value))
    except ValueError:
        raise RulesError(f"{what}: {value!r} is not YYYY-MM-DD") from None


def validate(rules: dict) -> dict:
    """Raise RulesError unless the file is internally consistent."""
    rid = rules.get("id", "?")
    if rules.get("schema") != 1:
        raise RulesError(f"{rid}: schema {rules.get('schema')!r}")
    d = rules["dates"]
    if not _date(d["applyFrom"], "applyFrom") <= _date(d["applyTo"], "applyTo") <= _date(d["feeTo"], "feeTo"):
        raise RulesError(f"{rid}: application dates out of order")
    age = rules["age"]
    as_on = _date(age["asOn"], "age.asOn")
    for name, band in age["bands"].items():
        if name != f"{band['min']}-{band['max']}":
            raise RulesError(f"{rid}: band {name} has min/max {band['min']}-{band['max']}")
        want = born_window(as_on, band["min"], band["max"])
        got = (_date(band["bornFrom"], f"{name}.bornFrom"), _date(band["bornTo"], f"{name}.bornTo"))
        if got != want:
            raise RulesError(f"{rid}: band {name} says born {got[0]}..{got[1]}, "
                             f"formula gives {want[0]}..{want[1]}")
    relax = age["relaxation"]
    # PwBD codes are optional: some Notices (SI 2026) list none, and then nothing is assumed.
    required = ("SC", "ST", "OBC") + (("PwBD_OBC", "PwBD_SC", "PwBD_ST") if "PwBD" in relax else ())
    for key in required:
        if not isinstance(relax.get(key), int) or relax[key] < 0:
            raise RulesError(f"{rid}: relaxation {key} missing")
    for key, value in relax.items():
        if key not in RELAXATION_KEYS or not isinstance(value, int) or value < 0:
            raise RulesError(f"{rid}: relaxation {key}={value!r}")
    for stage in rules.get("examSchedule", []):
        if "from" in stage:
            if _date(stage["from"], "examSchedule.from") > _date(stage["to"], "examSchedule.to"):
                raise RulesError(f"{rid}: exam stage {stage['stage']} ends before it starts")
        elif not stage.get("when"):
            raise RulesError(f"{rid}: exam stage {stage.get('stage')} has no dates")
    sig = rules.get("upload", {}).get("signature")
    if sig and not 0 < sig["minKB"] < sig["maxKB"]:
        raise RulesError(f"{rid}: signature size range")
    _date(rules["education"]["asOn"], "education.asOn")
    if not rules.get("posts"):
        raise RulesError(f"{rid}: no posts")
    for p in rules["posts"]:
        if p["ageBand"] not in age["bands"]:
            raise RulesError(f"{rid}: {p['name']} uses unknown band {p['ageBand']}")
        if p["education"] not in EDUCATION_LEVELS:
            raise RulesError(f"{rid}: {p['name']} education {p['education']!r}")
        if not set(p.get("genders", GENDERS)) <= set(GENDERS) or not p.get("genders", GENDERS):
            raise RulesError(f"{rid}: {p['name']} genders {p.get('genders')!r}")
    return rules


def load(rules_id: str, rules_dir: str = RULES_DIR) -> dict:
    with open(os.path.join(rules_dir, f"{rules_id}.json"), encoding="utf-8") as f:
        return validate(json.load(f))


def load_all(rules_dir: str = RULES_DIR) -> list[dict]:
    names = sorted(n[:-5] for n in os.listdir(rules_dir) if n.endswith(".json"))
    return [load(n, rules_dir) for n in names]


def relaxation_years(rules: dict, category: str, pwbd: bool) -> int:
    relax = rules["age"]["relaxation"]
    if pwbd and "PwBD" in relax:
        return relax.get(f"PwBD_{category}", relax["PwBD"])
    return relax.get(category, 0)


def application_status(rules: dict, today: dt.date) -> str:
    d = rules["dates"]
    if today < _date(d["applyFrom"], "applyFrom"):
        return "upcoming"
    return "open" if today <= _date(d["applyTo"], "applyTo") else "closed"


def fee(rules: dict, profile: dict) -> int:
    exempt = set(rules["fee"]["exempt"])
    if (profile.get("gender") == "female" and "female" in exempt) or profile["category"] in exempt \
            or (profile.get("pwbd") and "PwBD" in exempt):
        return 0
    return rules["fee"]["amount"]


def check_profile(profile: dict) -> None:
    if profile.get("category") not in CATEGORIES:
        raise ValueError(f"category must be one of {CATEGORIES}")
    if profile.get("education") not in EDUCATION_LEVELS:
        raise ValueError(f"education must be one of {EDUCATION_LEVELS}")
    if profile.get("gender") not in (None, *GENDERS):
        raise ValueError(f"gender must be one of {GENDERS}")
    _date(profile.get("dob"), "dob")


def evaluate(rules: dict, profile: dict, today: dt.date) -> dict:
    """profile: dob 'YYYY-MM-DD', category UR/EWS/OBC/SC/ST, pwbd bool, education '10'/'12'/'graduate',
    appearing bool (final exam of that level taken, result not out yet), gender optional,
    otherCategory bool (ESM, govt employee, widow...: relaxations this module does not compute)."""
    check_profile(profile)
    dob = _date(profile["dob"], "dob")
    as_on = _date(rules["age"]["asOn"], "age.asOn")
    relax = relaxation_years(rules, profile["category"], bool(profile.get("pwbd")))
    edu_by = _date(rules["education"]["asOn"], "education.asOn")
    level = EDUCATION_LEVELS.index(profile["education"])

    posts = []
    for p in rules["posts"]:
        band = rules["age"]["bands"][p["ageBand"]]
        earliest, latest = born_window(as_on, band["min"], band["max"], relax)
        status, reasons = "eligible", []

        def worse(new: str, reason: str) -> None:
            nonlocal status
            if STATUSES.index(new) > STATUSES.index(status):
                status = new
            reasons.append(reason)

        if dob > latest:
            worse("ineligible", f"age_below_min:{band['min']}")
        elif dob < earliest:
            if profile.get("otherCategory"):
                worse("check", "age_other_relaxation")
            else:
                worse("ineligible", f"age_above_max:{band['max'] + relax}")
        if profile.get("pwbd") and "PwBD" not in rules["age"]["relaxation"]:
            worse("check", "pwbd_not_in_notice")
        genders = p.get("genders")
        if genders and profile.get("gender") not in genders:
            # A post for the other binary gender is out; "other" or not given is left to the Notice.
            if profile.get("gender") in ("male", "female"):
                worse("ineligible", f"gender_only:{'/'.join(genders)}")
            else:
                worse("check", f"gender_only:{'/'.join(genders)}")
        need = EDUCATION_LEVELS.index(p["education"])
        if level < need:
            worse("ineligible", f"education_needs:{p['education']}")
        elif level == need and profile.get("appearing"):
            if today > edu_by:
                worse("ineligible", f"result_needed_by:{edu_by.isoformat()}")
            elif rules["education"]["appearingAllowed"]:
                worse("check", f"result_needed_by:{edu_by.isoformat()}")
            else:  # the Notice neither allows nor forbids appearing candidates
                worse("check", f"appearing_not_stated:{edu_by.isoformat()}")
        for req in p.get("requires", []):
            worse("check", f"requires:{req}")
        posts.append({
            "name": p["name"], "org": p["org"], "payLevel": p["payLevel"], "group": p["group"],
            "status": status, "reasons": reasons, "notes": list(p.get("notes", [])),
            "bornFrom": earliest.isoformat(), "bornTo": latest.isoformat(),
        })
    counts = {s: sum(1 for p in posts if p["status"] == s) for s in STATUSES}
    return {
        "id": rules["id"],
        "application": application_status(rules, today),
        "fee": fee(rules, profile),
        "relaxationYears": relax,
        "counts": counts,
        "posts": posts,
    }
