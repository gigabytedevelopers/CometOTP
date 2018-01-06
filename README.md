#  CometOTP - OTP Authenticator for Android

[![Build Status](https://travis-ci.org/gigabytedevelopers/CometOTP.svg?branch=master)](https://travis-ci.org/gigabytedevelopers/CometOTP)

![CometOTP](./assets/logo.png)
[<img height=80 alt="Get it on GitHub" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/badges/get-it-on-github.png" />](https://github.com/gigabytedevelopers/CometOTP/releases)
[<img height=80 alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" />](https://play.google.com/store/apps/details?id=com.gigabytedevelopersinc.app.CometOTP)

CometOTP is a powerful two-factor authentication App for Android.

It implements Time-based One-time Passwords (TOTP) like specified in RFC 6238.  
Simply scan the QR code and login with the generated 6-digit code. 

This is a fork of the great OTP Authenticator app written by Bruno Bierbaumer,
which has sadly been inactive since 2015. All credit for the original version
goes to Bruno.

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

**Please only use the internal backup functions provided by andOTP to backup your accounts!**
**Everything else WILL result in data loss.**

### Opening the backups on your PC:

 * [OpenPGP](http://openpgp.org/): OpenPGP can be used to easily decrypt the OpenPGP-encrypted backups on your PC.
 * [andOTP-decrypt](https://github.com/asmw/andOTP-decrypt): A python script written by @asmw to decrypt password-protected backups on your PC (needs more testing).

## Migration:

Check out [this](https://github.com/gigabytedevelopers/CometOTP/wiki/Migration) wiki page to learn about the different ways to migrate to CometOTP from other 2FA apps.

## Contribute:

 * **Translation**: If you want to help translate andOTP into your language head over to this [place](http://gigabytedevelopers.oneskyapp.com/collaboration/project/296138).
 * **Bug reports and feature requests**: You can report bugs and request features in the [Issue tracker](https://github.com/gigabytedevelopers/CometOTP/issues) on GitHub.
 * **Requesting thumbnails**: If you are missing a thumbnail you can request it by editing the [wiki](https://github.com/gigabytedevelopers/CometOTP/wiki/Thumbnails#thumbnail-requests)


#### Translators:

&nbsp; | Language          | Translators
------ | ----------------- | -----------
🇵🇱   | Polish (pl-rPL)   | [Daniel Pustuła](https://github.com/9Cube-dpustula)
:es:   | Spanish (es-rES)  | Carlos Melero
:de:   | German (de-rDE)   | SuperVirus
:fr:   | French (fr-rFR)   | [Johan Fleury](https://github.com/johanfleury), David Sferruzza, Primokorn
🇳🇱   | Dutch (nl-rNL)    | Toon, rain2reign
&nbsp; | Galician (gl-rES) | Triskel
:ru:   | Russian (ru-rRU)  | Victor Nidens, Ilia Drogaitsev, Dmitry
🇨🇿   | Czech (cs-rCZ)    | Picard0147
:cn: | Chinese Simplified (zh-rCN) | Cp0204

## Screenshots:

[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity_hidden.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/settings_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/settings_activity.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/about_activity.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/thumbs1.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/thumbs2.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/thumbs3.png)

[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity_dark.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/main_activity_hidden_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/settings_activity_dark.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/settings_activity_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity_dark.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/about_activity_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/thumbs1_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/thumbs2_dark.png)
[<img width=200 alt="Main Activity" src="https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/backup_activity.png">](https://raw.githubusercontent.com/gigabytedevelopers/CometOTP/master/assets/screenshots/thumbs3_dark.png)

## Acknowledgments:
#### Open-source components used:

 * [Apache Commons Codec](https://commons.apache.org/proper/commons-codec/)
 * [Expandable Layout](https://github.com/AAkira/ExpandableLayout)
 * [LicensesDialog](https://github.com/PSDev/LicensesDialog)
 * [MaterialProgressBar](https://github.com/DreaminginCodeZH/MaterialProgressBar)
 * [OpenPGP API library](https://github.com/open-keychain/openpgp-api)
 * [VNTNumberPickerPreference](https://github.com/vanniktech/VNTNumberPickerPreference)
 * [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded)

#### Code examples used:

 * [Android-ItemTouchHelper-Demo](https://github.com/iPaulPro/Android-ItemTouchHelper-Demo/tree/master/app/src/main/java/co/paulburke/android/itemtouchhelperdemo/helper)
 * [Code Parts from Google's Android Samples](https://android.googlesource.com/platform/development/+/master/samples/Vault/src/com/example/android/vault)
 * [FloatingActionMenuAndroid](https://github.com/pmahsky/FloatingActionMenuAndroid)
 * [LetterBitmap](http://stackoverflow.com/questions/23122088/colored-boxed-with-letters-a-la-gmail)
 * [DimensionConverter](https://stackoverflow.com/questions/8343971/how-to-parse-a-dimension-string-and-convert-it-to-a-dimension-value)

## License:
```
Copyright (C) 2018 Gigabyte Developers
Copyright (C) 2017 Jakob Nixdorf
Copyright (C) 2015 Bruno Bierbaumer
Copyright (C) 2015 Google (Google Developers) - Google Authenticator

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in the
Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.
```
