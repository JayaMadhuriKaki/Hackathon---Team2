// Shared test helpers: synthetic users, a fetch mock keyed by "METHOD /path", and an app renderer.
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { AppRoutes } from '../../../frontend/src/App.jsx'
import { MockAuthProvider } from '../../../frontend/src/context/AuthContext.jsx'
import { ToastProvider } from '../../../frontend/src/context/ToastContext.jsx'

const base = { departmentName: 'Consulting', email: 'x@expenseflow.test' }

export const USERS = {
  employee: {
    ...base, userId: 'u-emp', name: 'Ananya Rao', employeeCode: 'EMP0005', role: 'EMPLOYEE',
    isManager: false, isApprover: false, canDelegate: false, isAdmin: false,
  },
  manager: {
    ...base, userId: 'u-mgr', name: 'Manoj Pillai', employeeCode: 'EMP0004', role: 'EMPLOYEE',
    isManager: true, isApprover: true, canDelegate: true, isAdmin: false,
  },
  auditor: {
    ...base, userId: 'u-aud', name: 'Arjun Mehta', employeeCode: 'EMP0002', role: 'FINANCE_AUDITOR',
    isManager: true, isApprover: true, canDelegate: true, isAdmin: false,
  },
  head: {
    ...base, userId: 'u-head', name: 'Farah Iyer', employeeCode: 'EMP0001', role: 'FINANCE_HEAD',
    isManager: true, isApprover: true, canDelegate: true, isAdmin: true,
  },
}

export function json(body, status = 200, headers = {}) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

export function apiError(status, code, message = code, details = null, traceId = 'trace-123') {
  return json({ code, message, details, traceId }, status)
}

/**
 * Installs a fetch mock. `routes` maps "GET /claims/1" (path below /api/v1, no query) to a
 * Response, a body object, or a function (req) => Response|body. Unmatched calls return 404.
 * Returns the vi.fn so tests can inspect calls.
 */
export function mockFetch(routes) {
  const fn = vi.fn(async (url, init = {}) => {
    const u = new URL(url, 'http://localhost')
    const path = u.pathname.replace(/^\/api\/v1/, '')
    const key = `${init.method ?? 'GET'} ${path}`
    let handler = routes[key]
    if (handler === undefined) return apiError(404, 'NOT_FOUND')
    if (typeof handler === 'function') handler = await handler({ url: u, init, path })
    if (handler instanceof Response) return handler
    return json(handler)
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

export function calls(fetchMock, key) {
  return fetchMock.mock.calls.filter(([url, init = {}]) => {
    const path = new URL(url, 'http://localhost').pathname.replace(/^\/api\/v1/, '')
    return `${init.method ?? 'GET'} ${path}` === key
  })
}

export function renderApp(path, user = USERS.employee) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider>
        <MockAuthProvider value={{ user }}>
          <AppRoutes />
        </MockAuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  )
}

export function page(items, extra = {}) {
  return { items, page: 1, pageSize: 20, total: items.length, ...extra }
}

/** A claim as returned by API-006 (ClaimDtos.ClaimDetail). */
export function claimDetail(overrides = {}) {
  return {
    claimId: 'c1',
    claimNumber: 'CLM-2026-00042',
    status: 'PENDING_MANAGER',
    total: '27450.00',
    claimant: { userId: 'u-emp', name: 'Ananya Rao', employeeCode: 'EMP0005' },
    department: 'Consulting',
    createdAt: '2026-11-20T04:00:00Z',
    updatedAt: '2026-11-20T04:00:00Z',
    submittedAt: '2026-11-20T05:00:00Z',
    decidedAt: null,
    submissionNo: 1,
    configVersion: 1,
    decisionReason: null,
    latestSendBackComment: null,
    lineItems: [
      {
        lineItemId: 'l1', lineNo: 1, category: 'HOTEL', amount: '11000.00', expenseDate: '2026-11-10',
        vendor: 'Taj', receiptNumber: 'R-1', city: 'Mumbai', checkInDate: '2026-11-10',
        checkOutDate: '2026-11-12', nights: 2, receipt: null, policyResults: [],
      },
      {
        lineItemId: 'l2', lineNo: 2, category: 'MEALS', amount: '16450.00', expenseDate: '2026-11-15',
        vendor: 'Cafe', receiptNumber: 'R-2', city: null, checkInDate: null, checkOutDate: null, nights: null,
        receipt: null,
        policyResults: [
          { ruleId: 'POL-008', resultType: 'WEEKEND_FLAG', reason: 'Weekend spend', configuredLimit: null, actualValue: null },
        ],
      },
    ],
    steps: [
      {
        stepId: 's1', submissionNo: 1, level: 1, stepType: 'MANAGER', status: 'PENDING',
        assignedApprover: { userId: 'u-mgr', name: 'Manoj Pillai', employeeCode: 'EMP0004' },
        skipReason: null, actedBy: null, onBehalfOf: null, activatedAt: '2026-11-20T05:00:00Z', actedAt: null,
      },
    ],
    comments: [],
    allowedActions: [],
    ...overrides,
  }
}
