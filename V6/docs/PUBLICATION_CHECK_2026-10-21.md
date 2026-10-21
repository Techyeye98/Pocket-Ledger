# Publication check — 21 October 2026

Scope: Android 6.1.0-preview.14 (versionCode 23), current Apps Script/Canvas sources, approved static website, nine web guide chapters and the current 14-page PDF. The existing iOS workflow is retained separately.

## Passed before publication

- Offline Gradle `:app:assembleDebug :app:lintDebug`: successful. No lint errors; 40 existing warnings. The build warns that AGP 8.7.3 was tested through compileSdk 35 while this project compiles against 36. No toolchain migration was made during publication.
- All five JVM regression suites: money/ledger, insights, bill splitting, receipts and financial backup compatibility.
- All five Node regression files: Canvas round trip, clean report/support layout, ledger backend, migration/Need-Want and financial Sheet reports.
- Website content/resources/platform URLs and current APK/backend source parity.
- PDF 14 pages, ten valid bookmarks, link annotations, twelve approved screenshot hashes and staged PDF parity.
- Earlier desktop/mobile UI checks and targeted examples, guide-book, Home-menu and theme persistence checks.
- Staged source scan: no deployment endpoint, API key, private key, personal email, private captures/backups, Python caches or machine settings included.
- Owner-supplied Android template now opens the correct named copy screen; the earlier access restriction is resolved. The iOS template opens its separate named copy screen. No template copy or cloud sharing change was performed by this agent.

## Artifact hashes

- Android APK SHA-256: `6c22ed0942e7eb73e58a840c529c94c5ef342cd8384211d1513343d191c008c4`
- Current guide PDF SHA-256: `de7cd9a485fbbd8501e015f2fc2bc2004cbd4becde363183c0e8a840ebc23176`

Private phone backups and raw captures remain ignored. Unrelated local deletions of historical NativeExpenseEntry, V2, Reference and archive folders are excluded from this release. Older distribution copies no longer referenced by the current site are retired. This is a preview publication, not a claim of exhaustive Android device/UAT or security verification. No app signing change, financial record change or Apps Script deployment is part of this publication.
