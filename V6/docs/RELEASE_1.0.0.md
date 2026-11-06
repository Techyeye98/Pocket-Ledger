# Android 1.0.0

Released 6 November 2026. Version code 25; the application ID and signing identity are unchanged. Public versions now use major.minor.patch; source-folder numbers are not app versions.

- Dashboard blocks deleting entries with linked repayments/refunds and directs payment-account changes to Android.
- Strict transaction date/amount validation and increasing update revisions protect sync after clock changes.
- Equal-revision content conflicts are reported, and sync acknowledgement checks financial content.
- Private Sheet connections are excluded from JSON exports, Android cloud backup and device transfer; upgrades preserve the local connection.
- Standalone split-bill backup behavior and the separate iPhone backend are unchanged.
- Home checks the public GitHub latest stable release on a separate worker, at most once per hour. Newer versions show an update card opening `/releases/latest`; Settings has a manual check that bypasses the cooldown. Network failures leave the ledger usable.
- Settings includes Star the GitHub repository, Made by Namya Mehta and Contact — Telegram @Techyeye. Links open externally; users choose whether to star the repository.

Install over a compatibly signed existing app; do not uninstall to update. Existing records remain in place.

## Website publication

The site is served from `distribution/`. For each future Android release, bump `versionName` and `versionCode`, update current website/README/generator version references, run the protected release build described in [release signing](RELEASE_SIGNING.md), and copy the verified `app-release.apk` to `distribution/downloads/Pocket-Ledger.apk`. Run content/metadata checks and commit the release files to `main`. The existing GitHub Actions workflow then publishes the website automatically. This workflow publishes staged files; it does not build Android APKs. The download URL stays stable, and website buttons include a version query to refresh cached downloads. The staged APK is the verified 1.0.0 release build. It preserves the certificate of the previously published app.

## GitHub update notifications

Publish a **stable GitHub Release** in `Techyeye98/Pocket-Ledger`, with a tag such as `v1.0.0` or `v1.0.1`, mark it as latest and attach the corresponding signed APK. Draft/prerelease releases and prerelease tag suffixes are excluded. Version comparison uses the numeric major/minor/patch components; build metadata does not affect update precedence. The app opens `https://github.com/Techyeye98/Pocket-Ledger/releases/latest` rather than downloading/installing APKs itself.

The initial API check before publication returned HTTP 404. The stable `v1.0.0` release publishes the signed APK alongside this website update. An installed 1.0.0 reports up to date; a later stable version triggers the update card. A website APK by itself does not create a GitHub Release.

Only public release metadata is requested, without a token, ledger contents or installation identifier. Connect/read timeouts are five seconds each and the response is capped at 256 KiB. Repeated requests are coalesced; cached update cards survive temporary request failures. Manual checks report current/no-release/failure states, and a found update offers an Open releases action. Update results modify only the card host, preserving cached dashboard views and existing navigation/press animations.
