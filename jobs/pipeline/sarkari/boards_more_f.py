"""Public-sector banks and PSUs whose recruitment page is one server-rendered list (same Board model as listboards.py).

Each board below was written against its page of 30-10-2026, read from a cloud host outside India (tests/fixtures/<source>.html; bob, pgcil,
canara and iob are cut unchanged from larger pages, see tests/test_boards_more_f.py):

  pnb     Punjab National Bank            www.pnb.bank.in          one block per recruitment, each file with "Publish Date -: 20-Apr-2026 06:30 PM"
  bob     Bank of Baroda                  bankofbaroda.bank.in     the openings as JSON in the page ("eDate", shown as "Dated : 28 Oct 2026"), General tab
  nhpc    NHPC Limited                    www.nhpcindia.com        list: <small>27-10-2026</small>, headline, "View Details" PDF
  bpcl    Bharat Petroleum                www.bharatpetroleum.in   table per recruitment: "open from 29th July 2026", "UPDATE AS ON 13.10.2026 - ..."
  pgcil   POWERGRID                       www.powergrid.in         card per recruitment: files whose titles carry their date ("Notice-2 dated 18.09.2025");
                                                                   the rest are dated the day first seen (undated)
  canara  Canara Bank                     www.canarabank.bank.in   one list of recruitment pages, no dates: dated the day first seen (undated)
  iob     Indian Overseas Bank            www.iob.bank.in          tables per year (advertisement, then its notices), no dates: dated the day first seen

Not collectable from what these sites served here (30-10-2026), see the report: Bank of India (Cloudflare challenge),
Indian Bank (request rejected), UCO Bank (no connection), Central Bank of India and Union Bank of India (no posting dates),
SAIL (every file on a third party's S3 bucket), Coal India (files on a CloudFront host), HPCL (no dates), HAL and AIIMS
(script-only pages), NTPC careers (403), BHEL, FCI and NIELIT (certificate chain not verifiable), NIACL, ESIC and the Coast
Guard (connection reset), CSIR (only last dates), BARC (only walk-in dates), GIC Re (month only), LIC (no dates).

The rules of listboards.py apply: a day, no time; links leaving the board's own host (IBPS registration portals, BPCL's
results login) are left out, or, where the board's page itself states the news (PNB's form-post files, BPCL's updates),
the item links that page; a broken page raises FormatChanged.
"""
from __future__ import annotations

import datetime as dt
import html
import json
import re
import urllib.parse
from typing import Iterator

from .listboards import _TAG, Board, _dmy, kind_of

_WS = re.compile(r"\s+")


def _text(fragment: str) -> str:
    return _WS.sub(" ", html.unescape(_TAG.sub(" ", fragment))).strip()


def _on_host(base: str, page: str, href: str) -> bool:
    url = urllib.parse.urljoin(page, html.unescape(href.strip()))
    return url.startswith(base + "/")


def _kinds(rules: list[tuple[str, re.Pattern]], fallback: str | None = None):
    def kind(title: str) -> str:
        return next((k for k, p in rules if p.search(title)), None) or fallback or kind_of(title)
    return kind


# PNB (ASP.NET): one block per recruitment, its files below it, each with its own publish time:
#   <h2><span id="ContentPlaceHolder1_rptGrid_Label1_0">20.04.2026</span></h2>
#   <p><span id="ContentPlaceHolder1_rptGrid_lblName_0">RECRUITMENT OF 30 SPECIALIST OFFICERS UNDER HRP 2026-27</span></p> ...
#   <p><a id="ContentPlaceHolder1_rptGrid_lnkTitle_0_0" class="title-link" href="javascript:__doPostBack(...)">DETAILED ADVERTISEMENT
#   (ENGLISH) <img ...> </a><br/><span id="ContentPlaceHolder1_rptGrid_lblPublishOn_0_0" class="attachment-label">Publish Date -:
#   <b>20-Apr-2026 06:30 PM</b></span>
# Each file opens by a form post, not a link, so the item links the recruitment page that lists it.
_PNB_BLOCK = re.compile(r'<span id="ContentPlaceHolder1_rptGrid_lblName_(\d+)">(.*?)</span>(.*?)'
                        r'(?=<span id="ContentPlaceHolder1_rptGrid_Label1_\d+">|$)', re.S)
