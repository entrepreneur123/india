#!/usr/bin/env bash
# One run on the India server (started by sarkari.timer every 30 minutes):
#   update the code, run the unit tests, refresh page copies if their code changed, read the
#   boards only India can reach into jobs/v1/india.json and publish it to the data branch.
# Settings come from /etc/sarkari.env (see setup.sh). Exit 1 means nothing was published.
set -euo pipefail
: "${SARKARI_HOME:=/opt/sarkari}"
: "${CODE_BRANCH:=main}"
: "${SITE_URL:?set SITE_URL in /etc/sarkari.env}"
code="$SARKARI_HOME/code"
public="$SARKARI_HOME/public"
py="$SARKARI_HOME/venv/bin/python"
log() { echo "$(date -u +%FT%TZ) $*"; }

# Every run leaves its log on the "server-log" branch (one commit, replaced each run), so what
# happens here can be read on GitHub without logging in to this machine.
logfile="$SARKARI_HOME/last-run.log"
exec > >(tee "$logfile") 2>&1
report() {
  local rc=$? dir="$SARKARI_HOME/server-log"
  set +e
  sleep 1  # let tee write the last lines
  if [ ! -e "$dir/.git" ]; then git -C "$code" worktree add -q --orphan -b server-log "$dir" >/dev/null 2>&1 || return 0; fi
  { echo "exit $rc at $(date -u +%FT%TZ), code $(git -C "$code" rev-parse --short HEAD 2>/dev/null)"; tail -n 150 "$logfile"; } > "$dir/last-run.txt"
  git -C "$dir" add last-run.txt
  local who=(-c user.name="jobs-bot" -c user.email="jobs-bot@users.noreply.github.com")
  git -C "$dir" "${who[@]}" commit -q --amend -m "server log" >/dev/null 2>&1 || git -C "$dir" "${who[@]}" commit -q -m "server log" >/dev/null 2>&1
  git -C "$dir" push -q -f origin HEAD:server-log >/dev/null 2>&1
  return 0
}
trap report EXIT

# Never two runs at once (a slow PDF download can outlast the 30-minute timer).
exec 9>"$SARKARI_HOME/run.lock"
flock -n 9 || { log "previous run still going"; exit 0; }

git -C "$code" fetch -q origin "$CODE_BRANCH"
git -C "$code" reset -q --hard "origin/$CODE_BRANCH"
log "run start, code $(git -C "$code" rev-parse --short HEAD)"

# A broken commit on the code branch must not publish anything.
(cd "$code/jobs/pipeline" && "$py" -m pytest -q tests > "$SARKARI_HOME/pytest.log" 2>&1) \
  || { tail -n 40 "$SARKARI_HOME/pytest.log"; log "unit tests failed: not publishing"; exit 1; }

# Page copies again as soon as their code changes (new sites, connection fixes), not only on
# Mondays, so a fix reaches the captures branch within 30 minutes with nobody logging in here.
# First, so that nothing failing below can hold it up.
sum=$(cat "$code/jobs/pipeline/sarkari/capture.py" "$code/jobs/pipeline/sarkari/http.py" | sha256sum | cut -d" " -f1)
if [ "$sum" != "$(cat "$SARKARI_HOME/capture.sum" 2>/dev/null)" ]; then
  if "$code/jobs/deploy/india/capture.sh"; then echo "$sum" > "$SARKARI_HOME/capture.sum"; log "captures refreshed"; else log "capture failed"; fi
fi

# This server reads only the boards that answer from India (ssc.gov.in times out here) and writes
# jobs/v1/india.json; GitHub's jobs-publish builds the site and takes those items from it.
# Start from what is published now, so this server never works on a stale copy.
git -C "$public" rebase --abort >/dev/null 2>&1 || true  # a rebase left over from a failed push
if git -C "$public" ls-remote --exit-code --heads origin data >/dev/null 2>&1; then
  git -C "$public" fetch -q origin data
  git -C "$public" reset -q --hard origin/data
fi  # else: the very first run starts the data branch

(cd "$code/jobs/pipeline" && "$py" -m sarkari.india --out "$public")

git -C "$public" add jobs/v1/india.json
if ! git -C "$public" diff --cached --quiet; then
  git -C "$public" -c user.name="jobs-bot" -c user.email="jobs-bot@users.noreply.github.com" \
    commit -q -m "jobs $(date -u +%FT%TZ) (india)"
  pushed=""
  for i in 1 2 3 4 5; do
    if git -C "$public" push -q origin HEAD:data; then pushed=1; break; fi
    # GitHub's jobs-publish and the lottery write the same branch, never india.json: a rebase is clean.
    git -C "$public" fetch -q origin data && git -C "$public" rebase -q origin/data || git -C "$public" rebase --abort >/dev/null 2>&1 || true
    sleep $(( i * 5 ))
  done
  [ -n "$pushed" ] || { log "could not push to data"; exit 1; }
  log "published india.json"
else
  log "no change"
fi

# Instant alerts (only once enable-push.sh has run): from the feed GitHub publishes.
if [ -f "$SARKARI_HOME/push/vapid.pem" ]; then
  (cd "$code/jobs/pipeline" && "$py" -m sarkari.pushsend --out "$public" --db "$SARKARI_HOME/push/subscriptions.db" \
    --vapid "$SARKARI_HOME/push/vapid.pem" --contact "${PUSH_CONTACT:-mailto:admin@example.invalid}") || log "push failed"
fi
