/**
 * pdfshot.cjs — 用 pdf.js 把最终 PDF 每一页光栅化成 PNG（真实 PDF 页面，不是 HTML 截图）
 * 用于最终视觉校验：分页留白、元素重叠、图表是否画出、CJK 字形是否正确
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const root = __dirname;
const pdfName = '声之建筑-落地方案.pdf';
const outDir = path.join(root, 'pdfpages');
fs.mkdirSync(outDir, { recursive: true });

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.pdf': 'application/pdf',
  '.json': 'application/json'
};

const server = http.createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  const file = path.join(root, p === '/' ? '/pdfview.html' : p);
  if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); res.end('nf'); return;
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const base = `http://127.0.0.1:${port}`;

  const { chromium } = require(require('child_process').execSync('npm root -g').toString().trim() + '/playwright');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1000, height: 1400 } });
  const errs = [];
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  page.on('pageerror', e => errs.push(String(e)));

  await page.goto(base + '/pdfview.html', { waitUntil: 'networkidle', timeout: 60000 });
  const count = await page.waitForFunction(() => window.__ready === true, { timeout: 120000 }).then(() => page.evaluate(() => window.__pageCount));

  for (let i = 1; i <= count; i++) {
    const ok = await page.evaluate(async (n) => {
      const cv = document.getElementById('cv');
      const ctx = cv.getContext('2d');
      ctx.clearRect(0, 0, cv.width, cv.height);
      const pg = await window.__pdf.getPage(n);
      const vp = pg.getViewport({ scale: window.__scale });
      cv.width = Math.ceil(vp.width);
      cv.height = Math.ceil(vp.height);
      await pg.render({ canvasContext: ctx, viewport: vp }).promise;
      return true;
    }, i);
    if (!ok) { continue; }
    const el = await page.$('#cv');
    await el.screenshot({ path: path.join(outDir, `p${String(i).padStart(2, '0')}.png`) });
  }

  console.log(JSON.stringify({ pages: count, out: outDir, errors: errs.slice(0, 5) }));
  await browser.close();
  server.close();
})();
