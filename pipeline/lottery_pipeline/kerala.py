"""Kerala State Lottery results (daily draw at 3 PM IST, bumpers at 2 PM).

The official site (statelottery.kerala.gov.in) does not answer from outside India, which is
where our runners are (checked from GitHub Actions on 19-10-2026). So results are read from
three independent public copies and published only when at least two agree on every number:

  * govdotin.com text PDF   https://govdotin.com/keralalottery/pdf_results/DD-MM-YYYY_A4.pdf
  * keralalotteryresult.net  (Blogger post, found through the blog's JSON feed)
  * keralalotteries.net      (Blogger post, found through the blog's JSON feed)

The copies are typed from the live draw or the official PDF; a typo in one shows up as a
disagreement and nothing is published until a second copy matches. Places and agent names are
not stored (copies disagree on them and they do not matter for checking a ticket).
"""
from __future__ import annotations

import datetime as dt
import html
import json
import re
import subprocess
import tempfile
import urllib.parse

from .http import get_bytes

GOVDOTIN = "https://govdotin.com/keralalottery/pdf_results/{d:%d-%m-%Y}_A4.pdf"
BLOGS = ("https://www.keralalotteryresult.net", "https://www.keralalotteries.net")
OFFICIAL_SITE = "https://statelottery.kerala.gov.in/"

# Weekly draws by ISO weekday (checked against draws of 05-19 Oct 2026).
WEEKLY = {1: ("BT", "BHAGYATHARA"), 2: ("SS", "STHREE SAKTHI"), 3: ("DL", "DHANALEKSHMI"),
          4: ("KN", "KARUNYA PLUS"), 5: ("SK", "SUVARNA KERALAM"), 6: ("KR", "KARUNYA"), 7: ("SM", "SAMRUDHI")}

RANKS = [("1", r"first|1st"), ("CONS", r"consolation"), ("2", r"second|2nd"), ("3", r"third|3rd"),
         ("4", r"4th|fourth"), ("5", r"5th|fifth"), ("6", r"6th|sixth"), ("7", r"7th|seventh"),
         ("8", r"8th|eighth"), ("9", r"9th|ninth"), ("10", r"10th|tenth")]
_HEAD = re.compile(r"(?i)\b(%s)\s*prize" % "|".join(p for _, p in RANKS))
_TICKET = re.compile(r"\b([A-Z]{2}) ?(\d{6})\b")
_SERIES_BLOCK = re.compile(r"\{\s*((?:[A-Z]{2}\s+)*[A-Z]{2})\s*\}\s*(\d{6})")
_AMOUNT = re.compile(r"(\d{1,3}(?:,\d{2,3})+|\d{3,})")
_DRAWN = re.compile(r"(?i)drawn\s+(\d+)\s+times")


class KeralaError(ValueError):
    pass


def _rank_of(word: str) -> str:
    for rank, pat in RANKS:
        if re.fullmatch(pat, word, re.I):
            return rank
    raise KeralaError(word)


def html_text(page: str) -> str:
    """Visible text of a post, one block per line."""
    t = re.sub(r"(?is)<(script|style)\b.*?</\1>", " ", page)
    t = re.sub(r"(?i)<br\s*/?>|</(p|div|tr|td|li|h\d|span)>", "\n", t)
    t = html.unescape(re.sub(r"<[^>]+>", " ", t))
    return "\n".join(" ".join(line.split()) for line in t.splitlines() if line.strip())


