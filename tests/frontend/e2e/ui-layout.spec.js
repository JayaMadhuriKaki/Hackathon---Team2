// Browser UI checks with the API mocked at the network layer (no backend needed).
// Covers RESP-01..04 (1280×720 projector, 360×640 phone), A11Y-01/03/07 and NFR-002.
import { expect, test } from './pw.js'

const USERS = {
  'employee@expenseflow.test': {
    userId: 'u-emp', name: 'Ananya Rao', employeeCode: 'EMP0005', role: 'EMPLOYEE',
    isManager: false, isApprover: false, canDelegate: false, isAdmin: false,
  },
  'manager@expenseflow.test': {
    userId: 'u-mgr', name: 'Manoj Pillai', employeeCode: 'EMP0004', role: 'EMPLOYEE',
    isManager: true, isApprover: true, canDelegate: true, isAdmin: false,
  },
}

// 1×1 PNG
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
)

function claim(overrides = {}) {
  return {
    claimId: 'c1', claimNumber: 'CLM-2026-00042', status: 'PENDING_MANAGER', total: '27450.00',
    claimant: { userId: 'u-emp', name: 'Ananya Rao', employeeCode: 'EMP0005' }, department: 'Consulting',
    createdAt: '2026-11-20T04:00:00Z', submittedAt: '2026-11-20T05:00:00Z', submissionNo: 1,
    lineItems: [
      { lineItemId: 'l1', lineNo: 1, category: 'HOTEL', amount: '11000.00', expenseDate: '2026-11-10', vendor: 'Taj Lands End', receiptNumber: 'H-1001', city: 'Mumbai', checkInDate: '2026-11-10', checkOutDate: '2026-11-12', nights: 2, receipt: { receiptId: 'r1', contentType: 'image/png', sizeBytes: 70 }, policyResults: [] },
      { lineItemId: 'l2', lineNo: 2, category: 'MEALS', amount: '1450.00', expenseDate: '2026-11-15', vendor: 'Cafe Madras', receiptNumber: 'M-2002', receipt: { receiptId: 'r2', contentType: 'image/png', sizeBytes: 70 }, policyResults: [{ ruleId: 'POL-008', resultType: 'WEEKEND_FLAG', reason: 'Expense on a weekend' }] },
      { lineItemId: 'l3', lineNo: 3, category: 'LOCAL_CAB', amount: '15000.00', expenseDate: '2026-11-11', vendor: 'City Cabs Pvt Ltd', receiptNumber: 'C-3003', receipt: null, policyResults: [] },
    ],
    steps: [{ stepId: 's1', submissionNo: 1, level: 1, stepType: 'MANAGER', status: 'PENDING', assignedApprover: { userId: 'u-mgr', name: 'Manoj Pillai' } }],
    comments: [],
    allowedActions: ['APPROVE', 'REJECT', 'SEND_BACK', 'COMMENT'],
    ...overrides,
  }
}

async function mockApi(page, extra = {}) {
  let current = null
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1', '')
    const key = `${req.method()} ${path}`
    const reply = (body, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

    if (key === 'POST /auth/login') {
      const { email } = req.postDataJSON()
      current = USERS[email]
      return current
        ? reply({ accessToken: 'mock-token', tokenType: 'Bearer', user: current })
        : reply({ code: 'UNAUTHENTICATED', message: 'bad', traceId: 't' }, 401)
    }
    if (key === 'GET /me') return reply(current)
    if (key === 'GET /policy-rules/client-config') {
      return reply({
        receiptMandatoryAbove: '500.00', tier1Cities: ['Kolkata', 'Mumbai', 'Delhi', 'Chennai', 'Bangalore'],
        categories: ['MEALS', 'HOTEL', 'LOCAL_CAB'], allowedReceiptTypes: ['application/pdf', 'image/jpeg', 'image/png'],
        maxReceiptBytes: 5242880,
      })
    }
    if (key.endsWith('/receipt') && req.method() === 'GET') {
      return route.fulfill({ status: 200, contentType: 'image/png', body: PNG })
    }
    if (path.endsWith('/audit-events')) return reply({ items: [], page: 1, pageSize: 100, total: 0 })
    if (extra[key]) return reply(typeof extra[key] === 'function' ? extra[key]() : extra[key])
    return reply({ code: 'NOT_FOUND', message: 'nf', traceId: 't' }, 404)
  })
}

