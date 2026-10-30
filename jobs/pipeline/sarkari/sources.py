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
    "mppsc": {"short": "MPPSC", "name": "Madhya Pradesh Public Service Commission", "site": "https://mppsc.mp.gov.in", "state": "MP"},
    # The unified RRB site serves every board at its old address (rrbchennai.gov.in) and at rrb.indianrailways.gov.in.
    "rrb": {"short": "RRB", "name": "Railway Recruitment Boards", "site": "https://rrbchennai.gov.in", "state": None,
            "files": ["https://rrb.indianrailways.gov.in/"]},
    # CTET's files are on the NIC web CDN; "files" names its folder there, so only that folder counts.
    "ctet": {"short": "CTET", "name": "Central Teacher Eligibility Test (CBSE)", "site": "https://ctet.nic.in", "state": None,
             "files": ["https://cdnbbsr.s3waas.gov.in/s3443dec3062d0286986e21dc0631734c9/"]},
    "sbi": {"short": "SBI", "name": "State Bank of India", "site": "https://sbi.bank.in", "state": None},
    "ibps": {"short": "IBPS", "name": "Institute of Banking Personnel Selection", "site": "https://www.ibps.in", "state": None},
    "aai": {"short": "AAI", "name": "Airports Authority of India", "site": "https://www.aai.aero", "state": None},
    "iocl": {"short": "IOCL", "name": "Indian Oil Corporation Limited", "site": "https://iocl.com", "state": None},
    "becil": {"short": "BECIL", "name": "Broadcast Engineering Consultants India Limited", "site": "https://www.becil.com", "state": None},
    "bpsc": {"short": "BPSC", "name": "Bihar Public Service Commission", "site": "https://bpsc.bihar.gov.in", "state": "BR"},
    "hpsc": {"short": "HPSC", "name": "Haryana Public Service Commission", "site": "https://hpsc.gov.in", "state": "HR"},
    "ppsc": {"short": "PPSC", "name": "Punjab Public Service Commission", "site": "https://ppsc.gov.in", "state": "PB"},
    "psssb": {"short": "PSSSB", "name": "Subordinate Services Selection Board, Punjab", "site": "https://sssb.punjab.gov.in", "state": "PB"},
    "rpsc": {"short": "RPSC", "name": "Rajasthan Public Service Commission", "site": "https://rpsc.rajasthan.gov.in", "state": "RJ"},
    "rssb": {"short": "RSSB", "name": "Rajasthan Staff Selection Board", "site": "https://rssb.rajasthan.gov.in", "state": "RJ"},
    "dsssb": {"short": "DSSSB", "name": "Delhi Subordinate Services Selection Board", "site": "https://dsssb.delhi.gov.in", "state": "DL"},
    "uppsc": {"short": "UPPSC", "name": "Uttar Pradesh Public Service Commission", "site": "https://uppsc.up.nic.in", "state": "UP"},
    "rbi": {"short": "RBI", "name": "Reserve Bank of India", "site": "https://opportunities.rbi.org.in", "state": None},
    "crpf": {"short": "CRPF", "name": "Central Reserve Police Force", "site": "https://rect.crpf.gov.in", "state": None},
    "nta": {"short": "NTA", "name": "National Testing Agency", "site": "https://nta.ac.in", "state": None},
    "gpsc": {"short": "GPSC", "name": "Gujarat Public Service Commission", "site": "https://gpsc.gujarat.gov.in", "state": "GJ"},
    "ukpsc": {"short": "UKPSC", "name": "Uttarakhand Public Service Commission", "site": "https://psc.uk.gov.in", "state": "UK"},
    # UKSSSC's files are on the NIC web CDN; "files" names its folder there.
    "uksssc": {"short": "UKSSSC", "name": "Uttarakhand Subordinate Service Selection Commission", "site": "https://sssc.uk.gov.in", "state": "UK",
               "files": ["https://cdnbbsr.s3waas.gov.in/s34ffd0e19d2069412274bd3025b0e176c/"]},
    "osssc": {"short": "OSSSC", "name": "Odisha Sub-ordinate Staff Selection Commission", "site": "https://www.osssc.gov.in", "state": "OD"},
    "kpsc": {"short": "KPSC", "name": "Karnataka Public Service Commission", "site": "https://kpsc.karnataka.gov.in", "state": "KA"},
    "tntrb": {"short": "TN TRB", "name": "Teachers Recruitment Board, Tamil Nadu", "site": "https://www.trb.tn.gov.in", "state": "TN",
              "files": ["https://trb.tn.gov.in/"]},
    "indiapost": {"short": "India Post", "name": "India Post (Department of Posts)", "site": "https://www.indiapost.gov.in", "state": None},
    "pnb": {"short": "PNB", "name": "Punjab National Bank", "site": "https://www.pnb.bank.in", "state": None},
    "bob": {"short": "BOB", "name": "Bank of Baroda", "site": "https://bankofbaroda.bank.in", "state": None},
    "canara": {"short": "Canara Bank", "name": "Canara Bank", "site": "https://www.canarabank.bank.in", "state": None},
    "iob": {"short": "IOB", "name": "Indian Overseas Bank", "site": "https://www.iob.bank.in", "state": None},
    "nhpc": {"short": "NHPC", "name": "NHPC Limited", "site": "https://www.nhpcindia.com", "state": None},
    "bpcl": {"short": "BPCL", "name": "Bharat Petroleum Corporation Limited", "site": "https://www.bharatpetroleum.in", "state": None},
    "pgcil": {"short": "POWERGRID", "name": "Power Grid Corporation of India", "site": "https://www.powergrid.in", "state": None},
    "tgpsc": {"short": "TGPSC", "name": "Telangana Public Service Commission", "site": "https://websitenew.tgpsc.gov.in", "state": "TG"},
    "tnpsc": {"short": "TNPSC", "name": "Tamil Nadu Public Service Commission", "site": "https://tnpsc.gov.in", "state": "TN"},
    "itbp": {"short": "ITBP", "name": "Indo-Tibetan Border Police", "site": "https://recruitment.itbpolice.nic.in", "state": None},
    "esic": {"short": "ESIC", "name": "Employees' State Insurance Corporation", "site": "https://www.esic.gov.in", "state": None},
    "nabard": {"short": "NABARD", "name": "National Bank for Agriculture and Rural Development", "site": "https://www.nabard.org", "state": None},
    "ntpc": {"short": "NTPC", "name": "NTPC Limited", "site": "https://careers.ntpc.co.in", "state": None},
    "niacl": {"short": "NIACL", "name": "The New India Assurance Co. Ltd.", "site": "https://www.newindia.co.in", "state": None},
    "aiims": {"short": "AIIMS", "name": "All India Institute of Medical Sciences, New Delhi", "site": "https://www.aiims.edu", "state": None},
    "bssc": {"short": "BSSC", "name": "Bihar Staff Selection Commission", "site": "https://bssc.bihar.gov.in", "state": "BR"},
    "cgpsc": {"short": "CGPSC", "name": "Chhattisgarh Public Service Commission", "site": "https://psc.cg.gov.in", "state": "CG"},
    "hppsc": {"short": "HPPSC", "name": "Himachal Pradesh Public Service Commission", "site": "https://hppsc.hp.gov.in", "state": "HP"},
    "appsc": {"short": "APPSC", "name": "Andhra Pradesh Public Service Commission", "site": "https://portal-psc.ap.gov.in", "state": "AP",
              "files": ["https://psc.ap.gov.in/"]},
    "keralapsc": {"short": "Kerala PSC", "name": "Kerala Public Service Commission", "site": "https://www.keralapsc.gov.in", "state": "KL"},
}

# What a family is, for "jobs by sector" (exam_families.json "sectors").
SECTORS = ("ssc", "police", "defence", "railway", "bank", "psu", "science", "state", "teaching")


def host(source: str) -> str:
    return SOURCES.get(source, SOURCES["ssc"])["site"].split("://", 1)[1]


def official(url: str) -> bool:
    """True for a link on one of the recruiters' own sites (never a look-alike such as ssc.gov.in.evil.example)."""
    return any(url.startswith(s["site"] + "/") or any(url.startswith(f) for f in s.get("files", ())) for s in SOURCES.values())
