from __future__ import annotations

import http.client
import json
import time
import urllib.error
import urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) SarkariAlertBot/0.1 (+notice mirror; contact via GitHub)"
# ssc.gov.in is a government server shared with millions of candidates: one request at a time,
# with a pause in between. A full run is ~15 requests.
POLITE_DELAY_S = 1.0


class FetchError(RuntimeError):
    pass


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
            with urllib.request.urlopen(req, timeout=timeout) as resp:
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


def get_bytes(url: str, retries: int = 3, timeout: int = 120, max_bytes: int = 60_000_000) -> bytes:
    """GET a file (a result PDF), refusing anything over max_bytes."""
    last: Exception | None = None
    for attempt in range(retries):
        if attempt:
            time.sleep(2 ** attempt)
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
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
