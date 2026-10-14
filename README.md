# Pocket Ledger

A local-first expense tracker for Android, with an optional second copy in a Google Sheet you own. Record quickly, review your spending, and keep your backup portable. An Apple Shortcut is also available as a separate iPhone workflow.

[Website](https://techyeye98.github.io/Pocket-Ledger/) · [Download Android 6.0.8](https://techyeye98.github.io/Pocket-Ledger/downloads/Pocket-Ledger.apk) · [Android quick guide](https://techyeye98.github.io/Pocket-Ledger/guides/Pocket-Ledger-Quick-Guide-2026-10.pdf) · [Report an issue](https://github.com/Techyeye98/Pocket-Ledger/issues)

## Current Android app

**6.0.8** · Android 8.0+ · App name: **Pocket Ledger**

- A statement-led Home screen, interactive category donut, and Needs / Wants / Untagged spending insights with entry drill-down.
- Activity search, period/category/payment/type filters, sorting, editing, deletion, and quick Need / Want tagging.
- Fast expense entry with amount, category, payment method, optional note, and an optional Need / Want choice. Nothing is automatically labelled for you.
- A logo-led, resizable home-screen widget. Add it from Settings on supported launchers; it opens a separate expense sheet and returns to the launcher when saved or closed. A Quick Settings tile is also available.
- Light, dark and system themes; configurable category/payment names and order; currency and entry defaults; Show / Hide for latest entries.
- JSON backup and import for transactions, spending tags, preferences, categories, payment methods and defaults. Older transaction-only backups remain importable.
- Offline recording without an account or Sheet. Optional Apps Script sync sends pending transactions, edits and deletions to your own Sheet.

### Installation and migration

Download and open the APK on Android. If prompted, allow installation from the app used to open the file, then install.

**This build is a separate installation from the old V2 app**, not an in-place V2 update. Its package is `com.pocketledger.v6.preview` (version code 9); the visible app name has no V6 suffix. It can update a previous preview installed with the same signing certificate. The distributed APK is debug-signed, not signed with the original V2 release key.

Before migrating from V2, export a JSON backup and keep the old app installed until you have imported and checked your records in the new app. Older entries remain Untagged until you classify them. Do not uninstall your only copy of the data.

## Optional Google Sheets setup

You can skip this entirely and use the app offline.

1. [Make your own copy of the current Android Sheet](https://docs.google.com/spreadsheets/d/1vu140rEacxVWVeWVil1BvjcWNFS2xyJII1b-rjoisrw/copy).
2. In a desktop browser, open the copy and choose **Extensions → Apps Script**. Save the copied script. The current reference is [V6/Backend/AppsScript.gs](V6/Backend/AppsScript.gs).
3. Choose **Deploy → New deployment → Web app**. Set **Execute as: Me** and **Who has access: Anyone**, authorize your own script, and deploy.
4. Copy the Web app URL ending in `/exec`. In the app, open **Settings → Sync & Sheets → Web App URL**, paste it, and use **Test Connection**, then **Sync Now**.

Saving an expense stores it locally first and attempts to send pending changes when a URL is configured. Failed sync stays pending for retry. There is no scheduled background sync. Need / Want sync requires the current backend; an old script cannot confirm the tag.

**Keep your deployment URL private.** It is different from the Sheet copy link, and anyone with it can call the deployed script. It is deliberately excluded from JSON backup; reconnect Sheets on a new phone. A new Sheet does not automatically receive entries already marked synced to a previous Sheet. **Restore Data from Sheet** merges Sheet records into the phone; it does not replace JSON backup of your preferences.

## Backups

Use **Settings → Data & support → Export Backup** to save a JSON file. **Import Backup** merges transactions, preserves newer local records, and restores saved preferences and category/payment configuration. An old transaction-only backup leaves your current preferences unchanged. New version-2 backups cannot be imported by older APKs.

**Export as Text** copies a readable transaction list; it is not a restorable backup. Keep backup files private: they contain your financial records.

## iPhone

[Install the supplied Shortcut](https://techyeye98.github.io/Pocket-Ledger/downloads/Pocket-Ledger-iPhone.shortcut) and [copy its separate Sheet template](https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy). Use the [iPhone setup guide](https://techyeye98.github.io/Pocket-Ledger/guides/iPhone/iPhone-Shortcut-Setup-Guide.pdf). The Android APK, local database, Need / Want features and Android quick guide do not describe the iPhone Shortcut.

## Repository map

| Location | Purpose |
| --- | --- |
| `V6/NativeExpenseEntry/` | Current Android source, Java / native Android / Room |
| `V6/Backend/AppsScript.gs` | Current Google Sheets backend with spending tags |
| `V6/tests/` | Need / Want and spending-insight checks |
| `distribution/` | Published website, APK, guides and setup files |
| `website/experiments/v6/` | Original V6 website source and local preview |
| `V2/`, `NativeExpenseEntry/` | Previous Android generations |
| `Reference/`, `archive/` | Original references and historical material |

## Development

Install JDK 17 and an Android SDK with API 36, configure your local SDK path, then build:

```powershell
cd V6/NativeExpenseEntry
.\gradlew.bat :app:assembleDebug
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`. A release build requires local signing credentials; never commit signing keys, passwords, `local.properties`, private deployment URLs, or phone captures.

```sh
node V6/tests/needs-wants.test.cjs
node website/experiments/v6/verify.cjs
node tools/site-smoke/v6-publication.cjs
node website/experiments/v6/serve.cjs
```

The last command previews the website at `http://127.0.0.1:4176/website/experiments/v6/`. The publication smoke check requires an installed Chromium/Edge and `puppeteer-core`. The website has no build step or paid animation dependency; motion respects reduced-motion preferences.

GitHub Pages publishes **`distribution/`** through [.github/workflows/deploy-pages.yml](.github/workflows/deploy-pages.yml) on pushes to `main`. App source lives in `V6/`; the release download is `distribution/downloads/Pocket-Ledger.apk`. Real-device screenshots have been reviewed for the guide; sync, launcher behavior and migration still depend on the user's device, launcher and personal Sheet setup.
