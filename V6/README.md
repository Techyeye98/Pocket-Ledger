# Pocket Ledger Android source

Current source/build: **6.1.0-preview.14** (versionCode 23), Android 8.0+, package `com.pocketledger.v6.preview`, Room schema 3. The visible app name is Pocket Ledger.

The up-to-date [main README](../README.md) covers all current features, installation/migration, Android/iOS differences, setup, financial examples, backups and developer checks. It supersedes the earlier expense-only overview.

- Accounts/opening balances, income, transfers, merchant refunds and balance adjustments.
- Shared expenses, lending/borrowing, friend-paid spending, linked repayments and Lena / Dena.
- Home/Activity insights, Need / Want tagging, widget, Quick Settings tile and light/dark themes.
- Standalone split bills with saved friends, item allocations, reviewed on-device receipt recognition, tax and payment/settlement calculations.
- Full financial-ledger sync and JSON backup. Split bills/friends remain on this phone, outside both sync and backups.
- Clean Transactions report, existing Dashboard and a hidden support sheet; stable IDs and deletion markers remain in support storage.

## Start here

- [Android Sheet template](https://docs.google.com/spreadsheets/d/1rxBf4afldt5WeRTu_Lxue1OeDSCDepbNpJz45OeTdAA/copy)
- [Canonical backend and setup](Backend/CANVAS-DEPLOY.md)
- [Clean Sheet migration evidence](docs/CLEAN_SHEET_LAYOUT_2026-10-19.md)
- [Fresh-start sync fix](docs/FRESH_START_SYNC_FIX_2026-10-19.md)
- [Code-review fixes and validation limits](docs/CODE_REVIEW_FIXES_2026-10-19.md)

Install over a preview signed with the same certificate. Older release-signed installations require a separate migration; keep a verified JSON backup and the old app until records are checked. The build remains debug-signed.

The current local website package lives in `../distribution/`. Its earlier Android PDF has not yet been replaced with a new screenshot guide. Historical design/verification documents may describe prior previews; use the main README and the latest change-specific notes for current behavior. No Git push or public deployment is included in the local documentation update.

## Current illustrated guide

The local [guide hub](../distribution/guide.html) has nine short task chapters. The [14-page illustrated Android PDF](../distribution/guides/Pocket-Ledger-Guide-2026-10.pdf) covers the current accounts/financial ledger and standalone split bills, including partial repayments, tax/discount, receipt review and backup limits. Captures use labelled demo data; the owner’s original phone state was restored after capture. Older expense-only PDFs are archived, not the primary guide.
