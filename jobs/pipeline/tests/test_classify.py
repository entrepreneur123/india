import glob
import json
import os

import pytest

from sarkari import classify

FIX = os.path.join(os.path.dirname(__file__), "fixtures")


def _headline(fixture: str, rid: str) -> str:
    data = json.load(open(os.path.join(FIX, fixture), encoding="utf-8"))["data"]
    return next(r["headline"] for r in data if r["id"] == rid)


# Real headlines from ssc.gov.in (20-10-2026) -> (kind, exam, year)
@pytest.mark.parametrize("headline,kind,exam,year", [
    ("Notice of Combined Higher Secondary (10+2) Level Examination, 2026", "recruitment", "CHSL", 2026),
    ("Notice of Sub-Inspector in Delhi Police and Central Armed Police Forces Examination, 2026.", "recruitment", "CAPF", 2026),
    ("Addendum- Notice of Junior Engineer Examination, 2026", "amendment", "JE", 2026),
    ("Important Notice regarding re-opening of window for submission of online application form for Combined "
     "Graduate Level Examination, 2026", "amendment", "CGL", 2026),
    ("Cancellation Notice for the post of  'Junior Technical Officer', Post Code KK13526, advertised under "
     "Selection Posts Examination, Phase-XIV/2026", "amendment", "RHQ", 2026),
    ("Phase-XIV/2026/Selection Posts Examination - Uploading of Candidate’s Response Sheet(s) along with "
     "Tentative Answer Keys and inviting challenges thereon", "answer_key", "RHQ", 2026),
    ("Grade 'C' Stenographers Limited Departmental Competitive Examination, 2025 : Uploading of Final Answer Key "
     "and Final Marks", "answer_key", "LDCE", 2025),
    ("Information regarding the city of examination and Admission Certificate for the candidates of Combined "
     "Graduate level Examination, 2026", "admit_card", "CGL", 2026),
    ("Combined Higher Secondary (10+2) Level Examination, 2025: Declaration of Final Result", "result", "CHSL", 2025),
    ("Tentative Vacancy of Combined Graduate Level Examination, 2026 as on 24.09.2026", "vacancy", "CGL", 2026),
    ("Important Notice - Schedule of Examinations", "schedule", None, None),
    ("NOTIFICATION FOR RECRUITMENT OF ENROLLED PERSONS IN THE INDIAN COAST GUARD", "recruitment", None, None),
    ("Advisory to Candidates and Exam Functionaries for Computer Based Examinations of SSC", "notice", None, None),
    ("Constable (Executive) Male and Female in Delhi Police Examination, 2025– Declaration of result of Computer "
     "Based Examination", "result", "DP", 2025),
])
def test_english_headlines(headline, kind, exam, year):
    assert (classify.kind_of(headline), classify.exam_of(headline), classify.year_of(headline)) == (kind, exam, year)


@pytest.mark.parametrize("rid,kind,exam,year", [
    ("aitj44fcxan00jcfqlcph", "recruitment", "CHSL", 2026),  # विज्ञप्ति: संयुक्‍त उच्‍चतर माध्‍यमिक ... (with ZWJ)
    ("n9fb3p4hpomwv3u6oeenb", "recruitment", "JE", 2026),    # विज्ञप्ति: कनिष्ठ अभियंता परीक्षा, 2026
    ("n7axe9edwsrsf81ad9x1k", "recruitment", "JHT", 2026),   # विज्ञप्ति - संयुक्त हिंदी अनुवादक परीक्षा, 2026
    ("4h6vyk7527zzxzy04mupw", "answer_key", "JE", 2025),     # ... अंतिम उत्तर कुंजियों ...
    ("mljr2gpxcybct8xvy0epz", "amendment", "LDCE", 2025),    # परिशिष्ट - ...
    ("qlj2oyhgqrkbi2ae5wkba", "result", "LDCE", 2024),       # वार्षिक विभागीय टंकण परीक्षा, 2024 के परिणाम
])
def test_hindi_headlines(rid, kind, exam, year):
    h = _headline("notice_boards_hindi_p1.json", rid)
    assert classify.is_hindi(h)
    assert (classify.kind_of(h), classify.exam_of(h), classify.year_of(h)) == (kind, exam, year)


def test_normalize_drops_zero_width_joiners_and_extra_spaces():
    assert classify.normalize(" संयुक्‍त  उच्‍चतर\n") == "संयुक्त उच्चतर"


def test_every_fixture_headline_classifies():
    seen = 0
    for path in glob.glob(os.path.join(FIX, "*_p[12].json")):
        for r in json.load(open(path, encoding="utf-8"))["data"]:
            assert classify.kind_of(r["headline"]) in classify.KINDS
            seen += 1
    assert seen > 400


@pytest.mark.parametrize("code,family", [
    ("CGL", "CGL"), ("DPCE", "DP"), ("STENOLDC", "LDCE"), ("DEPARTMENTAL EXAMS", "LDCE"),
    ("RHQLDK", "RHQ"), ("OTHERS", None), ("NEWCODE", None),
])
def test_family_of(code, family):
    assert classify.family_of(code) == family
