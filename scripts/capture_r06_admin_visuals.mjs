import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const outputRoot = join(repositoryRoot, 'artifacts', 'validation', 'r06-ui', 'admin');
const baseUrl = process.env.HHY_R06_ADMIN_MOCK_URL ?? 'http://127.0.0.1:4176';
const executablePath = process.env.HHY_BROWSER_EXECUTABLE ?? 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

await mkdir(outputRoot, { recursive: true });
const browser = await chromium.launch({ executablePath, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
const failures = [];
page.on('console', (message) => { if (message.type() === 'error') failures.push(`console: ${message.text()}`); });
page.on('pageerror', (error) => failures.push(`pageerror: ${error.message}`));

try {
  await page.goto(`${baseUrl}/auth/login?redirect=/contents`, { waitUntil: 'networkidle' });
  await page.getByLabel('管理员账号').fill('r06-content-admin');
  await page.getByLabel('密码').fill('R06-content-admin-password');
  await page.getByRole('button', { name: '登录并继续' }).click();
  await page.waitForURL('**/contents');
  await page.getByText('星桥协作平台').waitFor();
  await page.screenshot({ path: join(outputRoot, 'ADM-CONTENT-001-browser-1440x1100.png') });

  await page.getByRole('link', { name: '查看详情' }).first().click();
  await page.waitForURL('**/contents/content-project-001');
  await page.getByRole('heading', { name: '星桥协作平台' }).first().waitFor();
  await page.screenshot({ path: join(outputRoot, 'ADM-CONTENT-002-browser-1440x1100.png') });

  await page.getByRole('link', { name: '字典与属性' }).click();
  await page.waitForURL('**/contents/dictionaries');
  await page.getByText('内容分类').waitFor();
  await page.screenshot({ path: join(outputRoot, 'ADM-CONTENT-003-browser-1440x1100.png') });
} finally {
  await browser.close();
}

if (failures.length) throw new Error(`Browser errors:\n${failures.join('\n')}`);
console.log(`R06_ADMIN_VISUALS_CAPTURED ${outputRoot}`);
