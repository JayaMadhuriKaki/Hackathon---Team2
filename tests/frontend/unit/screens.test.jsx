import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { USERS, apiError, calls, claimDetail, json, mockFetch, page, renderApp } from './helpers.jsx'

describe('navigation and route gates (TC-FE-040..043)', () => {
  it('AC-13: an employee sees no Approvals, Payouts, Audit or Policy links', async () => {
    mockFetch({ 'GET /claims': page([]) })
    renderApp('/claims', USERS.employee)
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'My claims' })).toBeInTheDocument()
    for (const name of ['Approvals', 'Review queue', 'Payouts', 'Audit trail', 'Policy rules', 'Dashboard']) {
      expect(within(nav).queryByRole('link', { name })).not.toBeInTheDocument()
    }
  })

  it('AC-12: an employee visiting /payout-batches gets a forbidden state and no API call', async () => {
    const f = mockFetch({})
    renderApp('/payout-batches', USERS.employee)
    expect(screen.getByRole('heading', { name: 'Not available' })).toBeInTheDocument()
    expect(calls(f, 'GET /payout-batches')).toHaveLength(0)
  })

  it('a finance head sees admin links; an auditor sees the review queue and dashboard', () => {
    mockFetch({ 'GET /claims': page([]) })
    const { unmount } = renderApp('/claims', USERS.head)
    let nav = screen.getByRole('navigation', { name: 'Main' })
    for (const name of ['Payouts', 'Audit trail', 'Policy rules', 'Dashboard', 'Approvals']) {
      expect(within(nav).getByRole('link', { name })).toBeInTheDocument()
    }
    expect(within(nav).queryByRole('link', { name: 'Review queue' })).not.toBeInTheDocument()
    unmount()
    renderApp('/claims', USERS.auditor)
    nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Review queue' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Payouts' })).not.toBeInTheDocument()
  })
})

describe('S-02 My claims (TC-FE-050..052)', () => {
  it('shows the empty state', async () => {
    mockFetch({ 'GET /claims': page([]) })
    renderApp('/claims')
    expect(await screen.findByText('You have no claims yet.')).toBeInTheDocument()
  })

  it('shows rows with Indian-grouped totals and an Edit link only for drafts', async () => {
    mockFetch({
      'GET /claims': page([
        { claimId: 'a', claimNumber: 'CLM-2026-00001', status: 'DRAFT', total: '1500.00', lineCount: 1, createdAt: '2026-11-20T00:00:00Z' },
        { claimId: 'b', claimNumber: 'CLM-2026-00002', status: 'PAID', total: '125000.00', lineCount: 3, createdAt: '2026-11-19T00:00:00Z' },
      ]),
    })
    renderApp('/claims')
    expect(await screen.findByText('CLM-2026-00001')).toBeInTheDocument()
    expect(screen.getByText(/1,25,000\.00/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit CLM-2026-00001' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit CLM-2026-00002' })).not.toBeInTheDocument()
  })

  it('shows a readable error with the trace reference', async () => {
    mockFetch({ 'GET /claims': apiError(500, 'INTERNAL_ERROR', 'boom', null, 'tr-77') })
    renderApp('/claims')
    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument()
    expect(screen.getByText('Reference: tr-77')).toBeInTheDocument()
    expect(screen.queryByText('boom')).not.toBeInTheDocument()
  })
})

