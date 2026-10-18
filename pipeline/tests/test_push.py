import base64
import datetime as dt
import json
import subprocess

import pytest

from lottery_pipeline import push


def _feed(*ids):
    return {"draws": [{"id": i, "date": i[:10], "tiers": [{"rank": "1"}]} for i in ids]
            + [{"id": "2026-10-18_NLLAXMI", "date": "2026-10-18", "status": "PDF_ONLY"}]}


def test_new_ids_only_recent_with_numbers_and_unannounced():
    feed = _feed("2026-10-18_NL1PM", "2026-10-18_SK6PM", "2026-10-17_NL8PM", "2026-10-16_NL8PM")
    got = push.new_ids(feed, {"2026-10-18_NL1PM"}, dt.date(2026, 10, 18))
    assert got == ["2026-10-17_NL8PM", "2026-10-18_SK6PM"]  # PDF-only and 2-day-old results are not news


def test_message_is_data_only_high_priority():
    m = push.message(["a", "b"])["message"]
    assert "notification" not in m  # the app writes the text in the reader's language
    assert m["topic"] == "results" and m["data"] == {"type": "result", "ids": "a,b"}
    assert m["android"]["priority"] == "high"


def test_jwt_signature_verifies(tmp_path):
    key = tmp_path / "k.pem"
    subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(key)],
                   check=True, capture_output=True)
    pub = tmp_path / "p.pem"
    subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout", "-out", str(pub)], check=True, capture_output=True)
    acct = {"client_email": "x@y.iam.gserviceaccount.com", "token_uri": "https://oauth2.googleapis.com/token",
            "private_key": key.read_text()}
    jwt = push.sign_jwt(acct, 1_800_000_000)
    head, claims, sig = jwt.split(".")
    pad = lambda s: s + "=" * (-len(s) % 4)
    c = json.loads(base64.urlsafe_b64decode(pad(claims)))
    assert c["iss"] == acct["client_email"] and c["aud"] == acct["token_uri"] and c["exp"] - c["iat"] == 3600
    (tmp_path / "sig").write_bytes(base64.urlsafe_b64decode(pad(sig)))
    ok = subprocess.run(["openssl", "dgst", "-sha256", "-verify", str(pub), "-signature", str(tmp_path / "sig")],
                        input=(head + "." + claims).encode(), capture_output=True)
    assert ok.returncode == 0, ok.stdout + ok.stderr


def _write(tmp_path, feed):
    (tmp_path / "v2").mkdir(exist_ok=True)
    (tmp_path / "v2/today.json").write_text(json.dumps(feed))


def test_first_run_records_without_sending(tmp_path, monkeypatch):
    today = dt.datetime.now(push.IST).date().isoformat()
    _write(tmp_path, _feed(today + "_NL1PM"))
    monkeypatch.setattr(push, "send", lambda *a: pytest.fail("must not send on first run"))
    monkeypatch.setenv("FCM_SERVICE_ACCOUNT", "{}")
    assert push.main(["--out", str(tmp_path)]) == 0
    assert json.loads((tmp_path / "push-state.json").read_text())["ids"] == [today + "_NL1PM"]


def test_sends_new_result_once_and_keeps_failed_ones(tmp_path, monkeypatch):
    today = dt.datetime.now(push.IST).date().isoformat()
    (tmp_path / "push-state.json").write_text(json.dumps({"ids": [today + "_NL1PM"]}))
    _write(tmp_path, _feed(today + "_NL1PM", today + "_SK6PM"))
    monkeypatch.setenv("FCM_SERVICE_ACCOUNT", json.dumps({"project_id": "p"}))
    sent = []
    monkeypatch.setattr(push, "send", lambda acct, ids: (_ for _ in ()).throw(OSError("down")))
    push.main(["--out", str(tmp_path)])
    assert json.loads((tmp_path / "push-state.json").read_text())["ids"] == [today + "_NL1PM"]  # retried next run
    monkeypatch.setattr(push, "send", lambda acct, ids: sent.append(ids))
    push.main(["--out", str(tmp_path)])
    push.main(["--out", str(tmp_path)])
    assert sent == [[today + "_SK6PM"]]
