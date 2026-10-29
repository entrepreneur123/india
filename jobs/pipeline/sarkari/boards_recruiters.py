"""State recruiters whose notice board is one server-rendered list of (date, link, headline).

Each board below was written against its page as read from the India server (29-10-2026;
tests/fixtures/<source>.html):

  rpsc       Rajasthan Public Service Commission           rpsc.rajasthan.gov.in  home "News and Events": <a><span class='badge'>29/10/2026</span> headline</a>
  rssb       Rajasthan Staff Selection Board               rssb.rajasthan.gov.in  home "Latest News": <li><span>29-10-2026</span> - <a>headline</a></li>
  dsssb      Delhi Subordinate Services Selection Board    dsssb.delhi.gov.in     home tabs Notifications and Results: tab-title, "Date: 28-10-2026", link
  keralapsc  Kerala Public Service Commission              www.keralapsc.gov.in   notifications table: gazette of DD/MM/YYYY | category numbers | last date

Not collected: India Post GDS (indiapostgdsonline.gov.in) shows no date anywhere, only the
shortlists of its January 2025 schedule; dating them the day they are first seen would announce
old lists as news.
"""
from __future__ import annotations

import html
import re
from typing import Iterator

from .listboards import Board, _dmy, kind_of

_COMMENT = re.compile(r"<!--.*?-->", re.S)

# RPSC: the home page's "News and Events" box lists results, answer keys, press notes, question
# papers and advertisements, newest first:
#   <a href='/Static/Result/C9F3....pdf' target='_blank' class='d-block ...' rel='noopener noreferrer'>
#   <img src='Images/New.png' alt='New Icon' /><span class='badge'>29/10/2026</span> Result Preamble for ...</a>
# Its general press notes link '~/Static/News/...' (an unresolved ASP.NET app path: a browser asks
# for /~/Static/News/..., which could not be checked to serve the file), so they are left out.
_RPSC = re.compile(r"""<a href='([^']+)'[^>]*>\s*(?:<img [^>]*>\s*)?<span class='badge'>\s*([^<]*?)\s*</span>(.*?)</a>""", re.S)


def _rpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, day, headline in _RPSC.findall(page):
        if href.startswith("~"):
            continue
        yield day, href, headline


# RPSC's own words: "Advt. No. 05/2026-27 for Physiotherapist - 2026" (a new advertisement; its
# corrigenda say "Corrigendum No."), "Interview Letter for the post of ...", "Pickup List against Non-Joiner ...".
_RPSC_KINDS = ((re.compile(r"^Advt\.?\s*No\.", re.I), "recruitment"), (re.compile(r"^Interview Letter", re.I), "admit_card"),
               (re.compile(r"^Pick-?up List", re.I), "result"))


def _rpsc_kind(title: str) -> str:
    return next((kind for pattern, kind in _RPSC_KINDS if pattern.search(title)), None) or kind_of(title)


# RSSB: the home page's "Latest News" ticker (the last ten notices; older ones are on /news, filled by script):
#   <li><span ...><img src=".../new.gif" ...></span><span style="font-weight:bold">29-10-2026</span> -
#   <a href="https://rssb.rajasthan.gov.in/storage/news_item/1793273784.pdf" target="_blank">Driver 2024 : ...</a></li>
# A commented-out sample row (<!--<li>... "Right when the page is loaded"-->) follows each one.
_RSSB = re.compile(r"""<li>\s*(?:<span[^>]*>\s*<img [^>]*>\s*</span>\s*)?<span style="font-weight:bold">\s*([^<]*?)\s*</span>\s*-\s*"""
                   r"""<a href="([^"]+)"[^>]*>(.*?)</a>\s*</li>""", re.S)


def _rssb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for day, href, headline in _RSSB.findall(_COMMENT.sub("", page)):
        yield day, href, headline


def _rssb_kind(title: str) -> str:
    """"Basic and Senior Computer Instructor 2026 : Ammended Advertisement" (sic) is a change."""
    return "amendment" if re.search(r"\bamm?ended\b", title, re.I) else kind_of(title)