describe('S-04 claim detail (TC-FE-060..062)', () => {
  it('AC-14: another user’s claim shows the same not-found text', async () => {
    mockFetch({
      'GET /claims/zzz': apiError(404, 'NOT_FOUND'),
      'GET /claims/zzz/audit-events': apiError(404, 'NOT_FOUND'),
    })
    renderApp('/claims/zzz')
    expect(await screen.findAllByText('Not found or you do not have access.')).not.toHaveLength(0)
  })

  it('auto-rejected claims list every breaching line with limit and actual (CL-07)', async () => {
    const c = claimDetail({
      status: 'AUTO_REJECTED',
      lineItems: [
        {
          ...claimDetail().lineItems[1],
          lineNo: 1,
          policyResults: [
            { ruleId: 'POL-001', resultType: 'LIMIT_BREACH', reason: 'Meals above daily limit', configuredLimit: '1500.00', actualValue: '1800.00' },
          ],
        },
        {
          ...claimDetail().lineItems[1],
          lineItemId: 'l3',
          lineNo: 2,
          category: 'LOCAL_CAB',
          policyResults: [
            { ruleId: 'POL-004', resultType: 'LIMIT_BREACH', reason: 'Cab above trip limit', configuredLimit: '2000.00', actualValue: '2500.00' },
          ],
        },
      ],
    })
    mockFetch({ 'GET /claims/c1': c, 'GET /claims/c1/audit-events': page([]) })
    renderApp('/claims/c1')
    const banner = await screen.findByRole('alert')
    expect(banner).toHaveTextContent('automatically rejected')
    expect(banner).toHaveTextContent('Line 1 (Meals): POL-001')
    expect(banner).toHaveTextContent('Line 2 (Local cab): POL-004')
    expect(banner).toHaveTextContent('₹2,500.00')
  })

  it('shows Edit only when EDIT is in allowedActions', async () => {
    mockFetch({
      'GET /claims/c1': claimDetail({ status: 'SENT_BACK', allowedActions: ['EDIT', 'SUBMIT'], latestSendBackComment: 'Add hotel bill' }),
      'GET /claims/c1/audit-events': page([]),
    })
    renderApp('/claims/c1')
    expect(await screen.findByRole('link', { name: 'Edit and resubmit' })).toBeInTheDocument()
    expect(screen.getByText('“Add hotel bill”')).toBeInTheDocument()
  })
})

