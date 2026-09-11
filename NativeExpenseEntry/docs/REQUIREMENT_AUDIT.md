# Pocket Ledger — Requirement Audit (Updated)

Targeted Correction Pass — 2026-09-11  
Status key: **DONE+CODE VERIFIED** | **DONE+PHYSICALLY VERIFIED** | **NOT DONE**

---

## Quick Access — Independent Classification

### Widget (AddExpenseWidgetProvider)
**DONE + CODE VERIFIED — NOT PHYSICALLY VERIFIED**

Full code trace:
- `AndroidManifest.xml` line 18: `android:exported="true"` ✅ (fixed in previous pass)
- `AndroidManifest.xml` line 19: `<action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>` ✅
- `AndroidManifest.xml` line 20: `android:resource="@xml/add_expense_widget"` ✅
- `add_expense_widget.xml`: declares `initialLayout="@layout/widget_add_expense"`, `widgetCategory="home_screen"` ✅
- `widget_add_expense.xml` layout with `@id/widget_root` exists ✅
- `AddExpenseWidgetProvider.onUpdate()`: creates `Intent(context, QuickEntryActivity.class)` with `ACTION_ADD_EXPENSE`, `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` ✅
- `PendingIntent.getActivity(context, id, intent, FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE)` ✅ (FLAG_IMMUTABLE required for targetSdk 31+)
- `view.setOnClickPendingIntent(R.id.widget_root, pending)` ✅
- Cold-process: `FLAG_ACTIVITY_NEW_TASK` ensures OS can start a new task stack from cold ✅
- Process recreation: `FLAG_ACTIVITY_CLEAR_TOP` prevents stacking duplicate `QuickEntryActivity` instances ✅

**Physically verified: NO. Must be tested by installing APK on device, adding widget to home screen, and tapping it.**

---

### Launcher Shortcut (shortcuts.xml)
**DONE + CODE VERIFIED — NOT PHYSICALLY VERIFIED**

Full code trace:
- `AndroidManifest.xml` line 10: `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>` on `MainActivity` ✅
- `shortcuts.xml`: `shortcutId="add_expense"`, `enabled="true"` ✅
- `<intent android:action="com.example.nativeexpenseentry.ADD_EXPENSE" android:targetPackage="com.example.nativeexpenseentry" android:targetClass="com.example.nativeexpenseentry.QuickEntryActivity"/>` ✅
- `QuickEntryActivity` declared with `android:exported="true"` and matching intent-filter ✅
- Cold-process: shortcut intents are processed by the launcher which starts a new task — `QuickEntryActivity` will launch directly ✅

**Physically verified: NO. Must be tested by long-pressing the Pocket Ledger launcher icon and tapping "Add Expense" shortcut on device.**

---

### Quick Settings Tile (AddExpenseTileService)
**DONE + CODE VERIFIED — NOT PHYSICALLY VERIFIED**

Full code trace:
- `AndroidManifest.xml` line 22: `android:exported="true"`, `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"` ✅
- `AndroidManifest.xml` line 23: `<action android:name="android.service.quicksettings.action.QS_TILE"/>` ✅
- `AddExpenseTileService.onClick()`:
  - API 34+: `PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE)` → `startActivityAndCollapse(pending)` ✅
  - API < 34: `startActivityAndCollapse(intent)` (deprecated but functional on pre-34) ✅
  - Intent: `new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE).addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP)` ✅
- `@SuppressLint("StartActivityAndCollapseDeprecated")` suppresses the pre-34 deprecation warning ✅
- Cold-process: `FLAG_ACTIVITY_NEW_TASK` ensures correct cold launch from system context ✅

**Physically verified: NO. Must be tested by opening Quick Settings panel, adding Pocket Ledger tile, and tapping it on device.**

---

### In-app Add Expense Button
**DONE + CODE VERIFIED — NOT PHYSICALLY VERIFIED**

Full code trace:
- `MainActivity.showHome()`: `startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE))` ✅
- Same `QuickEntryActivity` target as all other entry points ✅
- Same `ACTION_ADD_EXPENSE` action string ✅

**Physically verified: NO (no device available). This is the simplest path and has the highest code confidence.**

---

## Issue-by-Issue Audit (This Pass)

### Issue 3 — Remove TODAY Section
**DONE + CODE VERIFIED**
`section(page,"TODAY")`, the card, and its text removed from `showHome()`. No database added. Build confirmed clean.

### Issue 4 — Remove Quick-Access Caption Text
**DONE + CODE VERIFIED**
Static text "Widget, launcher shortcut, and Quick Settings all open direct entry." removed from `showHome()`. Build confirmed clean.

### Issue 5 — Settings Back Arrow Clipping
**DONE + CODE VERIFIED — NOT PHYSICALLY VERIFIED**
Character changed from `‹` → `←`, touch target 44dp → 48dp, right padding added to button. Cannot physically verify without device but the root cause (character glyph clipping at render boundary) is addressed by using a full standard Unicode arrow glyph.

### Issue 6 — Connection Status Persistence
**DONE + CODE VERIFIED**
Logic:
- `ConfigStore.verifiedUrl()` / `setVerifiedUrl()` / `clearVerifiedUrl()` added — URL-tied verification state persisted in SharedPreferences
- `connectionStateText()` / `connectionStateColor()` now compare `store.url()` vs `store.verifiedUrl()` — not `lastOperation`
- `editUrl()` calls `clearVerifiedUrl()` when URL changes — immediately reverts to "not yet tested"
- `testConnection()` calls `setVerifiedUrl(testedUrl)` only on actual HTTPS success
- State survives: app restart ✅, Settings re-entry ✅, Activity recreation ✅
- State invalidates: URL change ✅
- False "Connected" for untested URL: impossible ✅ (verifiedUrl starts empty, only set by real successful test)

### Issue 7 — Test Connection Semantics
**DONE + CODE VERIFIED**
`testConnection()` makes a real HTTPS GET to the configured URL via `ApiClient.test()`. Only sets `verifiedUrl` on HTTP 2xx response. Network errors, timeouts, non-2xx all produce `Result.fail()`. No artificial positive. No polling added.

---

## Known Remaining Items

1. **All Quick Access physical verification pending** — no Android device available. Install the APK and test all 4 entry points manually.
2. **Back arrow visual verification pending** — fix is code-level; visual confirmation requires device or emulator.
3. **Apps Script "success" response match** — backend must return exactly the string `"success"` (case-insensitive, no extra whitespace) for expense submissions to register as successful.
4. **Rotation mid-save** — documented in previous pass. Existing architectural limitation.
