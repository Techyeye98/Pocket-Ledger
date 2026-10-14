const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const here = __dirname;
const html = fs.readFileSync(path.join(here, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(here, 'style.css'), 'utf8');
const js = fs.readFileSync(path.join(here, 'main.js'), 'utf8');
const ids = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]));
const anchors = [...html.matchAll(/\bhref="#([^"]+)"/g)].map(match => match[1]);
for (const anchor of anchors) assert(ids.has(anchor), `Missing internal anchor #${anchor}`);
for (const name of ['style.css', 'main.js']) assert(fs.existsSync(path.join(here, name)), `Missing ${name}`);

const liveDestinations = [
  'https://techyeye98.github.io/Pocket-Ledger/downloads/Pocket-Ledger.apk',
  'https://techyeye98.github.io/Pocket-Ledger/downloads/Pocket-Ledger-iPhone.shortcut',
  'https://docs.google.com/spreadsheets/d/1vu140rEacxVWVeWVil1BvjcWNFS2xyJII1b-rjoisrw/copy',
  'https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy',
  'Pocket-Ledger-Quick-Guide-2026-10.pdf',
  'https://techyeye98.github.io/Pocket-Ledger/apps-script/Pocket-Ledger-Apps-Script.gs.txt',
  'https://github.com/Techyeye98/Pocket-Ledger',
  'https://github.com/Techyeye98/Pocket-Ledger/issues'
];
for (const destination of liveDestinations) assert(html.includes(`href="${destination}"`), `Missing live destination ${destination}`);
assert(fs.existsSync(path.join(here, 'Pocket-Ledger-Quick-Guide-2026-10.pdf')), 'Missing new PDF guide');
assert(css.includes('@media (prefers-reduced-motion:reduce)'), 'Missing reduced-motion CSS');
assert(js.includes('reducedMotion.matches'), 'Missing reduced-motion JS guard');
assert(css.includes('.trace-main') && css.includes('draw-trace'), 'Missing hero motion');
assert(css.includes('halo-breathe') && css.includes('trace-gleam'), 'Missing post-intro ambient hero motion');
assert(js.includes("hero.addEventListener('pointermove'") && js.includes("hero.addEventListener('pointerleave'"), 'Missing pointer depth and settle');
assert(js.includes('finePointer.matches'), 'Pointer motion must be fine-pointer gated');
assert(html.includes('id="visual-detail"') && js.includes("bar.addEventListener('focus'"), 'Missing keyboard-accessible expense detail');
assert(js.includes("bar.addEventListener('pointerenter'") && js.includes("bar.addEventListener('click'"), 'Missing hover and tap expense highlight');
assert(js.includes('categoryOrder') && js.includes('setView'), 'Missing data transformation');
assert(js.includes('IntersectionObserver'), 'Missing scroll storytelling');

console.log(`PASS: ${anchors.length} internal anchors, ${liveDestinations.length} preserved live destinations, motion and reduced-motion safeguards.`);
