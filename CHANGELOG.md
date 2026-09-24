# Changelog

All notable changes to CometOTP are recorded here.

Entries from 8.0.0 onwards are generated from the commit history when a release is deployed, so
the wording follows the commit subjects. See `.github/RELEASING.md` for how that works.

## 8.1.0 (2026-09-24)


### Fix

- Give equal entries equal hash codes
- Pick the same thumbnail for an issuer every time
- Keep a Steam account's period when it is saved
- Default a Steam account without a digit count to five characters
- Keep the other accounts when one saved entry cannot be read
- Stop the letter tile from reading a recycled color array
- Read secrets and settings the same way in every language
- Close the backup file even when writing it fails
- Show an error instead of crashing on a file that cannot be read
- Keep the old password when re-encryption fails
- Keep the new database key when the security screen is recreated
- Change the database encryption without freezing the settings screen
- Stop setting up the unlock screen after it has already closed
- Stop the setup wizard crashing on keyboard actions other than Done
- Stop the password dialog crashing on keyboard actions other than Done
- Store the lock method and encryption type the same in every language
- Close the backup screen when the database is still locked
- Release the old OpenPGP binding before binding a new one
- Reopen the backup screen cleanly after the app was killed mid-task
- Finish OpenPGP backups and restores after the backup screen is recreated
- Stop leaking the main screen through the process lifecycle observer
- Keep the main screen alive when all settings are cleared
- Ignore an image picker result that carries no image
- Do nothing on a panic trigger when no panic response is stored
- Stop clearing the cache from crashing on an unreadable folder
- Leave the app version blank in support mail when it is unknown
- Ignore taps on a service card that has no token yet
- Skip the auto-backup instead of crashing when the key is not loaded
- Remove every non-retained intro slide in retainSlides
- Apply the gravity given to ParallaxLinearLayout.LayoutParams
- Let page change listeners be removed from the intro pager
- Use the pager's current adapter in the intro page transformer
- Centre the intro page indicator dots inside its padding
- Return no intro CTA label when none has been set
- Stop a deleted card's reveal timer from crashing the app
- Ignore taps on a card while the list is being refreshed
- Refuse an unsigned OpenPGP backup instead of crashing when verifying
- Release the worker thread when a background task finishes
- Close the clear-cache sheet when its screen goes away
- Show the keyboard when search is focused on start
- Let the intro's page transformer be removed
- Stop the intro pager piling up adapter observers
- Return a slide's real position from getSlidePosition
- Use the intro CTA click listener that was set
- Keep only the current view's parallax children in intro slides
- Notice a permission revoked while the intro is open
- Size the intro dots correctly on fractional screen densities
- Stop the intro pager crashing on a move without a prior touch down
- Never save or back up a database that failed to load
- Truncate a backup file before writing over it
- Replace the database in one step when saving it
- Report a failed scheduled encrypted backup as failed
- Keep the other tags' colours when one stored value is broken
- Keep a Steam account's period when it is exported
- Stop a zero period from crashing the account list
- Stop an account without a label from crashing search, sort and tiles
- Fall back to the default token grouping on a bad setting
- Undo a key change whose new settings could not be saved
- Keep the old password when upgrading it fails
- Never unlock a password or PIN lock that has no stored password
- Store the password before the lock in the setup wizard
- Set a new password or PIN without freezing the settings screen
- Stop keeping the new password in the set-up screen's saved state
- Stay locked when the device lock has been removed
- Ask for authentication again after the app was killed in the background
- Stop keeping the database key in the main screen's saved state
- Stop password fields from saving what was typed into them
- Keep a plain-text password until its replacement is stored
- Stop keeping the database key in the settings screens' saved state
- Stop the password setup screen crashing on Android 6 and 7 when autofill is blocked
- Stop the About screen's author buttons cutting off their text
- Update PayPal link for author donations

### Improvement

