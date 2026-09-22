# Releasing CometOTP

Three workflows live in `.github/workflows`:

| Workflow | File | When it runs |
| --- | --- | --- |
| CI | `ci.yml` | Every pull request, and every push to `master` |
| Release Android | `release-android.yml` | A `v*` tag, or run by hand |
| Release iOS | `release-ios.yml` | A `v*` tag, but **switched off** until enabled |

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

Repository admins are on the bypass list. Everyone else is fully bound: a fork's pull request
needs your review and four passing checks before it can be merged. You still work through pull
requests, but you do not need a second person to approve your own, which as the only code owner
you would otherwise be unable to do — GitHub does not let anyone approve their own pull request.
Remove the `bypass_actors` entry once there is a second maintainer who can review your work.

`.github/CODEOWNERS` is what routes every pull request to you for that review.

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

## Deploying Android

1. Merge everything you want in the release.
2. Bump `app/version.properties` (`VERSION_MAJOR`/`MINOR`/`PATCH`). `VERSION_BUILD` bumps itself
   on every release build, so leave it alone.
3. Tag and push:

   ```bash
   git tag -s v8.0.0 -m "CometOTP 8.0.0"
   git push origin v8.0.0
   ```

Or run **Actions → Release Android → Run workflow** and pick a track. The tag path publishes to
`internal` by default; promote from the Play Console, or run the workflow again choosing
`production`.

The job builds a signed App Bundle, uploads it with the ProGuard mapping so crash reports
deobfuscate, attaches the bundle to a GitHub release, and opens a pull request adding the new
section to `CHANGELOG.md`. That last step is a pull request rather than a push because `master` is
protected, and the rules apply to the workflow too.

### Play release notes

`fastlane/metadata/android/en-US/changelogs/default.txt` is the "What's new" text, capped at the
500 characters Play allows. The workflow copies it to a file named after the versionCode it just
built. Delete it and the workflow falls back to the first 480 characters of the generated notes.

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
