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
