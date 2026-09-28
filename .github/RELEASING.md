# Releasing CometOTP

Five workflows live in `.github/workflows`:

| Workflow | File | When it runs |
| --- | --- | --- |
| CI | `ci.yml` | Every pull request, and every push to `master` |
| Request review | `request-review.yml` | Every pull request that is not a draft; asks `@princesseke` to review |
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
| `RELEASE_BOT_PRIVATE_KEY` | The release bot GitHub App's private key (`.pem`), pasted whole. Goes with the `RELEASE_BOT_CLIENT_ID` **variable**; see below |

One secret is the exception and sits on the repository instead: `RELEASE_DISPATCH_TOKEN`, a token of
yours that Auto release starts the releases with, so they are deployed by you. See [Who a release
is deployed by](#who-a-release-is-deployed-by).

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

### Who a release is deployed by

The Deployments page names whoever started the release run: "Deployed to production by …". A run
Auto release starts with the workflow's own token shows **github-actions**, as 8.1.0 and 8.1.1 do.
To have it show **@enwokoma**, as a release started by hand does, Auto release starts the releases
with a token of yours, the repository secret `RELEASE_DISPATCH_TOKEN`. Until it is set the
releases still go out, as github-actions, with a warning in the Auto release run.

Set it up once, signed in as @enwokoma:

1. **Settings (your account) → Developer settings → Personal access tokens → Fine-grained tokens
   → Generate new token.** Resource owner: **gigabytedevelopers**. Repository access: **Only select
   repositories → CometOTP**. Repository permissions: **Actions: Read and write**, nothing else
   (Metadata: Read is added automatically). Pick an expiry you will remember; see below.
2. If the organisation requires approval for fine-grained tokens, approve it under the
   organisation's **Settings → Personal access tokens → Pending requests**.
3. In this repository, **Settings → Secrets and variables → Actions → Repository secrets**: add
   `RELEASE_DISPATCH_TOKEN` with the token. It is a repository secret rather than a `production`
   environment one, because Auto release is not a deployment and must not appear as one.

The token can do nothing but start and read workflow runs in this repository; it cannot read code,
push, or reach the signing secrets, which stay on the `production` environment. When it expires,
releases quietly go back to being deployed by github-actions (the warning says so), so renew it
before then and replace the secret.

Only the deployments made after the token is in place change. The existing ones keep the name they
were recorded with.

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

### The website changelog

About → Changelog opens https://gigabytedevelopers.com/apps/cometotp/changelog/. That page reads
`log/CHANGELOG.md` next to it and has no error state: if it cannot read the file it says "Loading"
forever. Mode 750 is enough to cause that, because the web server then answers with a redirect to
its 404 page.

That file is `website/CHANGELOG.md` in this repository. Nothing in GitHub can reach the server;
the server fetches the file instead, with a cron job that runs every 15 minutes. It downloads
`website/CHANGELOG.md` from `master` and installs it with mode 644, but only when it has changed
and starts with the `---` the changelog does, so a failed download or an error page never
replaces the page.

Each release adds itself: Release Android runs `.github/scripts/website_changelog.py`, which puts
the release above the newest one in `website/CHANGELOG.md`, and the changelog pull request carries
that change along with `CHANGELOG.md`. The website shows the release within 15 minutes of that
pull request being merged. The entries come from the Play notes file for the version, one section
per "New:", "Improved:", "Fixed:" or "Security:" paragraph, with the summary paragraph left out.
Without that file they come from the commits, as on GitHub.

To change the page, edit `website/CHANGELOG.md` here, in a pull request. The copy on the server,
and the one in the `gigabytedevelopers/website` repository, are overwritten the next time this
file changes, so edits made there do not last. To add a release the workflow missed:

```bash
python3 .github/scripts/website_changelog.py website/CHANGELOG.md \
  fastlane/metadata/android/en-US/changelogs/8.2.0.txt 8.2.0 2026-10-01T10:00:00 website/CHANGELOG.md
```

Set the cron job up once, in cPanel → **Cron Jobs**, with **Once Per Fifteen Minutes**
(`*/15 * * * *`) and this command:

```sh
f=$HOME/public_html/apps/cometotp/changelog/log/CHANGELOG.md; curl -fsSL --max-time 60 --max-filesize 1048576 -o $f.new https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/website/CHANGELOG.md && test -s $f.new && head -n 1 $f.new | grep -q ^--- && { cmp -s $f.new $f || { chmod 644 $f.new && mv -f $f.new $f; }; }; rm -f $f.new
```

It prints nothing unless a download fails, so the cron email only arrives when something is wrong.
It contains no `%`, which cron would otherwise treat as a line break.

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
