#!/usr/bin/env bash
# Sets up the India server on a fresh Ubuntu 24.04 VM (any provider, an India region).
# (24.04 because its own package list has Caddy 2.6, used for HTTPS in front of the push server.)
# Run as root:  sudo bash setup.sh <github-user>/<repo> [code branch]
# It prints the two things you add on GitHub afterwards (a deploy key and, for push, a variable).
set -euo pipefail
repo="${1:?usage: setup.sh <owner>/<repo> [branch]}"
branch="${2:-main}"
home=/opt/sarkari

apt-get update -qq
grep -q 'VERSION_ID="24.04"' /etc/os-release || { echo "needs Ubuntu 24.04"; exit 1; }
apt-get install -y -qq git python3 python3-venv poppler-utils caddy curl
systemctl disable --now caddy  # only used once push is switched on (enable-push.sh)

id sarkari >/dev/null 2>&1 || useradd --system --home "$home" --shell /usr/sbin/nologin sarkari
mkdir -p "$home/push" "$home/.ssh"
python3 -m venv "$home/venv"
"$home/venv/bin/pip" install -q pytest==9.1.1 pywebpush==2.0.3

# A deploy key that can push to this one repository (data branch). Add the public half on GitHub.
[ -f "$home/.ssh/id_ed25519" ] || ssh-keygen -q -t ed25519 -N "" -C "sarkari-india" -f "$home/.ssh/id_ed25519"
ssh-keyscan -t ed25519 github.com > "$home/.ssh/known_hosts" 2>/dev/null
cat > "$home/.ssh/config" <<EOF
Host github.com
  IdentityFile $home/.ssh/id_ed25519
  IdentitiesOnly yes
EOF

[ -f /etc/sarkari.env ] || cat > /etc/sarkari.env <<EOF
SARKARI_HOME=$home
CODE_BRANCH=$branch
SITE_URL=https://${repo%%/*}.github.io/${repo##*/}
# Optional: Telegram channel (bot must be an admin of it)
JOBS_TELEGRAM_BOT_TOKEN=
JOBS_TELEGRAM_CHAT_ID=
# Optional: WhatsApp Channel invite link (https://whatsapp.com/channel/...), shown in the app
JOBS_WHATSAPP_CHANNEL=
# Optional: instant push. PUSH_HOST is a hostname that points at this server (see README).
PUSH_HOST=
PUSH_CONTACT=mailto:you@example.com
JOBS_PUSH_URL=
JOBS_PUSH_KEY=
EOF
chmod 600 /etc/sarkari.env

chown -R sarkari:sarkari "$home"
chmod 700 "$home/push" "$home/.ssh"  # VAPID key, subscriptions, deploy key
echo "== Add this deploy key on GitHub (repo → Settings → Deploy keys → Add, tick 'Allow write access'):"
cat "$home/.ssh/id_ed25519.pub"
read -r -p "Press Enter once the key is added... " _

sudo -H -u sarkari git clone -q --branch "$branch" "git@github.com:$repo.git" "$home/code"
if sudo -H -u sarkari git -C "$home/code" ls-remote --exit-code --heads origin data >/dev/null; then
  sudo -H -u sarkari git -C "$home/code" worktree add -q "$home/public" origin/data
  sudo -H -u sarkari git -C "$home/public" checkout -q -B data
else
  sudo -H -u sarkari git -C "$home/code" worktree add -q --orphan -b data "$home/public"
fi

install -m 644 "$home/code/jobs/deploy/india/sarkari.service" /etc/systemd/system/
install -m 644 "$home/code/jobs/deploy/india/sarkari.timer" /etc/systemd/system/
install -m 644 "$home/code/jobs/deploy/india/sarkari-push.service" /etc/systemd/system/
install -m 644 "$home/code/jobs/deploy/india/sarkari-capture.service" /etc/systemd/system/
install -m 644 "$home/code/jobs/deploy/india/sarkari-capture.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now sarkari.timer sarkari-capture.timer
systemctl start --no-block sarkari-capture.service  # first copies now, not next Monday

echo "== Collection runs every 30 minutes now:  systemctl list-timers sarkari.timer ; journalctl -u sarkari -f"
echo "== Next: README.md step 4 (switch GitHub to this server), step 5 (optional: instant push)."
