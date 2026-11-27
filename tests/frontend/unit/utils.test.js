import { describe, expect, it } from 'vitest'
import { formatInr, sumMoney, toPaise } from '../../../frontend/src/utils/money.js'
import { formatDate, formatTimestamp, nightsBetween, todayIst } from '../../../frontend/src/utils/dates.js'
import { messageFor, fieldErrors } from '../../../frontend/src/utils/errors.js'
import { receiptRequired, validateDelegation, validateLine, validateQueueFilters } from '../../../frontend/src/utils/validation.js'
import { safeNext } from '../../../frontend/src/pages/LoginPage.jsx'

const nb = (s) => s.replace(/ /g, ' ')

describe('money (TC-FE-001..004)', () => {
  it('AC-17: "100000.00" is shown as ₹1,00,000.00 (Indian grouping)', () => {
    expect(nb(formatInr('100000.00'))).toBe('₹1,00,000.00')
    expect(nb(formatInr('27450'))).toBe('₹27,450.00')
    expect(nb(formatInr('0.5'))).toBe('₹0.50')
  })
  it('sums in integer paise, never floats', () => {
    expect(sumMoney(['0.10', '0.20'])).toBe('0.30')
    expect(sumMoney(['99999.99', '0.01'])).toBe('100000.00')
    expect(sumMoney([])).toBe('0.00')
  })
  it('rejects malformed money strings', () => {
    expect(toPaise('1.234')).toBeNull()
    expect(toPaise('-5')).toBeNull()
    expect(toPaise('abc')).toBeNull()
    expect(toPaise('500.5')).toBe(50050)
  })
  it('shows a dash for missing values', () => {
    expect(formatInr(null)).toBe('—')
  })
})

describe('dates (TC-FE-005..007)', () => {
  it('AC-18: 2026-11-27T18:45:00Z is shown as 28 Nov 2026, 00:15 IST', () => {
    expect(formatTimestamp('2026-11-27T18:45:00Z')).toBe('28 Nov 2026, 00:15 IST')
  })
  it('date-only values are not shifted by time zone', () => {
    expect(formatDate('2026-11-27')).toBe('27 Nov 2026')
  })
  it('today is computed in IST', () => {
    expect(todayIst(new Date('2026-11-27T19:00:00Z'))).toBe('2026-11-28')
    expect(todayIst(new Date('2026-11-27T18:00:00Z'))).toBe('2026-11-27')
  })
  it('nights between check-in and check-out (ASM-005)', () => {
    expect(nightsBetween('2026-11-10', '2026-11-12')).toBe(2)
    expect(nightsBetween('2026-11-10', '')).toBeNull()
  })
})

describe('line validation (TC-FE-008..016)', () => {
  const today = '2026-11-27'
  const ok = { category: 'MEALS', amount: '450.00', expenseDate: '2026-11-20', vendor: 'Cafe', receiptNumber: 'R1' }

  it('accepts a valid meals line', () => {
    expect(validateLine(ok, today)).toEqual({})
  })
  it.each([
    ['0', 'Amount must be greater than 0.'],
    ['0.00', 'Amount must be greater than 0.'],
    ['-10', 'Use a number with at most 2 decimals.'],
    ['10.123', 'Use a number with at most 2 decimals.'],
    ['', 'Enter an amount.'],
  ])('amount %j is rejected', (amount, message) => {
    expect(validateLine({ ...ok, amount }, today).amount).toBe(message)
  })
  it('expense date in the future is rejected', () => {
    expect(validateLine({ ...ok, expenseDate: '2026-11-28' }, today).expenseDate).toMatch(/future/)
  })
  it('vendor longer than 120 characters is rejected; 120 is allowed', () => {
    expect(validateLine({ ...ok, vendor: 'v'.repeat(121) }, today).vendor).toMatch(/120/)
    expect(validateLine({ ...ok, vendor: 'v'.repeat(120) }, today).vendor).toBeUndefined()
  })
  it('receipt number is required (CL-05)', () => {
    expect(validateLine({ ...ok, receiptNumber: '  ' }, today).receiptNumber).toBeDefined()
  })
  it('hotel lines need city and check-out after check-in (CL-08, ASM-005)', () => {
    const hotel = { ...ok, category: 'HOTEL', expenseDate: '', city: '', checkInDate: '2026-11-10', checkOutDate: '2026-11-10' }
    const e = validateLine(hotel, today)
    expect(e.city).toBeDefined()
    expect(e.checkOutDate).toMatch(/after check-in/)
    expect(e.expenseDate).toBeUndefined()
    expect(validateLine({ ...hotel, city: 'Mumbai', checkOutDate: '2026-11-12' }, today)).toEqual({})
  })
  it('AC-02/AC-03: receipt is required strictly above the limit (POL-005)', () => {
    expect(receiptRequired('500.00', '500.00')).toBe(false)
    expect(receiptRequired('500.01', '500.00')).toBe(true)
    expect(receiptRequired('499.99', '500.00')).toBe(false)
  })
})

describe('filter and delegation validation (TC-FE-017..018)', () => {
  it('min must not exceed max; from must not be after to', () => {
    const e = validateQueueFilters({ minAmount: '500', maxAmount: '100', fromDate: '2026-11-10', toDate: '2026-11-01' })
    expect(e.maxAmount).toBeDefined()
    expect(e.toDate).toBeDefined()
  })
  it('delegation end date must be on or after start', () => {
    expect(validateDelegation({ delegateId: 'x', startDate: '2026-12-10', endDate: '2026-12-01' }).endDate).toBeDefined()
    expect(validateDelegation({ delegateId: 'x', startDate: '2026-12-10', endDate: '2026-12-10' })).toEqual({})
  })
})

describe('error messages (TC-FE-019..021)', () => {
  it('maps codes to human messages and never shows raw text for unknown errors', () => {
    expect(messageFor({ code: 'NOT_FOUND', status: 404 })).toBe('Not found or you do not have access.')
    expect(messageFor({ code: 'SELF_APPROVAL_FORBIDDEN' })).toBe('You cannot act on your own claim.')
    expect(messageFor({ code: 'WEIRD', status: 500, message: 'java.lang.NullPointerException' })).toBe(
      'Something went wrong. Please try again.',
    )
  })
  it('uses the server message for business-rule violations', () => {
    expect(messageFor({ code: 'BUSINESS_RULE_VIOLATION', message: 'Batch already exported' })).toBe('Batch already exported')
  })
  it('reads field errors from details.fields', () => {
    expect(fieldErrors({ details: { fields: { amount: 'must be greater than 0' } } })).toEqual({
      amount: 'must be greater than 0',
    })
  })
})

describe('login redirect safety (TC-FE-022)', () => {
  it('only same-origin relative paths are allowed (no open redirect)', () => {
    expect(safeNext('/approvals')).toBe('/approvals')
    expect(safeNext('https://evil.example')).toBe('/claims')
    expect(safeNext('//evil.example')).toBe('/claims')
    expect(safeNext('/\\evil.example')).toBe('/claims')
    expect(safeNext(null)).toBe('/claims')
  })
})
