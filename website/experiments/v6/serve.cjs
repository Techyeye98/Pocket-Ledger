const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');

const root = __dirname;
const prefix = '/website/experiments/v6/';
const allowed = new Map([
  ['index.html', 'text/html; charset=utf-8'],
  ['style.css', 'text/css; charset=utf-8'],
  ['main.js', 'text/javascript; charset=utf-8'],
  ['pocket-ledger-logo.png', 'image/png'],
  ['Pocket-Ledger-Quick-Guide-2026-10.pdf', 'application/pdf']
]);
const port = 4176;

http.createServer(async (request, response) => {
  const pathname = new URL(request.url, `http://127.0.0.1:${port}`).pathname;
  if (pathname === '/') {
    response.writeHead(302, { Location: prefix });
    response.end();
    return;
  }
  if (!pathname.startsWith(prefix)) {
    response.writeHead(404);
    response.end('Not found');
    return;
  }
  const name = pathname.slice(prefix.length) || 'index.html';
  if (!allowed.has(name)) {
    response.writeHead(404);
    response.end('Not found');
    return;
  }
  try {
    const body = await fs.readFile(path.join(root, name));
    response.writeHead(200, {
      'Content-Type': allowed.get(name),
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff'
    });
    response.end(body);
  } catch {
    response.writeHead(404);
    response.end('Not found');
  }
}).listen(port, '0.0.0.0', () => {
  console.log(`Pocket Ledger V6 preview: http://127.0.0.1:${port}${prefix}`);
  console.log(`Same-Wi-Fi preview: http://192.168.1.5:${port}${prefix}`);
});
