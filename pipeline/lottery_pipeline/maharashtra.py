"""Maharashtra State Lottery results, read from the official result sheets.

lottery.maharashtra.gov.in/mhlotteryresults.aspx lists one scanned PDF per draw (weekly draws at
~4-5 PM, monthly draws and bumpers). The sheets have no text layer, so each one is read by OCR
several times (different resolutions and clean-ups) and a tier is published only when two
readings agree on every number and the count printed on the sheet ("10 Numbers", "300 Random
Numbers"). The big "random" tiers are printed in ascending order down the columns, which is
checked too. Anything less and the draw is published as a link to the official PDF only.

Ticket numbers look like "GL-00 2142": a series (two letters, two digits) and a 4-digit number.
The 1st prize is for one series; the other tiers are "common to all series" (any series wins).
"""
from __future__ import annotations

import datetime as dt
import html
import os
import re
import subprocess
import tempfile
import urllib.parse
from collections import Counter

from .http import get_bytes

SITE = "https://lottery.maharashtra.gov.in/"
LIST_URL = SITE + "mhlotteryresults.aspx"

ORD = {"1": "1st", "2": "2nd", "3": "3rd"}
# "5th" is sometimes read "Sth", "4th" "Ath", "1st" "ist"/"lst": the ordinal is normalised below.
_TIER = re.compile(r"\b([0-9SAIil]{1,2})\s*(st|nd|rd|th)\b[^|]{0,14}?[Pp]rize|\b([Cc]onsolation)\s*[Pp]rize"
                   r"|(?<![A-Za-z])\S{0,3}\s?(th)\s+[Pp]rize\s+[Rr]s")  # "qm th Prize Rs.110/-": the 7 lost
_ORD_FIX = str.maketrans({"S": "5", "A": "4", "I": "1", "i": "1", "l": "1"})
_AMOUNT = re.compile(r"(?i)rs\.?\s*([\d,]{3,})")
_COUNT = re.compile(r"(?i)\b(\d{1,3})\s*(?:r[a-z]{3,6}\.?\s*)?n[a-z]{4,7}\b")  # "300 Randorn Numbers", "10 Nurnbers"
_SERIES = re.compile(r"\b([A-Z]{2})\s?[-.]\s?(\d{2})\b")
_TITLE = re.compile(r"(?i)maharashtra\W+([a-z][a-z .\-]+?)\W+(weekly|monthly|bumper)\W+lottery")
_DATE = re.compile(r"(?i)date\s*of\s*draw\s*[:.]?\s*(\d{1,2})\s*[.\-/]\s*(\d{1,2})\s*[.\-/]\s*(\d{4})")
_DRAWNO = re.compile(r"(?i)draw\s*no\.?\s*[:.]?\s*(\d{1,4})")
_TIME = re.compile(r"(?i)time\s*of\s*draw\s*[:.]?\s*(\d{1,2})\s*[.:]\s*(\d{2})\s*([ap])")


class MaharashtraError(ValueError):
    pass


# ---------------------------------------------------------------- the list of result sheets

def list_sheets(page: str) -> list[tuple[dt.date, str, str]]:
    """(date, Marathi name, absolute PDF url) for every row of the results page, newest first."""
    out = []
    for row in re.findall(r"<tr[^>]*>(.*?)</tr>", page, re.S | re.I):
        link = re.search(r'href="([^"]+\.pdf)"', row, re.I)
        when = re.search(r"(\d{2})/(\d{2})/(\d{4})", row)
        if not link or not when:
            continue
        cells = [html.unescape(re.sub(r"<[^>]+>|\s+", " ", c)).strip()
                 for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S | re.I)]
        href = html.unescape(link.group(1))
        url = urllib.parse.urljoin(LIST_URL, urllib.parse.quote(href, safe="/:.%_-"))
        d, m, y = map(int, when.groups())
        out.append((dt.date(y, m, d), cells[0] if cells else "", url))
    return out


# ---------------------------------------------------------------- OCR

def _render(pdf: bytes, dpi: int, work: str):
    import cv2
    src = os.path.join(work, "s.pdf")
    with open(src, "wb") as f:
        f.write(pdf)
    subprocess.run(["pdftoppm", "-r", str(dpi), "-gray", "-png", "-singlefile", "-f", "1", "-l", "1",
                    src, os.path.join(work, f"r{dpi}")], check=True, capture_output=True, timeout=120)
    return cv2.imread(os.path.join(work, f"r{dpi}.png"), 0)


