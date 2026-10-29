"""More state recruiters' notice boards (same Board model as listboards.py).

Each board below was written against its page as read from the India server (29-10-2026;
tests/fixtures/<source>.html; gpsc.html is only the news list, cut unchanged from the 436 KB page):

  gpsc    Gujarat Public Service Commission                 gpsc.gujarat.gov.in   "News": <time datetime='2026-10-29'> <a href='/newseventdetail?NewsID=N'>headline</a> + ATTACHMENT pdf
  ukpsc   Uttarakhand Public Service Commission             psc.uk.gov.in         "Announcements": <a href=section page><span class="announcement-date">29-10-2026</span> headline <span class="under-section">( Results )</span>
  uksssc  Uttarakhand Subordinate Service Selection Comm.   sssc.uk.gov.in        two lists: <a download href="<s3waas CDN>/uploads/2026/10/x.pdf">headline</a> <span class="date">25 Oct, 2026</span>
  osssc   Odisha Sub-ordinate Staff Selection Commission    www.osssc.gov.in      "Recruitment News": calendar (Oct / 27 / 2026) + headline "Notification No. ... dated - 27.10.2026 ----- ..."
  kpsc    Karnataka Public Service Commission               kpsc.karnataka.gov.in "Whats New" / "Press Release": <a href=".../uploads/x_1792752290.pdf"><li> headline (Kannada)</li></a>
  tntrb   Teachers Recruitment Board, Tamil Nadu            www.trb.tn.gov.in     "WHATS NEW": <a href=...><span> 27-10-2026 headline </span></a>

Links the boards' pages make to other hosts (exam portals, a "demo" host, an S3 bucket) are left out,
as everywhere else. Where a page gives no link of its own for a notice (OSSSC's are ASP.NET postbacks),
the notice links the board's page.
"""
from __future__ import annotations

import datetime as dt
import html
import re
import urllib.parse
from dataclasses import dataclass
from typing import Iterator

from .listboards import _TAG, Board, _dmy, kind_of
from .ssc import IST


@dataclass(frozen=True)
class _Board(Board):
    also: tuple[str, ...] = ()  # the board's own site under a second name (TRB links both www. and bare host)

    def url(self, href: str) -> str:
        url = urllib.parse.urljoin(self.NOTICE_PAGE, html.unescape(href.strip()))
        p = urllib.parse.urlsplit(url)
        if p.scheme == "https" and f"https://{p.netloc}" in self.also:
            return urllib.parse.urlunsplit((p.scheme, p.netloc, urllib.parse.quote(urllib.parse.unquote(p.path), safe="/"),
                                            p.query, ""))
        return super().url(href)


