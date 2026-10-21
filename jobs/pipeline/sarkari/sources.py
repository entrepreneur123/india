"""Every recruiter the app reads, in one place: who it is, its official site, and where it hires.

exam_families.json says which recruiter publishes a family ("source", default ssc); the website,
Telegram texts and exam pages take names and links from here, so a new board is one entry.
"state" is the state whose jobs the board fills (None: all-India). States use the codes of
ISO 3166-2:IN (UP, HR, BR, ...), which the app names in every language.
"""
from __future__ import annotations

SOURCES: dict[str, dict] = {
    "ssc": {"short": "SSC", "name": "Staff Selection Commission", "site": "https://ssc.gov.in", "state": None},
    "upprpb": {"short": "UPPRPB", "name": "UP Police Recruitment & Promotion Board", "site": "https://uppbpb.gov.in", "state": "UP"},
    "hssc": {"short": "HSSC", "name": "Haryana Staff Selection Commission", "site": "https://hssc.gov.in", "state": "HR"},
    "csbc": {"short": "CSBC", "name": "Central Selection Board of Constable, Bihar", "site": "https://csbc.bihar.gov.in", "state": "BR"},
    "bpssc": {"short": "BPSSC", "name": "Bihar Police Sub-ordinate Service Commission", "site": "https://bpssc.bihar.gov.in", "state": "BR"},
    "isro": {"short": "ISRO", "name": "Indian Space Research Organisation", "site": "https://www.isro.gov.in", "state": None},
    "ongc": {"short": "ONGC", "name": "Oil and Natural Gas Corporation", "site": "https://ongcindia.com", "state": None},
}

# What a family is, for "jobs by sector" (exam_families.json "sectors").
SECTORS = ("ssc", "police", "defence", "railway", "bank", "psu", "science", "state", "teaching")


def host(source: str) -> str:
    return SOURCES.get(source, SOURCES["ssc"])["site"].split("://", 1)[1]


def official(url: str) -> bool:
    """True for a link on one of the recruiters' own sites (never a look-alike such as ssc.gov.in.evil.example)."""
    return any(url.startswith(s["site"] + "/") for s in SOURCES.values())
