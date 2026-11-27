// Demo-path E2E cases (CLAUDE.md §16.5, frontend/SPEC.md §9.1) against the seeded Docker Compose stack.
// Prerequisites: `docker compose up -d --build` with SEED_DEMO_USERS=true, then
//   E2E_PASSWORD=<SEED_DEMO_PASSWORD> npm run test:e2e:stack   (from frontend/)
// Each run creates its own claims with unique vendors, receipt numbers, dates and receipt bytes,
// so re-runs do not trip the duplicate rules (POL-006/007/009) or the daily meals sum (POL-001).
import { randomBytes } from 'node:crypto'
import { expect, test } from './pw.js'

const PASSWORD = process.env.E2E_PASSWORD
const RUN = Date.now().toString(36).toUpperCase()
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
)

test.skip(!PASSWORD, 'Set E2E_PASSWORD to the stack’s SEED_DEMO_PASSWORD to run the demo-path suite')
test.describe.configure({ mode: 'serial' })

// ---------- helpers ----------
const ymd = (d) => d.toISOString().slice(0, 10)
// A random-ish past date for this run, offset per use; snapped to the requested weekday (1=Mon … 6=Sat).
const runSeed = Number.parseInt(RUN.slice(-4), 36) % 150
let dateOffset = 0
function pastDate(weekday) {
  const d = new Date()
  d.setUTCDate(d.getUTCDate() - 20 - runSeed - dateOffset)
  dateOffset += 7
  while (d.getUTCDay() !== weekday) d.setUTCDate(d.getUTCDate() - 1)
  return d
}
function addDays(d, n) {
  const c = new Date(d)
  c.setUTCDate(c.getUTCDate() + n)
  return c
}
let seq = 0
const uniq = (p) => `${p}-${RUN}-${++seq}`
const receipt = () => ({
  name: `${uniq('receipt')}.png`,
  mimeType: 'image/png',
  buffer: Buffer.concat([PNG, randomBytes(16)]), // unique content → unique SHA-256 (POL-007)
})

async function login(page, who, next = '/claims') {
  await page.goto(`/login?next=${encodeURIComponent(next)}`)
  await page.getByLabel('Email').fill(`${who}@expenseflow.test`)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

// The JWT lives in memory only (SPEC §6.2): a full page load signs the user out, so once signed in
// the tests navigate with in-app links, never page.goto.
async function nav(page, name) {
  const menu = page.getByRole('button', { name: 'Menu' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name }).click()
}

/** lines: [{ category, amount, date, city?, nights?, withReceipt? }] → returns { claimId, claimNumber } */
async function createClaim(page, lines) {
  await nav(page, 'My claims')
  await page.getByRole('link', { name: 'New claim' }).first().click()
  await expect(page.getByRole('heading', { name: 'New claim' })).toBeVisible()
  for (const [i, line] of lines.entries()) {
    await page.getByRole('button', { name: '+ Add line' }).click()
    await page.getByLabel(/Category/).selectOption(line.category)
    await page.getByLabel(/Amount/).fill(line.amount)
    if (line.category === 'HOTEL') {
      await page.getByLabel(/City/).fill(line.city ?? 'Mumbai')
      await page.getByLabel(/Check-in/).fill(ymd(line.date))
      await page.getByLabel(/Check-out/).fill(ymd(addDays(line.date, line.nights)))
    } else {
      await page.getByLabel(/Expense date/).fill(ymd(line.date))
    }
    await page.getByLabel(/Vendor/).fill(uniq('Vendor'))
    await page.getByLabel(/Receipt number/).fill(uniq('RN'))
    if (line.withReceipt) await page.getByLabel(/Receipt \(PDF/).setInputFiles(receipt())
    await page.getByRole('button', { name: 'Save line' }).click()
    await expect(page.getByText(`Line ${i + 1} saved.`)).toBeVisible()
  }
  await expect(page).toHaveURL(/\/claims\/[^/]+\/edit$/)
  const claimId = page.url().split('/claims/')[1].split('/')[0]
  const claimNumber = (await page.getByRole('heading', { level: 1 }).textContent()).replace('Edit ', '').trim()
  return { claimId, claimNumber }
}

async function submit(page, claimId) {
  await page.locator('.sticky-bar').getByRole('button', { name: 'Submit' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Submit claim' }).click()
  await expect(page).toHaveURL(new RegExp(`/claims/${claimId}$`))
}

const status = (page) => page.locator('.claim-header .badge').first()

async function openFromApprovals(page, claimNumber) {
  await nav(page, 'Approvals')
  await page.getByRole('button', { name: claimNumber }).click()
  await expect(page.getByRole('heading', { name: `Review ${claimNumber}` })).toBeVisible()
}

async function decide(page, action, comment) {
  await page.locator('.sticky-bar').getByRole('button', { name: action }).click()
  const dialog = page.getByRole('dialog')
  if (comment) await dialog.getByRole('textbox').fill(comment)
  await dialog.getByRole('button', { name: action }).click()
  await expect(dialog).toBeHidden()
}

// Shared across the serial chain E2E-04 → E2E-09 → E2E-10
const shared = {}

// ---------- cases ----------
test('TC-E2E-001 phone-width claim capture and submit → auto-approved (FR-001..003, NFR-001)', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 640 })
  await login(page, 'employee')
  const { claimId } = await createClaim(page, [{ category: 'MEALS', amount: '450.00', date: pastDate(2) }])
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow).toBeLessThanOrEqual(0)
  await submit(page, claimId)
  // Auto-approved claims become Approved, or Sample audit when drawn in the 10% sample (CL-14)
  await expect(status(page)).toHaveText(/Approved|Sample audit/)
})

