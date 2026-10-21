import base64
import datetime as dt
import json
import os
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from sarkari import build, push, pushsend

ORIGIN = "https://user.github.io"
P256DH = base64.urlsafe_b64encode(b"\x04" + b"\x01" * 64).decode().rstrip("=")
AUTH = base64.urlsafe_b64encode(b"\x02" * 16).decode().rstrip("=")


def sub(endpoint="https://fcm.googleapis.com/fcm/send/abc", p256dh=P256DH, auth=AUTH):
    return {"endpoint": endpoint, "keys": {"p256dh": p256dh, "auth": auth}}


@pytest.mark.parametrize("endpoint", [
    "https://fcm.googleapis.com/fcm/send/x", "https://updates.push.services.mozilla.com/wpush/v2/x",
    "https://web.push.apple.com/x", "https://db5p.notify.windows.com/w/?token=x",
])
def test_browser_push_services_are_accepted(endpoint):
    assert push.parse_subscription({"subscription": sub(endpoint), "exams": ["CGL", "NOPE"]}, {"CGL"})[3] == ["CGL"]


@pytest.mark.parametrize("body,reason", [
    ({"subscription": sub("http://fcm.googleapis.com/x"), "exams": []}, "push service"),
    ({"subscription": sub("https://evil.example/x"), "exams": []}, "push service"),
    ({"subscription": sub("https://fcm.googleapis.com.evil.example/x"), "exams": []}, "push service"),
    ({"subscription": sub("https://fcm.googleapis.com:8443/x"), "exams": []}, "push service"),
    ({"subscription": sub(p256dh="abc"), "exams": []}, "bytes"),
    ({"subscription": sub(auth="!!"), "exams": []}, "base64url"),
    ({"subscription": sub(), "exams": ["CGL"] * 41}, "bad exams"),
    ({"exams": []}, "no subscription"),
])
def test_anything_else_is_refused(body, reason):
    with pytest.raises(push.Invalid, match=reason):
        push.parse_subscription(body, {"CGL"})


@pytest.fixture
def server(tmp_path):
    db = push.connect(str(tmp_path / "subs.db"))
    srv = ThreadingHTTPServer(("127.0.0.1", 0), push.make_handler(db, ORIGIN, {"CGL", "CHSL"}, push.RateLimit(5)))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{srv.server_address[1]}", db
    srv.shutdown()


def post(url, body, origin=ORIGIN):
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", "Origin": origin})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status, r.headers.get("Access-Control-Allow-Origin")
    except urllib.error.HTTPError as e:
        return e.code, None


def test_subscribe_update_unsubscribe(server):
    base, db = server
    assert post(base + "/push/subscribe", {"subscription": sub(), "exams": ["CGL"], "lang": "hi"}) == (200, ORIGIN)
    assert post(base + "/push/subscribe", {"subscription": sub(), "exams": ["CGL", "CHSL"]})[0] == 200
    assert db.execute("select exams, lang from subs").fetchall() == [('["CGL", "CHSL"]', "en")]
    assert post(base + "/push/subscribe", {"subscription": sub(), "exams": []})[0] == 200  # follows nothing
    assert db.execute("select count(*) from subs").fetchone()[0] == 0
    post(base + "/push/subscribe", {"subscription": sub(), "exams": ["CGL"]})
    assert post(base + "/push/unsubscribe", {"endpoint": sub()["endpoint"]})[0] == 200
    assert db.execute("select count(*) from subs").fetchone()[0] == 0


def test_other_sites_bad_input_and_floods_are_refused(server):
    base, db = server
    assert post(base + "/push/subscribe", {"subscription": sub(), "exams": ["CGL"]}, origin="https://evil.example")[0] == 403
    assert post(base + "/push/subscribe", {"subscription": sub("https://evil.example/x"), "exams": ["CGL"]})[0] == 400
    assert post(base + "/push/subscribe", {"x": "y" * 5000})[0] == 413
    codes = [post(base + "/push/unsubscribe", {"endpoint": "e"})[0] for _ in range(6)]
    assert codes[-1] == 429  # 5 a minute in this test
    assert db.execute("select count(*) from subs").fetchone()[0] == 0