_PNB_FILE = re.compile(r'<a id="ContentPlaceHolder1_rptGrid_lnkTitle_\d+_\d+"[^>]*>(.*?)</a><br/>'
                       r'<span id="ContentPlaceHolder1_rptGrid_lblPublishOn_\d+_\d+"[^>]*>\s*Publish Date -:\s*<b>\s*([^<]*?)\s*</b>', re.S)


def _pnb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for _n, name, block in _PNB_BLOCK.findall(page):
        for title, published in _PNB_FILE.findall(block):
            yield published, "", f"{_text(name)}: {_text(title)}"


def _pnb_date(raw: str, _title: str = "") -> dt.date:
    """'20-Apr-2026 06:30 PM'."""
    return dt.datetime.strptime(raw.strip(), "%d-%b-%Y %I:%M %p").date()


# A headline is "RECRUITMENT NAME: FILE TITLE"; the file's title says what it is.
_PNB_KINDS = [
    ("amendment", re.compile(r":.*\b(corrigendum|addendum|extension)", re.I)),
    ("admit_card", re.compile(r":.*\bcall letter", re.I)),
    ("result", re.compile(r":.*\b(final result|selected for appointment|scorecard|qualified in|selected candidates)", re.I)),
    ("schedule", re.compile(r":.*\b(interview schedule|schedule for document|notice for (online written|language test))", re.I)),
    ("notice", re.compile(r":.*\b(joining schedule|information handout|application form|download application|scribe)", re.I)),
    ("recruitment", re.compile(r":.*(\bdetailed advertisement|\bclick here to apply|आवेदन के लिए)", re.I)),
]
_pnb_kind = _kinds(_PNB_KINDS, "notice")


# Bank of Baroda: the page carries every opening as JSON and draws the list in the browser:
#   var glblMasterCareerDetails = [{"id":"...","prfile":"Recruitment of ... Advt. No. BOB/HRM/REC/ADVT/2026/18","fntn":"Information
#   Technology","fntnName":"information technology",...,"multiplefunction":"...","latestUpdate":"Notification dated 26.10.2026",...,
#   "eDate":"2026-10-28 00:00:00","cta":"https://bankofbaroda.bank.in/career/current-opportunities/recruitment-of-...-17-11",...}, ...]
# Each card shows "Dated : 28 Oct 2026" (eDate). The page opens on its "General" tab: openings whose function (fntnName or one of
# multiplefunction) is in the page's own `const allowedList = [...]`; the others (business correspondent coordinators, financial
# literacy counsellors, RSETI staff, monitoring agencies, sports guests) are local contracts and left out. A "latestUpdate" that
# carries its own day ("Notification dated 26.10.2026") is an item of its own.
_BOB_ALLOWED = re.compile(r"const allowedList = (\[.*?\]);", re.S)
_BOB_DATA = "var glblMasterCareerDetails = "
_BOB_UPDATE_DAY = re.compile(r"\bdated\s+(\d{1,2}\.\d{1,2}\.\d{4})", re.I)


def _bob_norm(name: str) -> str:
    return name.lower().strip().replace(" ", "")


def _bob_entries(page: str) -> Iterator[tuple[str, str, str]]:
    allowed, start = _BOB_ALLOWED.search(page), page.find(_BOB_DATA)
    if not allowed or start < 0:
        return
    allowed = {_bob_norm(a) for a in json.loads(allowed.group(1))}
    openings, _end = json.JSONDecoder().raw_decode(page, start + len(_BOB_DATA))
    for o in openings:
        functions = [o.get("fntnName") or ""] + (o.get("multiplefunction") or "").split(",")
        if not any(_bob_norm(f) in allowed for f in functions if f.strip()):
            continue
        title, href = _WS.sub(" ", o["prfile"]).strip(), o.get("cta") or ""
        yield (o.get("eDate") or "")[:10], href, html.escape(title, quote=False)
        update = _WS.sub(" ", o.get("latestUpdate") or "").strip()
        day = _BOB_UPDATE_DAY.search(update)
        if day:
            yield day.group(1).replace(".", "-"), href, html.escape(f"{title}: {update}", quote=False)


