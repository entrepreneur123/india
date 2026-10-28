#!/usr/bin/env bash
# Weekly (sarkari-capture.timer): saves pages of recruiters that only answer from India
# (sarkari.capture) to the "captures" branch, for writing their collectors from real pages.
set -euo pipefail
: "${SARKARI_HOME:=/opt/sarkari}"
code="$SARKARI_HOME/code"
dir="$SARKARI_HOME/captures"
if [ ! -d "$dir/.git" ] && [ ! -f "$dir/.git" ]; then
  if git -C "$code" ls-remote --exit-code --heads origin captures >/dev/null 2>&1; then
    git -C "$code" fetch -q origin captures && git -C "$code" worktree add -q "$dir" origin/captures && git -C "$dir" checkout -q -B captures
  else
    git -C "$code" worktree add -q --orphan -b captures "$dir"
  fi
fi
(cd "$code/jobs/pipeline" && "$SARKARI_HOME/venv/bin/python" -m sarkari.capture --out "$dir")
git -C "$dir" add -A
git -C "$dir" diff --cached --quiet && exit 0
git -C "$dir" -c user.name="jobs-bot" -c user.email="jobs-bot@users.noreply.github.com" commit -q -m "captures $(date -u +%F)"
git -C "$dir" push -q origin HEAD:captures
