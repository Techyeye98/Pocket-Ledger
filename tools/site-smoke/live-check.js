/* Pocket Ledger website — LIVE verification
 *
 * Drives the deployed GitHub Pages URL in headless Edge and asserts the things
 * that can only be proven in a real browser against the real server: the page
 * that is actually served is the V3 build, the donut renders 3 segments / 3
 * legend rows totalling 100%, the Android chip reads ok (the APK really is
 * reachable on the live host), and every local resource returns 200.
 *
 * Run:  node tools/site-smoke/live-check.js [url]
 */
const path = require("path");

const URL_BASE = process.argv[2] || "https://techyeye98.github.io/Pocket-Ledger/";
const puppeteer = require(path.resolve(
  __dirname, "..", "..", "distribution", "guides", "node_modules", "puppeteer-core"
));
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const results = [];
const fails = [];
function check(name, ok, detail) {
  if (!ok) fails.push(`${name}${detail ? " — " + detail : ""}`);
  results.push([name, ok ? "ok" : "FAIL" + (detail ? " · " + detail : "")]);
  return ok;
}

function auditDonut(page) {
  return page.evaluate(() => {
    const segs = Array.from(document.querySelectorAll(".donut .seg"));
    const visible = segs.filter((el) => getComputedStyle(el).display !== "none");
    const dash = visible.map((el) =>
      parseFloat(el.style.strokeDasharray || el.getAttribute("stroke-dasharray")) || 0);
    const rows = Array.from(document.querySelectorAll(".donut-legend .legend-row"));
    const names = rows.map((r) => {
      const span = r.querySelector("span");
      return r.textContent.replace(span ? span.textContent : "", "").trim();
    });
    const pcts = rows.map((r) => {
      const m = (r.querySelector("span") ? r.querySelector("span").textContent : "").match(/(\d+)\s*%/);
      return m ? parseInt(m[1], 10) : NaN;
    });
    return {
      painted: visible.length, segPcts: dash, legendRows: rows.length, names, pcts,
      pctSum: pcts.reduce((a, b) => a + b, 0),
      total: (document.getElementById("demoAmt") || {}).textContent
    };
  });
}

function assertDonut(label, d) {
  check(`${label}: 3 painted segments`, d.painted === 3, `painted=${d.painted}`);
  check(`${label}: 3 legend rows`, d.legendRows === 3, `rows=${d.legendRows}`);
  check(`${label}: percentages total 100`, d.pctSum === 100 && JSON.stringify(d.segPcts) === JSON.stringify(d.pcts),
    `legend=${d.pcts.join("/")} donut=${d.segPcts.join("/")}`);
  check(`${label}: only Petrol/Groceries/Education`, d.names.join(",") === "Petrol,Groceries,Education", d.names.join(","));
  results.push([`${label} detail`, JSON.stringify(d)]);
}

