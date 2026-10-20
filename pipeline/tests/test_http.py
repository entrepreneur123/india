import http.client

from lottery_pipeline import http as lhttp


class _Resp:
    def __init__(self, body):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def read(self):
        if isinstance(self.body, Exception):
            raise self.body
        return self.body


def test_a_download_cut_off_mid_transfer_is_retried(monkeypatch):
    answers = [_Resp(http.client.IncompleteRead(b"x" * 16384, 500000)), _Resp(b"%PDF-1.7 whole file")]
    monkeypatch.setattr(lhttp.urllib.request, "urlopen", lambda req, timeout: answers.pop(0))
    monkeypatch.setattr(lhttp.time, "sleep", lambda s: None)
    assert lhttp.get_bytes("https://example.in/a.pdf") == b"%PDF-1.7 whole file"
