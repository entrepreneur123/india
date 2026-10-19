"""Posts each new result to a Telegram channel (free; people forward these like WhatsApp posts).

Needs a bot (from @BotFather) that is an admin of the channel:
TELEGRAM_BOT_TOKEN (secret) and TELEGRAM_CHAT_ID (e.g. @mychannel). Each result is posted once;
what was posted is remembered in telegram-state.json next to the feed.

    python -m lottery_pipeline.telegram --out ../public --site-url https://example.in
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import urllib.parse
import urllib.request

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
STATE = "telegram-state.json"
SLUG = {"NL1PM": "1pm", "SK6PM": "6pm", "NL8PM": "8pm", "NLLAXMI": "laxmi-8pm"}


def _time(hhmm: str) -> str:
    h, m = (int(x) for x in hhmm.split(":"))
    return f"{h % 12 or 12}{'' if m == 0 else f':{m:02d}'} {'PM' if h >= 12 else 'AM'}"


def text(d: dict, site_url: str) -> str:
    y, m, dd = d["date"].split("-")
    name = d["name"].removesuffix(" WEEKLY LOTTERY").title()
    tiers = d["tiers"]
    lines = [f"{name} · {dd}.{m}.{y} · {_time(d['time'])}", f"1st Prize: {tiers[0]['numbers'][0]}"]
    second = next((t for t in tiers if t["rank"] == "2"), None)
    if second:
        lines.append("2nd Prize: " + " ".join(second["numbers"]))
    slug = SLUG.get(d["slot"], d["slot"].lower().replace("_", "-"))
    lines.append(f"All prizes + ticket check: {site_url.rstrip('/')}/result/{dd}-{m}-{y}/{slug}/")
    return "\n".join(lines)


def new_draws(feed: dict, posted: set[str], today: dt.date) -> list[dict]:
    since = (today - dt.timedelta(days=1)).isoformat()
    return sorted((d for d in feed.get("draws", []) if d.get("tiers") and d["date"] >= since and d["id"] not in posted),
                  key=lambda d: (d["date"], d["time"]))


def send(token: str, chat: str, message: str) -> None:
    data = urllib.parse.urlencode({"chat_id": chat, "text": message, "disable_web_page_preview": "true"}).encode()
    with urllib.request.urlopen(f"https://api.telegram.org/bot{token}/sendMessage", data=data, timeout=20) as r:
        if not json.load(r).get("ok"):
            raise RuntimeError("telegram refused the message")


def _api(token: str, method: str, **params) -> dict:
    data = urllib.parse.urlencode(params).encode() if params else None
    with urllib.request.urlopen(f"https://api.telegram.org/bot{token}/{method}", data=data, timeout=20) as r:
        return json.load(r)


def check(token: str, chat: str) -> str:
    """Can the bot post to the channel? One line for the run log; never posts anything."""
    try:
        me = _api(token, "getMe")["result"]
        info = _api(token, "getChat", chat_id=chat)["result"]
        member = _api(token, "getChatMember", chat_id=chat, user_id=me["id"])["result"]
    except Exception as e:  # noqa: BLE001
        return f"telegram: cannot reach {chat} as this bot ({e}); is the bot an admin of the channel and the id right?"
    ok = member.get("status") == "creator" or (member.get("status") == "administrator" and member.get("can_post_messages"))
    return (f"telegram: bot @{me.get('username')} → '{info.get('title')}' ({chat}): "
            + ("can post" if ok else f"CANNOT post (status {member.get('status')}; give it 'Post messages')"))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--site-url", required=True)
    a = ap.parse_args(argv)
    token, chat = os.environ.get("TELEGRAM_BOT_TOKEN", ""), os.environ.get("TELEGRAM_CHAT_ID", "")
    if not token or not chat:
        return 0
    feed = json.load(open(os.path.join(a.out, "v2/today.json")))
    path = os.path.join(a.out, STATE)
    first = not os.path.exists(path)
    posted = set() if first else set(json.load(open(path)).get("ids", []))
    fresh = new_draws(feed, posted, dt.datetime.now(IST).date())
    if first:  # starting up: remember what is already out instead of posting a backlog
        print(check(token, chat))
        posted |= {d["id"] for d in fresh}
        fresh = []
    for d in fresh:
        try:
            send(token, chat, text(d, a.site_url))
            posted.add(d["id"])
            print("telegram: posted", d["id"])
        except Exception as e:  # noqa: BLE001 - retried on the next pass
            print(f"::warning::telegram post failed: {e}")
            break
    keep = sorted(posted & {d["id"] for d in feed.get("draws", [])})
    with open(path, "w") as f:
        json.dump({"ids": keep}, f)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
