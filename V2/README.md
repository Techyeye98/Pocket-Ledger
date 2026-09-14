# Pocket Ledger V2

A **local-first** Android expense tracker. The device database (Room) is the primary
source of truth; Google Sheets is an explicit, user-initiated backup/mirror.

No background services, no polling, no automatic background sync. Sync happens only
when the user triggers it.

---

## Architecture

```
Android App → Local Room Database (primary) → Dashboard / Ledger / Quick Entry
                                                     ↓ (explicit, batched)
                                          Apps Script (Backend/AppsScript.gs)
                                                     ↓
                                              Google Sheet (mirror/backup)
```

The app is fully functional offline and works without any Google Sheets setup.

## App versions

| Item          | Value                          |
|---------------|--------------------------------|
| ApplicationId | `com.pocketledger.v2`          |
| Version       | 2.0.0 (versionCode 1)          |
| Min SDK       | 26                             |
| Target/Compile| 36                             |
| Android Gradle Plugin | 8.7.3                 |
| Database      | Room — `pocket_ledger_v2.db` (`TransactionEntity`) |

## Quick Start

1. Open this folder (`NativeExpenseEntry/`) in Android Studio.
2. Build and run — package `com.pocketledger.v2` (`./gradlew assembleDebug`).
3. Install on a device. Add expenses immediately — no configuration required.
4. To enable cloud backup, paste the Google Sheets Web App URL in
   **Settings → Sync & Google Sheets**, then press **Sync Now**.

Release build: `./gradlew assembleRelease`. Signing credentials are read from a local,
gitignored `local.properties` (see the comment in `app/build.gradle`) — never stored in
source control.

## Repository structure

```
V2/
├── NativeExpenseEntry/       Android project (com.pocketledger.v2)
│   ├── app/src/main/java/    Java source
│   └── app/src/main/res/     Resources (drawables, layouts, values, xml)
└── Backend/AppsScript.gs     Google Apps Script backend (reference/copy)
```

## Key components

- **Room database** — local transaction store, primary source of truth
- **QuickEntryActivity** — polished bottom-sheet quick add (saves locally, then foreground sync)
- **Dashboard** — KPI cards, donut chart (`DonutChartView`), daily trend (`DailyTrendView`),
  category breakdown, period selector
- **Ledger** — full transaction list with search, filters, sort; edit and
  delete (soft-delete tombstone until confirmed synced)
- **Settings** — sync URL & status, categories, payment modes, currency, theme,
  JSON backup/restore, export as text
- **Home-screen widget** (3 responsive layouts) and **Quick Settings tile**

## Sync contract

- Sync is always **explicit** — Settings → Sync Now, or immediately after adding an
  expense. No background service or periodic sync.
- All pending transactions are sent to Apps Script in a **single batch** POST.
- Actions supported by `Backend/AppsScript.gs`: `CREATE`, `UPDATE`, `DELETE`,
  `BATCH`, `FETCH`/`RESTORE`, plus sheet formatting and V1→V2 migration.
- Backend schema columns A–L (Date, Month, Time, Category, Amount, Payment Mode,
  Remarks, Device Name, Transaction Id, Created At, Updated At, Deleted).
- `transactionId` is the stable identity (UUID), never a row number.
- Month format is the full name only — `"September"`, never `"September-2026"`.
- Emojis in categories/payment modes are preserved, never stripped.

## V1

The previous V1 release is preserved at the repository root
(`NativeExpenseEntry/` + `distribution/`). This folder is the V2 generation.

## Support

Telegram: @TechyBist