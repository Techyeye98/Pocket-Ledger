/* Render the actual Pages package, or pass a deployed URL to verify it live. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const root = path.resolve(__dirname, '../../distribution');
const out = path.join(__dirname, 'out/v6-publication');
const serveOnly = process.argv[2] === '--serve';
const live = serveOnly ? null : process.argv[2];
const base = live || 'http://127.0.0.1:4829/Pocket-Ledger/';
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.png': 'image/png', '.webp':'image/webp', '.svg':'image/svg+xml', '.woff2':'font/woff2', '.xml':'application/xml', '.txt':'text/plain; charset=utf-8', '.pdf': 'application/pdf', '.apk': 'application/vnd.android.package-archive' };
const server = http.createServer((req, res) => {
  const requestUrl = new URL(req.url, base);
  const relative = decodeURIComponent(requestUrl.pathname).replace(/^\/Pocket-Ledger\//, '') || 'index.html';
  // Local QA diagnostics are opt-in and never part of the published package.
  if (serveOnly && ['__qa/axe.min.js','__qa/diagnostics.js'].includes(relative)) {
    fs.readFile(path.join(out, '../awwwards', path.basename(relative)), (error, data) => {
      res.writeHead(error ? 404 : 200, { 'Content-Type':'text/javascript; charset=utf-8' }).end(error ? '' : data);
    });
    return;
  }
  const file = path.resolve(root, relative);
  if (!file.startsWith(root + path.sep)) { res.writeHead(403).end(); return; }
  fs.readFile(file, (error, data) => {
    if (error) {
      fs.readFile(path.join(root, '404.html'), (missing, page) => {
        res.writeHead(404, { 'Content-Type':'text/html; charset=utf-8' }).end(missing ? 'Page not found' : page);
      });
      return;
    }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
    if (serveOnly && path.extname(file) === '.html' && requestUrl.searchParams.has('__qa')) {
      data = Buffer.from(data.toString().replace('</body>', '<script src="__qa/axe.min.js" defer></script><script src="__qa/diagnostics.js" defer></script></body>'));
    }
    res.end(data);
  });
});
if (!serveOnly) {
  console.log('Use --serve for the local preview. Run content-check.cjs for static verification; browser QA uses the connected in-app browser.');
} else {
  server.listen(4829,'127.0.0.1',()=>console.log(base));
}
