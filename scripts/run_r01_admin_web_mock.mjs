import { createReadStream, existsSync } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { extname, isAbsolute, join, normalize, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const distributionRoot = join(repositoryRoot, 'apps', 'admin-web', 'dist');
const port = Number(process.env.HHY_R01_ADMIN_MOCK_PORT ?? '4174');
const host = '127.0.0.1';

if (!existsSync(join(distributionRoot, 'index.html'))) {
  throw new Error('admin-web dist is missing; run the admin-web build first');
}

const security = {
  adminId: '17',
  username: 'r01-admin',
  mfaEnabled: false,
  mfaMethods: [],
  activeSessionCount: 2,
  recoveryCodesRemaining: 0,
  lastPasswordChangedAt: '2026-07-17T09:20:00Z',
  lastLoginAt: '2026-07-17T11:15:00Z',
  lastLoginIpMasked: '203.0.113.***',
};

function envelope(data, requestId) {
  return {
    success: true,
    requestId,
    timestamp: '2026-07-17T11:30:00Z',
    data,
  };
}

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json; charset=UTF-8' });
  response.end(JSON.stringify(body));
}

async function bodyAsJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  if (chunks.length === 0) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function handleApi(request, response, pathname) {
  const requestId = `mock-r01-${Date.now()}`;
  if (request.method !== 'GET' && !request.headers['x-idempotency-key']) {
    sendJson(response, 400, { success: false, requestId, error: { code: 'COMMON-400-IDEMPOTENCY-REQUIRED', message: '缺少幂等键' } });
    return;
  }

  if (pathname === '/admin-api/v1/auth/login' && request.method === 'POST') {
    const body = await bodyAsJson(request);
    const mfaRequired = body.username === 'mfa-admin';
    sendJson(response, 200, envelope({
      ...(mfaRequired ? {} : { accessToken: 'mock-access-token' }),
      adminUserId: '17',
      displayName: 'R01 安全管理员',
      permissionCodes: ['admin.self.read', 'admin.self.security'],
      mfaRequired: mfaRequired ? 'TOTP' : 'NONE',
      ...(mfaRequired ? { mfaTicket: 'mock-mfa-ticket' } : {}),
    }, requestId));
    return;
  }

  if (pathname === '/admin-api/v1/auth/mfa/verify' && request.method === 'POST') {
    await bodyAsJson(request);
    sendJson(response, 200, envelope({
      accessToken: 'mock-mfa-access-token',
      adminUserId: '17',
      displayName: 'R01 MFA 管理员',
      permissionCodes: ['admin.self.read', 'admin.self.security'],
      mfaRequired: 'NONE',
    }, requestId));
    return;
  }

  if (pathname === '/admin-api/v1/me/security' && request.method === 'GET') {
    sendJson(response, 200, envelope(security, requestId));
    return;
  }

  if (pathname.endsWith('/password/change') && request.method === 'POST') {
    await bodyAsJson(request);
    sendJson(response, 200, envelope(security, requestId));
    return;
  }

  if (pathname.endsWith('/mfa/enroll') && request.method === 'POST') {
    sendJson(response, 200, envelope({
      enrollmentId: 'mock-enrollment-17',
      method: 'TOTP',
      secretQrCodeUrl: 'otpauth://totp/HHY:r01-admin?secret=MOCKR01ONLY',
      manualKeyMasked: 'MOCK****ONLY',
      expiresAt: '2026-07-17T11:40:00Z',
    }, requestId));
    return;
  }

  if (pathname.endsWith('/mfa/confirm') && request.method === 'POST') {
    await bodyAsJson(request);
    security.mfaEnabled = true;
    security.mfaMethods = ['TOTP'];
    security.recoveryCodesRemaining = 8;
    sendJson(response, 200, envelope(security, requestId));
    return;
  }

  if (pathname.endsWith('/mfa/disable') && request.method === 'POST') {
    await bodyAsJson(request);
    security.mfaEnabled = false;
    security.mfaMethods = [];
    security.recoveryCodesRemaining = 0;
    sendJson(response, 200, envelope(security, requestId));
    return;
  }

  if (pathname === '/admin-api/v1/auth/logout' && request.method === 'POST') {
    await bodyAsJson(request);
    sendJson(response, 200, envelope({ resourceId: 'mock-session-17', status: 'REVOKED', acceptedAt: '2026-07-17T11:30:00Z' }, requestId));
    return;
  }

  sendJson(response, 404, { success: false, requestId, error: { code: 'COMMON-404-NOT_FOUND', message: 'Not Found' } });
}

const contentTypes = {
  '.css': 'text/css; charset=UTF-8',
  '.html': 'text/html; charset=UTF-8',
  '.js': 'text/javascript; charset=UTF-8',
  '.map': 'application/json; charset=UTF-8',
};

async function handleStatic(response, pathname) {
  const relativePath = pathname === '/' ? 'index.html' : pathname.slice(1);
  const candidate = normalize(join(distributionRoot, relativePath));
  const fromDistribution = relative(distributionRoot, candidate);
  const withinDistribution = !fromDistribution.startsWith('..') && !isAbsolute(fromDistribution);
  const target = withinDistribution && existsSync(candidate) && (await stat(candidate)).isFile()
    ? candidate
    : join(distributionRoot, 'index.html');
  response.writeHead(200, { 'Content-Type': contentTypes[extname(target)] ?? 'application/octet-stream' });
  createReadStream(target).pipe(response);
}

const server = createServer(async (request, response) => {
  try {
    const pathname = new URL(request.url ?? '/', `http://${host}:${port}`).pathname;
    if (pathname.startsWith('/admin-api/')) await handleApi(request, response, pathname);
    else await handleStatic(response, pathname);
  } catch {
    sendJson(response, 500, { success: false, requestId: 'mock-r01-error', error: { code: 'MOCK-500', message: 'Mock server error' } });
  }
});

server.listen(port, host, () => {
  console.log(`R01 admin-web mock listening on http://${host}:${port}`);
});
