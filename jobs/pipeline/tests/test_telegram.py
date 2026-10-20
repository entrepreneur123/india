import json
import os

from sarkari import telegram

ITEM = {"id": "ssc-a", "kind": "recruitment", "exam": "CHSL", "examYear": 2026, "lang": "en",
        "title": "Notice of Combined Higher Secondary (10+2) Level Examination, 2026 <b>x</b>",
        "publishedAt": "2026-09-07T10:00:00+05:30",
        "files": [{"name": "n.pdf", "url": "https://ssc.gov.in/api/attachment/uploads/masterData/NoticeBoards/n.pdf", "size": 1}]}


def test_text_is_plain_and_names_the_source():
    t = telegram.text(ITEM, "https://example.in/jobs/")
    assert t.splitlines()[0] == "New job · CHSL 2026"
    assert "<b>x</b>" in t  # sent without parse_mode, so this stays literal text
    assert "Official PDF: https://ssc.gov.in/" in t and "Not a government channel" in t


def test_to_post_skips_posted_and_departmental_oldest_first():
    items = [dict(ITEM, id="ssc-new"), dict(ITEM, id="ssc-ldce", exam="LDCE"), dict(ITEM, id="ssc-old")]
    assert [i["id"] for i in telegram.to_post(items, {"ssc-x"})] == ["ssc-old", "ssc-new"]
    assert telegram.to_post(items, {"ssc-new", "ssc-old"}) == []


def test_no_token_does_nothing(tmp_path, monkeypatch):
    monkeypatch.delenv("JOBS_TELEGRAM_BOT_TOKEN", raising=False)
    assert telegram.main(["--out", str(tmp_path), "--site-url", "https://x"]) == 0
    assert not os.path.exists(os.path.join(str(tmp_path), telegram.STATE))


def test_posts_once_and_remembers(tmp_path, monkeypatch):
    out = str(tmp_path)
    os.makedirs(os.path.join(out, "jobs", "v1"))
    with open(os.path.join(out, "jobs", "v1", "new.json"), "w") as f:
        json.dump({"items": [ITEM]}, f)
    sent = []
    monkeypatch.setenv("JOBS_TELEGRAM_BOT_TOKEN", "t")
    monkeypatch.setenv("JOBS_TELEGRAM_CHAT_ID", "@c")
    monkeypatch.setattr(telegram, "send", lambda token, chat, msg: sent.append(msg))
    telegram.main(["--out", out, "--site-url", "https://x"])
    telegram.main(["--out", out, "--site-url", "https://x"])
    assert len(sent) == 1
