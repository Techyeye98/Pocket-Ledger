# Pocket Ledger for Android

Pocket Ledger is a standalone, native Android expense-entry app. It replaces the Android MacroDroid workflow with a branded light/dark home screen plus a separate, focused bottom-anchored quick-entry sheet from the widget, launcher shortcut, or Quick Settings tile.

## What it sends

On **Confirm & Save**, the app creates an HTTPS JSON POST compatible with the supplied Apps Script:

```json
{
  "date": "10/9/2026",
  "month": "September",
  "time": "11:47",
  "category": "Groceries",
  "amount": "53",
  "paymentMode": "Cash",
  "remarks": "Example",
  "deviceName": "My Android"
}
```

It shows **Expense Saved Successfully** only when the HTTP request returns a 2xx status and the body is exactly `Success` (case-insensitive), matching the reference Apps Script. A failure never produces a success notification.

## Open and build

1. Open the `NativeExpenseEntry` folder in Android Studio.
2. Let Android Studio download Gradle/Android Gradle Plugin dependencies if they are not already cached.
3. Select an Android emulator or device and run the `app` configuration, or use `gradlew.bat assembleDebug` from this folder.
4. The debug APK, after a successful build, is at `app/build/outputs/apk/debug/app-debug.apk`.

The project uses Android API 36.1, minSdk 26, and Java. It has no third-party runtime dependencies.

## First-time configuration

Open **Settings** in the app and configure:

1. **Apps Script Web App URL** - paste your own deployed HTTPS Apps Script Web App URL.
2. **Test Connection** - performs a non-mutating HTTPS GET reachability test. It does not add a transaction; a real confirmation is still required to validate the `doPost` contract.
3. **Device Name** - the value sent with each transaction. The initial value is `My Android`.
4. **Manage Categories** and **Manage Payment Modes** - add, rename, change emoji, reorder, or delete local choices. Values are stored and submitted in the canonical `Name emoji` format, such as `Food & Dining 🍔` and `UPI 📱`. Existing prefix-style values migrate automatically. Deleting a choice does not alter historical Google Sheet rows.
5. **Default Category** and **Default Payment Mode** - optional preselected entry choices.

All configuration is stored privately in Android `SharedPreferences` and survives app restarts.

## Adding an expense

1. Open the app and tap **Add expense**, tap the **Add Expense** home-screen widget, long-press the launcher icon and use **Add Expense**, or tap the **Add Expense** Quick Settings tile. Each opens the same direct quick-entry sheet, not the home screen.
2. Enter a positive amount.
3. Choose a category and payment mode.
4. Add an optional remark.
5. Tap **Confirm & Save**, review the confirmation dialog, then tap **Save**.

While the request is pending, the Save control is disabled to prevent accidental double submission. The app does not keep an offline queue, so an unconfirmed offline save must be retried intentionally.

## Widget and Quick Settings tile

- **Widget:** Touch and hold an empty area of the home screen, choose Widgets, then add **Add Expense** from Pocket Ledger.
- **Launcher shortcut:** Touch and hold the Pocket Ledger launcher icon, then drag **Add Expense** to the home screen. It opens direct quick entry.
- **Quick Settings:** Swipe down twice, choose Edit, and drag **Add Expense** into the active tiles. Android availability and exact edit UI vary by device.

Both only launch the app after an explicit user action. The application has no foreground service, background listener, shake detection, sensor monitoring, polling, or scheduled sync.

## Troubleshooting

- **Failed to Save Expense:** Verify mobile/Wi-Fi connectivity, URL deployment permissions, and that the URL is HTTPS. Redeploy the Apps Script web app if its deployment is no longer accessible.
- **Connection test succeeds but save fails:** The test only confirms endpoint reachability. Check that the Apps Script still has `doPost`, reads JSON, writes to `Transactions`, and returns `Success`.
- **No notification:** Android 13+ requires notification permission. The app still displays its on-screen success message after a confirmed save.
- **Missing category/payment option:** Add it in Settings. Existing Google Sheet data is never changed by local list management.

See [docs/BACKEND_COMPATIBILITY.md](docs/BACKEND_COMPATIBILITY.md) and [docs/TESTING.md](docs/TESTING.md) for the verified contract and test status.

See [docs/REQUIREMENT_AUDIT.md](docs/REQUIREMENT_AUDIT.md) for the enhancement audit and logo/testing limitations.
