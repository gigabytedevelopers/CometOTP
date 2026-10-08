# Releasing CometOTP

Five workflows live in `.github/workflows`:

| Workflow | File | When it runs |
| --- | --- | --- |
| CI | `ci.yml` | Every pull request, and every push to `master` |
| Request review | `request-review.yml` | Every pull request that is not a draft; asks `@princesseke` to review |
| Auto release | `auto-release.yml` | After CI passes on `master`; signs and pushes a new version tag as `@enwokoma` |
| Release Android | `release-android.yml` | Started by a signed `v*` tag pushed by `@enwokoma`, or dispatched by `@enwokoma` on that tag |
| Release iOS | `release-ios.yml` | Same as Android, but **switched off** until enabled |

## Protecting master

Nobody, including you, should be able to push straight to `master`. Import the ruleset once:

**Settings → Rules → Rulesets → New ruleset → Import a ruleset**, and choose
`.github/rulesets/protect-master.json`. Or from the command line:

```bash
gh api -X POST repos/gigabytedevelopers/CometOTP/rulesets --input .github/rulesets/protect-master.json
```

After changing the file, apply it to the existing ruleset (id 23826211) rather than creating a
second one:

```bash
gh api -X PUT repos/gigabytedevelopers/CometOTP/rulesets/23826211 --input .github/rulesets/protect-master.json
```

It requires a pull request with one approving review from anyone with write access (in practice
`@princesseke`, see below), dismisses stale approvals
when new commits land, requires review threads to be resolved, forbids force pushes and deletions,
keeps history linear, requires signed commits, and blocks the merge until all four CI jobs pass.

Squash is the only merge method allowed, and that is deliberate. A merge commit would break the
linear history rule, and GitHub's rebase merge rewrites each commit and drops its signature, which
the signed-commits rule would then reject. A squash merge is signed by GitHub and stays linear.

## Merging so the commits keep your signature

**Do not use the green merge button on your own pull requests.** None of GitHub's merge methods
puts your signature on master:

| Method | What lands | Signed by |
| --- | --- | --- |
| Merge commit | Your commits, plus a merge commit | You, plus GitHub for the merge commit |
| Squash | One new commit | GitHub's web-flow key |
| Rebase | Rewritten copies of your commits | **Nobody** — the rewrite drops your signature |

Merge locally instead. Your commits land on master exactly as you signed them:

```bash
git checkout <branch> && git rebase master   # replays and re-signs, keeping history linear
git checkout master && git merge --ff-only <branch>
git push origin master
```

GitHub sees the commits arrive and closes the pull request as merged. The push is a direct one, so
it only works because admins are on the ruleset's bypass list; the pull request, its review and its
four passing checks are still how the change got to that point.

For a contributor's pull request, where the commits are signed by them rather than by you, the
green button is fine — squash, and GitHub signs the result.

