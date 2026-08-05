import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const { chromium } = require('playwright')
const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)))
const outputRoot = join(repositoryRoot, 'artifacts', 'reports', 'R20', 'visual', 'admin')
const baseUrl = process.env.HHY_R20_ADMIN_VISUAL_URL ?? 'http://127.0.0.1:5181'
const executablePath = process.env.HHY_BROWSER_EXECUTABLE ?? 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'

await mkdir(outputRoot, { recursive: true })
const browser = await chromium.launch({ executablePath, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
const failures = []
page.on('console', (message) => { if (message.type() === 'error') failures.push(`console: ${message.text()}`) })
page.on('pageerror', (error) => failures.push(`pageerror: ${error.message}`))

try {
  await page.goto(`${baseUrl}/red-packets`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '红包活动' }).waitFor()
  await page.getByText('rp-active-2001').first().waitFor()
  await page.screenshot({ path: join(outputRoot, 'ADM-RP-001.png') })

  await page.goto(`${baseUrl}/red-packets/review?campaignId=rp-review-2002`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '红包预审核' }).waitFor()
  await page.locator('.review-action-form').waitFor()
  await page.locator('.review-action-form textarea').fill('内容状态、活动金额与投放时间均符合预审核规则')
  const evidenceInput = page.locator('.review-action-form input')
  await evidenceInput.fill('evidence-content-2002,evidence-budget-2002')
  await evidenceInput.evaluate((element) => { element.blur(); element.scrollLeft = 0 })
  await page.screenshot({ path: join(outputRoot, 'ADM-RP-003.png') })
} finally {
  await browser.close()
}

if (failures.length) throw new Error(`Browser errors:\n${failures.join('\n')}`)
console.log(`R20_ADMIN_VISUALS_CAPTURED ${outputRoot}`)
