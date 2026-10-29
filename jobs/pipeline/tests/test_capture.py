import datetime as dt
import json
import os

from sarkari import capture

PAGE = b"""<html><a href="/notices/advt-01.pdf">Advt. No. 01/2026 &amp; corrigendum</a>
<a href='https://other.gov.in/x'> <b>Result</b> list </a><a href="javascript:void(0)">menu</a>
<a href="/notices/advt-01.pdf">again</a></html>"""


def test_saves_each_site_and_survives_one_that_is_down(tmp_path):
    def get(url):
        if "down" in url:
            raise OSError("timed out")
        return PAGE
    index = capture.capture(str(tmp_path), get, dt.date(2026, 10, 21),
                            {"good": "https://good.gov.in/", "down": "https://down.gov.in/"})
    assert index["good"]["ok"] and index["good"]["bytes"] == len(PAGE)
    assert index["down"] == {"url": "https://down.gov.in/", "date": "2026-10-21", "ok": False, "error": "timed out"}
    assert open(os.path.join(tmp_path, "good", "2026-10-21.html"), "rb").read() == PAGE
    assert json.load(open(os.path.join(tmp_path, "good", "2026-10-21.links.json"))) == [
        {"href": "https://good.gov.in/notices/advt-01.pdf", "text": "Advt. No. 01/2026 & corrigendum"},
        {"href": "https://other.gov.in/x", "text": "Result list"}]
    assert json.load(open(os.path.join(tmp_path, "index.json")))["down"]["ok"] is False


def test_a_certificate_for_the_other_spelling_of_the_name_is_tried_once(tmp_path):
    seen = []

    def get(url):
        seen.append(url)
        if url == "https://www.mpsc.gov.in/":
            raise OSError("certificate verify failed: Hostname mismatch, certificate is not valid for 'www.mpsc.gov.in'")
        return PAGE
    index = capture.capture(str(tmp_path), get, dt.date(2026, 10, 29), {"mpsc": "https://www.mpsc.gov.in/"})
    assert seen == ["https://www.mpsc.gov.in/", "https://mpsc.gov.in/"]
    assert index["mpsc"]["ok"] and index["mpsc"]["url"] == "https://mpsc.gov.in/"
    assert capture._other_spelling("https://rrbcdg.gov.in/a?b=1") == "https://www.rrbcdg.gov.in/a?b=1"
