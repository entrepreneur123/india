"""Sends a Firebase Cloud Messaging (HTTP v1) data message when new results are published.

One message per draw, to that draw's topic (r_NL1PM, r_KL, r_SPECIAL...), so a phone only wakes for the
results its owner asked for. The message carries the draw's name and 1st prize: the app can show the
notification even when the feed download fails, then fetches the feed to check saved tickets. Run after the feed is live on the
mirror the app reads first, otherwise phones would wake up and download the old feed.

Credentials: a Google service-account JSON with the "Firebase Cloud Messaging API" role, passed in
the FCM_SERVICE_ACCOUNT environment variable. Signing uses the openssl CLI (RS256), so this module
needs no third-party Python packages.

    python -m lottery_pipeline.push --out ../public --wait-url https://.../v2/today.json
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

TOPIC = "results"   # credential check only; result messages go to per-draw topics
SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
STATE = "push-state.json"   # ids already announced; lives next to the feed on the data branch
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))


def _b64(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def sign_jwt(account: dict, now: int) -> str:
    """Service-account assertion for the OAuth token endpoint (RFC 7523)."""
    header = {"alg": "RS256", "typ": "JWT"}
    claims = {"iss": account["client_email"], "scope": SCOPE, "aud": account["token_uri"],
              "iat": now, "exp": now + 3600}
    signing_input = _b64(json.dumps(header, separators=(",", ":")).encode()) + "." + \
        _b64(json.dumps(claims, separators=(",", ":")).encode())
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as f:
        f.write(account["private_key"])
        key = f.name
    try:
        sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key], input=signing_input.encode(),
                             capture_output=True, check=True).stdout
    finally:
        os.unlink(key)
    return signing_input + "." + _b64(sig)


def access_token(account: dict) -> str:
    body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                   "assertion": sign_jwt(account, int(time.time()))}).encode()
    with urllib.request.urlopen(urllib.request.Request(account["token_uri"], data=body), timeout=30) as r:
        return json.load(r)["access_token"]


def ping() -> dict:
    """Credential check: the app ignores this type, so no phone shows anything."""
    return {"message": {"topic": TOPIC, "data": {"type": "ping"}, "android": {"priority": "normal", "ttl": "60s"}}}


def alert_key(d: dict) -> str:
    """Same as the app's Prefs.alertKey: the per-draw alert switch, and the topic a phone subscribes to."""
    return "KL" if d.get("lottery") == "kerala" else "SPECIAL" if d.get("special") else d["slot"]


def message(d: dict) -> dict:
    # High priority wakes the phone from Doze; only phones that want this draw get it, and the app shows
    # a notification for every one (Android lowers the priority of apps that don't). TTL: after an hour
    # a result is not news.
    return {"message": {"topic": "r_" + alert_key(d),
                        "data": {"type": "result", "id": d["id"], "key": alert_key(d), "name": d["name"][:80],
                                 "first": d["tiers"][0]["numbers"][0]},
                        "android": {"priority": "high", "ttl": "3600s"}}}


def send(account: dict, draw: dict | None) -> str:
    """Sends a result message (or a ping when draw is None); returns FCM's message name."""
    url = f"https://fcm.googleapis.com/v1/projects/{account['project_id']}/messages:send"
    body = ping() if draw is None else message(draw)
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={
        "Authorization": "Bearer " + access_token(account), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r).get("name", "")
    except urllib.error.HTTPError as e:  # FCM explains the problem in the body (API disabled, bad key...)
        raise RuntimeError(f"HTTP {e.code}: {e.read()[:300].decode('utf-8', 'replace')}") from None


def new_draws(today_feed: dict, announced: set[str], today: dt.date) -> list[dict]:
    """Results with numbers from today or yesterday that were not announced yet, oldest first."""
    since = (today - dt.timedelta(days=1)).isoformat()
    return sorted((d for d in today_feed.get("draws", [])
                   if d.get("tiers") and d["date"] >= since and d["id"] not in announced), key=lambda d: d["id"])


def new_ids(today_feed: dict, announced: set[str], today: dt.date) -> list[str]:
    return [d["id"] for d in new_draws(today_feed, announced, today)]


def wait_live(url: str, ids: list[str], timeout_s: int = 300) -> bool:
    """True once the public feed at `url` contains every id (GitHub Pages deploys ~1 min after push)."""
    end = time.time() + timeout_s
    while time.time() < end:
        try:
            req = urllib.request.Request(url + "?t=" + str(int(time.time())), headers={"Cache-Control": "no-cache"})
            with urllib.request.urlopen(req, timeout=20) as r:
                live = {d["id"] for d in json.load(r).get("draws", []) if d.get("tiers")}
            if set(ids) <= live:
                return True
        except Exception:  # noqa: BLE001 - keep waiting through transient errors
            pass
        time.sleep(15)
    return False


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--out", required=True, help="feed directory (contains v2/today.json)")
    p.add_argument("--wait-url", help="public today.json to wait for before sending")
    p.add_argument("--dry-run", action="store_true")
    a = p.parse_args(argv)

    with open(os.path.join(a.out, "v2/today.json")) as f:
        feed = json.load(f)
    state_path = os.path.join(a.out, STATE)
    first_run = not os.path.exists(state_path)
    announced = set() if first_run else set(json.load(open(state_path)).get("ids", []))
    today = dt.datetime.now(IST).date()
    draws = new_draws(feed, announced, today)
    ids = [d["id"] for d in draws]
    if not ids:
        return 0
    raw = os.environ.get("FCM_SERVICE_ACCOUNT", "")
    if first_run:
        print(f"push: first run, recording {len(ids)} existing results without announcing them")
        if raw and not a.dry_run:
            try:
                print("push: credentials OK, test message", send(json.loads(raw), None))
            except Exception as e:  # noqa: BLE001
                print(f"::warning::push credential check failed: {e}")
    elif a.dry_run:
        for d in draws:
            print("push (dry run):", json.dumps(message(d), ensure_ascii=False))
    else:
        if not raw:
            print("push: FCM_SERVICE_ACCOUNT not set; skipping")
            return 0
        if a.wait_url and not wait_live(a.wait_url, ids):
            print(f"::warning::push: {a.wait_url} did not show {ids} in time; sending anyway")
        account, sent = json.loads(raw), []
        for d in draws:
            try:
                print("push: sent", d["id"], send(account, d))
                sent.append(d["id"])
            except Exception as e:  # noqa: BLE001 - a failed push must not fail publishing
                print(f"::warning::push failed for {d['id']}: {e}")   # not recorded: the next run retries it
        ids = sent
    keep = sorted((announced | set(ids)) & {d["id"] for d in feed.get("draws", [])} | set(ids))
    with open(state_path, "w") as f:
        json.dump({"ids": keep}, f)
    return 0


if __name__ == "__main__":
    sys.exit(main())
