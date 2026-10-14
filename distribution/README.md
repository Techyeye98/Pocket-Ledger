# Pocket Ledger website and downloads

This is the GitHub Pages publication folder. `index.html`, `style.css`, `main.js` and `pocket-ledger-logo.png` are the V6 landing website with dark/light themes, interactive sample expenses, continuous hero motion and the opening-book guide interaction.

- `downloads/Pocket-Ledger.apk`: Android 6.0.8, version code 9, Android 8.0+, package `com.pocketledger.v6.preview`. This debug-signed build installs separately from the old V2 release.
- `downloads/Pocket-Ledger-Android-V2.apk` and `Pocket-Ledger-Android.apk`: preserved older downloads, not the current main download.
- `downloads/Pocket-Ledger-iPhone.shortcut`: separate iPhone workflow, unchanged.
- `guides/Pocket-Ledger-Quick-Guide-2026-10.pdf`: current eight-page Android quick guide. Older guide files are retained for existing links.
- `guides/iPhone/iPhone-Shortcut-Setup-Guide.pdf`: separate iPhone setup guide.
- `apps-script/Pocket-Ledger-Apps-Script.gs.txt`: current Android backend copied from `V6/Backend/AppsScript.gs`, including Need / Want support.
- `website/`: preserved older website entry point.

Current Android Sheet: https://docs.google.com/spreadsheets/d/1vu140rEacxVWVeWVil1BvjcWNFS2xyJII1b-rjoisrw/copy

Separate iPhone Sheet: https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy

See the [main README](../README.md) for features, migration safety, optional sync and build instructions. No private phone screenshots, signing keys or personal deployment URLs belong in this folder.

GitHub Actions publishes this folder on a push to `main`; use **GitHub Actions** as the Pages source. Run `node tools/site-smoke/v6-publication.cjs` from the repository root to check the actual publication folder on desktop/mobile, both themes, reduced motion, guide links and downloads.