# DSSSB: the home page's tabs Notifications (links to the notice's page) and Results (PDFs):
#   <li><div class="notification-view"><div class="tab-title">Notice:- ...</div>
#   <div class="tab-date">Date: 28-10-2026</div></div> <a class="tab-view" href="/dsssb/notice-...">View</a></li>
#   (Results: <div class="tab-date">Date: <time datetime="..." class="datetime">29-10-2026</time> <span class="file_size">...</span></div>)
# The third tab, Tenders (the office's own purchases, undated), is cut off before reading.
_DSSSB = re.compile(r"""<div class="tab-title">(.*?)</div>\s*<div class="tab-date">\s*Date:\s*(?:<time[^>]*>)?\s*([^<]*?)\s*(?:</time>)?\s*"""
                    r"""(?:<span class="file_size">[^<]*</span>\s*)?</div>\s*</div>\s*<a class="tab-view"[^>]*?href="([^"]+)\"""", re.S)
_DSSSB_TENDERS = re.compile(r"""<div id\s*=\s*"Tenders\"""")


def _dsssb_entries(page: str) -> Iterator[tuple[str, str, str]]:
    page = _DSSSB_TENDERS.split(page, 1)[0]
    for headline, day, href in _DSSSB.findall(page):
        yield day, href, headline


def _dsssb_kind(title: str) -> str:
    """"VACANCY NOTICE ADVERTISEMENT NO. 05/2026" opens applications: a recruitment, not a vacancy list."""
    return "recruitment" if re.match(r"vacancy notice\s*/?\s*advertisement\b", title, re.I) else kind_of(title)


# Kerala PSC: /notifications is a table of the monthly extraordinary gazettes that notify new posts
# (each a range of category numbers), the gazette's day in its title:
#   <td headers="view-title-table-column" ...><a href="/extra-ordinary-gazette-date-30092026" hreflang="en">EXTRA ORDINARY GAZETTE DATE 30/09/2026</a></td>
#   <td headers="view-field-category-number-table-column" ...><a ...>CAT.NO.151/2026 TO CAT.NO.161/2026</a></td>
#   <td headers="view-field-last-date-table-column" ...><time datetime="2026-11-04T12:00:00Z">04-11-2026</time></td>
_KPSC = re.compile(r"""<td headers="view-title-table-column"[^>]*>\s*<a href="([^"]+)"[^>]*>(.*?)</a>\s*</td>\s*"""
                   r"""<td headers="view-field-category-number-table-column"[^>]*>\s*(?:<a [^>]*>)?(.*?)(?:</a>)?\s*</td>\s*"""
                   r"""<td headers="view-field-last-date-table-column"[^>]*>\s*(?:<time[^>]*>)?\s*([^<]*?)\s*(?:</time>)?\s*</td>""", re.S)
_KPSC_DATE = re.compile(r"\bgazette\s+date\s+(\d{1,2}/\d{1,2}/\d{4})\b", re.I)


def _kpsc_entries(page: str) -> Iterator[tuple[str, str, str]]:
    for href, title, cats, last in _KPSC.findall(page):
        text = html.unescape(re.sub(r"<[^>]+>", " ", title))
        m = _KPSC_DATE.search(text)
        cats = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", cats))).strip()
        headline = f"Kerala PSC notifications: {text.strip()}" + (f" ({cats})" if cats else "") + (f", last date {last}" if last else "")
        yield (m.group(1) if m else ""), href, headline


def _kpsc_kind(_title: str) -> str:
    return "recruitment"  # every gazette on this page notifies new posts


RPSC = Board("rpsc", "https://rpsc.rajasthan.gov.in", "https://rpsc.rajasthan.gov.in/", "RPSC", _rpsc_entries, _dmy,
            kind=_rpsc_kind)
RSSB = Board("rssb", "https://rssb.rajasthan.gov.in", "https://rssb.rajasthan.gov.in/", "RSSB", _rssb_entries, _dmy,
            kind=_rssb_kind)
DSSSB = Board("dsssb", "https://dsssb.delhi.gov.in", "https://dsssb.delhi.gov.in/", "DSSSB", _dsssb_entries, _dmy,
              kind=_dsssb_kind)
KERALAPSC = Board("keralapsc", "https://www.keralapsc.gov.in", "https://www.keralapsc.gov.in/notifications", "KERALAPSC",
                  _kpsc_entries, _dmy, kind=_kpsc_kind)
ALL = (RPSC, RSSB, DSSSB, KERALAPSC)