async function main() {
  console.log(`LIVE URL: ${URL_BASE}`);
  const browser = await puppeteer.launch({
    executablePath: EDGE, headless: true,
    args: ["--no-sandbox", "--disable-gpu", "--font-render-hinting=none",
      "--force-device-scale-factor=1", "--mute-audio"]
  });

  const errors = [];
  const page = await browser.newPage();
  page.on("console", (m) => { if (m.type() === "error") errors.push(`[console.error] ${m.text()}`); });
  page.on("pageerror", (e) => errors.push("[pageerror] " + e.message));
  page.on("requestfailed", (r) => {
    const note = String(r.failure ? r.failure().errorText : "");
    if (r.method() === "HEAD" && /ERR_ABORTED|ERR_FAILED/.test(note)) return; /* HEAD probe quirk */
    errors.push(`[requestfailed] ${r.method()} ${r.url()} :: ${note}`);
  });

  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
  await page.goto(URL_BASE, { waitUntil: "networkidle2", timeout: 60000 });
  await sleep(3500); /* cinematic veil + entry */

  const title = await page.title();
  check("live root serves the V3 page", /Your money, on your device, in your sheet/.test(title), title);
  /* the brand uses a non-breaking space, so normalise before comparing */
  const brand = await page.$eval(".brand span", (el) => el.textContent.replace(/\s+/g, " ").trim());
  check("no prototype badge in the live nav", brand === "Pocket Ledger", brand);
  const footer = await page.$eval(".footer-bottom", (el) => el.textContent.replace(/\s+/g, " ").trim());
  check("no 'not deployed' wording in the live footer", !/not deployed|v3 concept/i.test(footer), footer);
  results.push(["live title", title]);

  assertDonut("live donut Today", await auditDonut(page));

  /* period tabs drive the same 3-category donut */
  for (const p of ["Week", "Month", "Year"]) {
    await page.evaluate((n) => {
      const b = Array.from(document.querySelectorAll(".demo-tabs button")).find((x) => x.textContent.trim() === n);
      if (b) b.click();
    }, p);
    await sleep(1200);
    assertDonut(`live donut ${p}`, await auditDonut(page));
  }
  await page.evaluate(() => document.querySelector(".demo-tabs button").click());
  await sleep(1000);

  /* the live APK reachability chip — the whole point of the deployment */
  await page.evaluate(() => document.querySelector("#get-started").scrollIntoView());
  await sleep(1500);
  const dl = await page.evaluate(() => ({
    text: document.getElementById("dlState").textContent.trim(),
    cls: document.getElementById("dlState").className,
    fallbackHidden: document.getElementById("dlFallback").hidden,
    href: document.getElementById("dlBtn").getAttribute("href")
  }));
  results.push(["live APK chip", JSON.stringify(dl)]);
  check("live APK chip reads ok", /(^|\s)ok(\s|$)/.test(dl.cls), dl.text);
  check("live APK href is root-relative", dl.href === "downloads/Pocket-Ledger-Android-V2.apk", dl.href);

  /* every local resource the page references must return 200 on the live host */
  const localRefs = await page.evaluate(() => {
    const out = new Set();
    document.querySelectorAll("[href],[src]").forEach((el) => {
      const v = el.getAttribute("href") || el.getAttribute("src");
      if (v && !/^(https?:|mailto:|tel:|data:|#)/.test(v)) out.add(v);
    });
    return Array.from(out);
  });
  for (const rel of localRefs) {
    const s = await page.evaluate(async (u) => {
      try { const r = await fetch(u, { cache: "no-store" }); return r.status; } catch (e) { return 0; }
    }, rel);
    check(`live 200 · ${rel}`, s === 200, `status=${s}`);
  }

  /* interactions */
  await page.evaluate(() => document.querySelector("#features").scrollIntoView());
  await sleep(800);
  await page.click("#qlogKeys button[data-v='100']");
  await page.click("#qlogGo");
  await sleep(300);
  const qOut = await page.$eval("#qlogOut", (el) => el.textContent.trim());
  check("live quick-log tile logs ¥100", /100/.test(qOut), qOut.slice(0, 60));
  await page.click('#tagFilters button[data-filter="petrol"]');
  await sleep(300);
  const shown = await page.$$eval("#tagsList .tl-row", (rs) => rs.filter((r) => !r.classList.contains("hidden")).length);
  check("live tag filter shows 2 of 5 rows", shown === 2, `shown=${shown}`);

  await page.evaluate(() => {
    const sec = document.querySelector("#architecture");
    window.scrollTo(0, sec.getBoundingClientRect().top + window.scrollY + (sec.offsetHeight - window.innerHeight) * 0.5);
  });
  await sleep(700);
  const pl = await page.evaluate(() => ({
    left: document.getElementById("plPacket").style.left,
    tabs: document.querySelectorAll(".pl-tab.active").length
  }));
  check("live pipeline scrubs with one active stage", parseFloat(pl.left) > 30 && parseFloat(pl.left) < 70 && pl.tabs === 1, JSON.stringify(pl));

  await page.click("#themeToggle");
  await sleep(400);
  check("live theme toggle → light",
    (await page.evaluate(() => document.documentElement.getAttribute("data-theme"))) === "light");
  await page.click("#themeToggle");
  await sleep(300);
  check("live theme toggle → dark",
    (await page.evaluate(() => document.documentElement.getAttribute("data-theme"))) === "dark");

  await page.screenshot({ path: path.join(__dirname, "out", "live-desktop.png") });
  await page.close();

  /* mobile */
  const m = await browser.newPage();
  m.on("pageerror", (e) => errors.push("[mobile pageerror] " + e.message));
  await m.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  await m.goto(URL_BASE, { waitUntil: "networkidle2", timeout: 60000 });
  await sleep(3000);
  const ov = await m.evaluate(() => ({ scrollW: document.documentElement.scrollWidth, innerW: window.innerWidth }));
  check("live mobile: no horizontal overflow", ov.scrollW <= ov.innerW + 1, JSON.stringify(ov));
  await m.evaluate(() => document.querySelector("#heroDemo").scrollIntoView({ block: "center" }));
  await sleep(1200);
  assertDonut("live mobile donut", await auditDonut(m));
  await m.evaluate(() => window.scrollTo(0, 0));
  await sleep(300);
  await m.click("#burger");
  await sleep(400);
  const menu = await m.evaluate(() => ({
    open: document.getElementById("mobileMenu").classList.contains("open"),
    locked: getComputedStyle(document.body).overflow
  }));
  check("live mobile menu opens", menu.open && menu.locked === "hidden", JSON.stringify(menu));
  await m.screenshot({ path: path.join(__dirname, "out", "live-mobile-menu.png") });
  await m.click("#burger");
  await sleep(200);
  check("live mobile menu closes",
    (await m.evaluate(() => document.getElementById("mobileMenu").classList.contains("open"))) === false);
  await m.close();

  await browser.close();

  console.log("\n=== LIVE CHECKS ===");
  results.forEach(([k, v]) => console.log(`  [${k}] ${v}`));
  console.log(`\nLive console/page errors: ${errors.length ? errors.join(" | ") : "none"}`);
  check("live: no console/page errors", errors.length === 0, errors.join(" | "));

  if (fails.length) {
    console.log(`\n!!! ${fails.length} LIVE CHECK(S) FAILED:`);
    fails.forEach((f) => console.log("  - " + f));
    process.exitCode = 1;
  } else {
    console.log(`\nLIVE SITE VERIFIED — all ${results.length} checks passed`);
  }
}

main().catch((e) => { console.error("LIVE CHECK FAILED:", e); process.exit(1); });
