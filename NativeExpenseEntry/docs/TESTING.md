# Pocket Ledger — Testing Log (Updated)

## Targeted Correction Pass — 2026-09-11

### Build Verification
- **Build:** `gradlew assembleDebug lint`
- **Result:** `BUILD SUCCESSFUL in 19s` — 42 actionable tasks, 13 executed, 29 up-to-date
- **Lint:** No errors. Same 3 pre-existing Java source warnings (deprecated source/target 8)
- **APK:** `app/build/outputs/apk/debug/app-debug.apk`

---

### Physical Device Testing
**No Android device or emulator was available in this build environment.**  
All quick-access verifications are code-level only.

---

### Quick Access — Code Verification Details

| Entry Point | Implementation | Manifest | Code Path | Status |
|---|---|---|---|---|
| **Widget** | `AddExpenseWidgetProvider.onUpdate()` fires `PendingIntent` → `QuickEntryActivity` | `exported=true` ✅ | `FLAG_ACTIVITY_NEW_TASK + FLAG_IMMUTABLE` ✅ | **CODE VERIFIED** |
| **Launcher Shortcut** | `shortcuts.xml` targets `QuickEntryActivity` with `ACTION_ADD_EXPENSE` | `QuickEntryActivity exported=true` ✅ | Cold-launch via shortcut mechanism ✅ | **CODE VERIFIED** |
| **QS Tile** | `AddExpenseTileService.onClick()` branches on API 34+ for `PendingIntent`-based launch | `exported=true`, `BIND_QUICK_SETTINGS_TILE` ✅ | `FLAG_ACTIVITY_NEW_TASK + FLAG_IMMUTABLE` ✅ | **CODE VERIFIED** |
| **In-app button** | `MainActivity.showHome()` → `startActivity(Intent → QuickEntryActivity)` | N/A | Direct activity start ✅ | **CODE VERIFIED** |

**NONE of the above are PHYSICALLY VERIFIED. Physical testing required on your Android device.**

---

### Connection Status — Code Verification

Traced the full state machine:

| User Action | verifiedUrl | connectionStateText() |
|---|---|---|
| Fresh install, no URL | `""` | Not configured |
| URL entered, never tested | `""` | Configured · not yet tested |
| Test Connection → success | `= current URL` | Connected |
| Leave Settings and return | unchanged | Connected ✅ |
| App restart | persisted in SharedPreferences | Connected ✅ |
| Change URL to new value | `clearVerifiedUrl()` called → `""` | Configured · not yet tested ✅ |
| Test new URL → success | `= new URL` | Connected ✅ |
| Test → failure | unchanged | Configured · not yet tested ✅ |

---

### Removed Elements — Verified Gone
- `section(page,"TODAY")` — gone from `showHome()` ✅
- Empty-state card text — gone ✅
- Quick-access caption text — gone ✅

---

## Previous Pass (Functional) — 2026-09-11

### Build Verification
- **Result:** `BUILD SUCCESSFUL in 30s` — lint clean
- **Physical device testing:** None available — all code-level

### Code-level verifications from that pass remain valid (unchanged)
- Date/time format fix
- Widget exported=true fix
- ApiClient disconnect fix
- saving flag persistence fix