describe('S-06 approver review (TC-FE-070..074)', () => {
  it('AC-09: no decision buttons when allowedActions is empty', async () => {
    mockFetch({ 'GET /claims/c1': claimDetail({ allowedActions: [] }), 'GET /claims/c1/audit-events': page([]) })
    renderApp('/approvals/c1', USERS.manager)
    await screen.findByRole('heading', { name: 'Review CLM-2026-00042' })
    for (const name of ['Approve', 'Reject', 'Send back']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument()
    }
  })

  it('AC-07: reject cannot be confirmed with an empty or whitespace comment', async () => {
    const user = userEvent.setup()
    const f = mockFetch({
      'GET /claims/c1': claimDetail({ allowedActions: ['APPROVE', 'REJECT', 'SEND_BACK', 'COMMENT'] }),
      'GET /claims/c1/audit-events': page([]),
      'POST /claims/c1/reject': claimDetail({ status: 'REJECTED' }),
    })
    renderApp('/approvals/c1', USERS.manager)
    await user.click(await screen.findByRole('button', { name: 'Reject' }))
    const dialog = screen.getByRole('dialog', { name: 'Reject claim?' })
    const confirm = within(dialog).getByRole('button', { name: 'Reject' })
    expect(confirm).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/Reason/), '   ')
    expect(confirm).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/Reason/), 'Duplicate of CLM-2026-00011')
    expect(confirm).toBeEnabled()
    await user.click(confirm)
    await waitFor(() => expect(calls(f, 'POST /claims/c1/reject')).toHaveLength(1))
    const [, init] = calls(f, 'POST /claims/c1/reject')[0]
    expect(JSON.parse(init.body)).toEqual({ comment: 'Duplicate of CLM-2026-00011' })
    expect(init.headers['Idempotency-Key']).toBeTruthy()
  })

  it('AC-08: a 409 on approve shows a message and reloads the claim', async () => {
    const user = userEvent.setup()
    let gets = 0
    mockFetch({
      'GET /claims/c1': () => {
        gets += 1
        return claimDetail({ allowedActions: gets === 1 ? ['APPROVE', 'REJECT'] : [], status: gets === 1 ? 'PENDING_MANAGER' : 'APPROVED' })
      },
      'GET /claims/c1/audit-events': page([]),
      'POST /claims/c1/approve': apiError(409, 'STATE_CONFLICT'),
    })
    renderApp('/approvals/c1', USERS.manager)
    await user.click(await screen.findByRole('button', { name: 'Approve' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Approve' }))
    expect(await screen.findByText(/has changed since you opened it/)).toBeInTheDocument()
    await waitFor(() => expect(gets).toBe(2))
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })

  it('shows the receipt side by side for the selected line (NFR-002)', async () => {
    const user = userEvent.setup()
    const c = claimDetail({ allowedActions: ['APPROVE'] })
    c.lineItems[1].receipt = { receiptId: 'r2', contentType: 'image/png', sizeBytes: 10, uploadedAt: '2026-11-20T00:00:00Z' }
    globalThis.URL.createObjectURL = () => 'blob:receipt'
    globalThis.URL.revokeObjectURL = () => {}
    mockFetch({
      'GET /claims/c1': c,
      'GET /claims/c1/audit-events': page([]),
      'GET /claims/c1/line-items/l2/receipt': new Response(new Blob(['x'], { type: 'image/png' }), { status: 200 }),
    })
    renderApp('/approvals/c1', USERS.manager)
    const aside = await screen.findByRole('complementary', { name: 'Receipt' })
    expect(within(aside).getByText('Line 1 has no receipt.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '2' }))
    expect(await within(aside).findByRole('img', { name: /Receipt for line 2/ })).toBeInTheDocument()
  })
})

describe('S-03 claim editor (TC-FE-080..083)', () => {
  const draft = claimDetail({ status: 'DRAFT', submittedAt: null, steps: [], allowedActions: ['EDIT', 'SUBMIT'] })

  it('AC-04: submitting with no lines shows a message and sends no request', async () => {
    const user = userEvent.setup()
    const f = mockFetch({ 'GET /claims/c1': { ...draft, lineItems: [], total: '0.00' } })
    renderApp('/claims/c1/edit')
    await user.click(await screen.findByRole('button', { name: 'Submit' }))
    expect(screen.getByText('Add at least one expense line before submitting.')).toBeInTheDocument()
    expect(calls(f, 'POST /claims/c1/submit')).toHaveLength(0)
  })

  it('AC-02: a line above ₹500 without a receipt is highlighted before submit', async () => {
    const user = userEvent.setup()
    mockFetch({ 'GET /claims/c1': draft })
    renderApp('/claims/c1/edit')
    await user.click(await screen.findByRole('button', { name: 'Submit' }))
    expect(screen.getAllByText('A receipt is required for lines above ₹500.00.')).toHaveLength(2)
  })

  it('AC-05: double-clicking confirm sends exactly one submit request', async () => {
    const user = userEvent.setup()
    const withReceipts = {
      ...draft,
      lineItems: draft.lineItems.map((l) => ({ ...l, receipt: { receiptId: 'r', contentType: 'image/png', sizeBytes: 1 } })),
    }
    let resolveSubmit
    const f = mockFetch({
      'GET /claims/c1': withReceipts,
      'GET /claims/c1/audit-events': page([]),
      'POST /claims/c1/submit': () => new Promise((r) => { resolveSubmit = () => r(json(claimDetail())) }),
    })
    renderApp('/claims/c1/edit')
    await user.click(await screen.findByRole('button', { name: 'Submit' }))
    const confirm = within(screen.getByRole('dialog')).getByRole('button', { name: 'Submit claim' })
    await user.dblClick(confirm)
    await waitFor(() => expect(calls(f, 'POST /claims/c1/submit')).toHaveLength(1))
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Working…' })).toBeDisabled()
    resolveSubmit()
    await waitFor(() => expect(calls(f, 'POST /claims/c1/submit')).toHaveLength(1))
  })

  it('a non-editable claim redirects to the detail page (B-04)', async () => {
    mockFetch({ 'GET /claims/c1': claimDetail({ allowedActions: [] }), 'GET /claims/c1/audit-events': page([]) })
    renderApp('/claims/c1/edit')
    expect(await screen.findByRole('heading', { name: 'CLM-2026-00042' })).toBeInTheDocument()
  })

  it('validates a new line on the client before calling the API', async () => {
    const user = userEvent.setup()
    const f = mockFetch({ 'GET /claims/c1': draft })
    renderApp('/claims/c1/edit')
    await user.click(await screen.findByRole('button', { name: '+ Add line' }))
    await user.selectOptions(screen.getByLabelText(/Category/), 'MEALS')
    await user.type(screen.getByLabelText(/Amount/), '0')
    await user.click(screen.getByRole('button', { name: 'Save line' }))
    expect(screen.getAllByText('Amount must be greater than 0.').length).toBeGreaterThan(0)
    expect(screen.getByLabelText(/Amount/)).toHaveAttribute('aria-invalid', 'true')
    expect(calls(f, 'POST /claims/c1/line-items')).toHaveLength(0)
  })
})

describe('S-07 review queue bulk approve (TC-FE-090..091)', () => {
  const items = [
    { claimId: 'a', claimNumber: 'CLM-2026-00101', status: 'PENDING_AUDITOR', queue: 'AUDITOR_STEP', total: '30000.00', claimant: { name: 'A' }, department: 'Consulting', categories: ['HOTEL'], flags: [], submittedAt: '2026-11-20T00:00:00Z', waitingSince: '2026-11-20T00:00:00Z' },
    { claimId: 'b', claimNumber: 'CLM-2026-00102', status: 'PENDING_AUDITOR', queue: 'AUDITOR_STEP', total: '40000.00', claimant: { name: 'B' }, department: 'Consulting', categories: ['MEALS'], flags: ['DUPLICATE'], submittedAt: '2026-11-20T00:00:00Z', waitingSince: '2026-11-20T00:00:00Z' },
    { claimId: 'c', claimNumber: 'CLM-2026-00103', status: 'SAMPLE_AUDIT_PENDING', queue: 'SAMPLE_AUDIT', total: '2000.00', claimant: { name: 'C' }, department: 'Finance', categories: ['LOCAL_CAB'], flags: [], submittedAt: '2026-11-20T00:00:00Z', waitingSince: '2026-11-20T00:00:00Z' },
  ]

  it('AC-10: one call with one key; "2 approved, 1 failed"; failed row shows its reason', async () => {
    const user = userEvent.setup()
    const f = mockFetch({
      'GET /review-queue': page(items),
      'GET /departments': [{ departmentId: 'd1', name: 'Consulting' }],
      'POST /review-queue/bulk-approve': {
        approved: 2,
        failed: 1,
        results: [
          { claimId: 'a', outcome: 'APPROVED' },
          { claimId: 'b', outcome: 'FAILED', code: 'STATE_CONFLICT', message: 'Already approved' },
          { claimId: 'c', outcome: 'APPROVED' },
        ],
      },
    })
    renderApp('/review-queue', USERS.auditor)
    await user.click(await screen.findByLabelText('Select all rows on this page'))
    expect(screen.getByRole('region', { name: 'Bulk approve' })).toHaveTextContent('3 selected · ₹72,000.00')
    await user.click(screen.getByRole('button', { name: 'Approve selected (3)' }))
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('1 of them has a duplicate or weekend flag')
    await user.click(within(dialog).getByRole('button', { name: 'Approve 3' }))
    expect(await screen.findByText('2 approved, 1 failed.')).toBeInTheDocument()
    const post = calls(f, 'POST /review-queue/bulk-approve')
    expect(post).toHaveLength(1)
    expect(JSON.parse(post[0][1].body)).toEqual({ claimIds: ['a', 'b', 'c'] })
    expect(post[0][1].headers['Idempotency-Key']).toBeTruthy()
    expect(await screen.findByRole('note')).toHaveTextContent('This item has changed since you opened it')
  })

  it('filters are validated and written to the request', async () => {
    const user = userEvent.setup()
    const f = mockFetch({ 'GET /review-queue': page([]), 'GET /departments': [] })
    renderApp('/review-queue', USERS.auditor)
    await screen.findByText('No claims match these filters.')
    await user.type(screen.getByLabelText('Min ₹'), '5000')
    await user.type(screen.getByLabelText('Max ₹'), '100')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    expect(screen.getByText('Maximum must be at least the minimum.')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Max ₹'))
    await user.type(screen.getByLabelText('Max ₹'), '90000')
    await user.selectOptions(screen.getByLabelText('Flag'), 'DUPLICATE')
    await user.click(screen.getByRole('button', { name: 'Apply' }))
    await waitFor(() => {
      const last = calls(f, 'GET /review-queue').at(-1)[0]
      expect(last).toContain('minAmount=5000')
      expect(last).toContain('maxAmount=90000')
      expect(last).toContain('flag=DUPLICATE')
    })
  })
})

describe('S-10 payout batch (TC-FE-100)', () => {
  it('AC-11: export only for CREATED; then control summary and download, no export', async () => {
    const user = userEvent.setup()
    let exported = false
    const batch = () => ({
      batch: {
        batchId: 'b1', batchNumber: 'PB-2026-0001', status: exported ? 'EXPORTED' : 'CREATED', claimCount: 2,
        totalAmount: '127450.00', createdAt: '2026-11-27T05:00:00Z', exportedAt: exported ? '2026-11-27T06:00:00Z' : null,
        fileName: exported ? 'EXPENSEFLOW_PAYOUT_PB-2026-0001_20261127.csv' : null,
        fileSha256: exported ? 'ab'.repeat(32) : null,
      },
      claims: [],
    })
    const f = mockFetch({
      'GET /payout-batches/b1': batch,
      'POST /payout-batches/b1/export': () => {
        exported = true
        return { batchId: 'b1', batchNumber: 'PB-2026-0001', rowCount: 2, totalAmount: '127450.00' }
      },
    })
    renderApp('/payout-batches/b1', USERS.head)
    await user.click(await screen.findByRole('button', { name: 'Export batch' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Export' }))
    expect(await screen.findByRole('heading', { name: 'Control summary' })).toBeInTheDocument()
    expect(screen.getByText('ab'.repeat(32), { exact: false })).toBeInTheDocument()
    expect(screen.getAllByText('₹1,27,450.00').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Export batch' })).not.toBeInTheDocument()
    expect(calls(f, 'POST /payout-batches/b1/export')[0][1].headers['Idempotency-Key']).toBeTruthy()
  })
})

describe('S-08 delegations (TC-FE-110)', () => {
  it('AC-15: an overlap error is shown on the date fields', async () => {
    const user = userEvent.setup()
    mockFetch({
      'GET /delegations': page([]),
      'GET /delegations/eligible-delegates': [{ userId: 'u-aud', name: 'Arjun Mehta', employeeCode: 'EMP0002' }],
      'POST /delegations': apiError(422, 'DELEGATION_OVERLAP'),
    })
    renderApp('/delegations', USERS.manager)
    await user.selectOptions(await screen.findByLabelText(/Delegate/), 'u-aud')
    await user.type(screen.getByLabelText(/Start date/), '2030-01-10')
    await user.type(screen.getByLabelText(/End date/), '2030-01-20')
    await user.click(screen.getByRole('button', { name: 'Create delegation' }))
    expect(await screen.findByText(/already have a delegation for these dates/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Start date/)).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('S-12 audit trail (TC-FE-120)', () => {
  it('renders read-only rows with no edit or delete controls (NFR-004)', async () => {
    mockFetch({
      'GET /audit-events': page([
        { eventId: 1, occurredAt: '2026-11-27T05:00:00Z', action: 'CLAIM_SUBMITTED', actor: { name: 'Ananya Rao' }, entityType: 'CLAIM', claimNumber: 'CLM-2026-00042', fromState: 'DRAFT', toState: 'SUBMITTED', comment: null, details: { total: '100.00' } },
      ]),
    })
    renderApp('/audit-trail', USERS.head)
    expect(await screen.findByText('CLAIM_SUBMITTED')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /edit|delete/i })).not.toBeInTheDocument()
  })
})

describe('contract alignment (TC-FE-140..141)', () => {
  it('My claims: sorting by Total sends sort=totalAmount, then -totalAmount (API-004 sort enum)', async () => {
    const user = userEvent.setup()
    const f = mockFetch({
      'GET /claims': page([
        { claimId: 'a', claimNumber: 'CLM-2026-00001', status: 'PAID', total: '1500.00', lineCount: 1, createdAt: '2026-11-20T00:00:00Z' },
      ]),
    })
    renderApp('/claims')
    await user.click(await screen.findByRole('button', { name: /^Total/ }))
    await waitFor(() => expect(calls(f, 'GET /claims').at(-1)[0]).toContain('sort=totalAmount'))
    await user.click(screen.getByRole('button', { name: /^Total/ }))
    await waitFor(() => expect(calls(f, 'GET /claims').at(-1)[0]).toContain('sort=-totalAmount'))
    expect(screen.getByRole('columnheader', { name: /Total/ })).toHaveAttribute('aria-sort', 'descending')
  })

  it('Dashboard: null average approval time shows an empty state, not "— h" (API-032)', async () => {
    mockFetch({
      'GET /departments': [],
      'GET /dashboard/spend': { groupBy: 'category', items: [], grandTotal: '0.00' },
      'GET /dashboard/policy-violations': { items: [], total: 0 },
      'GET /dashboard/approval-time': { approvedClaims: 0, averageHours: null, routedClaims: 0, averageHoursRouted: null },
    })
    renderApp('/dashboard', USERS.auditor)
    expect(await screen.findByText('No approved claims in this period.')).toBeInTheDocument()
    expect(screen.queryByText('— h')).not.toBeInTheDocument()
  })
})
