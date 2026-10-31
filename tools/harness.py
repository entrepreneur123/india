"""One harness for every check, used by the Claude Code hooks (.claude/hooks/), the incident loop and by hand.

    python3 tools/harness.py quick FILE...   compile the changed files and run the tests that cover them (seconds)
    python3 tools/harness.py full            every test, the workflows' YAML, a site build from the live data branch
    python3 tools/harness.py live            the watcher's checks against lottodost.com (open problems right now)
    python3 tools/harness.py incidents       open incidents on the ops branch not yet in docs/INCIDENTS.md

Exit status 0 = clean, 1 = something failed (printed), so a hook can block on it.
"""
from __future__ import annotations

import datetime as dt
import json
import pathlib
import py_compile
import re
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PIPE, WEB = ROOT / "pipeline", ROOT / "web"


def run(cmd: list[str], cwd: pathlib.Path = ROOT, timeout: int = 600) -> tuple[bool, str]:
    try:
        p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return False, f"timed out after {timeout}s: {' '.join(cmd)}"
    return p.returncode == 0, (p.stdout + p.stderr)[-3000:]


def tests_for(path: pathlib.Path) -> list[tuple[pathlib.Path, list[str]]]:
    """The test runs that cover a changed file."""
    rel = path.resolve().relative_to(ROOT).as_posix() if path.resolve().is_relative_to(ROOT) else ""
    if rel.startswith("pipeline/lottery_pipeline/"):
        own = PIPE / "tests" / f"test_{path.stem}.py"
        return [(PIPE, ["python3", "-m", "pytest", "-q", "-x", str(own) if own.exists() else "tests"])]
    if rel.startswith("pipeline/tests/"):
        return [(PIPE, ["python3", "-m", "pytest", "-q", "-x", str(path)])]
    if rel.startswith(("tools/", ".claude/hooks/")):
        return [(ROOT, ["python3", "-m", "pytest", "-q", "-x", "tools"])]
    if rel.startswith("web/"):
        return [(WEB, ["python3", "-m", "pytest", "-q", "-x", "test_site.py"])]
    return []


def quick(files: list[str]) -> list[str]:
    problems, runs = [], {}
    for f in files:
        p = pathlib.Path(f)
        if not p.exists():
            continue
        if p.suffix == ".py":
            try:
                py_compile.compile(str(p), doraise=True)
            except py_compile.PyCompileError as e:
                problems.append(f"{p}: does not compile: {e.msg}")
                continue
        if p.suffix in (".yml", ".yaml"):
            ok, out = yaml_ok(p)
            if not ok:
                problems.append(f"{p}: {out}")
        if p.name == "settings.json" or p.suffix == ".json" and ".claude" in p.parts:
            try:
                json.loads(p.read_text())
            except ValueError as e:
                problems.append(f"{p}: invalid JSON: {e}")
        for cwd, cmd in tests_for(p):
            runs[(cwd, tuple(cmd))] = None
    for cwd, cmd in runs:
        ok, out = run(list(cmd), cwd, timeout=300)
        if not ok:
            problems.append(f"tests failed ({' '.join(cmd[3:])} in {cwd.name}):\n{out}")
    return problems


def yaml_ok(p: pathlib.Path) -> tuple[bool, str]:
    try:
        import yaml
    except ImportError:
        return True, "PyYAML missing: not checked"
    try:
        yaml.safe_load(p.read_text())
        return True, ""
    except yaml.YAMLError as e:
        return False, f"invalid YAML: {e}"


def build_smoke() -> list[str]:
    """Build the site from the live data branch and check what users would see."""
    problems = []
    with tempfile.TemporaryDirectory() as tmp:
        data, out = pathlib.Path(tmp, "d"), pathlib.Path(tmp, "o")
        data.mkdir()
        ok, msg = run(["git", "fetch", "-q", "origin", "data"], timeout=120)
        arch = subprocess.run(["git", "archive", "origin/data", "v2"], cwd=ROOT, capture_output=True)
        if not ok or arch.returncode:
            return [f"could not read the data branch: {msg}"]
        subprocess.run(["tar", "-x", "-C", str(data)], input=arch.stdout, check=True)
        ok, msg = run(["python3", str(WEB / "sitegen.py"), "--data", str(data), "--out", str(out)], timeout=600)
        if not ok:
            return [f"site build failed:\n{msg}"]
        pages = list(out.rglob("index.html"))
        if len(pages) < 1000:
            problems.append(f"site build made only {len(pages)} pages")
        for page, rule in (("index.html", r"Lottery Result Today"), ("maharashtra/index.html", r"Maharashtra Lottery Result"),
                           ("kerala/index.html", r"Kerala Lottery Result")):
            f = out / page
            t = re.search(r"<title>(.*?)</title>", f.read_text(), re.S) if f.exists() else None
            if not t or not re.search(rule, t.group(1)):
                problems.append(f"/{page}: title {t.group(1) if t else None!r} lacks {rule!r}")
            elif page != "index.html" and re.search(r"[ऀ-ॿ]", t.group(1)):
                problems.append(f"/{page}: English title has Devanagari: {t.group(1)!r}")
        broken = set()
        for p in pages[:4000]:
            for href in re.findall(r'href="(/[^"#?]*)"', p.read_text(errors="ignore")):
                t = out / href.lstrip("/")
                if href.endswith("/"):
                    t = t / "index.html"
                if not t.exists() and not out.joinpath(href.lstrip("/") + ".html").exists() and href != "/privacy.html":
                    broken.add(href)
        if broken:
            problems.append(f"{len(broken)} broken internal links, e.g. {sorted(broken)[:5]}")
    return problems


def full() -> list[str]:
    problems = []
    for cwd, cmd in ((PIPE, ["python3", "-m", "pytest", "-q"]), (WEB, ["python3", "-m", "pytest", "-q", "test_site.py"]),
                     (ROOT, ["python3", "-m", "pytest", "-q", "tools"])):
        ok, out = run(cmd, cwd, timeout=900)
        if not ok:
            problems.append(f"{cwd.name} tests failed:\n{out}")
    for wf in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
        ok, out = yaml_ok(wf)
        if not ok:
            problems.append(f"{wf.name}: {out}")
    return problems + build_smoke()


def live() -> list[str]:
    sys.path.insert(0, str(PIPE))
    from lottery_pipeline import watch
    found = watch.checks("https://lottodost.com", dt.datetime.now(dt.timezone.utc), mirror="https://entrepreneur123.github.io/india")
    return [f"{k} [{v['severity']}] {v['detail']}" for k, v in found.items()]


def incidents(severities=("high", "medium")) -> list[str]:
    """Open incidents on the ops branch that docs/INCIDENTS.md does not mention yet."""
    run(["git", "fetch", "-q", "origin", "ops"], timeout=30)
    p = subprocess.run(["git", "show", "origin/ops:ops/incidents.json"], cwd=ROOT, capture_output=True, text=True)
    if p.returncode:
        return []
    state = json.loads(p.stdout)
    known = (ROOT / "docs" / "INCIDENTS.md").read_text() if (ROOT / "docs" / "INCIDENTS.md").exists() else ""
    return [f"{k} [{v['severity']}, since {v['first_seen']}] {v['detail']}" for k, v in state.get("open", {}).items()
            if v.get("severity") in severities and k not in known]


def main(argv: list[str]) -> int:
    what = argv[0] if argv else "full"
    problems = {"quick": lambda: quick(argv[1:]), "full": full, "live": live, "incidents": incidents}[what]()
    for p in problems:
        print(p)
    if not problems:
        print(f"harness {what}: clean")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
