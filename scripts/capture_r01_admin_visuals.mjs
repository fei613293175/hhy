import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const outputRoot = join(repositoryRoot, 'artifacts', 'validation', 'r09-historical-ui', 'admin');
const baseUrl = process.env.HHY_R01_ADMIN_MOCK_URL ?? 'http://127.0.0.1:4174';
const executablePath = process.env.HHY_BROWSER_EXECUTABLE ?? 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

await mkdir(outputRoot, { recursive: true });
const browser = await chromium.launch({ executablePath, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
const failures = [];
page.on('console', (message) => { if (message.type() === 'error') failures.push(`console: ${message.text()}`); });
page.on('pageerror', (error) => failures.push(`pageerror: ${error.message}`));

try {
  await page.goto(`${baseUrl}/auth/login`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: '登录管理后台' }).waitFor();
  await page.screenshot({ path: join(outputRoot, 'ADM-AUTH-001-browser-1440x1100.png') });

  await page.getByLabel('管理员账号').fill('mfa-admin');
  await page.getByLabel('密码').fill('R01-admin-password');
  await page.getByRole('button', { name: '登录并继续' }).click();
  await page.waitForURL('**/auth/mfa');
  await page.getByRole('heading', { name: '完成二次验证' }).waitFor();
  await page.screenshot({ path: join(outputRoot, 'ADM-AUTH-002-browser-1440x1100.png') });

  await page.getByRole('button', { name: '返回登录' }).click();
  await page.waitForURL('**/auth/login');
  await page.getByLabel('管理员账号').fill('r01-admin');
  await page.getByLabel('密码').fill('R01-admin-password');
  await page.getByRole('button', { name: '登录并继续' }).click();
  await page.waitForURL('**/me/security');
  await page.getByRole('heading', { name: '管理员安全设置' }).waitFor();
  await page.getByText('安全评分').waitFor();
  await page.screenshot({ path: join(outputRoot, 'ADM-SECURITY-001-browser-1440x1100.png') });
} finally {
  await browser.close();
}

if (failures.length) throw new Error(`Browser errors:\n${failures.join('\n')}`);
console.log(`R01_ADMIN_VISUALS_CAPTURED ${outputRoot}`);
