# Ledger preview: implementation and verification

## Current changes · 18 October 2026

Preview.10 (code 19) removes split-bill/friend export and restore, and excludes the separate split database plus its journals from Android cloud backup and device transfer. Existing local split data is preserved. Financial exports use schema 3, with schema 4 financial-only import compatibility. Build/lint and all ledger/split/receipt/financial backup regression checks pass; physical-device backup behavior is not verified. The previous report-tab design below was undone. The user's existing canvas is now edited through its native canvas editor, preserving its sheet ID and two-tab structure. A separate native Drive copy retains the original canvas. Account/people sections show missing-sync states until actual financial source data is present.

Preview version: 6.1.0-preview.9 (version code 18). Public download is unchanged. The user's supplied Sheet now has four new managed PL report tabs; the original two tabs are preserved. Apps Script deployment activation is tracked separately below.

## Complete backup and current Sheet reports · 17 October 2026

- JSON format 4 adds split bills and saved friends without connecting splitting to the financial ledger. Versions 1–3 remain readable. Strict split validation precedes imports; IDs/timestamps support idempotent merging and preservation of newer local data. Split SQLite v2→v3 adds saved-friend timestamps without changing existing IDs/names. A partial storage failure is reported accurately; separate ledger/split databases cannot provide one atomic transaction. Old backups can restore physically deleted split records.
- Apps Script derives PL Dashboard / PL Activity / PL Accounts / PL People from the committed ledger. Current account/card and person balances are separate from date-filtered income/spending. Personal shares, refunds, opening cutoffs, transfers and repayment exclusions match the app calculations. Managed-tab ownership prevents overwriting unrelated sheets. Literal strings and category wildcard criteria are protected.
- The app bundles the same backend source, offers current setup/redeployment instructions and Refresh Sheet dashboard. A dashboard failure after a ledger commit is shown as a report warning, with the financial save still confirmed.
- Live Sheet verification: 62 legacy records preserved exactly. September spending ₹22,962.02; October spending ₹8,415.35; total ₹31,377.37. Date-filter recalculation was checked and restored to current-month defaults. No account balances were fabricated; the report explains the unassigned records. Two charts are present with two/one series respectively. Exported layout and live browser summary were visually inspected.
- Passed: offline Android assembleDebug + lintDebug (0 errors, existing warnings), ledger/spending/split/receipt/backup JVM regressions, backend commit/restore/failure tests, Need/Want migration/backend/asset tests, report-model/chart/format/ownership/formula-safety tests and actual SQLite saved-friend ALTER migration.
- Not yet verified on a physical phone: v4 export/import through Android's file picker, full SQLite restore transaction behavior, new dashboard refresh against a deployed user endpoint, and real receipt OCR. Existing published APK/website remains untouched. See [Sheet upgrade](SHEETS-UPGRADE.md).
- The Sheet's bound Apps Script project was initially blank. The tested backend is now saved there under the project name Pocket Ledger, with `@OnlyCurrentDoc` to limit spreadsheet authorization. Running setup reached Google's authorization step; a deployed endpoint has not yet been activated. Do not assume the old app URL points at this newly configured bound project.

## Explicit tax percentage · 17 October 2026

- Manual totals and uploaded-bill review use No tax (0%) / 5% buttons plus an exact percentage field. These are convenience presets, not a claim that other rates cannot apply. The user confirms a rate; no OCR-derived tax amount is imported. Tax is recomputed from the original selected item subtotal, with an inline amount preview.
- Uploaded receipt review preserves the chosen rate across draft serialization and recomputes the tax preview when selected items change. Existing saved bills retain their prior tax calculations; legacy amount-based tax is converted for editing only when the inferred percentage reproduces the same amount exactly.
- Final amount has compact helper copy in both entry paths: the whole bill paid/payable to the restaurant/seller/business after discounts, including tax; individual/partial payments are recorded next.
- Tests cover 0%, 5%, fractional rates, invalid rates and a confirmed rate overriding a deliberately incorrect OCR amount. Build/lint and regression tests are run; phone interaction/OCR verification is still outstanding.

