// One function per API operation, named after it. API IDs follow architecture/SPEC.md.
import { api } from './client.js'

const id = encodeURIComponent

// ---- auth.js (API-002, API-003) and departments (API-036)
export const authApi = {
  /** API-002 */ login: (email, password) => api.post('/auth/login', { email, password }, { auth: false }),
  /** API-003 */ me: (opts) => api.get('/me', opts),
}
export const departmentsApi = {
  /** API-036 */ list: (opts) => api.get('/departments', opts),
}

// ---- claims (API-004..013)
export const claimsApi = {
  /** API-004 */ listOwn: (query, opts) => api.get('/claims', { ...opts, query }),
  /** API-005 */ create: () => api.post('/claims'),
  /** API-006 */ get: (claimId, opts) => api.get(`/claims/${id(claimId)}`, opts),
  /** API-007 */ addLine: (claimId, line) => api.post(`/claims/${id(claimId)}/line-items`, line),
  /** API-008 */ updateLine: (claimId, lineItemId, line) =>
    api.put(`/claims/${id(claimId)}/line-items/${id(lineItemId)}`, line),
  /** API-009 */ deleteLine: (claimId, lineItemId) =>
    api.delete(`/claims/${id(claimId)}/line-items/${id(lineItemId)}`),
  /** API-010 */ uploadReceipt: (claimId, lineItemId, file) => {
    const form = new FormData()
    form.append('file', file)
    return api.post(`/claims/${id(claimId)}/line-items/${id(lineItemId)}/receipt`, form)
  },
  /** API-011 */ downloadReceipt: (claimId, lineItemId, opts) =>
    api.get(`/claims/${id(claimId)}/line-items/${id(lineItemId)}/receipt`, { ...opts, responseType: 'blob' }),
  /** API-012 */ submit: (claimId, idempotencyKey) =>
    api.post(`/claims/${id(claimId)}/submit`, undefined, { idempotencyKey }),
  /** API-013 */ auditEvents: (claimId, query, opts) =>
    api.get(`/claims/${id(claimId)}/audit-events`, { ...opts, query }),
}

// ---- approvals (API-014..018)
export const approvalsApi = {
  /** API-014 */ queue: (query, opts) => api.get('/approvals', { ...opts, query }),
  /** API-015 */ approve: (claimId, comment, idempotencyKey) =>
    api.post(`/claims/${id(claimId)}/approve`, { comment: comment || null }, { idempotencyKey }),
  /** API-016 */ reject: (claimId, comment, idempotencyKey) =>
    api.post(`/claims/${id(claimId)}/reject`, { comment }, { idempotencyKey }),
  /** API-017 */ sendBack: (claimId, comment, idempotencyKey) =>
    api.post(`/claims/${id(claimId)}/send-back`, { comment }, { idempotencyKey }),
  /** API-018 */ comment: (claimId, body) => api.post(`/claims/${id(claimId)}/comments`, { body }),
}

// ---- delegations (API-019..021, API-038)
export const delegationsApi = {
  /** API-019 */ list: (query, opts) => api.get('/delegations', { ...opts, query }),
  /** API-020 */ create: (body) => api.post('/delegations', body),
  /** API-021 */ cancel: (delegationId) => api.delete(`/delegations/${id(delegationId)}`),
  /** API-038 */ eligibleDelegates: (opts) => api.get('/delegations/eligible-delegates', opts),
}

// ---- review queue (API-022, API-023)
export const reviewQueueApi = {
  /** API-022 */ list: (query, opts) => api.get('/review-queue', { ...opts, query }),
  /** API-023 */ bulkApprove: (claimIds, idempotencyKey) =>
    api.post('/review-queue/bulk-approve', { claimIds }, { idempotencyKey }),
}

// ---- payout batches (API-024..029)
export const payoutApi = {
  /** API-024 */ list: (query, opts) => api.get('/payout-batches', { ...opts, query }),
  /** API-025 */ eligible: (query, opts) => api.get('/payout-batches/eligible-claims', { ...opts, query }),
  /** API-026 */ create: (claimIds, idempotencyKey) => api.post('/payout-batches', { claimIds }, { idempotencyKey }),
  /** API-027 */ get: (batchId, opts) => api.get(`/payout-batches/${id(batchId)}`, opts),
  /** API-028 */ export: (batchId, idempotencyKey) =>
    api.post(`/payout-batches/${id(batchId)}/export`, undefined, { idempotencyKey }),
  /** API-029 */ download: (batchId) => api.get(`/payout-batches/${id(batchId)}/file`, { responseType: 'blob' }),
}

// ---- dashboard (API-030..032)
export const dashboardApi = {
  /** API-030 */ spend: (query, opts) => api.get('/dashboard/spend', { ...opts, query }),
  /** API-031 */ violations: (query, opts) => api.get('/dashboard/policy-violations', { ...opts, query }),
  /** API-032 */ approvalTime: (query, opts) => api.get('/dashboard/approval-time', { ...opts, query }),
}

// ---- audit trail (API-033)
export const auditApi = {
  /** API-033 */ search: (query, opts) => api.get('/audit-events', { ...opts, query }),
}

// ---- policy rules (API-034, API-035, API-037)
export const policyApi = {
  /** API-034 */ list: (opts) => api.get('/policy-rules', opts),
  /** API-035 */ update: (ruleKey, value) => api.put(`/policy-rules/${id(ruleKey)}`, { value }),
  /** API-037 */ clientConfig: (opts) => api.get('/policy-rules/client-config', opts),
}
