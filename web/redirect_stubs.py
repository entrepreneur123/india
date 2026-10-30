"""Turn a built copy of the site into redirects to the real address.

GitHub Pages serves the data branch (the app's JSON mirror) under entrepreneur123.github.io/india/, and the
site build lands there too. Two copies of every page split Google's signals between them: rel=canonical is
only a hint, and the older github.io copy can win. GitHub Pages cannot send a 301, so each page becomes a
stub with an instant meta refresh (Google reads that as a permanent redirect), a canonical link and a
script redirect. JSON, images and the rest are left alone.

  python web/redirect_stubs.py --dir public --to https://lottodost.com --prefix /india
  python web/redirect_stubs.py --dir cf --purge    # drop stubs a copy of that tree carried over
"""
from __future__ import annotations

import argparse
import html
import json
import pathlib

MARK = "<!-- lottodost-redirect -->"
# Other apps that share the data branch and stay on github.io (they are not part of lottodost.com):
# the government-jobs site under jobs/ has its own pages, canonical links and sitemap.
OTHER_APPS = {"jobs"}


def target(rel: pathlib.PurePosixPath, to: str) -> str:
    """index.html -> /, result/x/index.html -> /result/x/, privacy.html -> /privacy.html"""
    p = str(rel)
    if p == "index.html":
        return to + "/"
    if p.endswith("/index.html"):
        return f"{to}/{p[:-len('index.html')]}"
    return f"{to}/{p}"


def stub(url: str) -> str:
    u = html.escape(url, quote=True)
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8">{MARK}<title>LottoDost</title>'
            f'<link rel="canonical" href="{u}"><meta http-equiv="refresh" content="0; url={u}">'
            f'<script>location.replace({json.dumps(url)}+location.search+location.hash)</script></head>'
            f'<body><a href="{u}">{u}</a></body></html>\n')


def stub_404(to: str, prefix: str) -> str:
    """A missing page keeps its 404 for crawlers; people are sent to the same path on the real site."""
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8">{MARK}<title>LottoDost</title>'
            f'<meta name="robots" content="noindex"><script>var p=location.pathname;'
            f'if(p.indexOf({json.dumps(prefix + "/")})===0)p=p.slice({len(prefix)});'
            f'location.replace({json.dumps(to)}+p+location.search+location.hash)</script></head>'
            f'<body><a href="{html.escape(to, quote=True)}/">{html.escape(to)}</a></body></html>\n')


def stub_tree(root: pathlib.Path, to: str, prefix: str) -> int:
    to, prefix = to.rstrip("/"), prefix.rstrip("/")
    n = 0
    for f in sorted(root.rglob("*.html")):
        rel = pathlib.PurePosixPath(f.relative_to(root).as_posix())
        if rel.parts[0].startswith(".") or rel.parts[0] in OTHER_APPS:
            continue
        f.write_text(stub_404(to, prefix) if str(rel) == "404.html" else stub(target(rel, to)), encoding="utf-8")
        n += 1
    return n


def purge(root: pathlib.Path) -> int:
    n = 0
    for f in root.rglob("*.html"):
        if MARK in f.read_text(encoding="utf-8", errors="ignore")[:300]:
            f.unlink()
            n += 1
    return n


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", type=pathlib.Path, required=True)
    ap.add_argument("--to", help="real site address, e.g. https://lottodost.com")
    ap.add_argument("--prefix", default="", help="path this copy is served under, e.g. /india")
    ap.add_argument("--purge", action="store_true", help="delete stubs instead of writing them")
    a = ap.parse_args(argv)
    if a.purge:
        print(f"redirect stubs removed: {purge(a.dir)}")
    elif a.to:
        print(f"redirect stubs: {stub_tree(a.dir, a.to, a.prefix)}")
    else:
        ap.error("--to or --purge is required")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
