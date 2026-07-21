import { createReadStream, existsSync } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, isAbsolute, join, normalize, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const distributionRoot = join(repositoryRoot, 'apps', 'admin-web', 'dist');
const port = Number(process.env.HHY_R06_ADMIN_MOCK_PORT ?? '4176');
const host = '127.0.0.1';

if (!existsSync(join(distributionRoot, 'index.html'))) {
  throw new Error('admin-web dist is missing; run the admin-web build first');
}

const contentItems = [
  { id: 'content-project-001', title: '星桥协作平台', contentType: 'PROJECT', status: 'ONLINE', reviewStatus: 'APPROVED', version: 12, categoryCode: 'DIGITAL_SERVICE', regionCode: 'CN-31', summary: '面向成长团队的项目协作与资源对接服务。', createdAt: '2026-07-18T08:30:00Z', updatedAt: '2026-07-21T05:10:00Z' },
  { id: 'content-app-002', title: '云端进销存', contentType: 'APP', status: 'PENDING_REVIEW', reviewStatus: 'PENDING', version: 7, categoryCode: 'ENTERPRISE_APP', regionCode: 'CN-44', summary: '覆盖采购、库存和销售协同的企业应用。', createdAt: '2026-07-19T03:20:00Z', updatedAt: '2026-07-21T04:48:00Z' },
  { id: 'content-group-003', title: '跨境运营交流群', contentType: 'GROUP_CHAT', status: 'OFFLINE', reviewStatus: 'APPROVED', version: 4, categoryCode: 'BUSINESS_COMMUNITY', regionCode: 'CN-33', summary: '跨境业务从业者的经验交流与资源协作社群。', createdAt: '2026-07-17T10:00:00Z', updatedAt: '2026-07-20T15:42:00Z' },
  { id: 'content-leader-004', title: '华东平台招商负责人', contentType: 'TEAM_LEADER', status: 'ONLINE', reviewStatus: 'APPROVED', version: 9, categoryCode: 'TEAM_SERVICE', regionCode: 'CN-31', summary: '提供平台招商、项目评估和区域合作支持。', createdAt: '2026-07-16T09:15:00Z', updatedAt: '2026-07-20T12:26:00Z' },
];

const dictionaries = [
  { id: 'CONTENT_CATEGORY', title: '内容分类', status: 'ACTIVE', version: 6, updatedAt: '2026-07-21T03:20:00Z' },
  { id: 'REGION', title: '地区', status: 'ACTIVE', version: 18, updatedAt: '2026-07-20T10:05:00Z' },
  { id: 'PLATFORM', title: '适用平台', status: 'ACTIVE', version: 5, updatedAt: '2026-07-19T08:40:00Z' },
  { id: 'TEAM_SIZE', title: '团队规模', status: 'ACTIVE', version: 4, updatedAt: '2026-07-18T06:30:00Z' },
];

function envelope(data, requestId) {
  return { success: true, requestId, timestamp: '2026-07-21T06:00:00Z', data };
}

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json; charset=UTF-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(body));
}

async function bodyAsJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {};
}

async function handleApi(request, response, pathname, searchParams) {
  const requestId = `mock-r06-${Date.now()}`;
  if (request.method !== 'GET' && !request.headers['x-idempotency-key']) {
    sendJson(response, 400, { success: false, requestId, error: { code: 'COMMON-400-IDEMPOTENCY-REQUIRED', message: '缺少幂等键' } });
    return;
  }

  if (pathname === '/admin-api/v1/auth/login' && request.method === 'POST') {
    await bodyAsJson(request);
    sendJson(response, 200, envelope({
      accessToken: 'mock-r06-access-token', adminUserId: '26', displayName: '内容运营管理员',
      permissionCodes: ['content.read', 'content.manage', 'content.ban', 'content.recommend', 'content.official', 'content.dict.write'],
      mfaRequired: 'NONE',
    }, requestId));
    return;
  }

  if (pathname === '/admin-api/v1/contents' && request.method === 'GET') {
    const keyword = (searchParams.get('keyword') ?? '').toLowerCase();
    const items = keyword ? contentItems.filter((item) => `${item.title} ${item.id}`.toLowerCase().includes(keyword)) : contentItems;
    sendJson(response, 200, envelope({ items, page: 1, pageSize: 20, total: items.length, hasNext: false }, requestId));
    return;
  }

  if (pathname === '/admin-api/v1/content-dictionaries' && request.method === 'GET') {
    const keyword = (searchParams.get('keyword') ?? '').toLowerCase();
    const items = keyword ? dictionaries.filter((item) => `${item.title} ${item.id}`.toLowerCase().includes(keyword)) : dictionaries;
    sendJson(response, 200, envelope({ items, page: 1, pageSize: 100, total: items.length, hasNext: false }, requestId));
    return;
  }

  const contentMatch = pathname.match(/^\/admin-api\/v1\/contents\/([A-Za-z0-9_-]+)$/);
  if (contentMatch && request.method === 'GET') {
    const item = contentItems.find((candidate) => candidate.id === contentMatch[1]);
    if (item) sendJson(response, 200, envelope(item, requestId));
    else sendJson(response, 404, { success: false, requestId, error: { code: 'CONTENT-404-NOT_FOUND', message: '内容不存在' } });
    return;
  }

  sendJson(response, 404, { success: false, requestId, error: { code: 'COMMON-404-NOT_FOUND', message: 'Not Found' } });
}

const contentTypes = {
  '.css': 'text/css; charset=UTF-8', '.html': 'text/html; charset=UTF-8',
  '.js': 'text/javascript; charset=UTF-8', '.map': 'application/json; charset=UTF-8',
};

async function handleStatic(response, pathname) {
  const relativePath = pathname === '/' ? 'index.html' : pathname.slice(1);
  const candidate = normalize(join(distributionRoot, relativePath));
  const fromDistribution = relative(distributionRoot, candidate);
  const withinDistribution = !fromDistribution.startsWith('..') && !isAbsolute(fromDistribution);
  const target = withinDistribution && existsSync(candidate) && (await stat(candidate)).isFile()
    ? candidate : join(distributionRoot, 'index.html');
  response.writeHead(200, { 'Content-Type': contentTypes[extname(target)] ?? 'application/octet-stream' });
  createReadStream(target).pipe(response);
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url ?? '/', `http://${host}:${port}`);
    if (url.pathname.startsWith('/admin-api/')) await handleApi(request, response, url.pathname, url.searchParams);
    else await handleStatic(response, url.pathname);
  } catch {
    sendJson(response, 500, { success: false, requestId: 'mock-r06-error', error: { code: 'MOCK-500', message: 'Mock server error' } });
  }
});

server.listen(port, host, () => console.log(`R06 admin-web mock listening on http://${host}:${port}`));

