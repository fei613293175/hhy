import type { AdminContract } from '@hhy/api-client'
import { adminSecurityApi, type AdminCallOptions } from './adminSecurity'

type Op<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type Json<OpType> = OpType extends { responses: { 200: { content: { 'application/json': infer Body } } } } ? Body : never
type Data<Id extends keyof AdminContract.operations> = Json<Op<Id>> extends { data: infer Value } ? Value : never
export type PaymentResource = AdminContract.components['schemas']['PaymentResource']
export type PaymentPage = Data<'adminPaymentsGetPayments'>
type Params = { page?: number; pageSize?: number; status?: string; keyword?: string; sort?: string }
const key = () => globalThis.crypto.randomUUID()
const query = (params: Params) => { const value = new URLSearchParams(); Object.entries({ page: 1, pageSize: 20, ...params }).forEach(([k, v]) => v !== undefined && v !== '' && value.set(k, String(v))); return `?${value}` }
export const adminPaymentsApi = {
  async payments(params: Params = {}, options: AdminCallOptions = {}) { return (await adminSecurityApi.request<Json<Op<'adminPaymentsGetPayments'>>>(`/admin-api/v1/payments${query(params)}`, { method: 'GET' }, options)).data },
  async callbacks(params: Params = {}, options: AdminCallOptions = {}) { return (await adminSecurityApi.request<Json<Op<'adminPaymentsGetPaymentCallbacks'>>>(`/admin-api/v1/payment-callbacks${query(params)}`, { method: 'GET' }, options)).data },
  async exceptions(params: Params = {}, options: AdminCallOptions = {}) { return (await adminSecurityApi.request<Json<Op<'adminPaymentsGetPaymentExceptions'>>>(`/admin-api/v1/payment-exceptions${query(params)}`, { method: 'GET' }, options)).data },
  queryPayment(id: string, body: { reason?: string; expectedVersion?: number; payload?: Record<string, unknown> }, options: AdminCallOptions = {}) { return adminSecurityApi.request<Json<Op<'adminPaymentsPostPaymentsByIdQuery'>>>(`/admin-api/v1/payments/${encodeURIComponent(id)}/query`, { method: 'POST', body: JSON.stringify(body) }, { ...options, idempotencyKey: options.idempotencyKey || key() }) },
  resolve(id: string, body: { resolution: string; reason: string; expectedVersion: number }, options: AdminCallOptions = {}) { return adminSecurityApi.request<Json<Op<'adminPaymentsPostPaymentExceptionsByIdResolve'>>>(`/admin-api/v1/payment-exceptions/${encodeURIComponent(id)}/resolve`, { method: 'POST', body: JSON.stringify(body) }, { ...options, idempotencyKey: options.idempotencyKey || key() }) },
  reconcile(body: { parameters?: string; dryRun?: boolean }, options: AdminCallOptions = {}) { return adminSecurityApi.request<Json<Op<'adminPaymentsPostPaymentReconciliationRun'>>>('/admin-api/v1/payment-reconciliation/run', { method: 'POST', body: JSON.stringify(body) }, { ...options, idempotencyKey: options.idempotencyKey || key() }) },
}
