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
