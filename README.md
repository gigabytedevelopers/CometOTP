#  CometOTP -  Android Two Factor (2FA) OTP Authenticator

<!-- [![Build Status](https://travis-ci.org/gigabytedevelopers/CometOTP.svg?branch=master)](https://travis-ci.org/gigabytedevelopers/CometOTP)
[![Current release](https://img.shields.io/github/v/release/gigabytedevelopers/CometOTP.svg?token=e52dc09de66b2a3a280415155904a2a059c8c0a3)](https://github.com/gigabytedevelopers/CometOTP/releases/download/v3.0.0/CometOTP_v3.0.0.apk) -->

![CometOTP](./assets/feature_graphic_new.png)


It implements HMAC-Based One-Time Passwords (HOTP) and Time-based One-time Passwords (TOTP) like specified in RFC 6238.  
Simply scan the QR code and login with the generated 6-digit code. 

## Features:

 * Free and Open-Source
 * Requires minimal permissions
   - Camera access for QR code scanning
   - Storage access for import and export of the database
 * Encrypted storage
 * Multiple backup options:
   - Plain-text
   - Password-protected
   - OpenPGP-encrypted
 * Sleek minimalistic Material Design with a Dark and Light theme
 * Great Usability
 * Compatible with Google Authenticator

## Backups:

To keep your account information as secure as possible CometOTP only stores it in
encrypted data files. A part of the encryption key used for that is stored in the
Android KeyStore system. The advantage of this approach is that the key is kept
separate from the apps data and, as a bonus, can be backed by hardware cryptography
(if your device supports this).

However, due to that separation, backups with 3rd-party apps like Titanium Backup can not
be used with CometOTP. Such apps only backup the encrypted data files and not the encryption
key, which renders them useless.

**Please only use the internal backup functions provided by CometOTP to backup your accounts!**
**Everything else WILL result in data loss.**

### Opening the backups on your PC:

 * [OpenPGP](http://openpgp.org/): OpenPGP can be used to easily decrypt the OpenPGP-encrypted backups on your PC.

## Migration:

Check out [this](https://github.com/gigabytedevelopers/CometOTP/wiki/Migration) wiki page to learn about the different ways to migrate to CometOTP from other 2FA apps.

## Downloads:

[<img height=80 alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" />](https://play.google.com/store/apps/details?id=com.gigabytedevelopersinc.app.CometOTP)
[<img height=80 alt="Get it on GitHub" src="./assets/badges/get-it-on-github.png" />](https://github.com/gigabytedevelopers/CometOTP/releases)

**Warning**: All three versions (Google Play, F-Droid and the APKs) are not compatible (not signed by the same key)!
You will have to uninstall one to install the other, which will delete all your data.
So make sure you have a **current backup** before switching!

## Contribute:

 * **Bug reports and feature requests**: You can report bugs and request features in the [Issue tracker](https://github.com/gigabytedevelopers/CometOTP/issues) on GitHub.

## Screenshots:

[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_1.png">](./assets/screenshots/screen_1.png)
[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_2.png">](./assets/screenshots/screen_2.png)
[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_3.png">](./assets/screenshots/screen_3.png)
[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_4.png">](./assets/screenshots/screen_4.png)

[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_5.png">](./assets/screenshots/screen_5.png)
[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_6.png">](./assets/screenshots/screen_6.png)
[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_7.png">](./assets/screenshots/screen_7.png)
[<img width=200 alt="Main Activity" src="./assets/screenshots/screen_8.png">](./assets/screenshots/screen_8.png)

## Acknowledgments:
#### Open-source components used:

 * [Android CustomTabs](https://github.com/saschpe/android-customtabs)
 * [Apache Commons Codec](https://commons.apache.org/proper/commons-codec)
 * [Expandable Layout](https://github.com/AAkira/ExpandableLayout)
 * [Floating Action Button Speed Dial](https://github.com/leinardi/FloatingActionButtonSpeedDial)
 * [material-intro](https://github.com/heinrichreimer/material-intro)
 * [MaterialProgressBar](https://github.com/DreaminginCodeZH/MaterialProgressBar)
 * [OpenPGP API library](https://github.com/open-keychain/openpgp-api)
 * [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded)

#### Code examples used:

 * [Android-ItemTouchHelper-Demo](https://github.com/iPaulPro/Android-ItemTouchHelper-Demo/tree/master/app/src/main/java/co/paulburke/android/itemtouchhelperdemo/helper)
 * [Code Parts from Google's Android Samples](https://android.googlesource.com/platform/development/+/master/samples/Vault/src/com/example/android/vault)
 * [LetterBitmap](http://stackoverflow.com/questions/23122088/colored-boxed-with-letters-a-la-gmail)
 * [DimensionConverter](https://stackoverflow.com/questions/8343971/how-to-parse-a-dimension-string-and-convert-it-to-a-dimension-value)

#### Previously used open-source components:

 * [FABsMenu](https://github.com/jahirfiquitiva/FABsMenu)
 * [LicensesDialog](https://github.com/PSDev/LicensesDialog)

#### Previously used code examples:

 * [FloatingActionMenuAndroid](https://github.com/pmahsky/FloatingActionMenuAndroid)

#### Contributors:

* [Okocha Ebube](https://github.com/marcusjnr/)
* [Nwokoma Emmanuel](https://github.com/enwokoma)
* [Oboh Albert](https://github.com/a-oboh)


