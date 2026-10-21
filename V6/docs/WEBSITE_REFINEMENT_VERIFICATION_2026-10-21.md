# Local website refinement — 21 October 2026

Owner direction: keep the original motion/design and “Your accounts. Your share.” statement, improve the homepage product presentation, harmonize detail pages and verify current links. All changes remain local.

## Changes

- Retained original hero traces, statement, chart, guide-book interaction and Lenis/GSAP engine. No edits to motion JavaScript in this pass.
- Replaced the equal homepage six-feature grid with Accounts and Split bills focus windows using two already-reviewed original app captures; four smaller everyday highlights follow.
- Used native SVG framing with exact screenshot pixels; no fabricated app UI or personal balances.
- Clarified hero copy and tightened the privacy strip, download heading and guide invitation.
- Matched Features, Your data and Downloads lead type scale, spacing and back-home link. Guide retains its task-oriented reading layout with shared theme/fonts/navigation.
- Updated old guide-hub fragment links to the relevant current chapters.

## Verification

- `node tools/site-smoke/content-check.cjs`: passed. APK matches current local debug build; distributed Android script matches canonical backend; current platform URLs, resources, anchors and README versions verified.
- `python tools/site-smoke/link-audit.py`: 15 pages, 585 local references, no missing files or fragment targets. Five unique external visitor links return HTTP 200 or a sign-in redirect. Public canonical metadata is intentionally not asserted against a deployment because this work is local.
- Signed-in browser verified iOS link: “Pocket Ledger — iOS - Make a copy”. No copy was made.
- **Android template access remains restricted:** supplied latest URL displays “You need access” in the connected browser. Retained the owner-supplied URL and did not change cloud permissions. An anonymous HTTP 200 sign-in redirect is not proof that visitors can copy it.
- All 15 routes checked at 1440×900 dark theme and 390×844 light theme: no automated accessibility violations and no horizontal overflow. Some automated checks remain incomplete; this is not exhaustive accessibility certification.
- Visual inspection: desktop app preview framing, mobile previews, inner-page heading, and shared guide layout. Mobile menu opens and navigates correctly. Chart category mode preserves Petrol ₹711.75, Groceries ₹203, Education ₹99; same ₹1,013.75 total.
- Motion off switches to native scrolling; restoring Motion on reinitializes Lenis. Temporary viewport overrides reset before delivery.
- Existing 14-page PDF and its 12 original screenshot hashes still pass guide verification.

Browser reports/screenshots and HTTP output are stored under ignored `tools/site-smoke/out/home-refinement/`. No APK, financial app data, Sheet contents, deployment, cloud sharing or Git remote was changed during this pass.
