"""Builds the results web page from web/template.html.

Strings are read from the Android app's res/values*/strings.xml so the site and the app use the same
(localized, reviewed) wording. Usage:
    python web/build.py OUT.html                      # site for GitHub Pages (loads v2/*.json live)
    python web/build.py OUT.html --snapshot DATA_DIR  # also embed DATA_DIR/v2/{today,index}.json as a fallback
    add --fragment to drop <html>/<head>/<body> for a host that wraps the page itself
"""
from __future__ import annotations

import argparse
import json
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "android/app/src/main/res"
LANGS = {"en": "values", "hi": "values-hi", "bn": "values-bn", "ne": "values-ne", "pa": "values-pa", "mr": "values-mr"}
KEYS = """results_subtitle offline_banner upcoming_special today no_draw_day source_note state_lottery state_nagaland
state_sikkim state_punjab tag_morning tag_day tag_night verified verified_long first_prize tap_full_result pdf_only
open_official_pdf open_sheet special_tag draw_in result_coming no_result tier_1 tier_cons tier_2 tier_3 tier_4 tier_5
tier_n match_last4 match_last5 match_cons check_title check_hint ticket_number ticket_invalid check_button no_win_title
no_win_body need_full_number you_won maybe_first also_matches disclaimer_body safety_title safety_body
privacy_policy""".split()


def _unescape(s: str) -> str:
    # Android resource escapes used in this project: \' \" \n
    return s.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").strip()


def strings(folder: str) -> dict[str, str]:
    root = ET.parse(RES / folder / "strings.xml").getroot()
    out = {}
    for e in root:
        name = e.get("name")
        if e.tag == "string":
            out[name] = _unescape("".join(e.itertext()))
        elif e.tag == "plurals":  # the web only needs the general form
            other = next((i for i in e if i.get("quantity") == "other"), None)
            if other is not None:
                out[name] = _unescape("".join(other.itertext()))
    return {k: v for k, v in out.items() if k in KEYS}


def build(snapshot: pathlib.Path | None) -> str:
    i18n = {code: strings(folder) for code, folder in LANGS.items()}
    missing = [k for k in KEYS if k not in i18n["en"]]
    if missing:
        raise SystemExit(f"keys missing from values/strings.xml: {missing}")
    html = (ROOT / "web/template.html").read_text(encoding="utf-8")
    # "</" inside a <script> would end it early; JSON escaping of "/" keeps the payload inert.
    dump = lambda o: json.dumps(o, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    html = html.replace("/*I18N*/{}", dump(i18n), 1)
    if snapshot:
        snap = {"today": json.loads((snapshot / "v2/today.json").read_text()),
                "index": json.loads((snapshot / "v2/index.json").read_text())}
        html = html.replace("/*SNAPSHOT*/null", dump(snap), 1)
    return html


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--snapshot", type=pathlib.Path)
    ap.add_argument("--fragment", action="store_true", help="drop the document wrapper (for hosts that add their own)")
    a = ap.parse_args()
    html = build(a.snapshot)
    if a.fragment:
        drop = ("<!doctype html>", '<html lang="en">', "<head>", "</head>", "<body>", "</body>", "</html>")
        html = "\n".join(l for l in html.splitlines() if l.strip() not in drop and not l.startswith(("<meta charset", '<meta name="viewport"')))
    pathlib.Path(a.out).write_text(html, encoding="utf-8")
