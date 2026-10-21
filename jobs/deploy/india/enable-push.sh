#!/usr/bin/env bash
# Switches on instant push. First point a hostname at this server and set PUSH_HOST in /etc/sarkari.env
# (README step 5). Run as root:  sudo bash enable-push.sh
set -euo pipefail
. /etc/sarkari.env
: "${PUSH_HOST:?set PUSH_HOST in /etc/sarkari.env first}"
home=/opt/sarkari
key=$(sudo -H -u sarkari "$home/venv/bin/python" -c "import sys; sys.path.insert(0, '$home/code/jobs/pipeline'); from sarkari.pushsend import init_vapid; print(init_vapid('$home/push/vapid.pem'))")
origin=$(python3 -c "import urllib.parse,sys; u=urllib.parse.urlsplit(sys.argv[1]); print(f'{u.scheme}://{u.netloc}')" "$SITE_URL")
sed -i "s|^JOBS_PUSH_URL=.*|JOBS_PUSH_URL=https://$PUSH_HOST/push|; s|^JOBS_PUSH_KEY=.*|JOBS_PUSH_KEY=$key|" /etc/sarkari.env
grep -q '^SITE_ORIGIN=' /etc/sarkari.env && sed -i "s|^SITE_ORIGIN=.*|SITE_ORIGIN=$origin|" /etc/sarkari.env || echo "SITE_ORIGIN=$origin" >> /etc/sarkari.env
# Caddy gets the HTTPS certificate for PUSH_HOST by itself (ports 80 and 443 must be open).
bash "$(dirname "$0")/caddy.sh"
systemctl enable --now sarkari-push
sleep 5
curl -fsS "https://$PUSH_HOST/push/health" && echo " <- push server reachable over HTTPS"
echo "Push is announced to the app with the next collection run (site.json)."
