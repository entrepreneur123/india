"""Fails if a translation misses a key, has an extra key, or changes format placeholders.

A wrong placeholder (e.g. %1$d -> %1$s) crashes the app at runtime only in that language,
so this runs in CI before the build.  Usage: python tools/check_translations.py
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
PH = re.compile(r"%(\d+\$)?[sd]")


def load(path):
    root = ET.parse(path).getroot()
    out = {}
    for el in root:
        name = el.get("name")
        if el.get("translatable") == "false":
            continue
        if el.tag == "string":
            out[name] = "".join(el.itertext())
        elif el.tag == "plurals":
            for item in el:
                out[f"{name}#{item.get('quantity')}"] = "".join(item.itertext())
    return out


base = load(os.path.join(RES, "values", "strings.xml"))
errors = 0
for path in sorted(glob.glob(os.path.join(RES, "values-*", "strings.xml"))):
    lang = os.path.basename(os.path.dirname(path))[len("values-"):]
    tr = load(path)
    base_keys = {k.split("#")[0] for k in base}
    tr_keys = {k.split("#")[0] for k in tr}
    for k in sorted(base_keys - tr_keys):
        print(f"{lang}: missing {k}"); errors += 1
    for k in sorted(tr_keys - base_keys):
        print(f"{lang}: extra {k}"); errors += 1
    for k, v in tr.items():
        ref = base.get(k) or base.get(k.split("#")[0] + "#other")
        if ref is not None and sorted(PH.findall(v)) != sorted(PH.findall(ref)):
            print(f"{lang}: placeholder mismatch in {k}: {PH.findall(v)} vs {PH.findall(ref)}"); errors += 1
    print(f"{lang}: {len(tr_keys)} keys checked")
sys.exit(1 if errors else 0)
