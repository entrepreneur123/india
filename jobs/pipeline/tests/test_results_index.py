import json
import os

import pytest

from sarkari import results_index as ri

# Same layout as SSC's CT(GD) 2026 lists (pdftotext -layout), with made-up names.
HEADER = """                                                                                          Page No :   1
                          CONSTABLE(GD) IN CENTRAL ARMED POLICE FORCES (CAPFs) AND SSF, RIFLEMAN (GD)
                                                 IN ASSAM RIFLES EXAMINATION-2026
                             LIST-1:LIST OF FEMALE CANDIDATES QUALIFIED FOR PET/PST(IN ROLL NO ORDER)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Record# ROLL          NAME                     FATHERSNAM          MOTHERSNAM       CATEGORY
"""


def rows(rolls, start=1):
    return "".join(f"{start + i:7d} {r} NAME SURNAME              FATHER NAME         MOTHER NAME      9\n"
                   for i, r in enumerate(rolls))


def test_parse_keeps_roll_numbers_only():
    title, rolls = ri.parse(HEADER + rows(["126280000002", "126280000020", "80010001636"]) + "\fPage No :   2\n")
    assert title == "LIST-1:LIST OF FEMALE CANDIDATES QUALIFIED FOR PET/PST(IN ROLL NO ORDER)"
    assert rolls == ["126280000002", "126280000020", "80010001636"]  # mixed lengths are real (CT(GD) 2026)


@pytest.mark.parametrize("text,reason", [
    (HEADER + rows(["126280000002"]) + rows(["126280000020"], start=3), "record 3 where 2"),
    (HEADER + rows(["126280000002", "126280000002"]), "duplicate"),
    ("Final result\nRoll No Name Marks\n", "no 'Record#"),
])
def test_anything_unusual_is_not_published(text, reason):
    with pytest.raises(ri.NotIndexable, match=reason):
        ri.parse(text)


def test_shards_by_last_two_digits_sorted():
    sh = ri.shards(["126280000120", "126280000020", "80010001636"])
    assert len(sh) == 100 and sh["20"] == ["126280000020", "126280000120"] and sh["36"] == ["80010001636"]


def _item(iid, files, kind="result"):
    return {"id": iid, "kind": kind, "lang": "en", "exam": "CTGD", "examYear": 2026,
            "title": "CT(GD) 2026 - List of Female candidates", "publishedAt": "2026-10-12T18:46:30+05:30",
            "files": [{"name": n, "url": f"https://ssc.gov.in/api/attachment/uploads/masterData/Results/{n}", "size": s}
                      for n, s in files]}


def test_update_indexes_once_skips_writeups_and_drops_old_lists(tmp_path):
    out = str(tmp_path)
    fetched = []
    good = HEADER + rows(["126280000002", "226280043602"])

    def get(url):
        fetched.append(url)
        return url.encode()

    def text(data):
        return good if b"LIST1" in data else "garbage"

    items = [_item("ssc-a", [("LIST1.pdf", 100), ("writeup_x.pdf", 10), ("LIST9.pdf", 100)]),
             _item("ssc-b", [("LIST2.pdf", 10**9)])]  # too big: never downloaded
    assert ri.update(out, items, get, text, warn=lambda m: None) == 1
    assert [u.rsplit("/", 1)[1] for u in fetched] == ["LIST1.pdf", "LIST9.pdf"]
    root = os.path.join(out, "jobs", "v1", "results")
    index = json.load(open(os.path.join(root, "index.json")))["lists"]
    assert [(x["count"], x["title"][:6], x["item"]) for x in index] == [(2, "LIST-1", "ssc-a")]
    shard = json.load(open(os.path.join(root, index[0]["id"], "02.json")))
    assert shard == ["126280000002", "226280043602"]
    stored = open(os.path.join(root, index[0]["id"], "02.json")).read() + open(os.path.join(root, "index.json")).read()
    assert "NAME" not in stored and "FATHER" not in stored  # no personal data beyond the roll number
    assert "LIST9.pdf" in "".join(json.load(open(os.path.join(root, "skipped.json"))))

    fetched.clear()
    assert ri.update(out, items, get, text, warn=lambda m: None) == 0 and fetched == []  # nothing re-downloaded

    ri.update(out, [], get, text, warn=lambda m: None)  # the notice left the feed
    assert json.load(open(os.path.join(root, "index.json")))["lists"] == []
    assert not os.path.exists(os.path.join(root, index[0]["id"]))


def test_a_network_error_is_retried_next_run(tmp_path):
    def get(url):
        raise OSError("reset")
    items = [_item("ssc-a", [("LIST1.pdf", 100)])]
    assert ri.update(str(tmp_path), items, get, lambda d: "", warn=lambda m: None) == 0
    skipped = json.load(open(os.path.join(str(tmp_path), "jobs", "v1", "results", "skipped.json")))
    assert skipped == {}


def test_a_list_without_its_own_title_is_named_after_the_notice(tmp_path):
    text = "Final Result\nRecord# ROLL NAME\n" + rows(["2201234567"])
    items = [_item("ssc-a", [("Final.pdf", 100)])]
    ri.update(str(tmp_path), items, lambda u: b"x", lambda d: text, warn=lambda m: None)
    index = json.load(open(os.path.join(str(tmp_path), "jobs", "v1", "results", "index.json")))["lists"]
    assert index[0]["title"] == "CT(GD) 2026 - List of Female candidates"


def test_failed_downloads_count_towards_the_per_run_limit(tmp_path):
    calls = []

    def get(url):
        calls.append(url)
        raise OSError("pdftotext missing")
    items = [_item(f"ssc-{n}", [(f"LIST{n}.pdf", 100)]) for n in range(20)]
    assert ri.update(str(tmp_path), items, get, lambda d: "", warn=lambda m: None) == 0
    assert len(calls) == ri.MAX_TRIES_PER_RUN
