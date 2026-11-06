# Android 6.1.0-preview.15

Released 6 November 2026. Version code 24; the application ID and signing identity are unchanged.

- Dashboard blocks deleting entries with linked repayments/refunds and directs payment-account changes to Android.
- Strict transaction date/amount validation and increasing update revisions protect sync after clock changes.
- Equal-revision content conflicts are reported, and sync acknowledgement checks financial content.
- Private Sheet connections are excluded from JSON exports, Android cloud backup and device transfer; upgrades preserve the local connection.
- Standalone split-bill backup behavior and the separate iPhone backend are unchanged.

Install over a compatibly signed preview; do not uninstall to update. Existing records remain in place.

## Website publication

The site is served from `distribution/`. For each Android release, bump `versionName` and `versionCode`, update current website/README/generator version references, run the Android debug build, and copy `app-debug.apk` to `distribution/downloads/Pocket-Ledger.apk`. Run content/metadata checks and commit the release files to `main`. The existing GitHub Actions workflow then publishes the website automatically. This workflow publishes staged files; it does not build Android APKs. The download URL stays stable, and website buttons include a version query to refresh cached downloads.
