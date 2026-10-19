"""lotterysambad.com: fast, text-based result PDFs (no OCR needed).

  today:     https://www.lotterysambad.com/fetchtoday.php?filename=<CODE><DDMMYY>.PDF
  history:   https://www.lotterysambad.com/lotteryoldresultsdown.php?filename=<STEM>.PDF
             (about the last month; checked back to 18-09-2026)

Weekly files appear about 7-10 minutes after each draw. Before upload (or on
days without a draw, e.g. 02-10-2026) the endpoints return a short plain-text
message instead of a PDF. Numbers in these files matched the official
Nagaland gazette for all 132 numbers of 17-10-2026 1 PM.
"""
from __future__ import annotations

import datetime as dt
import html
import re
import subprocess
import tempfile

from .http import get_bytes
from .model import (DEAR_D5, EXACT, LAST4, LAST5, SLOTS, Draw,
                    FIRST_PRIZE_RE, ValidationError, build_tiers, validate)

BASE = "https://www.lotterysambad.com/"
TODAY_URL = BASE + "fetchtoday.php?filename={stem}.PDF"
OLD_URL = BASE + "lotteryoldresultsdown.php?filename={stem}.PDF"
_DRAWNO_DATE_RE = re.compile(r"^\s*(\d{1,4})\s+(\d{2})/(\d{2})/(\d{2})\s*$")
_NUMBER_LINE_RE = re.compile(r"^[\s\d]+$")
_EXACT_LINE_RE = re.compile(r"^\s*([A-Z]{1,2}) (\d{6})(?:\s{2,}|$)")


