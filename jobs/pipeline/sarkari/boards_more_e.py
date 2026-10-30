"""Telangana's public service commission (same Board model as listboards.py).

Written against its page of 30-10-2026, read live with the jobs UA (tests/fixtures/<source>.html, whole):

  tgpsc  Telangana Public Service Commission  websitenew.tgpsc.gov.in  "What's New": <tr><td><a href = "preview/<base64>r95v17a0y2d8i13v">headline</a>

www.tgpsc.gov.in (the address the India server reads) is now only a landing page that says "TGPSC main website
domain is changed to https://websitenew.tgpsc.gov.in/"; the notice list is on that site's home page.

Not collectable from what the other sites serve (29/30-10-2026), see the capture list for their list pages:
upsssc, bssc, mp-esb, cgpsc, hppsc, appsc, jssc (lists without dates), army, navy, itbp, cisf (undated notice board),
wb-police, gsssb, tnusrb, tnpsc, mpsc (React app whose API answers only with a CRC header, encrypted), upsconline.
"""
from __future__ import annotations

import base64
import binascii
import datetime as dt
import html
import re
import urllib.parse
from dataclasses import dataclass
from typing import Iterator

from .listboards import _TAG, Board, _dmy, kind_of


# TGPSC: the home page's "What's New" panel, newest first; each file is served by a "preview" link whose path is the
# base64 of "PRESSNOTE/<file>.pdfaccesstime=<YYYY-MM-DD>" followed by a fixed 16-letter tail:
#   <div class="panel-heading"> <h3 class="h4 panel-title">What's New</h3> ... <table ...> <tbody>
#   <tr> <td><a target="_blank" href = "preview/UFJFU1NOT1RFL1dFQk5PVEUtMDdH...TIwMjYtMTEtMzA=r95v17a0y2d8i13v"><i class="fas ..."></i>
#        &nbsp;&nbsp;GENERAL RECRUITMENT FOR THE POST OF ... - WEB NOTE</a></td> </tr>
# The upload system ends each file name with its upload time, YYYYMMDDhhmmss ("WEBNOTE-07G-MPSO20261028221504.pdf"):
# that day dates the notice. "accesstime" is the day the link stops serving the file.
# A link to another site (the departmental tests' application portal) is left out, as everywhere else.
# Left out too: serving officers' departmental tests and probationers' half-yearly examinations (their notifications,
# time-tables, viva-voce hall tickets), which are no recruitment. They can fill much of the panel, hence min_items=3.
_TGPSC_PANEL = re.compile(r"""<h3 class="h4 panel-title">\s*What's New\s*</h3>(.*?)</table>""", re.S)
_TGPSC_ROW = re.compile(r"""<tr>\s*<td>\s*<a\b[^>]*?href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>\s*</td>\s*</tr>""", re.S)
_TGPSC_TOKEN = re.compile(r"^/?preview/([A-Za-z0-9+/]+=*)[A-Za-z0-9]{16}$")
_TGPSC_FILE = re.compile(r"^PRESSNOTE/[^\x00-\x1f]*?(20\d{2})(\d{2})(\d{2})\d{6}\.pdfaccesstime=20\d{2}-\d{2}-\d{2}$")
_TGPSC_PATH = re.compile(r"^/preview/[A-Za-z0-9+/=]+$")
_DEPARTMENTAL = re.compile(r"\b(departmental tests?|half[- ]yearly examinations?)\b", re.I)


@dataclass(frozen=True)
class _RawPathBoard(Board):
    """TGPSC's server answers 500 to "preview/...%3D..." (Board.url quotes the base64 padding "="), so a path made
    only of base64 letters is kept as the page writes it; any other link is handled as everywhere else."""

    def url(self, href: str) -> str:
        checked = super().url(href)  # raises for links to other sites
        p = urllib.parse.urlsplit(urllib.parse.urljoin(self.NOTICE_PAGE, html.unescape(href.strip())))
        if _TGPSC_PATH.match(p.path) and not p.query:
            return urllib.parse.urlunsplit((p.scheme, p.netloc, p.path, "", ""))
        return checked


def _tgpsc_day(href: str) -> str:
    m = _TGPSC_TOKEN.match(html.unescape(href.strip()))
    if not m:
        return ""
    token = m.group(1)
    try:
        name = base64.b64decode(token + "=" * (-len(token) % 4), validate=True).decode("ascii")
    except (binascii.Error, UnicodeDecodeError):
        return ""
    f = _TGPSC_FILE.match(name)
    return f"{f.group(3)}-{f.group(2)}-{f.group(1)}" if f else ""


def _tgpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    panel = _TGPSC_PANEL.search(page)
    if not panel:
        return
    for href, headline in _TGPSC_ROW.findall(panel.group(1)):
        if _DEPARTMENTAL.search(html.unescape(_TAG.sub(" ", headline))):
            continue
        yield _tgpsc_day(href), href, headline


_WEB_NOTE_OF_ADVERT = re.compile(r"\b(general|direct) recruitment (for|to) the posts? of\b.*\bnotification\b.*-\s*web note\.?$", re.I)
_NOTIFICATION = re.compile(r"\b(general|direct) recruitment (for|to) the posts? of\b.*-\s*notification\.?$", re.I)
_HALL_TICKET = re.compile(r"\bhall[- ]?tickets?\b", re.I)
_KEY = re.compile(r"\b(hosting of key|objections on the key|master question papers? with (the )?key|final key)\b", re.I)
_SCHEDULE = re.compile(r"\b(schedule of|time-?table|exam scheduled on)\b|^schedule\b", re.I)
_RESCHEDULE = re.compile(r"\b(re-?schedul|postpone|cancell?ation|addendum|corrigendum|extension|extended)", re.I)


def _tgpsc_kind(title: str) -> str:
    """TGPSC writes the post first and the step last ("... NOTIFICATION NO. 07/G/ES/2026, DATED: 28/10/2026 - WEB NOTE",
    "... - AVAILABILITY OF HALL TICKETS FOR DOWNLOAD - WEB NOTE"). A new advertisement comes as two lines, the
    notification and its web note: both are the recruitment."""
    if _RESCHEDULE.search(title):
        return "amendment"
    if _HALL_TICKET.search(title):
        return "admit_card"
    if _KEY.search(title):
        return "answer_key"
    if _SCHEDULE.search(title):
        return "schedule"
    if _NOTIFICATION.search(title) or _WEB_NOTE_OF_ADVERT.search(title):
        return "recruitment"
    kind = kind_of(title)
    return "notice" if kind == "recruitment" else kind  # "RECRUITMENT TO THE POST OF X - ..." is a step of an advert


TGPSC = _RawPathBoard("tgpsc", "https://websitenew.tgpsc.gov.in", "https://websitenew.tgpsc.gov.in/", "TGPSC",
                      _tgpsc_entries, _dmy, min_items=3, kind=_tgpsc_kind)
ALL = (TGPSC,)
