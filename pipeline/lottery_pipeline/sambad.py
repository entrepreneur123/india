"""lotterysambad.com: fast, text-based result PDFs (no OCR needed).

Files appear about 7-10 minutes after each draw at
  https://www.lotterysambad.com/fetchtoday.php?filename=<CODE><DDMMYY>.PDF
and only for the current day. Before upload the endpoint returns a short
plain-text message instead of a PDF.
"""
from __future__ import annotations

import datetime as dt
import re
import subprocess
import tempfile

from .http import get_bytes
from .model import (DEAR_D5, Draw, FIRST_PRIZE_RE, SLOTS, ValidationError,
                    build_tiers, validate)

URL = "https://www.lotterysambad.com/fetchtoday.php?filename={code}{ddmmyy}.PDF"
_DRAWNO_DATE_RE = re.compile(r"^\s*(\d{1,4})\s+(\d{2})/(\d{2})/(\d{2})\s*$")
_NUMBER_LINE_RE = re.compile(r"^[\s\d]+$")


def pdf_to_text(pdf: bytes) -> str:
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(pdf)
        f.flush()
        out = subprocess.run(["pdftotext", "-layout", f.name, "-"],
                             check=True, capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def parse(text: str, slot_id: str, date: dt.date) -> Draw:
    slot = SLOTS[slot_id]
    if slot.scheme is not DEAR_D5:
        raise ValidationError(f"sambad parser only supports 5-digit Dear draws, not {slot_id}")

    lines = text.splitlines()
    name_parts: list[str] = []
    draw_no = None
    first = None
    five: list[str] = []
    four_before_5th: list[str] = []
    fifth: list[str] = []
    in_fifth = False

    for line in lines:
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
    # Each printed block is sorted ascending; 3rd and 4th are two separate blocks.
    raw = {
        "1": [first],
        "CONS": five[:1],
        "2": five[1:],
        "3": four_before_5th[:10],
        "4": four_before_5th[10:],
        "5": fifth,
    }
    for rank in ("2", "3", "4", "5"):
        block = raw[rank]
        if rank != "5" and block != sorted(block):
            raise ValidationError(f"tier {rank} block not in printed (sorted) order")

    name = " ".join(name_parts)
    if not name.startswith("DEAR"):
        name = f"DEAR {name}".strip()
    draw = Draw(slot=slot_id, date=date.isoformat(), name=name, draw_no=draw_no,
                tiers=build_tiers(slot.scheme, raw))
    validate(draw)
    return draw


def fetch(slot_id: str, date: dt.date) -> Draw | None:
    """Return the parsed draw, or None when the file is not uploaded yet."""
    slot = SLOTS[slot_id]
    url = URL.format(code=slot.file_code, ddmmyy=date.strftime("%d%m%y"))
    body = get_bytes(url)
    if body is None or not body.startswith(b"%PDF"):
        return None
    draw = parse(pdf_to_text(body), slot_id, date)
    draw.sources.append({"kind": "lotterysambad", "url": url})
    return draw
