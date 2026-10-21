"""Posts each new notice (SSC, UP Police board, Haryana SSC) (jobs/v1/new.json) to a Telegram channel.

SarkariResult and similar sites grow through Telegram/WhatsApp channels; this is the free
equivalent. Needs a bot (from @BotFather) that is an admin of the channel:
JOBS_TELEGRAM_BOT_TOKEN (secret) and JOBS_TELEGRAM_CHAT_ID (e.g. @mychannel). Without them it
does nothing. Each item is posted once; what was posted is kept in jobs/telegram-state.json.

    python -m sarkari.telegram --out ../../public --site-url https://example.in/jobs/
"""
from __future__ import annotations

import argparse
import json
import os
import urllib.parse
import urllib.request

from . import sources

STATE = os.path.join("jobs", "telegram-state.json")
LABEL = {
    "recruitment": "New job", "amendment": "Change", "result": "Result", "answer_key": "Answer key",
    "admit_card": "Admit card", "vacancy": "Vacancy", "schedule": "Exam dates", "notice": "Notice",
}
SKIP_EXAMS = {"LDCE"}  # departmental exams: only for serving employees


def text(item: dict, site_url: str, families: dict | None = None) -> str:
    """Plain text (no parse_mode), so nothing in an SSC headline can turn into markup."""
    head = LABEL.get(item["kind"], "Notice")
    if item.get("exam"):
        fam = (families or {}).get(item["exam"], {})
        name = fam["short"] if fam.get("source") else item["exam"]  # our codes for other boards mean nothing to readers
        head += f" · {name}{' ' + str(item['examYear']) if item.get('examYear') else ''}"
    lines = [head, item["title"]]
    if item["files"]:
        lines.append(f"Official PDF: {item['files'][0]['url']}")
    elif item.get("link") and sources.official(item["link"]):
        lines.append(f"Official page: {item['link']}")
    lines.append(f"All updates + eligibility check: {site_url}")
    lines.append(f"(Not a government channel. Source: {sources.host(item.get('source', 'ssc'))})")
    return "\n".join(lines)


def to_post(items: list[dict], posted: set[str]) -> list[dict]:
    return [i for i in reversed(items) if i["id"] not in posted and i.get("exam") not in SKIP_EXAMS]


def send(token: str, chat: str, message: str) -> None:
    data = urllib.parse.urlencode({"chat_id": chat, "text": message, "disable_web_page_preview": "true"}).encode()
    with urllib.request.urlopen(f"https://api.telegram.org/bot{token}/sendMessage", data=data, timeout=20) as r:
        if not json.load(r).get("ok"):
            raise RuntimeError("telegram refused the message")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--site-url", required=True)
    ap.add_argument("--state", help="where to remember what was posted (default: <out>/jobs/telegram-state.json); "
                                    "the India server keeps it on its own disk, since it resets the data copy each run")
    a = ap.parse_args(argv)
    token, chat = os.environ.get("JOBS_TELEGRAM_BOT_TOKEN", ""), os.environ.get("JOBS_TELEGRAM_CHAT_ID", "")
    if not token or not chat:
        return 0
    try:
        with open(os.path.join(a.out, "jobs", "v1", "new.json"), encoding="utf-8") as f:
            items = json.load(f)["items"]
    except FileNotFoundError:
        return 0
    try:
        with open(os.path.join(a.out, "jobs", "v1", "exams.json"), encoding="utf-8") as f:
            families = json.load(f)["families"]
    except FileNotFoundError:
        families = {}
    path = a.state or os.path.join(a.out, STATE)
    try:
        with open(path, encoding="utf-8") as f:
            posted = set(json.load(f).get("ids", []))
    except FileNotFoundError:
        posted = set()
    for item in to_post(items, posted):
        try:
            send(token, chat, text(item, a.site_url, families))
            posted.add(item["id"])
            print("telegram: posted", item["id"])
        except Exception as e:  # noqa: BLE001 - retried on the next run
            print(f"::warning::telegram post failed: {e}")
            break
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        # new.json only holds this run's news, so only those ids need remembering.
        json.dump({"ids": sorted(posted & {i["id"] for i in items})}, f)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
