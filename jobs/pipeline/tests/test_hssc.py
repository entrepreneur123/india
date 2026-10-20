import datetime as dt
import json
import os
import urllib.parse

import pytest

from sarkari import hssc, ssc

HERE = os.path.dirname(__file__)
NOW = dt.datetime(2026, 10, 20, 17, 0, tzinfo=ssc.IST)


def pages():
    """Real responses saved on 20-10-2026: page 1 and 2 of each section."""
    out = {}
    for section in hssc.SECTIONS:
        for page in (1, 2):
            with open(os.path.join(HERE, "fixtures", f"hssc_{section}_page{page}.json"), encoding="utf-8") as f:
                out[(section, page)] = f.read()
    return out


def fake(store=None, calls=None):
    store = store or pages()

    def get_text(url):
        q = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
        key = (q["section"][0], int(q["page"][0]))
        if calls is not None:
            calls.append(key)
        # Beyond the saved pages: an empty last page, as the API gives past last_page.
        return store.get(key, json.dumps({"current_page": key[1], "data": [], "next_page_url": None}))
    return get_text


def test_real_lists_within_the_window():
    calls = []
    items = hssc.fetch(fake(calls=calls), NOW, 120, warn=lambda m: None)
    assert items and all(i.published.date() >= dt.date(2026, 6, 22) for i in items)
    assert all(i.source == "hssc" and i.date_only and i.exam == "HSSC" for i in items)
    assert all(i.files[0][1].startswith("https://hssc.gov.in/file/") for i in items)
    assert [i.published for i in items] == sorted((i.published for i in items), reverse=True)
    # Pages stop once a page reaches past the window.
    assert ("advertisements", 2) not in calls and ("publicNotice", 2) in calls


def test_titles_and_kinds_from_real_rows():
    got = {i.title: i.kind for i in hssc.fetch(fake(), NOW, 365, warn=lambda m: None)}
    assert got["HSSC Advt. No. 06/2026 (Group-C)"] == "recruitment"
    assert got["HSSC Advt. No. 05/2026 (Group-D) Closing Date Extension"] == "amendment"
    assert got["Final Answer Key For The Post Of Primary Teacher (PRT) (Advt. No. 5/2024, Cat. No. 01)"] == "answer_key"
    assert got["Public notice regarding PST"] == "notice"  # advtNo "Various": nothing to add
    revised = next(t for t in got if t.startswith("Declaration of revised final result for the post of TGT Science"))
    assert got[revised] == "result"
    assert any(t.startswith("HSSC: Corrigendum") for t in got)  # not "HSSC Advt. No. Corrigendum"


@pytest.mark.parametrize("row,reason", [
    ({"id": "../../etc", "published_on": "2026-09-10", "title": "x"}, "id"),
    ({"id": "ac1f23cd-9fda-137e-81a0-89a629190004", "published_on": "10-09-2026", "title": "x"}, "published_on"),
    ({"id": "ac1f23cd-9fda-137e-81a0-89a629190004", "published_on": "2027-09-10", "title": "x"}, "out of range"),
    ({"id": "ac1f23cd-9fda-137e-81a0-89a629190004", "published_on": "2026-09-10"}, "no title"),
])
def test_bad_rows_are_rejected(row, reason):
    with pytest.raises(ssc.RecordError, match=reason):
        hssc.parse_row(row, "publicNotice", NOW.date())


def test_a_changed_api_is_not_published():
    store = pages()
    store[("result", 1)] = "<html>maintenance</html>"
    with pytest.raises(ssc.FormatChanged, match="not JSON"):
        hssc.fetch(fake(store), NOW, 120, warn=lambda m: None)
    store = pages()
    store[("advertisements", 1)] = json.dumps({"current_page": 1, "data": []})
    with pytest.raises(ssc.FormatChanged, match="empty"):
        hssc.fetch(fake(store), NOW, 120, warn=lambda m: None)
    store = pages()
    doc = json.loads(store[("publicNotice", 1)])
    for row in doc["data"]:
        row["published_on"] = "yesterday"
    store[("publicNotice", 1)] = json.dumps(doc)
    with pytest.raises(ssc.FormatChanged, match="failed validation"):
        hssc.fetch(fake(store), NOW, 120, warn=lambda m: None)
