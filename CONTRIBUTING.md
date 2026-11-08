# Contributing to Pocket Ledger

Thanks for helping improve Pocket Ledger. Please follow our [Code of Conduct](CODE_OF_CONDUCT.md).
For security vulnerabilities, use the private reporting route in [SECURITY.md](SECURITY.md).

## Issues and pull requests

Search existing issues before opening a new one. For a bug, include the app version,
Android version, steps to reproduce, expected and actual behavior, and a redacted
screenshot when useful. Use synthetic examples instead of real financial records.
Discuss substantial changes in an issue before starting implementation.

Keep pull requests focused. Explain what changed, why, and the checks you ran.
State any device or live-service behavior that you could not verify. Preserve
unrelated changes, existing data, backward compatibility, and third-party notices.

## Current project structure

- `V6/NativeExpenseEntry/`: current Android Java/Room app.
- `V6/Backend/AppsScript.gs`: canonical Android Google Sheets backend.
- `V6/Backend/Canvas.jsx`: maintained Dashboard component source.
- `V6/tests/`: ledger, backup, split, receipt, sync and Dashboard regressions.
- `distribution/`: GitHub Pages site, staged downloads and platform-specific scripts.

Historical root/V2 Android copies are not the current app. The iPhone Shortcut and
its backend are separate from Android's rich ledger; do not mix their contracts.
See the [README](README.md) for setup and current feature limitations.

## Build and validation

Use JDK 17+ and Android SDK API 36. From `V6/NativeExpenseEntry/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --offline
```

The offline flag requires dependencies to be cached first. From the repository root,
run the checks relevant to your change:

```powershell
node --test V6/tests/*.test.cjs
.\V6	estsun-ledger-tests.ps1
node tools/site-smoke/content-check.cjs
python -B tools/site-smoke/seo-check.py
```

Build Android first: some checks use generated Room classes or the built APK.
The JVM harness also requires Android SDK sources. For UI changes, verify on a
disposable emulator or test device and include screenshots where helpful.
Never run tests that create sample records against a user's personal ledger.

## Data and release safety

- Keep Room as Android's primary local store. Use existing exact-money arithmetic,
  whole-ledger validation, transactions, stable IDs and deletion markers.
- Preserve supported backup formats and sync compatibility. Test migrations with
  disposable data; do not clear a database or Sheet to make a test pass.
- Keep the canonical Android backend, bundled app asset and website script download
  identical when updating the backend. Update Dashboard code separately as needed.
- Never commit private Sheet deployment URLs, financial backups, personal captures,
  pairing codes, device addresses, signing keys, credentials or local settings.
- Android split bills remain separate from the financial ledger; do not change
  their backup or sync behavior incidentally.
- Publishing to `main` deploys `distribution/`. A source edit does not rebuild its
  APK automatically or redeploy users' Apps Script projects. Follow the release
  instructions in `V6/docs/RELEASE_6.1.0-preview.15.md`.

## Licensing

Contributions to Pocket Ledger's original code are provided under the repository's
[MIT License](LICENSE). Preserve the licenses and attribution of third-party code
and assets; only contribute material you have permission to share.
