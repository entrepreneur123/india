"""Instant phone alerts (Web Push), run on the India server behind Caddy (deploy/india).

The app subscribes the browser to push and tells this server which exams to alert about:

  POST /push/subscribe     {"subscription": {"endpoint", "keys": {"p256dh", "auth"}}, "exams": [...], "lang": "en"|"hi"}
  POST /push/unsubscribe   {"endpoint": "..."}
  GET  /push/health        "ok"

What is stored per phone: the browser's push address and keys, the followed exam codes and the
language. No name, number or e-mail. pushsend.py reads the same database after every build.

Only the browsers' own push services are accepted as addresses, so the server can't be used to
make requests to anywhere else. Bodies, exam lists and the number of phones are capped.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sqlite3
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 4096
MAX_EXAMS = 40
MAX_SUBSCRIPTIONS = 200_000
RATE_PER_MINUTE = 30  # per client address
# Push services of Chrome/Edge/Samsung (FCM), Firefox, Safari and Windows.
PUSH_HOSTS = ("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com",
              ".push.apple.com", ".notify.windows.com")
FAMILIES = os.path.join(os.path.dirname(__file__), "..", "exam_families.json")

SCHEMA = """
create table if not exists subs (endpoint text primary key, p256dh text not null, auth text not null,
                                 exams text not null, lang text not null, updated integer not null);
create table if not exists sent (key text primary key, at integer not null);
"""


class Invalid(ValueError):
    pass


def connect(path: str) -> sqlite3.Connection:
    db = sqlite3.connect(path, timeout=30, check_same_thread=False)
    db.execute("pragma journal_mode=wal")
    db.executescript(SCHEMA)
    return db


def _b64url_len(value: object, raw_len: int) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]+={0,2}", value):
        raise Invalid("key is not base64url")
    try:
        raw = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
    except ValueError:
        raise Invalid("key is not base64url") from None
    if len(raw) != raw_len:
        raise Invalid(f"key has {len(raw)} bytes, expected {raw_len}")
    return value


def known_exams() -> set[str]:
    with open(FAMILIES, encoding="utf-8") as f:
        return set(json.load(f)["families"])


def parse_subscription(body: dict, exams_known: set[str]) -> tuple[str, str, str, list[str], str]:
    sub = body.get("subscription")
    if not isinstance(sub, dict):
        raise Invalid("no subscription")
    endpoint = sub.get("endpoint")
    if not isinstance(endpoint, str) or len(endpoint) > 1000:
        raise Invalid("bad endpoint")
    url = urllib.parse.urlsplit(endpoint)
    host = (url.hostname or "").lower()
    if url.scheme != "https" or url.port not in (None, 443) or \
            not any(host == h or (h.startswith(".") and host.endswith(h)) for h in PUSH_HOSTS):
        raise Invalid("endpoint is not a browser push service")
    keys = sub.get("keys") or {}
    p256dh = _b64url_len(keys.get("p256dh"), 65)  # uncompressed P-256 point
    auth = _b64url_len(keys.get("auth"), 16)
    exams = body.get("exams")
    if not isinstance(exams, list) or len(exams) > MAX_EXAMS or not all(isinstance(e, str) for e in exams):
        raise Invalid("bad exams")
    exams = sorted(set(exams) & exams_known)
    lang = body.get("lang") if body.get("lang") in ("en", "hi") else "en"
    return endpoint, p256dh, auth, exams, lang


class RateLimit:
    def __init__(self, per_minute: int):
        self.per_minute, self.hits, self.lock = per_minute, {}, threading.Lock()

    def allow(self, client: str, now: float) -> bool:
        with self.lock:
            recent = [t for t in self.hits.get(client, []) if now - t < 60]
            if len(recent) >= self.per_minute:
                self.hits[client] = recent
                return False
            recent.append(now)
            self.hits[client] = recent
            if len(self.hits) > 10_000:  # forget idle clients
                self.hits = {k: v for k, v in self.hits.items() if v and now - v[-1] < 60}
            return True


def make_handler(db: sqlite3.Connection, origin: str, exams_known: set[str], limit: RateLimit):
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        server_version = "sarkari-push"

        def _reply(self, code: int, text: str = "ok") -> None:
            body = text.encode()
            self.send_response(code)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("Vary", "Origin")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):  # no client addresses in the logs
            pass

        def do_OPTIONS(self):
            self._reply(204, "")

        def do_GET(self):
            self._reply(200 if self.path == "/push/health" else 404, "ok" if self.path == "/push/health" else "not found")

        def do_POST(self):
            # Caddy appends the real address last; anything before it came from the client.
            client = self.headers.get("X-Forwarded-For", self.client_address[0]).split(",")[-1].strip()
            if not limit.allow(client, time.monotonic()):
                return self._reply(429, "too many requests")
            if self.headers.get("Origin") not in (None, origin):
                return self._reply(403, "wrong origin")
            try:
                size = int(self.headers.get("Content-Length") or 0)
            except ValueError:
                size = MAX_BODY + 1
            if size <= 0 or size > MAX_BODY:
                return self._reply(413, "body too large")
            try:
                body = json.loads(self.rfile.read(size))
                if not isinstance(body, dict):
                    raise Invalid("not an object")
                if self.path == "/push/subscribe":
                    endpoint, p256dh, auth, exams, lang = parse_subscription(body, exams_known)
                    with lock:
                        count = db.execute("select count(*) from subs").fetchone()[0]
                        exists = db.execute("select 1 from subs where endpoint=?", (endpoint,)).fetchone()
                        if count >= MAX_SUBSCRIPTIONS and not exists:
                            return self._reply(503, "full")
                        if exams:
                            db.execute("insert or replace into subs values (?,?,?,?,?,?)",
                                       (endpoint, p256dh, auth, json.dumps(exams), lang, int(time.time())))
                        else:  # following nothing: nothing to send
                            db.execute("delete from subs where endpoint=?", (endpoint,))
                        db.commit()
                    return self._reply(200)
                if self.path == "/push/unsubscribe":
                    endpoint = body.get("endpoint")
                    if not isinstance(endpoint, str):
                        raise Invalid("bad endpoint")
                    with lock:
                        db.execute("delete from subs where endpoint=?", (endpoint,))
                        db.commit()
                    return self._reply(200)
                return self._reply(404, "not found")
            except (Invalid, ValueError) as e:
                return self._reply(400, str(e)[:200])

    return Handler


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True)
    ap.add_argument("--origin", required=True, help="the site's origin, e.g. https://user.github.io")
    ap.add_argument("--port", type=int, default=8787)
    a = ap.parse_args(argv)
    db = connect(a.db)
    server = ThreadingHTTPServer(("127.0.0.1", a.port), make_handler(db, a.origin, known_exams(), RateLimit(RATE_PER_MINUTE)))
    print(f"push: listening on 127.0.0.1:{a.port} for {a.origin}")
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
