"""Kerala results from three public copies (fixtures: real pages of 17 and 18 Oct 2026)."""
import datetime as dt
import json
import pathlib

import pytest

from lottery_pipeline import build, kerala

F = pathlib.Path(__file__).parent / "fixtures" / "kerala"


def read(name):
    t = (F / name).read_text()
    return kerala.html_text(t) if name.endswith(".html") else t


def tiers(name):
    return {t["rank"]: t for t in kerala.parse(read(name))}


def test_every_copy_parses_to_the_published_shape():
    t = tiers("govdotin_18-10-2026.txt")
    assert t["1"] == {"rank": "1", "prize": 1_00_00_000, "match": "EXACT", "numbers": ["MM 289707"]}
    assert t["CONS"]["numbers"][0] == "MA 289707" and len(t["CONS"]["numbers"]) == 11
    assert t["2"]["numbers"] == ["MK 867989"] and t["3"]["prize"] == 5_00_000
    assert [len(t[r]["numbers"]) for r in "456789"] == [19, 6, 25, 76, 92, 150]
    assert tiers("klnet_18-10-2026.html") == t          # an independent copy agrees exactly
    assert kerala.header(read("govdotin_18-10-2026.txt"), "SM") == ("SM", 77)
    assert kerala.header(read("klresult_17-10-2026.html"), "KR") == ("KR", 772)


def test_a_typo_in_one_copy_is_outvoted_and_an_unresolved_tier_is_flagged():
    # 17-10: keralalotteryresult.net misses 5383 in the 4th prize and has no 9th prize;
    # govdotin and keralalotteries.net differ on one 9th-prize number (9865 / 9465).
    reads = [tiers(f"{s}_17-10-2026.{e}") for s, e in (("govdotin", "txt"), ("klresult", "html"), ("klnet", "html"))]
    out, used = kerala.vote(reads)
    by = {t["rank"]: t for t in out}
    assert "5383" in by["4"]["numbers"] and by["4"].get("confirmed", True)
    assert by["9"]["confirmed"] is False and "9865" in by["9"]["numbers"]   # govdotin's reading, flagged
    assert all(by[r].get("confirmed", True) for r in ["1", "CONS", "2", "3", "5", "6", "7", "8"])


def test_no_publication_when_copies_disagree_on_a_top_prize():
    a = tiers("govdotin_18-10-2026.txt")
    b = json.loads(json.dumps(a))
    b["1"]["numbers"] = ["MM 289708"]
    assert kerala.vote([a, b]) == (None, set())
    assert kerala.vote([a]) == (None, set())        # one copy alone is never enough


def test_bad_pages_are_rejected():
    with pytest.raises(kerala.KeralaError):
        kerala.parse("Kerala lottery result will be published at 4 PM")
    t = read("govdotin_18-10-2026.txt").replace("0197 1159", "1159 0197")   # out of order: a cut or mixed page
    with pytest.raises(kerala.KeralaError):
        kerala.parse(t)


def test_blog_post_is_found_by_date_in_the_feed(monkeypatch):
    feed = {"feed": {"entry": [
        {"link": [{"rel": "alternate", "href": "https://x.test/2021/05/kerala-lottery-today-result.html"}],
         "content": {"$t": "<p>yesterday's page, rewritten daily</p>"}},
        {"link": [{"rel": "alternate", "href": "https://x.test/2026/10/samrudhi-kerala-lottery-result-sm-77-today-18-10-2026.html"}],
         "content": {"$t": (F / "klnet_18-10-2026.html").read_text()}},
    ]}}
    monkeypatch.setattr(kerala, "get_bytes", lambda url, **k: json.dumps(feed).encode())
    text, url = kerala.from_blog("https://x.test", dt.date(2026, 10, 18))
    assert url.endswith("18-10-2026.html") and "289707" in text
    assert kerala.from_blog("https://x.test", dt.date(2026, 10, 19)) is None


def test_build_publishes_kerala_after_the_draw_only(tmp_path, monkeypatch):
    from lottery_pipeline import official, sambad
    monkeypatch.setattr(sambad, "fetch", lambda *a, **k: None)
    monkeypatch.setattr(sambad, "listed_today", lambda: [])
    monkeypatch.setattr(official, "list_codes", lambda date: [])
    reads = {"govdotin": read("govdotin_18-10-2026.txt"), "net": read("klnet_18-10-2026.html")}
    monkeypatch.setattr(kerala, "from_govdotin", lambda d: (reads["govdotin"], "https://g.test/18.pdf"))
    monkeypatch.setattr(kerala, "from_blog", lambda base, d: (reads["net"], base + "/post") if "keralalotteries.net" in base else None)
    day = dt.date(2026, 10, 18)

    def at(hh, mm):
        build.run(str(tmp_path), day, 0, use_official=True, deep=False, ocr=False, use_kerala=True,
                  now_ist=dt.datetime(2026, 10, 18, hh, mm, tzinfo=build.IST))
        return {d["id"]: d for d in json.loads((tmp_path / "v2/today.json").read_text())["draws"]}

    assert "2026-10-18_KL" not in at(15, 30)
    kl = at(16, 30)["2026-10-18_KL"]
    assert kl["lottery"] == "kerala" and kl["name"] == "SAMRUDHI SM-77" and kl["format"] == "B6"
    assert kl["tiers"][0]["numbers"] == ["MM 289707"]
    assert [s["kind"] for s in kl["sources"]] == ["copy", "copy", "officialSite"]
