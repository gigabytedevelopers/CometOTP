# R8 configuration for CometOTP.
#
# Release builds are shrunk, obfuscated and resource-shrunk (Google Play's "App optimization"
# check flags bundles that are not). Keep these rules minimal and specific: the AndroidX and
# Material libraries ship their own consumer rules, and AGP generates keep rules for every
# component named in the manifest and every custom view named in a layout.

# Keep file names and line numbers so stack traces in the Play Console and crash reports can be
# de-obfuscated with the mapping file that is bundled with each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preferences are instantiated by class name from res/xml/preferences*.xml.
-keep class com.gigabytedevelopersinc.app.cometOTP.Preferences.** { <init>(...); }
-keep class org.openintents.openpgp.util.OpenPgpAppPreference { <init>(...); }
-keep class org.openintents.openpgp.util.OpenPgpKeyPreference { <init>(...); }

# Custom views are inflated by name from layouts (belt and braces next to the AGP-generated rules).
-keep class com.gigabytedevelopersinc.app.cometOTP.View.** extends android.view.View { <init>(...); }

# The Android backup framework instantiates the agent named in the manifest.
-keep class com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupAgent { <init>(...); }
