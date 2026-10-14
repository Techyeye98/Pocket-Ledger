/* Render the actual Pages package, or pass a deployed URL to verify it live. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const puppeteer = require('../../distribution/guides/node_modules/puppeteer-core');
const root = path.resolve(__dirname, '../../distribution');
const out = path.join(__dirname, 'out/v6-publication');
const serveOnly = process.argv[2] === '--serve';
const live = serveOnly ? null : process.argv[2];
const base = live || 'http://127.0.0.1:4829/Pocket-Ledger/';
const mime = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.png': 'image/png', '.pdf': 'application/pdf', '.apk': 'application/vnd.android.package-archive' };
const server = http.createServer((req, res) => {
  const relative = decodeURIComponent(new URL(req.url, base).pathname).replace(/^\/Pocket-Ledger\//, '') || 'index.html';
  const file = path.resolve(root, relative);
  if (!file.startsWith(root + path.sep)) { res.writeHead(403).end(); return; }
  fs.readFile(file, (error, data) => {
    if (error) { res.writeHead(404).end(); return; }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
});
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

(async () => {
  let browser;
  try {
    if (!live) await new Promise(resolve => server.listen(4829, '127.0.0.1', resolve));
    if (serveOnly) { console.log(base); await new Promise(() => {}); }
    fs.mkdirSync(out, { recursive: true });
    browser = await puppeteer.launch({ executablePath: process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true, args: ['--no-sandbox', '--disable-gpu', '--disable-features=msEdgeStartupBoost'] });
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setViewport({ width: 1440, height: 900 });
    await page.goto(base, { waitUntil: 'networkidle2' });
    await sleep(3500);
    assert.equal(await page.$eval('h1', el => el.textContent.replace(/\s+/g, ' ').trim()), 'WHERE DIDTODAY GO?');
    assert.equal(await page.$eval('.wordmark-logo', el => el.naturalWidth > 0), true);
    assert.equal(await page.$$eval('.viz-bar', els => els.length), 5);
    const before = await page.$eval('.hero-traces', el => getComputedStyle(el).transform);
    await page.mouse.move(1250, 250);
    await sleep(650);
    const after = await page.$eval('.hero-traces', el => getComputedStyle(el).transform);
    assert.notEqual(after, before, 'Hero depth must still respond after intro');
    const point = () => page.$eval('.traveler-one', el => { const b = el.getBoundingClientRect(); return [b.x, b.y]; });
    const first = await point(); await sleep(700);
    assert.notDeepEqual(await point(), first, 'Hero traveler must continue moving');
    await page.screenshot({ path: path.join(out, 'desktop-dark.png') });
    await page.$eval('#features', el => el.scrollIntoView());
    await sleep(700);
    await page.$eval('.viz-bar', el => el.focus());
    assert(!(await page.$eval('#visual-detail', el => el.textContent)).includes('Hover, focus'), 'Focus should show expense detail');
    await page.click('[data-view="categories"]');
    assert.equal(await page.$eval('[data-view="categories"]', el => el.getAttribute('aria-pressed')), 'true');
    await page.$eval('#sync-demo', el => el.click());
    assert.equal(await page.$eval('#sync-demo', el => el.getAttribute('aria-pressed')), 'true');
    await page.click('#theme-toggle');
    assert.equal(await page.$eval('html', el => el.dataset.theme), 'light');
    await page.$eval('#top', el => el.scrollIntoView()); await sleep(350);
    await page.screenshot({ path: path.join(out, 'desktop-light.png') });
    for (const width of [390, 360]) {
      await page.setViewport({ width, height: 844 });
      await page.goto(base, { waitUntil: 'networkidle2' });
      await sleep(500);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `Overflow at ${width}`);
      await page.click('#menu-toggle');
      assert.equal(await page.$eval('#menu-toggle', el => el.getAttribute('aria-expanded')), 'true');
      await page.click('#mobile-nav a[href="#manual"]');
      await sleep(350);
      assert.equal(await page.$eval('#menu-toggle', el => el.getAttribute('aria-expanded')), 'false');
      await page.screenshot({ path: path.join(out, `mobile-${width}.png`) });
    }
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }]);
    await page.reload({ waitUntil: 'networkidle2' });
    assert.equal(await page.$eval('.traveler-one', el => getComputedStyle(el).display), 'none');
    const guides = await page.$$eval('[data-guide-open]', els => els.map(el => el.href));
    assert.equal(guides.length, 2);
    assert(guides.every(url => url.endsWith('/guides/Pocket-Ledger-Quick-Guide-2026-10.pdf')));
    const apk = await page.$eval('a[aria-label="Download Pocket Ledger 6.0.8 Android APK"]', el => el.href);
    assert(apk.endsWith('/downloads/Pocket-Ledger.apk'));
    for (const asset of ['guides/Pocket-Ledger-Quick-Guide-2026-10.pdf', 'downloads/Pocket-Ledger.apk', 'downloads/Pocket-Ledger-iPhone.shortcut', 'apps-script/Pocket-Ledger-Apps-Script.gs.txt']) {
      const response = await fetch(new URL(asset, base));
      assert.equal(response.status, 200, asset);
      const bytes = Buffer.from(await response.arrayBuffer());
      if (asset.endsWith('.pdf')) assert.equal(bytes.subarray(0, 5).toString(), '%PDF-');
      if (asset.endsWith('.apk')) assert.equal(bytes.subarray(0, 2).toString(), 'PK');
      if (asset.endsWith('.txt')) assert(bytes.toString().includes('Spending Type'));
    }
    assert.deepEqual(errors, []);
    console.log(`PASS ${base}: desktop/mobile, themes, post-intro pointer/ambient motion, keyboard expense detail, reduced motion, guide and downloads.`);
  } finally {
    if (browser) await browser.close();
    if (!live) server.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
