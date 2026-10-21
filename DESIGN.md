# Current direction — original design, separate detail pages

Latest owner correction: replace both homepage screenshot windows with native HTML/CSS financial examples, labelled Example. No simulated phone frame or raster app UI. Use the established typography, rules and palette; interactions illustrate a transfer preserving total money and a partial friend repayment. Keep one book link in the guide invitation, remove repeated body calls to the same destination and redundant footer Guide navigation. This supersedes the screenshot-preview choice below; the illustrated handbook retains its real captures.

21 October refinement: preserve the original hero traces, statement (including “Your accounts. Your share.”), interactive chart, guide-book motion and shared Lenis/GSAP clock. Replace the homepage's equal six-feature grid with two prominent real app focus windows for Accounts and Split bills, then four compact highlights. These approved original captures are framed in native SVG without modified app text or private data. Keep the shared-money example. Tighten the privacy strip and guide invitation. Features, data and download pages share lead type scale, top spacing, back-home link and semantic theme tokens; the handbook retains its calmer reading layout within the same navigation, fonts and palette. No new animation driver, dependency, app data change or publication.

Updated 20 October 2026 after owner review. The owner prefers the original neon traces, animated guide-book, typography and motion to the paper-slip redesign below. Preserve that visual design. Retain the newer short-copy approach and separate pages.

The homepage contains the original hero, short statement, compact spending demo, six feature highlights, shared-payment example, brief local-first summary, direct Android/iOS downloads and animated book. The owner clarified that useful product content belongs on home; only long descriptive copy, setup and FAQs move to detail pages. Features, data/sync, downloads and setup each have their own page with consistent navigation and current-page indication. The book now opens the current HTML guide rather than the earlier PDF. The PDF remains linked from guide resources and labelled as an earlier edition.

Keep locally hosted Lenis, fonts and the shared GSAP clock, correct category totals, keyboard/menu fixes, reduced motion, theme persistence and factual financial/backup limits. No app/backend/download changes or publication belong to this revision.

The archived design and its independent review below are historical; their score does not assess the current revision.

---

# Pocket Ledger: Money finds its place

Local website design pass, 20 October 2026. Uses the installed Awwwards skill, Lenis and GSAP. Existing static HTML/CSS/JS is the right stack: the product needs a fast download page and readable setup, not a framework migration.

## Brief and concepts

Audience: people tracking their everyday money and shared expenses. Goal: understand the local Android ledger, distinguish the simpler iOS Shortcut, and reach the correct download/setup. Keep the green identity, compact copy and readable word spacing. All sample amounts are explicitly illustrative. No new app screenshots, awards, usage counts or testimonials are invented.

| Territory | Concept | Signature | Originality / fit / feasibility / portability |
| --- | --- | --- | --- |
| Name and material | Pocket slips: a small stack separates money into its purpose | Ledger slips settle into place in the hero | 4 / 5 / 5 / 3 |
| Shared-money workflow | A payment forks into your share and what comes back | ₹900 branches to ₹300 spending and ₹600 receivable | 5 / 5 / 5 / 1 |
| Daily habit | A day's scattered expenses gather into categories | An interactive spending chart aggregates category totals | 3 / 4 / 5 / 3 |

Choose the shared-money workflow, with pocket slips as its material language. It explains this app's real allocation model instead of reproducing the skill's sample running-balance concept. The chart is a quiet supporting interaction.

## Concept card

- Idea: Money finds its place, even when you paid for someone else.
- Metaphor: small ledger slips separating a real payment into the amounts that matter.
- Signature: an SVG branch draws with scroll progress; the two destinations settle into place.
- Emotion: relief and clarity.
- Motion verb: settle. One GSAP ticker coordinates Lenis, the diagram and the hero's subtle idle motion.
- Screenshot: the oversized payment branching into personal spending and money to receive.

## System

Palette: forest ground #081e18, quiet surface #102d24, readable ink #eff5e8, secondary ink #b0c5b8, paper #e6eedc, soft green accent #c4edac. Green comes from the established product identity. The paper scene breaks the formerly uniform dark page. Light mode reverses semantic tokens rather than changing isolated text colors.

Type: existing Archivo variable for display/data, DM Sans variable for text/UI. Self-hosted WOFF2, original OFL licenses retained. Display tracking around -.04em with explicit word spacing; body 16px at 1.55-1.7. Mobile display scales independently.

Layout: fluid 12-column concept, 5vw gutters, no nested card scaffolding. Six short features in a flat grid, mobile icon/text rows. Border radius 4px for controls/slips, 8px for download panels. Buttons lift/settle and arrows travel; nav underlines draw. No replacement cursor, loader, GL or pinned scroll trap.

Section wireframes:

```text
Hero:       [headline + short CTA]    [overlapping ledger slips]
Money:             centered premise
                         ₹900
                        /    \
                    ₹300      ₹600
Features:   [large heading]          [short platform distinction]
            [icon+copy] [icon+copy] [icon+copy]   (two rows)
Day:        [short narrative]        [interactive sample chart]
Control:    [local-first premise]    [phone -- optional -- sheet]
Download:   [Android, wider]         [iOS Shortcut]
            [matching templates, compact row]
Guide:      one bold help link into a separate readable guide
Footer:     small signature / oversized product name / useful links
```

Genericness check: a neon curve over a huge headline could belong to any finance app. Replace that with the actual shared-payment model and paper slips. Keep the green brand, vary section compositions, and remove the old wall of setup text from the homepage. The hero slips are an editorial illustration, explicitly not an app UI mockup.

## Motion and fallbacks

Lenis uses local pinned assets, autoRaf:false, native touch, and GSAP's single ticker. Reduced motion destroys smoothing and restores the complete static diagram. All text exists in HTML and is visible without JS; the chart's JS controls are progressive enhancement. Anchor navigation uses one header-offset source and preserves hashes/focus. Native disclosures trigger size refresh. Cross-document View Transitions are an optional enhancement, never a navigation requirement.

The hero has a small breathing movement only while visible; no busy background effects. Guide and 404 are intentionally still. No payment amount changes or fabricated live data.

## Scope and validation

Local package only. Existing APK, Shortcut, scripts, templates and PDFs retained. New HTML guide supplements the earlier PDF; fresh app screenshot/PDF work stays deferred. Validate desktop/mobile, both themes, disclosure/menu/keyboard, anchors/history, reduced-motion and no-JS content, source link integrity and real 404 status. Record measured limitations instead of claiming an award or unmeasured performance score.


## Review iterations

Two fresh independent screenshot critics reviewed the local package. The first pass identified mobile caption collisions, incomplete feature evidence and ambiguous chart grouping. The implementation now places the illustration caption in normal flow with reserved space, protects copy above the artwork, and aggregates the sample chart into three actual category totals. Hero motion no longer reduces text opacity. Further keyboard/history, explicit motion-control, focus/hover and metadata evidence is recorded in the verification note.

The hero logo uses a pixel-identical lossless WebP encoding of the original artwork; the PNG is retained for Apple touch icons. No app screenshots are fabricated. Real device performance and the new screenshot PDF remain follow-up work.
