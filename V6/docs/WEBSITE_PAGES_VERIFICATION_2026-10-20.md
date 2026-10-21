# Original design with separate pages — 20 October 2026

The owner preferred the earlier neon-trace design and motion. That visual language has been restored from `tools/site-smoke/out/before-motion-2026-10-20/`, while retaining shorter copy and separate detail pages from the newer approach.

## Current pages

- Home: original moving traces, concise introduction, interactive spending demo, six feature highlights, shared-payment example, short local-first summary, direct Android/iOS downloads and original animated guide-book. The owner clarified that detail pages should remove lengthy explanations rather than the essential homepage product story.
- Features: accounts, money moves, Lena / Dena, separate split bills, shared-payment example and interactive spending chart.
- Your data: local storage, optional Sheets sync, clean reports and backup limits.
- Download: Android preview, iOS Shortcut and matching Sheet templates.
- Guide: installation/migration, everyday tools, sync/backup FAQs, scripts and existing PDFs.
- 404: styled missing-page response with working links and resources on nested paths.

The book now opens the current HTML guide. Earlier PDFs remain available from its resources section. Self-hosted fonts, Lenis, GSAP, keyboard/menu fixes, themes and explicit Motion control remain. Category totals aggregate correctly: Petrol ₹711.75, Groceries ₹203 and Education ₹99; total ₹1,013.75.

## Verification

- `node tools/site-smoke/content-check.cjs`: passed across all six pages, checking local links/fragments, unique IDs, one h1 per page, current templates/versions, financial copy and APK/backend parity.
- `node --check distribution/main.js`: passed.
- Browser axe checks: zero violations on all six pages at 390 × 844 in light theme and 1440 × 900 in dark theme. Automated checks retain manual-review items and do not certify complete accessibility.
- Final home check at 360 × 844: no horizontal overflow (document width 345px), zero automated violations and active Lenis scrolling.
- Menu opened, Escape closed it and reset `aria-expanded` to false. Mobile navigation opened the separate download page.
- Chart Categories displayed the correct three totals; optional-sync control updated its illustration and explanatory caption.
- Original animated book completed navigation to `guide.html`.
- Explicit Motion off changed the scroll engine to native and reported motion off; Motion on was restored for the final preview.
- Light-theme contrast and accessible download/book names were corrected during checks. Optional Sheet text remains readable before sync is demonstrated.

Ignored local screenshots and audit JSON are in `tools/site-smoke/out/restored-pages/`. The rejected paper-slip design is preserved under `tools/site-smoke/out/paper-redesign-2026-10-20/`. Its earlier critic score does not assess this revision.

Real-phone performance, production hosting and browser-disabled JavaScript were not tested. Static/no-JavaScript content is included. No app/backend/download changes, Git push or live publication were performed for this revision.

## Homepage content balance follow-up

Restored essential product content to home after owner clarification: compact interactive spending demo, six feature highlights, shared-expense amounts, short local-first summary and direct Android/iOS download actions. Detailed explanations, setup, templates and FAQs remain available on separate pages. Original hero and book motion are retained. Homepage chart narrative has no viewport-height story steps or sticky chart, keeping the demo compact.

Content checks passed again. Fresh homepage axe scans at 1440 × 900 dark and 360 × 844 light reported zero violations and no horizontal overflow. Evidence: `tools/site-smoke/out/restored-pages/home-content-audits.json`, `home-highlights.png` and `home-highlights-mobile.png`. Real-device performance remains untested.
