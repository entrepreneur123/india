"""Result data model, prize schemes and structural validation.

Every number published to the app must pass `validate()`. Prize amounts and
tier sizes below were transcribed from the official gazettes
(nagalandlotteries.com/old_results/*171026.PDF, 17 Oct 2026) and the Sikkim
6 PM sheet on lotterysambad.com (18 Oct 2026). If a scheme changes, the
structural checks fail loudly instead of silently publishing wrong data.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field, asdict

# Match rules understood by the Android app (keep in sync with Matcher.kt).
FULL = "FULL"          # serial + series letter + 5 digits, exact
CONS = "CONS"          # same 5 digits as 1st prize, any other serial/series
LAST5 = "LAST5"        # last 5 digits, all serials/series
LAST4 = "LAST4"        # last 4 digits, all serials/series

# Series letters printed on Dear 5-digit tickets (F and I are never used).
D5_LETTERS = "ABCDEGHJKL"
FIRST_PRIZE_RE = re.compile(r"^(\d{2})\s*([%s])\s*(\d{5})$" % D5_LETTERS)


@dataclass(frozen=True)
class TierSpec:
    rank: str
    prize: int          # rupees, for the winner (seller commission excluded)
    match: str
    count: int          # how many winning numbers are drawn for this tier
    digits: int


@dataclass(frozen=True)
class Scheme:
    id: str
    ticket_format: str  # "D5" = 34A 12345, "D4" = 12 3456
    price: int
    tiers: tuple[TierSpec, ...]


DEAR_D5 = Scheme(
    id="DEAR_D5_2025",
    ticket_format="D5",
    price=7,
    tiers=(
        TierSpec("1", 1_00_00_000, FULL, 1, 5),
        TierSpec("CONS", 1_000, CONS, 1, 5),
        TierSpec("2", 10_000, LAST5, 10, 5),
        TierSpec("3", 500, LAST4, 10, 4),
        TierSpec("4", 250, LAST4, 10, 4),
        TierSpec("5", 120, LAST4, 100, 4),
    ),
)

DEAR_LAXMI_D4 = Scheme(
    id="DEAR_LAXMI_D4_2026",
    ticket_format="D4",
    price=12,
    tiers=(
        TierSpec("1", 10_000, LAST4, 1, 4),
        TierSpec("2", 5_000, LAST4, 1, 4),
        TierSpec("3", 1_000, LAST4, 5, 4),
        TierSpec("4", 500, LAST4, 5, 4),
        TierSpec("5", 100, LAST4, 360, 4),
    ),
)


@dataclass(frozen=True)
class Slot:
    id: str
    file_code: str      # prefix used by both result websites, e.g. MN171026.PDF
    state: str
    time: str           # draw time, IST, HH:MM
    scheme: Scheme
    label: str


SLOTS: dict[str, Slot] = {
    s.id: s
    for s in (
        Slot("NL1PM", "MN", "Nagaland", "13:00", DEAR_D5, "Dear 1 PM"),
        Slot("SK6PM", "DN", "Sikkim", "18:00", DEAR_D5, "Dear 6 PM"),
        Slot("NL8PM", "EN", "Nagaland", "20:00", DEAR_D5, "Dear 8 PM"),
        Slot("NLLAXMI", "DG", "Nagaland", "20:00", DEAR_LAXMI_D4, "Dear Laxmi 8 PM"),
    )
}


class ValidationError(ValueError):
    pass


@dataclass
class Tier:
    rank: str
    prize: int
    match: str
    numbers: list[str]


@dataclass
class Draw:
    slot: str
    date: str               # ISO yyyy-mm-dd (IST)
    name: str               # e.g. "DEAR VISION SATURDAY WEEKLY LOTTERY"
    draw_no: int | None
    tiers: list[Tier]
    sources: list[dict] = field(default_factory=list)
    verified: bool = False  # True when two independent sources agree

    @property
    def id(self) -> str:
        return f"{self.date}_{self.slot}"

    def numbers_key(self) -> tuple:
        return tuple((t.rank, tuple(t.numbers)) for t in self.tiers)

    def to_json(self) -> dict:
        slot = SLOTS[self.slot]
        return {
            "id": self.id,
            "slot": self.slot,
            "date": self.date,
            "time": slot.time,
            "state": slot.state,
            "name": self.name,
            "drawNo": self.draw_no,
            "format": slot.scheme.ticket_format,
            "price": slot.scheme.price,
            "verified": self.verified,
            "tiers": [asdict(t) for t in self.tiers],
            "sources": self.sources,
        }

    @staticmethod
    def from_json(d: dict) -> "Draw":
        return Draw(
            slot=d["slot"],
            date=d["date"],
            name=d["name"],
            draw_no=d.get("drawNo"),
            tiers=[Tier(**t) for t in d["tiers"]],
            sources=list(d.get("sources", [])),
            verified=bool(d.get("verified")),
        )


def build_tiers(scheme: Scheme, raw: dict[str, list[str]]) -> list[Tier]:
    """Attach prize metadata to raw per-rank numbers, sorted for stable output."""
    tiers = []
    for spec in scheme.tiers:
        nums = list(raw.get(spec.rank, []))
        if spec.match != FULL:
            nums = sorted(nums)
        tiers.append(Tier(spec.rank, spec.prize, spec.match, nums))
    return tiers


def validate(draw: Draw) -> None:
    """Raise ValidationError unless the draw is structurally plausible."""
    slot = SLOTS.get(draw.slot)
    if slot is None:
        raise ValidationError(f"unknown slot {draw.slot}")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", draw.date):
        raise ValidationError(f"bad date {draw.date}")
    specs = slot.scheme.tiers
    if [t.rank for t in draw.tiers] != [s.rank for s in specs]:
        raise ValidationError("tier ranks do not match scheme")
    for tier, spec in zip(draw.tiers, specs):
        if len(tier.numbers) != spec.count:
            raise ValidationError(
                f"tier {spec.rank}: expected {spec.count} numbers, got {len(tier.numbers)}")
        for n in tier.numbers:
            if spec.match == FULL:
                m = FIRST_PRIZE_RE.match(n)
                if not m or not 34 <= int(m.group(1)) <= 99:
                    raise ValidationError(f"bad 1st prize number {n!r}")
            elif not re.fullmatch(r"\d{%d}" % spec.digits, n):
                raise ValidationError(f"tier {spec.rank}: bad number {n!r}")
        if spec.match != FULL and len(set(tier.numbers)) != len(tier.numbers):
            raise ValidationError(f"tier {spec.rank}: duplicate numbers")
    if slot.scheme.ticket_format == "D5":
        first = draw.tiers[0].numbers[0]
        cons = draw.tiers[1].numbers[0]
        if first[-5:] != cons:
            raise ValidationError(f"consolation {cons} != last 5 of 1st prize {first}")
