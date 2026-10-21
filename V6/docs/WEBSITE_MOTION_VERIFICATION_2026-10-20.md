# Archived paper-slip redesign verification — 20 October 2026

> Superseded by the owner-requested original-design restoration and separate-page revision. The checks and score below apply to the archived paper-slip design. See `WEBSITE_PAGES_VERIFICATION_2026-10-20.md` for the current revision.

## Delivered locally

The static distribution site now follows the “Money finds its place” concept recorded in [DESIGN.md](../../DESIGN.md). The existing HTML/CSS/JavaScript stack was retained to avoid a framework migration for a static product site.

- Shorter homepage copy, clear word spacing, fluid Archivo display typography and DM Sans body text, with self-hosted licensed fonts.
- An illustrative paper-ledger hero and a shared-payment explanation: ₹900 paid becomes ₹300 personal spending and ₹600 receivable. These are clearly marked examples, not app screenshots or live financial data.
- Local Lenis 1.3.27 and GSAP 3.15.0 assets. GSAP supplies the single animation ticker; native touch scrolling remains available.
- Scroll-drawn money flow, restrained hero movement, interactive sample spending chart, and optional-sync illustration.
- Separate Android/iOS downloads and Sheet templates, current preview information, a readable HTML setup guide and a designed 404 page.
- Light/dark themes, explicit Motion control, keyboard navigation, reduced-motion handling, and static/no-JavaScript content fallbacks.
- Updated root and distribution READMEs, metadata, social image, robots file and sitemap.

The Android APK, Shortcut, canonical backend scripts and previous PDF were retained. The PDF is identified as an earlier edition; new app screenshot/PDF work remains deferred. Nothing was pushed or published.

## Checks performed

| Check | Result and scope |
| --- | --- |
| Content integrity | `node tools/site-smoke/content-check.cjs` passed: current links/versions, resources, financial copy, private-data exclusion, APK identity and canonical Android script byte parity. |
| JavaScript syntax | `node --check distribution/main.js` passed. |
| Responsive browser review | Desktop and phone layouts reviewed in both themes. Final 360 × 844 inspection reported document width 345px, with no horizontal overflow. |
| Automated accessibility | Axe reported zero violations on the tested homepage, guide and 404 states. Final narrow-phone audit: 24 passes; manual-review items remained for contrast, prohibited ARIA attributes and label/name matching. This is not an exhaustive accessibility certification. |
| Navigation and controls | Mobile menu/Escape/focus loop, skip link, themes, chart category totals, optional-sync toggle, motion toggle, keyboard scrolling, anchors and history were exercised. |
| Motion disabled | Explicit site control restored native scrolling and complete static diagram while preserving position. Operating-system reduced-motion behavior is implemented but was not separately emulated. |
| HTTP | Homepage/resources/guide returned 200; unknown routes returned a real 404 with the designed page. Nested missing paths loaded their absolute asset links. |
| Source audit | Heading/metadata/resource checks passed. Local runtime JavaScript was approximately 56.6KB gzipped. Referenced assets including the retained Apple icon were approximately 2.64MB; these are source totals, not a production network benchmark. |

Local unthrottled desktop observation recorded LCP about 128ms and CLS about 0.0012 through the first completed audit. These localhost observations do not establish production or real-device performance. QA instrumentation is served only by the local preview server with `?__qa=1`; it is not injected into shipped distribution files.

## Review and corrections

Two independent screenshot critics reviewed separate frozen evidence sets. Pass one estimated 7.17/10 and prompted fixes for mobile artwork/caption collisions and ambiguous chart grouping. The chart now aggregates the sample into Petrol ₹711.75, Groceries ₹203 and Education ₹99, totaling ₹1,013.75.

Pass two estimated Design 7.8, Usability 7.4, Creativity 7.3 and Content 7.8: weighted **7.57/10**, rounded to **7.6**. No hard user-facing functional defect was confirmed in that review. This is an internal estimate, not an Awwwards jury result or award. Meaningful small labels were enlarged after that evidence snapshot.

Implementation fixes also addressed competing transform properties, Lenis/native keyboard interaction, stale anchor positions after history navigation, text opacity during animation, and motion restarts unexpectedly repositioning the page.

## Evidence and remaining limits

Local evidence is retained under ignored `tools/site-smoke/out/awwwards/`, including frozen `pass1/` and `pass2/` captures, source/HTTP/interaction reports, `final-desktop.png` and `final-mobile-360.png`.

Physical-phone performance, throttled network/CPU testing, foreground frame-rate measurement, exhaustive keyboard traversal, browser-disabled JavaScript testing and temporal View Transition inspection were not completed. Actual app screenshots would strengthen the product presentation; none were fabricated. The large original Apple touch icon remains a future optimization opportunity.

Run the preview with:

```powershell
node tools/site-smoke/v6-publication.cjs --serve
```

Open `http://127.0.0.1:4829/Pocket-Ledger/`.