def _bob_date(raw: str, _title: str = "") -> dt.date:
    """'2026-10-28' (eDate) or '26-10-2026' (an update's own day)."""
    return dt.date.fromisoformat(raw) if re.fullmatch(r"\d{4}-\d{2}-\d{2}", raw) else _dmy(raw)


def _bob_kind(title: str) -> str:
    update = title.rsplit(": ", 1)[1] if re.search(r":[^:]*\bdated \d", title) else ""
    if not update:
        return "recruitment"  # the opening itself
    if re.search(r"\b(addendum|corrigendum)\b", update, re.I):
        return "amendment"
    if re.search(r"\b(shortlisted|result|selected)\b", update, re.I):
        return "result"
    return "notice"


# NHPC: the "Jobs" page lists its ten newest notices:
#   <li class="list-group-item"> ... <small tabindex="0">27-10-2026</small> ... <div class="col-md-7" tabindex="0">
#   Engagement of Consultant (Safety) on contract basis. <p ...>(1.56 MB | PDF | ...)</p></div> ...
#   <a class="btn btn-light btn-sm" href="https://www.nhpcindia.com/assests/pzi_public/job/details_1793084987_4256.pdf" ... aria-label="View Details">
# Calls for arbitrators or other panels (expressions of interest) are no jobs and are left out.
_NHPC = re.compile(r'<li class="list-group-item">\s*<div class="row">\s*<div class="col-md-1">\s*<small tabindex="0">\s*([^<]*?)\s*</small>'
                   r'\s*</div>\s*<div class="col-md-7" tabindex="0">(.*?)(?:<p\b.*?)?</div>(.*?)</li>', re.S)
_NHPC_FILE = re.compile(r'<a\b[^>]*?href="([^"]+)"[^>]*?aria-label="View Details"', re.S)
_NHPC_SKIP = re.compile(r"\b(expression of interest|EOI|empanelment|tender)\b", re.I)


def _nhpc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for day, headline, rest in _NHPC.findall(page):
        if _NHPC_SKIP.search(_text(headline)):
            continue
        f = _NHPC_FILE.search(rest)
        yield day, f.group(1) if f else "", headline


_NHPC_KINDS = [
    ("result", re.compile(r"\b(provisionally selected|provisional selection|selected candidates|result)", re.I)),
    ("schedule", re.compile(r"\bcalled for document verification\b", re.I)),
]
_nhpc_kind = _kinds(_NHPC_KINDS)


# BPCL: "Job Openings", one table row per recruitment: posts | details | last date. The details give the opening day
# ("Applications for all profiles open from 29th July 2026.", with "Click here to view Detailed Advertisement") and dated updates:
#   <p><strong><span class="important-text">UPDATE AS ON 01.10.2026</span></strong>- <a ... href="/images/files/SHORTLIST-...pdf"
#   ...><u>Click here to view the list of candidates shortlisted for the skill / proficiency test.</u></a></p>
# An update links its own file when that is on BPCL's site; most link IBPS's portal or BPCL's results login, so the item links
# this page, which states the update. Rows without a day (the board posts of Director, CMD) carry no item.
BPCL_PAGE = "https://www.bharatpetroleum.in/careers/job-openings"
_BPCL_TABLE = re.compile(r'<table class="table table-bordered main-tbl">(.*?)</table>', re.S)
_BPCL_ROW = re.compile(r"<tr>\s*<td>.*?</td>\s*<td>(.*?)</td>\s*<td>(.*?)</td>\s*<td>.*?</td>\s*</tr>", re.S)
_P = re.compile(r"<p\b[^>]*>(.*?)</p>", re.S)
_BPCL_UPDATE = re.compile(r"^\s*UPDATE AS ON\s+(\d{1,2}\.\d{1,2}\.\d{4})\s*(?:,\s*[\d:]+\s*hrs)?\s*-?\s*", re.I)
_BPCL_OPEN = re.compile(r"\bopen from\s+(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+),?\s+(\d{4})", re.I)
_A_HREF = re.compile(r'<a\b[^>]*?href="([^"]*)"', re.S)


