#!/usr/bin/env python3
"""PostToolUse(Edit|Write): compile the file and run the tests that cover it; a failure goes straight back to Claude."""
import json, pathlib, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
data = json.load(sys.stdin)
f = (data.get("tool_input") or {}).get("file_path") or (data.get("tool_response") or {}).get("filePath") or ""
p = pathlib.Path(f)
if not f or not p.resolve().is_relative_to(ROOT) or p.suffix not in (".py", ".yml", ".yaml", ".json"):
    sys.exit(0)
r = subprocess.run([sys.executable, str(ROOT / "tools" / "harness.py"), "quick", str(p)], cwd=ROOT,
                   capture_output=True, text=True, timeout=400)
if r.returncode:
    print(json.dumps({"decision": "block", "reason": f"harness quick failed after editing {p.name}:\n{r.stdout[-2500:]}"}))
