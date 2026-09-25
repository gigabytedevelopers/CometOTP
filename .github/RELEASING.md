# Releasing CometOTP

Four workflows live in `.github/workflows`:

| Workflow | File | When it runs |
| --- | --- | --- |
| CI | `ci.yml` | Every pull request, and every push to `master` |
| Auto release | `auto-release.yml` | After CI passes on `master`; starts both releases when the version is new |
| Release Android | `release-android.yml` | Started by Auto release, a `v*` tag, or run by hand |
| Release iOS | `release-ios.yml` | Same as Android, but **switched off** until enabled |

## Protecting master

Nobody, including you, should be able to push straight to `master`. Import the ruleset once:

**Settings → Rules → Rulesets → New ruleset → Import a ruleset**, and choose
`.github/rulesets/protect-master.json`. Or from the command line:

```bash
gh api -X POST repos/gigabytedevelopers/CometOTP/rulesets --input .github/rulesets/protect-master.json
```

It requires a pull request with one approving review from a code owner, dismisses stale approvals
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
needs your review and four passing checks before it can be merged. You still work through pull
requests, but you do not need a second person to approve your own, which as the only code owner
you would otherwise be unable to do — GitHub does not let anyone approve their own pull request.
Remove the `bypass_actors` entry once there is a second maintainer who can review your work.

`.github/CODEOWNERS` is what routes every pull request for that review. It names
`@gigabytedevelopersinc`, `@enwokoma` and `@princesseke`, so all three are asked on every pull
request and an approval from any one of them satisfies the ruleset. The author is never asked to
review their own, so the other two are. Owners
must be user accounts or `@org/team` names with write access; the organisation `@gigabytedevelopers`
is neither, and while the file named it, no review was ever requested automatically. GitHub lists
any owner it cannot resolve under the file's "Code owners errors" on the repository page.

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
| `RELEASE_BOT_PRIVATE_KEY` | The release bot GitHub App's private key (`.pem`), pasted whole. Goes with the `RELEASE_BOT_CLIENT_ID` **variable**; see below |

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
2. Open a pull request that bumps `app/version.properties` (`VERSION_MAJOR`/`MINOR`/`PATCH`), and
   merge it.
3. When CI passes on that commit on `master`, **Auto release** sees there is no tag for the new
   version yet, tags the commit `v<version>`, and starts Release Android and Release iOS on it. The
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

### Releasing by hand

Pushing a tag still works, and Auto release then leaves that version alone because the tag
already exists:

```bash
git tag -s v8.0.0 -m "CometOTP 8.0.0"
git push origin v8.0.0
```

The tag has to match `version.properties` exactly (`v` + `MAJOR.MINOR.PATCH`), or the workflow
fails before building anything. If it does, delete the tag, fix the version on master, and tag
again.

If Auto release created the tag but a release workflow failed to start, re-running Auto release
will not help, because the tag now exists. Start it by hand instead: **Actions → Release Android
→ Run workflow → Use workflow from: Tags → v&lt;version&gt;**.

The job builds a signed App Bundle, uploads it with the ProGuard mapping so crash reports
deobfuscate, attaches the bundle to a GitHub release, and opens a pull request adding the new
section to `CHANGELOG.md`. That last step is a pull request rather than a push because `master` is
protected, and the rules apply to the workflow too.

### The changelog pull request and the release bot

The changelog pull request is opened by a GitHub App, the release bot, rather than the workflow's
own `GITHUB_TOKEN`. A pull request opened with `GITHUB_TOKEN` gets its CI run held at "action
required" until someone approves it, so the required checks never report, as happened to 8.1.0.
The app's pull request runs CI like anyone else's, and because the app is the author, you can
approve it as code owner. Either way the commit is made through the API (`sign-commits`), so GitHub
signs it and it passes the signed-commits rule.

Set it up once:

1. **Settings → Developer settings → GitHub Apps → New GitHub App** (on the organisation that owns
   the repository). Name it something like "CometOTP release bot", untick **Webhook → Active**,
   and under **Repository permissions** give it **Contents: Read and write** and **Pull requests:
   Read and write**. Nothing else.
2. Create it, note its **Client ID**, and under **Private keys** generate a key. A `.pem` file
   downloads.
3. **Install App** → install it on this repository only.
4. In this repository, **Settings → Secrets and variables → Actions → Variables**: add
   `RELEASE_BOT_CLIENT_ID` with the client ID.
5. **Settings → Environments → production**: add the secret `RELEASE_BOT_PRIVATE_KEY` with the
   whole contents of the `.pem` file, then delete the file.

Until that is done the workflow falls back to `GITHUB_TOKEN` and warns: the pull request still
opens with a signed commit, but you have to press **Approve and run** on it before its CI runs.
Squash-merging it with the green button is fine, since the commit is the bot's, not yours.

### Play release notes

`fastlane/metadata/android/en-US/changelogs/default.txt` is the "What's new" text, capped at the
500 characters Play allows. Delete it and the workflow falls back to the first 480 characters of
the generated notes.

The workflow copies it to `whatsnew-en-US` in a temporary directory and points the upload there,
because that is the name `upload-google-play` looks for. Fastlane's `supply` wants
`<versionCode>.txt` instead; using that name here uploads the bundle with no notes at all and says
nothing about it. Keep `default.txt` where it is and let the workflow do the renaming.

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
