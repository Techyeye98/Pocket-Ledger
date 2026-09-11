# Pocket Ledger — Codex Handoff Document

Last updated: 2026-09-11  
Three passes completed: UI/UX Redesign → Functional Correctness → Targeted Correction

---

## Current Project State

Clean and buildable. All three passes complete. Final APK at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture Summary

| File | Role |
|------|------|
| `MainActivity.java` | Home screen + Settings — programmatic views only |
| `QuickEntryActivity.java` | Shared expense entry bottom sheet |
| `ConfigStore.java` | All user config via SharedPreferences |
| `Design.java` | Color/token system with theme override |
| `Components.java` | Reusable programmatic UI building blocks |
| `ApiClient.java` | HTTPS POST/GET; strict 2xx + "success" body |
| `ExpensePayload.java` | 8-field JSON payload; `dd-MM-yyyy`, `MMMM-yyyy`, `HH:mm:ss` in Locale.US |
| `AmountUtil.java` | BigDecimal amount validation/normalization |
| `NotificationHelper.java` | POST_NOTIFICATIONS permission + channel |
| `AddExpenseWidgetProvider.java` | Widget → `QuickEntryActivity` via PendingIntent |
| `AddExpenseTileService.java` | QS Tile → `QuickEntryActivity` (API 34+ PendingIntent path) |
| `res/xml/shortcuts.xml` | Launcher shortcut → `QuickEntryActivity` |

---

## All Changes Made (Cumulative)

### Pass 4 — Drag-and-Drop Reordering (2026-09-11)
1. **`gradle.properties`**: `android.useAndroidX=true`
2. **`app/build.gradle`**: added `implementation 'androidx.recyclerview:recyclerview:1.3.2'`
3. **`ReorderableListDialog.java`** [NEW]: RecyclerView + ItemTouchHelper dialog with `≡` drag handle. Drag starts only on handle touch. Row tap → actions. Saves order immediately on every swap.
4. **`MainActivity.java`**: replaced `manage()` flat AlertDialog list with `ReorderableListDialog`. Removed `moveItem()`. Removed "Move up"/"Move down" from `itemActions()`.

### Pass 3 — Targeted Correction (2026-09-11)
1. **Removed TODAY section + quick-access caption from Home** (`MainActivity.showHome()`)
2. **Fixed Settings back arrow clipping** — `‹` → `←`, 44dp → 48dp touch target, right padding
3. **Implemented persistent URL-tied connection status** (`ConfigStore` + `MainActivity`):
   - `ConfigStore`: added `verifiedUrl()`, `setVerifiedUrl()`, `clearVerifiedUrl()`
   - `connectionStateText/Color()`: compares current URL vs verified URL (not `lastOperation`)
   - `editUrl()`: clears verified state when URL changes
   - `testConnection()`: sets verified URL only on actual HTTPS success

### Pass 2 — Functional Correctness (2026-09-11)
1. `ExpensePayload.java`: date/time/month format + Locale.US fix
2. `AndroidManifest.xml`: widget `exported=true` fix
3. `ApiClient.java`: `connection.disconnect()` in finally block
4. `QuickEntryActivity.java`: `saving` flag in onSaveInstanceState

### Pass 1 — UI/UX Redesign
Complete programmatic view-based redesign. See `implementation_plan.md` for full spec.

---

## Known Remaining Items

1. **Quick access physical testing** — all 4 entry points code-verified, NOT physically tested. Must test on device:
   - Widget: add to home screen, tap
   - Launcher shortcut: long-press icon, tap shortcut
   - QS Tile: open quick settings, add tile, tap
   - In-app button: open app, tap "Add expense"

2. **Back arrow physical verification** — fix applied, visual confirmation needs device

3. **Apps Script backend contract** — backend `doPost()` must return exactly `"success"` (case-insensitive). Verify manually.

4. **Rotation mid-save** — known architectural limitation. Executor tied to old Activity instance; new instance can't receive result. Would require ViewModel to fix properly.

---

## Build Instructions

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\namya\AppData\Local\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
