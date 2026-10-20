from __future__ import annotations

import http.client
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) DearResultBot/0.1 (+results mirror; contact via GitHub)"


def get_bytes(url: str, data: dict | None = None, retries: int = 3, timeout: int = 40) -> bytes | None:
    """GET (or POST form when `data` is given). Returns None on HTTP 404."""
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    last: Exception | None = None
    for attempt in range(retries):
        req = urllib.request.Request(url, data=body, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last = e
        except (urllib.error.URLError, TimeoutError, http.client.IncompleteRead, ConnectionError) as e:
            last = e   # incl. a body cut off mid-transfer (seen on 2 of ~880 archive PDFs, 20-10-2026)
        time.sleep(2 ** attempt)
    raise RuntimeError(f"GET {url} failed: {last}")


def is_pdf(url: str, timeout: int = 20) -> bool:
    """True only when the URL serves a PDF right now. Listings name files before they exist."""
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status == 200 and "pdf" in resp.headers.get("Content-Type", "").lower()
    except (urllib.error.URLError, TimeoutError):
        return False
