#!/usr/bin/env python3
"""SessionStart: every session begins knowing the open incidents (owner's incident loop, CLAUDE.md)."""
import json, pathlib, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
try:
    out = subprocess.run([sys.executable, str(ROOT / "tools" / "harness.py"), "incidents"], cwd=ROOT,
                         capture_output=True, text=True, timeout=45).stdout.strip()
except subprocess.TimeoutExpired:
    out = "harness incidents: timed out (ops branch not read)"
if out and "clean" not in out:
    msg = ("Open incidents not yet handled (CLAUDE.md 'Incident loop': root cause, fix, test, watch check, "
           "row in docs/INCIDENTS.md):\n" + out)
else:
    msg = "No unhandled high/medium incidents on the ops branch."
print(json.dumps({"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": msg}}))