def _bpcl_link(base: str, fragment: str) -> str:
    return next((h for h in _A_HREF.findall(fragment) if _on_host(base, BPCL_PAGE, h)), "")


def _bpcl_entries(page: str) -> Iterator[tuple[str, str, str]]:
    base = "https://www.bharatpetroleum.in"
    for table in _BPCL_TABLE.findall(page):
        for posts, details in _BPCL_ROW.findall(table):
            lines = [t for t in (_text(p) for p in _P.findall(posts)) if t]
            if not lines:
                continue
            # "Recruitment for Entry Level Roles" comes back every year: its posts tell the rows apart
            name = lines[0] + (" (" + ", ".join(re.sub(r"^\d+\.\s*", "", t) for t in lines[1:]) + ")" if lines[1:] else "")
            paragraphs = _P.findall(details)
            opened = _BPCL_OPEN.search(_text(details))
            if opened:
                advert = next((_bpcl_link(base, p) for p in paragraphs if re.search(r"detailed advertisement", _text(p), re.I)), "")
                day = dt.datetime.strptime(" ".join(opened.groups()), "%d %B %Y").strftime("%d-%m-%Y")
                yield day, advert, html.escape(f"{name}: Detailed Advertisement", quote=False)
            for p in paragraphs:
                text = _text(p)
                m = _BPCL_UPDATE.match(text)
                if m:
                    yield m.group(1), _bpcl_link(base, p), html.escape(f"{name}: {text[m.end():]}", quote=False)


_BPCL_KINDS = [
    ("amendment", re.compile(r":[^:]*\b(corrigendum|extended)\b", re.I)),
    ("admit_card", re.compile(r":[^:]*\bdownload (the )?call letters?\b", re.I)),
    ("result", re.compile(r":[^:]*\b(view (the )?results?|list of candidates shortlisted|provisionally selected candidates have)\b", re.I)),
    ("schedule", re.compile(r":[^:]*\b(is scheduled on|will be conducted)\b", re.I)),
    ("recruitment", re.compile(r": Detailed Advertisement$")),
]
_bpcl_kind = _kinds(_BPCL_KINDS, "notice")


# POWERGRID: one card per recruitment (a card's own date is when it was last edited, not a publication day):
#   <h4 class="midHeadSize"> Recruitment of Engineer Trainees-2027 through GATE 2027 Advt No. CC/03/2026 dated 24.09.2026 </h4> ...
#   <div class="showMoreCont mt-4"> ... <div class="views-row"> ... <a href="https://www.powergrid.in/sites/default/files/
#   job_opportunities_document/Notice02_2025ApplicationCount_CBTSchedule.pdf" ... title="Notice - 02 dtd 11-11-2025 - Applications
#   Count and CBT Schedule">
# A file is dated by its title ("dtd 11-11-2025", "dated 24th December 2024", "dt 04.02.26"); the detailed advertisement by the
# card's "dated DD.MM.YYYY"; files with no day in their title are dated the day they are first seen.
_PG_CARD = re.compile(r'<h4 class="midHeadSize">\s*(.*?)\s*</h4>(.*?)(?=<div class="newBorderBoxTop">|$)', re.S)
_PG_FILES = re.compile(r'<div class="showMoreCont mt-4">(.*)', re.S)
_PG_FILE = re.compile(r'<div class="views-row">.*?<a href="(https://www\.powergrid\.in/sites/default/files/[^"]+)"[^>]*?title="([^"]*)"',
                      re.S)
_MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec"
_PG_DAY = re.compile(r"\b(?:dtd|dated|dt)\.?\s*:?\s*(?:(\d{1,2})[-./](\d{1,2})[-./](\d{4}|\d{2})\b|"
                     r"(\d{1,2})(?:st|nd|rd|th)?\s+(" + _MONTHS + r")[a-z]*,?\s+(\d{4}))", re.I)


