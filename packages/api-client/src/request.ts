export interface RequestOptions { token?: string; idempotencyKey?: string; signal?: AbortSignal; }
export class ApiClient {
  constructor(private readonly baseUrl: string) {}
  async request<T>(path: string, init: RequestInit = {}, options: RequestOptions = {}): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body) headers.set('Content-Type', 'application/json');
    if (options.token) headers.set('Authorization', `Bearer ${options.token}`);
    if (options.idempotencyKey) headers.set('X-Idempotency-Key', options.idempotencyKey);
    const response = await fetch(new URL(path, this.baseUrl), { ...init, headers, signal: options.signal });
    const requestId = response.headers.get('X-Request-Id') ?? 'missing';
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(`${payload?.error?.code ?? response.status}: ${payload?.error?.message ?? 'request failed'} [${requestId}]`);
    return payload as T;
  }
}