def _grid(gray, dpi: int):
    """The cells the table's ruling lines enclose as (x, y, w, h), the page without those lines, and
    the heights of the rules that cross the whole table (they separate one prize from the next)."""
    import cv2
    import numpy as np
    b = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 31, 15)
    h = cv2.morphologyEx(b, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (dpi // 8, 1)))
    v = cv2.morphologyEx(b, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (1, dpi // 10)))
    lines = cv2.dilate(h | v, np.ones((5, 5), np.uint8))
    ys, xs = np.nonzero(lines)
    if len(xs):  # close the outer frame: a faint right edge would merge cells with the margin
        cv2.rectangle(lines, (int(xs.min()), int(ys.min())), (int(xs.max()), int(ys.max())), 255, 7)
    n, _, st, _ = cv2.connectedComponentsWithStats(255 - lines, connectivity=4)
    H = gray.shape[0]
    cells = [tuple(int(x) for x in st[i][:4]) for i in range(1, n)
             if st[i][2] > dpi // 7 and st[i][3] > dpi // 10 and st[i][3] < H * 0.4
             and st[i][0] > 0 and st[i][1] > 0]
    # Text without the ruling lines (they confuse the digit reader); only long lines are removed.
    lh = cv2.morphologyEx(b, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (dpi // 5, 1)))
    lv = cv2.morphologyEx(b, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (1, dpi // 5)))
    clean = 255 - (b & ~cv2.dilate(lh | lv, np.ones((3, 3), np.uint8)))
    rules = []
    if len(xs):
        # a skewed scan's rule spans a few pixel rows (the 27-10-2026 Padmini sheet: ~15 at 400 dpi)
        across = (cv2.dilate(h, np.ones((max(7, dpi // 25), 1), np.uint8)) > 0).sum(axis=1)
        full = np.nonzero(across >= (xs.max() - xs.min()) * 0.85)[0]
        for y in full:
            if rules and y - rules[-1][-1] <= 2:
                rules[-1].append(int(y))
            else:
                rules.append([int(y)])
        rules = [sum(r) / len(r) for r in rules]
    return cells, clean, rules


def _words(img, work: str) -> list[tuple]:
    """Tesseract words with boxes: (text, x, y, w, h, line id)."""
    import cv2
    p = os.path.join(work, "w.png")
    cv2.imwrite(p, img)
    out = subprocess.run(["tesseract", p, "-", "--psm", "6", "tsv"], capture_output=True, text=True, timeout=180).stdout
    words = []
    for line in out.splitlines()[1:]:
        f = line.split("\t")
        if len(f) == 12 and f[11].strip():
            words.append((f[11].strip(), int(f[6]), int(f[7]), int(f[8]), int(f[9]), (f[2], f[3], f[4])))
    return words


def _inside(word, cell) -> bool:
    x, y, w, h = word[1:5]
    cx, cy = x + w / 2, y + h / 2
    X, Y, W, H = cell
    return X <= cx <= X + W and Y <= cy <= Y + H


def _cell_numbers(gray, cells, dpi: int, work: str) -> tuple[list, list]:
    """Read every number-sized cell on its own (a lone number in a box is what OCR reads best).
    The crops are stacked into strips, so a page costs a few Tesseract runs instead of hundreds.
    Returns ([(number, cx, cy, w)], the cells that gave a number)."""
    import cv2
    import numpy as np
    H, W = gray.shape
    # Number boxes: 0.3 inch (random tiers) to 0.75 inch tall (the 1st prize in its series column).
    small = [c for c in cells if dpi * 0.25 < c[2] < W / 5 and dpi * 0.12 < c[3] < dpi * 1.0]
    if not small:
        return [], []
    def strip_pass(idx: list[int], hh: int, gap: int, psm: str) -> dict[int, str]:
        crops = []
        for i in idx:
            x, y, w, h = small[i]
            # Barely trimmed: big 1st-prize digits fill their box edge to edge (a 6% trim cut "6840" to "84").
            iy = max(3, int(h * 0.06))
            c = gray[y + iy:y + h - iy, x + 3:x + w - 3]
            crops.append(cv2.resize(c, (max(1, int(c.shape[1] * hh / c.shape[0])), hh)) if c.size
                         else np.full((hh, hh), 255, np.uint8))
        found: dict[int, list[str]] = {}
        chunk = 60  # crops per Tesseract run: one strip of all 300+ cells is taller than it reads (~39,000 px)
        for start in range(0, len(crops), chunk):
            part = crops[start:start + chunk]
            width = max(c.shape[1] for c in part) + 2 * gap
            strip = np.full((len(part) * (hh + gap) + gap, width), 255, np.uint8)
            for i, c in enumerate(part):
                strip[gap + i * (hh + gap):gap + i * (hh + gap) + hh, gap:gap + c.shape[1]] = c
            p = os.path.join(work, "strip.png")
            cv2.imwrite(p, strip)
            out = subprocess.run(["tesseract", p, "-", "--psm", psm, "-c", "tessedit_char_whitelist=0123456789-", "tsv"],
                                 capture_output=True, text=True, timeout=180).stdout
            for line in out.splitlines()[1:]:
                f = line.split("\t")
                if len(f) == 12 and f[11].strip():
                    i = (int(f[7]) + int(f[9]) // 2 - gap // 2) // (hh + gap)
                    if 0 <= i < len(part):
                        found.setdefault(idx[start + i], []).append(f[11].strip())
        return {i: re.sub(r"\D", "", "".join(ws)) for i, ws in found.items()}  # "6840-": a dash from the box edge

    # A block of lines first; the boxes it skipped (it drops a line whose size differs, e.g. the big
    # 1st prize) are read again as sparse text.
    got = strip_pass(list(range(len(small))), 90, 40, "6")
    todo = [i for i in range(len(small)) if not re.fullmatch(r"\d{4}", got.get(i, ""))]
    if todo:
        got.update({i: t for i, t in strip_pass(todo, 70, 60, "11").items() if re.fullmatch(r"\d{4}", t)})
    nums, done = [], []
    for i, t in got.items():
        if re.fullmatch(r"\d{4}", t):
            x, y, w, h = small[i]
            nums.append((t, x + w / 2, y + h / 2, w))
            done.append(small[i])
    # Big boxes (the 1st prize and its consolation, 0.45-1 inch tall; on "Series | Number" sheets
    # the number box is also wide): each read alone, scaled so its digits are ~40 px tall, which
    # read all six test boxes right (the strip misread bold "2142" as "9142").
    for c in cells:
        x, y, w, h = c
        if not (dpi * 0.38 < h < dpi * 1.0 and dpi * 0.25 < w < W / 3):
            continue
        t = _read_box(gray[y + 3:y + h - 3, x + 3:x + w - 3], work)
        if t:
            nums = [n for n in nums if (n[1], n[2]) != (x + w / 2, y + h / 2)] + [(t, x + w / 2, y + h / 2, w)]
            done.append(c)
    return nums, done


def _read_box(crop, work: str, label: bool = False) -> str | None:
    """One box read alone, its ink scaled to ~40 px: a 4-digit number, or (label=True) a series "SM-06"."""
    import cv2
    import numpy as np
    ys, xs = np.nonzero(crop < 128)
    if len(ys) < 50:
        return None
    for ink in (40, 52):
        sc = ink / (ys.max() - ys.min() + 1)
        r = cv2.resize(crop, (max(1, int(crop.shape[1] * sc)), max(1, int(crop.shape[0] * sc))), interpolation=cv2.INTER_AREA)
        pad = np.full((r.shape[0] + 60, r.shape[1] + 60), 255, np.uint8)
        pad[30:30 + r.shape[0], 30:30 + r.shape[1]] = r
        p = os.path.join(work, "box.png")
        cv2.imwrite(p, pad)
        chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789" if label else "0123456789"
        raw = subprocess.run(["tesseract", p, "-", "--psm", "7", "-c", f"tessedit_char_whitelist={chars}"],
                             capture_output=True, text=True, timeout=60).stdout
        if label:
            if m := re.fullmatch(r"\s*([A-Z]{2})-?(\d{2})\s*", raw):
                return f"{m.group(1)}-{m.group(2)}"
            continue
        t = re.sub(r"\D", "", raw)
        if re.fullmatch(r"\d{4}", t):
            return t
    return None


_NUMWORD = re.compile(r"[^\w]*\d{4}(?:[^\w]+\d{4})*[^\w]*")


def _numbers_in(word) -> list[tuple[str, float, float]]:
    """4-digit numbers in one OCR word, with their centres: "2137'|" -> 2137, "5053:5636" -> two,
    "26083730" (two cells read as one word) -> two."""
    t, x, y, w, h = word[:5]
    if re.fullmatch(r"\d{8}", t):
        groups = [t[:4], t[4:]]
    elif _NUMWORD.fullmatch(t):
        groups = re.findall(r"\d{4}", t)
    else:
        return []
    k = len(groups)
    return [(g, x + w * (2 * i + 1) / (2 * k), y + h / 2) for i, g in enumerate(groups)]


def _lead(ws) -> int:
    """Where a line's text starts (specks of the table's left rule are read as '_' or ',')."""
    return next((w[1] for w in ws if re.search(r"\w\w", w[0])), ws[0][1])


def _ordinal(digits: str, suffix: str) -> str:
    """'5th' -> '5'; OCR slips: 'Sth' -> '5', 'ist' -> '1', '4st' -> '1' (only 1 ends in -st)."""
    n = int(digits.translate(_ORD_FIX))
    want = {1: "st", 2: "nd", 3: "rd"}.get(n % 10 if n % 100 not in (11, 12, 13) else 0, "th")
    if want != suffix and suffix in ("st", "nd", "rd") and n < 10:
        n = {"st": 1, "nd": 2, "rd": 3}[suffix]
    return str(n)


def _text_alone(crop) -> str:
    """Tesseract on one region of the page alone (a heading beside its grid of numbers reads clean
    alone: "(350 Random Numbers)" where the full page gave "P20: Reman Runt")."""
    import cv2
    with tempfile.TemporaryDirectory() as work:
        p = os.path.join(work, "h.png")
        cv2.imwrite(p, crop)
        return subprocess.run(["tesseract", p, "-", "--psm", "4"], capture_output=True, text=True, timeout=60).stdout


def read_once(pdf: bytes, dpi: int, clean: bool) -> dict:
    """One reading of a sheet. Raises MaharashtraError if the sheet does not make sense."""
    with tempfile.TemporaryDirectory() as work:
        gray = _render(pdf, dpi, work)
        cells, cleaned, rules = _grid(gray, dpi)
        words = _words(gray, work)                                # headings, series, date
        num_words = _words(cleaned, work) if clean else words     # the numbers
        cell_nums, read_cells = _cell_numbers(cleaned if clean else gray, cells, dpi, work)
    H, W = gray.shape
    lines: dict = {}
    for wd in words:
        lines.setdefault(wd[5], []).append(wd)
    lines = sorted((sorted(ws, key=lambda w: w[1]) for ws in lines.values()), key=lambda ws: min(w[2] for w in ws))
    full = " ".join(" ".join(w[0] for w in ws) for ws in lines)

    head: dict = {}
    if m := _TITLE.search(full):
        head["name"] = re.sub(r"[^A-Za-z ]", " ", m.group(1)).split()
        head["kind"] = m.group(2).lower()
    if m := _DATE.search(full):
        d, mo, y = map(int, m.groups())
        head["date"] = dt.date(y, mo, d).isoformat()
    if m := _DRAWNO.search(full):
        head["drawNo"] = int(m.group(1))
    if m := _TIME.search(full):
        hh, mm, ap = int(m.group(1)), int(m.group(2)), m.group(3).lower()
        head["time"] = f"{hh + 12 if ap == 'p' and hh < 12 else hh:02d}:{mm:02d}"

    # Prize headings. A heading in its own cell (e.g. the 7th prize, left of its 300 numbers)
    # starts at the cell's top; numbers inside a heading cell count only right of its words.
    tiers: dict[str, dict] = {}
    for li, ws in enumerate(lines):
        text = " ".join(w[0] for w in ws)
        m = _TIER.search(text)
        if not m:
            continue
        rank = "CONS" if m.group(3) else "?" if m.group(4) else _ordinal(m.group(1), m.group(2))
        first = next(w for w in ws if m.group(0).split()[0] in w[0]) if m.group(0).split() else ws[0]
        cell = next((c for c in cells if _inside(first, c) and c[3] < H * 0.4), None)
        region = " ".join(w[0] for w in words if cell and _inside(w, cell)) if cell else text[m.start():]
        if not cell or not _AMOUNT.search(region):
            # A heading whose box was not found (faint rule): the amount and count are on its next lines,
            # which start where it starts ("1st (Common) Prize" / "Rs.27,00,000/- (Full Digit)").
            x0 = first[1]
            below = [nxt for nxt in lines[li + 1:li + 4] if abs(nxt[0][1] - x0) < W * 0.08]
            region = text[m.start():] + " " + " ".join(" ".join(w[0] for w in nxt) for nxt in below)
        top = min(cell[1], min(w[2] for w in ws)) if cell else min(w[2] for w in ws)
        line_top = min(w[2] for w in ws)
        rule = max((r for r in rules if r <= line_top), default=None)
        if not cell or cell[3] < dpi * 0.4:  # no box, or one around this line only: start at its first line
            for prev in reversed(lines[:li]):
                if _lead(prev) > W * 0.5 or not any(re.search(r"\w\w", w[0]) for w in prev):
                    continue  # the series/number boxes beside the heading, or specks read as a line
                prev_text = " ".join(w[0] for w in prev if w[1] < W * 0.5)  # the heading side, not its number boxes
                left_text = " ".join(w[0] for w in prev if w[1] < W * 0.25)  # where a row of prizes would start
                if re.search(r"(?i)\brank\b|series|[A-Z]{2}\s?[-.]\s?\d{2}", prev_text) or _TIER.search(prev_text) \
                        or _AMOUNT.search(prev_text) or len(re.findall(r"\b\d{4}\b", left_text)) >= 2:
                    break  # the series labels, the heading above ("Rs. ..."), or a row of prize numbers
                if line_top - min(w[2] for w in prev) > dpi * 0.8:
                    break  # a heading is at most four lines tall
                if top - max(w[2] + w[4] for w in prev) > dpi * 0.3 or abs(_lead(prev) - _lead(ws)) > W * 0.03:
                    break
                top = min(w[2] for w in prev)
        amt = _AMOUNT.findall(region)
        cnt = _COUNT.search(region)
        num_word = next((w for w in ws if re.match(r"(?i)numbers?\b", w[0])), None)
        band = (min(w[2] for w in ws), max(w[2] + w[4] for w in ws))
        t = {"rank": rank, "top": top, "cell": cell, "band": band, "line_top": line_top,
             "rule": rule if rule is not None and line_top - rule < dpi * 1.3 else None,  # "7th Prize" is its 5th line
             "after": (num_word[1] + num_word[3]) if num_word else
             max(w[1] + w[3] for w in ws if re.search(r"[A-Za-z]", w[0])),
             "prize": int(amt[0].replace(",", "")) if amt else None, "count": int(cnt.group(1)) if cnt else None,
             "numbers": []}
        if rank not in tiers or top < tiers[rank]["top"]:
            tiers[rank] = t
    if not tiers:
        raise MaharashtraError("no prize headings")
    for t in list(tiers.values()):
        if t["rank"] != "?" and t["prize"] is not None and (t["count"] is not None or t["rank"] in ("1", "CONS")):
            continue
        # Read its heading again alone: the cell it is in (left of a random tier's grid), else the
        # left third of the page from its line down.
        c = t["cell"]
        if c and c[2] < W / 2 and c[3] > dpi * 0.5:
            x0, y0, x1, y1 = c[0], c[1], c[0] + c[2], c[1] + c[3]
        else:
            x0, y0, x1, y1 = 0, int(t["line_top"] - dpi * 0.05), int(W * 0.35), int(t["line_top"] + dpi * 0.6)
        text = _text_alone(gray[max(0, y0):y1, x0:x1])
        m = _TIER.search(text)
        if not m:
            continue
        rest = text[m.start():]
        if t["rank"] == "?" and m.group(1):
            rank = _ordinal(m.group(1), m.group(2))
            if rank not in tiers:
                tiers[rank] = dict(t, rank=rank)
                del tiers["?"]
                t = tiers[rank]
        if t["prize"] is None and (amt := _AMOUNT.findall(rest)):
            t["prize"] = int(amt[0].replace(",", ""))
        if t["count"] is None and (cnt := _COUNT.search(rest)):
            t["count"] = int(cnt.group(1))
    for t in tiers.values():
        # The rule across the table above a heading: its prize's rows start there, whatever lines of
        # the heading OCR missed (the Marathi first line above "Consolation Prize" on the 27-10-2026
        # Padmini sheet: its row of numbers sat above the line that was read). Not when another
        # heading lies between them: that rule was too faint to find.
        if t["rule"] is not None and not any(t["rule"] < o["line_top"] < t["line_top"] for o in tiers.values()):
            t["top"] = t["rule"]
    order = sorted(tiers.values(), key=lambda t: t["top"])
    panel = next((w[2] for w in words if re.fullmatch(r"(?i)panel", w[0])), H)

    series = []
    for w in words:
        if m := re.fullmatch(r"[^\w]*([A-Z]{2})\s?-\s?(\d{2})[^\w]*", w[0]):
            series.append((f"{m.group(1)}-{m.group(2)}", w[1] + w[3] / 2, w[2] + w[4] / 2, w[3]))
    # The label row is often misread ("PWLO1", "PM-O3"): its digits still give each column.
    loose = []
    for w in words:
        if m := re.fullmatch(r"[^\w]*([A-Z]{2,3})[^0-9OoQ]{0,2}([0-9OoQ]{2})[^\w]*", w[0]):
            loose.append((int(m.group(2).translate(str.maketrans("OoQ", "000"))), w[1] + w[3] / 2, w[2] + w[4] / 2))
    cols = _series_columns(series, loose, H)
    # Two layouts: a "Series | Number" box for the 1st prize (GL-00 | 2142), or the 1st prize under its
    # series column (TJ-01 over 6840). In the first, the series is only ever read from its own box:
    # the number box happens to stand under some series column too.
    boxed_series = any(re.search(r"(?i)series\W.{0,40}\bnumber\b", " ".join(w[0] for w in ws)) for ws in lines)
    prefix = cols["prefix"] if cols else (Counter(x[0][:2] for x in series).most_common(1) or [("", 0)])[0][0]
    head["series"] = cols["all"] if cols else sorted({s for s, *_ in series})

    found = [(n, cx, cy, ww, False, True) for n, cx, cy, ww in cell_nums]
    for wd in num_words:  # numbers outside single cells: merged cells of the big random tiers
        for n, cx, cy in _numbers_in(wd):
            if not any(c[0] <= cx <= c[0] + c[2] and c[1] <= cy <= c[1] + c[3] for c in read_cells):
                k = len(_numbers_in(wd))  # k > 1: positions inside the word are estimates
                found.append((n, cx, cy, wd[3] / k, k > 1, False))
    widths = sorted(f[3] for f in found)
    typical = widths[len(widths) // 2] if widths else 0
    for n, cx, cy, ww, approx, boxed in found:
        approx = approx or ww > typical * 1.6  # from a box wider than one number: its centre is not the number's
        if cy >= panel:
            continue
        owner = None
        for t in order:
            if t["top"] <= cy:
                owner = t
        if owner is None:
            continue
        c, (lo, hi) = owner["cell"], owner["band"]
        if not boxed:  # a number alone in its own box is never part of a heading's text
            if c and c[2] < W / 2 and c[1] <= cy <= c[1] + c[3] and c[0] <= cx <= c[0] + c[2]:
                continue  # inside a heading cell beside its numbers (e.g. a misread amount)
            if lo - dpi * 0.05 <= cy <= hi + dpi * 0.05 and cx < owner["after"]:
                continue  # on the heading's own line, left of its end: part of the heading text
        owner["numbers"].append((n, cx, cy, ww, approx))

    def series_for(cx, cy):
        got = _series_of(cx, cy, loose, prefix, None if boxed_series else cols, dpi)
        if boxed_series:  # its own box, left of the number in the same row, read alone like the number
            left = [c for c in cells if c[1] <= cy <= c[1] + c[3] and c[0] + c[2] <= cx and c[3] > dpi * 0.3]
            own = next((c for c in cells if c[0] <= cx <= c[0] + c[2] and c[1] <= cy <= c[1] + c[3]), None)
            if not left and own:  # its rules too faint to make a box: the same-sized space left of the number
                left = [(max(0, own[0] - own[2]), own[1], own[2] - 6, own[3])]
            if left:
                x, y, w, h = max(left, key=lambda c: c[0])
                with tempfile.TemporaryDirectory() as work2:
                    box = _read_box(cleaned[y + 3:y + h - 3, x + 3:x + w - 3], work2, label=True)
                if box and (not prefix or box[:2] == prefix):
                    return box
        return got

    numbers = [(n, cx / W, cy / H, ww / W, series_for(cx, cy) if t["rank"] in ("1", "CONS") else None, approx)
               for t in order for n, cx, cy, ww, approx in t["numbers"]]
    headings = [{"rank": t["rank"], "top": t["top"] / H, "prize": t["prize"], "count": t["count"]} for t in order]
    return {"head": head, "headings": headings, "numbers": numbers}


def _series_columns(series, loose, H: int) -> dict | None:
    """The row of series labels over the table (GL-00 ... GL-09) is evenly spaced: fit column = a + b*k
    to the labels that were read, so a number can be placed under its series even where a label
    was misread ("TJ.gQ" for TJ-00). The prefix comes only from labels read exactly; the column
    positions also from loosely read ones ("PWLO1" is column 1). None unless 6 columns fit closely."""
    if not series or len(loose) < 6:
        return None
    # The label row: the height at which most labels were read (a stray match elsewhere is ignored).
    top = max((s[2] for s in loose), key=lambda y: sum(abs(t[2] - y) < H * 0.012 for t in loose))
    row = [s for s in loose if abs(s[2] - top) < H * 0.012 and s[0] <= 9]
    exact = [s[0][:2] for s in series if abs(s[2] - top) < H * 0.012]
    if not exact:
        return None
    prefix, n = Counter(exact).most_common(1)[0]
    if n < len(exact) / 2:
        return None
    pts = sorted({k: x for k, x, _ in row}.items())
    if len(pts) < 6:
        return None
    ks, xs = [k for k, _ in pts], [x for _, x in pts]
    mk, mx = sum(ks) / len(ks), sum(xs) / len(xs)
    b = sum((k - mk) * (x - mx) for k, x in pts) / sum((k - mk) ** 2 for k in ks)
    a = mx - b * mk
    if b <= 0 or any(abs(x - (a + b * k)) > b * 0.25 for k, x in pts):
        return None
    return {"prefix": prefix, "a": a, "b": b, "y": top, "all": [f"{prefix}-{k:02d}" for k in range(10)]}


def _series_of(x: float, y: float, loose, prefix: str, cols: dict | None, dpi: int) -> str | None:
    """The series a 1st-prize/consolation number belongs to: the series box in its own row, left of
    it (GL-00 | 2142); else, where the sheet prints it so (`cols` given), the series column it
    stands under (TJ-01 over 6840)."""
    label_row = cols["y"] if cols else None
    row = [s for s in loose if abs(s[2] - y) < dpi * 0.2 and s[1] < x and s[0] <= 9
           and (label_row is None or s[2] > label_row + dpi * 0.2)]
    if row and prefix:
        return f"{prefix}-{max(row, key=lambda s: s[1])[0]:02d}"
    if cols and y > cols["y"]:
        k = round((x - cols["a"]) / cols["b"])
        if 0 <= k <= 9 and abs(x - (cols["a"] + cols["b"] * k)) < cols["b"] * 0.3:
            return f"{cols['prefix']}-{k:02d}"
    return None


def _printed_order(nums, count: int | None = None) -> list | None:
    """The numbers of a random tier in printed order: down each column, column after column.
    Columns and rows are found from the positions (x, y, w as page fractions) of all numbers at
    once, so a number missing from one row cannot shift the rest of that row into the wrong column.
    With the printed `count`, a place no reading found is returned as ("", x, y, w) where the grid
    has it (columns of equal length, the last one shorter: 350 = 19 columns of 18 and one of 8).
    None if the numbers do not form that grid (two in one place, one outside it)."""
    if not nums:
        return []
    width = sorted(n[3] for n in nums)[len(nums) // 2]

    def bands(values, gap):
        idx, k, prev = {}, -1, None
        for v in sorted(set(values)):
            if prev is None or v - prev > gap:
                k += 1
            idx[v], prev = k, v
        return idx
    col = bands([n[1] for n in nums], width * 0.5)
    row = bands([n[2] for n in nums], 0.008)  # page fractions: printed rows are >= 0.016 apart
    size = {k: sum(col[n[1]] == k for n in nums) for k in set(col.values())}
    big = sorted(size.values())[len(size) // 2]
    cells: dict = {}
    stray = []
    for n in nums:
        key = (col[n[1]], row[n[2]])
        if size[key[0]] * 4 < big:  # a column of one or two: a position estimated from a merged OCR word
            stray.append(n)
        elif key in cells:
            # Two in one place: one read by few readings (positions estimated from merged words, values
            # split) beside one read by twice as many is not a number of its own. If it was one, its
            # place shows up as a hole (count) and is read again there.
            votes = lambda x: sum(x[5].values()) if len(x) > 5 else 0  # noqa: E731
            a, b = sorted((cells[key], n), key=votes)
            if votes(b) < 2 * votes(a):
                return None
            cells[key] = b
        else:
            cells[key] = n
    cx = {k: sum(n[1] for kk, n in cells.items() if kk[0] == k) / sum(kk[0] == k for kk in cells) for k in {kk[0] for kk in cells}}
    cy = {r: sum(n[2] for kk, n in cells.items() if kk[1] == r) / sum(kk[1] == r for kk in cells) for r in {kk[1] for kk in cells}}
    for n in stray:
        r = row[n[2]]
        if any(kk[1] == r and m[0] == n[0] for kk, m in cells.items()):
            continue  # the same number again, its position estimated wrong: already in its place
        order = sorted(cx)  # with the count: only places the printed grid has (the last column is short)
        free = sorted((k for k in cx if (k, r) not in cells
                       and (count is None or order.index(k) * len(cy) + sorted(cy).index(r) < count)),
                      key=lambda k: abs(cx[k] - n[1]))
        if not free or (len(free) > 1 and abs(cx[free[1]] - n[1]) < abs(cx[free[0]] - n[1]) + width):
            return None
        cells[(free[0], r)] = n
    # Number the columns and rows that hold numbers 0, 1, 2, ... (a band of strays leaves a gap). Without
    # the count a whole column missing could not be told from one never printed, so then none may be.
    if count is None and (sorted(cx) != list(range(len(cx))) or sorted(cy) != list(range(len(cy)))):
        return None
    ck, rk = {c: i for i, c in enumerate(sorted(cx))}, {r: i for i, r in enumerate(sorted(cy))}
    cells = {(ck[c], rk[r]): n for (c, r), n in cells.items()}
    cx, cy = {ck[c]: v for c, v in cx.items()}, {rk[r]: v for r, v in cy.items()}
    cols, rows = sorted(cx), sorted(cy)
    if count is None:
        want = set(cells)
        for c in cols:  # without the count: every column a run of rows from the top, none longer than the one before
            rs = sorted(r for k, r in cells if k == c)
            if rs != list(range(len(rs))):
                return None
        lens = [sum(k == c for k, _ in cells) for c in cols]
        if lens != sorted(lens, reverse=True) or len(set(lens)) > 2:
            return None
    else:
        R = len(rows)
        if not 0 < count <= R * len(cols) or count <= R * (len(cols) - 1):
            return None
        want = {(c, r) for c in cols for r in rows if c * R + r < count}
        if set(cells) - want:
            return None
    # Every place at its column's and row's centre, one number wide (a position estimated from a
    # merged word can sit between two numbers: re-reading it there reads neither).
    return [(cells[k][0], cx[k[0]], cy[k[1]], width) + tuple(cells[k][4:]) if k in cells else ("", cx[k[0]], cy[k[1]], width)
            for k in sorted(want)]


def _grid_size(nums) -> int | None:
    """Rows x columns of the grid the numbers stand in, found as in _printed_order (a column of one
    or two numbers is a position estimated wrong, not a column)."""
    if not nums:
        return None
    width = sorted(n[3] for n in nums)[len(nums) // 2]

    def bands(values, gap):
        out, prev = [], None
        for v in sorted(values):
            if prev is None or v - prev > gap:
                out.append(0)
            out[-1] += 1
            prev = v
        return out
    cols = bands([n[1] for n in nums], width * 0.5)
    big = sorted(cols)[len(cols) // 2]
    return len(bands([n[2] for n in nums], 0.008)) * sum(c * 4 >= big for c in cols)


def _column_order_ok(nums, count: int | None = None) -> bool:
    """Random tiers are printed in ascending order down each column, column after column."""
    if len(nums) < 20:
        return True
    seq = _printed_order(nums, count)
    return seq is not None and all(x[0] for x in seq) and all(int(a[0]) < int(b[0]) for a, b in zip(seq, seq[1:]))


# ---------------------------------------------------------------- agreement between readings

# Line-free images read the numbers best (grey ones missed whole rows of the 27-10-2026 Tejaswini sheet).
READINGS = ((400, True), (300, True), (500, True), (350, True), (450, True), (550, True))


def read(pdf: bytes, warn=print) -> dict:
    """Read the sheet several ways; publish only what the readings agree on and what a second,
    different reader (the sheet's own digit shapes, see Glyphs) confirms (see _agree)."""
    runs = []
    for dpi, clean in READINGS:
        try:
            runs.append(read_once(pdf, dpi, clean))
        except MaharashtraError as e:
            warn(f"Maharashtra OCR {dpi} dpi{' clean' if clean else ''}: {e}")
        except Exception as e:  # OCR tool errors and image decoder limits
            warn(f"Maharashtra OCR {dpi} dpi failed: {type(e).__name__}: {e}")
    if len(runs) < 3:
        raise MaharashtraError("fewer than three usable readings")
    return _agree(runs, Glyphs.from_pdf(pdf))


class Glyphs:
    """A second reader that does not share Tesseract's mistakes: the sheet's own digit shapes.

    Tesseract reads this font's 1 (it has a long flag) as 4 again and again (1542 -> 4542, 1021 ->
    4021, 2142 -> 9142 on the 27-10-2026 sheets), so more readings do not outvote it. Every sheet
    prints a few hundred numbers in one font; the ones all readings read the same way teach this
    reader what 0-9 look like *on this sheet*, and it then reads a number by comparing its four
    digit shapes with those. A number is published only where both readers agree."""

    SIZE = (14, 20)

    def __init__(self, img, cells):
        self.img, self.cells = img, cells
        self.H, self.W = img.shape
        self.templates: dict[str, object] = {}

    @classmethod
    def from_pdf(cls, pdf: bytes, dpi: int = 400) -> "Glyphs | None":
        try:
            with tempfile.TemporaryDirectory() as work:
                gray = _render(pdf, dpi, work)
                cells, _, _ = _grid(gray, dpi)
            return cls(gray, cells)  # the grey scan: removing ruling lines also breaks digit strokes
        except Exception:  # noqa: BLE001 - without it, numbers just need the stricter Tesseract vote
            return None

    def _box(self, x: float, y: float, w: float):
        """The crop around the number at (x, y), and the height of its centre in the crop."""
        cx, cy, half = x * self.W, y * self.H, w * self.W * 0.75  # a word box can end at the last digit
        cell = next((c for c in self.cells if c[0] <= cx <= c[0] + c[2] and c[1] <= cy <= c[1] + c[3]
                     and c[3] < self.H * 0.1), None)
        if cell is None:  # no box found around it: a window the size of a number
            x0, x1, y0, y1 = int(cx - half), int(cx + half), int(cy - half * 0.7), int(cy + half * 0.7)
        else:  # a little more than the box: a faint rule can make it shorter than its digits (2802 cut at the foot)
            y0, y1 = cell[1] - cell[3] // 4, cell[1] + cell[3] + cell[3] // 4
            x0, x1 = ((cell[0] - cell[2] // 10, cell[0] + cell[2] + cell[2] // 10) if cell[2] < w * self.W * 2.2
                      else (int(cx - half), int(cx + half)))  # and wider: "2802" ended past its box's right rule
        y0, x0 = max(0, y0), max(0, x0)
        return self.img[y0:max(0, y1), x0:max(0, x1)], cy - y0

    @staticmethod
    def _digits(lab, st, n: int, shape, cy: float, k: int = 4, groups: bool = True) -> list | None:
        """The k characters of the number, left to right, as masks: one line of characters of one
        height (a box whose rule is too faint also holds the heading text above it), the group of k
        nearest the number's centre (a window may hold part of the next number). Two digits printed
        touching ("44") are cut where they have the least ink. Not characters: ruling lines (as long
        as the crop is wide or tall), shapes cut by the crop's left or right side, specks."""
        import numpy as np
        ch, cw = shape
        comps = [i for i in range(1, n) if st[i][0] > 0 and st[i][0] + st[i][2] < cw and st[i][4] > 15
                 and st[i][2] < cw * 0.5 and st[i][3] < ch * 0.85 and st[i][3] > ch * 0.08
                 and st[i][2] > st[i][3] * 0.2]  # a dotted rule's dash: a 1 is twice as wide
        lines: list[list[int]] = []
        for i in sorted(comps, key=lambda i: st[i][1] + st[i][3] / 2):
            mid = st[i][1] + st[i][3] / 2
            ln = next((ln for ln in lines if abs(mid - (st[ln[0]][1] + st[ln[0]][3] / 2)) < st[ln[0]][3] * 0.4), None)
            if ln is None:
                lines.append([i])
            else:
                ln.append(i)
        best = None
        for ln in lines:
            top = max(st[i][3] for i in ln)
            ln = sorted((i for i in ln if st[i][3] >= top * 0.7), key=lambda i: st[i][0])
            if top > 1.3 * min(st[i][3] for i in ln):
                continue
            groups, cur = [], [ln[0]]
            for i in ln[1:]:
                if groups and st[i][0] - (st[cur[-1]][0] + st[cur[-1]][2]) > top * 0.5:  # the gap between two numbers
                    groups.append(cur)
                    cur = []
                cur.append(i)
            groups.append(cur)
            for grp in groups:
                chars = []
                narrow = sorted(st[i][2] for i in grp)[len(grp) // 2]
                for i in grp:
                    x, y, w, h = st[i][:4]
                    mask = lab[y:y + h, x:x + w] == i
                    parts = round(w / narrow) if len(grp) < k and w > narrow * 1.6 else 1
                    while parts > 1:  # cut off one digit where the ink is thinnest near its expected edge
                        cut_at = mask.shape[1] // parts
                        lo, hi = max(1, int(cut_at * 0.7)), min(mask.shape[1] - 1, int(cut_at * 1.3) + 1)
                        cut = lo + int(np.argmin(mask[:, lo:hi].sum(axis=0)))
                        chars.append(mask[:, :cut])
                        mask, parts = mask[:, cut:], parts - 1
                    chars.append(mask)
                if len(chars) != k:
                    continue
                d = abs(sum(st[i][1] + st[i][3] / 2 for i in grp) / len(grp) - cy)
                d += abs((st[grp[0]][0] + st[grp[-1]][0] + st[grp[-1]][2]) / 2 - cw / 2) * 0.5
                if best is None or d < best[0]:
                    best = (d, chars)
        return best[1] if best else None

    def _vec(self, mask):
        """One character as a unit vector: trimmed, centred in a 2:3 box, scaled to SIZE, lightly blurred."""
        import cv2
        import numpy as np
        ys, xs = np.nonzero(mask)
        if not len(ys):
            return np.zeros(self.SIZE[0] * self.SIZE[1], np.float32)
        g = mask[ys.min():ys.max() + 1, xs.min():xs.max() + 1].astype(np.float32)
        h, w = g.shape
        side = max(h, int(w * 1.5))
        pad = np.zeros((side, int(side / 1.5) + 1), np.float32)
        pad[(side - h) // 2:(side - h) // 2 + h, (pad.shape[1] - w) // 2:(pad.shape[1] - w) // 2 + w] = g
        v = cv2.GaussianBlur(cv2.resize(pad, self.SIZE, interpolation=cv2.INTER_AREA), (3, 3), 0).ravel()
        v = v - v.mean()
        return v / (np.linalg.norm(v) or 1)

    def _split(self, box, k: int = 4):
        """The k characters of a box, left to right, as vectors; None unless exactly k were found."""
        import cv2
        crop, cy = box
        if crop is None or crop.size == 0:
            return None
        _, b = cv2.threshold(crop, 0, 1, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        n, lab, st, _ = cv2.connectedComponentsWithStats(b, 8)
        chars = self._digits(lab, st, n, crop.shape, cy, k)
        return [self._vec(c) for c in chars] if chars else None

    def train(self, labelled) -> None:
        """labelled: [(value, x, y, w)] read the same way by every reading."""
        import numpy as np
        samples: dict[str, list] = {}
        for value, x, y, w in labelled:
            parts = self._split(self._box(x, y, w))
            if parts:
                for ch, v in zip(value, parts):
                    samples.setdefault(ch, []).append(v)
        self.templates = {ch: np.mean(vs, axis=0) for ch, vs in samples.items() if len(vs) >= 3}

    def read_series(self, x: float, y: float, w: float) -> str | None:
        """The two digits of the series box left of the number at (x, y) ("GL-08" -> "08")."""
        cx, cy = x * self.W, y * self.H
        own = next((c for c in self.cells if c[0] <= cx <= c[0] + c[2] and c[1] <= cy <= c[1] + c[3]
                    and c[3] < self.H * 0.1), None)
        if len(self.templates) < 10:
            return None
        if own is not None:
            left = [c for c in self.cells if c[1] <= cy <= c[1] + c[3] and c[0] + c[2] <= own[0] + 2 and c[3] < self.H * 0.1]
            box = max(left, key=lambda c: c[0]) if left else (own[0] - own[2], own[1], own[2] - 6, own[3])
        else:  # no box found around the number: the strip left of it, as wide as a "Series" column
            half = w * self.W * 0.6
            box = (int(cx - self.W * 0.3), int(cy - half * 0.9), int(self.W * 0.3 - half), int(half * 1.8))
        y0 = max(0, box[1])
        crop = self.img[y0:box[1] + box[3], max(0, box[0]):box[0] + box[2]]
        if crop.size == 0:
            return None
        import cv2
        _, b = cv2.threshold(crop, 0, 1, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        n, lab, st, _ = cv2.connectedComponentsWithStats(b, 8)
        for k in (5, 4):  # "GL-08" (the dash is too small to count) or "GL08"
            chars = self._digits(lab, st, n, crop.shape, cy - y0, k, groups=False)  # the dash leaves a gap
            if chars:
                out = ""
                for c in chars[-2:]:
                    ch_ = self._classify(self._vec(c))
                    if ch_ is None:
                        return None
                    out += ch_
                return out
        return None

    def tesseract(self, x: float, y: float, w: float) -> str | None:
        """Tesseract on the number at (x, y) alone (see _read_box): a second, independent reading of a
        place. Only its four characters' box is read (rules and the next number's edge cut it)."""
        import cv2
        crop, cy = self._box(x, y, w)
        if crop.size == 0:
            return None
        _, b = cv2.threshold(crop, 0, 1, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
        n, lab, st, _ = cv2.connectedComponentsWithStats(b, 8)
        if self._digits(lab, st, n, crop.shape, cy) is None:
            return None
        ch, cw = crop.shape
        comps = [i for i in range(1, n) if st[i][4] > 15 and st[i][0] > 0 and st[i][0] + st[i][2] < cw
                 and st[i][2] < cw * 0.5 and st[i][3] < ch * 0.85 and st[i][2] > st[i][3] * 0.2]
        mid = [i for i in comps if st[i][1] <= cy <= st[i][1] + st[i][3]]
        if not mid:
            return None
        x0, x1 = min(st[i][0] for i in mid), max(st[i][0] + st[i][2] for i in mid)
        y0, y1 = min(st[i][1] for i in mid), max(st[i][1] + st[i][3] for i in mid)
        pad = (y1 - y0) // 3
        tight = crop[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad].copy()
        keep = (lab[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad])
        tight[~sum((keep == i) for i in mid).astype(bool)] = 255  # only those characters, on white
        with tempfile.TemporaryDirectory() as work:
            return _read_box(tight, work)

    def _classify(self, v) -> str | None:
        import numpy as np
        scores = sorted(((float(np.dot(v, t / (np.linalg.norm(t) or 1))), ch) for ch, t in self.templates.items()),
                        reverse=True)
        (best, ch), (second, _) = scores[0], scores[1]
        return ch if best >= 0.8 and best - second >= 0.04 else None

    def read(self, x: float, y: float, w: float) -> str | None:
        """The number at (x, y), or None if a digit is unsure or this sheet taught no template for it."""
        parts = self._split(self._box(x, y, w))
        if not parts or len(self.templates) < 10:
            return None
        out = ""
        for v in parts:
            ch = self._classify(v)
            if ch is None:
                return None
            out += ch
        return out


def _vote(values: list, need: int = 2):
    """The value most readings gave: at least `need` of them, and at least twice as many as any other
    value (4 against 2 passes, 3 against 2 does not)."""
    c = Counter(values).most_common()
    if not c or c[0][1] < need or (len(c) > 1 and c[0][1] < 2 * c[1][1]):
        return None
    return c[0][0]


def _agree(runs: list[dict], glyphs: "Glyphs | None" = None) -> dict:
    """Combine the readings. Every heading and every printed number position must be read the same
    way by enough readings (or settled by the second reader `glyphs`); then the sheet's own counts
    and order must hold."""
    head = {}
    # Readings that fitted the whole label row give the ten series; partial lists do not vote.
    full = [r for r in runs if len(r["head"].get("series", [])) == 10]
    for k in ("name", "kind", "date", "drawNo", "time", "series"):
        v = _vote([repr(r["head"][k]) for r in (full if k == "series" else runs) if r["head"].get(k)])
        if v is not None:
            head[k] = eval(v)  # noqa: S307 - our own repr() of str/int/list
    if "series" not in head and full:
        # One reading fitted the whole label row; the others read parts of it. The ten series are one
        # prefix, 00-09: taken when every reading that read labels read that prefix (2 or more).
        prefixes = [Counter(x[:2] for x in r["head"]["series"]).most_common(1)[0][0] for r in runs if r["head"].get("series")]
        if len(prefixes) >= 2 and len(set(prefixes)) == 1 and all(r["head"]["series"][0][:2] == prefixes[0] for r in full):
            head["series"] = [f"{prefixes[0]}-{k:02d}" for k in range(10)]
    for k in ("name", "date", "series"):
        if k not in head:
            raise MaharashtraError(f"readings disagree on the sheet's {k}")

    # A heading is the same heading in every reading where it starts at the same height (all start
    # at the rule above them); its rank is what the readings that read one say, by a clear majority.
    # A heading seen in two readings whose ordinal none could read ("qm th Prize Rs.110/-") follows
    # the one above it; _check then holds the ranks to 1, 2, 3, ... with falling amounts.
    # (A heading whose rank was read joins the others of that rank wherever it starts: the 2nd prize
    # of the 27-10-2026 Sahyadri sheet starts at the rule above or below "Common To All Series".)
    groups: list[list] = []
    for i, r in enumerate(runs):
        for h in sorted(r["headings"], key=lambda h: h["rank"] == "?"):
            g = next((g for g in groups if h["rank"] != "?" and any(x["rank"] == h["rank"] for _, x in g)), None)
            g = g or next((g for g in groups if any(abs(x["top"] - h["top"]) < 0.012 for _, x in g)), None)
            (g.append((i, h)) if g is not None else groups.append([(i, h)]))
    headings = []
    for g in sorted(groups, key=lambda g: sorted(h["top"] for _, h in g)[len(g) // 2]):
        hs = [h for _, h in g]
        if len({i for i, _ in g}) < 2:
            continue  # seen by one reading only
        rank = _vote([h["rank"] for h in hs if h["rank"] != "?"], need=1)
        if rank is None and any(h["rank"] != "?" for h in hs):
            raise MaharashtraError(f"readings disagree on a prize rank ({[h['rank'] for h in hs]})")
        if rank is None:
            prev = headings[-1]["rank"] if headings else None
            if prev is None or not (prev.isdigit() or prev == "CONS"):
                continue
            rank = "2" if prev == "CONS" else str(int(prev) + 1)
        tops = sorted(h["top"] for h in hs)
        headings.append({"rank": rank, "top": tops[len(tops) // 2], "prize": _vote([h["prize"] for h in hs if h["prize"]]),
                         "count": _vote([h["count"] for h in hs if h["count"]]),
                         "counts": [c for c, _ in Counter(h["count"] for h in hs if h["count"]).most_common()]})
    if len({h["rank"] for h in headings}) != len(headings):
        raise MaharashtraError(f"two headings with one rank: {[h['rank'] for h in headings]}")

    # Same printed number = same place on the page in every reading.
    # A number split out of a merged OCR word ("3181/3444") has an estimated position: it only
    # joins a nearby place that holds the same value, else it starts its own.
    clusters: list[dict] = []
    pooled = sorted(((approx, i, n, x, y, w, ser) for i, r in enumerate(runs) for n, x, y, w, ser, approx in r["numbers"]),
                    key=lambda t: t[0])  # exact positions first, from every reading
    for approx, i, n, x, y, w, ser in pooled:
        # Within 2% of the page width at most: one number moves up to ~1.3% between readings, the next
        # column is >= 3.5% away (a wide box's width alone let two consolation numbers merge).
        near = [c for c in clusters if abs(c["y"] - y) < 0.006 and abs(c["x"] - x) < min(max(w, c["w"]) * 0.45, 0.02)]
        if approx:
            same = [c for c in clusters if abs(c["y"] - y) < 0.006 and abs(c["x"] - x) < min(max(w, c["w"]) * 1.5, 0.03)  # consolation: one value in every column
                    and any(v[0] == n for v in c["votes"].values())]
            near = same or near
        c = min(near, key=lambda c: abs(c["x"] - x), default=None)
        if c is None:
            c = {"x": x, "y": y, "w": w, "votes": {}}
            clusters.append(c)
        if i in c["votes"] and c["votes"][i][0] != n:
            c["votes"][i] = (None, None)  # this reading put two numbers in one place: no vote
        else:
            c["votes"].setdefault(i, (n, ser))
    # One printed number split in two places (a cell box centre in some readings, a word centre a
    # little higher in others: 1160 at y 0.752..0.759 on the 27-10-2026 Sahyadri sheet). Two places
    # that no reading read both of, closer than half a number, are one place; two printed numbers
    # are always both read by some reading.
    merged = True
    while merged:
        merged = False
        for a in clusters:
            b = next((b for b in clusters if b is not a and not (a["votes"].keys() & b["votes"].keys())
                      and abs(a["y"] - b["y"]) < 0.012 and abs(a["x"] - b["x"]) < min(a["w"] * 0.5, b["w"] * 0.5, 0.02)), None)
            if b is not None:
                a["votes"].update(b["votes"])
                clusters.remove(b)
                merged = True
                break
    numbers = []
    for c in clusters:
        if len(c["votes"]) < 2:
            continue  # seen once: noise, or a number the count check below will miss
        counts = Counter(n for n, _ in c["votes"].values() if n)
        # Unanimous places are settled here; the others are settled per tier below.
        v = next(iter(counts)) if len(counts) == 1 else None
        numbers.append((v, c["x"], c["y"], c["w"], c["votes"], counts))

    if glyphs is not None:  # teach the second reader this sheet's digits: places every reading agrees on
        glyphs.train([(x[0], x[1], x[2], x[3]) for x in numbers if x[0] and sum(x[5].values()) >= 4])
    check = glyphs.read if glyphs is not None and glyphs.templates else None

    tiers = []
    for j, h in enumerate(headings):
        end = headings[j + 1]["top"] if j + 1 < len(headings) else 2
        mine = [x for x in numbers if h["top"] <= x[2] < end]
        if len(mine) >= 20:
            if h["count"] is None or _printed_order(mine, h["count"]) is None:
                # The count is misread now and then ("(200 Randorn Numbers" for 300): a count some
                # reading gave that the printed grid of numbers fits, if only one does.
                fit = [c for c in h["counts"] if _printed_order(mine, c) is not None]
                if not fit:  # "300" read as "200" by every reading that read it: the full grid, one digit away
                    full = _grid_size(mine)
                    fit = [full] if full and any(_one_digit_apart(str(c), str(full)) for c in h["counts"]) \
                        and _printed_order(mine, full) is not None else []
                h["count"] = fit[0] if len(fit) == 1 else None
            mine = _settle_by_order(mine, h["rank"], check, h["count"])
        else:
            mine = [_settle_small(x, h["rank"], check) for x in mine]
        if not h["prize"]:
            raise MaharashtraError(f"{h['rank']} prize: amount unreadable")
        if h["rank"] == "1":
            if len(mine) != 1 or not mine[0][4]:
                raise MaharashtraError(f"1st prize: read {[(x[0], x[4]) for x in mine]}")
            nums = [f"{mine[0][4]} {mine[0][0]}"]
        elif h["rank"] == "CONS":
            if any(not x[4] for x in mine):
                raise MaharashtraError("consolation prize: a series is unreadable")
            nums = sorted(f"{x[4]} {x[0]}" for x in mine)
        else:
            if h["count"] is None:
                raise MaharashtraError(f"{h['rank']} prize: the printed count is unreadable")
            if len(mine) != h["count"]:
                raise MaharashtraError(f"{h['rank']} prize: sheet says {h['count']} numbers, read {len(mine)}")
            if not _column_order_ok([(n, x, y, w) for n, x, y, w, *_ in mine], h["count"]):
                raise MaharashtraError(f"{h['rank']} prize: numbers out of the printed order")
            nums = sorted(n for n, *_ in mine)
            if len(set(nums)) != len(nums):
                raise MaharashtraError(f"{h['rank']} prize: a number twice")
        tiers.append({"rank": h["rank"], "prize": h["prize"], "numbers": nums})
    _check(head, tiers)
    return {"head": head, "tiers": tiers}


def _with_series(x: tuple, v: str, series_check=None) -> tuple:
    """(value, x, y, w, series) for the 1st prize/consolation: the series needs the same margin as a
    number with no order to check (3 readings, 3x any other series), or 2 readings and 2x when the
    second reader reads the same two series digits in the series box."""
    c = Counter(s for n, s in x[4].values() if n == v and s).most_common()
    ser = c[0][0] if c and c[0][1] >= 3 and (len(c) == 1 or c[0][1] >= 3 * c[1][1]) else None
    if ser is None and c and c[0][1] >= 2 and (len(c) == 1 or c[0][1] >= 2 * c[1][1]) and series_check:
        if series_check(x[1], x[2], x[3]) == c[0][0][-2:]:
            ser = c[0][0]
    return (v, x[1], x[2], x[3], ser)


def _settle_small(x: tuple, rank: str, check=None) -> tuple:
    """A tier with no printed order to check (1st, consolation, 10-number tiers). With the second
    reader: the value it reads, if at least two Tesseract readings gave it too. Without it (the
    digits could not be cut apart): at least three readings and three times any other value
    (3:1 passes, 4:2 does not) - the same engine can repeat a misread, so a close vote is never
    trusted. Where the second reader reads something else, nothing is published."""
    c = x[5].most_common()
    second = check(x[1], x[2], x[3]) if check else None
    if second is not None:
        if x[5].get(second, 0) >= 2:
            return _with_series(x, second, getattr(check, "__self__", None) and check.__self__.read_series)
        raise MaharashtraError(f"{rank} prize: the two readers disagree ({dict(x[5])}, shapes say {second})")
    if not c or c[0][1] < 3 or (len(c) > 1 and c[0][1] < 3 * c[1][1]):
        raise MaharashtraError(f"{rank} prize: readings disagree on a number ({dict(x[5])})")
    return _with_series(x, c[0][0])


def _settle_by_order(mine: list, rank: str, check=None, count: int | None = None) -> list:
    """A random tier is printed in ascending order, column after column, which settles each place:
    * every Tesseract reading the same and the second reader (`check`) the same or unsure: taken;
    * the second reader sure of a value that fits between the settled neighbours: taken when some
      Tesseract reading gave it too, or one digit away from a value read at least twice (Tesseract
      repeats its misreads on these scans: 4165 x4 between 1148 and 1299, the shapes say 1165;
      7207 x4 and 7907 between 7278 and 7483, the shapes say 7307 - both right on the sheet);
    * no second reading: the one value read at least twice that fits between the neighbours;
    * a place of the printed grid no reading found (`count` given): the value the second reader and
      Tesseract on that box alone both read, if it fits between the neighbours.
    Anything else (two values that fit, none, the two readers against each other) and the sheet is
    not published; the whole order is checked again afterwards."""
    seq = _printed_order(mine, count)
    if seq is None:
        raise MaharashtraError(f"{rank} prize: the numbers do not form the printed columns")
    seq = [x if x[0] != "" else (None, x[1], x[2], x[3], {}, Counter()) for x in seq]  # a hole in the grid
    shapes = [check(x[1], x[2], x[3]) if check else None for x in seq]
    sure = [int(x[0]) if x[0] and g in (None, x[0]) else None for x, g in zip(seq, shapes)]
    out = []
    for k, (x, g) in enumerate(zip(seq, shapes)):
        if sure[k] is not None:
            out.append(_with_series(x, x[0]))
            continue
        lo = next((v for v in reversed(sure[:k]) if v is not None), -1)
        hi = next((v for v in sure[k + 1:] if v is not None), 10_000)
        fits = lambda n: lo < int(n) < hi  # noqa: E731
        if not x[5]:  # no reading found this place
            box = getattr(getattr(check, "__self__", None), "tesseract", None)
            if g is not None and fits(g) and box is not None and box(x[1], x[2], x[3]) == g:
                out.append(_with_series(x, g))
                continue
            raise MaharashtraError(f"{rank} prize: a printed place no reading found (shapes say {g}, between {lo} and {hi})")
        if g is not None:
            if fits(g) and (x[5].get(g, 0) >= 1 or any(c >= 2 and _one_digit_apart(n, g) for n, c in x[5].items())):
                out.append(_with_series(x, g))
                continue
            box = getattr(getattr(check, "__self__", None), "tesseract", None)
            if fits(g) and box is not None and box(x[1], x[2], x[3]) == g:  # as for a hole: that box read alone
                out.append(_with_series(x, g))
                continue
            raise MaharashtraError(f"{rank} prize: the two readers disagree ({dict(x[5])}, shapes say {g}, between {lo} and {hi})")
        fit = [n for n, c in x[5].items() if c >= 2 and fits(n)]
        if len(fit) != 1:
            raise MaharashtraError(f"{rank} prize: readings disagree on a number ({dict(x[5])} between {lo} and {hi})")
        out.append(_with_series(x, fit[0]))
    return out


def _one_digit_apart(a: str, b: str) -> bool:
    return len(a) == len(b) and sum(x != y for x, y in zip(a, b)) == 1


def _check(head: dict, tiers: list[dict]) -> None:
    """The sheet's own structure: tiers 1..N in order (consolation after the 1st), the 1st prize
    in one of the printed series, the consolation = same number in every other series."""
    ranks = [t["rank"] for t in tiers]
    plain = [r for r in ranks if r != "CONS"]
    if plain != [str(i) for i in range(1, len(plain) + 1)] or ("CONS" in ranks and ranks.index("CONS") != 1):
        raise MaharashtraError(f"prize tiers out of order: {ranks}")
    series, num = tiers[0]["numbers"][0].split()
    if series not in head["series"]:
        raise MaharashtraError(f"1st prize series {series} is not on the sheet")
    if "CONS" in ranks:
        cons = [x.split() for x in tiers[1]["numbers"]]
        if {n for _, n in cons} != {num} or sorted(s for s, _ in cons) != sorted(set(head["series"]) - {series}):
            raise MaharashtraError("consolation prize does not match the 1st prize")
    prizes = [t["prize"] for t in tiers if t["rank"] != "CONS"]
    if prizes != sorted(prizes, reverse=True):
        raise MaharashtraError(f"prize amounts out of order: {prizes}")


# ---------------------------------------------------------------- publishing

FEED = "v2/mh"          # separate from the Dear/Kerala feed: the app does not read it (yet)
KEEP_TRYING_DAYS = 3    # a sheet that does not read is retried by later runs for this long


def english_name(url: str) -> str | None:
    """The sheet's file name carries the English name: '... N 100 Mah. Tejaswini Monthly, Draw Result._0001.pdf'."""
    f = urllib.parse.unquote(url.rsplit("/", 1)[-1])
    m = re.search(r"(?i)mah\s*[._]\s*(.+?)\s*,?\s*(?:weekly|monthly|bumper)?\s*,?\s*(?:draw|lottery)", f)
    if not m:
        return None
    words = re.sub(r"[^A-Za-z ]", " ", m.group(1)).split()
    words = [w for w in words if w.lower() not in ("result", "of", "mah", "weekly", "monthly", "bumper")]
    return " ".join(w.capitalize() for w in words) or None


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def download(url: str, timeout_s: int = 900) -> bytes:
    """The state server is slow from abroad (~3 KB/s seen from GitHub's runners, 27-10-2026) and
    cuts long transfers: resume until the PDF is complete."""
    import time
    with tempfile.TemporaryDirectory() as work:
        f = os.path.join(work, "s.pdf")
        end = time.time() + timeout_s
        while time.time() < end:
            subprocess.run(["curl", "-sS", "-L", "-C", "-", "--max-time", "300", "-A", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                            "-o", f, url], capture_output=True, timeout=330)
            if os.path.exists(f):
                data = open(f, "rb").read()
                if data[:5] == b"%PDF-" and b"%%EOF" in data[-2048:]:
                    return data
        raise RuntimeError(f"incomplete download after {timeout_s}s: {url}")


def entry(date: dt.date, url: str, read_result: dict | None, listed_name: str) -> dict:
    name = english_name(url) or (" ".join(w.capitalize() for w in read_result["head"]["name"]) if read_result else listed_name)
    kind = "monthly" if "monthly" in url.lower() else "bumper" if "bumper" in url.lower() else "weekly"
    d = {"id": f"{date.isoformat()}_MH_{slug(name).upper()}", "slot": "MH", "lottery": "maharashtra",
         "date": date.isoformat(), "state": "Maharashtra", "name": f"Maharashtra {name}", "kind": kind,
         "nameLocal": listed_name, "format": "MH4", "ocr": True, "verified": False,
         "sources": [{"kind": "officialPdf", "url": url}, {"kind": "officialSite", "url": LIST_URL}]}
    if read_result is None:
        return d | {"status": "PDF", "time": "", "drawNo": None, "tiers": []}
    h = read_result["head"]
    if h["date"] != date.isoformat():
        raise MaharashtraError(f"sheet says {h['date']}, listed for {date}")
    tiers = [{"rank": t["rank"], "prize": t["prize"], "match": "EXACT" if t["rank"] in ("1", "CONS") else "LAST4",
              "numbers": t["numbers"]} for t in read_result["tiers"]]
    return d | {"status": "OK", "time": h.get("time", ""), "drawNo": h.get("drawNo"), "series": h["series"], "tiers": tiers}


OCR_TRIES = 2           # a sheet reads the same way every time: two tries cover a failed download or a timeout
SHEET_SECONDS = 200     # six readings of one sheet (~2 min on a GitHub runner); downloads run alongside
DOWNLOADS = 6           # sheets fetched at once: the lottery site sends each slowly (7-12 min a sheet, 28-10-2026)


def update(out, today: dt.date, warn=print, fetch_list=None, fetch_pdf=None, ocr: bool = True,
           deadline: float | None = None) -> int:
    """List every draw of the last few days with its official PDF, then (ocr=True) read the sheets
    not published as numbers yet, newest first, until `deadline` (time.monotonic()) leaves no room
    for another. Returns how many draws changed."""
    import json
    import pathlib
    import time
    out = pathlib.Path(out)
    page = (fetch_list or (lambda: get_bytes(LIST_URL, timeout=90).decode("utf-8", "replace")))()
    sheets = [(date, local, url) for date, local, url in list_sheets(page) if 0 <= (today - date).days <= KEEP_TRYING_DAYS]
    changed: set[str] = set()

    def load(date):
        f = out / FEED / "days" / f"{date.isoformat()}.json"
        return f, (json.loads(f.read_text()) if f.exists() else {"schema": 2, "date": date.isoformat(), "draws": []})

    def save(date, url, got):
        f, day = load(date)
        if next((x for x in day["draws"] if x["sources"][0]["url"] == url), None) == got:
            return
        day["draws"] = [x for x in day["draws"] if x["sources"][0]["url"] != url] + [got]
        day["draws"].sort(key=lambda x: (x.get("time") or "99", x["name"]))
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps(day, ensure_ascii=False, separators=(",", ":")))
        changed.add(url)

    def known(date, url):
        return next((x for x in load(date)[1]["draws"] if x["sources"][0]["url"] == url), None)

    for date, local, url in sheets:  # every draw is on the site at once, as its official sheet
        k = known(date, url)
        if k is None or k.get("status") != "OK":
            save(date, url, entry(date, url, None, local) | ({"ocrTries": k["ocrTries"]} if k and "ocrTries" in k else {}))
    # Sheets not read yet first (one that did not read reads the same way again), newest first.
    todo = sorted(((date, local, url) for date, local, url in sheets
                   if (k := known(date, url)).get("status") != "OK" and k.get("ocrTries", 0) < OCR_TRIES),
                  key=lambda x: (known(x[0], x[2]).get("ocrTries", 0), -x[0].toordinal())) if ocr else []
    if deadline is not None and time.monotonic() + SHEET_SECONDS > deadline:
        todo = []
    fetched: dict[str, tuple] = {}

    def fetch(url, done):
        try:
            fetched[url] = (True, (fetch_pdf or download)(url))
        except Exception as e:  # noqa: BLE001 - reported where the sheet is read
            fetched[url] = (False, e)
        done.set()
    import threading
    waits = {}
    for i, (_, _, url) in enumerate(todo):
        waits[url] = threading.Event()
        if i < DOWNLOADS:
            threading.Thread(target=fetch, args=(url, waits[url]), daemon=True).start()
    started = min(len(todo), DOWNLOADS)
    for date, local, url in todo:
        k = known(date, url)
        if deadline is not None and time.monotonic() + SHEET_SECONDS > deadline:
            warn("Maharashtra: no time left to read more sheets this run")
            break
        left = None if deadline is None else deadline - SHEET_SECONDS - time.monotonic()
        if not waits[url].wait(timeout=left):
            warn(f"Maharashtra {date} {local}: still downloading; next run")
            continue
        if started < len(todo):  # one more download in the place of this one
            nxt = todo[started][2]
            threading.Thread(target=fetch, args=(nxt, waits[nxt]), daemon=True).start()
            started += 1
        tries = {"ocrTries": k.get("ocrTries", 0) + 1}
        ok, pdf = fetched[url]
        if not ok:
            warn(f"Maharashtra {date} {local}: download failed: {pdf}")
            save(date, url, k | tries)
            continue
        try:
            got = entry(date, url, read(pdf, warn), local)
        except MaharashtraError as e:
            warn(f"Maharashtra {date} {english_name(url) or local}: not published as numbers ({e}); linking the PDF")
            got = entry(date, url, None, local) | tries
        save(date, url, got)
    days = sorted((p.stem for p in (out / FEED / "days").glob("*.json")), reverse=True) if (out / FEED / "days").exists() else []
    (out / FEED).mkdir(parents=True, exist_ok=True)
    (out / FEED / "index.json").write_text(json.dumps({"schema": 2, "dates": days}, separators=(",", ":")))
    return len(changed)


def main(argv=None) -> int:
    import argparse
    ap = argparse.ArgumentParser(description="Maharashtra State Lottery: read the official sheets into v2/mh/")
    ap.add_argument("--out", required=True)
    ap.add_argument("--ocr", action="store_true", help="read the numbers (needs tesseract, poppler, OpenCV); "
                    "without it each draw is listed with its official PDF only")
    ap.add_argument("--seconds", type=int, default=0, help="with --ocr: stop starting new sheets after this long")
    a = ap.parse_args(argv)
    import time
    ist = dt.timezone(dt.timedelta(hours=5, minutes=30))
    n = update(a.out, dt.datetime.now(ist).date(), ocr=a.ocr, deadline=time.monotonic() + a.seconds if a.seconds else None)
    print(f"maharashtra: {n} draws changed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
