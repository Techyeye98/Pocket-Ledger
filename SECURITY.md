# Security policy

Pocket Ledger is an offline-first Android financial ledger with optional Google
Sheets sync. The Android preview, Apps Script backend, Dashboard, website and
separate iPhone workflow have different trust boundaries. Passing functional tests
does not establish an independent security audit.

## Reporting a vulnerability

Do not publish credentials, exploit details or financial data in a public issue.
Contact the maintainer, Techyeye98, privately via
[Telegram @Techyeye](https://t.me/Techyeye). Start with a brief description of the
affected component and version; agree on a private channel before sharing sensitive
details. Never send your real ledger, private deployment URL, signing key, Google
credentials or ADB pairing code as part of an initial report.

A useful report describes the impact, affected version, reproduction steps using
synthetic data, and any suggested mitigation. Coordinate disclosure with the
maintainer so a fix can be investigated before publishing exploit details.
There is no guaranteed response deadline or paid bug bounty stated by this project.
Ordinary non-sensitive bugs can be reported through GitHub issues.

## Supported releases

Security fixes target the latest published Android preview and the current backend
and Dashboard source. Historical V1/V2 apps and older preview builds have no
separate security-maintenance commitment. Update the Android app and applicable
Sheet components together when a release changes their contracts.

## Protecting your data

- The Android ledger is stored locally first; JSON financial exports are sensitive
  files. Keep them private and verify imports before removing an original copy.
- The optional Apps Script Web App uses a private-URL access model. Anyone who has
  the URL can access or modify the synced ledger. Do not post it in screenshots,
  public issues, source code, analytics or logs. If exposed, disable the affected
  deployment and create a replacement, then reconnect the app.
- The private connection URL is excluded from current JSON exports and Android
  cloud/device-transfer backups. A phone restore requires reconnecting the Sheet.
- Check Sheet sharing separately: possession of a template link and access to a
  personal ledger or its deployment are different things.
- Install APKs only from this repository's documented distribution. Current Android
  builds are debug-signed previews; preserve a verified backup before migration.
- Only enable wireless debugging on a trusted network, and disable it when finished.

Do not test vulnerabilities by changing another person's ledger, Sheet or device.
Use disposable accounts and synthetic financial records with explicit authorization.
