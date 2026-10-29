"""What a notice is about, from its headline.

SSC posts everything (new recruitments, results, answer keys, corrigenda) to one notice
board, so the kind has to be read from the headline. The rules below were written against
the real headlines in tests/fixtures (SSC, 20-10-2026). An unknown headline falls back to
"notice", which is always safe: the user still sees it, it just isn't highlighted.
"""
from __future__ import annotations

import re
import unicodedata

KINDS = ("recruitment", "amendment", "result", "answer_key", "admit_card", "vacancy", "schedule", "notice")

# Order matters: "Addendum- Notice of Junior Engineer Examination" is an amendment, not a
# new recruitment; "Uploading of Final Answer Key ... and Marks" is an answer key, not a result.
_EN_RULES: list[tuple[str, re.Pattern]] = [
    ("amendment", re.compile(
        r"\b(addendum|corrigendum|re-?opening|extension of|revised dates?|postpone|cancell?ation)", re.I)),
    ("recruitment", re.compile(
        r"^(notice of\b|notification for recruitment|recruitment of\b)", re.I)),
    ("answer_key", re.compile(r"\banswer[- ]?keys?\b", re.I)),
    ("admit_card", re.compile(r"\b(admission certificate|admit card|city of examination)", re.I)),
    ("result", re.compile(
        r"\b(result|shortlist|marks\b|tentative allocation|frta|list of .*candidates|selected candidates)", re.I)),
    ("vacancy", re.compile(r"\bvacanc(y|ies)\b", re.I)),
    ("schedule", re.compile(r"\bschedule of\b", re.I)),
]

_HI_RULES: list[tuple[str, re.Pattern]] = [
    ("amendment", re.compile(r"(शुद्धिपत्र|परिशिष्ट|स्थगित|रद्द)")),
    ("recruitment", re.compile(r"^विज्ञप्ति\s*[:\-–]|,\s*विज्ञप्ति$")),
    ("answer_key", re.compile(r"उत्तर\s*कुंज[ीि]")),  # कुंजी, कुंजियों
    ("admit_card", re.compile(r"(प्रवेश\s*प्रमाण|परीक्षा\s*शहर)")),
    ("result", re.compile(r"(परिणाम|शॉर्टलिस्ट|आवंटन)")),
    ("vacancy", re.compile(r"रिक्ति")),
]

# Exam families, keyed by SSC's own examCode (from /api/admin/5.1/allExams).
# Used only when the record carries no examId.
_EXAM_RULES: list[tuple[str, re.Pattern]] = [
    ("LDCE", re.compile(r"(limited|annual) departmental|departmental exam|\bLDCE\b|(सीमित|वार्षिक) विभागीय", re.I)),
    ("CGL", re.compile(r"combined graduate level|संयुक्त स्नातक स्तरीय", re.I)),
    ("CHSL", re.compile(r"combined higher secondary|उच्चतर माध्यमिक", re.I)),
    ("MTS", re.compile(r"multi[- ]?tasking|\bMTS\b|मल्टी टास्किंग", re.I)),
    ("CTGD", re.compile(r"constable \(gd\)|कांस्टेबल \((जीडी|सामान्य ड्यूट[ीि])\)", re.I)),
    ("CAPF", re.compile(r"(sub[- ]?inspector|\bSI) in (the )?delhi police|उप-निरीक्षक", re.I)),
    ("JE", re.compile(r"junior engineer|कनिष्ठ अभियंता", re.I)),
    ("STENO", re.compile(r"stenographer grade|आशुलिपिक|स्टेनोग्राफर ग्रेड", re.I)),
    ("JHT", re.compile(r"hindi translators?|हिंदी अनुवादक", re.I)),
    ("RHQ", re.compile(r"selection posts?\b|चयन पद", re.I)),
    ("DP", re.compile(r"in (the )?delhi police examination|दिल्ली पुलिस में", re.I)),
]

# SSC's examCodes that belong to one family above. "OTHERS" (INET, coast guard, ...) has none.
_FAMILY = {
    "DPCD": "DP", "CEDP": "DP", "DPCE": "DP", "DPHCT": "DP", "DPHCM": "DP",
    "STENOLDC": "LDCE", "DOPTJSA": "LDCE", "DOPTSSA": "LDCE", "DEPARTMENTAL EXAMS": "LDCE",
    "RHQLDK": "RHQ", "OTHERS": None,
}
# Other boards' families (upprpb.py, hssc.py, listboards.py), never read from SSC headlines.
EXAM_FAMILIES = tuple(code for code, _ in _EXAM_RULES) + ("UPPC", "UPSI", "UPHG", "UPP", "HSSC", "CSBC", "BPSSC", "ISRO", "ONGC", "MPPSC", "CTET", "RRB",
                                                              "SBI", "IBPS", "AAI", "IOCL", "BECIL", "BPSC", "HPSC", "PPSC", "PSSSB",
                                                              "RPSC", "RSSB", "DSSSB", "KERALAPSC", "UPPSC", "RBI", "CRPF", "NTA")


def family_of(exam_code: str) -> str | None:
    code = exam_code.strip().upper()
    return _FAMILY[code] if code in _FAMILY else (code if code in EXAM_FAMILIES else None)


_YEAR = re.compile(r"(?:examinations?|परीक्षा)\s*[,:\-–]?\s*(20\d{2})\b|phase-[ivxl]+/(20\d{2})\b", re.I)


def normalize(text: str) -> str:
    """NFC, no zero-width joiners (SSC's Hindi has U+200C/U+200D inside words), single spaces."""
    text = unicodedata.normalize("NFC", text)
    text = text.replace("‌", "").replace("‍", "").replace(" ", " ")
    return re.sub(r"\s+", " ", text).strip()


def is_hindi(text: str) -> bool:
    letters = [c for c in text if c.isalpha()]
    return bool(letters) and sum("ऀ" <= c <= "ॿ" for c in letters) * 2 > len(letters)


def kind_of(headline: str) -> str:
    text = normalize(headline)
    for kind, pattern in (_HI_RULES if is_hindi(text) else _EN_RULES):
        if pattern.search(text):
            return kind
    return "notice"


def exam_of(headline: str) -> str | None:
    text = normalize(headline)
    for code, pattern in _EXAM_RULES:
        if pattern.search(text):
            return code
    return None


def year_of(headline: str) -> int | None:
    m = _YEAR.search(normalize(headline))
    return int(m.group(1) or m.group(2)) if m else None
