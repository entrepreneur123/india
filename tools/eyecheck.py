"""Compare a published Dear 1st prize with the official gazette by eye, in seconds.

    python3 tools/eyecheck.py 2026-10-28_NL8PM [more ids...]

Tesseract cannot read the bold 1st-prize digits of the Nagaland gazette (pipeline/lottery_pipeline/official.py),
so a draw whose 1st prize it did not confirm opens a `review:*` incident (watch.py). This downloads the gazette,
cuts out the header with the 1st prize and consolation number, saves it as a PNG next to the published numbers
and prints both. Look at the PNG, then note the outcome in docs/INCIDENTS.md.
"""
from __future__ import annotations

import datetime as dt
import glob
import json
import os
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "pipeline"))

HEADER = (0.08, 0.30)   # the gazette's 1st-prize block: this band of the page, top to bottom


def header_crop(image, band=HEADER, width=1400):
    """The band of the gazette page that shows the draw name, date and the 1st prize, scaled to read on a screen."""
    w, h = image.size
    top, bottom = int(h * band[0]), int(h * band[1])
    crop = image.crop((0, top, w, bottom))
    return crop.resize((width, max(1, round(width * (bottom - top) / w))))


def published(draw_id: str) -> dict | None:
    date = draw_id[:10]
    p = subprocess.run(["git", "show", f"origin/data:v2/days/{date}.json"], cwd=ROOT, capture_output=True, text=True)
    if p.returncode:
        return None
    return next((d for d in json.loads(p.stdout)["draws"] if d["id"] == draw_id), None)


def gazette_png(draw_id: str, out_dir: pathlib.Path) -> pathlib.Path | None:
    from PIL import Image
    from lottery_pipeline import official
    from lottery_pipeline.http import get_bytes
    from lottery_pipeline.model import SLOTS
    date, slot_id = dt.date.fromisoformat(draw_id[:10]), draw_id[11:]
    url = official.PDF_URL.format(code=SLOTS[slot_id].file_code, ddmmyy=date.strftime("%d%m%y"))
    pdf = get_bytes(url)
    if not pdf or not pdf.startswith(b"%PDF"):
        print(f"{draw_id}: gazette not available at {url}")
        return None
    with tempfile.TemporaryDirectory() as wd:
        pdf_path = os.path.join(wd, "g.pdf")
        pathlib.Path(pdf_path).write_bytes(pdf)
        subprocess.run(["pdfimages", "-j", pdf_path, os.path.join(wd, "img")], check=True)
        jpgs = sorted(glob.glob(os.path.join(wd, "img-*.jpg")))
        if not jpgs:
            print(f"{draw_id}: no image inside the gazette PDF")
            return None
        out = out_dir / f"{draw_id}.png"
        header_crop(Image.open(jpgs[0])).save(out)
    return out


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 2
    out_dir = pathlib.Path(tempfile.gettempdir()) / "eyecheck"
    out_dir.mkdir(exist_ok=True)
    subprocess.run(["git", "fetch", "-q", "origin", "data"], cwd=ROOT)
    for draw_id in argv:
        d = published(draw_id)
        if d is None:
            print(f"{draw_id}: not on the data branch")
            continue
        first = d["tiers"][0]["numbers"][0] if d.get("tiers") else "?"
        png = gazette_png(draw_id, out_dir)
        print(f"{draw_id}: published 1st prize {first} (officialCheck {json.dumps(d.get('officialCheck'))})"
              + (f"\n  gazette header: {png}" if png else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
