#!/usr/bin/env bash
set -euo pipefail
: "${VERSION:?VERSION is required}"
: "${VERSION_CODE:?VERSION_CODE is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid version'; exit 1; }

branch="chore/changelog-$VERSION"
git fetch origin master
git checkout -b "$branch" origin/master
python3 .github/scripts/insert_changelog.py CHANGELOG.md NEW_SECTION.md
git add -- CHANGELOG.md
if git diff --cached --quiet; then
  echo 'The changelog entry already exists; no commit or pull request is needed.'
  exit 0
fi
git commit -S -m "chore: Add the $VERSION changelog entry"
git verify-commit HEAD
sha=$(git rev-parse HEAD)
# Refuse to replace an existing branch: a retry must not discard reviewed or signed work.
git push origin "HEAD:refs/heads/$branch"
verification=$(gh api "repos/$GITHUB_REPOSITORY/commits/$sha" \
  --jq '.commit.verification.verified and .author.login == "enwokoma" and .committer.login == "enwokoma"')
[[ "$verification" == true ]] || { echo '::error::GitHub did not verify the changelog commit as enwokoma.'; exit 1; }

body="$RUNNER_TEMP/changelog-pr-body.md"
printf 'Adds the generated changelog for CometOTP %s (versionCode %s).\n\nSigned by enwokoma. Required review and CI checks still apply.\n' "$VERSION" "$VERSION_CODE" > "$body"
existing=$(gh pr list --repo "$GITHUB_REPOSITORY" --head "$branch" --base master --state open --json url --jq '.[0].url // empty')
if [[ -n "$existing" ]]; then
  echo "$existing"
else
  gh pr create --repo "$GITHUB_REPOSITORY" --head "$branch" --base master \
    --title "chore: Add the $VERSION changelog entry" --body-file "$body"
fi
