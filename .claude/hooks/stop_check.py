#!/usr/bin/env python3
"""Stop: do not finish while the work is unsafe or unfinished.

1. uncommitted or unpushed changes;
2. code changed since the last verified commit and `harness.py full` fails (tests, workflows, a real site build);
3. high/medium incidents on the ops branch that docs/INCIDENTS.md does not cover yet.
On the second stop in a row (stop_hook_active) it lets go, so it can never trap the session.
"""
import json, pathlib, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
BRANCH = "claude/gallant-lovelace-ynoa63"
if json.load(sys.stdin).get("stop_hook_active"):
    sys.exit(0)


def git(*a):
    return subprocess.run(["git", *a], cwd=ROOT, capture_output=True, text=True).stdout.strip()


problems = []
if git("status", "--porcelain"):
    problems.append("uncommitted changes:\n" + git("status", "--short")[:800])
if git("rev-parse", "--abbrev-ref", "HEAD") == BRANCH:
    ahead = git("rev-list", "--count", f"origin/{BRANCH}..HEAD")
    if ahead not in ("", "0"):
        problems.append(f"{ahead} commit(s) not pushed to origin/{BRANCH}")
stamp = ROOT / ".git" / "harness-verified"
head = git("rev-parse", "HEAD")
last = stamp.read_text().strip() if stamp.exists() else ""
code_changed = not last or bool(git("diff", "--name-only", last, head, "--", "pipeline", "web", ".github", "tools", ".claude"))
if code_changed and not problems:
    r = subprocess.run([sys.executable, str(ROOT / "tools" / "harness.py"), "full"], cwd=ROOT,
                       capture_output=True, text=True, timeout=1500)
    if r.returncode:
        problems.append("harness full failed:\n" + r.stdout[-2500:])
    else:
        stamp.write_text(head)
r = subprocess.run([sys.executable, str(ROOT / "tools" / "harness.py"), "incidents"], cwd=ROOT,
                   capture_output=True, text=True, timeout=60)
if r.returncode:
    problems.append("unhandled incidents (CLAUDE.md 'Incident loop'):\n" + r.stdout[-1500:])
if problems:
    print(json.dumps({"decision": "block", "reason": "Before finishing:\n- " + "\n- ".join(problems)}))
