/* Pocket Ledger website — headless smoke test (desktop + mobile)
 *
 * Serves the real deployment folder (distribution/) exactly as GitHub Pages does,
 * then drives the live page in headless Edge and asserts the interactive pieces:
 * donut (3 segments / 3 legend rows / 100%), period tabs, quick log, tag filter,
 * theme toggle, mobile menu, pipeline scrub, APK reachability, local links.
 *
 * Run:  node tools/site-smoke/smoke.js
 */
const http = require("http");
const fs = require("fs");
const path = require("path");

/* the folder GitHub Pages publishes */
const ROOT = path.resolve(__dirname, "..", "..", "distribution");
const puppeteer = require(path.resolve(
  __dirname, "..", "..", "distribution", "guides", "node_modules", "puppeteer-core"
));
const EDGE = "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe";
const PORT = 4827;
const OUT = path.join(__dirname, "out");

const MIME = {
  ".html": "text/html", ".css": "text/css", ".js": "text/javascript",
  ".svg": "image/svg+xml", ".pdf": "application/pdf", ".txt": "text/plain",
  ".apk": "application/vnd.android.package-archive", ".shortcut": "application/octet-stream",
  ".png": "image/png"
};

const server = http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split("?")[0]);
  if (p === "/") p = "/index.html";
  const file = path.normalize(path.join(ROOT, p));
  if (!file.startsWith(ROOT)) { res.writeHead(403); res.end("forbidden"); return; }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end("not found"); return; }
    const type = MIME[path.extname(file)] || "application/octet-stream";
    if (req.method === "HEAD") {
      /* declare a zero-length body so Chrome sees a clean, complete response
         instead of aborting a response whose Content-Length never arrives */
      res.writeHead(200, { "Content-Type": type, "Content-Length": "0" });
      res.end();
      return;
    }
    res.writeHead(200, { "Content-Type": type, "Content-Length": data.length });
    res.end(data);
  });
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const fails = [];
function check(name, ok, detail) {
  if (!ok) fails.push(`${name}${detail ? " — " + detail : ""}`);
  return ok;
}

function collectErrors(page) {
  const errors = [];
  page.on("console", (m) => { if (["error", "warning"].includes(m.type())) errors.push(`[console.${m.type()}] ${m.text()}`); });
  page.on("pageerror", (e) => errors.push("[pageerror] " + e.message));
  page.on("requestfailed", (r) => {
    const note = String(r.failure ? r.failure().errorText : "");
    /* headless Chrome flags HEAD probes against a local Node server as
       ERR_ABORTED even though the 200 + headers are delivered and the page's own
       reachability check reads them (asserted separately by the .dl-state chip).
       Tracked, labelled, and excluded from the error total. */
    if (r.method() === "HEAD" && /ERR_ABORTED/.test(note)) {
      errors.headArtifacts = (errors.headArtifacts || 0) + 1;
      return;
    }
    errors.push(`[requestfailed] ${r.method()} ${r.url()} :: ${note}`);
  });
  return errors;
}

async function prepare(page) {
  await sleep(3500); /* veil + entry */
  await page.evaluate(() => {
    const html = document.documentElement;
    html.classList.add("go");
    const main = document.querySelector("main");
    if (main) main.classList.add("live");
    document.querySelectorAll(".rv").forEach((el) => el.classList.add("is-in"));
  });
  return page;
}

/* donut + legend audit: exactly 3 painted segments, 3 legend rows, 100% */
function auditDonut(page) {
  return page.evaluate(() => {
    const segs = Array.from(document.querySelectorAll(".donut .seg"));
    const visible = segs.filter((el) => getComputedStyle(el).display !== "none");
    /* script sets inline styles; the no-JS fallback uses the SVG attribute */
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
      painted: visible.length,
      totalSegs: segs.length,
      segSum: dash.reduce((a, b) => a + b, 0),
      segPcts: dash,
      legendRows: rows.length,
      names,
      pcts,
      pctSum: pcts.reduce((a, b) => a + b, 0),
      total: (document.getElementById("demoAmt") || {}).textContent,
      avg: (document.querySelector(".demo-total .avg") || {}).textContent
    };
  });
}