Repository admins are on the bypass list. Everyone else is fully bound: a fork's pull request
needs an approving review and four passing checks before it can be merged. Admins still work
through pull requests reviewed by Princess (see [Who reviews](#who-reviews)); the bypass is what
lets you push a locally merged branch straight to `master`. Remove the `bypass_actors` entry if you
stop merging locally and want the rules to bind admins too.

### Who reviews

`@princesseke` reviews every pull request. She is not an owner: she is the reviewer because GitHub
never lets the author of a pull request approve it, so the owners cannot approve their own work.
The **Request review** workflow asks her on every pull request that is not a draft, and again on
each new push, since a push dismisses her earlier approval.

The owners, `@gigabytedevelopersinc` and `@enwokoma`, are listed in `.github/CODEOWNERS`. GitHub
asks them for a review too (never the author), but the ruleset does not require a code owner's
approval, only one approving review from someone with write access. That is what lets Princess's
approval count. A pull request Princess opens is not sent to her; either owner approves it.

Owners must be user accounts or `@org/team` names with write access. The organisation
`@gigabytedevelopers` is neither, and while the file named it, no review was ever requested
automatically. GitHub lists any owner it cannot resolve under the file's "Code owners errors" on
the repository page.

If you rename a CI job, update the matching `context` in the ruleset. A required check that no
longer reports leaves pull requests stuck waiting for it forever.

## Secrets

Secrets are never committed. `keystore.properties` and `*.jks` stay in `.gitignore`, and the build
falls back to environment variables when that file is absent, which is how CI supplies them.

Put these on the **`production` environment** (Settings → Environments), not on the repository, so
only the release job can read them, and add yourself as a required reviewer there if you want to
approve each deployment by hand.

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The upload keystore, base64 encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play service account JSON, pasted whole |

Two GitHub identity secrets sit on the repository instead: `RELEASE_OWNER_TOKEN` and
`RELEASE_SIGNING_KEY`. Auto release needs them before the Android deployment begins. See
[Release identity](#release-identity). Android signing secrets above stay on `production`.

To encode the keystore:

```bash
base64 -w0 path/to/release.keystore > keystore.base64
```

Paste the contents of that file, then delete it. The workflow decodes it into the runner's temp
directory, which never enters the workspace or an artifact, and removes it even if the job fails.

Two things keep this out of reach of the public once the repository is open:

- CI runs on `pull_request`, not `pull_request_target`, so a pull request from a fork gets a
  read-only token and **no secrets at all**. Someone editing a workflow in their fork cannot print
  your signing key.
- The deployment secrets sit on an environment, so even a workflow on `master` has to name that
  environment, and any reviewer you add has to release the run first.

## Deploying

A release is a version bump. There is nothing to tag by hand:

1. Merge everything you want in the release.
2. Open a pull request that bumps `app/version.properties` (`VERSION_MAJOR`/`MINOR`/`PATCH`) and
   adds the Play "What's new" text as `fastlane/metadata/android/en-US/changelogs/<version>.txt`
   (see [Play release notes](#play-release-notes)), and merge it.
3. When CI passes on that commit on `master`, **Auto release** sees there is no tag for the new
   version yet, signs the commit's `v<version>` tag as `@enwokoma` and pushes it using your token.
   That tag push starts Release Android and Release iOS once each, with you as the run actor. The
   Android job then waits for the `production` environment's approval, if you set one.

Merges that leave the version alone release nothing. If CI fails on the bump, nothing is tagged,
and the first later commit on `master` that passes CI with that version is released instead. A
version lower than the latest tag is refused.

The version in `version.properties` is the only one anyone sets. The Play versionCode is worked
out from the time of the build (minutes since 2026-01-01 on top of 10,000,000), so every later
build gets a higher code and nothing has to be written back to the repository. See "Versioning" in
`app/build.gradle`.

Auto release publishes to `internal`; promote from the Play Console, or run **Actions → Release
Android → Run workflow** on the tag and choose `production`.

### Release identity

Every future automated tag, GitHub release, release asset upload, and changelog PR uses
`@enwokoma`. Tags and changelog commits carry an SSH signature made with a key registered to
that account, rather than GitHub's web-flow signature. Missing, expired, wrong-account, or
unregistered credentials stop the corresponding operation; there is no bot fallback.

Set these **repository secrets** once, while signed in as `@enwokoma`:

| Secret | Setup |
| --- | --- |
| `RELEASE_OWNER_TOKEN` | A fine-grained personal access token owned by `@enwokoma`, resource owner `gigabytedevelopers`, repository access restricted to `CometOTP`. Grant **Contents: Read and write**, **Actions: Read and write**, **Pull requests: Read and write**, and **Workflows: Read and write**. Approve it in the organisation if required. |
| `RELEASE_SIGNING_KEY` | The complete private half of a dedicated, unencrypted Ed25519 SSH signing key. Register its public half under **enwokoma → Settings → SSH and GPG keys → New SSH key → Signing Key** first. This does not need SSH authentication access. |

Use a dedicated signing key, keeping your existing personal authentication/signing key on your
computer. For example, create the automation key locally with:

```bash
ssh-keygen -t ed25519 -C 'CometOTP release signing (enwokoma)' -f ~/.ssh/cometotp_release_signing -N ''
```

Add the `.pub` file to your GitHub account as a **Signing Key**. Add the private file to the
repository's `RELEASE_SIGNING_KEY` secret; never commit either secret. Configure the token under
**Settings → Secrets and variables → Actions**. Renew the token before expiry. The workflows
verify its account with GitHub and check that the signing key belongs to `@enwokoma` before use.
The private key is restored into the runner's temporary directory and removed when the job ends.

`RELEASE_DISPATCH_TOKEN`, `RELEASE_BOT_PRIVATE_KEY`, and `RELEASE_BOT_CLIENT_ID` are no longer
used. Once the new setup works, their old configuration can be removed.

A tag push authenticated with your token starts the release workflows; Auto release must not
also dispatch them or it would publish twice. Android and enabled iOS deployment jobs run only
when the original workflow actor is `@enwokoma`. A rerun retains the original actor, so rerunning
an old bot-started release does not repair its attribution. Start a new manual run as yourself
on a signed tag instead.

The request-review workflow also uses your token. CI needs no personal token or signing key;
its actor remains whoever performed the real push or PR event. GitHub Actions still provides
and identifies the check runs. Contributions by other people retain their own attribution.

Existing Actions runs and deployment records retain their original actor. A release's author
cannot be edited through the release update API. Published tags and commit history are not
rewritten by this change. Changing an old release author would require recreating the release,
and replacing a published unsigned tag changes its tag object; neither is done automatically.
Releases, tags, deployments, and workflow runs do not themselves add squares to the contribution
graph. Your own commits count there when their email belongs to your account and they reach the
default branch, under GitHub's contribution rules.

### Releasing by hand

Pushing a tag still works, and Auto release then leaves that version alone because the tag
already exists:

```bash
git tag -s v8.0.0 -m "CometOTP 8.0.0"
git push origin v8.0.0
```

The tag has to match `version.properties` exactly (`v` + `MAJOR.MINOR.PATCH`), or the workflow
fails before building anything. Fix the version and use a new matching signed tag; do not
rewrite a published tag as part of a normal release.

If a tag-triggered release fails, re-running Auto release will not help, because the tag now
exists. Check the existing release runs first, then retry the failed release by hand: **Actions → Release Android
→ Run workflow → Use workflow from: Tags → v&lt;version&gt;**.

The job builds a signed App Bundle, uploads it with the ProGuard mapping so crash reports
deobfuscate, attaches the bundle to a GitHub release, and opens a pull request adding the new
section to `CHANGELOG.md`. That last step is a pull request rather than a push because `master` is
protected, and the rules apply to the workflow too.

### Signed changelog pull requests

After publishing, the Android workflow creates a branch from the latest `master`, inserts the
new changelog section, and makes a locally signed commit as `@enwokoma`. It verifies the signature
locally and on GitHub, pushes with your token, then opens the PR as you. The token starts CI
normally; no bot PR or GitHub web-flow signing is used.

Princess or another eligible reviewer must approve the current PR head, all required checks must
pass, and review threads must be resolved. Integrate it using the same signature-preserving
local fast-forward procedure as your other PRs. You cannot approve your own generated PR.
The workflow does not auto-merge it or force-update an existing changelog branch. A bookkeeping
failure after publishing is reported as a warning; it does not undo the Play release.

### Play release notes

Write the "What's new" text for a release in
`fastlane/metadata/android/en-US/changelogs/<version>.txt`, for example `8.2.0.txt`, and include it
in the pull request that bumps the version. Play allows 500 characters; a longer file fails the
release before anything is uploaded.

The file is named after the version so it can only ever describe that version. It used to be a
single `default.txt` that every release picked up, so 8.1.0 shipped to Play with the 8.0.0 text.
The old files stay as a record of what each release said.

Without a file for the version, the release still goes out: the workflow warns, and builds the
text from the generated notes instead, as plain text with New, Fixed and Improved sections, stopping
at the last whole entry that fits and ending with "Plus N more changes." That is serviceable but
reads like a commit log, so write the file for anything users will see.

The workflow copies the file to `whatsnew-en-US` in a temporary directory and points the upload
there, because that is the name `upload-google-play` looks for. Fastlane's `supply` wants
`<versionCode>.txt` instead; using that name here uploads the bundle with no notes at all and says
nothing about it. Name the file after the version and let the workflow do the renaming.

To change the text of a release that is already on Play, edit it in the Play Console. Promoting a
release to another track there also lets you rewrite its notes.

Write the text as one line per paragraph. Play preserves newlines, so a hard-wrapped file shows
its wrapping as line breaks in the middle of sentences.

## Enabling iOS later

The iOS workflow is complete but gated. Every run currently stops at a job that prints a notice and
does nothing, so it cannot fail or publish by accident.

When you have an Apple Developer account and a Kotlin Multiplatform iOS target:

1. Settings → Secrets and variables → Actions → **Variables** → set `IOS_DEPLOY_ENABLED` to `true`.
2. Add these to a **`production-ios`** environment:

   | Secret | What it is |
   | --- | --- |
   | `IOS_CERTIFICATE_BASE64` | Distribution certificate `.p12`, base64 encoded |
   | `IOS_CERTIFICATE_PASSWORD` | Password for that `.p12` |
   | `IOS_PROVISIONING_PROFILE_BASE64` | Provisioning profile, base64 encoded |
   | `IOS_KEYCHAIN_PASSWORD` | Any throwaway password for the temporary keychain |
   | `APPSTORE_KEY_ID` | App Store Connect API key id |
   | `APPSTORE_ISSUER_ID` | App Store Connect issuer id |
   | `APPSTORE_PRIVATE_KEY` | The `.p8` private key, pasted whole |

3. Set `IOS_SCHEME` and `IOS_PROJECT` at the top of the workflow to match the real target, and add
   `iosApp/ExportOptions.plist`.

To switch it off again, set the variable back to `false`. Nothing else needs changing.

## How the changelog is generated

`cliff.toml` drives [git-cliff](https://git-cliff.org). It reads commit subjects since the last
`v*` tag and sorts them into Breaking, New, Improvement and Fix.

Prefix a commit subject to choose the section:

- `feat:` → New
- `fix:` → Fix
- `perf:` `refactor:` `build:` `deps:` → Improvement
- `docs:` `chore:` `test:` `ci:` `style:` → left out
- `feat!:` or a `BREAKING CHANGE` footer → Breaking

A subject with no prefix is filed under Improvement rather than dropped, so nothing disappears
from the changelog just because a commit was written in plain English.
