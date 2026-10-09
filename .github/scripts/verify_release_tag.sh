#!/usr/bin/env bash
set -euo pipefail
if [[ -z "${GH_TOKEN:-}" ]] || [[ "$(gh api user --jq .login)" != enwokoma ]]; then
  echo '::error::RELEASE_OWNER_TOKEN must authenticate as enwokoma; bot fallback is disabled.'
  exit 1
fi
if [[ "${GITHUB_REF_TYPE:-}" != tag ]]; then
  echo '::error::Run releases on a signed version tag, not on a branch.'
  exit 1
fi
: "${GITHUB_REF_NAME:?GITHUB_REF_NAME is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
[[ "$GITHUB_REF_NAME" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo '::error::Expected a vMAJOR.MINOR.PATCH tag.'; exit 1; }
test "$(git cat-file -t "$GITHUB_REF_NAME^{}")" = commit
allowed="$RUNNER_TEMP/enwokoma-tag-signers"
# Current gh refuses --slurp together with --jq, so jq does the filtering.
gh api users/enwokoma/ssh_signing_keys --paginate --slurp \
  | jq -r '.[][] | "enwokoma " + .key' > "$allowed"
# Only the existing key is eligible, even if another key is registered later.
expected="enwokoma $(awk '{print $1 " " $2}' "$(git rev-parse --show-toplevel)/.github/release-signing.pub")"
awk '{print $1 " " $2 " " $3}' "$allowed" | grep -Fx -- "$expected" > "$allowed.existing"
mv "$allowed.existing" "$allowed"
git -c gpg.format=ssh -c gpg.ssh.allowedSignersFile="$allowed" verify-tag "$GITHUB_REF_NAME"
# A public key identifies the signer; the tagger must also use the user's commit identity.
test "$(git for-each-ref --format='%(taggeremail:trim)' "refs/tags/$GITHUB_REF_NAME")" = 'emmanuel.nwokoma@payinvert.com'
echo "Verified $GITHUB_REF_NAME was signed by enwokoma."
