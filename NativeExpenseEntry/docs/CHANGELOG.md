# Pocket Ledger — Changelog

## [UI Pass — Widget + 3 App Fixes] — 2026-09-11

### Change 1 — Widget Redesign

#### New drawables
- `widget_background.xml` — 3-layer premium background: near-black base `#111A1E`, teal gradient sweep (transparent → `#3A1E8060` right-side), subtle top highlight. 24dp corner radius.
- `widget_plus_bg.xml` — teal oval `#3ECFAA` for the "+" circle button
- `widget_logo_bg.xml` — dark rounded square `#1E2D34` / 12dp radius for the logo slot

#### Redesigned layout (`widget_add_expense.xml`)
- Removed: "ADD EXPENSE" all-caps text, "Tap for quick entry" caption
- Left: 54dp logo slot with app_logo centered at 38dp
- Center: "Pocket Ledger" (17sp bold, near-white) + "Add Expense" (13sp muted teal)
- Right: 52dp teal circle with "+" (28sp bold, dark text)
- 14dp card padding, 24dp corner radius
- Entire card remains the click target → `QuickEntryActivity` (PendingIntent unchanged)

### Change 2 — Dark Mode Readability Fix (ReorderableListDialog)

#### `ReorderableListDialog.java`
- `RecyclerView` now has explicit `Design.surface(ctx)` background — prevents dialog-theme bleed-through
- `onBindViewHolder` now explicitly sets `vh.label.setTextColor(Design.text(ctx))`, `vh.handle.setTextColor(Design.muted(ctx))`, and `vh.itemView.setBackgroundColor(Design.surface(ctx))` on every bind
- Result: category/payment mode names, emojis, and drag handles all readable in both Light and Dark mode regardless of system `AlertDialog` theme inheritance

### Change 3 — Home Screen Logo

#### `MainActivity.java` — `header()`
- Home header (isSettings=false) now prepends a 32dp × 32dp `ImageView` using `@mipmap/app_logo` with 10dp end margin before the "Pocket Ledger" title
- Settings header unchanged
- Logo is vertically centered with the title and does not crowd the Settings gear button

### Change 4 — Settings Back Navigation

#### `MainActivity.java`
- Added `Screen { HOME, SETTINGS }` enum and `currentScreen` field
- `showHome()` sets `currentScreen = Screen.HOME`
- `showSettings()` sets `currentScreen = Screen.SETTINGS`
- Added `onBackPressed()` override: if `currentScreen == SETTINGS` → call `showHome()` (no new Activity instance, no stack growth); otherwise → `super.onBackPressed()` (normal Home exit behavior)
- No duplicate instances, no infinite back-stack


### Added

#### ReorderableListDialog.java — New file
- Self-contained drag-and-drop list dialog using `RecyclerView` + `ItemTouchHelper`
- Drag handle `≡` on the left of every row (48dp × 52dp touch target)
- Press-and-hold handle → drag vertically → release at target position
- `isLongPressDragEnabled() = false` — drag only starts from the handle, never from an accidental long-press on the label text
- Row tap → item actions dialog (Rename / Change emoji / Delete)
- `onOrderChanged` fires immediately after every swap and calls `ConfigStore.setCategories/setPaymentModes` — order is persisted to SharedPreferences on every single drag movement

#### app/build.gradle — Added dependency
- `androidx.recyclerview:recyclerview:1.3.2`

#### gradle.properties — Enabled AndroidX
- `android.useAndroidX=false` → `android.useAndroidX=true`
- Required by the RecyclerView dependency

### Changed

#### MainActivity.java — manage()
- Replaced `AlertDialog.Builder.setItems()` flat list with `ReorderableListDialog.show()`
- Order callback saves immediately without reopening the dialog

#### MainActivity.java — itemActions()
- Removed "Move up" and "Move down" actions — replaced by direct drag-and-drop
- Remaining actions: Rename, Change emoji, Delete

### Removed

#### MainActivity.java — moveItem()
- Method deleted — replaced entirely by drag-and-drop

### No Regressions
- Data format unchanged: category/payment strings remain `Text 🍔` suffix format
- Default category / default payment mode stored independently — unaffected by reorder
- Add / Rename / Change emoji / Delete all functional
- All other screens untouched


## [Targeted Correction Pass] — 2026-09-11

### Removed

#### MainActivity.java — TODAY Section
- Removed `section(page, "TODAY")`, the empty-state card ("No expenses yet today..."), and all related logic from `showHome()`
- No local database was added or referenced — removal was clean

#### MainActivity.java — Misleading Quick-Access Caption
- Removed static text "Widget, launcher shortcut, and Quick Settings all open direct entry." from the Home screen
- The actual functional quick-access mechanisms are the evidence; the text was redundant and misleading

### Fixed

#### MainActivity.java — Settings Back Arrow Clipping
- Changed character from `‹` (single left-pointing angle quotation mark) to `←` (left arrow)
- Resized touch target from 44dp → 48dp
- Added right padding to the button so the arrow glyph has internal breathing room
- Arrow is now fully visible without clipping against the screen/container edge

#### ConfigStore.java + MainActivity.java — Connection Status Persistence
- Added `verifiedUrl()`, `setVerifiedUrl()`, `clearVerifiedUrl()` methods to `ConfigStore`
- Connection status now determined by comparing current URL against the last successfully verified URL (URL-tied state), not by inspecting `lastOperation` which expense submissions would overwrite
- `editUrl()` now calls `clearVerifiedUrl()` when the URL is changed — immediately invalidates the "Connected" state for a new unverified URL
- `testConnection()` now calls `setVerifiedUrl(testedUrl)` on a real successful test result — persists across app restarts, Settings re-entry, and Activity recreation
- Failed connection tests do NOT set verifiedUrl — only actual HTTPS success does

### No Changes Made
- Widget implementation (`AddExpenseWidgetProvider.java`) — already correct
- Launcher shortcut (`shortcuts.xml`) — already correct  
- Quick Settings tile (`AddExpenseTileService.java`) — already correct
- In-app Add Expense button — already correct
- All UI colors, typography, layout structure — untouched
- API payload contract — untouched
- Emoji suffix format — untouched

## [Functional Pass] — 2026-09-11

### Bug Fixes

#### ExpensePayload.java — Date/Time/Month Format Correction
- `date`: `d/M/yyyy` → `dd-MM-yyyy` (Locale.US)
- `month`: `MMMM` → `MMMM-yyyy` (Locale.US)
- `time`: `H:mm` → `HH:mm:ss` (Locale.US)

#### AndroidManifest.xml — Widget Receiver Exported Flag
- `AddExpenseWidgetProvider`: `exported="false"` → `exported="true"`

#### ApiClient.java — Connection Lifecycle
- `HttpURLConnection` always disconnected in `finally` block

#### QuickEntryActivity.java — Duplicate Submission Protection
- `saving` flag persisted in `onSaveInstanceState` and restored in `onCreate`