def _pg_day(text: str) -> str:
    m = _PG_DAY.search(text)
    if not m:
        return ""
    if m.group(1):
        year = m.group(3) if len(m.group(3)) == 4 else "20" + m.group(3)
        return f"{m.group(1)}-{m.group(2)}-{year}"
    month = ("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").index(m.group(5).lower()[:3]) + 1
    return f"{m.group(4)}-{month}-{m.group(6)}"


def _pgcil_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for heading, card in _PG_CARD.findall(page):
        files = _PG_FILES.search(card)
        name = _text(heading)
        for href, title in _PG_FILE.findall(files.group(1) if files else ""):
            title = _text(title)
            day = _pg_day(title) or (_pg_day(name) if re.fullmatch(r"detailed advertisement", title, re.I) else "")
            yield day, href, html.escape(f"{name}: {title}", quote=False)


_PG_KINDS = [
    ("amendment", re.compile(r":.*\b(corrigendum|addendum|addition of vacanc|extension)", re.I)),
    ("admit_card", re.compile(r":.*\b(admit card|call letter)", re.I)),
    ("answer_key", re.compile(r":.*\bobjection management\b", re.I)),
    ("result", re.compile(r":.*\b(shortlisted|selected|cut[- ]?off|eligible candidates|result)", re.I)),
    ("schedule", re.compile(r":.*\b(cbt schedule|schedule for|date of cbt|date & city|conduct of|date, time|interview notice)", re.I)),
    ("recruitment", re.compile(r":\s*(detailed advertisement|window advertisement|vacancy notification)", re.I)),
]
_pgcil_kind = _kinds(_PG_KINDS, "notice")


# Canara Bank: the recruitment page is one list of links, newest first, with no dates; a "[NEW]" tag comes and goes and is
# dropped from the headline (else its going would make a new item):
#   <h2 class="sb-heading">Recruitment</h2> ... <li><a aria-label="..." href="/ibps-crp-xv-probationary-customer-service-associates"
#   ... title="...">IBPS-CRP-XV-Probationary Customer Service Associates&nbsp;(CSAs) <span ...>[NEW]</span></a></li>
# Script-only links (javascript:;) and the fraud caution are left out.
_CANARA_LIST = re.compile(r'<h2 class="sb-heading">Recruitment</h2>(.*?)</ul>', re.S)
_CANARA = re.compile(r"<li>\s*<a\b([^>]*)>(.*?)</a>", re.S)
_HREF = re.compile(r'href="([^"]*)"')
_NEW_TAG = re.compile(r"\[\s*NEW\s*\]", re.I)


def _canara_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for block in _CANARA_LIST.findall(page):
        for attrs, inner in _CANARA.findall(block):
            h = _HREF.search(attrs)
            title = _NEW_TAG.sub(" ", _text(inner))
            title = re.sub(r"\s*\(\.pdf - [\d.]+ [KM]B\)", "", title).strip().rstrip(".").strip()
            if re.search(r"\bfraud alert\b", title, re.I):
                continue
            yield "", h.group(1) if h else "", html.escape(title, quote=False)


_CANARA_KINDS = [
    ("amendment", re.compile(r"\b(extension of|corrigendum|addendum)\b", re.I)),
    ("result", re.compile(r"\b(provisionally selected|selected candidates|result)\b", re.I)),
    ("notice", re.compile(r"^IBPS\b", re.I)),  # the bank's page for candidates IBPS allotted to it (joining), not an opening
]
_canara_kind = _kinds(_CANARA_KINDS, "recruitment")


