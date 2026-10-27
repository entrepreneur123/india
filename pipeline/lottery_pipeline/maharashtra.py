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
_TIER = re.compile(r"\b([0-9SAIil]{1,2})\s*(st|nd|rd|th)\b[^|]{0,14}?[Pp]rize|\b([Cc]onsolation)\s*[Pp]rize")
_ORD_FIX = str.maketrans({"S": "5", "A": "4", "I": "1", "i": "1", "l": "1"})
_AMOUNT = re.compile(r"(?i)rs\.?\s*([\d,]{3,})")
_COUNT = re.compile(r"(?i)\b(\d{1,3})\s*(?:random\s*)?numbers?\b")
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
    """The table's ruling lines, and the cells they enclose as (x, y, w, h)."""
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
    return cells, clean


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
        if not (dpi * 0.45 < h < dpi * 1.0 and dpi * 0.25 < w < W / 3):
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


def read_once(pdf: bytes, dpi: int, clean: bool) -> dict:
    """One reading of a sheet. Raises MaharashtraError if the sheet does not make sense."""
    with tempfile.TemporaryDirectory() as work:
        gray = _render(pdf, dpi, work)
        cells, cleaned = _grid(gray, dpi)
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
        rank = "CONS" if m.group(3) else _ordinal(m.group(1), m.group(2))
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
        if not cell or cell[3] < dpi * 0.4:  # no box, or one around this line only: start at its first line
            for prev in reversed(lines[:li]):
                if _lead(prev) > W * 0.5 or not any(re.search(r"\w\w", w[0]) for w in prev):
                    continue  # the series/number boxes beside the heading, or specks read as a line
                prev_text = " ".join(w[0] for w in prev if w[1] < W * 0.5)  # the heading side, not its number boxes
                if re.search(r"(?i)\brank\b|series|[A-Z]{2}\s?[-.]\s?\d{2}", prev_text) or _TIER.search(prev_text) \
                        or _AMOUNT.search(prev_text) or len(re.findall(r"\b\d{4}\b", prev_text)) >= 2:
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
        t = {"rank": rank, "top": top, "cell": cell, "band": band,
             "after": (num_word[1] + num_word[3]) if num_word else
             max(w[1] + w[3] for w in ws if re.search(r"[A-Za-z]", w[0])),
             "prize": int(amt[0].replace(",", "")) if amt else None, "count": int(cnt.group(1)) if cnt else None,
             "numbers": []}
        if rank not in tiers or top < tiers[rank]["top"]:
            tiers[rank] = t
    if not tiers:
        raise MaharashtraError("no prize headings")
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


def _column_order_ok(nums) -> bool:
    """Random tiers are printed in ascending order down each column, column after column: so every
    printed row is ascending, and row r, column c < row r+1, column c < row 1, column c+1.
    Rows are found by height on the page (y as a fraction of it); within a row the values are sorted (word positions are
    unreliable where two cells were read as one word), which this check still holds to."""
    if len(nums) < 20:
        return True
    pts = sorted(nums, key=lambda n: n[2])
    rows, cur = [], [pts[0]]
    for p in pts[1:]:
        if p[2] - cur[-1][2] > 0.008:  # page fractions: printed rows are >= 0.016 apart
            rows.append(cur)
            cur = []
        cur.append(p)
    rows.append(cur)
    lens = [len(r) for r in rows]
    # Full rows first; when the count does not fill the grid the last column is shorter
    # (350 numbers = 8 rows of 20 + 10 rows of 19).
    if lens != sorted(lens, reverse=True) or lens[0] - lens[-1] > 1:
        return False
    grid = [sorted(int(n[0]) for n in r) for r in rows]
    seq = [row[c] for c in range(lens[0]) for row in grid if c < len(row)]
    return all(a < b for a, b in zip(seq, seq[1:]))


# ---------------------------------------------------------------- agreement between readings

# Line-free images read the numbers best (grey ones missed whole rows of the 27-10-2026 Tejaswini sheet).
READINGS = ((400, True), (300, True), (500, True), (350, True), (450, True), (550, True))


def read(pdf: bytes, warn=print) -> dict:
    """Read the sheet several ways; publish only what the readings agree on (see _agree)."""
    runs, last = [], None
    for dpi, clean in READINGS:
        try:
            runs.append(read_once(pdf, dpi, clean))
        except MaharashtraError as e:
            warn(f"Maharashtra OCR {dpi} dpi{' clean' if clean else ''}: {e}")
        except Exception as e:  # OCR tool errors and image decoder limits
            warn(f"Maharashtra OCR {dpi} dpi failed: {type(e).__name__}: {e}")
        if len(runs) >= 3:
            try:
                return _agree(runs)
            except MaharashtraError as e:
                last = e
    raise last or MaharashtraError("fewer than three usable readings")


