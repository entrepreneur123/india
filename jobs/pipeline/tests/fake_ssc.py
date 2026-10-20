"""Serves the saved ssc.gov.in responses in tests/fixtures by URL, like http.get_json."""
from __future__ import annotations

import copy
import json
import os
import urllib.parse

FIX = os.path.join(os.path.dirname(__file__), "fixtures")
EMPTY_PAGE = {"statusCode": "200", "data": [], "paginate": {"totalRecords": 0, "totalPerpage": 0, "nextPage": 0}}


def load(name: str) -> dict:
    with open(os.path.join(FIX, name), encoding="utf-8") as f:
        return json.load(f)


class FakeSSC:
    def __init__(self):
        self.urls: list[str] = []
        self.overrides: dict[tuple, dict] = {}  # (contentType, language, page) -> response

    def __call__(self, url: str) -> dict:
        self.urls.append(url)
        u = urllib.parse.urlparse(url)
        q = dict(urllib.parse.parse_qsl(u.query))
        if u.path.endswith("/allExams"):
            return load("all_exams.json")
        if u.path.endswith("/lastUpdates"):
            return load("last_updates.json")
        if u.path.endswith("/ssc-calendar"):
            return load("calendar_en.json")
        assert u.path.endswith("/records"), url
        assert q.get("pageType") == "filter", "records must be fetched in filter mode (live, uncached)"
        key = (q["contentType"], q.get("language"), int(q["page"]))
        if key in self.overrides:
            return copy.deepcopy(self.overrides[key])
        name = f"{key[0].replace('-', '_')}_{key[1] or 'all'}_p{key[2]}.json"
        return load(name) if os.path.exists(os.path.join(FIX, name)) else copy.deepcopy(EMPTY_PAGE)
