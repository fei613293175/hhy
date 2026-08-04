import { createReadStream, readFileSync, statSync } from 'node:fs'
import { createServer } from 'node:http'
import { extname, join, normalize } from 'node:path'

const port = Number(process.argv[2] ?? 5180)
const root = normalize(join(import.meta.dirname, '..', 'apps', 'admin-web', 'dist'))
const session = {
  accessToken: 'visual-r19-token', displayName: '道具运营',
  permissionCodes: ['prop.read', 'prop.write', 'prop.slot.read', 'prop.slot.write'],
  expiresAt: '2027-08-04T00:00:00Z',
}
const fixtures = {
  '/admin-api/v1/props': [
    { id: '101', propType: 'HEADLINE', name: '头条加速', quantity: 0, status: 'ACTIVE', configuration: { priceCent: 990, durationSeconds: '86400', scope: { contentType: true } }, version: 3 },
    { id: '102', propType: 'REFRESH', name: '内容焕新', quantity: 0, status: 'ACTIVE', configuration: { priceCent: 390, durationSeconds: '0', scope: { ownerOnly: true } }, version: 2 },
    { id: '103', propType: 'COLOR_THEME', name: '品牌主题色', quantity: 0, status: 'INACTIVE', configuration: { priceCent: 690, durationSeconds: '604800', scope: { contentType: true } }, version: 5 },
  ],
  '/admin-api/v1/headline-slots': [
    { id: '201', propType: 'HEADLINE_SLOT', name: 'home/top', quantity: 3, status: 'ACTIVE', configuration: { pageCode: '首页', slotCode: '顶部推荐', capacity: 3 }, version: 4 },
    { id: '202', propType: 'HEADLINE_SLOT', name: 'discover/featured', quantity: 6, status: 'ACTIVE', configuration: { pageCode: '发现页', slotCode: '精选推荐', capacity: 6 }, version: 2 },
    { id: '203', propType: 'HEADLINE_SLOT', name: 'projects/top', quantity: 2, status: 'INACTIVE', configuration: { pageCode: '项目页', slotCode: '优先展示', capacity: 2 }, version: 7 },
  ],
  '/admin-api/v1/prop-executions': [
    { id: '301', propType: 'EXECUTION', name: 'HEADLINE · 品牌联合增长计划', quantity: 1, status: 'SUCCEEDED', expiresAt: '2026-08-04T09:30:00Z', configuration: { result: { contentId: '19001' } }, version: 1 },
    { id: '302', propType: 'EXECUTION', name: 'REFRESH · 企业效率工具合作', quantity: 1, status: 'PENDING', expiresAt: '2026-08-04T09:10:00Z', configuration: { result: {} }, version: 1 },
    { id: '303', propType: 'EXECUTION', name: 'COLOR_THEME · 区域渠道招募', quantity: 1, status: 'FAILED', expiresAt: '2026-08-04T08:45:00Z', configuration: { error: '目标内容当前不可用' }, version: 2 },
  ],
}
const types = { '.css': 'text/css', '.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' }
function json(response, data, status = 200) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify(data))
}
createServer((request, response) => {
  const url = new URL(request.url ?? '/', `http://127.0.0.1:${port}`)
  const items = fixtures[url.pathname]
  if (request.method === 'GET' && items) {
    return json(response, { success: true, requestId: 'visual-r19-request', data: { items, page: { page: 1, pageSize: 20, total: String(items.length), hasMore: 'false' } } })
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
}).listen(port, '127.0.0.1', () => console.log(`R19 admin visual server: http://127.0.0.1:${port}`))
