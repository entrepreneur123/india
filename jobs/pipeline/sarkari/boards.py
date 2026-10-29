"""Every list board in one place: listboards.py (the first ones) and boards_*.py (by region).

All boards added from 29-10-2026 are read by the India server (india.py): many government sites
answer only from India, and one reader for them keeps their first-seen records in one file.
"""
from __future__ import annotations

from . import boards_central, boards_more_a, boards_more_b, boards_more_c, boards_recruiters, boards_states, listboards

NEW = boards_states.ALL + boards_recruiters.ALL + boards_central.ALL + boards_more_a.ALL + boards_more_b.ALL + boards_more_c.ALL
ALL = listboards.ALL + NEW
INDIA_ONLY = listboards.INDIA_ONLY | {b.SOURCE for b in NEW}
assert len({b.SOURCE for b in ALL}) == len(ALL), "two boards share a source id"