def pdf_to_text(pdf: bytes) -> str:
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(pdf)
        f.flush()
        out = subprocess.run(["pdftotext", "-layout", f.name, "-"],
                             check=True, capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def sheet_url(stem: str, date: dt.date, today: dt.date) -> str:
    return (TODAY_URL if date == today else OLD_URL).format(stem=stem)


def parse(text: str, slot_id: str, date: dt.date) -> Draw:
    """Weekly Dear 5-digit sheet (1 PM / 6 PM / 8 PM)."""
    slot = SLOTS[slot_id]
    if slot.scheme is not DEAR_D5:
        raise ValidationError(f"weekly parser only supports 5-digit Dear draws, not {slot_id}")

    name_parts: list[str] = []
    draw_no = None
    first = None
    five: list[str] = []
    four_before_5th: list[str] = []
    fifth: list[str] = []
    in_fifth = False

    for line in text.splitlines():
        s = line.strip()
        if not s:
            continue
        if first is None:
            m = _DRAWNO_DATE_RE.match(s)
            if m:
                draw_no = int(m.group(1))
                d, mth, yy = int(m.group(2)), int(m.group(3)), 2000 + int(m.group(4))
                if dt.date(yy, mth, d) != date:
                    raise ValidationError(f"sheet is dated {d:02}/{mth:02}/{yy}, expected {date}")
                continue
            m = FIRST_PRIZE_RE.match(s)
            if m:
                first = f"{m.group(1)}{m.group(2)} {m.group(3)}"
                continue
            if draw_no is None and re.fullmatch(r"[A-Z][A-Z ]+", s):
                name_parts.append(s)
            continue
        if s.lower().startswith("5th prize"):
            in_fifth = True
            continue
        if not _NUMBER_LINE_RE.match(s):
            continue  # seller line, dates (contain '/'), TDS footer (letters)
        for tok in s.split():
            if in_fifth:
                fifth.append(tok)
            elif len(tok) == 5:
                five.append(tok)
            else:
                four_before_5th.append(tok)

    if first is None or draw_no is None:
        raise ValidationError("could not find draw number/date or 1st prize")
    if any(len(t) != 4 for t in four_before_5th + fifth):
        raise ValidationError("unexpected token length in 4-digit tiers")
    raw = {
        "1": [first],
        "CONS": five[:1],
        "2": five[1:],
        "3": four_before_5th[:10],
        "4": four_before_5th[10:],
        "5": fifth,
    }
    # Each printed block is sorted ascending; 3rd and 4th are two separate blocks.
    for rank in ("2", "3", "4"):
        if raw[rank] != sorted(raw[rank]):
            raise ValidationError(f"tier {rank} block not in printed (sorted) order")

    name = " ".join(name_parts)
    if not name.startswith("DEAR"):
        name = f"DEAR {name}".strip()
    draw = Draw(slot=slot_id, date=date.isoformat(), name=name, draw_no=draw_no,
                tiers=build_tiers(slot.scheme, raw))
    validate(draw)
    return draw


def parse_special(text: str, slot_id: str) -> Draw:
    """Bumper/monthly sheet: tiers are filled in printed order from the scheme in specials.json.

    Whole ticket numbers ("A 801021") fill EXACT tiers; 5- and 4-digit numbers fill LAST5 /
    LAST4 tiers in order. Every token must be used exactly once, so a layout change fails.
    """
    slot = SLOTS[slot_id]
    exact: list[str] = []
    by_len: dict[int, list[str]] = {4: [], 5: []}
    for line in text.splitlines():
        m = _EXACT_LINE_RE.match(line)
        if m:
            exact.append(f"{m.group(1)} {m.group(2)}")
            continue
        s = line.strip()
        if s and _NUMBER_LINE_RE.match(s):
            for tok in s.split():
                if len(tok) not in by_len:
                    raise ValidationError(f"unexpected token {tok!r}")
                by_len[len(tok)].append(tok)
    raw: dict[str, list[str]] = {}
    for spec in slot.scheme.tiers:
        if spec.match not in (EXACT, LAST5, LAST4):
            raise ValidationError(f"tier {spec.rank}: {spec.match} is not used on special sheets")
        pool = exact if spec.match == EXACT else by_len[{LAST5: 5, LAST4: 4}[spec.match]]
        if len(pool) < spec.count:
            raise ValidationError(f"tier {spec.rank}: only {len(pool)} numbers left, need {spec.count}")
        raw[spec.rank] = pool[:spec.count]
        del pool[:spec.count]
    if exact or by_len[4] or by_len[5]:
        raise ValidationError(f"unused numbers on sheet: {len(exact)} tickets, "
                              f"{len(by_len[5])} 5-digit, {len(by_len[4])} 4-digit")
    draw = Draw(slot=slot_id, date=slot.date, name=slot.name, draw_no=None,
                tiers=build_tiers(slot.scheme, raw))
    validate(draw)
    return draw


def fetch(slot_id: str, date: dt.date, today: dt.date | None = None) -> Draw | None:
    """Return the parsed draw, or None when the file is not uploaded (yet)."""
    slot = SLOTS[slot_id]
    today = today or date
    stem = slot.file_code if slot.special else f"{slot.file_code}{date:%d%m%y}"
    url = sheet_url(stem, date, today)
    body = get_bytes(url)
    if body is None or not body.startswith(b"%PDF"):
        return None
    text = pdf_to_text(body)
    draw = parse_special(text, slot_id) if slot.special else parse(text, slot_id, date)
    # Link users to the stable history URL, which keeps working after the draw day.
    draw.sources.append({"kind": "lotterysambad", "url": OLD_URL.format(stem=stem)})
    return draw


def listed_today() -> list[tuple[str, str]]:
    """(file stem, title) pairs on today.php, e.g. ('MN181026', 'DEAR 1:00 PM WEEKLY')."""
    page = (get_bytes(BASE + "today.php") or b"").decode("utf-8", "replace")
    titles = re.findall(r"<h5[^>]*>(.*?)</h5>", page, re.S)
    stems = re.findall(r'id="([A-Z]{2}\d{6})\.PDF"\s+class="pdfile"', page)
    return [(s, clean_title(html.unescape(re.sub(r"<[^>]+>", " ", t)))) for s, t in zip(stems, titles)]


def clean_title(t: str) -> str:
    """Draw names come from someone else's page and end up in our feed: plain words only."""
    return " ".join(re.sub(r"[^A-Za-z0-9 :.,&'()/-]", " ", t).split())[:80]


def special_catalog() -> list[dict]:
    """Bumper/monthly sheets offered for download on old-result.php."""
    page = (get_bytes(BASE + "old-result.php") or b"").decode("utf-8", "replace")
    out = []
    for stem, label in re.findall(r'<option value="([A-Z]{2}\d{6})\.PDF">([^<]+)</option>', page):
        m = re.match(r"(.+?)\s+(\d{2})-(\d{2})-(\d{4})\s*$", label.strip())
        if m:
            out.append({"stem": stem, "name": clean_title(html.unescape(m.group(1))),
                        "date": f"{m.group(4)}-{m.group(3)}-{m.group(2)}",
                        "url": OLD_URL.format(stem=stem)})
    return out


def upcoming_specials(today: dt.date) -> list[dict]:
    """Announced bumper/monthly draws, from the WB tax-challan links on the home page."""
    page = (get_bytes(BASE) or b"").decode("utf-8", "replace")
    out = []
    for name, d, m, y in re.findall(r'href="tax/([a-z0-9 ]+?)-(\d{2})-(\d{2})-(\d{4})\.pdf"', page):
        if name.startswith("dear weekly"):
            continue
        date = dt.date(int(y), int(m), int(d))
        if date >= today:
            out.append({"name": name.upper(), "date": date.isoformat()})
    return sorted({(e["date"], e["name"]): e for e in out}.values(), key=lambda e: e["date"])
