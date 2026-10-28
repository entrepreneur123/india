#!/usr/bin/env bash
# Serves the website from this server in India instead of (or as well as) GitHub Pages:
# HTTPS, zstd/gzip compression and caching set for Indian mobile networks, and new notices live
# the moment a run finishes (GitHub Pages takes minutes to rebuild).
# First point a hostname (your domain, e.g. jobs.example.in) at this server, set SITE_HOST in
# /etc/sarkari.env, open ports 80 and 443. Run as root:  sudo bash enable-site.sh
set -euo pipefail
. /etc/sarkari.env
: "${SITE_HOST:?set SITE_HOST in /etc/sarkari.env first}"
# Links in sitemaps, Telegram and WhatsApp posts, and the push server's allowed origin follow the new address.
sed -i "s|^SITE_URL=.*|SITE_URL=https://$SITE_HOST|" /etc/sarkari.env
grep -q '^SITE_ORIGIN=' /etc/sarkari.env && sed -i "s|^SITE_ORIGIN=.*|SITE_ORIGIN=https://$SITE_HOST|" /etc/sarkari.env \
  || echo "SITE_ORIGIN=https://$SITE_HOST" >> /etc/sarkari.env
# Caddy runs as its own user: it may pass through /opt/sarkari (not list it) and read public/ and www/.
# The keys (push/, .ssh/) stay readable by the sarkari user only.
chmod 700 /opt/sarkari/push /opt/sarkari/.ssh
chmod o+x /opt/sarkari
chmod -R o+rX /opt/sarkari/public
bash "$(dirname "$0")/caddy.sh"
chmod -R o+rX /opt/sarkari/www
systemctl try-restart sarkari-push
sleep 5
curl -fsS -o /dev/null "https://$SITE_HOST/jobs/" && echo "site reachable: https://$SITE_HOST/jobs/"
echo "The next run (within 30 minutes) rebuilds sitemaps and links for https://$SITE_HOST."
