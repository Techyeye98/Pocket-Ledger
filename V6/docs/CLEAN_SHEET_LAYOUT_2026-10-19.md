# Clean Sheet layout — 19 October 2026

The user requested consecutive useful columns, no technical rows in Transactions, and separate support storage. They explicitly chose app/Dashboard editing, with Transactions remaining a generated report.

- Raw sheet ID 0 renamed **Sync Support - Do not edit** and hidden. Stable IDs, custom columns, snapshot buffers and Canvas source identity remain intact.
- New **Transactions** (ID 425967320) has Date, Month, Time, Category, Amount, Payment Mode, Account, Remarks, Type, Spending Type and Personal amount in A:K, with no hidden columns between them.
- Only active entries appear; UNTAGGED displays blank. Empty history produces headers only. Current account balances remain in Dashboard.
- Backend uses support when present and regenerates the report after sync. `setupCleanTransactions` performs the idempotent migration. Updated bundled script/setup instructions are included in preview.14 (code 23); existing app versions work with this live layout.

## Evidence

- Backup created first: https://docs.google.com/spreadsheets/d/1BHFAykb5AEY09tu9spBb5BOlB0fyCoyhUid78Zxooro/edit
- Exact snapshot comparison with backup: **all 76 records unchanged**, including four active accounts and 63 deleted transaction markers. No synthetic records were written live.
- Saved Apps Script readback matched the tested source. Existing deployment updated to **Version 13, 19 October 2026 at 19:53 IST**, retaining URL/access settings.
- Migration completed at 19:55:14; only Transactions and Dashboard remain visible. Report readback contains exactly 11 headers, consistent with zero active entries.
- Dashboard's View data opened original sheet ID 0 under its new support name, confirming the source binding. That command unhides its source, so support was hidden again afterward.
- Clean-layout tests cover raw sheet/ledger preservation, eight repeated syncs without growth, updates in place, clearing, balances and stale-device resurrection prevention. Backend, Canvas, migration and report tests passed. Android build/lint passed: 0 errors, 40 existing warnings.
- Screenshot: `NativeExpenseEntry/app/build/sheet-upgrade/clean-transactions-layout-2026-10-19.png`.

The connected browser still cannot render Canvas; user verification of that rendering and actual phone sync remains outstanding. Transactions was visually checked at normal zoom.

Edit in the app or Dashboard. Clearing report cells does not delete history; the next sync rebuilds them. Use the app's clear-history flow to reset entries. Do not delete support storage or reinstall the app to clean the report.
