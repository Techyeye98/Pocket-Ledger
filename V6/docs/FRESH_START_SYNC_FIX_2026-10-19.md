# Fresh-start sync fix — 19 October 2026

The phone reported `Synced 0, 63 failed: Amount must be positive` after transaction history and visible Sheet cells were cleared. Live inspection found residual `WALLET`, `BANK` and `CARD` values in hidden Account type cells, with no date, amount or transaction ID. The legacy Sheet importer treated those metadata-only rows as new zero-amount expenses and blocked the ledger merge.

`handleFetch` now ignores rows with no date, amount or transaction ID. It retains deletion markers that have stable IDs and continues rejecting live zero-amount transactions. No balances or transaction amounts were invented or changed to bypass validation. The same fix is included in the bundled Apps Script asset.

The error was reproduced against the actual pre-fix live script with synthetic metadata leftovers. Regression tests now cover clearing the Sheet, leaving hidden account types, pushing the phone's cleared-history markers, rebuilding accounts and balances, and rejecting an active zero-amount entry. Backend, Canvas, migration, report and JVM suites passed. Android assembleDebug and lint passed (0 errors, 40 existing warnings).

The user's confirmed **Pocket Ledger** deployment was updated in place to **Version 12**, 19 October 2026 at 19:34, with the existing endpoint and access settings. The saved source was read back and matched the minimal patch. A live read-only `FETCH` returned success and an empty transaction list; account upload still requires Sync Now on the phone. Deployment evidence: `NativeExpenseEntry/app/build/sheet-upgrade/sync-fix-deployed-2026-10-19.png`.

Android **6.1.0-preview.13** (versionCode 22) adds the existing app logo beside the Home header's Pocket Ledger text. Install the debug APK over the existing preview app, preserving its local data. No device was connected for visual verification.

## User steps

1. Keep the current app data and Web App URL. Do not uninstall or clear data to resolve this error.
2. Use Settings → Sync & Sheets → Sync Now, then Refresh Sheet dashboard.
3. Check account balances in the app. If the fresh start requires different opening balances, edit the accounts there and sync again.
4. Reload the Sheet. Old cleared entries should remain absent. Hidden technical columns must be retained for sync.

No old transaction history was restored, and no synthetic test records were written to the live Sheet.
