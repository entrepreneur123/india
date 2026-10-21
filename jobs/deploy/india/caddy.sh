#!/usr/bin/env bash
# Writes /etc/caddy/Caddyfile from /etc/sarkari.env and reloads Caddy. Called by enable-site.sh and
# enable-push.sh; safe to run again. Run as root.
#   SITE_HOST  the website itself, served from this server in India (enable-site.sh)
#   PUSH_HOST  the push server (enable-push.sh); may be the same name as SITE_HOST
# CADDYFILE=<path> writes there and skips the reload (used to check the output with `caddy validate`).
set -euo pipefail
ENV_FILE="${ENV_FILE:-/etc/sarkari.env}"
. "$ENV_FILE"
home="${SARKARI_HOME:-/opt/sarkari}"
out="${CADDYFILE:-/etc/caddy/Caddyfile}"
site="${SITE_HOST:-}"
push="${PUSH_HOST:-}"
[ -n "$site$push" ] || { echo "set SITE_HOST and/or PUSH_HOST in $ENV_FILE"; exit 1; }
push_src=""
[ -n "$push" ] && [ "$push" != "$site" ] && push_src="https://$push"

if [ -n "$site" ]; then
  mkdir -p "$home/www"
  printf 'User-agent: *\nAllow: /\nSitemap: https://%s/jobs/sitemap.xml\n' "$site" > "$home/www/robots.txt"
fi

proxy_push='	handle /push/* {
		reverse_proxy 127.0.0.1:8787
	}'
{
  if [ -n "$site" ]; then
    cat <<CADDY
$site {
	encode zstd gzip
	header {
		Strict-Transport-Security "max-age=31536000"
		X-Content-Type-Options "nosniff"
		Referrer-Policy "strict-origin-when-cross-origin"
		Permissions-Policy "camera=(), microphone=(), geolocation=()"
		Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' $push_src; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
		-Server
	}
	# Short caches: new notices must show within minutes; stale copies still serve on a bad network.
	@data path *.json *.txt *.xml
	header @data Cache-Control "public, max-age=120, stale-while-revalidate=86400"
	@code {
		path *.js *.html *.webmanifest */
		not path /jobs/sw.js
	}
	header @code Cache-Control "public, max-age=300, stale-while-revalidate=86400"
	@images path *.png *.svg
	header @images Cache-Control "public, max-age=604800"
	header /jobs/sw.js Cache-Control "no-cache"
$( [ -n "$push" ] && [ "$push" = "$site" ] && echo "$proxy_push" )
	handle /robots.txt {
		root * $home/www
		file_server
	}
	@hidden path /.git /.git/* /captures/*
	handle @hidden {
		respond 404
	}
	redir / /jobs/ 302
	handle {
		root * $home/public
		file_server
	}
}
CADDY
  fi
  if [ -n "$push" ] && [ "$push" != "$site" ]; then
    cat <<CADDY
$push {
$proxy_push
	respond 404
}
CADDY
  fi
} > "$out"
if [ -z "${CADDYFILE:-}" ]; then
  caddy validate --config "$out" --adapter caddyfile >/dev/null
  systemctl enable caddy >/dev/null 2>&1
  systemctl reload caddy 2>/dev/null || systemctl restart caddy
fi
echo "Caddy: ${site:+site https://$site }${push:+push https://$push}"
