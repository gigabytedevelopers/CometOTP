# Changelog

All notable changes to CometOTP are recorded here.

Entries from 8.0.0 onwards are generated from the commit history when a release is deployed, so
the wording follows the commit subjects. See `.github/RELEASING.md` for how that works.

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
