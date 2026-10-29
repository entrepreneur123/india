#!/usr/bin/env bash
# One run on the India server (started by sarkari.timer every 30 minutes):
#   update the code, run the unit tests, build, publish to the data branch, then send alerts.
# Settings come from /etc/sarkari.env (see setup.sh). Exit 1 means nothing was published.
set -euo pipefail
: "${SARKARI_HOME:=/opt/sarkari}"
: "${CODE_BRANCH:=main}"
: "${SITE_URL:?set SITE_URL in /etc/sarkari.env}"
code="$SARKARI_HOME/code"
public="$SARKARI_HOME/public"
py="$SARKARI_HOME/venv/bin/python"
log() { echo "$(date -u +%FT%TZ) $*"; }

# Never two runs at once (a slow PDF download can outlast the 30-minute timer).
exec 9>"$SARKARI_HOME/run.lock"
flock -n 9 || { log "previous run still going"; exit 0; }

git -C "$code" fetch -q origin "$CODE_BRANCH"
git -C "$code" reset -q --hard "origin/$CODE_BRANCH"

# A broken commit on the code branch must not publish anything.
(cd "$code/jobs/pipeline" && "$py" -m pytest -q tests >/dev/null) || { log "unit tests failed: not publishing"; exit 1; }

# Start from what is published now, so this server never builds on a stale copy.
git -C "$public" rebase --abort >/dev/null 2>&1 || true  # a rebase left over from a failed push
if git -C "$public" ls-remote --exit-code --heads origin data >/dev/null 2>&1; then
  git -C "$public" fetch -q origin data
  git -C "$public" reset -q --hard origin/data
fi  # else: the very first run starts the data branch

(cd "$code/jobs/pipeline" && JOBS_IN_INDIA=1 "$py" -m sarkari.build --out "$public" --site-url "$SITE_URL" ${BUILD_ARGS:-})
"$code/jobs/deploy/copy_web.sh" "$public" "$SITE_URL"

git -C "$public" add -A jobs .nojekyll
if ! git -C "$public" diff --cached --quiet; then
  git -C "$public" -c user.name="jobs-bot" -c user.email="jobs-bot@users.noreply.github.com" \
    commit -q -m "jobs $(date -u +%FT%TZ) (india)"
  pushed=""
  for i in 1 2 3 4 5; do
    if git -C "$public" push -q origin HEAD:data; then pushed=1; break; fi
    # The lottery workflow writes to the same branch (never under jobs/), so a rebase is clean.
    git -C "$public" fetch -q origin data && git -C "$public" rebase -q origin/data || git -C "$public" rebase --abort >/dev/null 2>&1 || true
    sleep $(( i * 5 ))
  done
  [ -n "$pushed" ] || { log "could not push to data"; exit 1; }
  log "published"
else
  log "no change"
fi

# Alerts after the push, so what a notification points to is already on its way to the site.
(cd "$code/jobs/pipeline" && "$py" -m sarkari.telegram --out "$public" --site-url "$SITE_URL/jobs/" \
  --state "$SARKARI_HOME/telegram-state.json") || log "telegram failed"
if [ -f "$SARKARI_HOME/push/vapid.pem" ]; then
  (cd "$code/jobs/pipeline" && "$py" -m sarkari.pushsend --out "$public" --db "$SARKARI_HOME/push/subscriptions.db" \
    --vapid "$SARKARI_HOME/push/vapid.pem" --contact "${PUSH_CONTACT:-mailto:admin@example.invalid}") || log "push failed"
fi

# Page copies again as soon as their code changes (new sites, connection fixes), not only on
# Mondays, so a fix reaches the captures branch within 30 minutes with nobody logging in here.
sum=$(cat "$code/jobs/pipeline/sarkari/capture.py" "$code/jobs/pipeline/sarkari/http.py" | sha256sum | cut -d" " -f1)
if [ "$sum" != "$(cat "$SARKARI_HOME/capture.sum" 2>/dev/null)" ]; then
  if "$code/jobs/deploy/india/capture.sh"; then echo "$sum" > "$SARKARI_HOME/capture.sum"; log "captures refreshed"; else log "capture failed"; fi
fi
