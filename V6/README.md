# Pocket Ledger · Android preview

V6 is the new native Android design. Its visual direction is a modern passbook: a prominent monthly statement, legible chronological entries, and charts that behave like tools for examining spending. It is intentionally not a recolour of the earlier dashboard. The palette stays in the Pocket Ledger green family, with designed light and dark modes.

## What changed

- Home: statement-led summary, date range controls, touch-selectable category donut, Needs/Wants/Untagged spending shares with entry drill-down and review, and a persistent Show/Hide control for recent entries.
- Activity: search, category/payment/spending-type filters, sort options, full transaction edit/delete, and a quick Set Need / Want action.
- Add expense: focused entry slip, category/payment choices, optional Need/Want selection and note, local-first save, and optional Sheets sync. New entries start untagged; tap a selected type again to clear it. Widget and Quick Settings entry use the same form.
- Widget: logo-led tiny, compact, standard, and expanded appearances. New widgets target a wide 5×1 default, with one-cell minimum resize limits; the launcher controls exact placement and may retain an existing widget's old size until it is removed and re-added. Settings can request a launcher-pinned widget, subject to launcher support and confirmation. Tap opens a separate, temporary entry sheet over the launcher; Save or Close removes that task. In-app Add Expense remains full-screen.
- Settings: sync status is prominent, while preferences, entry options, backups, and support use grouped controls with the same light/dark visual system.
- Settings, backup/import, Quick Settings tile, shortcuts, and sync remain available.

## Portable backups

`Export Backup` creates a JSON file with transactions plus the selected theme, currency, device name, category and payment-method lists (including names, emoji and order), entry defaults/last selections, and recent-entry visibility. Import merges transactions without wiping newer local records, then restores those saved settings. Importing an older transaction-only backup leaves the current settings alone. The Google Sheets URL/verified connection, sync timestamps and anonymous install ID are intentionally device-specific and are not exported or replaced; reconnect Sheets manually on a new device. `Export as Text` is a separate transactions-only clipboard copy.

## Need / Want backup and sync

The database upgrades from schema 1 to 2 by adding a non-null spending type with `UNTAGGED` as its default; existing amounts, IDs, timestamps and sync states are preserved. JSON exports use backup version 2 and include each transaction's type. This app still imports version 1 backups, whose entries start untagged. Older APKs cannot import the new version 2 backup.

Settings → Sync & Sheets → Need / Want sync setup opens the latest Android template: `https://docs.google.com/spreadsheets/d/1vu140rEacxVWVeWVil1BvjcWNFS2xyJII1b-rjoisrw/copy`. The setup explains making a copy, opening its Apps Script project, deploying a web app, entering the deployment `/exec` URL, and testing/syncing. New copies do not automatically receive previously synced entries.

The setup has a single template flow with **Open Sheet template** and **Close**. `Backend/AppsScript.gs` is the reference backend source. It appends a Spending Type column after existing columns, reads it on restore, and updates by transaction ID. Pending saves are confirmed only when the script echoes the saved type. An older script can receive the base expense but cannot confirm the tag; the app leaves it pending for retry after the script update. A stale script is also rejected for restore to avoid losing locally saved tags.

Offline tracking and JSON backups work without this setup. This change does not deploy the script to a user's Google account.

## Installation safety

The app's visible name is simply **Pocket Ledger**. Version **6.0.8** (version code 9) uses `com.pocketledger.v6.preview`, so it updates the previously installed preview while remaining separate from the old V2 app and its local data. Both icons may therefore say Pocket Ledger on a phone with both apps installed. The current website download is `distribution/downloads/Pocket-Ledger.apk`; older APK links are retained.

An in-place update of V2 still requires its original release-signing password. Without it, do not relabel a debug APK as a V2 update; export and verify a V2 backup before migrating data to the separate preview.

## Build and test status

`NativeExpenseEntry` builds with `:app:assembleDebug`. The published APK is a debug-signed build, not a V2 release-key build. Current real-phone screens were captured and reviewed for the eight-page Android quick guide. No emulator download is required. Verify your own backup/import, launcher widget behavior and personal Sheet sync on your device; screenshots alone do not establish those end-to-end checks.
