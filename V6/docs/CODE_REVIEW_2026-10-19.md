# Pocket Ledger code review — 19 October 2026

**Verdict: request changes before calling this build final-ready.**

Baseline: Android `6.1.0-preview.12` / versionCode 21, the current Apps Script source and bundled copy, and the saved source of the original Sheets Canvas (`canvas-after.jsx`). This is a code and local-test review. The live Canvas source was not fetched again during this review. No app source, live Sheet, deployment, or financial records were changed during the review.

## Findings, in priority order

### 1. [P1] Changing an expense amount in Canvas can block all ledger sync

Location: saved Canvas `handleSaveEdit`, lines 1103–1116; `Backend/AppsScript.gs:815` and `:717`.

The editor writes Amount and Updated At but preserves the old Personal amount. The server imports both and rejects expenses whose personal share plus friend shares no longer equals Amount. `ledgerRead()` applies these edits before merging an app request, so both fetch and push fail until the Sheet row is repaired.

Reproduced using the actual Canvas handler and actual backend with mocked Sheets: edit an ordinary ₹500 expense to ₹600. Personal amount remains ₹500. `LEDGER_FETCH` returns `Shares must equal the total`.

Fix: keep personal amounts consistent for simple expense edits, and require a supported allocation editor or app editing for shared/linked entries. Validate edits before saving them to the Sheet. Add a Canvas-edit-to-backend round-trip test.

### 2. [P2] Category/payment charts include income, transfers and gross friend shares

Location: saved Canvas `categoryChartData`, lines 930–939, and `paymentChartData`, lines 943–952.

Both aggregate every filtered transaction's gross `amount`, while the main spending metric uses personal expense/refund rules. Income, transfers and loans can therefore appear in the spending breakdown.

Reproduced: ₹300 shared expense with ₹100 personal share, ₹10,000 income and ₹5,000 transfer produces personal spending ₹100 but category chart total ₹15,300.

Fix: give spending charts a common signed personal-spending calculation; define payment-volume semantics explicitly and exclude unrelated transaction kinds. Cover mixed-kind and refund fixtures in chart tests.

### 3. [P2] Same-name accounts lose balances in Canvas

Location: saved Canvas account snapshot map, lines 515–524. The native app explicitly supports duplicate account names (`Ledger.accountName`).

The Canvas groups snapshots by display name instead of account ID, retaining only the latest row. Valid separate accounts can disappear from the overall balance.

Reproduced: two accounts named Cash holding ₹2,000 and ₹1,000 display as one account with net ₹1,000 rather than ₹3,000.

Fix: map snapshots by Account ID, retain names as labels, and use a compatibility fallback for older rows. Apply the same identity rule to people snapshots.

### 4. [P2] Adding a person inside an entry clears the unfinished entry

Location: `LedgerActivity.java:61`, `:277`, `:358`, `:368`.

Inline Add person captures the entry draft, saves the person and calls `reload()`. The successful-save path sets `applyingSavedState=true`; its special reload branch then clears `draft` before rendering. Entered amount, note, payment choice and split allocations can reset while the user is still creating an expense or loan.

Confirmed by tracing the shared save/reload path; not exercised on a physical device.

Fix: preserve/capture the entry draft when refreshing dependent people choices. Clear it only when the entry itself is successfully saved or explicitly discarded. Add an inline-person creation UI test.

### 5. [P2] System Back bypasses advanced-screen navigation and split draft handling

Location: `BillSplitActivity.java:135–141`; `LedgerActivity` toolbar Back handler around line 85; manifest and `targetSdk 36`.

Bill splitting relies on deprecated `onBackPressed()`. On Android 16 with this target, that callback is no longer invoked, so system Back can finish the activity instead of returning to the preceding step or asking before discarding a draft. LedgerActivity also lacks a shared system-Back handler for its internal account/person routes. MainActivity and QuickEntryActivity already register modern callbacks.

Platform behavior verified against [Android's API 36 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16#predictive-back). Device behavior still needs testing.

Fix: route supported platform callbacks and legacy Back through the same navigation/draft handler used by the toolbar. Verify both gestures and three-button navigation.

## Checks completed

| Area | Evidence |
|---|---|
| Current Android build | `:app:assembleDebug :app:lintDebug --offline` passed; lint reports 0 errors and 40 warnings |
| Exact money, balances, lending, repayments, refunds and clear history | JVM regression suite passed |
| Split calculations, original-subtotal tax, settlements, rounding | BillSplitTest passed |
| Receipt parsing and review serialization | ReceiptParserTest passed; actual image recognition was not run |
| Financial backup v3/v4, split-data exclusion | BackupCompatibilityTest passed; backup exclusion XML reviewed |
| Schema migrations, stable IDs, older clients, bundled script parity | `needs-wants.test.cjs` passed |
| Server merge/restore, interrupted writes, same-tab projection, cleared history | `ledger-backend.test.cjs` passed |
| Financial summaries, refund tags, opening cutoffs, saved-friend migration | `sheet-reports.test.cjs` passed |
| Canvas integration counterexamples | Three additional local probes reproduced findings 1–3 against saved Canvas and actual server code |

Probe: `NativeExpenseEntry/app/build/review-2026-10-19/probes.cjs`. It uses synthetic data and in-memory Sheets; it does not call the live Sheet.

Reviewed flow areas include widget/quick entry, advanced entries, accounts/payment routes, people and linked settlements, dashboard/activity summaries, reset, backend merge/storage/projection, imports/exports, split storage/calculation, receipt parsing/reading, navigation, manifest and backup rules. The passing tests do not cover every Android interaction or every line of code.

## Remaining release validation

No phone or emulator was connected. Account create/edit/delete, rotation or theme change during save, inline friend creation, keyboard behavior, slow/offline sync, actual image/PDF OCR, and Android 15/16 system navigation need device tests. Full-screen root layouts have no explicit system-bar inset handling in ScreenPresenter/QuickEntryActivity; verify status-bar, navigation-bar and IME overlap under enforced edge-to-edge before approval.

The public Web App endpoint's private-URL access model was previously chosen by the user; this review did not change it. No claim of exhaustive security validation or zero remaining bugs is made.

Fix the findings and rerun the targeted cases plus the device checks before giving a final readiness verdict. No feature expansion or visual redesign is needed to address these findings.
