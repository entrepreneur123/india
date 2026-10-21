#!/usr/bin/env bash
# Copy the website files listed in web_files.txt into <public>/jobs/.  Usage: copy_web.sh <public dir>
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
dest="$1/jobs"
mkdir -p "$dest"
grep -v '^\s*#' "$here/web_files.txt" | grep -v '^\s*$' | while read -r f; do
  cp "$here/../web/$f" "$dest/$f"
done
touch "$1/.nojekyll"
