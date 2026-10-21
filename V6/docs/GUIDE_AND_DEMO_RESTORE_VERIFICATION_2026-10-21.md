# Guide and demo restoration verification

Completed locally on 2026-10-21. Nothing was published or pushed.

## Phone restored before guide authoring

Compared the official pre-demo JSON export with a new export before cleanup. Original account, route, person and transaction records were unchanged; preferences had no changed keys. Cleanup targeted only the recorded demo IDs and retained deletion markers so an older demo backup cannot silently revive those entries.

- Official import result: 24 demo financial entries tombstoned, zero added or updated.
- Removed the three demo accounts, three payment routes and two ledger people through the selective cleanup backup.
- Deleted only Demo Dinner and the two saved demo split friends through the app UI.
- Verified restored Home: original balances, zero income/expenses, no people dues.
- Verified Split bills: no bills and zero saved friends.
- Preserved original settings, accounts and Sheet connection. No sync or Sheet restoration was triggered.

Private evidence remains in ignored `V6/guide-captures/2026-10-20/`: official backups, cleanup validation, and screenshots 47/48. Screenshot 49 is the empty account form captured after restoration; it was closed without saving.

The phone disconnected after restoration. Three temporary guide JSON/receipt files may still be in its Downloads folder; the attempted file cleanup stopped with device not found. This does not affect the verified app restoration.

## Guide delivered

- `distribution/guide.html`: topic chooser, beginner starting steps and targeted answers.
- Nine separate chapter pages cover accounts, entries, insights, Lena / Dena, split items, split payments, receipt scanning, Sheets and backups.
- `output/pdf/Pocket-Ledger-Guide-2026-10.pdf`: 14 pages with linked contents and bookmarks; identical staged copy under `distribution/guides/`.
- Twelve reviewed real screenshots contain demo examples or generic controls. Private balances, private account lists and deployment URLs were excluded. Original image bytes are preserved; framing, highlights and lettered callouts are native HTML/SVG/PDF presentation layers.
- README files and sitemap updated. Authoring sources live under `tools/guide/`.

## Checks passed

- `python tools/guide/verify_current_guide.py`: page count, ten bookmark destinations, PDF links, topic coverage, private-text exclusion, exact screenshot hashes/dimensions and staged PDF parity.
- `node tools/site-smoke/content-check.cjs`: local resources and fragments, current platform links, feature/backup/sync copy, README versions, APK and script parity.
- All ten guide pages audited in browser at 1440×900 dark theme and 390×844 light theme: zero automated accessibility violations and no horizontal overflow. Automated contrast review retains an incomplete result for some styled content; this is not a claim of exhaustive accessibility certification.
- Visually inspected all 14 PDF pages. Rechecked corrected account callouts, Needs/Want framing, cover and complete OCR final amount after rebuilding.
- Visually checked mobile callout readability; verified FAQ expansion and a direct lesson anchor. Reset temporary viewport override afterward.

Browser evidence is in ignored `tools/site-smoke/out/current-guide/`; PDF page renders are in ignored `tmp/pdfs/current-guide/`.