test('TC-E2E-002 total of exactly ₹25,000.00 is auto-approved (CL-02, CL-03 boundary)', async ({ page }) => {
  await login(page, 'employee')
  const monday = pastDate(1)
  const { claimId } = await createClaim(page, [
    { category: 'HOTEL', amount: '24000.00', date: monday, city: 'Mumbai', nights: 4, withReceipt: true },
    { category: 'MEALS', amount: '1000.00', date: addDays(monday, 1), withReceipt: true },
  ])
  await submit(page, claimId)
  await expect(page.locator('.claim-header')).toContainText('₹25,000.00')
  await expect(status(page)).toHaveText(/Approved|Sample audit/)
})

test('TC-E2E-003 meals above ₹1,500/day → auto-rejected with reasons (POL-001, CL-07)', async ({ page }) => {
  await login(page, 'employee')
  const { claimId } = await createClaim(page, [
    { category: 'MEALS', amount: '1800.00', date: pastDate(3), withReceipt: true },
  ])
  await submit(page, claimId)
  await expect(status(page)).toHaveText(/Auto-rejected/)
  const banner = page.getByRole('alert').filter({ hasText: 'automatically rejected' })
  await expect(banner).toContainText('POL-001')
  await expect(banner).toContainText('₹1,800.00')
})

test('TC-E2E-004 ₹30,000 claim: manager approves → appears in the auditor review queue (FR-005, CL-03)', async ({ page }) => {
  await login(page, 'employee')
  const { claimId, claimNumber } = await createClaim(page, [
    { category: 'HOTEL', amount: '30000.00', date: pastDate(1), city: 'Mumbai', nights: 5, withReceipt: true },
  ])
  await submit(page, claimId)
  await expect(status(page)).toHaveText('● Pending manager')
  Object.assign(shared, { claimId, claimNumber })

  await login(page, 'manager', '/approvals')
  await openFromApprovals(page, claimNumber)
  await expect(page.getByRole('complementary', { name: 'Receipt' }).getByRole('img')).toBeVisible() // NFR-002
  await decide(page, 'Approve')
  await expect(page.getByRole('heading', { name: 'Approvals' })).toBeVisible()

  await login(page, 'auditor', '/review-queue')
  await expect(page.getByRole('button', { name: claimNumber })).toBeVisible()
})