def _text(fragment: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _day_mon_year(raw: str, _title: str = "") -> dt.date:
    """'25 Oct, 2026' / '27 Oct 2026'."""
    return dt.datetime.strptime(re.sub(r"[\s,]+", " ", raw.strip()), "%d %b %Y").date()


_TENDER = re.compile(r"\b(e-?tenders?|tenders?|quotations?)\b|ಟೆಂಡರ್|ಕೊಟೇಶನ್", re.I)


# GPSC: the home page's news list (newest first, back to 2021):
#   <li class='news-item'> <time class='news-date-container' datetime='2026-10-29'> ... </time>
#   <div class='news-content'> ... <a href='/newseventdetail?NewsID=29094' title='...' target='_blank'> headline </a>
#   <small ...> Click here to see <a href='/Documents/AdvertismentDocument/LECI-10-202526c.pdf' class='attachment-link' ...> ATTACHMENT </a>
# The item links the attachment when there is one (one PDF can serve several headlines, e.g. one final key
# for three advertisements), else the news page.
_GPSC_ITEM = re.compile(r"<li class='news-item'>(.*?)</li>", re.S)
_GPSC_DAY = re.compile(r"<time class='news-date-container' datetime='(\d{4})-(\d{2})-(\d{2})'>")
_GPSC_NEWS = re.compile(r"<a href='(/newseventdetail\?NewsID=\d+)'[^>]*>(.*?)</a>", re.S)
_GPSC_FILE = re.compile(r"<a href='([^']+)'\s+class='attachment-link'")


def _gpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for item in _GPSC_ITEM.findall(page):
        day, news, attachment = _GPSC_DAY.search(item), _GPSC_NEWS.search(item), _GPSC_FILE.search(item)
        if not news:
            continue
        yield (f"{day.group(3)}-{day.group(2)}-{day.group(1)}" if day else ""), (attachment or news).group(1), news.group(2)


# GPSC's headlines lead with the stage: "Final Key (Prelim) - 2/2026-27 ...", "Interview Schedule - 113/2025-26 ...",
# "Final Stage - 106/2025-26 (...)" (the final result), "Eligibility List (Interview) - ...".
_GPSC_KINDS = [
    ("admit_card", re.compile(r"\bcall ?letters?\b", re.I)),
    ("answer_key", re.compile(r"\b(final|provisional) (answer ?)?key\b|\banswer ?key", re.I)),
    ("notice", re.compile(r"^important (notice|instructions)\b", re.I)),  # "... counting of Common Marks" is no result
    ("result", re.compile(r"^(final stage|eligibility list|ineligibility list|list of eligible candidates|recommendation)\b", re.I)),
    ("schedule", re.compile(r"\binterview (schedule|program)|\bexam(ination)? schedule\b|\bcalendar for the examinations\b", re.I)),
    ("recruitment", re.compile(r"^newspaper format advertisement\b", re.I)),
]


def _gpsc_kind(title: str) -> str:
    return next((kind for kind, pattern in _GPSC_KINDS if pattern.search(title)), None) or kind_of(title)


# UKPSC: the home page's "Announcements" list (a marquee above repeats its newest entries, dates commented out):
#   <ul class="list announcment-list"> <li><a href="https://psc.uk.gov.in/candidate-corner/results">
#   <span class="new-announcement">..</span><span class="announcement-date">27-10-2026</span> headline
#   <span class="under-section">( Results )</span></a></li>
# Each entry links the section page that holds its files (Results, Answer Key, Interview, ...), so the
# section is kept at the end of the headline. The office's tenders are left out.
_UKPSC_LIST = re.compile(r'<ul class="list announcment-list">(.*?)</ul>', re.S)
_UKPSC = re.compile(r'<li><a href="([^"]*)">(.*?)</a></li>', re.S)
_UKPSC_DAY = re.compile(r'<span class="announcement-date">\s*([^<]*?)\s*</span>')
_UKPSC_SECTION = re.compile(r'<span class="under-section">\s*\(\s*([^<]*?)\s*\)\s*</span>')
_UKPSC_DROP = re.compile(r'<span class="(?:new-announcement|announcement-date|under-section)">.*?</span>', re.S)
UKPSC_SECTIONS = {"Results": "result", "Answer Key": "answer_key", "Admit Card": "admit_card", "Interview": "schedule"}


def _ukpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for block in _UKPSC_LIST.findall(page):
        for href, inner in _UKPSC.findall(block):
            day, section = _UKPSC_DAY.search(inner), _UKPSC_SECTION.search(inner)
            if section and section.group(1) == "Tenders":
                continue
            headline = _text(_UKPSC_DROP.sub(" ", inner)) + (f" ({section.group(1)})" if section else "")
            yield day.group(1) if day else "", href, html.escape(headline, quote=False)


# A new advertisement reads "... Examination – 2026 --Notification, Detailed Notification, Check List ... (Recruitment Notification)".
_UKPSC_NEW = re.compile(r"\bexam(ination)?\s*[-–]\s*20\d{2}\s*--\s*notification,\s*detailed notification\b", re.I)


def _ukpsc_kind(title: str) -> str:
    m = re.search(r"\(([^()]*)\)$", title)
    kind = kind_of(title)
    if kind == "amendment":
        return kind
    if m and m.group(1) in UKPSC_SECTIONS:  # "Selection Result, Cut Off Marks, Final Answer Key ... (Results)" is a result
        return UKPSC_SECTIONS[m.group(1)]
    return "recruitment" if _UKPSC_NEW.search(title) else kind


# UKSSSC (an S3WaaS site): two lists on the home page, "Recuritment & Notification" and "Result & Answer Keys":
#   <div class="gen-list ..."> <h2 ...>Result &amp; Answer Keys</h2> <ul> <li>
#   <a download href="https://cdnbbsr.s3waas.gov.in/s34ffd0e19d2069412274bd3025b0e176c/uploads/2026/10/202610231635461748.pdf"
#   title="download">headline</a> <span class="date">23 Oct, 2026</span> </li>
# The files sit on the shared S3WaaS CDN, so only this board's own folder there is accepted. Its other lists
# (application portals, quick links) carry no date.
UKSSSC_FILES = "https://cdnbbsr.s3waas.gov.in/s34ffd0e19d2069412274bd3025b0e176c/"
_UKSSSC_LIST = re.compile(r'<div\s+class="gen-list[^"]*"[^>]*>\s*<h2[^>]*>(.*?)</h2>\s*<ul>(.*?)</ul>', re.S)
_UKSSSC = re.compile(r'<li>\s*<a\b([^>]*)>(.*?)</a>\s*<span class="date">\s*([^<]*?)\s*</span>\s*</li>', re.S)
_HREF = re.compile(r'href="([^"]*)"')
_CDN = "https://cdnbbsr.s3waas.gov.in/"


def _uksssc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for _heading, block in _UKSSSC_LIST.findall(page):
        for attrs, headline, day in _UKSSSC.findall(block):
            h = _HREF.search(attrs)
            href = h.group(1) if h else ""
            if href.startswith(_CDN) and not href.startswith(UKSSSC_FILES + "uploads/"):
                continue  # another site's folder on the shared CDN
            yield day, href, headline


# UKSSSC's headlines are Hindi with English words in brackets: "...प्रवेश पत्र(ADMIT CARD)...", "...उत्तरकुंजी(Answer Keys)...",
# "...चयन संस्तुति की सूची..." (recommendation list), "...औपबंधिक श्रेष्ठता..." (provisional merit), "...स्थगन..." (postponement),
# "...अभिलेख सन्निरीक्षा की सूची / की तिथि..." (document scrutiny list / date).
_UKSSSC_KINDS = [
    ("amendment", re.compile(r"स्थगन|स्थगित|संशोधन|शुद्धिपत्र|postpone", re.I)),
    ("answer_key", re.compile(r"उत्तर\s*कुंजी|answer\s*keys?", re.I)),
    ("admit_card", re.compile(r"प्रवेश\s*पत्र|admit\s*card", re.I)),
    ("result", re.compile(r"परिणाम|चयन\s*संस्तुति|श्रेष्ठता|सन्निरीक्षा\s*की\s*सूची|result|recomm?endation", re.I)),
    ("schedule", re.compile(r"की\s*तिथि|तिथि\s*हेतु|scrutiny\s*date", re.I)),
    ("recruitment", re.compile(r"सीधी\s*भर्ती\s*के\s*विज्ञापन", re.I)),
]


def _uksssc_kind(title: str) -> str:
    return next((kind for kind, pattern in _UKSSSC_KINDS if pattern.search(title)), "notice")


# OSSSC (ASP.NET): the home page's "Recruitment News" grid, ten newest:
#   <div id="generic_masterpage1_gv_UR_div_cld_0" class="listing_date newflash"> <div class="calendar_head">
#   <span class="month">Oct</span> <span class="date"><h4>27</h4></span> </div> <span class="year">2026</span> </div>
#   </td><td> <a ... href="javascript:__doPostBack(&#39;ctl00$generic_masterpage1$gv_UR$ctl02$lnk_btn_UR&#39;,&#39;&#39;)">
#   Notification No. IIE-96/2026-3183/OSSSC dated - 27.10.2026 ----- Physical Test (Phase-II) for ...</a>
# Each notice opens by a form post, not a link, so the item links the home page that lists it.
_OSSSC = re.compile(r'<div id="generic_masterpage1_gv_UR_div_cld_\d+"[^>]*>\s*<div class="calendar_head"\s*>\s*'
                    r'<span class="month">\s*([A-Za-z]{3})\s*</span>\s*<span class="date">\s*<h4>\s*(\d{1,2})\s*</h4>\s*</span>\s*</div>\s*'
                    r'<span class="year">\s*(\d{4})\s*</span>\s*</div>\s*</td>\s*<td>\s*'
                    r'<a\b[^>]*id="generic_masterpage1_gv_UR_lnk_btn_UR_\d+"[^>]*>(.*?)</a>', re.S)


def _osssc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for month, day, year, headline in _OSSSC.findall(page):
        yield f"{day} {month} {year}", "", headline


_OSSSC_KINDS = [
    ("amendment", re.compile(r"\b(extension of|revised dates?|postpone|reschedul|corrigendum|addendum|cancell?ation)", re.I)),
    ("answer_key", re.compile(r"\banswer[- ]?keys?\b", re.I)),
    ("admit_card", re.compile(r"\badmission letter|\badmit card", re.I)),
    ("result", re.compile(r"\b(result|screening list|selection list|merit list|select list)\b", re.I)),
    ("schedule", re.compile(r"\b(physical test|conduct of|counselling|document verification|written test)\b", re.I)),
    ("recruitment", re.compile(r"\badvertisement\b|\binviting applications\b", re.I)),
]


def _osssc_kind(title: str) -> str:
    about = title.split("-----", 1)[-1]  # after "Notification No. ... dated - DD.MM.YYYY -----"
    return next((kind for kind, pattern in _OSSSC_KINDS if pattern.search(about)), "notice")


# KPSC: the home page's "Whats New" and "Press Release" scrollers (no dates shown):
#   <a href="https://kpsc.karnataka.gov.in/uploads/DE2026VivaPressNote_1792752290.pdf"><li><i ...></i> headline</li> </a>
# Most entries link a copy of the site on demo.karnataka.gov.in (left out: another host, and a demo one);
# placeholder entries ("Lorem Ipsum", href="#.") are left out too. A file uploaded to the board's own site
# carries its upload time in its name (Unix seconds: "..._1792752290.pdf" = 23-10-2026 16:14 IST; files named
# "..._DATED_29-10-2026_..." / "...as on 17-10-2026_..." carry stamps of that very day), which dates the entry;
# its other pages carry none, so they are dated the day they are first seen (undated).
_KPSC_TAB = re.compile(r'<div class="tab-pane[^"]*" id="tab[13]" role="tabpanel">(.*?)</ul>', re.S)
_KPSC = re.compile(r'<a href="([^"]*)"\s*>\s*<li>(.*?)</li>\s*</a>', re.S)
_KPSC_STAMP = re.compile(r"^https://kpsc\.karnataka\.gov\.in/uploads/[^?#]*_(1[5-9]\d{8})\.pdf$", re.I)


def _kpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for block in _KPSC_TAB.findall(page):
        for href, headline in _KPSC.findall(block):
            text = _text(headline)
            if href.startswith("#") or "lorem ipsum" in text.lower() or _TENDER.search(href + " " + text):
                continue
            m = _KPSC_STAMP.search(href)
            yield (dt.datetime.fromtimestamp(int(m.group(1)), IST).strftime("%d-%m-%Y") if m else ""), href, headline


# Kannada: ಫಲಿತಾಂಶ result, ಆಯ್ಕೆ ಪಟ್ಟಿ selection list, ಪ್ರವೇಶ ಪತ್ರ admission ticket, ವೇಳಾಪಟ್ಟಿ / ವೇಳಾ ಪಟ್ಟಿ time table,
# ಕೀ ಉತ್ತರ answer key, ತಿದ್ದುಪಡಿ correction, ಹಿಂಪಡೆ withdrawn, ಅಧಿಸೂಚನೆ notification.
_KPSC_KINDS = [
    ("amendment", re.compile(r"ತಿದ್ದುಪಡಿ|ಹಿಂಪಡೆ|corrigendum|extension", re.I)),
    ("answer_key", re.compile(r"ಕೀ\s*ಉತ್ತರ|answer\s*key", re.I)),
    ("admit_card", re.compile(r"ಪ್ರವೇಶ\s*ಪತ್ರ|admit\s*card|admission\s*ticket", re.I)),
    ("result", re.compile(r"ಫಲಿತಾಂಶ|ಆಯ್ಕೆ\s*ಪಟ್ಟಿ|ಅರ್ಹತಾ\s*ಪಟ್ಟಿ|result|select\s*list", re.I)),
    ("schedule", re.compile(r"ವೇಳಾ\s*ಪಟ್ಟಿ|time\s*table", re.I)),
]


def _kpsc_kind(title: str) -> str:
    return next((kind for kind, pattern in _KPSC_KINDS if pattern.search(title)), None) or kind_of(title)


# TRB Tamil Nadu: the home page's "WHATS NEW" table, each headline opening with its day:
#   <div class="datesty"> <div class="date1"> 27 </div> <div class="month1">Oct </div> </div> </td> <td>
#   <a href="https://trb.tn.gov.in/syllabus1.php?language=LG-1&status=Active" target="_blank" class="viewlink ...">
#   <span>  27-10-2026 TAMIL NADU TEACHER ELIGIBILITY TEST - Revised Syllabus </span><img ...> </a>
# The page links its own site both as www.trb.tn.gov.in (relative links) and trb.tn.gov.in; files on an
# S3 bucket are left out. The headline is kept without its leading date.
_TRB = re.compile(r'<a\s+href="([^"]*)"[^>]*class="viewlink[^"]*"[^>]*>\s*<span>\s*(\d{1,2}-\d{1,2}-\d{4})\s*-?\s*(.*?)</span>', re.S)


def _trb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, day, headline in _TRB.findall(page):
        yield day, href, headline


def _trb_kind(title: str) -> str:
    if re.search(r"\bprovisional selection\b|\bselection list\b|\bcertificates?\b", title, re.I):
        return "result"
    if re.search(r"\b(revised )?syllabus\b|\bpress news\b", title, re.I):
        return "notice"
    if re.search(r"- notification$", title, re.I):
        return "recruitment"
    return kind_of(title)


GPSC = Board("gpsc", "https://gpsc.gujarat.gov.in", "https://gpsc.gujarat.gov.in/", "GPSC", _gpsc_entries, _dmy,
             kind=_gpsc_kind)
UKPSC = Board("ukpsc", "https://psc.uk.gov.in", "https://psc.uk.gov.in/", "UKPSC", _ukpsc_entries, _dmy, kind=_ukpsc_kind)
UKSSSC = Board("uksssc", "https://cdnbbsr.s3waas.gov.in", "https://sssc.uk.gov.in/", "UKSSSC", _uksssc_entries,
               _day_mon_year, kind=_uksssc_kind)
OSSSC = Board("osssc", "https://www.osssc.gov.in", "https://www.osssc.gov.in/", "OSSSC", _osssc_entries, _day_mon_year,
              kind=_osssc_kind)
KPSC = Board("kpsc", "https://kpsc.karnataka.gov.in", "https://kpsc.karnataka.gov.in/", "KPSC", _kpsc_entries, _dmy,
             min_items=3, kind=_kpsc_kind, undated=True)
TNTRB = _Board("tntrb", "https://www.trb.tn.gov.in", "https://www.trb.tn.gov.in/", "TNTRB", _trb_entries, _dmy,
               min_items=3, kind=_trb_kind, also=("https://trb.tn.gov.in",))
ALL = (GPSC, UKPSC, UKSSSC, OSSSC, KPSC, TNTRB)