def _public(tmp_path, items, exams=()):
    v1 = tmp_path / "public" / "jobs" / "v1"
    (v1 / "rules").mkdir(parents=True)
    (v1 / "new.json").write_text(json.dumps({"items": items}))
    (v1 / "rules" / "index.json").write_text(json.dumps({"exams": list(exams)}))
    return str(tmp_path / "public")


def test_send_once_to_followers_and_drop_dead_phones(tmp_path):
    db = push.connect(str(tmp_path / "subs.db"))
    for name, exams in (("a", ["CGL"]), ("b", ["CGL", "CHSL"]), ("c", ["CHSL"]), ("dead", ["CGL"])):
        db.execute("insert into subs values (?,?,?,?,?,?)", (f"https://fcm.googleapis.com/{name}", P256DH, AUTH, json.dumps(exams), "en", 0))
    items = [{"id": "ssc-1", "exam": "CGL", "kind": "result", "title": "CGL result"}]
    deadline = {"id": "ssc-chsl-2026", "exam": "CHSL", "title": "CHSL 2026", "applyFrom": "2026-06-01", "applyTo": "2026-06-30"}
    public = _public(tmp_path, items, [deadline])
    got = []

    def send(sub_info, payload):
        got.append((sub_info["endpoint"].rsplit("/", 1)[1], json.loads(payload)))
        return 410 if sub_info["endpoint"].endswith("dead") else 201
    stats = pushsend.run(public, db, send, dt.date(2026, 6, 29), log=lambda m: None)
    assert sorted((n, p["type"]) for n, p in got) == [("a", "item"), ("b", "deadline"), ("b", "item"), ("c", "deadline"), ("dead", "item")]
    assert next(p for n, p in got if p["type"] == "deadline")["n"] == 1
    assert stats == {"events": 2, "sent": 4, "gone": 1, "failed": 0}
    assert db.execute("select count(*) from subs where endpoint like '%dead'").fetchone()[0] == 0
    got.clear()
    assert pushsend.run(public, db, send, dt.date(2026, 6, 29), log=lambda m: None)["events"] == 0 and got == []


def test_site_json_names_the_push_server_only_when_it_looks_right():
    key = "B" + "A" * 86
    assert build.site_info(None, "https://203-0-113-5.sslip.io/push", key)["push"] == {"url": "https://203-0-113-5.sslip.io/push", "key": key}
    assert "push" not in build.site_info(None, "http://x.example/push", key)
    assert "push" not in build.site_info(None, "https://x.example/push", "short")
    assert "push" not in build.site_info(None, None, key)


def test_a_real_encrypted_push_can_be_read_by_the_phone(tmp_path):
    """End to end with the real library: VAPID-signed, aes128gcm-encrypted, decrypted with the phone's keys."""
    pytest.importorskip("pywebpush")
    import http_ece
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    received = {}

    class FakePushService(BaseHTTPRequestHandler):
        def do_POST(self):
            received["headers"] = {k.lower(): v for k, v in self.headers.items()}
            received["body"] = self.rfile.read(int(self.headers["Content-Length"]))
            self.send_response(201)
            self.end_headers()

        def log_message(self, *a):
            pass
    srv = ThreadingHTTPServer(("127.0.0.1", 0), FakePushService)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    phone_key = ec.generate_private_key(ec.SECP256R1())
    auth = os.urandom(16)
    b64 = lambda b: base64.urlsafe_b64encode(b).decode().rstrip("=")  # noqa: E731
    phone = {"endpoint": f"http://127.0.0.1:{srv.server_address[1]}/send/1",
             "keys": {"p256dh": b64(phone_key.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)),
                      "auth": b64(auth)}}
    pem = str(tmp_path / "vapid.pem")
    public_key = pushsend.init_vapid(pem)
    assert len(public_key) == 87 and pushsend.init_vapid(pem) == public_key  # made once, then reused
    send = pushsend.webpush_sender(pem, "mailto:test@example.invalid")
    assert send(phone, json.dumps({"type": "item", "exam": "CGL"})) == 201
    srv.shutdown()
    plain = http_ece.decrypt(received["body"], private_key=phone_key, auth_secret=auth, version="aes128gcm")
    assert json.loads(plain) == {"type": "item", "exam": "CGL"}
    assert received["headers"]["authorization"].startswith("vapid t=") and f"k={public_key}" in received["headers"]["authorization"]
    assert received["headers"]["ttl"] == str(pushsend.TTL) and received["headers"]["content-encoding"] == "aes128gcm"
