import os
import re
import subprocess

JOBS = os.path.join(os.path.dirname(__file__), "..", "..")
DEPLOY = os.path.join(JOBS, "deploy")


def test_every_website_file_is_published():
    listed = {l.strip() for l in open(os.path.join(DEPLOY, "web_files.txt")) if l.strip() and not l.startswith("#")}
    root = os.path.join(JOBS, "web")
    web = {os.path.relpath(os.path.join(d, f), root).replace(os.sep, "/") for d, dirs, files in os.walk(root) for f in files
           if "node_modules" not in d and not f.endswith(".test.mjs") and f != "package.json"}
    assert listed == web, f"missing from web_files.txt: {web - listed}; listed but absent: {listed - web}"


def test_scripts_parse_and_units_point_at_real_scripts():
    india = os.path.join(DEPLOY, "india")
    for sh in ("run.sh", "setup.sh", "enable-push.sh", "enable-site.sh", "caddy.sh", "capture.sh", os.path.join("..", "copy_web.sh")):
        subprocess.run(["bash", "-n", os.path.join(india, sh)], check=True)
        assert os.access(os.path.join(india, sh), os.X_OK), f"{sh} is not executable"
    for unit in ("sarkari.service", "sarkari-capture.service"):
        start = re.search(r"ExecStart=/opt/sarkari/code/(\S+)", open(os.path.join(india, unit)).read()).group(1)
        assert os.path.exists(os.path.join(JOBS, "..", start)), start


def test_copy_web_adds_the_full_address_for_link_previews(tmp_path):
    script = os.path.join(DEPLOY, "copy_web.sh")
    subprocess.run(["bash", script, str(tmp_path / "a"), "https://example.in/india/"], check=True)
    page = (tmp_path / "a" / "jobs" / "index.html").read_text(encoding="utf-8")
    assert '<meta property="og:image" content="https://example.in/india/jobs/icon-512.png">' in page
    assert '<link rel="canonical" href="https://example.in/india/jobs/">' in page and "share: copy_web.sh" not in page
    subprocess.run(["bash", script, str(tmp_path / "b")], check=True)  # no address: the marker stays, nothing breaks
    assert "og:url" not in (tmp_path / "b" / "jobs" / "index.html").read_text(encoding="utf-8")
    for bad in ("https://evil.example/a|b", "https://x.in/&", "http://example.in", 'https://x.in/"><script>'):
        assert subprocess.run(["bash", script, str(tmp_path / "c"), bad], capture_output=True).returncode == 1, bad
