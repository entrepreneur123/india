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
_TIER = re.compile(r"\b([0-9SAIil]{1,2})\s*(?:st|nd|rd|th)\b[^|]{0,14}?[Pp]rize|\b([Cc]onsolation)\s*[Pp]rize")
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
    small = [c for c in cells if dpi * 0.25 < c[2] < W / 5 and dpi * 0.12 < c[3] < dpi * 0.7]
    if not small:
        return [], []
    hh, gap = 90, 40
    crops = []
    for x, y, w, h in small:
        ix, iy = max(2, int(w * 0.06)), max(2, int(h * 0.12))
        c = gray[y + iy:y + h - iy, x + ix:x + w - ix]
        if c.size == 0:
            crops.append(np.full((hh, hh), 255, np.uint8))
            continue
        crops.append(cv2.resize(c, (max(1, int(c.shape[1] * hh / c.shape[0])), hh)))
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
        out = subprocess.run(["tesseract", p, "-", "--psm", "6", "-c", "tessedit_char_whitelist=0123456789-", "tsv"],
                             capture_output=True, text=True, timeout=180).stdout
        for line in out.splitlines()[1:]:
            f = line.split("\t")
            if len(f) == 12 and f[11].strip():
                i = (int(f[7]) + int(f[9]) // 2 - gap // 2) // (hh + gap)
                if 0 <= i < len(part):
                    found.setdefault(start + i, []).append(f[11].strip())
    nums, done = [], []
    for i, ws in found.items():
        t = "".join(ws)
        if re.fullmatch(r"\d{4}", t):
            x, y, w, h = small[i]
            nums.append((t, x + w / 2, y + h / 2, w))
            done.append(small[i])
    return nums, done


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
    for ws in lines:
        text = " ".join(w[0] for w in ws)
        m = _TIER.search(text)
        if not m:
            continue
        rank = "CONS" if m.group(2) else str(int(m.group(1).translate(_ORD_FIX)))
        first = next(w for w in ws if m.group(0).split()[0] in w[0]) if m.group(0).split() else ws[0]
        cell = next((c for c in cells if _inside(first, c) and c[3] < H * 0.4), None)
        region = " ".join(w[0] for w in words if cell and _inside(w, cell)) if cell else text[m.start():]
        top = cell[1] if cell else min(w[2] for w in ws)
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
    head["series"] = sorted({s for s, *_ in series})

    found = [(n, cx, cy, ww, False) for n, cx, cy, ww in cell_nums]
    for wd in num_words:  # numbers outside single cells: merged cells of the big random tiers
        for n, cx, cy in _numbers_in(wd):
            if not any(c[0] <= cx <= c[0] + c[2] and c[1] <= cy <= c[1] + c[3] for c in read_cells):
                k = len(_numbers_in(wd))  # k > 1: positions inside the word are estimates
                found.append((n, cx, cy, wd[3] / k, k > 1))
    widths = sorted(f[3] for f in found)
    typical = widths[len(widths) // 2] if widths else 0
    for n, cx, cy, ww, approx in found:
        approx = approx or ww > typical * 1.6  # from a box wider than one number: its centre is not the number's
        if cy >= panel:
            continue
        owner = None
        for t in order:
            if t["top"] <= cy:
                owner = t
        if owner is None:
            continue
        c = owner["cell"]
        if c and c[2] < W / 2 and c[1] <= cy <= c[1] + c[3] and c[0] <= cx <= c[0] + c[2]:
            continue  # inside a heading cell beside its numbers (e.g. a misread amount)
        lo, hi = owner["band"]
        if lo - dpi * 0.05 <= cy <= hi + dpi * 0.05 and cx < owner["after"]:
            continue  # on the heading's own line, left of its end: part of the heading text
        owner["numbers"].append((n, cx, cy, ww, approx))

    numbers = [(n, cx / W, cy / H, ww / W, _series_of(cx, cy, series) if t["rank"] in ("1", "CONS") else None, approx)
               for t in order for n, cx, cy, ww, approx in t["numbers"]]
    headings = [{"rank": t["rank"], "top": t["top"] / H, "prize": t["prize"], "count": t["count"]} for t in order]
    return {"head": head, "headings": headings, "numbers": numbers}


def _series_of(x: float, y: float, series) -> str | None:
    """The series a 1st-prize/consolation number belongs to: a series label in the same row to its
    left (GL-00 | 2142), else the series column header above it (TJ-01 over 6840)."""
    row = [s for s in series if abs(s[2] - y) < 60 and s[1] < x]
    if row:
        return max(row, key=lambda s: s[1])[0]
    col = [s for s in series if abs(s[1] - x) < s[3] and s[2] < y]
    if col:
        return max(col, key=lambda s: s[2])[0]
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
    for k in ("name", "kind", "date", "drawNo", "time", "series"):
        v = _vote([repr(r["head"][k]) for r in runs if r["head"].get(k)])
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
        v = _vote([n for n, _ in c["votes"].values() if n])
        cand = {n for n, k in Counter(n for n, _ in c["votes"].values() if n).items() if k >= 2}
        if v is None and not cand:
            raise MaharashtraError(f"readings disagree on a number near {c['x']:.2f},{c['y']:.2f}")
        ser = _vote([s for n, s in c["votes"].values() if n == v and s]) if v else None
        numbers.append((v, c["x"], c["y"], c["w"], ser, cand))

    tiers = []
    for j, h in enumerate(headings):
        end = headings[j + 1]["top"] if j + 1 < len(headings) else 2
        mine = [x for x in numbers if h["top"] <= x[2] < end]
        if any(x[0] is None for x in mine):
            mine = _settle_by_order(mine, h["rank"])
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


def _settle_by_order(mine: list, rank: str) -> list:
    """A place the readings split on (4139 x3, 1139 x2, 1189 x1) is settled only in a random tier,
    whose numbers are printed in ascending order: the one value read at least twice that fits
    between its settled neighbours wins. If none or more than one fits, the sheet is not published."""
    if len(mine) < 20:
        raise MaharashtraError(f"{rank} prize: readings disagree on a number")
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
    vals = [int(x[0]) if x[0] else None for x in seq]
    out = []
    for k, x in enumerate(seq):
        if x[0] is not None:
            out.append(x)
            continue
        lo = next((v for v in reversed(vals[:k]) if v is not None), -1)
        hi = next((v for v in vals[k + 1:] if v is not None), 10_000)
        fit = [n for n in x[5] if lo < int(n) < hi]
        if len(fit) != 1:
            raise MaharashtraError(f"{rank} prize: readings disagree on a number ({sorted(x[5])} between {lo} and {hi})")
        vals[k] = int(fit[0])
        out.append((fit[0],) + tuple(x[1:]))
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


def update(out, today: dt.date, warn=print, fetch_list=None, fetch_pdf=None) -> int:
    """Read every sheet listed for the last few days that is not published yet. Returns how many
    draws changed."""
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
    a = ap.parse_args(argv)
    ist = dt.timezone(dt.timedelta(hours=5, minutes=30))
    n = update(a.out, dt.datetime.now(ist).date())
    print(f"maharashtra: {n} draws changed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
