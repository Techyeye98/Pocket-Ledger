> Implementation update: the local website now uses the plan below. Lenis/GSAP, the money-flow scene, current HTML guide, 404, self-hosted fonts and Motion control are active. See root DESIGN.md and WEBSITE_MOTION_VERIFICATION_2026-10-20.md for the final choices and verification. Earlier planning notes below record the pre-implementation state.

# Pocket Ledger: Awwwards skill and Lenis

20 October 2026. Research and installation only; no visual redesign or deployment in this step.

## Installed and verified

| Item | Result |
| --- | --- |
| `juliobsz/skills`, `awwwards/` | Installed in `C:/Users/namya/.codex/skills/awwwards`. The skill and its references/scripts are present. Available to Codex on the next turn. |
| `darkroomengineering/lenis` | A JavaScript scrolling library, not an agent skill. The repository tree has no `SKILL.md`. |
| Lenis 1.3.27 | Official npm browser JS, CSS and MIT license staged in `distribution/vendor/lenis/`; package SHA-512 verified. Origin and asset hashes recorded alongside them. Not yet referenced by the website. |

Awwwards already includes Lenis integration guidance in its motion-engine reference. No invented Lenis skill or extra framework is needed.

## How the two fit together

The Awwwards skill guides concept, composition, typography, motion and browser review. Lenis supplies the scroll interpolation; it does not design a page or animate features by itself. A GSAP timeline/ScrollTrigger could coordinate a more involved scroll scene if that scene justifies another dependency.

Keep the existing static HTML/CSS/JavaScript website in `distribution/`. There is no need to migrate to Next.js or Astro for this product landing page. Retain the recent compact copy, generous word spacing, separate Android/iOS routes, and the user's green brand palette.

## Proposed creative direction

**Money finds its place.** One movement separates into what you spent and what comes back.

Use the existing illustrative example: pay ₹900, keep ₹300 as personal spending and ₹600 as money to receive. As the visitor scrolls, one line branches into those two amounts. It explains a distinctive real feature with very little text. Mark it as sample data. It is an illustration, not a fabricated app screenshot.

| Area | Proposed treatment |
| --- | --- |
| Hero | Keep the readable two-line headline. Refine the existing money traces into the same visual language as the shared-expense branch. One short entrance; download CTA visible immediately. |
| Shared money | Make this the one signature scroll scene. Amount labels remain readable and meaningful even when motion is off. Avoid a long pinned section. |
| Features | Preserve short summaries and simple icons. Use the app's real screenshots only when fresh captures are available. Do not turn the page back into a manual. |
| Buttons and navigation | Consistent hover, focus and press feedback; obvious platform downloads and short anchor transitions. |
| Setup and FAQ | Keep the three short steps and native disclosures. Detailed instructions appear only when opened. |
| Phone layout | Compose a vertical version of the money flow. Keep touch scrolling native and retain all content. |

## Lenis integration notes for this source

Current code has CSS `scroll-behavior:smooth`, header progress driven by a scroll listener, pointer motion scheduled with requestAnimationFrame, IntersectionObserver reveals/story changes, and a guide-book `scrollIntoView` action. Review these together before adding another scroll controller.

1. Load the pinned local `lenis.min.js` and `lenis.css` before the site's own script, once integration begins. Keep native scrolling available if initialization fails.
2. Start with subtle desktop wheel smoothing, `syncTouch:false`, and explicit reduced-motion support. Avoid forced snapping and locked scroll.
3. When Lenis controls scrolling, override CSS smooth behavior so two smoothing systems do not compete. Use one header-offset source for anchors; the current scroll padding and section scroll margins must not accidentally double that offset.
4. If adding GSAP for the signature scene, use its ticker as the single JavaScript animation clock. Set Lenis `autoRaf:false`, call `lenis.raf(time * 1000)` from the ticker, and connect its scroll event to `ScrollTrigger.update`. Do not also start Lenis's automatic loop. Refactor the existing JavaScript animation loops into that clock during the same pass.
5. Route header progress through the shared scroll feed. Keep IntersectionObserver where appropriate, and confirm sticky sample charts and anchor navigation still work.
6. Revisit the guide-book programmatic scroll so Lenis's internal target agrees with the actual scroll position. Preserve document hashes, browser history and keyboard focus when handling navigation.
7. Under reduced motion, use native scrolling and a complete static money-flow diagram. Respond to preference changes during the session; all content must stay visible without JavaScript too.
8. Native disclosures change page height. Check resize behavior after expanding setup/FAQ, changing theme, loading fonts and rotating the phone.

## Acceptance for the implementation pass

- Desktop and 360/390px mobile: no clipped headings, overflow or sticky content covering controls.
- Wheel, touch, keyboard, skip link, anchor links, Back/Forward and direct hash navigation all work.
- Both themes and reduced motion retain readable labels and complete information.
- FAQ opening/closing, guide link and downloads still work; no extra work required to reach an APK.
- One coordinated money-flow scene; no heavy loader, replacement cursor or decorative WebGL added merely for spectacle.
- Measure frame pacing and loading rather than promising that a library automatically improves speed. Use a physical phone for final touch/performance acceptance.
- Browser screenshot review and accessibility checks before calling the redesign complete. No awards or jury scores claimed without an actual review.

## Sources read

- [Awwwards skill](https://github.com/juliobsz/skills/blob/main/awwwards/SKILL.md): existing-stack support, concept/system workflow, restrained signature motion and QA.
- [Awwwards motion engine](https://github.com/juliobsz/skills/blob/main/awwwards/references/engine.md): shared clock, Lenis/GSAP wiring, cleanup and static fallbacks.
- [Official Lenis documentation](https://github.com/darkroomengineering/lenis#readme): installation, stylesheet, anchors, GSAP synchronization, touch and reduced-motion settings.

No website behavior was changed and no live publication or Git push was performed by this installation step. The staged library's scrolling has not been browser-tested because it is not active yet.
