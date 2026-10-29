import ssl
import urllib.error

import pytest

from sarkari import http

CA_ISSUERS = b"\x06\x08\x2b\x06\x01\x05\x05\x07\x30\x02"  # id-ad-caIssuers
OCSP = b"\x06\x08\x2b\x06\x01\x05\x05\x07\x30\x01"


def _uri(url: bytes) -> bytes:
    return b"\x86" + bytes([len(url)]) + url


def test_issuer_address_is_read_from_the_certificate_and_ocsp_is_ignored():
    der = (b"\x30\x82junk" + OCSP + _uri(b"http://ocsp.example.in") + CA_ISSUERS
           + _uri(b"http://cacerts.example.in/Intermediate.crt") + b"more")
    assert http.aia_issuer_urls(der) == ["http://cacerts.example.in/Intermediate.crt"]
    assert http.aia_issuer_urls(b"no extension here") == []


def test_every_connection_verifies_and_allows_legacy_renegotiation():
    ctx = http._context("example.in")
    assert ctx.verify_mode == ssl.CERT_REQUIRED and ctx.check_hostname
    assert ctx.options & getattr(ssl, "OP_LEGACY_SERVER_CONNECT", 0x4)


def test_missing_intermediate_is_fetched_once_then_the_request_is_verified_again(monkeypatch):
    err = urllib.error.URLError(ssl.SSLCertVerificationError(1, "certificate verify failed: unable to get local issuer certificate"))
    calls, learned = [], []
    monkeypatch.setattr(http, "_extra_ca", {})

    def fake_urlopen(req, timeout, context):
        calls.append(context)
        if len(calls) == 1:
            raise err
        return "page"

    def fake_learn(url, timeout):
        learned.append(url)
        http._extra_ca["board.gov.in"] = "PEM"
        return True
    monkeypatch.setattr(http.urllib.request, "urlopen", fake_urlopen)
    monkeypatch.setattr(http, "_learn_intermediates", fake_learn)
    monkeypatch.setattr(http, "_context", lambda host="": ("ctx", http._extra_ca.get(host)))
    assert http._open(http.urllib.request.Request("https://board.gov.in/notices"), 10) == "page"
    assert learned == ["https://board.gov.in/notices"] and calls == [("ctx", None), ("ctx", "PEM")]


def test_other_certificate_errors_are_not_retried(monkeypatch):
    err = urllib.error.URLError(ssl.SSLCertVerificationError(1, "certificate verify failed: Hostname mismatch"))
    monkeypatch.setattr(http, "_extra_ca", {})
    monkeypatch.setattr(http.urllib.request, "urlopen", lambda req, timeout, context: (_ for _ in ()).throw(err))
    monkeypatch.setattr(http, "_learn_intermediates", lambda url, timeout: pytest.fail("must not fetch issuers"))
    with pytest.raises(urllib.error.URLError):
        http._open(http.urllib.request.Request("https://www.mpsc.gov.in/"), 10)


CERTS = __import__("os").path.join(__import__("os").path.dirname(__file__), "fixtures", "certs")


def _cert(name):
    return open(__import__("os").path.join(CERTS, name), "rb").read()


def test_issuer_downloads_in_pem_or_der_become_loadable_pem():
    pem = _cert("c.pem")
    assert http._as_pem(pem).startswith("-----BEGIN CERTIFICATE-----")
    assert http._as_pem(_cert("c.der")) == ssl.DER_cert_to_PEM_cert(_cert("c.der"))
    assert http._as_pem(b"<html>not found</html>") is None and http._as_pem(b"\x30\x03junk") is None


def test_a_pkcs7_bundle_of_issuers_is_unpacked():
    pytest.importorskip("cryptography")  # installed with pywebpush, as on the India server
    got = http._as_pem(_cert("c.p7c"))
    assert got and ssl.PEM_cert_to_DER_cert(got.strip()) == _cert("c.der")


class _Resp:
    def __init__(self, body, encoding=""):
        self.body, self.headers = body, {"Content-Encoding": encoding} if encoding else {}

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def read(self, n):
        return self.body[:n]


def test_a_page_sent_gzipped_unasked_is_unpacked_and_still_size_checked(monkeypatch):
    import gzip
    page = b"<html>" + b"x" * 5000 + b"</html>"
    monkeypatch.setattr(http, "POLITE_DELAY_S", 0)
    monkeypatch.setattr(http, "_open", lambda req, timeout: _Resp(gzip.compress(page), "gzip"))
    assert http.get_bytes("https://sbi.bank.in/web/careers/current-openings") == page
    with pytest.raises(http.FetchError, match="unpacked"):
        http.get_bytes("https://sbi.bank.in/x", max_bytes=1000)
    monkeypatch.setattr(http, "_open", lambda req, timeout: _Resp(b"%PDF-1.7 plain"))
    assert http.get_bytes("https://sbi.bank.in/a.pdf") == b"%PDF-1.7 plain"
