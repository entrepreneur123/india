import json
import os

from sarkari import cutoffs


def test_pages_that_mention_the_cut_off_or_the_last_selected_candidate():
    text = ("Result of CBE ...\n\f"
            "8. Category-wise cut-off details\n UR 145.79 \n\f"
            " L02 UR 127 127 298\n\f"  # the table goes on without a header
            "Details of last selected candidate\n\f"
            "Marks and DoB of the last shortlisted candidates\n\f"
            "Cutoff marks\n")
    assert cutoffs.pages(text) == [2, 4, 5, 6]
    assert cutoffs.pages("2. As per Para 09 of the result write up, 542 candidates ...") == []


def _item(iid, files, published="2026-10-12T18:46:30+05:30", **kw):
    return {"id": iid, "kind": "result", "lang": "en", "exam": "CTGD", "examYear": 2026, "title": f"CT(GD) {iid}",
            "publishedAt": published, "source": "ssc", **kw,
            "files": [{"name": n, "url": f"https://ssc.gov.in/api/attachment/uploads/masterData/Results/{n}", "size": 100}
                      for n in files]}


def test_update_reads_each_writeup_once_and_lists_it_once(tmp_path):
    out = str(tmp_path)
    fetched = []

    def get(url):
        fetched.append(url.rsplit("/", 1)[1])
        return url.encode()

    def text(data):
        return "intro\fcut-off details\f" if b"writeup_1" in data else "no table here"

    items = [_item("a", ["writeup_12102026.pdf", "LIST1.pdf"]),
             _item("b", ["writeup_12102026.pdf", "LIST2.pdf"], published="2026-10-12T18:47:00+05:30"),
             _item("b2", [], published="2026-10-12T18:47:00+05:30"),
             _item("c", ["Final_Marks_Writeup_07102026.pdf"]),
             _item("d", ["writeup_x.pdf"], lang="hi"),
             _item("e", ["Write_up_x.pdf"], kind="admit_card")]
    # The same write-up under SSC's other folder (Results/ besides NoticeBoards/)
    items[2]["files"] = [dict(items[0]["files"][0], url=items[0]["files"][0]["url"].replace("Results/", "NoticeBoards/"))]
    assert cutoffs.update(out, items, get, text, warn=lambda m: None) == 3
    assert sorted(fetched) == ["Final_Marks_Writeup_07102026.pdf", "writeup_12102026.pdf", "writeup_12102026.pdf"]  # never a roll list
    root = os.path.join(out, "jobs", "v1", "results")
    listed = json.load(open(os.path.join(root, "cutoffs.json")))["writeups"]
    assert [(w["name"], w["item"], w["pages"]) for w in listed] == [("writeup_12102026.pdf", "b2", [2])]  # once, under the newest notice

    fetched.clear()
    assert cutoffs.update(out, items, get, text, warn=lambda m: None) == 0 and fetched == []

    cutoffs.update(out, [], get, text, warn=lambda m: None)  # the notices left the feed
    assert json.load(open(os.path.join(root, "cutoffs.json")))["writeups"] == []
    assert json.load(open(os.path.join(root, "cutoffs-done.json"))) == {}


def test_a_failed_download_is_tried_again_next_run_not_once_per_notice(tmp_path):
    calls = []

    def get(url):
        calls.append(url)
        raise OSError("reset")
    items = [_item("a", ["writeup_1.pdf"]), _item("b", ["writeup_1.pdf"])]
    assert cutoffs.update(str(tmp_path), items, get, lambda d: "", warn=lambda m: None) == 0
    assert len(calls) == 1
    assert json.load(open(os.path.join(str(tmp_path), "jobs", "v1", "results", "cutoffs-done.json"))) == {}


def test_failed_downloads_count_towards_the_per_run_limit(tmp_path):
    calls = []

    def get(url):
        calls.append(url)
        raise OSError("pdftotext missing")
    items = [_item(str(n), [f"writeup_{n}.pdf"]) for n in range(30)]
    cutoffs.update(str(tmp_path), items, get, lambda d: "", warn=lambda m: None)
    assert len(calls) == cutoffs.MAX_NEW_PER_RUN * 2
