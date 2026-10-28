"""Sends the Web Push alerts after each build on the India server (deploy/india/run.sh).

  python -m sarkari.pushsend --out <public> --db subscriptions.db --vapid vapid.pem --contact mailto:you@x
  python -m sarkari.pushsend --init-vapid vapid.pem      # once: makes the key, prints the public half

What is sent, once each (the "sent" table remembers):
  * every item in jobs/v1/new.json, to the phones following its exam;
  * the last two days to apply for an exam with a rules file (official closing date).
The payload carries data only (exam, kind, title, link). The phone's service worker words the
notification in the phone's language (sw.js), like the background check does.
A phone whose push address is gone (HTTP 404/410) is removed.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import os
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Callable

from . import push

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
WORKERS = 8
TTL = 24 * 3600  # a phone that is off for a day still gets it; older news isn't news


def events(public: str, today: dt.date) -> list[dict]:
    def read(name):
        try:
            with open(os.path.join(public, "jobs", "v1", name), encoding="utf-8") as f:
                return json.load(f)
        except FileNotFoundError:
            return None
    out = []
    for i in (read("new.json") or {}).get("items", []):
        if i.get("exam"):
            out.append({"key": f"item:{i['id']}", "exam": i["exam"],
                        "data": {"type": "item", "id": i["id"], "exam": i["exam"], "kind": i["kind"],
                                 "title": i["title"][:300], "url": f"#exam/{i['exam']}"}})
    for e in (read("rules/index.json") or {}).get("exams", []):
        start, end = dt.date.fromisoformat(e["applyFrom"]), dt.date.fromisoformat(e["applyTo"])
        left = (end - today).days
        if start <= today <= end and left <= 2:
            out.append({"key": f"deadline:{e['id']}:{e['applyTo']}", "exam": e["exam"],
                        "data": {"type": "deadline", "exam": e["exam"], "title": e["title"], "n": left,
                                 "url": f"#exam/{e['exam']}"}})
    return out


Sender = Callable[[dict, str], int]  # (subscription_info, payload) -> HTTP status


def webpush_sender(vapid_path: str, contact: str) -> Sender:
    from py_vapid import Vapid02
    from pywebpush import WebPushException, webpush
    vapid = Vapid02.from_file(vapid_path)

    def send(sub: dict, payload: str) -> int:
        try:
            r = webpush(sub, payload, vapid_private_key=vapid, vapid_claims={"sub": contact}, ttl=TTL, timeout=20)
            return r.status_code
        except WebPushException as e:
            return e.response.status_code if e.response is not None else 0
        except Exception:  # noqa: BLE001 - network trouble: counted as a failure, not fatal
            return 0
    return send


def run(public: str, db, send: Sender, today: dt.date, log=print) -> dict:
    todo = [e for e in events(public, today)
            if not db.execute("select 1 from sent where key=?", (e["key"],)).fetchone()]
    subs = db.execute("select endpoint, p256dh, auth, exams from subs").fetchall()
    stats = {"events": len(todo), "sent": 0, "gone": 0, "failed": 0}
    gone = set()
    for e in todo:
        targets = [(ep, p, a) for ep, p, a, ex in subs if e["exam"] in json.loads(ex) and ep not in gone]
        payload = json.dumps(e["data"], ensure_ascii=False)
        jobs = [({"endpoint": ep, "keys": {"p256dh": p, "auth": a}}, payload) for ep, p, a in targets]
        with ThreadPoolExecutor(WORKERS) as pool:
            codes = list(pool.map(lambda j: send(*j), jobs))
        for (sub, _), code in zip(jobs, codes):
            if code in (404, 410):
                gone.add(sub["endpoint"])
            elif 200 <= code < 300:
                stats["sent"] += 1
            else:
                stats["failed"] += 1
        # Marked even if some phones failed: one unreachable phone must not hold back everyone's
        # alert, and resending later would bring old news.
        db.execute("insert or ignore into sent values (?,?)", (e["key"], int(time.time())))
    for ep in gone:
        db.execute("delete from subs where endpoint=?", (ep,))
    stats["gone"] = len(gone)
    db.execute("delete from sent where at < ?", (int(time.time()) - 60 * 86400,))
    db.commit()
    log(f"push: {stats}")
    return stats


def init_vapid(path: str) -> str:
    """Make a VAPID key (once). Returns the public key for the app (applicationServerKey)."""
    from cryptography.hazmat.primitives import serialization
    from py_vapid import Vapid02
    if os.path.exists(path):
        vapid = Vapid02.from_file(path)
    else:
        vapid = Vapid02()
        vapid.generate_keys()
        vapid.save_key(path)
        os.chmod(path, 0o600)
    raw = vapid.public_key.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--init-vapid", metavar="PEM")
    ap.add_argument("--out")
    ap.add_argument("--db")
    ap.add_argument("--vapid")
    ap.add_argument("--contact", default="mailto:admin@example.invalid")
    a = ap.parse_args(argv)
    if a.init_vapid:
        print(init_vapid(a.init_vapid))
        return 0
    if not (a.out and a.db and a.vapid):
        ap.error("--out, --db and --vapid are needed")
    db = push.connect(a.db)
    run(a.out, db, webpush_sender(a.vapid, a.contact), dt.datetime.now(IST).date())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
