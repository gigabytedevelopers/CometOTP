#!/usr/bin/env bash
# Puts the changelog on the CometOTP changelog page, gigabytedevelopers.com/apps/cometotp/changelog,
# whose page reads log/CHANGELOG.md. Builds that file from CHANGELOG.md and the releases before
# 8.0.0 (build_site_changelog.py), uploads it with the folder's .htaccess over FTP with TLS to an
# FTP account limited to that folder, then checks the site serves exactly this file.
#
# Without the two credentials it warns and does nothing, so the workflow stays green until they are
# set up (see "Website changelog" in .github/RELEASING.md). The password never reaches a command
# line: curl reads it from a config file only this user can read.
set -euo pipefail

: "${WEBSITE_FTP_HOST:?WEBSITE_FTP_HOST is required}"
SITE_URL=${SITE_URL:-https://gigabytedevelopers.com/apps/cometotp/changelog/log}
CHANGELOG=${CHANGELOG:-CHANGELOG.md}
HISTORY=${HISTORY:-.github/website/changelog-before-8.0.0.md}
HTACCESS=${HTACCESS:-.github/website/changelog-log.htaccess}
CHECK_ATTEMPTS=${CHECK_ATTEMPTS:-5}
CHECK_WAIT=${CHECK_WAIT:-10}

if [[ -z "${WEBSITE_FTP_USER:-}" && -z "${WEBSITE_FTP_PASSWORD:-}" ]]; then
  echo '::warning::WEBSITE_FTP_USER and WEBSITE_FTP_PASSWORD are not set, so the website changelog was not updated. See "Website changelog" in .github/RELEASING.md.'
  exit 0
fi
if [[ -z "${WEBSITE_FTP_USER:-}" || -z "${WEBSITE_FTP_PASSWORD:-}" ]]; then
  echo '::error::Set both WEBSITE_FTP_USER and WEBSITE_FTP_PASSWORD, or neither.'
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
chmod 700 "$work"
config="$work/curl.conf"
# curl's config syntax: a quoted value, with backslashes and quotes escaped.
escape() { local s=${1//\\/\\\\}; printf '%s' "${s//\"/\\\"}"; }
( umask 077; printf 'user = "%s:%s"\n' "$(escape "$WEBSITE_FTP_USER")" "$(escape "$WEBSITE_FTP_PASSWORD")" > "$config" )

page="$work/CHANGELOG.md"
python3 "$(dirname "$0")/build_site_changelog.py" "$CHANGELOG" "$HISTORY" "$page"

upload() {
  # --ssl-reqd: never fall back to plain FTP. The certificate is checked against the host name.
  curl --config "$config" --fail --silent --show-error --ssl-reqd \
    --retry 3 --retry-delay 5 --retry-all-errors \
    -T "$1" "ftp://$WEBSITE_FTP_HOST/$2"
}

upload "$HTACCESS" .htaccess
upload "$page" CHANGELOG.md
echo "Uploaded the changelog to $WEBSITE_FTP_HOST."

# The site may take a moment to serve the new file. The query string keeps any cache out of it.
expected=$(sha256sum "$page" | cut -d' ' -f1)
url="$SITE_URL/CHANGELOG.md?deploy=${GITHUB_SHA:-$expected}"
for attempt in $(seq 1 "$CHECK_ATTEMPTS"); do
  if curl --fail --silent --show-error -D "$work/headers" -o "$work/served" "$url" &&
      [[ "$(sha256sum "$work/served" | cut -d' ' -f1)" == "$expected" ]]; then
    echo "The site serves the uploaded changelog: $SITE_URL/CHANGELOG.md"
    if grep -qiE '^cache-control:.*max-age=[0-9]{5,}' "$work/headers"; then
      echo '::warning::The site still tells browsers to cache the changelog for a long time, so returning visitors may see an old copy. Check that the uploaded .htaccess is in effect.'
    fi
    exit 0
  fi
  [[ "$attempt" -lt "$CHECK_ATTEMPTS" ]] && sleep "$CHECK_WAIT"
done
echo "::error::$SITE_URL/CHANGELOG.md does not match the uploaded file. Check that the FTP account's folder is public_html/apps/cometotp/changelog/log, and that the web server can read it (folders 755, files 644)."
exit 1
