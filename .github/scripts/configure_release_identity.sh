#!/usr/bin/env bash
# Run only from trusted release workflows. Never print the token or private key.
set -euo pipefail

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo '::error::Set RELEASE_OWNER_TOKEN to an enwokoma personal access token. Bot fallback is disabled.'
  exit 1
fi
if [[ "$(gh api user --jq .login)" != enwokoma ]]; then
  echo '::error::RELEASE_OWNER_TOKEN must authenticate as enwokoma.'
  exit 1
fi

git config user.name 'Emmanuel Nwokoma'
git config user.email 'emmanuel.nwokoma@payinvert.com'
# Checkout must use persist-credentials: false, so its bot token cannot override this helper.
git config credential.helper ''
git config --add credential.helper '!gh auth git-credential'

if [[ -z "${RELEASE_SIGNING_KEY:-}" ]]; then
  echo '::error::Set RELEASE_SIGNING_KEY to a dedicated SSH signing key registered to enwokoma.'
  exit 1
fi
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
identity_dir="$RUNNER_TEMP/enwokoma-release-signing"
umask 077
mkdir -p "$identity_dir"
printf '%s\n' "$RELEASE_SIGNING_KEY" > "$identity_dir/key"
# CI uses an unencrypted dedicated key; -P prevents an interactive passphrase prompt.
ssh-keygen -y -P '' -f "$identity_dir/key" > "$identity_dir/key.pub"
gh api users/enwokoma/ssh_signing_keys --paginate --slurp > "$identity_dir/registered-keys.json"
python3 - "$identity_dir" <<'PY'
import json
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
public = directory.joinpath('key.pub').read_text().split()[:2]
pages = json.loads(directory.joinpath('registered-keys.json').read_text())
if not any(key['key'].split()[:2] == public for page in pages for key in page):
    sys.exit('::error::The release signing key is not registered as an SSH signing key on enwokoma.')
PY
printf 'enwokoma %s\n' "$(cat "$identity_dir/key.pub")" > "$identity_dir/allowed-signers"
git config gpg.format ssh
git config user.signingkey "$identity_dir/key"
git config gpg.ssh.allowedSignersFile "$identity_dir/allowed-signers"
git config commit.gpgsign true
git config tag.gpgsign true
echo 'Release identity and signing key verified for enwokoma.'
