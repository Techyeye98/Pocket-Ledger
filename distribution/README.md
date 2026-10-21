# Pocket Ledger local website package

This folder contains the website and locally staged downloads. **Nothing has been pushed or published by this update.** The current source is Android 6.1.0-preview.14 (code 23), Android 8.0+, package `com.pocketledger.v6.preview`.

- `index.html`, `style.css`, `main.js`: existing visual design with updated accounts/financial-ledger features, examples, setup, sync/backup limits and Android/iOS naming.
- `downloads/Pocket-Ledger.apk`: current debug-signed Android build staged locally; updates a compatible preview, separate from the older release-signed app.
- `downloads/Pocket-Ledger-iPhone.shortcut`: existing iOS Shortcut; unchanged bytes/name for link compatibility.
- `apps-script/Pocket-Ledger-Apps-Script.gs.txt`: canonical current Android backend, including clean Transactions + hidden Sync Support.
- `apps-script/Pocket-Ledger-iOS.gs.txt`: original iOS expense-to-Sheet script, kept separate from Android’s rich ledger.
- `guides/Pocket-Ledger-Quick-Guide-2026-10.pdf`: earlier Android expense/widget edition, retained under earlier editions.
- `guides/iPhone/iPhone-Shortcut-Setup-Guide.pdf`: existing iOS setup guide; unchanged.

Android template: https://docs.google.com/spreadsheets/d/1rxBf4afldt5WeRTu_Lxue1OeDSCDepbNpJz45OeTdAA/copy

iOS template: https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy

Run `node tools/site-smoke/content-check.cjs` from the repository root for content/assets checks. Run `node tools/site-smoke/v6-publication.cjs --serve` to preview at http://127.0.0.1:4829/Pocket-Ledger/. Browser verification uses the connected in-app browser.

See the [main README](../README.md) for complete features, setup and limitations. Do not place private captures, personal deployment URLs or signing credentials in this package. Historical `website/experiments/v6/` is not the current website source. Publishing is a separate action.

## Current page structure, 21 October 2026

The original neon traces, green palette, typography and animated guide-book have been restored at the owner's request. The paper-slip redesign is archived locally under ignored `tools/site-smoke/out/paper-redesign-2026-10-20/`.

- `index.html`: original hero and statement, interactive spending demo, native HTML/CSS Accounts/Split bills examples, four compact feature highlights, shared-payment example, concise data summary, direct Android/iOS downloads and animated guide-book. No screenshots appear in the homepage examples. Long explanations and FAQs stay on the detail pages.
- `features.html`: accounts, transfers/refunds, people dues, bill splitting and interactive spending example.
- `your-data.html`: local-first storage, optional Sheet sync and backup limits.
- `download.html`: Android/iOS downloads and matching Sheet templates.
- `guide.html`: task hub for nine `guide-*.html` chapters; beginner steps, annotated real screenshots, FAQs and current PDF.
- `404.html`: designed missing-page response.

Lenis 1.3.27 and GSAP 3.15.0/ScrollTrigger remain locally hosted in `vendor/`; variable fonts and OFL notices are in `fonts/`. Native touch scrolling and reduced-motion/site Motion off remain available. There is no build step, runtime CDN or analytics.

Run the content check and local preview commands above. Browser evidence for this revision is ignored under `tools/site-smoke/out/restored-pages/`. The earlier Awwwards review score applies to the archived design, not this revision. Nothing has been pushed or published.

21 October verification: all 15 pages passed desktop-dark and mobile-light browser audits without automated accessibility violations or horizontal overflow. Features, data and download pages now share lead typography, spacing and back-home navigation. `python tools/site-smoke/link-audit.py` checks local links/fragments and outbound HTTP responses; its report is under ignored `tools/site-smoke/out/home-refinement/`. HTTP success alone does not establish template access: the signed-in browser opened the correct iOS copy page, but the supplied Android template returned **You need access**. Owner sharing settings must be checked before public distribution. No sharing permissions were changed.

Follow-up: the owner rejected screenshot previews. The replacement examples inherit the website's fonts and semantic colours. A sample ₹500 bank-to-cash transfer preserves the ₹10,000 total; a ₹200 friend repayment reduces ₹800 receivable to ₹600. Both reset and are website-only. The guide invitation now has one clickable book, with no duplicate button or caption link. Repeated homepage feature/guide links, the redundant footer Guide link and the guide hub's second current-PDF link were removed. Navigation remains available in the main header. Current follow-up evidence is in `tools/site-smoke/out/native-examples/`.

Publication check, 21 October: after the owner updated access, the generic Android template link successfully opened **Pocket Ledger — Android - Make a copy** in the browser. This resolves the sharing blocker recorded above. Both platform links keep account-independent URLs. Final offline APK build/lint, five JVM suites, five Node regression files, website content checks and handbook verification passed. Lint retains 40 warnings and no errors; the app remains preview. Home is present in every mobile menu, and the circular half-light theme button matches the owner's chosen earlier design.

## Illustrated handbook

`guides/Pocket-Ledger-Guide-2026-10.pdf` is the current 14-page Android PDF with clickable contents and bookmarks. Accounts, everyday/advanced entries, insights, Lena / Dena, item splits, tax/discount, multiple payments, reimbursements, receipt review, Sheets and backups are covered. `guide.css` supplies its web reading layout. Twelve approved original screenshot assets live in `guides/current/screens/`; focus windows, lettered callouts and explanations are native HTML/SVG/PDF presentation, preserving exact app pixels. Personal Home balances, account lists, device names and deployment URLs are excluded.

Authoring: `python tools/guide/build_current_guide.py`. Content lives in `tools/guide/current_guide_content.py`. Build inputs are the private local capture set; do not publish that folder. Rebuild checks: `python tools/guide/verify_current_guide.py` and the site content check. The phone’s demo records were removed after capture and its original balances/settings restored. No public deployment was performed.
