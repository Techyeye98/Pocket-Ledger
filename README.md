# Pocket Ledger

A local-first personal ledger for **Android**, with optional sync to a Google Sheet you own. Track accounts, income, spending, transfers and money owed; calculate an outing’s split in a separate bill-splitting tool. **iOS** has its own, simpler Apple Shortcut workflow for recording expenses in Sheets.

## Current version

**Android 6.1.0-preview.14** · version code **23** · Android **8.0+** · app name **Pocket Ledger**.

This README describes the current source and website downloads. The illustrated Android handbook is available as short web chapters and a linked 14-page PDF; the earlier expense-only PDF is retained as an archive. The Android app remains a debug-signed preview.

| Platform | What you get | Sheet template |
| --- | --- | --- |
| Android | Native offline ledger, accounts, people dues, widgets and standalone bill splitting | [Pocket Ledger — Android](https://docs.google.com/spreadsheets/d/1rxBf4afldt5WeRTu_Lxue1OeDSCDepbNpJz45OeTdAA/copy) |
| iOS | Apple Shortcut that sends expenses to its own Sheet | [Pocket Ledger — iOS](https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy) |

Start with the local [website](distribution/index.html), [Android APK](distribution/downloads/Pocket-Ledger.apk), or [iOS Shortcut](distribution/downloads/Pocket-Ledger-iPhone.shortcut). Use the matching platform’s Sheet and script. Android-only functionality below does not apply to the iOS Shortcut.

## What the Android app does

### Accounts and balances

Create bank, cash, wallet and credit-card accounts. Set an actual opening balance and its starting date; opening money is not income. Account balances apply financial movements from that starting point onward.

Home separates **available money**, **card dues** and **current net balance**, alongside period income and expenses. Date filters change period activity; current balances are a separate snapshot.

Connect payment names such as UPI or a debit card to the account they use. A payment label alone does not create an opening balance. Older expenses without account assignments remain visible and can be linked later. Configure default/last-used payment choices, reorder accounts, archive them, or record a balance adjustment with a reason. Archived accounts retain their balances and history; only unused accounts can be deleted.

### Financial entries

| Entry | How it works |
| --- | --- |
| Expense | Money spent, with an optional personal/friend allocation and Need / Want tag. |
| Income | Money received into an account, with configurable income categories. |
| Transfer | Moves money between accounts, including bank-to-cash movements or a credit-card payment. It is not another expense. |
| Shared expense | You pay the gross amount; only your allocation is personal spending, and friends’ allocations become receivables. |
| Lend / Borrow | Tracks money to receive/pay separately from everyday spending and income. |
| Friend paid | Your personal expense paid by someone else, creating an amount owed to them. |
| Repayment | Settles a specific original item and person, with partial repayments supported. |
| Merchant refund | Links to an expense; personal and friend refunded shares cannot exceed the original allocations. |
| Write-off / forgiven debt | Resolves an outstanding amount without pretending money moved between accounts. A receivable write-off counts as expense; forgiven payable counts as income. |
| Balance adjustment | Corrects an account balance with a required reason. |

**Example:** pay ₹900 from a bank account, allocate ₹300 to yourself and ₹600 to a friend. The bank falls by ₹900, personal spending is ₹300, and Lena / Dena shows ₹600 to receive. When the friend pays you back, the linked repayment settles that receivable without counting as salary.

Use **Lena / Dena** to see separate receive/pay totals, open items and linked history. Shared/linked entries should be edited in the app. Validation rejects inconsistent splits, excess refunds/repayments, invalid references and unsupported account routes.

### Everyday entry and review

- Quick expense entry with category, payment, date/time, optional note and optional Need / Want classification.
- A resizable home-screen widget and Quick Settings tile for expense entry; the widget opens a temporary entry sheet over the launcher.
- Home summaries, category charts and Needs / Wants / Untagged insights with entry drill-down.
- Searchable Activity with period, category, payment and spending-type filters, sorting, edit/delete, and quick tagging.
- Light, dark and system themes; currency, category/payment ordering and entry defaults; a Show / Hide control for recent entries.

Need / Want is a choice, not an automatic judgment. Transfers and friends’ shares are not personal shopping. Refunds reduce personal spending, while linked repayments resolve dues.

### Standalone bill splitting

Open **Home → Tools → Split bills**. Add or reuse saved friends, enter items, choose consumers and portions, confirm the tax percentage, and enter the final amount payable after discounts (including tax). Tax is calculated from the original item subtotal. Record multiple or partial payments and inspect reimbursements/settlements.

Import an image or scanned PDF for **on-device English text recognition**, then review the extracted items, totals and tax rate. Files are limited to 20 MB and five PDF pages. Manual entry remains available; recognition can make mistakes and does not replace your review. There is no built-in camera-capture flow.

**Split bills and saved split friends are separate from the financial ledger.** They do not automatically post expenses or affect account balances, income/spending reports or Lena / Dena. They stay on the current phone and are excluded from JSON export/import, Sheet sync, Android cloud backup and device transfer. Uninstalling or clearing app data can lose them.

## Getting started on Android

1. Install the APK and open Pocket Ledger. Offline tracking does not require a Google Sheet or a Pocket Ledger account.
2. Open Accounts and add the balances you actually have, with correct opening dates. Link payment methods to accounts.
3. Add expenses/income; use More for transfers and lending. Use Lena / Dena for linked settlements.
4. Review Home and Activity. Use Split bills separately when you need an item-by-item calculation.
5. Export a JSON backup before changing phones or resetting anything. Add optional Sheets sync when ready.

### Installation and migration

The current package is `com.pocketledger.v6.preview`. The staged APK is **debug-signed**. It updates a preview installed with the same signing certificate, and remains separate from the older release-signed Android app. Do not uninstall your only copy of the ledger to update.

For migration from the older app, export a JSON backup, keep that app installed, import into the current preview, and verify balances/history. Device-specific configuration, including the Web App URL, must be reconnected. Separate bill-splitting data is not portable through the financial backup.

## Optional Android Google Sheets setup

Skip this if you only want offline use.

1. [Copy Pocket Ledger — Android](https://docs.google.com/spreadsheets/d/1rxBf4afldt5WeRTu_Lxue1OeDSCDepbNpJz45OeTdAA/copy) into your own Drive. The copy link is a template, not the app’s sync URL.
2. In your Sheet’s desktop browser choose **Extensions → Apps Script**. Save the full [Android script](V6/Backend/AppsScript.gs), also included as [a website download](distribution/apps-script/Pocket-Ledger-Apps-Script.gs.txt).
3. Deploy as a **Web app**, executing as yourself, with access set to **Anyone** for the chosen private-URL workflow. Complete Google’s authorization. For an existing deployment use Manage deployments → Edit → New version → Deploy; retain its existing `/exec` URL.
4. Run **setupPocketLedger**. For the clean layout, also run **setupCleanTransactions** after updating the deployment. Back up an existing workbook before migrating it.
5. App → **Settings → Sync & Sheets → Web App URL**: paste the deployment URL ending in `/exec`, then **Test Connection → Sync Now → Refresh Sheet dashboard**.

**Keep the deployment URL private:** anyone with it can call the Web app to access the synced ledger. This access model is different from sharing the Sheet template. The URL is deliberately excluded from JSON backups.

### What sync actually does

The phone’s Room database stores entries first. With a URL configured, saving an entry can attempt sync; **Sync Now** manually sends the complete financial ledger, including accounts, payment routes, people and transaction/deletion records. The server merges stable IDs and timestamps. A new empty Sheet can receive previously synced records too. There is no scheduled background sync.

Failed requests retain local data for retry. **Refresh Sheet dashboard** rebuilds projections from the already-synced ledger; it does not upload the phone. **Restore Data from Sheet** merges the financial ledger into the phone and is not a substitute for preferences backup.

### Clean workbook layout

| Tab | Purpose |
| --- | --- |
| Transactions | Generated report of active entries, with consecutive Date, Month, Time, Category, Amount, Payment Mode, Account, Remarks, Type, Spending Type and Personal amount columns. Unclassified tags display blank. |
| Dashboard | Existing Canvas with period spending/income and current account/people snapshots. |
| Sync Support - Do not edit | Hidden original data sheet containing IDs, account/person snapshots, deletion markers and snapshot buffers. Its identity remains stable so Canvas retains its source. |

Edit entries in the **app or Dashboard**, not the generated report. Clearing report cells does not delete history; sync recreates the report. Do not delete hidden support storage. Canvas’s View data command may unhide that source; hide it again when finished. Repeated sync with the same IDs reuses rows; reinstalling and recreating accounts produces new IDs.

### Fresh start and common sync problems

Use **Clear transaction history** in the app to clear financial entries. Accounts, payment links, opening balances, preferences and separate split bills remain. Balances return to opening amounts and people dues become zero. Set today’s actual opening balances afterward, then sync. Deletion markers prevent stale history from returning.

If `Amount must be positive` appears after manually clearing visible Sheet cells, update the backend/deployment: current code ignores metadata-only leftovers instead of treating them as zero-amount expenses. Active zero-amount expenses remain invalid. Uninstalling the app or deleting support data is not the repair.

If the Sheet is empty but phone balances are correct, use **Sync Now**, then **Refresh Sheet dashboard**. For a dashboard warning after successful sync, keep the local ledger and update the script before retrying the refresh. See [backend setup](V6/Backend/CANVAS-DEPLOY.md).

## Backup and restore

**Export Backup** produces financial JSON containing accounts, payment routes, people, transactions and preferences, including theme/currency and category/payment configuration. Current exports use schema 3; older supported backups and earlier preview schema-4 financial data remain importable. Import merges stable records and preserves newer local data rather than blindly wiping the database.

The Web App URL, verified connection, sync timestamps and installation identifier are device-specific. Split bills and saved split friends are excluded. **Export as Text** is a readable copy, not a restorable backup. Keep financial backup files private and check an import before deleting the original.

## iOS: separate Shortcut workflow

[Copy Pocket Ledger — iOS](https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy), use the [supplied Shortcut](distribution/downloads/Pocket-Ledger-iPhone.shortcut), and follow the existing [iOS setup PDF](distribution/guides/iPhone/iPhone-Shortcut-Setup-Guide.pdf). The matching [iOS script](distribution/apps-script/Pocket-Ledger-iOS.gs.txt) retains the original expense-to-Sheet workflow.

The native account ledger, offline Room storage, income/transfers, people settlements, widgets and standalone bill splitter described above belong to Android. Do not connect the iOS Shortcut to Android’s rich-ledger backend or use one platform’s setup guide as the other’s.

## Local website and guide status

`distribution/` is the current website package. The restored neon-trace design uses locally hosted Lenis, GSAP and variable fonts. The homepage includes spending and feature highlights, a shared-payment example, a short data summary and direct Android/iOS downloads. Separate Features, Your data, Download and Guide pages hold detailed explanations, setup and FAQs. The original animated guide-book opens the current HTML guide. A Motion control complements device reduced-motion preferences. See [design direction](DESIGN.md). It now describes the full Android ledger, separate iOS workflow, matching Sheet/script links, sync behavior and backup limits. Its APK and Android script are staged locally from the current build/source. No Git push or website deployment is part of this update.

The current [illustrated Android PDF](distribution/guides/Pocket-Ledger-Guide-2026-10.pdf) covers accounts, entries, Lena / Dena, item-level bill splitting, payments, OCR review, Sheets and backups. The [guide hub](distribution/guide.html) links nine task chapters using real screenshots with demo data and editable native callouts. The earlier expense/widget PDF remains an archive. The iOS guide/Shortcut remain unchanged. `website/experiments/v6/` is an older design experiment, not the current publication package.

## Repository map

| Location | Purpose |
| --- | --- |
| `V6/NativeExpenseEntry/` | Current native Android Java/Room app and bundled backend |
| `V6/Backend/` | Canonical Android Apps Script, maintained Canvas source and deployment notes |
| `V6/tests/` | Money/ledger, backup, split/receipt, sync, Canvas and clean-Sheet regressions |
| `V6/docs/` | Change-specific verification and implementation notes |
| `distribution/` | Local website, staged downloads, platform scripts and existing PDFs |
| `tools/site-smoke/` | Static content checks and optional browser smoke harness |
| `tools/guide/`, `V6/guide-captures/` | Earlier guide authoring and private captures; guide work is deferred |
| `website/experiments/v6/` | Historical design preview |

## Development and validation

Install a compatible JDK (17+) and Android SDK API 36. Keep local paths/signing credentials outside committed files.

```powershell
cd V6/NativeExpenseEntry
.\gradlew.bat :app:assembleDebug :app:lintDebug --offline
```

APK output: `V6/NativeExpenseEntry/app/build/outputs/apk/debug/app-debug.apk`. Release signing needs the original local credentials. Never store signing keys/passwords, personal deployment URLs or private captures in the website package.

From the repository root:

```powershell
node V6/tests/needs-wants.test.cjs
node V6/tests/ledger-backend.test.cjs
node V6/tests/sheet-reports.test.cjs
node V6/tests/canvas-regressions.test.cjs
node V6/tests/clean-sheet-layout.test.cjs
.\V6\tests\run-ledger-tests.ps1
node tools/site-smoke/content-check.cjs
node tools/site-smoke/v6-publication.cjs --serve
```

The last command previews the actual website package at `http://127.0.0.1:4829/Pocket-Ledger/`. Browser verification uses the connected in-app browser; the preview server supports opt-in local axe diagnostics. The site has no build step; keyboard navigation, both themes and reduced-motion preferences are supported.

Source/local regression results do not establish every real-device interaction, receipt-recognition result or live Canvas rendering. Current backend/layout verification notes are in `V6/docs/`. GitHub Pages configuration points to `distribution/`, but publishing is a separate action.
