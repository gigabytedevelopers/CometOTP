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
git config --replace-all credential.helper ''
git config --add credential.helper '!gh auth git-credential'

: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
identity_dir="$RUNNER_TEMP/enwokoma-release-signing"
public_key="$(git rev-parse --show-toplevel)/.github/release-signing.pub"
umask 077
mkdir -p "$identity_dir"
gh api users/enwokoma/ssh_signing_keys --paginate --slurp > "$identity_dir/registered-keys.json"
python3 - "$identity_dir" "$public_key" <<'PY'
import json
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
public = pathlib.Path(sys.argv[2]).read_text().split()[:2]
pages = json.loads(directory.joinpath('registered-keys.json').read_text())
if not any(key['key'].split()[:2] == public for page in pages for key in page):
    sys.exit('::error::The release signing key is not registered as an SSH signing key on enwokoma.')
PY
identity_ready=false
cleanup_script="$(git rev-parse --show-toplevel)/.github/scripts/cleanup_release_identity.sh"
trap 'if [[ "$identity_ready" != true ]]; then bash "$cleanup_script"; fi' EXIT
if [[ -n "${RELEASE_SIGNING_KEY:-}" ]]; then
  # Reuse the existing key, including its passphrase protection. Never generate a key.
  printf '%s\n' "$RELEASE_SIGNING_KEY" > "$identity_dir/key"
  agent_environment=$(ssh-agent -s)
  eval "$agent_environment" >/dev/null
  printf '%s\n' "$SSH_AGENT_PID" > "$identity_dir/agent.pid"
  printf '%s\n' "$SSH_AUTH_SOCK" > "$identity_dir/agent.socket"
  cat > "$identity_dir/askpass" <<'ASKPASS'
#!/usr/bin/env bash
# Fail after one attempt instead of repeatedly supplying an incorrect passphrase.
asked="${0%/*}/passphrase-requested"
[[ ! -e "$asked" ]] || exit 1
: > "$asked"
printf '%s\n' "${RELEASE_SIGNING_PASSPHRASE:-}"
ASKPASS
  chmod 700 "$identity_dir/askpass"
  SSH_ASKPASS_REQUIRE=force SSH_ASKPASS="$identity_dir/askpass" DISPLAY=release-signing \
    ssh-add "$identity_dir/key" </dev/null
  rm -f "$identity_dir/key" "$identity_dir/askpass" "$identity_dir/passphrase-requested"
fi
if ! ssh-add -T "$public_key"; then
  echo '::error::The existing enwokoma signing key must be loaded in SSH agent. Hosted runners need RELEASE_SIGNING_KEY and its passphrase; no replacement key is generated.'
  exit 1
fi
if [[ -f "$identity_dir/agent.pid" && -n "${GITHUB_ENV:-}" ]]; then
  printf 'SSH_AUTH_SOCK=%s\nSSH_AGENT_PID=%s\n' "$SSH_AUTH_SOCK" "$SSH_AGENT_PID" >> "$GITHUB_ENV"
fi
printf 'enwokoma %s\n' "$(cat "$public_key")" > "$identity_dir/allowed-signers"
git config gpg.format ssh
git config user.signingkey "$public_key"
git config gpg.ssh.allowedSignersFile "$identity_dir/allowed-signers"
git config commit.gpgsign true
git config tag.gpgsign true
identity_ready=true
echo 'Release identity and signing key verified for enwokoma.'