def parse(text: str) -> list[dict]:
    """Prize tiers in draw order: [{rank, prize, match, numbers}]. Raises KeralaError when the
    text does not look like one complete result."""
    heads = list(_HEAD.finditer(text))
    if not heads:
        raise KeralaError("no prize headings")
    tiers: dict[str, dict] = {}
    for i, h in enumerate(heads):
        rank = _rank_of(h.group(1))
        if rank in tiers:   # blogs repeat headings in their summaries: keep the first full block
            continue
        body = text[h.end(): heads[i + 1].start() if i + 1 < len(heads) else len(text)]
        amount = _AMOUNT.search(body[:60])
        if not amount:
            continue
        prize = int(amount.group(1).replace(",", ""))
        rest = body[amount.end():]
        if rank in ("1", "CONS", "2", "3"):
            nums = [f"{s} {n}" for s, n in _TICKET.findall(rest)]
            block = _SERIES_BLOCK.search(rest)   # govdotin: "{MA MB … ML } 289707"
            if block:
                nums = [f"{s} {block.group(2)}" for s in block.group(1).split()]
            tiers[rank] = {"rank": rank, "prize": prize, "match": "EXACT", "numbers": nums}
        else:
            words = rest.split()
            nums, started = [], False
            for w in words:
                if re.fullmatch(r"\d{4}", w):
                    nums.append(w)
                    started = True
                elif started and w not in ("---", "|", "-"):
                    break   # the list ended; later 4-digit tokens are page text (years, phone numbers)
            drawn = _DRAWN.search(rest[:120])
            if drawn and int(drawn.group(1)) != len(nums):
                raise KeralaError(f"tier {rank}: page says {drawn.group(1)} numbers, found {len(nums)}")
            tiers[rank] = {"rank": rank, "prize": prize, "match": "LAST4", "numbers": nums}
    order = [r for r, _ in RANKS if r in tiers]
    out = [tiers[r] for r in order]
    _check(out)
    return out


def _check(tiers: list[dict]) -> None:
    ranks = [t["rank"] for t in tiers]
    if ranks[:1] != ["1"] or "CONS" not in ranks or not {"2", "3", "4", "5"} <= set(ranks):
        raise KeralaError(f"incomplete result: {ranks}")
    if len(tiers[0]["numbers"]) != 1:
        raise KeralaError(f"1st prize: {tiers[0]['numbers']}")
    first = tiers[0]["numbers"][0]
    cons = next(t for t in tiers if t["rank"] == "CONS")["numbers"]
    if not cons or any(c[-6:] != first[-6:] or c == first for c in cons):
        raise KeralaError(f"consolation {cons} does not match 1st prize {first}")
    expected = [r for r, _ in RANKS[:len(tiers)]]
    if ranks != expected:
        raise KeralaError(f"missing tier: {ranks}")
    for t in tiers:
        if not t["numbers"] or len(set(t["numbers"])) != len(t["numbers"]):
            raise KeralaError(f"tier {t['rank']}: empty or duplicate numbers")
        if t["match"] == "LAST4" and t["numbers"] != sorted(t["numbers"]):
            raise KeralaError(f"tier {t['rank']}: numbers out of order (page cut or mixed up)")
    prizes = [t["prize"] for t in tiers if t["rank"] != "CONS"]
    if prizes != sorted(prizes, reverse=True):
        raise KeralaError(f"prizes not decreasing: {prizes}")


def header(text: str, code: str) -> tuple[str, int] | None:
    """(code, draw no.) from 'SM77 SAMRUDHI 18-10-2026', 'Samrudhi SM 77' or 'Karunya KR- 772', looking for
    the weekday's own code first and a bumper code (BR) otherwise."""
    head = text[:1500].upper()
    for c in (code, "BR"):
        m = re.search(r"\b%s\s*[-.]?\s*(\d{1,4})\b" % c, head)
        if m:
            return c, int(m.group(1))
    return None


# ---- fetching ----

def pdf_text(pdf: bytes) -> str:
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(pdf)
        f.flush()
        out = subprocess.run(["pdftotext", "-layout", f.name, "-"], check=True, capture_output=True, timeout=60)
    return out.stdout.decode("utf-8", "replace")


def from_govdotin(date: dt.date) -> tuple[str, str] | None:
    """(text of that date's page, url). The PDF sometimes carries the previous day too."""
    url = GOVDOTIN.format(d=date)
    pdf = get_bytes(url)
    if not pdf or not pdf.startswith(b"%PDF"):
        return None
    text = pdf_text(pdf)
    first_line = next((line for line in text.splitlines() if line.strip()), "")
    return (text, url) if date.strftime("%d-%m-%Y") in first_line else None   # a stale file keeps its old date


