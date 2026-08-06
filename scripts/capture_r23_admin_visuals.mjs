import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const { chromium } = require('playwright-core')
const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)))
const outputRoot = join(repositoryRoot, 'artifacts', 'reports', 'R23', 'ui')
const baseUrl = process.env.HHY_R23_ADMIN_VISUAL_URL ?? 'http://127.0.0.1:5183'
const executablePath = process.env.HHY_BROWSER_EXECUTABLE
if (!executablePath) throw new Error('HHY_BROWSER_EXECUTABLE is required for R23 admin visual acceptance')

await mkdir(outputRoot, { recursive: true })
const browser = await chromium.launch({ executablePath, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
const failures = []
page.on('console', (message) => {
  if (message.type() === 'error' && !message.text().includes('Failed to load resource: the server responded with a status of 404')) {
    failures.push(`console: ${message.text()}`)
  }
})
page.on('pageerror', (error) => failures.push(`pageerror: ${error.message}`))

try {
  await page.goto(`${baseUrl}/auth/login?redirect=/red-packets/r23-active-2001`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '登录管理后台' }).waitFor()
  await page.getByLabel('管理员账号').fill('visual-admin')
  await page.getByLabel('密码').fill('visual-fixture-only')
  await page.getByRole('button', { name: '登录并继续' }).click()
  await page.getByRole('heading', { name: '红包活动 r23-active-2001' }).waitFor()
  await page.screenshot({ path: join(outputRoot, 'ADM-RP-002.png') })

  await page.goto(`${baseUrl}/red-packets/risk`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '红包风险事件' }).waitFor()
  await page.getByText('r23-risk-2002').waitFor()
  await page.screenshot({ path: join(outputRoot, 'ADM-RP-004.png') })
} finally {
  await browser.close()
}
if (failures.length) throw new Error(`Browser errors:\n${failures.join('\n')}`)
console.log(`R23_ADMIN_VISUALS_CAPTURED ${outputRoot}`)
