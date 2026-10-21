"""Today's post for the WhatsApp Channel, written to jobs/v1/whatsapp.txt for the channel's admin.

WhatsApp Channels are posted to by hand from the WhatsApp app (no posting API was found for
them), so the build prepares the text: the last day's updates, one line each, with the
official link and the site. Plain text only: WhatsApp would turn *...* and _..._ in a headline
into formatting, so those characters are removed from headlines.
"""
from __future__ import annotations

import datetime as dt

from . import sources

LABEL = {
    "recruitment": "🆕 New job", "result": "✅ Result", "admit_card": "🎫 Admit card", "answer_key": "🔑 Answer key",
    "amendment": "✏️ Change", "vacancy": "📋 Vacancy", "schedule": "📅 Exam dates", "notice": "📢 Notice",
}
ORDER = ("recruitment", "result", "admit_card", "answer_key", "vacancy", "schedule", "amendment", "notice")
MAX_LINES = 20
SKIP_EXAMS = {"LDCE"}  # departmental exams: only for serving employees


def _plain(text: str) -> str:
    return " ".join(text.translate(str.maketrans("", "", "*_~`")).split())


def digest(feed: list[dict], families: dict, now: dt.datetime, site_url: str | None) -> str:
    """Updates published in the 24 hours before now (IST); SSC's English copy only."""
    since = (now - dt.timedelta(hours=24)).isoformat(timespec="seconds")
    items = [i for i in feed if i["publishedAt"] >= since and i.get("exam") not in SKIP_EXAMS
             and (i.get("source", "ssc") != "ssc" or i.get("lang") == "en")]
    items.sort(key=lambda i: (ORDER.index(i["kind"]) if i["kind"] in ORDER else len(ORDER), i["publishedAt"]))
    lines = [f"Sarkari job updates · {now.strftime('%d %b %Y')}", ""]
    for i in items[:MAX_LINES]:
        fam = families.get(i.get("exam") or "", {})
        who = fam.get("short") or sources.SOURCES.get(i.get("source", "ssc"), {}).get("short", "")
        url = i["files"][0]["url"] if i["files"] else i.get("link")
        lines.append(f"{LABEL.get(i['kind'], LABEL['notice'])} · {who}: {_plain(i['title'])[:180]}")
        if url and sources.official(url):
            lines.append(f"   {url}")
    if not items:
        lines.append("No new official notices in the last 24 hours.")
    elif len(items) > MAX_LINES:
        lines.append(f"…and {len(items) - MAX_LINES} more on the site.")
    lines += ["", f"All updates, eligibility check and result by roll number: {site_url}" if site_url else "",
              "Unofficial channel. Always confirm on the official website."]
    return "\n".join(x for x in lines if x is not None).strip() + "\n"
