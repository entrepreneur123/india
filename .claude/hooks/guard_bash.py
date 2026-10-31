#!/usr/bin/env python3
"""PreToolUse(Bash): stop the shell mistakes that already cost this project (docs/INCIDENTS.md).

- `pkill -f` / `killall`: the pattern matches the hook's own shell and kills it (exit 144, twice on 30-10-2026).
- `git push` anywhere but claude/gallant-lovelace-ynoa63, or a force push: data/ops/seo are written by workflows only.
"""
import json, re, sys
cmd = json.load(sys.stdin).get("tool_input", {}).get("command", "")
BRANCH = "claude/gallant-lovelace-ynoa63"
reason = None
# only where a command starts (line start, after ; & | ( or sudo), not in text such as a heredoc that mentions it
if re.search(r"(?m)(?:^|[;&|(]|\$\()\s*(?:sudo\s+)?(?:pkill\s+(?:-\w+\s+)*-\w*f|killall)\b", cmd):
    reason = "pkill -f / killall also kill this shell (its command line matches). Find PIDs with ps and `kill PID`."
for push in re.findall(r"git(?:\s+-C\s+\S+)?\s+push\b([^;&|\n]*)", cmd):
    args = push.split()
    if any(a in ("-f", "--force", "--force-with-lease", "--mirror", "--delete", "-d") or a.startswith("+") for a in args):
        reason = "Force/delete/mirror pushes are not allowed here; ask the owner first."
    refs = [a for a in args if not a.startswith("-") and a not in ("origin", "-u")]
    if any(BRANCH not in r and r not in ("HEAD",) for r in refs):
        reason = f"Push only to {BRANCH} (data, ops and seo are written by the workflows). Refs given: {refs}"
if reason:
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny",
                                             "permissionDecisionReason": reason}}))