- Fix the release workflow's changelog pull request (#12)
- Stop a shipped release showing as a failed deployment (#13)
- Convert the constants, token maths and encryption helpers to Kotlin
- Convert the entry model, database and backup helpers to Kotlin
- Convert Settings and the remaining utility helpers to Kotlin
- Convert the vendored ExpandableLayout to Kotlin
- Convert the drag helper, autofill field and double-click listener to Kotlin
- Convert the countdown ring, bottom bar and coach marks to Kotlin
- Convert the privacy policy and terms fragments to Kotlin
- Convert the intro screen utilities and parallax views to Kotlin
- Convert the intro screen slides and slide adapter to Kotlin
- Convert the intro screen pager and page indicator to Kotlin
- Convert the intro screen activity and navigation API to Kotlin
- Convert the base activities and the small screens to Kotlin
- Convert the backup screen to Kotlin
- Convert the main activity to Kotlin
- Convert the background tasks to Kotlin
- Convert the backup broadcast receivers to Kotlin
- Convert the dialogs and bottom sheets to Kotlin
- Convert the entry list adapters and card view holder to Kotlin
- Convert the preferences to Kotlin
- Convert the authentication and security activities to Kotlin
- Convert the tags, settings and intro screen activities to Kotlin
- Drop the annotations that only existed for Java callers
- Use requireArguments() in the intro slide fragments
- Release automatically on a version bump, and stop versionCode collisions (#15)
- Ci/release versioning (#16)

## 8.0.0 (2026-09-22)

### Breaking
- The launcher icon is now supplied by a set of aliases so it can be changed from Settings. An
  existing CometOTP shortcut pinned to the home screen may need to be added again after updating.

### New
- Rebuilt the whole interface on the new CometOTP design system
- Added a Security screen for setting up a PIN, a password or fingerprint and device-lock unlock
- Added a Tags screen for creating, colouring, renaming and deleting tags, with item counts
- Added Support & FAQs, an FAQ reader and a Contact Us form
- Added coach marks that introduce the home screen on first launch
- Added a choice of app icon in Settings: blue on white, black on white, white on blue, or the
  original artwork
- Added an animated launch screen
- Added a copy button to every entry card, so a code is one tap away
- Added a sort icon that shows which sort is active
- Moved the card's own options onto a bottom sheet, alongside the navigation and add menus
- Moved the tag filter into a bottom sheet
- Made tags created on the Tags screen available when setting up a key and when filtering, and
  allowed a new tag to be created from either place

### Improvement
- Moved the app onto Material 3 Expressive
- Changed the typeface from Oxygen to Inter
- Rebuilt the launcher icon around the brand mark, with adaptive and monochrome layers
- Docked the bottom bar to the window, notched it for the add button, and let the list scroll
  underneath without the last entry being covered
- Gave every dialog in the app the same Material treatment
- Put issuer logos on a light plate in the dark and black themes, where black artwork used to
  disappear
- Redrew the empty states for the service list, the tag list and search
- Widened the entry cards and tightened their spacing to match the previous app, and applied the
  same width and spacing to the tag, FAQ and content cards
- Let a long token shrink to fit its line instead of wrapping, with the previous token beneath it
- Gave the backup format dropdown room for its longest name
- Worked each token out as its card is bound, so a freshly built list is never briefly blank
- Brought the launcher shortcut icons onto the brand palette
- Rebuilt the store graphics and README screenshots around the new identity
- Dropped the platform from the product description, ahead of sharing this codebase beyond Android

### Fix
- Fixed a crash when enabling special features, changing the security method or choosing a backup
  type after the Settings screen had been opened
- Fixed the restore sheet running indefinitely when the chosen file did not match the selected
  backup format, which could end in the app being killed
- Fixed status bar icons being drawn dark on the dark themes, leaving the clock unreadable
- Fixed Cancel and OK being invisible on the preference dialogs in the light theme
- Fixed the bottom bar icons sitting off-centre in their touch targets
- Fixed a blank band across the cards while tokens were recalculated
- Fixed the label size setting no longer reaching the token
- Fixed a crash when searching or sorting by issuer, if an account had been added from a link
  that carried no issuer
