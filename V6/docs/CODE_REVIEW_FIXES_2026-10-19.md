# Code review fixes — 19 October 2026

All five findings in `CODE_REVIEW_2026-10-19.md` have source fixes:

1. `Backend/Canvas.jsx` updates Personal amount with ordinary expense amount changes and additions. Invalid amounts/dates are rejected before a Sheet write. Shared, linked and advanced transactions must be edited in the app; errors appear inside the editor.
2. Category and payment breakdowns use the same signed personal-spending calculation: personal EXPENSE/FRIEND_PAID shares minus personal REFUND shares. Income, transfers and lending are excluded. Negative category totals use the ranked list. The daily timeline also retains negative refund totals regardless of row order.
3. Account/person snapshots are keyed by stable IDs, with names retained as labels and a compatibility fallback for rows without IDs. React row keys use the same identity.
4. LedgerActivity captures the current entry/account draft before rendering a successful dependent-data refresh, preserving inline-person entry fields, payment choice, date, tag and split allocations.
5. LedgerActivity and BillSplitActivity register modern platform Back callbacks on API 33+. Toolbar, legacy and modern Back all call each activity's shared handler, preserving internal routes and split discard/step handling.

The maintained Canvas source was recovered from the review's saved `canvas-after.jsx`. The backend integration test now requires and consumes the maintained source, rather than optionally checking an ignored build artifact.

## Validation

- `node V6/tests/canvas-regressions.test.cjs`: passed. Tests actual Canvas handlers and actual backend fetch/push after editing an ordinary expense from 500 to 600; rejects shared/linked/invalid edits; checks mixed kinds, refunds, duplicate account/person names, latest snapshot selection and legacy fallback.
- `node V6/tests/ledger-backend.test.cjs`, `needs-wants.test.cjs`, `sheet-reports.test.cjs`: passed.
- `V6/tests/run-ledger-tests.ps1`: all five JVM suites passed.
- Gradle `:app:assembleDebug :app:lintDebug --offline`: passed.
- Gradle `:app:assembleDebugAndroidTest :app:lintDebug --offline`: passed. The inline-person UI regression is compiled, but has not run on a device.
- ADB device inventory was empty. Gesture/three-button Back, keyboard/insets, lifecycle changes during saves and receipt OCR remain device validation items from the original review.

## Applying and testing

The existing live Dashboard was subsequently updated through the Sheet's integrated Gemini at the user's request. The Dashboard retained sheet ID `300839893` and the Sheet still showed only Transactions and Dashboard. Sheets displayed Saved to Drive after generation. No financial records or Apps Script deployments were intentionally changed.

Gemini's actual generated source was read through Show code and saved locally as `NativeExpenseEntry/app/build/sheet-upgrade/canvas-live-2026-10-19.jsx`. The Canvas regression suite passed against this source, including the actual backend fetch/push round trip. The harness was adapted to capture Gemini's returned validation-error strings instead of the maintained source's error-state setter. The live generated source is a Gemini implementation of the requested fixes, not a byte-identical upload of `Backend/Canvas.jsx`.

The Canvas preview remained blank in the connected browser; the user will check the rendered result. The save/response screenshot is `NativeExpenseEntry/app/build/sheet-upgrade/canvas-gemini-fixes-saved-2026-10-19.png`. `Backend/Canvas.jsx` remains the independently maintained and tested source for manual deployment.

Use a disposable emulator with no sync endpoint for the UI regression: the test creates a synthetic person through the real Add person dialog. After installing the debug and androidTest APKs, run:

```powershell
adb shell am instrument -w com.pocketledger.v6.preview.test/com.pocketledger.v2.ReviewRegressionInstrumentation
```

The test verifies that amount, note, personal allocation, split toggle, payment selection and date survive the successful save refresh. Financial calculation and sync checks use synthetic local data only. Source fixes and passing local checks do not establish final device readiness.
