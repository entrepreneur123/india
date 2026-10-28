"""Intent breakdown of Google India autocomplete suggestions (see NEEDS.md).

    python analyze_needs.py google_suggest_IN_2026-10-20.json

Autocomplete lists what many people type, roughly most popular first. It is not search volume:
it shows which needs exist and how they are phrased, so the shares below are shares of distinct
suggestions, not of searches.
"""
import collections
import json
import re
import sys

INTENTS = [  # first match wins
    ("info (full form, what is)", r"full form|kya hai|kya hota|meaning|what is|क्या है|फुल फॉर्म|का मतलब"),
    ("state school board 10th/12th", r"maharashtra board|telangana|\bap\b|andhra|board result|\b10th result|matric result|inter result|\bhsc\b|ssc board"),
    ("cut-off", r"cut ?off|कट ?ऑफ"),
    ("answer key", r"answer key|response sheet|उत्तर कुंजी|आंसर की"),
    ("admit card", r"admit card|hall ticket|city slip|exam city|application status|प्रवेश पत्र|एडमिट"),
    ("result / marks", r"result|merit|score ?card|marks|रिजल्ट|परिणाम"),
    ("exam date", r"exam date|date sheet|schedule|calendar|kab hoga|exam kab|परीक्षा तिथि|\bdate\b"),
    ("eligibility / age", r"eligib|age limit|qualification|age relax|योग्यता|आयु"),
    ("salary / posts", r"salary|in hand|post ?list|posts|job profile|promotion|pay ?scale|वेतन|सैलरी"),
    ("syllabus / pattern", r"syllabus|pattern|पाठ्यक्रम|सिलेबस"),
    ("preparation", r"mock|previous year|question|\bpyq|book|test series|practice|quiz|\bgk\b|math|reasoning|english|preparation|taiyari|strategy|class|coaching|notes"),
    ("physical test", r"physical|\bpet\b|\bpst\b|height|running|medical|chest|दौड़"),
    ("notification / vacancy / apply", r"notification|vacanc|recruitment|apply|online form|\bform\b|last date|bharti|भर्ती|नोटिफिकेशन|वैकेंसी|job|naukri|नौकरी|रिक्ति"),
    ("login / website", r"login|gov\.?in|official website|website|registration|\botr\b|portal|\bapp\b|nic in"),
]


def intent(q: str) -> str:
    return next((name for name, pat in INTENTS if re.search(pat, q, re.I)), "other (exam name alone etc.)")


def main(path: str) -> None:
    data = json.load(open(path, encoding="utf-8"))
    first = {}
    for seed, res in data.items():
        for sugg in res.values():
            for s in sugg:
                first.setdefault(s, seed)
    c = collections.Counter(intent(q) for q in first)
    print(f"{len(first)} distinct suggestions\n")
    for k, v in c.most_common():
        print(f"  {k:34s}{v:5d}  {v * 100 / len(first):4.1f}%")
    print("\nGoogle's own top 10 for each seed (most popular first):")
    for seed, res in data.items():
        print(f"  [{seed}] " + " | ".join(res[seed]))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "google_suggest_IN_2026-10-20.json")
