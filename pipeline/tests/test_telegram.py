import datetime as dt
import json

from lottery_pipeline import telegram

WISH = {"id": "2026-10-18_NL1PM", "slot": "NL1PM", "date": "2026-10-18", "time": "13:00",
        "name": "DEAR WISH SUNDAY WEEKLY LOTTERY",
        "tiers": [{"rank": "1", "numbers": ["74D 70698"]}, {"rank": "CONS", "numbers": ["70698"]},
                  {"rank": "2", "numbers": ["15172", "15525"]}]}


def test_message_has_numbers_and_link():
    t = telegram.text(WISH, "https://example.in")
    assert t.splitlines() == ["Dear Wish Sunday · 18.10.2026 · 1 PM", "1st Prize: 74D 70698", "2nd Prize: 15172 15525",
                              "All prizes + ticket check: https://example.in/result/18-10-2026/1pm/"]


def test_posts_each_new_result_once_and_not_the_backlog(tmp_path, monkeypatch):
    (tmp_path / "v2").mkdir()
    today = dt.datetime.now(telegram.IST).date().isoformat()
    d1 = dict(WISH, id=today + "_NL1PM", date=today)
    (tmp_path / "v2/today.json").write_text(json.dumps({"draws": [d1]}))
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "t"); monkeypatch.setenv("TELEGRAM_CHAT_ID", "@c")
    sent = []
    monkeypatch.setattr(telegram, "send", lambda tok, chat, msg: sent.append(msg))
    telegram.main(["--out", str(tmp_path), "--site-url", "https://example.in"])
    assert sent == []  # first run: already-published results are not posted
    d2 = dict(WISH, id=today + "_SK6PM", slot="SK6PM", date=today, time="18:00")
    (tmp_path / "v2/today.json").write_text(json.dumps({"draws": [d1, d2]}))
    telegram.main(["--out", str(tmp_path), "--site-url", "https://example.in"])
    telegram.main(["--out", str(tmp_path), "--site-url", "https://example.in"])
    assert len(sent) == 1 and "6 PM" in sent[0] and "/6pm/" in sent[0]
