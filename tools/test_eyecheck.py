import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import eyecheck  # noqa: E402


def test_header_crop_keeps_the_first_prize_band_and_scales_it():
    Image = __import__("pytest").importorskip("PIL.Image")
    img = Image.new("L", (1000, 1400), 255)
    img.paste(0, (0, 200, 1000, 300))          # a dark band where the 1st prize sits (14-21% of the page)
    out = eyecheck.header_crop(img, width=500)
    assert out.size == (500, 154)               # 8%..30% of 1400 = 308 rows, halved with the width
    assert out.getpixel((250, 10)) == 255 and out.getpixel((250, 60)) == 0   # the band is inside the crop


def test_published_reads_the_data_branch(monkeypatch):
    class P:
        returncode = 0
        stdout = '{"draws": [{"id": "2026-10-28_NL8PM", "tiers": [{"numbers": ["58C 53977"]}]}]}'
    monkeypatch.setattr(eyecheck.subprocess, "run", lambda *a, **k: P())
    assert eyecheck.published("2026-10-28_NL8PM")["tiers"][0]["numbers"] == ["58C 53977"]
    assert eyecheck.published("2026-10-28_NL1PM") is None
