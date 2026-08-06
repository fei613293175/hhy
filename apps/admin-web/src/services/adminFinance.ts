import type { AdminContract } from '@hhy/api-client'
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity'
import { adminSession, type AdminSession } from './adminSession'

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type Json<Value> = Value extends { content: { 'application/json': infer Body } } ? Body : never
type Success<Op> = Op extends { responses: { 200: infer Response } } ? Json<Response> : never
type Data<Op> = Success<Op> extends { data: infer Value } ? Value : never
type Body<Op> = Op extends { requestBody?: infer Request } ? Json<NonNullable<Request>> : never

export type FinanceQuery = { page?: number; pageSize?: number; cursor?: string; status?: string; keyword?: string; sort?: string }
export type RewardPage = Data<Operation<'adminRewardsGetRewardAccounts'>>
export type RewardResource = AdminContract.components['schemas']['RewardAccountResource']
export type WithdrawalPage = Data<Operation<'adminWithdrawalsGetWithdrawals'>>
export type WithdrawalResource = AdminContract.components['schemas']['WithdrawalResource']
export type AccountingPage = Data<Operation<'adminAccountingGetTransactions'>>
export type AccountingResource = AdminContract.components['schemas']['AccountingTransactionResource']
export type RewardAdjustmentBody = Body<Operation<'adminRewardsPostRewardAdjustments'>>
export type WithdrawalReviewBody = Body<Operation<'adminWithdrawalsPostWithdrawalsByIdRiskReview'>>
export type WithdrawalPayoutBody = Body<Operation<'adminWithdrawalsPostWithdrawalsByIdPayout'>>
export type WithdrawalQueryBody = Body<Operation<'adminWithdrawalsPostWithdrawalsByIdQuery'>>
export type AccountingReversalBody = Body<Operation<'adminAccountingPostReversal'>>

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/
function safeId(value: string) { if (!SAFE_ID.test(value)) throw new RangeError('资源标识格式无效'); return encodeURIComponent(value) }
function query(parameters: FinanceQuery = {}) {
  if (parameters.page !== undefined && parameters.page < 1) throw new RangeError('页码必须大于等于 1')
  if (parameters.pageSize !== undefined && (parameters.pageSize < 1 || parameters.pageSize > 100)) throw new RangeError('每页数量必须在 1 到 100 之间')
  if (parameters.cursor && parameters.page && parameters.page !== 1) throw new RangeError('页码和游标不能同时用于翻页')
  const value = new URLSearchParams(); Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) })
  return value.size ? `?${value}` : ''
}

export class AdminFinanceApi {
  constructor(private readonly transport: AdminSecurityApi = adminSecurityApi, private readonly session: AdminSession = adminSession) {}
  rewards(parameters: FinanceQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminRewardsGetRewardAccounts'>>('/admin-api/v1/reward-accounts', parameters, options) }
  rewardLedger(userId: string, parameters: FinanceQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminRewardsGetRewardAccountsByUseridLedger'>>(`/admin-api/v1/reward-accounts/${safeId(userId)}/ledger`, parameters, options) }
  withdrawals(parameters: FinanceQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminWithdrawalsGetWithdrawals'>>('/admin-api/v1/withdrawals', parameters, options) }
  withdrawal(idValue: string, options: AdminCallOptions = {}) { return this.read<Operation<'adminWithdrawalsGetWithdrawalsById'>>(`/admin-api/v1/withdrawals/${safeId(idValue)}`, options) }
  accounting(parameters: FinanceQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminAccountingGetTransactions'>>('/admin-api/v1/accounting/transactions', parameters, options) }
  accountingEntries(parameters: FinanceQuery & { transactionId?: string; direction?: string } = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminAccountingGetEntries'>>('/admin-api/v1/accounting/entries', parameters, options) }
  accountingTransaction(idValue: string, options: AdminCallOptions = {}) { return this.read<Operation<'adminAccountingGetTransactionById'>>(`/admin-api/v1/accounting/transactions/${safeId(idValue)}`, options) }
  rewardAdjustment(body: RewardAdjustmentBody, options: AdminCallOptions = {}) { return this.write<Operation<'adminRewardsPostRewardAdjustments'>>('/admin-api/v1/reward-adjustments', body, options) }
  riskReview(idValue: string, body: WithdrawalReviewBody, options: AdminCallOptions = {}) { return this.write<Operation<'adminWithdrawalsPostWithdrawalsByIdRiskReview'>>(`/admin-api/v1/withdrawals/${safeId(idValue)}/risk-review`, body, options) }
  financeReview(idValue: string, body: WithdrawalReviewBody, options: AdminCallOptions = {}) { return this.write<Operation<'adminWithdrawalsPostWithdrawalsByIdFinanceReview'>>(`/admin-api/v1/withdrawals/${safeId(idValue)}/finance-review`, body, options) }
  payout(idValue: string, body: WithdrawalPayoutBody, options: AdminCallOptions = {}) { return this.write<Operation<'adminWithdrawalsPostWithdrawalsByIdPayout'>>(`/admin-api/v1/withdrawals/${safeId(idValue)}/payout`, body, options) }
  queryPayout(idValue: string, body: WithdrawalQueryBody, options: AdminCallOptions = {}) { return this.write<Operation<'adminWithdrawalsPostWithdrawalsByIdQuery'>>(`/admin-api/v1/withdrawals/${safeId(idValue)}/query`, body, options) }
  reverseAccounting(idValue: string, body: AccountingReversalBody, options: AdminCallOptions = {}) { return this.write<Operation<'adminAccountingPostReversal'>>(`/admin-api/v1/accounting/transactions/${safeId(idValue)}/reversals`, body, options) }
  private async read<Op>(path: string, options: AdminCallOptions) { const response = await this.transport.request<{ data: Data<Op> }>(path, { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken }); return response.data }
  private async list<Op>(path: string, parameters: FinanceQuery & Record<string, unknown>, options: AdminCallOptions) { const response = await this.transport.request<{ data: Data<Op> }>(path + query(parameters), { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken }); return response.data }
  private async write<Op>(path: string, body: Body<Op>, options: AdminCallOptions) { const response = await this.transport.request<{ data: Data<Op> }>(path, { method: 'POST', body: JSON.stringify(body) }, { ...options, token: options.token ?? this.session.accessToken, idempotencyKey: options.idempotencyKey ?? globalThis.crypto.randomUUID() }); return response.data }
}
export const adminFinanceApi = new AdminFinanceApi()
