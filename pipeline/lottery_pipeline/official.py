"""nagalandlotteries.com: the official gazette (scanned image inside a PDF).

The gazette is the legal source, but it is uploaded hours late (the whole day
appears around 20:30 IST) and has no text layer. Tesseract misreads the bold
underlined digits (e.g. 7587 -> 2587), so OCR output is only trusted when
several independent OCR passes agree exactly AND the structure validates.
It is used to *verify* the fast Sambad data, and as the only source for the
4-digit Dear Laxmi draw (published with ocr=True so the app shows a caution).
"""
from __future__ import annotations

import datetime as dt
import glob
import os
import re
import subprocess
import tempfile

from .http import get_bytes
from .model import (D5_LETTERS, Draw, SLOTS, ValidationError, build_tiers,
                    validate)

LIST_URL = "https://www.nagalandlotteries.com/getContent.php"
PDF_URL = "https://www.nagalandlotteries.com/old_results/{code}{ddmmyy}.PDF"

_FIRST_D5_RE = re.compile(r"(?<!\d)(\d{2})\s?([%s])\s?(\d{5})(?!\d)" % D5_LETTERS)
_NAME_RE = re.compile(r"(DEAR [A-Z ]+? WEEKLY LOTTERY)")
_DRAWNO_RE = re.compile(r"Draw\s*No[:.]?\s*(\d{1,4})", re.I)
_WHITELIST = "0123456789" + D5_LETTERS + " "


def list_codes(date: dt.date) -> list[str]:
    """File codes (e.g. ['MN', 'EN', 'DG']) the official site lists for a date."""
    html = get_bytes(LIST_URL, data={"id": date.strftime("%d-%m-%Y")}) or b""
    return re.findall(r'data-id="([A-Z]{2})\d{6}\.PDF"', html.decode("utf-8", "replace"))


def _number_rows(text: str) -> list[list[str]]:
    rows = []
    for line in text.splitlines():
        toks = line.split()
        if len(toks) >= 5 and all(re.fullmatch(r"\d{4,5}", t) for t in toks):
            rows.append(toks)
    return rows


def parse_d5(text: str, slot_id: str, date: dt.date, name: str, draw_no: int | None) -> Draw:
    m = _FIRST_D5_RE.search(text)
    if not m:
        raise ValidationError("1st prize not found")
    first = f"{m.group(1)}{m.group(2)} {m.group(3)}"
    rows = _number_rows(text)
    five = [r for r in rows if all(len(t) == 5 for t in r)]
    four = [r for r in rows if all(len(t) == 4 for t in r)]
    if len(five) != 1 or len(four) != 12:
        raise ValidationError(f"unexpected row layout: {len(five)} five-digit, {len(four)} four-digit rows")
    raw = {"1": [first], "CONS": [first[-5:]], "2": five[0], "3": four[0], "4": four[1],
           "5": [t for r in four[2:] for t in r]}
    draw = Draw(slot_id, date.isoformat(), name, draw_no, build_tiers(SLOTS[slot_id].scheme, raw))
    validate(draw)
    return draw


def parse_d4(text: str, slot_id: str, date: dt.date, name: str, draw_no: int | None) -> Draw:
    singles = []
    for line in text.splitlines():
        if re.search(r"1\s*time", line):
            m = re.search(r"(\d{4})\s*$", line)
            if not m:
                raise ValidationError("single-number prize line unreadable")
            singles.append(m.group(1))
    rows = [r for r in _number_rows(text) if all(len(t) == 4 for t in r)]
    if len(singles) != 2 or len(rows) < 3:
        raise ValidationError("unexpected Laxmi layout")
    raw = {"1": singles[:1], "2": singles[1:], "3": rows[0], "4": rows[1],
           "5": [t for r in rows[2:] for t in r]}
    draw = Draw(slot_id, date.isoformat(), name, draw_no, build_tiers(SLOTS[slot_id].scheme, raw))
    validate(draw)
    return draw


def _ocr_variants(jpg: str, workdir: str) -> list[str]:
    """Run several OCR configurations; each is an independent read."""
    from PIL import Image  # imported lazily: only needed on the OCR path

    im = Image.open(jpg).convert("L")
    up = im.resize((im.width * 2, im.height * 2), Image.LANCZOS)
    binar = up.point(lambda p: 0 if p < 150 else 255)
    texts = []
    for i, (img, psm, wl) in enumerate([(im, 4, False), (up, 6, True), (binar, 6, True), (up, 4, False)]):
        path = os.path.join(workdir, f"v{i}.png")
        img.save(path)
        cmd = ["tesseract", path, "-", "--psm", str(psm)]
        if wl:
            cmd += ["-c", f"tessedit_char_whitelist={_WHITELIST}"]
        texts.append(subprocess.run(cmd, capture_output=True, text=True).stdout)
    return texts


def fetch(slot_id: str, date: dt.date) -> tuple[Draw | None, str | None]:
    """Returns (draw or None, pdf_url or None). Draw is None when OCR has no consensus."""
    slot = SLOTS[slot_id]
    url = PDF_URL.format(code=slot.file_code, ddmmyy=date.strftime("%d%m%y"))
    pdf = get_bytes(url)
    if pdf is None or not pdf.startswith(b"%PDF"):
        return None, None
    with tempfile.TemporaryDirectory() as wd:
        pdf_path = os.path.join(wd, "r.pdf")
        with open(pdf_path, "wb") as f:
            f.write(pdf)
        subprocess.run(["pdfimages", "-j", pdf_path, os.path.join(wd, "img")], check=True)
        jpgs = sorted(glob.glob(os.path.join(wd, "img-*.jpg")))
        if not jpgs:
            return None, url
        texts = _ocr_variants(jpgs[0], wd)

    head = texts[0]
    name_m = _NAME_RE.search(re.sub(r"\s+", " ", head))
    name = name_m.group(1) if name_m else slot.label.upper()
    no_m = _DRAWNO_RE.search(head)
    draw_no = int(no_m.group(1)) if no_m else None
    parser = parse_d5 if slot.scheme.ticket_format == "D5" else parse_d4

    reads = []
    for t in texts:
        try:
            reads.append(parser(t, slot_id, date, name, draw_no))
        except ValidationError:
            reads.append(None)
    valid = [r for r in reads if r is not None]
    # Every OCR pass must succeed and agree number-for-number.
    if len(valid) == len(reads) and len({r.numbers_key() for r in valid}) == 1:
        draw = valid[0]
        draw.sources.append({"kind": "official", "url": url, "ocr": True})
        return draw, url
    return None, url
