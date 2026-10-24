# Releasing CometOTP

Seven workflows live in `.github/workflows`:

| Workflow | File | When it runs |
| --- | --- | --- |
| CI | `ci.yml` | Every pull request, and every push to `master` |
| Repository security | `repository-security.yml` | Every pull request and push; rejects editor autorun, disguised payloads and committed credentials |
| Auto merge | `auto-merge.yml` | After CI, Repository security or a review; fast-forwards `master` to an owner's approved, green pull request |
| Review submitted | `review-submitted.yml` | Every submitted review; does nothing but start Auto merge |
| Auto release | `auto-release.yml` | After CI passes on `master`; signs and pushes a new version tag as `@enwokoma`, then calls the two release workflows |
| Release Android | `release-android.yml` | Called by Auto release to publish that tag directly to Play production, or run by hand from `master` with a tag |
| Release iOS | `release-ios.yml` | Called by Auto release to submit that tag to App Review, but **switched off** until an iOS target and credentials are available |

Nothing in this list waits for a click. The people in the loop are the reviewer, whose approval
of a pull request's latest push is what lets it reach `master`, and the stores' own reviews.

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
keeps history linear, requires signed commits, and blocks the merge until all five required checks
pass: the four CI jobs and **Repository security**. Nobody is on its bypass list, admins included.

Squash and rebase are the allowed merge methods. A merge commit would break the linear history
rule. Rebase is allowed only because GitHub counts a locally fast-forwarded push (see below) as a
rebase merge: with squash alone it refuses that push with "merges must occur on the web". The web's
**Rebase and merge** button still cannot be used, because it rewrites each commit and drops its
signature, which the signed-commits rule rejects. A squash merge on the web is signed by GitHub and
stays linear.

## Merging so the commits keep your signature

**Do not use the green merge button on your own pull requests.** None of GitHub's merge methods
puts your signature on master:

| Method | What lands | Signed by |
| --- | --- | --- |
| Merge commit | Your commits, plus a merge commit | You, plus GitHub for the merge commit |
| Squash | One new commit | GitHub's web-flow key |
| Rebase | Rewritten copies of your commits | **Nobody** — the rewrite drops your signature |