## Dedicated Home tool · 17 October 2026

Split bills now opens from Home → Tools → Split bills, in its own full-width button below the balance/account summary and before the period filters. A custom receipt-and-branch icon replaces the categories/payment filter icon. It is removed from Settings and the expense entry More menu. The button follows V6 surfaces, icon geometry, press feedback and themes. Calculation/storage behavior is unchanged. Android build/lint are checked; actual phone layout remains unverified.

## Simpler totals, saved friends and V6 UI · 17 October 2026

- Final bill amount replaces discount entry in both manual and uploaded-bill review. The user enters the whole bill’s final payable including tax, independently of partial/individual payments. Discount is derived as items + tax − final amount and allocated exactly across participants. Blank final amount on manual entry means no discount. Final amounts above items + tax are rejected for review.
- Removed tip/additional-charge and round-off input controls. Existing saved bills retain their original calculations when viewed; editing their totals uses the simpler flow. Older JSON records remain readable.
- Split database version 2 adds a separate saved-friends roster. The upgrade seeds distinct names from existing split bills, excluding You, without rewriting historical bills. Add/rename/remove saved friends from the splitter, then select attendees for each outing. Removing a saved name does not delete previous bill participants or payment records. Names are normalized for duplicate checks; existing item/payment dependencies prevent unsafe participant removal.
- The UI extends the V6 palette/components with statement bill cards, status badges, rounded back navigation, step progress, selected friend surfaces, filled fields, a prominent final amount, live discount preview, grouped receipt rows and result summary. Storage details move behind an About action.
- Regression checks cover inferred discounts, final totals below tax, zero/full-price bills, conservation over randomized cases, legacy records and friend-name normalization. SQLite upgrade and native visual/interaction checks still require a phone/emulator; no new on-device verification is claimed.

## Upload bill follow-up · 17 October 2026

- In the Items step, Upload bill opens the Android file picker for JPG/PNG/WebP images or PDF scans. Manual entry remains available. No camera permission or server receipt-processing endpoint is added.
- Google's bundled Latin text-recognition model reads printed English receipt text/digits locally. Scanned PDFs render on-device through Android PdfRenderer. Inputs are limited to 20 MB and five PDF pages, with bounded image decoding, orientation correction and temporary-file cleanup. Back cancels applying an in-flight result.
- Review uploaded bill shows selectable/editable proposed item rows, editable tax/discount/charges/round-off and final total. The original file can be opened in a viewer; recognized text can be inspected/corrected and parsed again. OCR prices are proposals, not confirmed financial records.
- The importer checks that selected line totals plus charges minus discounts match the reviewed receipt total when supplied. Existing items are replaced only after confirmation; imported items have no assumed consumers. Choose who shared each item before continuing.
- Imported charges use receipt amounts. Manually entered percentage tax still uses the original food subtotal. Explicit signed round-off supports adjustments up to 1.00 either way, with exact per-person allocation. The optional receipt total is retained with the split and checked before saving; older saved splits remain readable.
- Receipt extraction tests cover quantity/rate/amount columns, wrapped names, GST components plus a duplicate total-tax summary, rate-only tax lines, metadata exclusion, Indian digit grouping, round-off and draft serialization. Actual OCR accuracy, PDF/image picker behavior, rotation/cancellation and keyboard review need phone testing. No real user receipt was supplied for end-to-end validation.

## Standalone manual bill splitter · 17 October 2026

This is an additional calculator/history tool, deliberately separate from accounts, expense/income, people debts and financial reports. No existing ledger schema, backup format or Sheets backend is changed for splitting.

