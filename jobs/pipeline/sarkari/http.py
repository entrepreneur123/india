from __future__ import annotations

import http.client
import json
import re
import socket
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) SarkariAlertBot/0.1 (+notice mirror; contact via GitHub)"
# ssc.gov.in is a government server shared with millions of candidates: one request at a time,
# with a pause in between. A full run is ~15 requests.
POLITE_DELAY_S = 1.0


class FetchError(RuntimeError):
    pass


# Many Indian government servers are set up in ways browsers forgive and Python does not
# (seen from the India server, 29-10-2026). Certificates are still verified in every case:
#  * the intermediate certificate is missing ("unable to get local issuer certificate"): browsers
#    download it from the address inside the server's certificate (AIA); _issuer_pem does the same;
#  * renegotiation without RFC 5746 ("unsafe legacy renegotiation disabled"): allowed again, these
#    are read-only requests for public notices.
_AIA_CA_ISSUERS = re.compile(rb"\x06\x08\x2b\x06\x01\x05\x05\x07\x30\x02\x86([\x01-\x7f])", re.S)
_extra_ca: dict[str, str] = {}  # host -> PEM of the intermediates found for it


def _context(host: str = "") -> ssl.SSLContext:
    ctx = ssl.create_default_context()
    ctx.options |= getattr(ssl, "OP_LEGACY_SERVER_CONNECT", 0x4)
    if _extra_ca.get(host):
        ctx.load_verify_locations(cadata=_extra_ca[host])
    return ctx


def aia_issuer_urls(der: bytes) -> list[str]:
    """The "CA Issuers" addresses in a DER certificate (id-ad-caIssuers, then a URI of that length)."""
    out = []
    for m in _AIA_CA_ISSUERS.finditer(der):
        start = m.end()
        url = der[start:start + m.group(1)[0]].decode("ascii", "replace")
        if url.startswith(("http://", "https://")):
            out.append(url)
    return out


def _as_pem(body: bytes) -> str | None:
    """The certificate(s) in an AIA download (PEM, DER or a PKCS#7 bundle), as PEM that loads."""
    candidates = []
    if body.startswith(b"-----BEGIN CERTIFICATE-----"):
        candidates.append(body.decode("ascii", "replace"))
    elif body[:1] == b"\x30":
        candidates.append(ssl.DER_cert_to_PEM_cert(body))
        try:  # .p7c bundles (bpsc.bihar.gov.in, ppsc.gov.in): needs `cryptography`, there with pywebpush
            from cryptography.hazmat.primitives.serialization import Encoding, pkcs7
            candidates.append("".join(c.public_bytes(Encoding.PEM).decode() for c in pkcs7.load_der_pkcs7_certificates(body)))
        except Exception:  # noqa: BLE001 - not PKCS#7, or no cryptography: the DER try above stands
            pass
    for pem in candidates:
        try:
            ssl.create_default_context().load_verify_locations(cadata=pem)
            return pem
        except (ssl.SSLError, ValueError):
            continue
    return None


def _issuer_pem(der: bytes, timeout: int) -> str | None:
    for url in aia_issuer_urls(der):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=timeout) as resp:
                body = resp.read(100_000)
        except (urllib.error.URLError, http.client.HTTPException, TimeoutError, ConnectionError):
            continue
        pem = _as_pem(body)
        if pem:
            return pem
    return None


def _peer_certificate(host: str, port: int, timeout: int) -> bytes:
    """The server's own certificate, unverified: only to read which issuer it names. Allows legacy
    renegotiation like _context, which ssl.get_server_certificate does not (esb.mp.gov.in, upsssc.gov.in)."""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    ctx.options |= getattr(ssl, "OP_LEGACY_SERVER_CONNECT", 0x4)
    with socket.create_connection((host, port), timeout=timeout) as sock, ctx.wrap_socket(sock, server_hostname=host) as tls:
        return tls.getpeercert(binary_form=True)


def _learn_intermediates(url: str, timeout: int) -> bool:
    """Fetch the missing intermediate(s) for url's host, up to two levels; True if any was found."""
    host = urllib.parse.urlsplit(url).hostname or ""
    port = urllib.parse.urlsplit(url).port or 443
    try:
        # Unverified on purpose: only to read which issuer the server names. The page itself is then
        # fetched with full verification against the system roots plus what was found here.
        der = _peer_certificate(host, port, timeout)
    except (OSError, ValueError):
        return False
    found = []
    for _ in range(2):
        pem = _issuer_pem(der, timeout)
        if not pem:
            break
        found.append(pem)
        der = ssl.PEM_cert_to_DER_cert(pem)
    if found:
        _extra_ca[host] = "".join(found)
    return bool(found)


def _missing_issuer(e: Exception) -> bool:
    reason = getattr(e, "reason", e)
    return isinstance(reason, ssl.SSLCertVerificationError) and "unable to get local issuer" in str(reason)


def _open(req: urllib.request.Request, timeout: int):
    host = urllib.parse.urlsplit(req.full_url).hostname or ""
    try:
        return urllib.request.urlopen(req, timeout=timeout, context=_context(host))
    except urllib.error.URLError as e:
        if host in _extra_ca or not _missing_issuer(e) or not _learn_intermediates(req.full_url, timeout):
            raise
        return urllib.request.urlopen(req, timeout=timeout, context=_context(host))


def get_json(url: str, retries: int = 3, timeout: int = 40) -> dict:
    """GET a JSON document from the SSC API.

    The API answers HTTP 200 with {"statusCode": "203", "error": ...} for a bad query,
    and the connection is sometimes reset or cut short (seen 20-10-2026), so both a
    non-JSON body and a statusCode other than "200" count as a failed attempt.
    """
    last: Exception | None = None
    for attempt in range(retries):
        if attempt:
            time.sleep(2 ** attempt)
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        try:
            with _open(req, timeout) as resp:
                body = resp.read()
            doc = json.loads(body)
            if not isinstance(doc, dict) or doc.get("statusCode") != "200":
                raise FetchError(f"API error: {str(doc)[:200]}")
            time.sleep(POLITE_DELAY_S)
            return doc
        except (urllib.error.URLError, http.client.HTTPException, TimeoutError, ConnectionError,
                ValueError, FetchError) as e:
            last = e
    raise FetchError(f"GET {url} failed after {retries} attempts: {last}")


# Pages of recruiters other than SSC: the same honest User-Agent, plus the Accept headers a page
# request normally carries. A site that refuses automated clients (HTTP 403) is left alone.
PAGE_HEADERS = {"User-Agent": UA, "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "en-IN,en;q=0.9,hi;q=0.8"}


def get_bytes(url: str, retries: int = 3, timeout: int = 120, max_bytes: int = 60_000_000,
              headers: dict | None = None) -> bytes:
    """GET a file (a result PDF) or a page, refusing anything over max_bytes."""
    last: Exception | None = None
    for attempt in range(retries):
        if attempt:
            time.sleep(2 ** attempt)
        req = urllib.request.Request(url, headers=headers or {"User-Agent": UA})
        try:
            with _open(req, timeout) as resp:
                body = resp.read(max_bytes + 1)
            if len(body) > max_bytes:
                raise FetchError(f"{url}: larger than {max_bytes} bytes")
            time.sleep(POLITE_DELAY_S)
            return body
        except FetchError:
            raise
        except (urllib.error.URLError, http.client.HTTPException, TimeoutError, ConnectionError) as e:
            last = e
    raise FetchError(f"GET {url} failed after {retries} attempts: {last}")