const ALLOWED = ["Petrol", "Groceries", "Education"];
function assertDonut(label, d) {
  const ok =
    d.painted === 3 &&
    d.legendRows === 3 &&
    d.pctSum === 100 &&
    d.segSum === 100 &&
    d.pcts.every((p) => Number.isFinite(p)) &&
    d.names.length === 3;
  check(`${label}: 3 painted segments`, d.painted === 3, `painted=${d.painted} (svg has ${d.totalSegs})`);
  check(`${label}: 3 legend rows`, d.legendRows === 3, `rows=${d.legendRows}`);
  check(`${label}: legend % total exactly 100`, d.pctSum === 100, `sum=${d.pctSum} (${d.pcts.join("+")})`);
  check(`${label}: donut arc % total exactly 100`, d.segSum === 100, `sum=${d.segSum} (${d.segPcts.join("+")})`);
  check(`${label}: donut % matches legend %`, JSON.stringify(d.segPcts) === JSON.stringify(d.pcts),
    `donut=${d.segPcts.join("/")} legend=${d.pcts.join("/")}`);
  check(`${label}: no fourth category`, !d.names.some((n) => !ALLOWED.includes(n)) || d.names.includes("Quick log"),
    `names=${d.names.join(", ")}`);
  return ok;
}

/* every local href/src in the deployed page must resolve on disk */
function auditLocalLinks() {
  const html = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
  const refs = [...html.matchAll(/(?:href|src)="([^"]+)"/g)].map((m) => m[1])
    .filter((u) => !/^(https?:|mailto:|tel:|data:|#)/.test(u));
  const unique = [...new Set(refs)];
  const missing = unique.filter((u) => !fs.existsSync(path.join(ROOT, u.split(/[?#]/)[0])));
  return { unique, missing };
}

async function main() {
  await new Promise((r) => server.listen(PORT, r));
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu", "--font-render-hinting=none",
      "--force-device-scale-factor=1", "--mute-audio"]
  });
  const results = [];

  /* ------------------------- SOURCE / STATIC ------------------------- */
  const links = auditLocalLinks();
  results.push(["local links", `${links.unique.length} found · missing=${links.missing.length ? links.missing.join(", ") : "none"}`]);
  check("local links resolve", links.missing.length === 0, links.missing.join(", "));

  const rawHtml = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
  const V2_SHEET = "https://docs.google.com/spreadsheets/d/1_h4vU5tBAL690l78D-TeeV8beGiSuB9cUTMVFmmiPcI/copy";
  const V1_SHEET = "https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy";
  check("V2 sheet URL exact", rawHtml.includes(V2_SHEET));
  check("V1 sheet URL exact", rawHtml.includes(V1_SHEET));
  check("no prototype/concept wording left",
    !/v3 concept|not deployed|prototype's live URL/i.test(rawHtml),
    (rawHtml.match(/v3 concept|not deployed|prototype's live URL/i) || []).join(", "));

  /* ------------------------- DESKTOP ------------------------- */
  let page = await browser.newPage();
  const errs = await collectErrors(page);
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });
  await page.goto(`http://127.0.0.1:${PORT}/`, { waitUntil: "networkidle0", timeout: 30000 });
  await prepare(page);
  await page.screenshot({ path: path.join(OUT, "desktop-hero.png") });

  /* donut on load (Today) and after each tab */
  const today = await auditDonut(page);
  assertDonut("donut Today", today);
  results.push(["donut Today", JSON.stringify({ painted: today.painted, segs: today.segPcts, names: today.names, sum: today.pctSum, total: today.total, avg: today.avg })]);

  for (const name of ["Week", "Month", "Year"]) {
    await page.evaluate((n) => {
      const btn = Array.from(document.querySelectorAll(".demo-tabs button")).find((b) => b.textContent.trim() === n);
      if (btn) btn.click();
    }, name);
    await sleep(1200); /* donut transition */
    const d = await auditDonut(page);
    assertDonut(`donut ${name}`, d);
    results.push([`donut ${name}`, JSON.stringify({ painted: d.painted, segs: d.segPcts, names: d.names, sum: d.pctSum, total: d.total, avg: d.avg })]);
  }
  /* back to Today for the screenshots */
  await page.evaluate(() => document.querySelector(".demo-tabs button").click());
  await sleep(1200);

  /* horizontal overflow */
  const ovDesktop = await page.evaluate(() => ({
    scrollW: document.documentElement.scrollWidth,
    innerW: window.innerWidth
  }));
  check("desktop: no horizontal overflow", ovDesktop.scrollW <= ovDesktop.innerW + 1, JSON.stringify(ovDesktop));
  results.push(["desktop overflow", JSON.stringify(ovDesktop)]);

  /* quick log in the hero card */
  await page.click("#demoInput");
  await page.type("#demoInput", "100");
  await page.click("#demoLog");
  await sleep(400);
  const afterLog = await auditDonut(page);
  assertDonut("donut after quick log", afterLog);
  results.push(["hero quick log", JSON.stringify({ rows: afterLog.legendRows, sum: afterLog.pctSum, total: afterLog.total, names: afterLog.names })]);
  const logRows = await page.$$eval("#demoRows .demo-row", (rs) => rs.length);
  check("hero quick log appends a row", logRows === 3, `rows=${logRows}`);

  /* pipeline mid — scroll into the middle of the pinned scroll-room */
  await page.evaluate(() => {
    const sec = document.querySelector("#architecture");
    const top = sec.getBoundingClientRect().top + window.scrollY;
    const room = sec.offsetHeight - window.innerHeight;
    window.scrollTo(0, Math.max(0, top + room * 0.5));
  });
  await sleep(700);
  const mid = await page.evaluate(() => {
    const packet = document.getElementById("plPacket");
    return {
      packetLeft: packet ? packet.style.left : null,
      activeNodes: document.querySelectorAll(".pl-node.active").length,
      activeTabs: document.querySelectorAll(".pl-tab.active").length
    };
  });
  results.push(["pipeline mid", JSON.stringify(mid)]);
  const midPct = parseFloat(mid.packetLeft);
  check("pipeline: packet scrubs to mid-rail + a stage is active",
    midPct > 30 && midPct < 70 && mid.activeTabs === 1,
    JSON.stringify(mid));
  await page.screenshot({ path: path.join(OUT, "desktop-architecture.png") });

  await page.click(".pl-tab:nth-child(3)");
  await sleep(900);
  const afterTab = await page.evaluate(() => ({
    packetLeft: document.getElementById("plPacket").style.left,
    activeTabs: document.querySelectorAll(".pl-tab.active").length
  }));
  results.push(["pipeline tab click", JSON.stringify(afterTab)]);
  check("pipeline: tab click lands a stage", afterTab.activeTabs === 1 && parseFloat(afterTab.packetLeft) > 50, JSON.stringify(afterTab));

  /* features */
  const featY = await page.evaluate(() => document.querySelector("#features").getBoundingClientRect().top + window.scrollY);
  await page.evaluate((y) => window.scrollTo(0, Math.max(0, y - 56)), featY);
  await sleep(400);
  await page.screenshot({ path: path.join(OUT, "desktop-features.png") });

  /* quick-log tile */
  await page.click("#qlogKeys button[data-v='100']");
  const qAmt = await page.$eval("#qlogAmt", (el) => el.textContent);
  await page.click("#qlogGo");
  await sleep(250);
  const qOut = await page.$eval("#qlogOut", (el) => el.textContent.trim().slice(0, 60));
  results.push(["quick log tile", `amt=${qAmt} out=${qOut}`]);
  check("quick log tile logs ₹100", qAmt === "100" && /100/.test(qOut), `amt=${qAmt} out=${qOut}`);

  /* tags filter */
  await page.click('#tagFilters button[data-filter="petrol"]');
  await sleep(300);
  const tagRows = await page.$$eval("#tagsList .tl-row", (rs) => rs.map((r) => (r.classList.contains("hidden") ? "hidden" : "shown")));
  results.push(["tags petrol", tagRows.join(",")]);
  check("tag filter hides non-matching rows", tagRows.filter((s) => s === "shown").length === 2, tagRows.join(","));
  await page.click('#tagFilters button[data-filter="all"]');

  /* APK honest state — the file now ships in the deployment folder, so it must be OK */
  await sleep(600);
  const dl = await page.evaluate(() => ({
    text: document.getElementById("dlState").textContent,
    cls: document.getElementById("dlState").className,
    fallbackHidden: document.getElementById("dlFallback").hidden,
    href: document.getElementById("dlBtn").getAttribute("href")
  }));
  results.push(["apk state", JSON.stringify(dl)]);
  check("APK reachable (HEAD 200) → ok chip", /(^|\s)ok(\s|$)/.test(dl.cls), JSON.stringify(dl));
  check("APK fallback stays hidden when reachable", dl.fallbackHidden === true, JSON.stringify(dl));
  check("APK href points at the deployed path", dl.href === "downloads/Pocket-Ledger-Android-V2.apk", dl.href);

  /* start + manual + footer */
  const startY = await page.evaluate(() => document.querySelector("#get-started").getBoundingClientRect().top + window.scrollY);
  await page.evaluate((y) => window.scrollTo(0, Math.max(0, y - 56)), startY);
  await sleep(400);
  await page.screenshot({ path: path.join(OUT, "desktop-start.png") });

  const bottomY = await page.evaluate(() => document.querySelector(".footer").getBoundingClientRect().top + window.scrollY);
  await page.evaluate((y) => window.scrollTo(0, Math.max(0, y - 200)), bottomY);
  await sleep(400);
  await page.screenshot({ path: path.join(OUT, "desktop-manual-footer.png") });

  /* live HTTP check of the real download targets through the served root */
  for (const rel of ["downloads/Pocket-Ledger-Android-V2.apk", "downloads/Pocket-Ledger-iPhone.shortcut",
    "guides/Pocket-Ledger-Complete-User-Guide.pdf", "apps-script/Pocket-Ledger-Apps-Script.gs.txt",
    "style.css", "main.js"]) {
    const status = await page.evaluate(async (u) => {
      const r = await fetch(u, { method: "HEAD", cache: "no-store" });
      return r.status;
    }, rel);
    check(`served 200 · ${rel}`, status === 200, `status=${status}`);
    results.push([`http ${rel}`, String(status)]);
  }

  /* theme toggle */
  await page.click("#themeToggle");
  await sleep(300);
  const theme = await page.evaluate(() => document.documentElement.getAttribute("data-theme"));
  results.push(["theme toggle", theme]);
  check("theme toggle switches to light", theme === "light", theme);
  await page.screenshot({ path: path.join(OUT, "desktop-light.png") });
  await page.click("#themeToggle");
  await sleep(200);
  check("theme toggle returns to dark",
    (await page.evaluate(() => document.documentElement.getAttribute("data-theme"))) === "dark");

  await page.close();

  /* ------------------------- MOBILE ------------------------- */
  page = await browser.newPage();
  const mobErrs = await collectErrors(page);
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2 });
  await page.goto(`http://127.0.0.1:${PORT}/`, { waitUntil: "networkidle0", timeout: 30000 });
  await sleep(3000);
  await page.evaluate(() => {
    document.documentElement.classList.add("go");
    const main = document.querySelector("main");
    if (main) main.classList.add("live");
    document.querySelectorAll(".rv").forEach((el) => el.classList.add("is-in"));
  });
  await page.screenshot({ path: path.join(OUT, "mobile-hero.png") });

  /* the hero card only renders its donut once it intersects — bring it into view */
  await page.evaluate(() => {
    const y = document.querySelector("#heroDemo").getBoundingClientRect().top + window.scrollY;
    window.scrollTo(0, Math.max(0, y - 80));
  });
  await sleep(900);
  const mobDonut = await auditDonut(page);
  assertDonut("mobile donut Today", mobDonut);
  results.push(["mobile donut", JSON.stringify({ painted: mobDonut.painted, segs: mobDonut.segPcts, sum: mobDonut.pctSum })]);

  const ovMobile = await page.evaluate(() => ({
    scrollW: document.documentElement.scrollWidth,
    innerW: window.innerWidth
  }));
  check("mobile: no horizontal overflow", ovMobile.scrollW <= ovMobile.innerW + 1, JSON.stringify(ovMobile));
  results.push(["mobile overflow", JSON.stringify(ovMobile)]);

  await page.evaluate(() => {
    const y = document.querySelector("#architecture").getBoundingClientRect().top + window.scrollY + window.innerHeight * 0.5;
    window.scrollTo(0, Math.max(0, y - window.innerHeight * 0.4));
  });
  await sleep(500);
  await page.screenshot({ path: path.join(OUT, "mobile-architecture.png") });
  const mobPin = await page.evaluate(() => getComputedStyle(document.querySelector(".pin-wrap")).position);
  results.push(["mobile pin-wrap", mobPin]);
  check("mobile: pipeline un-pins", mobPin === "static", mobPin);

  await page.click("#burger");
  await sleep(250);
  const mobMenu = await page.evaluate(() => ({
    open: document.getElementById("mobileMenu").classList.contains("open"),
    expanded: document.getElementById("burger").getAttribute("aria-expanded"),
    bodyLocked: getComputedStyle(document.body).overflow
  }));
  results.push(["mobile menu", JSON.stringify(mobMenu)]);
  check("mobile menu opens + locks scroll", mobMenu.open && mobMenu.expanded === "true" && mobMenu.bodyLocked === "hidden", JSON.stringify(mobMenu));
  await page.screenshot({ path: path.join(OUT, "mobile-menu.png") });
  await page.click("#burger");
  await sleep(200);
  check("mobile menu closes", (await page.evaluate(() => document.getElementById("mobileMenu").classList.contains("open"))) === false);

  await page.evaluate(() => {
    const y = document.querySelector("#features").getBoundingClientRect().top + window.scrollY;
    window.scrollTo(0, Math.max(0, y - 40));
  });
  await sleep(300);
  await page.screenshot({ path: path.join(OUT, "mobile-features.png") });

  const startY2 = await page.evaluate(() => document.querySelector("#get-started").getBoundingClientRect().top + window.scrollY);
  await page.evaluate((y) => window.scrollTo(0, Math.max(0, y - 40)), startY2);
  await sleep(300);
  await page.screenshot({ path: path.join(OUT, "mobile-start.png") });

  await page.close();
  await browser.close();
  server.close();

  /* ------------------------- REPORT ------------------------- */
  console.log("=== SMOKE CHECKLIST ===");
  results.forEach(([k, v]) => console.log(`  [${k}] ${v}`));
  const pageErrs = (errs.length ? errs : []).concat(mobErrs);
  const headArtifacts = (errs.headArtifacts || 0) + (mobErrs.headArtifacts || 0);
  console.log(`\nDESKTOP console errors: ${errs.length ? "" : "none"}${errs.map((e) => "\n  " + e).join("")}`);
  console.log(`MOBILE  console errors: ${mobErrs.length ? "" : "none"}${mobErrs.map((e) => "\n  " + e).join("")}`);
  console.log(`HEAD probe artifacts (local server quirk, see smoke.js): ${headArtifacts}`);
  console.log(`\nScreenshots → ${OUT}`);
  check("no real console/page errors", pageErrs.length === 0, pageErrs.join(" | "));

  if (fails.length) {
    console.log(`\n!!! ${fails.length} CHECK(S) FAILED:`);
    fails.forEach((f) => console.log("  - " + f));
    process.exitCode = 1;
  } else {
    console.log(`\nALL CHECKS PASSED (${results.length} probes, ${pageErrs.length} console/network notes)`);
  }
}

main().catch((e) => { console.error("SMOKE FAILED:", e); process.exit(1); });