- Open Home → Tools → Split bills.
- Name the bill and add friends. Enter item line totals, select consumers and optionally change their portion weights. Amounts and allocations use exact paise with deterministic largest-remainder rounding.
- Enter a discount and tax as amounts or percentages, plus optional tip/additional charges. Percentage tax uses the original food subtotal, as requested: food 1,000 + tax 50 − discount 100 = final bill 950. Charges/discounts are proportional to food consumption; leave tax blank when prices already include it.
- Record actual payments by one or multiple participants. Partial restaurant payments leave an explicit restaurant due; friend transfers never reduce that due. Reimbursement suggestions appear only after the restaurant is fully paid.
- Save, reopen, review individual breakdowns, record partial friend payments, undo payment records, share a text summary or delete the bill. Editing shares after friend payments requires undoing those records first.
- Bills persist in a separate local SQLite database. This initial manual feature has no online collaboration or ledger backup/Sheets integration. The tool explicitly discloses its device-only storage. File-based OCR was added in the follow-up above.

Verification covers the corrected 950 example (shares 285 / 285 / 380), weighted/fractional portions, stable paise allocation, zero bill, tax amount override, partial/multiple payers, friend advances, settlement conservation, JSON round-trip, overflow-safe allocation and invalid input. Phone UI, keyboard and lifecycle behavior still require device testing.

## Switching and motion follow-up · 17 October 2026

The supplied recording shows an empty Activity list at approximately 4.75 seconds, followed by populated entries, and Home/Activity state resetting during navigation. Source inspection found whole-screen replacement followed by asynchronous data hydration and redundant form renders.

- Home and Activity retain their prepared views and scroll/chart state. A cached ledger snapshot fills each view before it is presented; unchanged resume loads do not rebuild it.
- Filters operate on the loaded snapshot without a transition. Request revisions reject superseded background results during rapid navigation.
- The window and bottom navigation stay mounted. Route changes use a cancellable 160 ms fade with 8 dp movement and an ease-out curve; form mode changes use a 120 ms fade. Interrupted transitions settle immediately, and the system's disabled-animation preference is respected.
- In-app Expense and Income share one activity. Form changes render once, preserving draft fields, focus and cursor where applicable. The widget still opens its direct expense sheet.
- Cached ledger copies isolate mutable form data from Home. Import, sync and foreground refresh invalidate stale snapshots.

Debug assembly, Android lint and the JVM ledger/insights regression tests pass. Added checks cover stale-request rejection and independent presentation copies with unchanged balances. Actual phone frame pacing, keyboard behavior and perceived smoothness remain unverified because no usable device/emulator is available.

## V6 UI follow-up · 16 October 2026

The supplied phone screenshots exposed unstyled dropdowns, a second oversized Home summary, raw operation names and an overcrowded Activity filter area. This pass preserves the original `Design.java` colour tokens and extends the V6 components:

- Home uses one statement card. With accounts configured it shows the current balance; period income/spending appears beneath the date selector. Before account setup the original spending statement stays visible, with a compact setup action instead of a misleading zero balance.
- Styled account/category/person fields open themed choice dialogs. Operation labels use normal words; the new entry form shares the Expense / Income / More controls with quick entry.
- Amount entry, filled text fields, primary save footer, back navigation, empty states and loading states follow the existing V6 language in light and dark themes.
- Accounts and people have summaries and separate activity sections. Infrequent management actions sit behind account/person settings.
- Activity has selected entry-type chips and a collapsible filter panel for category, payment, spending tag, account and person.
- The widget retains its direct expense-entry sheet. Switching from in-app quick entry carries the entered amount and note into the advanced form.

The isolated emulator could not boot: the installed system-image directory is missing `system.img`. This pass therefore uses the user's screenshots, source comparison, compilation/lint and calculation regression tests; it does not claim a new on-device visual verification.

## Implemented

