# Pocket Ledger

Pocket Ledger is a private expense-entry ecosystem built around a Google Sheet that each user owns. Android users install the native Android app; iPhone users install the supplied Apple Shortcut. Both are configured with the user's own Apps Script Web App URL.

This repository contains two generations of the Android app:

- **V2 (current)** — a local-first Android expense tracker, in [`V2/`](V2/README.md).
- **V1** — the original released Android app + distribution site, preserved in `NativeExpenseEntry/` and `distribution/`.

## Pocket Ledger V2 (current)

V2 is a **local-first** Android expense tracker. The on-device Room database is the
primary source of truth; Google Sheets is an explicit, user-initiated backup. No
background services, no polling, no automatic sync.

- Android project: [`V2/NativeExpenseEntry/`](V2/NativeExpenseEntry/)
- Backend: [`V2/Backend/AppsScript.gs`](V2/Backend/AppsScript.gs)
- Package: `com.pocketledger.v2` (version 2.0.0 · min SDK 26 · target SDK 36)
- Fully functional offline, works with no Sheets setup required.
- Sync is always explicit: **Settings → Sync & Google Sheets → Sync Now** (or right after adding an expense).
- Pending transactions are pushed to Apps Script in a single batched request.
- Features: dashboard with charts, ledger (search/filter/sort), quick-entry bottom sheet, home-screen widget, Quick Settings tile, JSON backup/restore.
- Build with `./gradlew assembleRelease` — see the [V2 README](V2/README.md).

## V1 — Distribution portal

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