def from_blog(base: str, date: dt.date) -> tuple[str, str] | None:
    """Text of the blog post for that date, found in the blog's JSON feed (no guessing of slugs)."""
    feed = get_bytes(f"{base}/feeds/posts/default?alt=json&max-results=8")
    if not feed:
        return None
    stamp = date.strftime("%d-%m-%Y")
    for e in json.loads(feed)["feed"].get("entry", []):
        link = next((l["href"] for l in e.get("link", []) if l.get("rel") == "alternate"), "")
        path = urllib.parse.unquote(urllib.parse.urlsplit(link).path)
        # The post named after that date, not the evergreen "today's result" page rewritten every day.
        if stamp in path:
            return html_text(e.get("content", {}).get("$t", "")), link
    return None


def fetch(date: dt.date, warn=print) -> dict | None:
    """The draw of `date`, decided prize tier by prize tier: a tier is confirmed when two copies agree on
    it exactly. Nothing is published until the 1st, consolation, 2nd and 3rd prizes are confirmed; a lower
    tier the copies disagree on is published marked confirmed=false ("check the official result")."""
    reads = []
    for name, get in (("govdotin", lambda: from_govdotin(date)),
                      ("keralalotteryresult", lambda: from_blog(BLOGS[0], date)),
                      ("keralalotteries", lambda: from_blog(BLOGS[1], date))):
        try:
            got = get()
        except Exception as e:  # one copy failing must not stop the others
            warn(f"Kerala {date}: {name} unavailable: {type(e).__name__}: {e}")
            continue
        if got is None:
            continue
        text, url = got
        try:
            reads.append((name, url, {t["rank"]: t for t in parse(text)}, header(text, WEEKLY[date.isoweekday()][0])))
        except KeralaError as e:
            warn(f"Kerala {date}: {name} not usable yet: {e}")
    tiers, used = vote([r[2] for r in reads])
    if tiers is None:
        if len(reads) >= 2:
            warn(f"Kerala {date}: copies disagree on a top prize ({', '.join(r[0] for r in reads)}); not published")
        return None
    unsure = [t["rank"] for t in tiers if not t.get("confirmed", True)]
    if unsure:
        warn(f"Kerala {date}: tiers {unsure} not confirmed by two copies; published marked unconfirmed")
    head = next((r[3] for r in reads if r[3]), None)
    return entry(date, tiers, head, [reads[i][1] for i in sorted(used)])


def vote(reads: list[dict[str, dict]]) -> tuple[list[dict] | None, set[int]]:
    """Tier-by-tier agreement between copies. Returns (tiers in draw order, indexes of copies used)."""
    ranks = [r for r, _ in RANKS if any(r in rd for rd in reads)]
    out, used = [], set()
    for rank in ranks:
        have = [(i, rd[rank]) for i, rd in enumerate(reads) if rank in rd]
        agreed = next(((i, t) for i, t in have if sum(t == u for _, u in have) >= 2), None)
        if agreed:
            out.append(agreed[1])
            used |= {i for i, t in have if t == agreed[1]}
        elif rank in ("1", "CONS", "2", "3"):
            return None, set()
        else:
            out.append(dict(have[0][1], confirmed=False))   # first copy in source order (the typed PDF)
            used.add(have[0][0])
    return (out or None), used


def entry(date: dt.date, tiers: list[dict], head: tuple[str, int] | None, urls: list[str]) -> dict:
    code, name = WEEKLY[date.isoweekday()]
    draw_no = None
    if head and head[0] == code:
        draw_no = head[1]
    elif head:   # a bumper (e.g. BR-112) replaces the weekly draw that day
        code, name, draw_no = head[0], f"KERALA BUMPER {head[0]}", head[1]
    bumper = code == "BR"
    label = f"{name} {code}-{draw_no}" if draw_no else name
    return {"id": f"{date.isoformat()}_KL", "slot": "KL", "lottery": "kerala", "date": date.isoformat(),
            "time": "14:00" if bumper else "15:00", "state": "Kerala", "name": label, "drawNo": draw_no,
            "format": "B6", "price": 0, "special": bumper, "verified": False, "status": "OK", "ocr": False,
            "tiers": tiers,
            "sources": [{"kind": "copy", "url": u} for u in urls] + [{"kind": "officialSite", "url": OFFICIAL_SITE}]}
