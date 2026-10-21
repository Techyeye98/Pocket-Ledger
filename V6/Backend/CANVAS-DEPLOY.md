# Current: clean Transactions + hidden sync support

The user's live workbook now has visible **Transactions** and **Dashboard** tabs, with a hidden **Sync Support - Do not edit** sheet. This replaces the earlier preference for keeping raw sync data inside Transactions.

Transactions is a generated report in contiguous A:K columns: Date, Month, Time, Category, Amount, Payment Mode, Account, Remarks, Type, Spending Type and Personal amount. Only active entries appear; untagged values display blank. Edit entries in the app or Dashboard.

For migration, back up the workbook, save this script, update the existing Web App deployment to a new version, then run `setupCleanTransactions`. It preserves the original raw sheet ID (and Canvas binding), creates the clean report and hides support. Re-running is safe. Once support exists, backend sync/restore uses it automatically and refreshes Transactions after sync. `setupPocketLedger` also remains compatible.

The support sheet retains account/person snapshots, deletion IDs and committed snapshot buffers. Do not clear it or rebind Canvas to the clean report. Canvas's View data command opens its raw source and can unhide support; hide that tab again afterward if desired.

Live deployment: **Version 13, 19 October 2026 at 19:53 IST**, same endpoint/access settings. All 76 committed records were unchanged by migration. Run `node V6/tests/clean-sheet-layout.test.cjs` to verify repeated sync, updates, cleared history, balances and stable sheet identity. See `../docs/CLEAN_SHEET_LAYOUT_2026-10-19.md` for evidence.

## Previous layout instructions (before clean-report migration)

Use `AppsScript.gs` in this folder. It updates only the existing `Transactions` tab; it does not create, clear or redesign the `Dashboard` Canvas, or create report tabs. Split bills and saved split friends are not sent to Sheets.

1. Open your Sheet → Extensions → Apps Script. Replace the script with the complete contents of `AppsScript.gs`, and Save.
2. Select `setupPocketLedger` → Run, and authorize if asked. It preserves existing transaction IDs and adds the income/account/person fields plus hidden sync storage columns in `Transactions`.
3. If this project already has the app's deployment: Deploy → Manage deployments → Edit → New version → Deploy. The existing `/exec` URL stays the same.
4. If this project has no deployment: Deploy → New deployment → Web app → Execute as Me → Anyone → Deploy. Paste this new `/exec` URL into the app. Keep this URL private: anyone with it can read/write the synced ledger.
5. App → Test Connection → Sync Now. Set up opening balances, accounts and payment-method connections in the app. The existing Canvas then receives actual balances and people dues; payment-method names alone do not establish opening balances.

Balances and people dues are current snapshots; date filters affect income/spending. Edit account settings and shared/linked transactions in the app. Canvas transaction changes must retain their IDs and update timestamps. After rich sync starts, older expense-only app clients are blocked from overwriting the ledger.

The updated script has been tested locally against the saved Canvas calculation code. It has not been deployed or run on the live Sheet as part of this check.

## Canvas review fixes (19 October 2026)

`Canvas.jsx` is the maintained dashboard source, based on the saved original Canvas. Replace the existing Canvas component source with this file through its editor, keeping its existing `Transactions` data range. Updating Apps Script alone does not update the Canvas component.

This version keeps personal amounts consistent when adding/editing ordinary expenses, rejects invalid amounts/dates, and directs shared, linked and advanced entry edits to the app. Category and payment breakdowns both show personal spending less personal refunds. Negative category totals use the ranked list because a donut cannot represent them. Account/person snapshots use stable IDs, with a name fallback for older rows lacking IDs.

Run `node V6/tests/canvas-regressions.test.cjs` from the repository root to check the Canvas handlers against the actual backend with in-memory Sheets. These checks do not access the live Sheet.

The existing live Dashboard was updated through integrated Gemini on 19 October at the user's request. Its generated source was read back and passed the Canvas/backend regression cases; it is not byte-identical to this maintained file. Sheets confirmed Saved to Drive and retained the Dashboard sheet ID and two-tab structure. The user will verify the rendered Canvas, which remains blank in the connected browser. See `../docs/CODE_REVIEW_FIXES_2026-10-19.md` for saved evidence.
