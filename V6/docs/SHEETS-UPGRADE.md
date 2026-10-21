# Accounts, income and dashboard upgrade

Use the same Google Sheet and existing Web App deployment. Export a JSON backup in the app before upgrading. The app remains the editor and primary store; Sheet reports are derived views.

## One-time script update

1. Open your Sheet on desktop → Extensions → Apps Script.
2. Replace its backend code with `V6/Backend/AppsScript.gs`, or use the app's **Settings → Sync & Sheets → Sheet & dashboard setup → Copy updated script**. Save.
3. Run `setupPocketLedger` once and authorize access to your Sheet. This creates the four managed `PL ...` tabs. The original `Transactions` and `Dashboard` tabs remain intact. If an unrelated tab already uses one of the new names, setup refuses to overwrite it; rename that tab first.
4. Choose **Deploy → Manage deployments → Edit → New version → Deploy**. Keep the existing `/exec` URL and existing access settings. Merely saving code does not update the deployed Web App. For a first deployment, use Web app / Execute as Me / Anyone, and keep its URL private.
5. In the updated APK, run **Test Connection**. It should confirm ledger sync and dashboard support. Then use **Sync Now**. A successful ledger sync automatically rebuilds reports. **Refresh Sheet dashboard** rebuilds from the last committed Sheet snapshot without importing data from report cells.

## Account setup without double-counting

- Add real bank, wallet, cash and card accounts in the app. Link payment methods to the account they draw from. A payment method and an account are different: UPI can point to SBI; another UPI route can point to ICICI.
- Enter each account's actual opening balance and its start date/time. For a credit card, an outstanding amount is a negative balance. Current balances include movements at or after this starting point. Earlier expenses remain in spending reports without being deducted from today's opening balance again.
- Assign older unassigned expenses where their source is known. Do not invent account balances from old expense-only records. Until setup is complete, dashboard balances may be incomplete even though spending totals are available.
- Overall net balance sums account balances, including negative cards. Available money excludes cards; card dues and positive card credit are separate. People receivables/payables are shown separately from account balances.

## Report tabs

| Tab | Content |
| --- | --- |
| PL Dashboard | Current net/available/card balances; date-filtered income, personal spending and net change; current people dues; monthly trend, categories and Need/Want/Untagged |
| PL Activity | Synced financial entries, source/destination account, personal spending, income, payment route and notes |
| PL Accounts | Opening balance, movements since opening, current balance, card dues/credit and archive state |
| PL People | Current receivables/payables and person IDs |

Yellow **From / To** cells control period reports. Current balances and people dues do not change with those filters. Transfers and repayments do not count as income/spending. Shared expenses count only your personal share; refunds reverse personal spending and retain the original Need/Want tag. Borrowing/lending affect balances and dues, with settlement tracked against the original entry.

`Ledger Records` and `Ledger Metadata` store the committed sync snapshot after rich sync is first used. Do not edit those raw tabs or the generated report data. Once ledger sync is active, older expense-only clients cannot write into the same Sheet.

If report refresh fails after a ledger commit, the app reports **Data synced · dashboard needs attention**. Your ledger is committed; correct the report issue and retry Refresh Sheet dashboard. A failed report refresh does not falsely mark the financial save as failed.

## Complete portable backup

JSON backup version 4 contains financial accounts/routes/people/entries, settings, income categories, split bills and the reusable saved-friends roster. Split bills remain independent of income, expenses, balances and Sheet sync. Their participants, allocations, original tax, final paid total and payments are preserved.

Import validates the entire file before writing. Ledger and split records merge by stable ID and timestamp, preserving newer local records. Duplicate saved-friend names are retained locally rather than multiplied. Versions 1–3 remain importable and keep existing split data untouched. Ledger and splitter use separate databases: if one restore step encounters a storage error, the app reports partial completion and supports retry; the entire import is not one cross-database transaction. Deleted split records have no tombstones, so importing an older backup can bring back a bill/friend that was deleted later.

The private Web App URL is not in backups. Reconnect it on a new device. The receipt image/PDF itself is not embedded; saved reviewed bill data is included.