# IOB: "Careers" holds one table per year ("Recruitment 2026-27", "Engagement of Apprentices 2025-26", ...): a numbered row for each
# advertisement, then rows "2.1", "2.2" for its notices, no dates:
#   <tr> <td>2</td> <td><p>Recruitment of Local Bank Officers 2026-27</p></td> <td><p><a href="/documents/d/guest/lbo-final-ad-2026-27"
#   target="_blank">(View)</a> ... </td> <td><a href="https://ibpsreg.ibps.in/iobjul26/" ...>(APPLY ONLINE)</a> ...</td> </tr>
#   <tr> <td>2.3</td> <td>&nbsp;Online Examination Call Letter Download Link&nbsp;<img alt="New Update" ...></td> <td><a href=
#   "https://ibpsreg.ibps.in/iobjul26/oecla_sep26/login.php?appid=..." ...>(View)</a></td> ...
# A notice's headline leads with its advertisement's. A row is linked to its first file on IOB's site; a row that has only an
# outside link (IBPS's call letter portal) links this page. Items are dated the day they are first seen.
IOB_PAGE = "https://www.iob.bank.in/en/Careers"
_IOB_ROW = re.compile(r"<tr>\s*<td>\s*(?:<p>)?\s*(\d+(?:\.\d+)?)\.?\s*(?:</p>)?\s*</td>\s*<td>(.*?)</td>(.*?)</tr>", re.S)


def _iob_entries(page: str) -> Iterator[tuple[str, str, str]]:
    base, parent = "https://www.iob.bank.in", ""
    for number, title, rest in _IOB_ROW.findall(page):
        text = _text(title)
        if not text:
            continue
        if re.search(r"\bdirect selling agent\b", text, re.I):
            parent = ""
            continue  # empanelment of sales agents: no job
        if "." not in number:
            parent = text
        elif parent:
            text = f"{parent}: {text}"
        href = next((h for h in _A_HREF.findall(title + rest) if _on_host(base, IOB_PAGE, h)), "")
        yield "", href, html.escape(text, quote=False)


_IOB_KINDS = [
    ("amendment", re.compile(r"\b(corrigendum|addendum|extension)", re.I)),
    ("admit_card", re.compile(r"\bcall letter", re.I)),
    ("result", re.compile(r"\b(results?\b|shortlisted|provisional selection|selected|qualified candidates|final selection|waitlist|rejected)", re.I)),
    ("schedule", re.compile(r"\b(conduct of|examination date|interview schedule|physical fitness test)", re.I)),
    ("recruitment", re.compile(r"^(recruitment|engagement|advertisement|financial literacy|rseti|government business)[^:]*$", re.I)),
]


def _iob_kind(title: str) -> str:
    """A notice ("ADVERTISEMENT: notice") is judged by its own words; a row of its own that is no advertisement is a notice."""
    own = title.rsplit(": ", 1)[-1] if ": " in title else title
    return next((k for k, p in _IOB_KINDS[:-1] if p.search(own)), None) or (
        "recruitment" if _IOB_KINDS[-1][1].search(title) else "notice")


PNB = Board("pnb", "https://www.pnb.bank.in", "https://www.pnb.bank.in/Recruitments.aspx", "PNB", _pnb_entries, _pnb_date,
            kind=_pnb_kind)
BOB = Board("bob", "https://bankofbaroda.bank.in", "https://bankofbaroda.bank.in/career/current-opportunities", "BOB", _bob_entries,
            _bob_date, kind=_bob_kind)
NHPC = Board("nhpc", "https://www.nhpcindia.com", "https://www.nhpcindia.com/welcome/job", "NHPC", _nhpc_entries, _dmy,
             min_items=3, kind=_nhpc_kind)
BPCL = Board("bpcl", "https://www.bharatpetroleum.in", BPCL_PAGE, "BPCL", _bpcl_entries, _dmy, kind=_bpcl_kind, dated_ids=True)
PGCIL = Board("pgcil", "https://www.powergrid.in", "https://www.powergrid.in/en/job-opportunities", "POWERGRID", _pgcil_entries,
              _dmy, kind=_pgcil_kind, undated=True)
CANARA = Board("canara", "https://www.canarabank.bank.in", "https://www.canarabank.bank.in/pages/Recruitment", "CANARA",
               _canara_entries, _dmy, kind=_canara_kind, undated=True)
IOB = Board("iob", "https://www.iob.bank.in", IOB_PAGE, "IOB", _iob_entries, _dmy, kind=_iob_kind, undated=True)
ALL = (PNB, BOB, NHPC, BPCL, PGCIL, CANARA, IOB)
