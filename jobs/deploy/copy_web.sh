#!/usr/bin/env bash
# Copy the website files listed in web_files.txt into <public>/jobs/.  Usage: copy_web.sh <public dir> [site URL]
# With the site URL, index.html gets its full address for link previews (WhatsApp needs a full image URL).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
dest="$1/jobs"
mkdir -p "$dest"
grep -v '^\s*#' "$here/web_files.txt" | grep -v '^\s*$' | while read -r f; do
  mkdir -p "$(dirname "$dest/$f")"
  cp "$here/../web/$f" "$dest/$f"
done
site="${2:-}"
if [ -n "$site" ]; then
  # Checked first: the address goes into a sed replacement, so no | & \ or quotes may get through.
  [[ "$site" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?(/[A-Za-z0-9._~-]+)*/?$ ]] || { echo "copy_web.sh: bad site URL: $site" >&2; exit 1; }
  site="${site%/}"
  sed -i "s|<!-- share: copy_web.sh -->|<link rel=\"canonical\" href=\"$site/jobs/\"><meta property=\"og:url\" content=\"$site/jobs/\"><meta property=\"og:image\" content=\"$site/jobs/icon-512.png\">|" "$dest/index.html"
fi
touch "$1/.nojekyll"