async function login(page, email, next = '/claims') {
  await page.goto(`/login?next=${encodeURIComponent(next)}`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('any-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
}

async function noHorizontalScroll(page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow, 'page must not scroll horizontally').toBeLessThanOrEqual(0)
}

async function unlabeledInputs(page) {
  return page.evaluate(() =>
    [...document.querySelectorAll('input, select, textarea')]
      .filter((el) => el.type !== 'hidden')
      .filter((el) => !(el.labels && el.labels.length) && !el.getAttribute('aria-label') && !el.getAttribute('aria-labelledby'))
      .map((el) => el.outerHTML.slice(0, 80)),
  )
}

test.describe('projector 1280×720', () => {
  test('TC-E2E-UI-01 login → my claims: no horizontal scroll, labelled fields', async ({ page }) => {
    await mockApi(page, {
      'GET /claims': {
        items: [{ claimId: 'c1', claimNumber: 'CLM-2026-00042', status: 'PENDING_MANAGER', total: '27450.00', lineCount: 3, createdAt: '2026-11-20T04:00:00Z', submittedAt: '2026-11-20T05:00:00Z' }],
        page: 1, pageSize: 20, total: 1,
      },
    })
    await page.goto('/login')
    expect(await unlabeledInputs(page)).toEqual([])
    await login(page, 'employee@expenseflow.test')
    await expect(page.getByRole('heading', { name: 'My claims' })).toBeVisible()
    await expect(page.getByText('₹27,450.00')).toBeVisible()
    await expect(page).toHaveTitle('My claims · ExpenseFlow')
    await noHorizontalScroll(page)
  })

  test('TC-E2E-UI-02 approver review shows claim and receipt side by side (NFR-002, AC-06)', async ({ page }) => {
    await mockApi(page, { 'GET /claims/c1': claim() })
    await login(page, 'manager@expenseflow.test', '/approvals/c1')
    const table = page.locator('table.lines-table')
    const receipt = page.getByRole('complementary', { name: 'Receipt' })
    await expect(receipt.getByRole('img', { name: /Receipt for line 1/ })).toBeVisible()
    const t = await table.boundingBox()
    const r = await receipt.boundingBox()
    expect(r.x).toBeGreaterThan(t.x + t.width - 1) // receipt is to the right
    expect(r.y).toBeLessThan(t.y + t.height) // and vertically overlapping
    await noHorizontalScroll(page)

    // Keyboard: ↓ moves to line 2 and swaps the receipt
    await page.getByRole('button', { name: '1', exact: true }).focus()
    await page.keyboard.press('ArrowDown')
    await expect(receipt.getByRole('img', { name: /Receipt for line 2/ })).toBeVisible()
    await page.keyboard.press('ArrowDown')
    await expect(receipt.getByText('Line 3 has no receipt.')).toBeVisible()
  })

  test('TC-E2E-UI-03 reject with keyboard only: Esc closes, empty reason blocked (A11Y-03, A11Y-07, AC-07)', async ({ page }) => {
    let rejected = null
    await mockApi(page, {
      'GET /claims/c1': () => claim(rejected ? { status: 'REJECTED', allowedActions: [] } : {}),
      'GET /approvals': { items: [], page: 1, pageSize: 20, total: 0 },
    })
    await page.route('**/api/v1/claims/c1/reject', async (route) => {
      rejected = route.request().postDataJSON()
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(claim({ status: 'REJECTED' })) })
    })
    await login(page, 'manager@expenseflow.test', '/approvals/c1')
    const rejectBtn = page.getByRole('button', { name: 'Reject' })
    await rejectBtn.focus()
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog', { name: 'Reject claim?' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByLabel(/Reason/)).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(rejectBtn).toBeFocused() // focus restored

    await page.keyboard.press('Enter')
    await expect(dialog.getByRole('button', { name: 'Reject' })).toBeDisabled()
    await page.keyboard.type('Cab amount looks like a multi-day booking')
    await page.keyboard.press('Tab') // Cancel
    await page.keyboard.press('Tab') // Reject
    await expect(dialog.getByRole('button', { name: 'Reject' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('heading', { name: 'Approvals' })).toBeVisible()
    expect(rejected).toEqual({ comment: 'Cab amount looks like a multi-day booking' })
  })
})

test.describe('phone 360×640 (NFR-001)', () => {
  test.use({ viewport: { width: 360, height: 640 }, hasTouch: true, isMobile: true })

  test('TC-E2E-UI-04 claim capture: no horizontal scroll, 44px targets, sticky submit bar (RESP-02, AC-01)', async ({ page }) => {
    const draft = claim({ status: 'DRAFT', submittedAt: null, steps: [], allowedActions: ['EDIT', 'SUBMIT'] })
    await mockApi(page, { 'GET /claims/c1': draft })
    await login(page, 'employee@expenseflow.test', '/claims/c1/edit')
    await expect(page.getByRole('heading', { name: 'Edit CLM-2026-00042' })).toBeVisible()
    await noHorizontalScroll(page)

    const bar = page.locator('.sticky-bar')
    const box = await bar.boundingBox()
    expect(box.y + box.height).toBeLessThanOrEqual(640 + 1) // pinned to the bottom of the viewport
    await expect(bar.getByRole('button', { name: 'Submit' })).toBeVisible()

    await page.getByRole('button', { name: '+ Add line' }).click()
    await page.getByLabel(/Category/).selectOption('HOTEL')
    await expect(page.getByLabel(/City/)).toBeVisible()
    await expect(page.getByText('Nights: —')).toBeVisible()
    await page.getByLabel(/Check-in/).fill('2026-11-10')
    await page.getByLabel(/Check-out/).fill('2026-11-12')
    await expect(page.getByText('Nights: 2')).toBeVisible()
    await noHorizontalScroll(page)
    expect(await unlabeledInputs(page)).toEqual([])

    const small = await page.evaluate(() =>
      [...document.querySelectorAll('.line-form input:not([type=file]), .line-form select, .line-form button, .sticky-bar .btn')]
        .filter((el) => el.offsetParent !== null)
        .filter((el) => el.getBoundingClientRect().height < 44)
        .map((el) => el.outerHTML.slice(0, 60)),
    )
    expect(small).toEqual([])

    // Submit pre-check: line 3 is ₹15,000 with no receipt
    await page.getByRole('button', { name: 'Cancel' }).click()
    await page.getByRole('button', { name: 'Submit' }).click()
    await expect(page.getByText('A receipt is required for lines above ₹500.00.')).toBeVisible()
  })

  test('TC-E2E-UI-05 tables become cards and the nav collapses into a menu (RESP-03)', async ({ page }) => {
    await mockApi(page, {
      'GET /claims': {
        items: [{ claimId: 'c1', claimNumber: 'CLM-2026-00042', status: 'APPROVED', total: '100000.00', lineCount: 3, createdAt: '2026-11-20T04:00:00Z' }],
        page: 1, pageSize: 20, total: 1,
      },
    })
    await login(page, 'employee@expenseflow.test')
    await expect(page.getByText('₹1,00,000.00')).toBeVisible()
    // Header is visually hidden (kept for screen readers); each row renders as a block card.
    const head = await page.locator('thead').boundingBox()
    expect(head.height).toBeLessThanOrEqual(1)
    expect(await page.locator('tbody tr').first().evaluate((el) => getComputedStyle(el).display)).toBe('block')
    await noHorizontalScroll(page)
    await expect(page.getByRole('link', { name: 'My claims' })).toBeHidden()
    await page.getByRole('button', { name: 'Menu' }).click()
    await expect(page.getByRole('link', { name: 'My claims' })).toBeVisible()
  })

  test('TC-E2E-UI-06 approver review on phone: receipt above the lines, actions reachable (RESP-04)', async ({ page }) => {
    await mockApi(page, { 'GET /claims/c1': claim() })
    await login(page, 'manager@expenseflow.test', '/approvals/c1')
    const receipt = page.getByRole('complementary', { name: 'Receipt' })
    await expect(receipt.getByRole('img')).toBeVisible()
    await noHorizontalScroll(page)
    await expect(page.locator('.sticky-bar').getByRole('button', { name: 'Approve' })).toBeInViewport()
  })
})
