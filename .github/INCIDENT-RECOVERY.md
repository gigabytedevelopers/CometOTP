# CometOTP Incident Recovery

## 1. Release Hold

The editor autorun malware and disguised font payload were removed on 12 October 2026. Automatic release, Android release, and iOS release workflows are disabled in GitHub. All publishing jobs also require the administrator-controlled repository variable `SECURITY_RELEASE_APPROVED` to equal `true`. Its incident value is `false`.

The production environment requires approval from `enwokoma`. Automatic tag signing now uses that environment too. The repository security check runs before build code or release credentials are used. Workflow dependencies are pinned to reviewed upstream commit IDs.

<div style="break-before: page;"></div>

## 2. Recovery Requirements

Before changing the release approval variable or enabling publishing:

1. Audit organization activity from July onward using the GitHub audit log. Commit author names and timestamps do not identify the account that pushed a change.
2. Investigate machines that opened infected folders in VS Code or Cursor. Removing files from GitHub does not clear an affected machine or its cached credentials.
3. Revoke and replace potentially exposed release tokens, website deployment credentials, signing credentials, and Google Play service-account credentials. Create replacement credentials from a trusted machine and scope them to the required repositories and operations.
4. Review the release signing key's exposure. Changing only a passphrase does not invalidate a stolen private key. Replace an exposed Git signing key and update the pinned public key and registration together.
5. Review Android signing through the appropriate Play App Signing/upload-key recovery process. Do not replace the app's signing identity blindly: that can break updates for installed users.
6. Confirm required reviewers and deployment branch/tag restrictions remain enabled, then validate the intended signed release tag and its tree.
7. Enable publishing workflows and set `SECURITY_RELEASE_APPROVED=true` only after recovery is complete. Keep deployment approval in place.

<div style="break-before: page;"></div>

## 3. Existing Clones and History

Old commits and tags retain the removed files. Use a fresh clone of the remediated branch. Do not open old revisions in an editor that permits folder-open tasks. Inspect and remove untracked copies of the payload and propagation scripts in existing clones after preserving any evidence needed for investigation.

The cleanup uses forward commits and preserves history. It does not certify historical release artifacts, external credentials, or developer machines. The security check rejects this incident's filenames, invalid font headers, hidden editor autorun, concealed payloads, committed private signing keys, and recognizable GitHub tokens; it is not a general malware detector.