def _vote(values: list, need: int = 2):
    """The value most readings gave: at least `need` of them, and at least twice as many as any other
    value (4 against 2 passes, 3 against 2 does not)."""
    c = Counter(values).most_common()
    if not c or c[0][1] < need or (len(c) > 1 and c[0][1] < 2 * c[1][1]):
        return None
    return c[0][0]


def _agree(runs: list[dict]) -> dict:
    """Combine the readings. Every heading and every printed number position must be read the same
    way by at least two readings, with no tie; then the sheet's own counts and order must hold."""
    head = {}
    # Readings that fitted the whole label row give the ten series; partial lists do not vote.
    full = [r for r in runs if len(r["head"].get("series", [])) == 10]
    for k in ("name", "kind", "date", "drawNo", "time", "series"):
        v = _vote([repr(r["head"][k]) for r in (full if k == "series" else runs) if r["head"].get(k)])
        if v is not None:
            head[k] = eval(v)  # noqa: S307 - our own repr() of str/int/list
    for k in ("name", "date", "series"):
        if k not in head:
            raise MaharashtraError(f"readings disagree on the sheet's {k}")

    by_rank: dict[str, list] = {}
    for r in runs:
        for h in r["headings"]:
            by_rank.setdefault(h["rank"], []).append(h)
    headings = []
    for rank, hs in by_rank.items():
        if len(hs) < 2:
            continue  # a misread ordinal in one reading
        tops = sorted(h["top"] for h in hs)
        headings.append({"rank": rank, "top": tops[len(tops) // 2], "prize": _vote([h["prize"] for h in hs if h["prize"]]),
                         "count": _vote([h["count"] for h in hs if h["count"]])})
    headings.sort(key=lambda h: h["top"])

    # Same printed number = same place on the page in every reading.
    # A number split out of a merged OCR word ("3181/3444") has an estimated position: it only
    # joins a nearby place that holds the same value, else it starts its own.
    clusters: list[dict] = []
    pooled = sorted(((approx, i, n, x, y, w, ser) for i, r in enumerate(runs) for n, x, y, w, ser, approx in r["numbers"]),
                    key=lambda t: t[0])  # exact positions first, from every reading
    for approx, i, n, x, y, w, ser in pooled:
        near = [c for c in clusters if abs(c["y"] - y) < 0.006 and abs(c["x"] - x) < max(w, c["w"]) * 0.45]
        if approx:
            same = [c for c in clusters if abs(c["y"] - y) < 0.006 and abs(c["x"] - x) < max(w, c["w"]) * 1.5
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
    numbers = []
    for c in clusters:
        if len(c["votes"]) < 2:
            continue  # seen once: noise, or a number the count check below will miss
        counts = Counter(n for n, _ in c["votes"].values() if n)
        # Unanimous places are settled here; the others are settled per tier below.
        v = next(iter(counts)) if len(counts) == 1 else None
        numbers.append((v, c["x"], c["y"], c["w"], c["votes"], counts))

    tiers = []
    for j, h in enumerate(headings):
        end = headings[j + 1]["top"] if j + 1 < len(headings) else 2
        mine = [x for x in numbers if h["top"] <= x[2] < end]
        if len(mine) >= 20:
            mine = _settle_by_order(mine, h["rank"])
        else:
            mine = [_settle_small(x, h["rank"]) for x in mine]
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
            if not _column_order_ok([(n, x, y, w) for n, x, y, w, *_ in mine]):
                raise MaharashtraError(f"{h['rank']} prize: numbers out of the printed order")
            nums = sorted(n for n, *_ in mine)
            if len(set(nums)) != len(nums):
                raise MaharashtraError(f"{h['rank']} prize: a number twice")
        tiers.append({"rank": h["rank"], "prize": h["prize"], "numbers": nums})
    _check(head, tiers)
    return {"head": head, "tiers": tiers}


def _with_series(x: tuple, v: str) -> tuple:
    """(value, x, y, w, series) for the 1st prize/consolation: the series needs the same margin as a
    number with no order to check (3 readings, 3x any other series)."""
    c = Counter(s for n, s in x[4].values() if n == v and s).most_common()
    ser = c[0][0] if c and c[0][1] >= 3 and (len(c) == 1 or c[0][1] >= 3 * c[1][1]) else None
    return (v, x[1], x[2], x[3], ser)


def _settle_small(x: tuple, rank: str) -> tuple:
    """A tier with no printed order to check (1st, consolation, 10-number tiers): a value needs at
    least three readings and three times the readings of any other value (3:1 passes, 4:2 does not).
    The same engine can repeat a misread, so a close vote is never trusted here."""
    c = x[5].most_common()
    if not c or c[0][1] < 3 or (len(c) > 1 and c[0][1] < 3 * c[1][1]):
        raise MaharashtraError(f"{rank} prize: readings disagree on a number ({dict(x[5])})")
    return _with_series(x, c[0][0])


def _settle_by_order(mine: list, rank: str) -> list:
    """A random tier is printed in ascending order, column after column. Places every reading read
    the same way are taken; a place the readings split on (4139 x3, 1139 x2) takes the one value,
    read at least twice, that fits between its unanimous neighbours. A majority is not enough here:
    the same misread can repeat. If none or more than one value fits, the sheet is not published."""
    pts = sorted(mine, key=lambda n: n[2])
    rows, cur = [], [pts[0]]
    for p in pts[1:]:
        if p[2] - cur[-1][2] > 0.008:
            rows.append(cur)
            cur = []
        cur.append(p)
    rows.append(cur)
    rows = [sorted(r, key=lambda n: n[1]) for r in rows]
    seq = [r[c] for c in range(max(len(r) for r in rows)) for r in rows if c < len(r)]
    sure = [int(x[0]) if x[0] else None for x in seq]
    out = []
    for k, x in enumerate(seq):
        if x[0] is not None:
            out.append(_with_series(x, x[0]))
            continue
        lo = next((v for v in reversed(sure[:k]) if v is not None), -1)
        hi = next((v for v in sure[k + 1:] if v is not None), 10_000)
        fit = [n for n, c in x[5].items() if c >= 2 and lo < int(n) < hi]
        if len(fit) != 1:
            raise MaharashtraError(f"{rank} prize: readings disagree on a number ({dict(x[5])} between {lo} and {hi})")
        out.append(_with_series(x, fit[0]))
    return out


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


def update(out, today: dt.date, warn=print, fetch_list=None, fetch_pdf=None, ocr: bool = True) -> int:
    """Read every sheet listed for the last few days that is not published yet. Returns how many
    draws changed. ocr=False only lists the draws with their official PDF (no download, no OCR)."""
    import json
    import pathlib
    out = pathlib.Path(out)
    page = (fetch_list or (lambda: get_bytes(LIST_URL, timeout=90).decode("utf-8", "replace")))()
    changed = 0
    for date, local, url in list_sheets(page):
        if (today - date).days > KEEP_TRYING_DAYS or date > today:
            continue
        f = out / FEED / "days" / f"{date.isoformat()}.json"
        day = json.loads(f.read_text()) if f.exists() else {"schema": 2, "date": date.isoformat(), "draws": []}
        known = {x["sources"][0]["url"]: x for x in day["draws"]}
        if known.get(url, {}).get("status") == "OK":
            continue
        if not ocr:
            got = entry(date, url, None, local)
            if known.get(url) != got:
                day["draws"] = [x for x in day["draws"] if x["sources"][0]["url"] != url] + [got]
                day["draws"].sort(key=lambda x: (x.get("time") or "99", x["name"]))
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_text(json.dumps(day, ensure_ascii=False, separators=(",", ":")))
                changed += 1
            continue
        try:
            pdf = (fetch_pdf or download)(url)
        except Exception as e:
            warn(f"Maharashtra {date} {local}: download failed: {e}")
            continue
        try:
            got = entry(date, url, read(pdf, warn), local)
        except MaharashtraError as e:
            warn(f"Maharashtra {date} {english_name(url) or local}: not published as numbers ({e}); linking the PDF")
            got = entry(date, url, None, local)
        if known.get(url) == got:
            continue
        day["draws"] = [x for x in day["draws"] if x["sources"][0]["url"] != url] + [got]
        day["draws"].sort(key=lambda x: (x.get("time") or "99", x["name"]))
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps(day, ensure_ascii=False, separators=(",", ":")))
        changed += 1
    days = sorted((p.stem for p in (out / FEED / "days").glob("*.json")), reverse=True) if (out / FEED / "days").exists() else []
    (out / FEED).mkdir(parents=True, exist_ok=True)
    (out / FEED / "index.json").write_text(json.dumps({"schema": 2, "dates": days}, separators=(",", ":")))
    return changed


def main(argv=None) -> int:
    import argparse
    ap = argparse.ArgumentParser(description="Maharashtra State Lottery: read the official sheets into v2/mh/")
    ap.add_argument("--out", required=True)
    ap.add_argument("--ocr", action="store_true", help="read the numbers (needs tesseract, poppler, OpenCV); "
                    "without it each draw is listed with its official PDF only")
    a = ap.parse_args(argv)
    ist = dt.timezone(dt.timedelta(hours=5, minutes=30))
    n = update(a.out, dt.datetime.now(ist).date(), ocr=a.ocr)
    print(f"maharashtra: {n} draws changed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