test('TC-E2E-005 auditor bulk-approves the claim from the review queue (FR-009, FR-010)', async ({ page }) => {
  test.skip(!shared.claimNumber, 'depends on TC-E2E-004')
  await login(page, 'auditor', '/review-queue?queue=AUDITOR_STEP')
  await page.getByLabel(`Select claim ${shared.claimNumber}`).check()
  await page.getByRole('button', { name: 'Approve selected (1)' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Approve 1' }).click()
  await expect(page.getByText('1 approved, 0 failed.')).toBeVisible()
  await expect(page.getByRole('button', { name: shared.claimNumber })).toBeHidden()
})

test('TC-E2E-006 payout: create batch → export → claims Paid → download CSV (FR-012..014, CL-06)', async ({ page }) => {
  test.skip(!shared.claimNumber, 'depends on TC-E2E-005')
  await login(page, 'financehead', '/payout-batches')
  await page.getByRole('button', { name: 'New batch' }).click()
  await page.getByLabel(`Select claim ${shared.claimNumber}`).check()
  await page.getByRole('region', { name: 'Create batch' }).getByRole('button', { name: 'Create batch' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Create batch' }).click()
  await expect(page.getByRole('heading', { name: /^Batch PB-/ })).toBeVisible()
  await page.getByRole('button', { name: 'Export batch' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Export' }).click()
  await expect(page.getByRole('heading', { name: 'Control summary' })).toBeVisible()
  await expect(page.locator('.control-summary .hash')).toContainText(/[0-9a-f]{64}/)
  await expect(page.getByRole('cell', { name: /Paid/ }).first()).toBeVisible()
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Download CSV' }).click(),
  ])
  expect(download.suggestedFilename()).toMatch(/^EXPENSEFLOW_PAYOUT_PB-\d{4}-\d{4}_\d{8}\.csv$/)
})

test('TC-E2E-007 reject requires a comment and ends the claim (FR-020, CL-04)', async ({ page }) => {
  await login(page, 'employee')
  const { claimId, claimNumber } = await createClaim(page, [
    { category: 'LOCAL_CAB', amount: '400.00', date: pastDate(6) }, // Saturday → routed to manager (POL-008)
  ])
  await submit(page, claimId)
  await expect(status(page)).toHaveText('● Pending manager')

  await login(page, 'manager', '/approvals')
  await openFromApprovals(page, claimNumber)
  await page.locator('.sticky-bar').getByRole('button', { name: 'Reject' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: 'Reject' })).toBeDisabled()
  await dialog.getByRole('textbox').fill('   ')
  await expect(dialog.getByRole('button', { name: 'Reject' })).toBeDisabled()
  await dialog.getByRole('textbox').fill('Weekend cab not related to client work')
  await dialog.getByRole('button', { name: 'Reject' }).click()

  await login(page, 'employee', `/claims/${claimId}`)
  await expect(status(page)).toHaveText(/Rejected/)
  await expect(page.getByText('“Weekend cab not related to client work”').first()).toBeVisible()
})

test('TC-E2E-008 send back → edit → resubmit restarts at the manager step (FR-007, CL-12)', async ({ page }) => {
  await login(page, 'employee')
  const { claimId, claimNumber } = await createClaim(page, [
    { category: 'MEALS', amount: '480.00', date: pastDate(0) }, // Sunday → manager
  ])
  await submit(page, claimId)

  await login(page, 'manager', '/approvals')
  await openFromApprovals(page, claimNumber)
  await decide(page, 'Send back', 'Please confirm the attendees')

  await login(page, 'employee', `/claims/${claimId}`)
  await expect(status(page)).toHaveText(/Sent back/)
  await page.getByRole('link', { name: 'Edit and resubmit' }).click()
  await expect(page.getByText('Resubmitting restarts approval from the first step.')).toBeVisible()
  await page.getByRole('button', { name: 'Edit line 1' }).click()
  await page.getByLabel(/Amount/).fill('470.00')
  await page.getByRole('button', { name: 'Save line' }).click()
  await expect(page.getByText('Line 1 saved.')).toBeVisible()
  await submit(page, claimId)
  await expect(status(page)).toHaveText('● Pending manager')
  await expect(page.getByText(/Earlier submission/)).toBeVisible()
})

test('TC-E2E-009 delegate (finance auditor) approves a manager step on behalf (FR-008, CL-13)', async ({ page }) => {
  // Manager delegates to the auditor for today only
  await login(page, 'manager', '/delegations')
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date())
  await page.getByLabel(/Delegate/).selectOption({ label: 'Arjun Mehta (EMP0002)' })
  await page.getByLabel(/Start date/).fill(today)
  await page.getByLabel(/End date/).fill(today)
  await page.getByRole('button', { name: 'Create delegation' }).click()
  await expect(page.getByText('Delegation created.')).toBeVisible()

  try {
    await login(page, 'employee2')
    const { claimId, claimNumber } = await createClaim(page, [
      { category: 'LOCAL_CAB', amount: '300.00', date: pastDate(6) },
    ])
    await submit(page, claimId)

    await login(page, 'auditor', '/approvals')
    const row = page.getByRole('row', { name: new RegExp(claimNumber) })
    await expect(row).toContainText('Manoj Pillai') // On behalf of
    await openFromApprovals(page, claimNumber)
    await expect(page.getByText('Acting on behalf of Manoj Pillai.')).toBeVisible()
    await decide(page, 'Approve')

    await login(page, 'employee2', `/claims/${claimId}`)
    await expect(status(page)).toHaveText(/Approved/)
    await expect(page.locator('.steps')).toContainText('on behalf of Manoj Pillai')
  } finally {
    await login(page, 'manager', '/delegations')
    const cancel = page.getByRole('button', { name: 'Cancel delegation' }).first()
    if (await cancel.isVisible()) {
      await cancel.click()
      await page.getByRole('dialog').getByRole('button', { name: 'Cancel delegation' }).click()
    }
  }
})

test('TC-E2E-010 another employee’s claim URL shows not-found; no admin links (FR-019, CL-15, B-10)', async ({ page }) => {
  await login(page, 'employee')
  const { claimId } = await createClaim(page, [{ category: 'MEALS', amount: '120.00', date: pastDate(4) }])
  await login(page, 'employee2', `/claims/${claimId}`)
  await expect(page.getByText('Not found or you do not have access.').first()).toBeVisible()
  const nav = page.getByRole('navigation', { name: 'Main' })
  for (const name of ['Approvals', 'Payouts', 'Audit trail', 'Policy rules']) {
    await expect(nav.getByRole('link', { name })).toHaveCount(0)
  }
  await login(page, 'employee2', '/payout-batches')
  await expect(page.getByRole('heading', { name: 'Not available' })).toBeVisible()
})

test('TC-E2E-011 finance head sees dashboard, audit trail and policy rules (FR-015..018, CL-10)', async ({ page }) => {
  await login(page, 'financehead', '/dashboard')
  await expect(page.getByRole('heading', { name: 'Spend by category' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Average approval time' })).toBeVisible()
  await nav(page, 'Audit trail')
  // Filter, then look inside the table: the newest rows may all be logins, and the filter
  // dropdown has a hidden <option> with the same text.
  await page.getByLabel('Action').selectOption('CLAIM_SUBMITTED')
  await page.getByRole('button', { name: 'Apply' }).click()
  await expect(page.locator('table').getByText('CLAIM_SUBMITTED').first()).toBeVisible()
  await expect(page.getByRole('button', { name: /delete/i })).toHaveCount(0)
  await nav(page, 'Policy rules')
  await expect(page.getByText('meals.max_per_day').first()).toBeVisible()
})
