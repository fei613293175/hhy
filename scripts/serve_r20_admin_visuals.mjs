import { createReadStream, readFileSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { extname, join, normalize } from 'node:path'

const port = Number(process.argv[2] ?? 5181)
const root = normalize(join(import.meta.dirname, '..', 'apps', 'admin-web', 'dist'))
const session = {
  accessToken: 'visual-r20-token',
  displayName: '红包运营审核员',
  permissionCodes: ['redpacket.read', 'redpacket.review'],
  expiresAt: '2027-08-05T00:00:00Z',
}
const campaigns = [
  { id: 'rp-active-2001', contentId: 'content-growth-2001', ownerUserId: 'advertiser-2001', status: 'ACTIVE', totalCount: 200, remainingCount: 124, amountPerClaimCent: 150, principalCent: 30000, serviceFeeCent: 1500, startAt: '2026-08-05T08:00:00Z', endAt: '2026-08-12T08:00:00Z', version: 7 },
  { id: 'rp-review-2002', contentId: 'content-partner-2002', ownerUserId: 'advertiser-2002', status: 'PRE_REVIEWING', totalCount: 100, remainingCount: 100, amountPerClaimCent: 200, principalCent: 20000, serviceFeeCent: 1000, startAt: '2026-08-10T08:00:00Z', endAt: '2026-08-17T08:00:00Z', version: 3 },
  { id: 'rp-approved-2003', contentId: 'content-brand-2003', ownerUserId: 'advertiser-2003', status: 'PRE_REVIEW_APPROVED', totalCount: 300, remainingCount: 300, amountPerClaimCent: 100, principalCent: 30000, serviceFeeCent: 1500, startAt: '2026-08-12T08:00:00Z', endAt: '2026-08-19T08:00:00Z', version: 5 },
]
const types = { '.css': 'text/css', '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' }

function json(response, data, status = 200) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify(data))
}

createServer((request, response) => {
  const url = new URL(request.url ?? '/', `http://127.0.0.1:${port}`)
  if (request.method === 'GET' && url.pathname === '/admin-api/v1/red-packet-campaigns') {
    const status = url.searchParams.get('status')
    const keyword = url.searchParams.get('keyword')?.toLowerCase()
    const items = campaigns.filter((item) =>
      (!status || item.status === status) &&
      (!keyword || item.id.toLowerCase().includes(keyword) || item.contentId.toLowerCase().includes(keyword)),
    )
    return json(response, { success: true, requestId: 'visual-r20-list', data: { items, page: { page: 1, pageSize: 20, total: String(items.length), hasMore: 'false' } } })
  }
  const detail = url.pathname.match(/^\/admin-api\/v1\/red-packet-campaigns\/([A-Za-z0-9_-]+)$/)
  if (request.method === 'GET' && detail) {
    const item = campaigns.find((campaign) => campaign.id === detail[1])
    return item
      ? json(response, { success: true, requestId: 'visual-r20-detail', data: item })
      : json(response, { success: false, requestId: 'visual-r20-missing', error: { code: 'COMMON-404-NOT_FOUND', message: '红包活动不存在' } }, 404)
  }
  const relative = url.pathname.replace(/^\/+/, '')
  const file = normalize(join(root, relative))
  if (file.startsWith(root)) {
    try {
      if (statSync(file).isFile()) {
        response.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' })
        return createReadStream(file).pipe(response)
      }
    } catch {}
  }
  const bootstrap = `<script>sessionStorage.setItem('hhy.admin.session.v1',${JSON.stringify(JSON.stringify(session))})</script>`
  const index = readFileSync(join(root, 'index.html'), 'utf8').replace('<head>', `<head>${bootstrap}`)
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(index)
}).listen(port, '127.0.0.1', () => console.log(`R20 admin visual server: http://127.0.0.1:${port}`))