Fast-forward `master` to the pull request's head instead, so your commits land on master exactly
as you signed them. [Auto merge](#auto-merge) does that for you once a pull request is ready; by
hand it is:

```bash
git checkout <branch> && git rebase master   # only if master has moved; replays and re-signs
git checkout master && git merge --ff-only <branch>
git push origin master
```

GitHub sees the commits arrive and closes the pull request as merged. The ruleset accepts this push
without any bypass because the commit you push is the head of a pull request into `master` that is
approved and has passed its checks. Anything else is rejected: a commit that is not that head, or
the head of a pull request that targets another branch. A rebase that changes the commits gives the
pull request a new head, which needs its checks and a fresh approval before the push goes through.

For a contributor's pull request, where the commits are signed by them rather than by you, the
green button is fine — squash, and GitHub signs the result.

Everyone is fully bound, admins and forks alike: nothing reaches `master` without an approving
review of its latest push and five passing checks (see [Who reviews](#who-reviews)).

Stacked pull requests, where one targets the branch of another, cannot be merged this way: a pull
request into another branch never satisfies `master`'s rule. Once the lower one has merged, open a
pull request of the upper one's head into `master` and get that approved. Retargeting the upper
pull request to `master` works too, but the base change dismisses its approval.

Two pull requests opened from the same `master` cannot both be fast-forwarded either: once one
lands, the other is behind. Rebase the second onto the first's head before asking for reviews.
Landing the first then moves the second's base, which dismisses its approval, so it needs one more
approval after the first is in. GitHub can also go on recording the old base for a while and
dismiss that approval too ("The merge-base changed after approval"). When the pull request's
`base.sha` is not the current `master`, point it at another branch with the same tip and back
before asking again; that makes GitHub record the base afresh:

```bash
gh api repos/gigabytedevelopers/CometOTP/pulls/<number> --jq .base.sha
gh api -X PATCH repos/gigabytedevelopers/CometOTP/pulls/<number> -f base=<branch at master's tip>
gh api -X PATCH repos/gigabytedevelopers/CometOTP/pulls/<number> -f base=master
```

### Auto merge

`auto-merge.yml` makes the same fast-forward push for you, as `@enwokoma`, so `master`'s CI and
Auto release start exactly as they do after a push from your machine. It looks for the oldest open
pull request into `master` that:

- was opened by an owner (`@enwokoma` or `@gigabytedevelopersinc`) from a branch of this
  repository, not a fork, and is not a draft;
- GitHub calls ready: approved on its latest push, every required check green, up to date with
  `master` (its merge state is `CLEAN`);
- has no `hold` label. Add one to keep an approved pull request out of `master` until you take it
  off.

It runs whenever CI or Repository security finishes and whenever a review is submitted, and can
be started by hand from the Actions tab. It runs `master`'s copy of the workflow, never a pull
request's, and reads nothing from the pull request but the commit id it pushes, which it checks is
still the approved head and a fast-forward of `master`. The ruleset checks the same again when the
push arrives.

It merges one pull request at a time. It cannot rebase, because rebasing makes new commits that
only your key can sign, so a pull request that has fallen behind `master` waits for a rebase from
your machine and a fresh approval. Contributors' pull requests are left for the green squash
button, as above.

### Who reviews

`@princesseke` reviews every pull request. She is the reviewer because GitHub never lets the author
of a pull request approve it, so the owners cannot approve their own work.

She is listed in `.github/CODEOWNERS` with the owners, `@gigabytedevelopersinc` and `@enwokoma`, so
GitHub asks all three (never the author) when a pull request is opened. GitHub makes those requests
itself, so they show as coming from the pull request's author; no workflow or token is involved,
and nothing appears as `github-actions`. The ruleset does not require a code owner's approval, only
one approving review from someone with write access, which is what lets Princess's approval count.
A pull request Princess opens is not sent to her; either owner approves it.

A new push dismisses her approval but does not ask her again. Ask her yourself, so the request is
yours too:

```bash
gh pr edit <number> --add-reviewer princesseke
```

Owners must be user accounts or `@org/team` names with write access. The organisation
`@gigabytedevelopers` is neither, and while the file named it, no review was ever requested
automatically. GitHub lists any owner it cannot resolve under the file's "Code owners errors" on
the repository page.

If you rename a CI job, update the matching `context` in the ruleset. A required check that no
longer reports leaves pull requests stuck waiting for it forever.

## Secrets

Secrets are never committed. `keystore.properties` and `*.jks` stay in `.gitignore`, and the build
falls back to environment variables when that file is absent, which is how CI supplies them.

Put these on the **`production` environment** (Settings → Environments), not on the repository.
The environment's **Deployment branches and tags** setting allows `master` only, so the only jobs
that can read them are the ones running `master`'s workflows, which are the reviewed ones. It has
no required reviewers: an approved, green pull request reaching `master` is the approval.

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The upload keystore, base64 encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play service account JSON, pasted whole |

The GitHub identity secrets sit on `production` too: `RELEASE_OWNER_TOKEN`,
`RELEASE_SIGNING_KEY`, and `RELEASE_SIGNING_PASSPHRASE` for the encrypted existing key. Auto merge
and Auto release need them as well as the Android release. See
[Release identity](#release-identity).

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
- The deployment secrets sit on an environment that only deploys from `master`. A branch or a tag
  that someone with write access pushes, with a workflow of their own, is refused the environment,
  so it never sees them; only a reviewed change that has reached `master` can name it. That is why
  the release workflows run on `master` and are handed the tag, rather than running on the tag.

## Deploying

A release is a version bump. There is nothing to tag by hand:

1. Merge everything you want in the release.
2. Open a pull request that bumps `app/version.properties` (`VERSION_MAJOR`/`MINOR`/`PATCH`) and
   adds the Play "What's new" text as `fastlane/metadata/android/en-US/changelogs/<version>.txt`
   (see [Play release notes](#play-release-notes)). Once it is approved and green,
   [Auto merge](#auto-merge) puts it on `master`.
3. When CI passes on that commit on `master`, **Auto release** sees there is no tag for the new
   version yet, signs the commit's `v<version>` tag as `@enwokoma` and pushes it using your token.
   It then calls Release Android and Release iOS with that tag, once each, with you as the run
   actor, and they publish it. Nobody has to approve anything after the pull request.

Merges that leave the version alone release nothing. If CI fails on the bump, nothing is tagged,
and the first later commit on `master` that passes CI with that version is released instead. A
version lower than the latest tag is refused.

The version in `version.properties` is the only one anyone sets. The Play versionCode is worked
out from the time of the build (minutes since 2026-01-01 on top of 10,000,000), so every later
build gets a higher code and nothing has to be written back to the repository. See "Versioning" in
`app/build.gradle`.

Android releases publish directly to `production` with status `completed`, targeting all
users after Google approves the release. The manual workflow also defaults to production;
choose `internal`, `alpha`, or `beta` explicitly for a testing release. Google Play review still
applies. With managed publishing enabled in Play Console, approved changes wait for you to
publish them; disable managed publishing there if approved releases should go live automatically.

Enabled iOS releases submit directly to App Review and request automatic release to all
users after Apple's approval, without a TestFlight testing stage. A manual run defaults to
`appstore`; choose `testflight` explicitly to upload a testing build without App Store submission.
The workflow summary reports submission, not public availability. Store review is separate
from the required PR review and CI checks.

### Release identity

Every future automated tag, GitHub release, release asset upload, and changelog PR uses
`@enwokoma`. Tags and changelog commits carry an SSH signature made with the existing key
`~/.ssh/id_ed25519_github_signing`, already registered to that account. Missing, expired, wrong-account, or
unregistered credentials stop the corresponding operation; there is no bot fallback.

The public key is pinned in `.github/release-signing.pub`, with fingerprint
`SHA256:unTULP6nScRb/MZELL6qtqy+UFP7T05TNlmIkv8OgPY`. No new signing key is created or
registered. Local commits and tags keep using this key through the Mac's existing SSH agent.
The setup script also accepts this already-loaded agent without a private-key secret.

GitHub-hosted runners cannot access the Mac's SSH agent. Fully automatic tag and changelog
signing on those runners requires access to the **same existing key**, using these secrets on the
`production` environment:

| Secret | Setup |
| --- | --- |
| `RELEASE_OWNER_TOKEN` | A fine-grained personal access token owned by `@enwokoma`, resource owner `gigabytedevelopers`, repository access restricted to `CometOTP`. Grant **Contents: Read and write**, **Actions: Read and write**, **Pull requests: Read and write**, and **Workflows: Read and write**. Approve it in the organisation if required. |
| `RELEASE_SIGNING_KEY` | The complete private half of the existing `~/.ssh/id_ed25519_github_signing` key, retaining its passphrase protection. A different key is rejected even if registered to the same account. |
| `RELEASE_SIGNING_PASSPHRASE` | The existing key's passphrase, required when restoring the encrypted key on a hosted runner. It is not needed with an already-loaded agent. |

No key generation or GitHub key registration is needed. Without hosted-runner access, sign and
push release tags locally using the existing key (see "Releasing by hand") and prepare signed
changelog changes locally. Automatic signing stops if the key is unavailable; it never creates
a replacement key or falls back to a bot.

The runner checks the pinned public key's GitHub registration, restores the encrypted private
key into a restricted temporary directory, and unlocks it in an isolated SSH agent. It then
removes the private file and passphrase helper. Only the agent socket and PID are passed to
later steps; cleanup stops that isolated agent. An existing local agent is left running.
Never commit private keys, passphrases, or tokens. Renew the owner token before expiry.

`RELEASE_DISPATCH_TOKEN`, `RELEASE_BOT_PRIVATE_KEY`, and `RELEASE_BOT_CLIENT_ID` are no longer
used. Once the new setup works, their old configuration can be removed.

A tag push starts no release; Auto release calls the release workflows itself, so nothing
publishes twice. Android and enabled iOS deployment jobs run only when the original workflow
actor is `@enwokoma`, which holds because Auto merge and your own merges push as you. A rerun
retains the original actor, so rerunning an old bot-started release does not repair its
attribution. Start a new manual run as yourself from `master` instead.

No workflow requests reviews: GitHub requests them from `.github/CODEOWNERS` under the pull
request author's name (see [Who reviews](#who-reviews)). CI needs no personal token or signing
key; its actor remains whoever performed the real push or PR event. GitHub Actions still provides
and identifies the check runs. Contributions by other people retain their own attribution.
Existing bot-authored timeline events remain part of the PR history.

Existing Actions runs and deployment records retain their original actor. A release's author
cannot be edited through the release update API. Published tags and commit history are not
rewritten by this change. Changing an old release author would require recreating the release,
and replacing a published unsigned tag changes its tag object; neither is done automatically.
Releases, tags, deployments, and workflow runs do not themselves add squares to the contribution
graph. Your own commits count there when their email belongs to your account and they reach the
default branch, under GitHub's contribution rules.

### Releasing by hand

A tag you push yourself starts nothing on its own, and Auto release then leaves that version alone
because the tag already exists. Publish it by running the release from `master` and naming it:

```bash
git tag -s v8.0.0 -m "CometOTP 8.0.0"
git push origin v8.0.0
gh workflow run release-android.yml --ref master -f tag=v8.0.0
```

The tag has to match `version.properties` exactly (`v` + `MAJOR.MINOR.PATCH`), or the workflow
fails before building anything. Fix the version and use a new matching signed tag; do not
rewrite a published tag as part of a normal release. A run started from the tag instead of
`master` is refused the `production` environment and stops before reading any secret.

If a release fails after its tag was pushed, re-running Auto release will not help, because the
tag now exists. Check the Android and iOS jobs of that Auto release run first, then retry the
failed release by hand: **Actions → Release Android → Run workflow → Use workflow from: master →
tag v&lt;version&gt;**.

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
pass, and review threads must be resolved. It is your pull request, so [Auto merge](#auto-merge)
fast-forwards `master` to it once that is so. You cannot approve your own generated PR.
The release workflow never merges it itself or force-updates an existing changelog branch. A bookkeeping
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

The iOS publishing pipeline is configured but gated. This repository currently has only the
Android `:app` module; the referenced iOS project and shared Kotlin framework are not present.
Keep `IOS_DEPLOY_ENABLED` unset or `false` until these targets and the Apple account are ready.
Disabled runs stop at a notice and do not access signing credentials.

When you have an Apple Developer account and a Kotlin Multiplatform iOS target:

1. Add the real iOS Xcode project, a working `:shared:assembleXCFramework` Gradle target, and an
   App Store export-options plist. Configure the target to use `MARKETING_VERSION` and
   `CURRENT_PROJECT_VERSION` for its Info.plist version fields; the archive receives the version
   from `app/version.properties` and the workflow's `<run_number>.<run_attempt>` build number.
2. Configure repository **Variables**: `IOS_BUNDLE_ID` (required, matching App Store Connect),
   `IOS_SCHEME` (default `CometOTP`), `IOS_PROJECT` (default `iosApp/iosApp.xcodeproj`), and
   `IOS_EXPORT_OPTIONS` (default `iosApp/ExportOptions.plist`).
3. Add these to a **`production-ios`** environment:

   | Secret | What it is |
   | --- | --- |
   | `IOS_CERTIFICATE_BASE64` | Distribution certificate `.p12`, base64 encoded |
   | `IOS_CERTIFICATE_PASSWORD` | Password for that `.p12` |
   | `IOS_PROVISIONING_PROFILE_BASE64` | Provisioning profile, base64 encoded |
   | `IOS_KEYCHAIN_PASSWORD` | Any throwaway password for the temporary keychain |
   | `APPSTORE_KEY_ID` | App Store Connect API key id |
   | `APPSTORE_ISSUER_ID` | App Store Connect issuer id |
   | `APPSTORE_PRIVATE_KEY` | The `.p8` private key, pasted whole |

4. Create the app in App Store Connect and complete its required listing, screenshots, privacy,
   App Review contact details, agreements, and accurate export-compliance declarations. The
   Fastlane `appstore` lane uses this existing metadata and screenshots; it does not invent or
   replace them. The App Store Connect API key must have permission to submit the app for review.
5. Set `IOS_DEPLOY_ENABLED` to `true` only after these prerequisites are complete. Limit the
   `production-ios` environment to deploy from `master` only, as `production` is.

`Gemfile.lock` pins Fastlane and its dependencies. Releases run `bundle exec fastlane ios
appstore`; that lane uploads the exact exported IPA/build, submits it for review, and sets
automatic release with phased release disabled. The explicit `testflight` lane uploads only
to TestFlight. Submission failures fail the job; neither lane falls back to the other.

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
