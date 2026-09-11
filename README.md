# Pocket Ledger

Pocket Ledger is a private expense-entry ecosystem built around a Google Sheet that each user owns. Android users install the native app; iPhone users install the supplied Apple Shortcut. Both are configured with the user's own Apps Script Web App URL.

## Distribution portal

The GitHub Pages-ready distribution site and end-user downloads are in [distribution](distribution/README.md).

- Android APK: `distribution/downloads/Pocket-Ledger-Android.apk`
- iPhone Shortcut: `distribution/downloads/Pocket-Ledger-iPhone.shortcut`
- Google Sheet copy flow: available from the distribution website's **Make your copy** button.
- Setup material: `distribution/guides` and `distribution/apps-script`.

## Basic setup

1. Choose Android or iPhone.
2. Make a personal copy of the Google Sheet template.
3. Follow the supplied guide to deploy Apps Script for that personal Sheet.
4. Configure the resulting Web App URL in Pocket Ledger/Shortcut and test it.

The Android source is retained in `NativeExpenseEntry/`. Reference files, including the original iPhone Shortcut, are preserved unchanged.