- Custom Bank, Cash, Wallet and Credit card accounts; starting balances with timestamp cutoffs; rename, order, archive/unarchive and unused-account deletion.
- Overall and individual account views. Available money, card dues, card credit, current net balance, period income/net change, and separate money owed between people.
- One payment picker linked by account IDs, optional aliases such as SBI UPI, explicit legacy payment mapping, account default and last selection.
- Widget, shortcut and Quick Settings tile still open expense entry directly. Income and advanced actions are available inside the app.
- Income categories separate from expense categories, with category management.
- Atomic account transfers and credit card payments, without counting the movement as another expense/income.
- Shared expenses with multiple named friends, purchases entirely for friends, lending, borrowing, friend-paid expenses, partial repayments, debt write-off and forgiveness.
- Linked merchant refunds, including explicit personal/friend allocation; dependent edit/deletion safeguards.
- Exact minor-unit amounts, balance recomputation, personal-spending reports, refund-aware category/Need-Want summaries, Activity operation/account/person filters and text export.
- Room schema 1/2 -> 3 migration; old records remain expenses and retain raw legacy amount/metadata. HALF_UP conversion applies when old amounts have more than two decimals; Home reports that rounding.
- Backup schema 3 covers accounts, payment routes, people, transactions, linked repayments/refunds, income categories and portable account preferences. Version 1/2 backups remain readable. Whole-graph validation occurs before a merged import commits.
- Accounts/people-aware Sheets schema and capability checks; whole-ledger push/restore; stable IDs and tombstones; staged records with a publication pointer, recovery from incomplete writes, legacy-client write gating, and cleanup of older staged generations.

## Where to try it

- Home -> Set up balances / Accounts: create accounts and starting balances.
- Account -> Set as default payment or Add payment alias.
- Accounts -> Connect old payment methods: explicitly map existing labels to an account. The preview shows affected entries; entries before the starting timestamp are not deducted twice.
- Main Add -> Expense or Income. More actions opens split/date changes, transfers/card payment, lending/borrowing, and accounts.
- Home -> To receive / To pay: people, open items, partial repayments, and history.
- Activity -> an entry: detail, edit, refund, or delete.
- Settings -> Sync & Sheets -> Ledger sync setup -> Copy updated script: paste into the user's own Apps Script project and deploy an updated web app before testing sync. The complete script is also `V6/Backend/AppsScript.gs`.

The original Transactions tab is retained. Rich records live in Ledger Records; Ledger Metadata points to the last fully staged snapshot. Once enabled, older clients are rejected for writing/restoring this Sheet to avoid dropping ledger fields. A real hosted deployment was not modified or tested by this task.

## Automated checks

Run from the repository root after building:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = "$env:USERPROFILE/AppData/Local/Android/Sdk"
./V6/NativeExpenseEntry/gradlew.bat -p V6/NativeExpenseEntry :app:assembleDebug :app:lintDebug --offline
./V6/tests/run-ledger-tests.ps1 -Sdk $env:ANDROID_HOME
node V6/tests/needs-wants.test.cjs
node V6/tests/ledger-backend.test.cjs
```

The JVM tests use the actual app calculation/serialization classes and the installed Android SDK's JSON source implementation, with platform-only annotations stripped in ignored build output. No new app dependency is introduced. SDK sources are needed for that test runner. SQLite checks exercise migration SQL and compare its transaction columns with Room's generated fresh schema.

Verified scenarios include exact decimal money, legacy rounding, the complete salary/ATM/dinner/repayment/card example, opening timestamp boundaries, personal shares, partial repayments, borrowing, friend-paid expenses, write-offs, refunds, backup parsing/serialization, duplicate identities, invalid references, over-settlement, idempotent sync/import, interrupted backend writes and legacy-client rejection. The example ends with SBI 25,800, Cash 2,800, card dues 500, and net balance 28,100.

## Device verification still required

No Android device or configured emulator was available. Compilation and lint are not a substitute for these checks:

1. Export a backup from the installed app before installing this preview. A schema-3 database cannot be opened by the older app; retain the old backup for rollback.
2. Upgrade a populated installation and verify mapping/opening balances, preserving IDs, categories, tags and notes.
3. Exercise light/dark themes, small screens, large text, native pickers, back navigation and rotation with an unsaved draft.
4. Test widget save/close returning to the launcher, tile/shortcut entry, and capture before account setup.
5. Export/import on a fresh installation and verify accounts, linked operations, people totals and preferences.
6. Redeploy the user's own Apps Script and test live sync/restore, offline retry, and simultaneous edits. Automated backend tests use an in-memory Sheet simulation.

This is a testable preview, not a production release or a claim of completed device UAT. The previously agreed deferred features (bank connections, multi-currency, billing cycles, recurring entries and reminders) remain out of scope.
